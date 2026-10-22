package dev.skiff

/**
 * Simkl pages (simkl.com): a movie, a TV show or an anime, and an episode of a show. The link has Simkl's id; its API
 * (no key) says the IMDb id, and whether an anime is a movie. Not found: the name in the link, searched.
 */
object Simkl : TitleSource {
    override val name = "Simkl"
    override val key = "simkl"

    /** `type`: "movies", "tv" or "anime"; `slug` may be missing ("https://simkl.com/tv/17465"). */
    data class Ref(val type: String, val id: String, val slug: String?, val season: Int? = null, val episode: Int? = null)

    private val LINK = Regex(
        """^https?://(?:www\.)?simkl\.com/(movies|tv|anime)/(\d+)(?:/([^/?#\s]+))?(?:/season-(\d+)(?:/episode-(\d+))?)?""",
        RegexOption.IGNORE_CASE,
    )
    private val IMDB = Regex(""""imdb"\s*:\s*"(tt\d{5,})"""")
    private val ANIME_TYPE = Regex(""""anime_type"\s*:\s*"([^"]*)"""")

    /** "https://simkl.com/tv/17465/game-of-thrones/season-1/episode-4" → tv, 17465, game-of-thrones, 1, 4. */
    fun parse(link: String): Ref? {
        val m = LINK.find(link.trim()) ?: return null
        val (type, id, slug, season, episode) = m.destructured
        val t = type.lowercase()
        val series = t != "movies"
        return Ref(t, id, slug.ifEmpty { null }, season.toIntOrNull()?.takeIf { series }, episode.toIntOrNull()?.takeIf { series })
    }

    override fun handles(link: String) = parse(link) != null

    /** From the API's answer for the title: its IMDb id, and whether it's a series (`type` from the link). */
    fun read(json: String, type: String): Pair<String, Boolean>? {
        val id = IMDB.find(json)?.groupValues?.get(1) ?: return null
        val series = when (type) {
            "movies" -> false
            "anime" -> ANIME_TYPE.find(json)?.groupValues?.get(1) != "movie"     // the title's own, before its relations
            else -> true
        }
        return id to series
    }

    override suspend fun resolve(link: String): TitleSource.Found? {
        val ref = parse(link) ?: return null
        val byType = ref.type != "movies"     // an anime movie is only told by the API
        val (id, series) = TitleSources.page("https://api.simkl.com/${ref.type}/${ref.id}")?.let { read(it, ref.type) }
            ?: ref.slug?.let { slug -> TitleSources.byName(slug, byType)?.let { it to byType } }
            ?: return null
        return TitleSource.Found(Title(id, "", series, ref.season ?: 1, ref.episode ?: 1), series && ref.episode != null)
    }

    override fun searchFor(link: String): String? = parse(link)?.slug?.let { TitleSources.nameAndYear(it).first }
}
