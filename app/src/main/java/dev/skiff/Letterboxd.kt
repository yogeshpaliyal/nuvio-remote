package dev.skiff

/**
 * Letterboxd films: letterboxd.com/film/dune-part-two/, someone's (…/someone/film/dune-part-two/), and boxd.it short
 * links (the app's shares, which go to the film's page). Films only; the page links to IMDb.
 */
object Letterboxd : PageSource() {
    override val name = "Letterboxd"
    override val key = "letterboxd"

    private val FILM = Regex("""^https?://(?:www\.|m\.)?letterboxd\.com/(?:[^/?#\s]+/)?film/([^/?#\s]+)""", RegexOption.IGNORE_CASE)
    private val SHORT = Regex("""^https?://boxd\.it/([A-Za-z0-9]+)/?(?:[?#]|$)""", RegexOption.IGNORE_CASE)

    override fun ref(link: String): TitleRef? {
        val l = link.trim()
        FILM.find(l)?.let { m ->
            val slug = m.groupValues[1]
            val (words, year) = TitleSources.slugWords(slug)
            return TitleRef("https://letterboxd.com/film/$slug/", series = false, name = words, year = year)
        }
        // A short link names nothing: its page says.
        return SHORT.find(l)?.let { TitleRef("https://boxd.it/${it.groupValues[1]}", series = false) }
    }
}
