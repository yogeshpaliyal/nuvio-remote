package dev.skiff

/**
 * WeTrakr pages: wetrakr.com/movies/882086, wetrakr.com/shows/…, and an episode (…/seasons/1/episodes/4). The links
 * carry only WeTrakr's number: the IMDb id comes from the page's IMDb link, else its title ("Under the Grey Sky (2024)
 * · WeTrakr") is searched.
 */
object WeTrakr : PageSource() {
    override val name = "WeTrakr"
    override val key = "wetrakr"

    private val LINK = Regex(
        """^https?://(?:www\.)?wetrakr\.com/(movies|shows)/(\d+)(?:/seasons?/(\d+)(?:/episodes?/(\d+))?)?""",
        RegexOption.IGNORE_CASE,
    )

    override fun ref(link: String): TitleRef? {
        val m = LINK.find(link.trim()) ?: return null
        val (type, id, season, episode) = m.destructured
        val series = type.equals("shows", ignoreCase = true)
        return TitleRef(
            page = "https://wetrakr.com/${type.lowercase()}/$id",
            series = series,
            season = season.toIntOrNull()?.takeIf { series },
            episode = episode.toIntOrNull()?.takeIf { series },
        )
    }
}
