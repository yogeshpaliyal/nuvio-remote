package dev.skiff

/**
 * WeTrakr pages (wetrakr.com): a movie, a show, or one episode of it. The link has WeTrakr's own id; the title's data,
 * which the site's pages read (no key), says the IMDb id.
 */
object Wetrakr : TitleSource {
    override val name = "WeTrakr"
    override val key = "wetrakr"

    data class Ref(val id: String, val series: Boolean, val season: Int? = null, val episode: Int? = null)

    private val LINK = Regex(
        """^https?://(?:www\.)?wetrakr\.com/(movies|shows)/(\d+)(?:/seasons/(\d+)(?:/episodes/(\d+))?)?""",
        RegexOption.IGNORE_CASE,
    )
    private val IMDB = Regex(""""imdb"\s*:\s*\{\s*"id"\s*:\s*"(tt\d{5,})"""")

    /** "https://wetrakr.com/shows/1391953/seasons/1/episodes/4" → 1391953, series, 1, 4; null when not WeTrakr's. */
    fun parse(link: String): Ref? {
        val m = LINK.find(link.trim()) ?: return null
        val (type, id, season, episode) = m.destructured
        val series = type.equals("shows", ignoreCase = true)
        return Ref(id, series, season.toIntOrNull()?.takeIf { series }, episode.toIntOrNull()?.takeIf { series })
    }

    override fun handles(link: String) = parse(link) != null

    /** The title's IMDb id, in its data: `"ids": {"tmdb": {…}, "imdb": {"id": "tt0903747"}, …}`. */
    fun imdbIdIn(json: String): String? = IMDB.find(json)?.groupValues?.get(1)

    override suspend fun resolve(link: String): TitleSource.Found? {
        val ref = parse(link) ?: return null
        val data = "https://wetrakr.com/proxy/frontend/${if (ref.series) "shows" else "movies"}/${ref.id}"
        val id = TitleSources.page(data)?.let(::imdbIdIn) ?: return null
        return TitleSource.Found(Title(id, "", ref.series, ref.season ?: 1, ref.episode ?: 1), ref.episode != null)
    }
}
