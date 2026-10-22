package dev.skiff

/**
 * Trakt pages (trakt.tv, app.trakt.tv): a movie, a show, or one episode of it. Trakt's links say the type, season and
 * episode, not the IMDb id: that's on the title's page (its IMDb link), or else found by the name in the link.
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

    /** "https://trakt.tv/shows/breaking-bad/seasons/1/episodes/4" → breaking-bad, series, 1, 4; null when not Trakt's. */
    fun parse(link: String): Ref? {
        val m = LINK.find(link.trim()) ?: return null
        val (type, slug, season, episode) = m.destructured
        val series = type.equals("shows", ignoreCase = true)
        return Ref(slug, series, season.toIntOrNull()?.takeIf { series }, episode.toIntOrNull()?.takeIf { series })
    }

    override fun handles(link: String) = parse(link) != null

    override suspend fun resolve(link: String): TitleSource.Found? {
        val ref = parse(link) ?: return null
        // The title's own page (an episode's links to the episode on IMDb; Nuvio wants the show's).
        val page = "https://trakt.tv/${if (ref.series) "shows" else "movies"}/${ref.slug}"
        val id = TitleSources.page(page)?.let(TitleSources::imdbLinkIn) ?: TitleSources.byName(ref.slug, ref.series) ?: return null
        return TitleSource.Found(Title(id, "", ref.series, ref.season ?: 1, ref.episode ?: 1), ref.episode != null)
    }

    override fun searchFor(link: String): String? = parse(link)?.let { TitleSources.nameAndYear(it.slug).first }
}
