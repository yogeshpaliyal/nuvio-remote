package dev.nuvioremote

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens a title in Nuvio on the TV over Wi-Fi, through the TV's own ADB (port 5555), as `adb shell am start` would
 * from a computer. Nuvio has no link for a title: it reads these extras (the ones it writes to its own "Continue
 * Watching" row), and with launchMode=stream (and a name) it goes straight to the stream picker.
 *
 * The phone has its own ADB key, made once: the first time, the TV asks "Allow USB debugging?" for it.
 */
object TvLink {
    const val NUVIO = "com.nuvio.tv"
    private const val PORT = 5555

    private fun keyPair(context: Context): AdbKeyPair {
        val private = File(context.filesDir, "adbkey")
        val public = File(context.filesDir, "adbkey.pub")
        if (!private.exists() || !public.exists()) AdbKeyPair.generate(private, public)
        return AdbKeyPair.read(private, public)
    }

    /** The shell command, with every value quoted for the TV's shell. */
    fun command(t: Title): String = buildString {
        append("am start -n $NUVIO/.MainActivity -f 0x14000000")
        append(" --es contentId ").append(q(t.imdbId))
        append(" --es contentType ").append(if (t.series) "series" else "movie")
        append(" --es videoId ").append(q(t.videoId))
        append(" --es launchMode stream")
        append(" --es name ").append(q(t.name))
        if (t.series) append(" --ei season ${t.season} --ei episode ${t.episode}")
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Off the main thread. Returns the TV's answer; throws when the TV can't be reached or refuses. */
    suspend fun open(context: Context, host: String, t: Title): String = withContext(Dispatchers.IO) {
        Dadb.create(host.trim(), PORT, keyPair(context)).use { tv ->
            val installed = tv.shell("pm path $NUVIO").output.contains("package:")
            if (!installed) error("Nuvio ($NUVIO) isn't installed on that TV.")
            val r = tv.shell(command(t))
            val out = r.allOutput.trim()
            if (r.exitCode != 0 || out.contains("Error")) error(out.ifEmpty { "The TV said no (exit ${r.exitCode})." })
            out
        }
    }
}
