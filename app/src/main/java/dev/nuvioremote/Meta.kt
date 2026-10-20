package dev.nuvioremote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A title's name and type from its IMDb id, through Cinemeta (the free catalog Stremio and Nuvio use; no key).
 * An id is either a movie or a series: both are asked at once, and the one that answers wins.
 */
object Meta {
    data class Found(val name: String, val series: Boolean)

    private const val CINEMETA = "https://v3-cinemeta.strem.io/meta"

    /** Off the main thread; null when neither knows it (or there's no internet). */
    suspend fun lookup(imdbId: String): Found? = coroutineScope {
        val movie = async { fetch("movie", imdbId) }
        val series = async { fetch("series", imdbId) }
        movie.await()?.let { Found(it, false) } ?: series.await()?.let { Found(it, true) }
    }

    private suspend fun fetch(type: String, id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("$CINEMETA/$type/$id.json").openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.instanceFollowRedirects = false   // the wrong type redirects (307): not this one
            try {
                if (c.responseCode != 200) return@runCatching null
                val meta = JSONObject(c.inputStream.bufferedReader().readText()).optJSONObject("meta")
                meta?.optString("name")?.takeIf { it.isNotBlank() }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
