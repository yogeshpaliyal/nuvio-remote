package dev.skiff

/**
 * MDBList pages: mdblist.com/movie/tt…, /show/tt… (the IMDb id and the type are in the link, no lookup needed), or
 * with MDBList's own id, as the site links them (mdblist.com/show/3xzbl-carrie): the page links to IMDb, else the name
 * in the link is searched.
 */
object MdbList : TitleSource {
    override val name = "MDBList"
    override val key = "mdblist"

    private val LINK = Regex("""^https?://(?:www\.)?mdblist\.com/(movie|show)/(tt\d{5,})""", RegexOption.IGNORE_CASE)
    private val OWN_ID = Regex("""^https?://(?:www\.)?mdblist\.com/(movie|show)/(\d[a-z0-9]{3,7})(?:-([^/?#\s]+))?(?:[/?#]|$)""", RegexOption.IGNORE_CASE)

    /** A link with MDBList's own id: its page, movie or show, and the slug ("carrie"; empty when the link has none). */
    data class Ref(val page: String, val series: Boolean, val slug: String)

    /** "https://mdblist.com/show/tt0903747" → that show; null when not MDBList's, or the link has MDBList's id. */
    fun parse(link: String): Title? {
        val (type, id) = LINK.find(link.trim())?.destructured ?: return null
        return Title(id, "", series = type.equals("show", ignoreCase = true))
    }

    /** "https://mdblist.com/show/3xzbl-carrie" → its page, a show, "carrie"; null for an IMDb id's link or another page. */
    fun ref(link: String): Ref? {
        if (parse(link) != null) return null
        val (type, id, slug) = OWN_ID.find(link.trim())?.destructured ?: return null
        val t = type.lowercase()
        return Ref("https://mdblist.com/$t/$id" + if (slug.isEmpty()) "" else "-$slug", t == "show", slug)
    }

    override fun handles(link: String) = parse(link) != null || ref(link) != null

    override suspend fun resolve(link: String): TitleSource.Found? {
        parse(link)?.let { return TitleSource.Found(it) }
        val ref = ref(link) ?: return null
        val id = TitleSources.page(ref.page)?.let(TitleSources::imdbLinkIn)
            ?: ref.slug.takeIf { it.isNotEmpty() }?.let { TitleSources.byName(it, ref.series) }
            ?: return null
        return TitleSource.Found(Title(id, "", ref.series))
    }

    override fun searchFor(link: String): String? =
        ref(link)?.slug?.takeIf { it.isNotEmpty() }?.let { TitleSources.nameAndYear(it).first }
}
