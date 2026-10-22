package dev.skiff.providers

import dev.skiff.Links
import dev.skiff.Meta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The title sites Skiff knows, and what most of them need: a page's IMDb link, or a title found by its name. */
object Providers {
    /** The first that matches a link wins. */
    val all: List<TitleProvider> = listOf(Trakt)

    /** The provider for `link`, or null (an IMDb link, any other link, or words). */
    fun forLink(link: String?): TitleProvider? = link?.trim()?.let { l -> all.firstOrNull { it.matches(l) } }

    private val IMDB_LINK = Regex("""imdb\.com/title/(tt\d{5,})""")

    /** The first IMDb title link in a page ("https://www.imdb.com/title/tt0120737/" → "tt0120737"). */
    fun imdbIdIn(html: String): String? = IMDB_LINK.find(html)?.groupValues?.get(1)

    /** Off the main thread. The IMDb id linked from the page at `url` (as a phone's browser gets it), or null. */
    suspend fun imdbIdOnPage(url: String, limit: Int = 600_000): String? = withContext(Dispatchers.IO) {
        runCatching { Links.get(url, limit)?.let(::imdbIdIn) }.getOrNull()
    }

    /** Off the main thread. A title by name in Cinemeta: the first of the right type, of `year` when given. */
    suspend fun search(name: String, series: Boolean, year: String? = null): String? {
        if (name.isBlank()) return null
        val hits = Meta.search(name, limit = 20).filter { it.series == series }
        return (hits.firstOrNull { year != null && it.year?.startsWith(year) == true } ?: hits.firstOrNull())?.imdbId
    }
}
