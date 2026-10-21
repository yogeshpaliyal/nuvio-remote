package dev.skiff

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The github build's updates: the latest GitHub release, asked at most twice a day (or now, from Settings); the
 * update is its APK, opened in the browser to download and install.
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

    fun open(context: Context, r: Updates.Release) {
        val url = r.apkUrl ?: r.pageUrl
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
