package dev.skiff

/**
 * Simkl pages: a movie (simkl.com/movies/53536/the-lord-of-the-rings…), a show or an anime (simkl.com/tv/11121/
 * breaking-bad), or one episode of it (…/breaking-bad/season-1/episode-4/). The title's page links to IMDb.
 */
object Simkl : PageSource() {
    override val name = "Simkl"
    override val key = "simkl"

    private val LINK = Regex(
        """^https?://(?:www\.)?simkl\.com/(movies|tv|anime)/(\d+)(?:/(?!season-)([^/?#\s]+))?(?:/season-(\d+)(?:/episode-(\d+))?)?""",
        RegexOption.IGNORE_CASE,
    )

    override fun ref(link: String): TitleRef? {
        val m = LINK.find(link.trim()) ?: return null
        val (type, id, slug, season, episode) = m.destructured
        val series = !type.equals("movies", ignoreCase = true)
        val (words, year) = if (slug.isEmpty()) null to null else TitleSources.slugWords(slug)
        return TitleRef(
            page = "https://simkl.com/${type.lowercase()}/$id" + if (slug.isEmpty()) "" else "/$slug",
            series = series,
            season = season.toIntOrNull()?.takeIf { series },
            episode = episode.toIntOrNull()?.takeIf { series },
            name = words,
            year = year,
        )
    }
}
