package dev.skiff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A site whose links are titles but don't carry the IMDb id (Trakt…): looked up, then opened in Nuvio or Stremio like
 * a shared IMDb page. A new site is a `TitleSource` in its own file and a line in [TitleSources.all]; the remote,
 * shares, pastes and the search box take any of them.
 */
interface TitleSource {
    /** Its name, as the app says it ("Trakt"). */
    val name: String

    /** For analytics: "trakt". */
    val key: String

    /** Whether `link` is one of its title pages. */
    fun handles(link: String): Boolean

    /** Off the main thread. The title `link` points at, or null when it can't be found (no internet, no IMDb id). */
    suspend fun resolve(link: String): Found?

    /** Not found: what to search Cinemeta for instead (the name in the link), or null. */
    fun searchFor(link: String): String? = null

    /** `episodeKnown`: the link named the episode, so it plays without asking for one. */
    data class Found(val title: Title, val episodeKnown: Boolean = false)
}

/**
 * What a site's link says: the page to read (it links to the title on IMDb), movie or series, the episode, and the
 * name (and year) in the link, to search when the page doesn't say. `name` null: the page's own title is searched.
 */
data class TitleRef(
    val page: String,
    val series: Boolean,
    val season: Int? = null,
    val episode: Int? = null,
    val name: String? = null,
    val year: String? = null,
)

/**
 * Most sites: the title's page links to IMDb. Else its name is searched in Cinemeta: the one in the link, or the
 * page's title. A site only has to read its links (`ref`).
 */
abstract class PageSource : TitleSource {
    /** What `link` points at, or null when it isn't one of this site's title pages. No network: called while typing. */
    abstract fun ref(link: String): TitleRef?

    override fun handles(link: String) = ref(link) != null

    override fun searchFor(link: String): String? = ref(link)?.name?.takeIf { it.isNotBlank() }

    override suspend fun resolve(link: String): TitleSource.Found? {
        val ref = ref(link) ?: return null
        val html = withContext(Dispatchers.IO) { runCatching { Links.get(ref.page, limit = 600_000) }.getOrNull() }
        val id = html?.let(TitleSources::imdbIdIn)
            ?: (ref.name?.let { it to ref.year } ?: html?.let(TitleSources::titleIn))
                ?.let { (n, y) -> TitleSources.search(n, ref.series, y) }
            ?: return null
        return TitleSource.Found(Title(id, "", ref.series, ref.season ?: 1, ref.episode ?: 1), ref.episode != null)
    }
}

object TitleSources {
    val all: List<TitleSource> = listOf(Trakt, Simkl, Letterboxd, MdbList, WeTrakr)

    /** The site `link` is a title page of; null for IMDb's (read directly) and any other link. */
    fun of(link: String): TitleSource? = all.firstOrNull { it.handles(link.trim()) }

    private val IMDB_LINK = Regex("""imdb\.com/title/(tt\d{5,})""")
    private val SLUG_YEAR = Regex("""-((?:19|20)\d\d)$""")
    private val TITLE_YEAR = Regex("""^(.+?)\s*\(((?:19|20)\d\d)\)""")
    private val OG_TITLE = Regex("""<meta[^>]+property=["']og:title["'][^>]*content=(?:"([^"]+)"|'([^']+)')""", RegexOption.IGNORE_CASE)
    private val HTML_TITLE = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)

    /** The first IMDb title link in a page ("https://www.imdb.com/title/tt0120737/" → "tt0120737"). */
    fun imdbIdIn(html: String): String? = IMDB_LINK.find(html)?.groupValues?.get(1)

    /** "dune-part-two-2024" → "dune part two", "2024"; "breaking-bad" → "breaking bad", null. */
    fun slugWords(slug: String): Pair<String, String?> {
        val year = SLUG_YEAR.find(slug)?.groupValues?.get(1)
        return (if (year != null) slug.removeSuffix("-$year") else slug).replace('-', ' ').trim() to year
    }

    /**
     * A page's own name for the title, and its year: "Under the Grey Sky (2024) · WeTrakr" → "Under the Grey Sky",
     * "2024". From og:title, else <title>; the site's name after " · ", " | " or " • " left out.
     */
    fun titleIn(html: String): Pair<String, String?>? {
        val raw = OG_TITLE.find(html)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
            ?: HTML_TITLE.find(html)?.groupValues?.get(1) ?: return null
        val text = raw.replace("&amp;", "&").replace("&#39;", "'").replace("&#039;", "'").replace("&quot;", "\"")
            .replace("\u200E", "").split(" · ", " | ", " • ").first().trim()
        val m = TITLE_YEAR.find(text)
        val (name, year) = if (m != null) m.groupValues[1] to m.groupValues[2] else text to null
        return name.trim().takeIf { it.isNotEmpty() }?.let { it to year }
    }

    /** Off the main thread. A title by name in Cinemeta: the first of the right type, of `year` when given. */
    suspend fun search(name: String, series: Boolean, year: String? = null): String? {
        if (name.isBlank()) return null
        val hits = Meta.search(name, limit = 20).filter { it.series == series }
        return (hits.firstOrNull { year != null && it.year?.startsWith(year) == true } ?: hits.firstOrNull())?.imdbId
    }
}
