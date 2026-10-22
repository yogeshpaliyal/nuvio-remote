package dev.skiff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A site whose links are titles but don't carry the IMDb id (Trakt…): looked up, then opened in Nuvio or Stremio like
 * a shared IMDb page. A new site is a `TitleSource` in its own file and a line in [TitleSources.all]; the remote,
 * shares, pastes and the search box take any of them.
 */
interface TitleSource {
    /** Its name, as the app says it ("Trakt"). */
    val name: String

    /** For analytics: "trakt". */
    val key: String

    /** Whether `link` is one of its title pages. */
    fun handles(link: String): Boolean

    /** Off the main thread. The title `link` points at, or null when it can't be found (no internet, no IMDb id). */
    suspend fun resolve(link: String): Found?

    /** Not found: what to search Cinemeta for instead (the name in the link), or null. */
    fun searchFor(link: String): String? = null

    /** `episodeKnown`: the link named the episode, so it plays without asking for one. */
    data class Found(val title: Title, val episodeKnown: Boolean = false)
}

object TitleSources {
    val all: List<TitleSource> = listOf(Trakt, Simkl, Letterboxd, MdbList, Wetrakr)

    /** The site `link` is a title page of; null for IMDb's (read directly) and any other link. */
    fun of(link: String): TitleSource? = all.firstOrNull { it.handles(link.trim()) }

    // For the sources: what most of them need.

    private val IMDB_LINK = Regex("""imdb\.com/title/(tt\d{5,})""")
    private val YEAR = Regex("""-((?:19|20)\d\d)$""")

    /** The IMDb id a title's page links to (its IMDb link, with the other sites it's on). */
    fun imdbLinkIn(html: String): String? = IMDB_LINK.find(html)?.groupValues?.get(1)

    /** "the-lord-of-the-rings-the-fellowship-of-the-ring-2001" → "the lord of the rings the fellowship of the ring", "2001". */
    fun nameAndYear(slug: String): Pair<String, String?> {
        val year = YEAR.find(slug)?.groupValues?.get(1)
        val words = (if (year != null) slug.removeSuffix("-$year") else slug).replace('-', ' ').trim()
        return words to year
    }

    /** Off the main thread. A page's text, or null when it doesn't answer (or there's no internet). */
    suspend fun page(url: String, limit: Int = 600_000): String? =
        withContext(Dispatchers.IO) { runCatching { Links.get(url, limit) }.getOrNull() }

    /** A slug's words searched in Cinemeta: the first of the right type (and year, when the slug has one). */
    suspend fun byName(slug: String, series: Boolean): String? {
        if (slug.all { it.isDigit() }) return null
        val (name, year) = nameAndYear(slug)
        val hits = Meta.search(name, limit = 20).filter { it.series == series }
        return (hits.firstOrNull { year != null && it.year?.startsWith(year) == true } ?: hits.firstOrNull())?.imdbId
    }
}
