package dev.skiff.providers

import dev.skiff.Title

/**
 * Trakt pages (trakt.tv, app.trakt.tv): a movie, a show, or one episode of it. Trakt's links say the type, season and
 * episode, not the IMDb id: that's on the title's page (its IMDb link), or else found by the name in the link.
 */
object Trakt : TitleProvider {
    override val id = "trakt"
    override val name = "Trakt"

    /** What a Trakt link points at: `slug` is Trakt's ("breaking-bad", "the-lord-of-the-rings-…-2001", or a number). */
    data class Ref(val slug: String, val series: Boolean, val season: Int? = null, val episode: Int? = null)

    private val LINK = Regex(
        """^https?://(?:[a-z0-9-]+\.)?trakt\.tv/(movies|shows)/([^/?#\s]+)(?:/seasons/(\d+)(?:/episodes/(\d+))?)?""",
        RegexOption.IGNORE_CASE,
    )
    private val YEAR = Regex("""-((?:19|20)\d\d)$""")

    /** "https://trakt.tv/shows/breaking-bad/seasons/1/episodes/4" → breaking-bad, series, 1, 4; null when not Trakt's. */
    fun parse(link: String): Ref? {
        val m = LINK.find(link.trim()) ?: return null
        val (type, slug, season, episode) = m.destructured
        val series = type.equals("shows", ignoreCase = true)
        return Ref(slug, series, season.toIntOrNull()?.takeIf { series }, episode.toIntOrNull()?.takeIf { series })
    }

    /** "the-lord-of-the-rings-the-fellowship-of-the-ring-2001" → "the lord of the rings the fellowship of the ring", "2001". */
    fun nameAndYear(slug: String): Pair<String, String?> {
        val year = YEAR.find(slug)?.groupValues?.get(1)
        val words = (if (year != null) slug.removeSuffix("-$year") else slug).replace('-', ' ').trim()
        return words to year
    }

    override fun matches(link: String) = parse(link) != null

    override suspend fun resolve(link: String): TitleProvider.Found? {
        val ref = parse(link) ?: return null
        // The title's own page (an episode's links to the episode on IMDb; Nuvio wants the show's).
        val id = Providers.imdbIdOnPage("https://trakt.tv/${if (ref.series) "shows" else "movies"}/${ref.slug}")
            ?: byName(ref) ?: return null
        return TitleProvider.Found(Title(id, "", ref.series, ref.season ?: 1, ref.episode ?: 1), episode = ref.episode != null)
    }

    override fun searchWords(link: String): String? =
        parse(link)?.slug?.takeIf { s -> !s.all { it.isDigit() } }?.let { nameAndYear(it).first }

    private suspend fun byName(ref: Ref): String? {
        if (ref.slug.all { it.isDigit() }) return null
        val (name, year) = nameAndYear(ref.slug)
        return Providers.search(name, ref.series, year)
    }
}
