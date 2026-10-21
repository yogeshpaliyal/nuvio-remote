package dev.skiff

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * How links open on the TV, as data (assets/rules.json): which links a rule is for, how to change the link for the
 * TV app, which apps to prefer, how to start them, and where the title comes from. A link no rule matches is sent
 * as it is to whichever TV app opens it.
 *
 * The newest valid copy wins: the one in the app, or the repo's (fetched at most once a day, kept only if it parses
 * and its version is higher). Rules are data, never commands: a rule can only change the link and choose among the
 * apps the TV itself says open it; flags come from a fixed list; and every value is quoted for the TV's shell.
 */
object Rules {
    data class Rule(
        val id: String,
        val name: String?,
        val hosts: List<String>,
        val schemes: List<String>,
        val rewriteFrom: Regex?,
        val rewriteTo: String?,
        val packages: List<String>,
        val flags: Int,
        val title: String?,     // "slug", "og", "oembed:<url with {link}>"; null: og, then the slug
    ) {
        fun matches(link: String): Boolean {
            val u = runCatching { Uri.parse(link) }.getOrNull() ?: return false
            val scheme = u.scheme?.lowercase(Locale.ROOT)
            val host = u.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
            if (scheme != null && scheme in schemes) return true
            return host != null && (scheme == "http" || scheme == "https") && hosts.any { host == it || host.endsWith(".$it") }
        }

        /** The link as the TV app wants it; unchanged when the result isn't a link. */
        fun forTv(link: String): String {
            val from = rewriteFrom ?: return link
            val out = runCatching { from.replaceFirst(link, rewriteTo.orEmpty()) }.getOrNull() ?: return link
            return if (Links.isLink(out)) out else link
        }
    }

    private class Set(val version: Int, val rules: List<Rule>, val apps: Map<String, String>)

    /** The repo's copy (raw), checked at most once a day. */
    private const val REMOTE = "https://raw.githubusercontent.com/yogeshpaliyal/skiff/main/app/src/main/assets/rules.json"
    private const val DAY = 24 * 60 * 60 * 1000L

    /** Start flags a rule may ask for, by name (Intent.FLAG_ACTIVITY_*). */
    private val FLAGS = mapOf(
        "NEW_TASK" to 0x10000000,
        "CLEAR_TASK" to 0x00008000,
        "CLEAR_TOP" to 0x04000000,
        "SINGLE_TOP" to 0x20000000,
        "NO_HISTORY" to 0x40000000,
        "RESET_TASK_IF_NEEDED" to 0x00200000,
    )
    /** NEW_TASK | CLEAR_TASK: a fresh start of the app's screen, so it reads the link (JioHotstar needs it). */
    private const val DEFAULT_FLAGS = 0x10008000

    @Volatile private var current: Set? = null
    private lateinit var cache: File

    /** Once, at start: the bundled rules, or the downloaded ones when newer. */
    fun init(context: Context) {
        if (current != null) return
        cache = File(context.filesDir, "rules.json")
        val bundled = runCatching { parse(context.assets.open("rules.json").bufferedReader().readText()) }.getOrNull()
        val downloaded = runCatching { parse(cache.readText()) }.getOrNull()
        current = listOfNotNull(bundled, downloaded).maxByOrNull { it.version } ?: Set(0, emptyList(), emptyMap())
    }

    val version: Int get() = current?.version ?: 0

    /** Off the main thread: the repo's rules, at most once a day; kept when they parse and are newer. */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        init(context)
        val stamp = File(context.filesDir, "rules.checked")
        if (System.currentTimeMillis() - stamp.lastModified() < DAY && stamp.exists()) return@withContext
        runCatching {
            val c = URL(REMOTE).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            val text = try {
                if (c.responseCode != 200) null else c.inputStream.bufferedReader().readText()
            } finally {
                c.disconnect()
            }
            stamp.writeText(System.currentTimeMillis().toString())
            val fresh = text?.let(::parse) ?: return@runCatching
            if (fresh.version > version) {
                File(cache.path + ".tmp").apply { writeText(text) }.renameTo(cache)
                current = fresh
            }
        }
    }

    /** The first rule for this link, or null. */
    fun ruleFor(link: String): Rule? = current?.rules?.firstOrNull { it.matches(link) }

    /** The link as its TV app wants it (a rule's rewrite), or as it is. */
    fun forTv(link: String): String = ruleFor(link)?.forTv(link) ?: link

    /** Start flags for this link: the rule's, or a fresh start. */
    fun flags(link: String): Int = ruleFor(link)?.flags ?: DEFAULT_FLAGS

    /** An app's name ("JioHotstar"): the rules' list, or the package. */
    fun appName(pkg: String): String = current?.apps?.get(pkg) ?: pkg

    /** Apps in the rule's order first (its preferred app on top), then the rest as the TV listed them. */
    fun <T> preferred(link: String, apps: List<T>, pkg: (T) -> String): List<T> {
        val order = ruleFor(link)?.packages.orEmpty()
        if (order.isEmpty()) return apps
        return apps.sortedBy { a -> order.indexOf(pkg(a)).let { if (it < 0) order.size else it } }
    }

    private fun parse(text: String): Set {
        val o = JSONObject(text)
        val rules = o.optJSONArray("rules")
        val list = List(rules?.length() ?: 0) { i -> rule(rules!!.getJSONObject(i)) }.filterNotNull()
        val appsObj = o.optJSONObject("apps")
        val apps = appsObj?.keys()?.asSequence()?.associateWith { appsObj.optString(it) }.orEmpty()
        return Set(o.getInt("version"), list, apps)
    }

    /** One rule, or null when it doesn't make sense (it's skipped, the others still count). */
    private fun rule(o: JSONObject): Rule? = runCatching {
        val match = o.getJSONObject("match")
        fun strings(name: String, from: JSONObject = o) =
            from.optJSONArray(name)?.let { a -> List(a.length()) { a.getString(it).lowercase(Locale.ROOT) } }.orEmpty()
        val rewrite = o.optJSONObject("rewrite")
        val flagNames = o.optJSONArray("flags")?.let { a -> List(a.length()) { a.getString(it) } }
        val flags = flagNames?.map { FLAGS[it] ?: error("unknown flag $it") }?.fold(0) { acc, f -> acc or f } ?: DEFAULT_FLAGS
        Rule(
            id = o.getString("id"),
            name = o.optString("name").takeIf { it.isNotEmpty() },
            hosts = strings("hosts", match).map { it.removePrefix("www.") },
            schemes = strings("schemes", match),
            rewriteFrom = rewrite?.getString("from")?.let { Regex(it, RegexOption.IGNORE_CASE) },
            rewriteTo = rewrite?.getString("to"),
            packages = o.optJSONArray("packages")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
            flags = if (flags and 0x10000000 == 0) flags or 0x10000000 else flags,  // started from the shell: always a task
            title = o.optString("title").takeIf { it.isNotEmpty() },
        ).takeIf { it.hosts.isNotEmpty() || it.schemes.isNotEmpty() }
    }.getOrNull()
}
