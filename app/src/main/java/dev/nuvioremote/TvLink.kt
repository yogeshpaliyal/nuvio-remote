package dev.nuvioremote

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Talks to the TV over Wi-Fi, through its own ADB (port 5555), as `adb shell` would from a computer:
 *  - apps: the Nuvio apps on the TV (the Play Store and GitHub builds, and forks), from what Android says handles
 *    their intents;
 *  - open: a title in one of them. Nuvio has no link for a title: it reads these extras (the ones it writes to its own
 *    "Continue Watching" row), and with launchMode=stream (and a name) it goes straight to the stream picker.
 *
 * The phone has its own ADB key, made once: the first time, the TV asks "Allow USB debugging?" for it.
 */
object TvLink {
    private const val PORT = 5555
    /** Nuvio's activity: forks that renamed the app (and even the scheme) usually keep the code, and this class. */
    private const val NUVIO_CLASS = "com.nuvio.tv.MainActivity"

    /** A Nuvio app on the TV: `component` is what to start ("com.nuvio.app/com.nuvio.tv.MainActivity"). */
    data class NuvioApp(val pkg: String, val component: String, val version: String?, val source: String) {
        val name: String get() = if (pkg.startsWith("com.nuvio.")) "Nuvio" else pkg
    }

    private fun keyPair(context: Context): AdbKeyPair {
        val private = File(context.filesDir, "adbkey")
        val public = File(context.filesDir, "adbkey.pub")
        if (!private.exists() || !public.exists()) AdbKeyPair.generate(private, public)
        return AdbKeyPair.read(private, public)
    }

    private fun connect(context: Context, host: String) = Dadb.create(host.trim(), PORT, keyPair(context))

    /** Off the main thread. The Nuvio apps on that TV, the TV build first. Throws when the TV can't be reached. */
    suspend fun apps(context: Context, host: String): List<NuvioApp> = withContext(Dispatchers.IO) {
        connect(context, host).use(::detect)
    }

    /**
     * What Android says opens `nuvio://` (Nuvio's own scheme: every build, and forks that kept it), plus what opens
     * `stremio://` with Nuvio's activity class (forks with their own scheme). Not Stremio itself: it opens
     * stremio:// too, but knows nothing of these extras.
     */
    private fun detect(tv: Dadb): List<NuvioApp> {
        val query = "cmd package query-activities --brief -a android.intent.action.VIEW -d"
        val out = tv.shell("$query nuvio://open; echo '#'; $query stremio://open").output
        val nuvio = components(out.substringBefore('#'))
        val stremio = components(out.substringAfter('#', "")).filter { it.substringAfter('/') == NUVIO_CLASS }
        val byPkg = LinkedHashMap<String, String>()
        for (c in nuvio + stremio) byPkg.putIfAbsent(c.substringBefore('/'), c)
        return byPkg.map { (pkg, component) ->
            val info = tv.shell("dumpsys package $pkg | grep -E 'versionName=|installerPackageName='").output
            NuvioApp(
                pkg = pkg,
                component = component,
                version = Regex("""versionName=(\S+)""").find(info)?.groupValues?.get(1),
                source = source(Regex("""installerPackageName=(\S+)""").find(info)?.groupValues?.get(1)),
            )
        }.sortedBy { if (it.pkg == "com.nuvio.tv") 0 else 1 }
    }

    /** "com.nuvio.tv/.MainActivity" → "com.nuvio.tv/com.nuvio.tv.MainActivity", from query-activities' lines. */
    private fun components(text: String): List<String> = text.lines().map { it.trim() }
        .filter { it.contains('/') && !it.contains(' ') && !it.contains('=') }
        .map { c ->
            val pkg = c.substringBefore('/')
            val cls = c.substringAfter('/')
            "$pkg/" + if (cls.startsWith(".")) pkg + cls else cls
        }

    private fun source(installer: String?): String = when (installer) {
        "com.android.vending" -> "Play Store"
        "com.google.android.packageinstaller", "com.android.packageinstaller" -> "Installed from an APK"
        null, "null", "com.android.shell" -> "Installed with ADB"
        else -> installer
    }

    /** The shell command, with every value quoted for the TV's shell. */
    fun command(t: Title, component: String): String = buildString {
        append("am start -n ").append(q(component)).append(" -f 0x14000000")
        append(" --es contentId ").append(q(t.imdbId))
        append(" --es contentType ").append(if (t.series) "series" else "movie")
        append(" --es videoId ").append(q(t.videoId))
        append(" --es launchMode stream")
        append(" --es name ").append(q(t.name))
        if (t.series) append(" --ei season ${t.season} --ei episode ${t.episode}")
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Off the main thread. Opens the title in `component` (null = the first Nuvio app found). Returns the TV's answer;
     * throws when the TV can't be reached, has no Nuvio app, or refuses.
     */
    suspend fun open(context: Context, host: String, t: Title, component: String?): String = withContext(Dispatchers.IO) {
        connect(context, host).use { tv ->
            val apps = detect(tv)
            val target = apps.firstOrNull { it.component == component } ?: apps.firstOrNull()
                ?: error("There's no Nuvio app on that TV.")
            val r = tv.shell(command(t, target.component))
            val out = r.allOutput.trim()
            if (r.exitCode != 0 || out.contains("Error")) error(out.ifEmpty { "The TV said no (exit ${r.exitCode})." })
            out
        }
    }
}
