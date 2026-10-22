package dev.skiff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Trakt pages (trakt.tv, app.trakt.tv): a movie, a show, or one episode of it, opened in Nuvio or Stremio like an IMDb
 * page. Trakt's links say the type, season and episode, not the IMDb id: that's on the title's page (its IMDb link), or
 * else found by the name in the link, through Cinemeta's search.
 */
object Trakt : TitleSource {
    override val name = "Trakt"
    override val key = "trakt"

    /** What a Trakt link points at: `slug` is Trakt's ("breaking-bad", "the-lord-of-the-rings-…-2001", or a number). */
    data class Ref(val slug: String, val series: Boolean, val season: Int? = null, val episode: Int? = null)

    private val LINK = Regex(
        """^https?://(?:[a-z0-9-]+\.)?trakt\.tv/(movies|shows)/([^/?#\s]+)(?:/seasons/(\d+)(?:/episodes/(\d+))?)?""",
        RegexOption.IGNORE_CASE,
    )
    private val IMDB_LINK = Regex("""imdb\.com/title/(tt\d{5,})""")
    private val YEAR = Regex("""-((?:19|20)\d\d)$""")

    /** "https://trakt.tv/shows/breaking-bad/seasons/1/episodes/4" → breaking-bad, series, 1, 4; null when not Trakt's. */
    fun parse(link: String): Ref? {
        val m = LINK.find(link.trim()) ?: return null
        val (type, slug, season, episode) = m.destructured
        val series = type.equals("shows", ignoreCase = true)
        return Ref(slug, series, season.toIntOrNull()?.takeIf { series }, episode.toIntOrNull()?.takeIf { series })
    }

    override fun handles(link: String) = parse(link) != null

    /** The IMDb id on a title's Trakt page: its IMDb link, with the other sites it's on. */
    fun imdbIdIn(html: String): String? = IMDB_LINK.find(html)?.groupValues?.get(1)

    /** "the-lord-of-the-rings-the-fellowship-of-the-ring-2001" → "the lord of the rings the fellowship of the ring", "2001". */
    fun nameAndYear(slug: String): Pair<String, String?> {
        val year = YEAR.find(slug)?.groupValues?.get(1)
        val words = (if (year != null) slug.removeSuffix("-$year") else slug).replace('-', ' ').trim()
        return words to year
    }

    /** The title a Trakt link points at: season and episode from the link, the name left for Cinemeta. */
    override suspend fun resolve(link: String): TitleSource.Found? {
        val ref = parse(link) ?: return null
        // The title's own page (an episode's links to the episode on IMDb; Nuvio wants the show's).
        val page = "https://trakt.tv/${if (ref.series) "shows" else "movies"}/${ref.slug}"
        val fromPage = withContext(Dispatchers.IO) { runCatching { Links.get(page, limit = 600_000)?.let(::imdbIdIn) }.getOrNull() }
        val id = fromPage ?: byName(ref) ?: return null
        return TitleSource.Found(Title(id, "", ref.series, ref.season ?: 1, ref.episode ?: 1), ref.episode != null)
    }

    override fun searchFor(link: String): String? = parse(link)?.let { nameAndYear(it.slug).first }

    /** The slug's words searched in Cinemeta: the first of the right type (and year, when the slug has one). */
    private suspend fun byName(ref: Ref): String? {
        if (ref.slug.all { it.isDigit() }) return null
        val (name, year) = nameAndYear(ref.slug)
        val hits = Meta.search(name, limit = 20).filter { it.series == ref.series }
        return (hits.firstOrNull { year != null && it.year?.startsWith(year) == true } ?: hits.firstOrNull())?.imdbId
    }
}
