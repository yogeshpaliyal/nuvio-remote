package dev.skiff

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

object TitleSources {
    val all: List<TitleSource> = listOf(Trakt)

    /** The site `link` is a title page of; null for IMDb's (read directly) and any other link. */
    fun of(link: String): TitleSource? = all.firstOrNull { it.handles(link.trim()) }
}
