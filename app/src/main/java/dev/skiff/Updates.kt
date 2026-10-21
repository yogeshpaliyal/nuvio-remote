package dev.skiff

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Newer versions of Skiff: the latest GitHub release, checked at most twice a day (Settings can check now). Installed
 * from Google Play, the update comes from Play (Play doesn't allow apps to update themselves); otherwise the release's
 * APK is opened in the browser to download and install.
 */
object Updates {
    private const val LATEST = "https://api.github.com/repos/yogeshpaliyal/skiff/releases/latest"
    private const val EVERY = 12 * 60 * 60 * 1000L

    /** A release: its version ("1.9.0"), what's new (Markdown), the APK and the release page. */
    data class Release(val version: String, val notes: String, val apkUrl: String?, val pageUrl: String)

    private val available = MutableStateFlow<Release?>(null)
    /** The newer release, when there's one; null when up to date or not checked yet. */
    val update: StateFlow<Release?> = available

    private fun prefs(context: Context) = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** This build's version: "1.8.0" (a debug build's "-debug" left off). */
    fun current(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().substringBefore('-')
    }.getOrDefault("")

    /** True when `a` is a later version than `b` ("1.10.0" > "1.9.2"). */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /**
     * Off the main thread. Checks GitHub (`force`, or when the last check is old), and updates [update]. Returns
     * the newer release, or null when up to date. Throws when GitHub can't be reached and `force` is set.
     */
    suspend fun check(context: Context, force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val cached = p.getString("latest", null)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }
        val fresh = System.currentTimeMillis() - p.getLong("checkedAt", 0) < EVERY
        val latest = if (!force && fresh && cached != null) cached else {
            try {
                val json = fetch()
                p.edit().putString("latest", json.toString()).putLong("checkedAt", System.currentTimeMillis()).apply()
                parse(json)
            } catch (e: Exception) {
                if (force) throw e
                cached
            }
        }
        val newer = latest?.takeIf { newer(it.version, current(context)) }
        available.value = newer
        newer
    }

    private fun fetch(): JSONObject {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) error("GitHub answered ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    private fun parse(o: JSONObject): Release {
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
        return Release(
            version = o.optString("tag_name").removePrefix("v"),
            notes = o.optString("body"),
            apkUrl = apk?.takeIf { it.startsWith("https://") },
            pageUrl = o.optString("html_url"),
        )
    }

    /** Installed from Google Play: updates come from there. */
    fun fromPlay(context: Context): Boolean = runCatching {
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(context.packageName)
        }
        installer == "com.android.vending"
    }.getOrDefault(false)

    /** Gets the update: Play's page for Skiff, or the release's APK (the browser downloads it; tap it to install). */
    fun open(context: Context, r: Release) {
        val url = when {
            fromPlay(context) -> "https://play.google.com/store/apps/details?id=dev.skiff"
            r.apkUrl != null -> r.apkUrl
            else -> r.pageUrl
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
