package dev.skiff

/** MDBList pages: mdblist.com/movie/2ygqn-hope, mdblist.com/show/…; the page links to IMDb. */
object MdbList : PageSource() {
    override val name = "MDBList"
    override val key = "mdblist"

    private val LINK = Regex("""^https?://(?:www\.)?mdblist\.com/(movie|show)/([a-z0-9]+)(?:-([^/?#\s]+))?""", RegexOption.IGNORE_CASE)

    override fun ref(link: String): TitleRef? {
        val m = LINK.find(link.trim()) ?: return null
        val (type, id, slug) = m.destructured
        val (words, year) = if (slug.isEmpty()) null to null else TitleSources.slugWords(slug)
        return TitleRef(
            page = "https://mdblist.com/${type.lowercase()}/$id" + if (slug.isEmpty()) "" else "-$slug",
            series = type.equals("show", ignoreCase = true),
            name = words,
            year = year,
        )
    }
}
