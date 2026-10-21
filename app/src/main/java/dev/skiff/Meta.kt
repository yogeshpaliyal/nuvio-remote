package dev.skiff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Titles found by name (search), and a title's name and type from its IMDb id, through Cinemeta (the free catalog Stremio and Nuvio use; no key).
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
    private const val CATALOG = "https://v3-cinemeta.strem.io/catalog"

    /** A search result: an IMDb title, enough to show it and fill it in. */
    data class Hit(val imdbId: String, val name: String, val series: Boolean, val year: String?, val poster: String?)

    /**
     * Off the main thread. Titles whose name matches `query`, movies and series asked at once and taken in turn
     * (the best of each first), up to `limit`. Only the words typed go to Cinemeta. Empty on no match or no internet.
     */
    suspend fun search(query: String, limit: Int = 8): List<Hit> = coroutineScope {
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val movies = async { catalog("movie", q) }
        val series = async { catalog("series", q) }
        val m = movies.await()
        val s = series.await()
        buildList { for (i in 0 until maxOf(m.size, s.size)) { m.getOrNull(i)?.let(::add); s.getOrNull(i)?.let(::add) } }
            .distinctBy { it.imdbId }
            .take(limit)
    }

    private suspend fun catalog(type: String, q: String): List<Hit> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("$CATALOG/$type/top/search=$q.json").openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            try {
                if (c.responseCode != 200) return@runCatching emptyList()
                val a = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("metas") ?: return@runCatching emptyList()
                List(a.length()) { a.getJSONObject(it) }
                    .filter { Title.isImdbId(it.optString("id")) && it.optString("name").isNotBlank() }
                    .map {
                        Hit(
                            imdbId = it.optString("id"),
                            name = it.optString("name"),
                            series = type == "series",
                            year = it.optString("releaseInfo").takeIf { y -> y.isNotBlank() && y != "null" },
                            poster = it.optString("poster").takeIf { p -> p.startsWith("http") },
                        )
                    }
            } finally {
                c.disconnect()
            }
        }.getOrDefault(emptyList())
    }

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
