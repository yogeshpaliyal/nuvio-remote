package dev.skiff

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
    data class Found(
        val name: String,
        val series: Boolean,
        val poster: String? = null,
        val background: String? = null,
        val year: String? = null,       // "2001", "2025–"
        val genres: List<String> = emptyList(),
        val rating: String? = null,     // IMDb, "8.9"
        val runtime: String? = null,    // "179 min"
    )

    private const val CINEMETA = "https://v3-cinemeta.strem.io/meta"

    /** Off the main thread; null when neither knows it (or there's no internet). */
    suspend fun lookup(imdbId: String): Found? = coroutineScope {
        val movie = async { fetch("movie", imdbId) }
        val series = async { fetch("series", imdbId) }
        movie.await()?.let { found(it, false) } ?: series.await()?.let { found(it, true) }
    }

    private fun found(m: JSONObject, series: Boolean) = Found(
        name = m.optString("name"),
        series = series,
        poster = m.optString("poster").takeIf { it.startsWith("http") },
        background = m.optString("background").takeIf { it.startsWith("http") },
        year = m.optString("releaseInfo").takeIf { it.isNotBlank() },
        genres = m.optJSONArray("genres")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty().take(3),
        rating = m.optString("imdbRating").takeIf { it.isNotBlank() },
        runtime = m.optString("runtime").takeIf { it.isNotBlank() },
    )

    /** The title's "meta" object, when this type knows the id. */
    private suspend fun fetch(type: String, id: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("$CINEMETA/$type/$id.json").openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.instanceFollowRedirects = false   // the wrong type redirects (307): not this one
            try {
                if (c.responseCode != 200) return@runCatching null
                val meta = JSONObject(c.inputStream.bufferedReader().readText()).optJSONObject("meta")
                meta?.takeIf { it.optString("name").isNotBlank() }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
