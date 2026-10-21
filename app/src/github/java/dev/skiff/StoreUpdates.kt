package dev.skiff

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The github build's updates: the latest GitHub release, asked at most twice a day (or now, from Settings). The
 * update is its APK: Skiff downloads it (with progress), checks it's Skiff, and hands it to Android's installer, which
 * asks you to confirm. Android takes it only when it's signed with the same key, so your data stays.
 */
internal object StoreUpdates {
    private const val LATEST = "https://api.github.com/repos/yogeshpaliyal/skiff/releases/latest"
    private const val EVERY = 12 * 60 * 60 * 1000L

    suspend fun check(context: Context, force: Boolean): Updates.Release? = withContext(Dispatchers.IO) {
        val p = Updates.prefs(context)
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
        latest?.takeIf { Updates.newer(it.version, Updates.current(context)) }
    }

    suspend fun open(context: Context, r: Updates.Release) {
        val apkUrl = r.apkUrl ?: return browse(context, r.pageUrl)
        val progress = Updates.progressState
        // Android asks once to allow Skiff to install apps: its settings page, then Update again.
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            progress.value = Updates.Progress.Problem("Allow Skiff to install apps, then tap Update again.")
            return
        }
        try {
            progress.value = Updates.Progress.Downloading(0f)
            val apk = download(context, apkUrl) { progress.value = Updates.Progress.Downloading(it) }
            // It must be Skiff (the release app), or nothing is installed.
            val pkg = context.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName
            if (pkg != RELEASE_PACKAGE) error("The download isn't Skiff ($pkg)")
            progress.value = Updates.Progress.Installing
            install(context, apk)
        } catch (e: Exception) {
            progress.value = Updates.Progress.Problem("Couldn't update: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private const val RELEASE_PACKAGE = "dev.skiff"

    private fun browse(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** The APK into the cache (GitHub redirects to its file host); `onProgress` with 0–1, or null when the size isn't known. */
    private suspend fun download(context: Context, url: String, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        try {
            if (c.responseCode != 200) error("GitHub answered ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            var shown = -1
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != shown) {
                            shown = pct
                            onProgress(if (total > 0) done.toFloat() / total else null)
                        }
                    }
                }
            }
            if (total > 0 && done != total) error("The download stopped early")
            file
        } finally {
            c.disconnect()
        }
    }

    /** Hands the APK to Android's installer: it asks you to confirm (InstallReceiver shows that), then updates. */
    private suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(RELEASE_PACKAGE)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("skiff.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val status = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
        }
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

    private fun parse(o: JSONObject): Updates.Release {
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
        return Updates.Release(
            version = o.optString("tag_name").removePrefix("v"),
            notes = o.optString("body"),
            apkUrl = apk?.takeIf { it.startsWith("https://") },
            pageUrl = o.optString("html_url"),
        )
    }
}
