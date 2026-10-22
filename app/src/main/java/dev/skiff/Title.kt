package dev.skiff

/** What to open in Nuvio on the TV: a movie, or one episode of a series. */
data class Title(
    val imdbId: String,
    val name: String,
    val series: Boolean,
    val season: Int = 1,
    val episode: Int = 1,
) {
    /** Nuvio's own id for the video: "tt0120737" for a movie, "tt42127457:1:4" for an episode. */
    val videoId: String get() = if (series) "$imdbId:$season:$episode" else imdbId

    /**
     * Stremio's link for it: `streams`, its stream list ("stremio:///detail/series/tt42127457/tt42127457:1:4"); else
     * the title's page.
     */
    fun stremioLink(streams: Boolean): String =
        "stremio:///detail/${if (series) "series" else "movie"}/$imdbId" + if (streams) "/$videoId" else ""

    /**
     * A shared page: its IMDb id, and whatever its text says about the name and type (null = not said). Or, with no
     * IMDb id, any other `link` (JioHotstar, YouTube…), with imdbId empty.
     */
    data class Shared(val imdbId: String, val name: String, val series: Boolean?, val link: String? = null)

    companion object {
        private val IMDB_ID = Regex("""\btt\d{5,}\b""")

        fun isImdbId(s: String) = IMDB_ID.matches(s.trim())

        /** The IMDb id in any text: "tt0120737", "https://m.imdb.com/title/tt0120737/?ref_=…". */
        fun idIn(text: String): String? = IMDB_ID.find(text)?.value

        /**
         * What a share brings. Chrome sends the page's URL as the text and its title as the subject
         * ("The Lord of the Rings: The Fellowship of the Ring (2001) - IMDb"); the IMDb app puts both in the text
         * ("Agent Kim Reactivated (TV Series 2025– ) ⭐ 7.6 | Action https://m.imdb.com/title/tt42127457/").
         * With no IMDb id, any other link in it; null when there's neither.
         */
        fun fromShared(text: String?, subject: String?): Shared? {
            val all = listOfNotNull(subject, text).joinToString("\n")
            // Another site's title page (TitleSources), even one with the IMDb id in it (MDBList's): looked up.
            val link = Links.linkIn(all)
            val shared = { l: String -> Shared("", subject?.lines()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty(), null, l) }
            if (link != null && TitleSources.of(link) != null) return shared(link)
            val id = idIn(all) ?: return link?.let(shared)
            val name = listOfNotNull(subject, text)
                .flatMap { it.lines() }
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("http") && idIn(it) == null }
                ?.let(::cleanName)
                .orEmpty()
            // The IMDb app says "TV Series"; Chrome's page title doesn't say (Cinemeta will).
            val series = if (Regex("""TV (Mini )?Series""", RegexOption.IGNORE_CASE).containsMatchIn(all)) true else null
            return Shared(id, name, series)
        }

        /** "The Lord of the Rings… (2001) - IMDb" → "The Lord of the Rings…". */
        private fun cleanName(line: String): String = line
            .substringBefore("http").substringBefore(" - IMDb").substringBefore(" | ").substringBefore(" (")
            .substringBefore(" ⭐").trim().trim('"', '“', '”')
    }
}
