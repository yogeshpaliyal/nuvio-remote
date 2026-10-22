package dev.skiff

/**
 * MDBList pages: mdblist.com/movie/tt…, /show/tt… (the IMDb id and type in the link, no lookup needed), or
 * /show/3xzbl-carrie (MDBList's own id and the name; its page is behind a browser check, so the name is searched).
 */
object MdbList : TitleSource {
    override val name = "MDBList"
    override val key = "mdblist"

    /** One of `imdbId` (in the link) or `slug` (the name in it: "carrie"). */
    data class Ref(val series: Boolean, val imdbId: String? = null, val slug: String? = null)

    private val LINK = Regex(
        """^https?://(?:www\.)?mdblist\.com/(movie|show)/(?:(tt\d{5,})|[a-z0-9]{5}-([^/?#\s]+))""",
        RegexOption.IGNORE_CASE,
    )

    /** "https://mdblist.com/show/tt0903747" → that show; ".../show/3xzbl-carrie" → "carrie"; null when not MDBList's. */
    fun parse(link: String): Ref? {
        val (type, id, slug) = LINK.find(link.trim())?.destructured ?: return null
        return Ref(type.equals("show", ignoreCase = true), id.ifEmpty { null }, slug.ifEmpty { null })
    }

    override fun handles(link: String) = parse(link) != null

    override suspend fun resolve(link: String): TitleSource.Found? {
        val ref = parse(link) ?: return null
        val id = ref.imdbId ?: ref.slug?.let { TitleSources.byName(it, ref.series) } ?: return null
        return TitleSource.Found(Title(id, "", ref.series))
    }

    override fun searchFor(link: String): String? = parse(link)?.slug?.let { TitleSources.nameAndYear(it).first }
}
