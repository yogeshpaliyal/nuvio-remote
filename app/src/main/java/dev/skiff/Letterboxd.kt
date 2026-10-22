package dev.skiff

/**
 * Letterboxd films (letterboxd.com/film/…, a member's review of one, and boxd.it short links): always movies. The film's
 * page links to its IMDb page; not found, the name in the link is searched.
 */
object Letterboxd : TitleSource {
    override val name = "Letterboxd"
    override val key = "letterboxd"

    private val FILM = Regex("""^https?://(?:www\.)?letterboxd\.com/(?:[^/?#\s]+/)?film/([^/?#\s]+)""", RegexOption.IGNORE_CASE)
    private val SHORT = Regex("""^https?://boxd\.it/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

    /** "https://letterboxd.com/someone/film/the-matrix/" → "the-matrix"; null for a short link or another site's. */
    fun slug(link: String): String? = FILM.find(link.trim())?.groupValues?.get(1)

    override fun handles(link: String) = slug(link) != null || SHORT.containsMatchIn(link.trim())

    override suspend fun resolve(link: String): TitleSource.Found? {
        val slug = slug(link)
        // The film's own page (a short link redirects to it).
        val page = if (slug != null) "https://letterboxd.com/film/$slug/" else SHORT.find(link.trim())?.value ?: return null
        val id = TitleSources.page(page)?.let(TitleSources::imdbLinkIn) ?: slug?.let { TitleSources.byName(it, series = false) } ?: return null
        return TitleSource.Found(Title(id, "", series = false))
    }

    override fun searchFor(link: String): String? = slug(link)?.let { TitleSources.nameAndYear(it).first }
}
