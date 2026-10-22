package dev.skiff

/** MDBList pages (mdblist.com/movie/tt…, /show/tt…): the IMDb id and the type are in the link, no lookup needed. */
object MdbList : TitleSource {
    override val name = "MDBList"
    override val key = "mdblist"

    private val LINK = Regex("""^https?://(?:www\.)?mdblist\.com/(movie|show)/(tt\d{5,})""", RegexOption.IGNORE_CASE)

    /** "https://mdblist.com/show/tt0903747" → that show; null when not MDBList's. */
    fun parse(link: String): Title? {
        val (type, id) = LINK.find(link.trim())?.destructured ?: return null
        return Title(id, "", series = type.equals("show", ignoreCase = true))
    }

    override fun handles(link: String) = parse(link) != null

    override suspend fun resolve(link: String): TitleSource.Found? = parse(link)?.let { TitleSource.Found(it) }
}
