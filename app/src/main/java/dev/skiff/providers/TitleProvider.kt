package dev.skiff.providers

import dev.skiff.Title

/**
 * A site whose pages are titles (Trakt…), opened in Nuvio or Stremio like an IMDb page: the provider turns its link
 * into the title's IMDb id, and the episode when the link names one. The rest (the name, art and type from Cinemeta,
 * history, opening it on the TV) is the app's, as for a shared IMDb page.
 *
 * A new site: a file in this package with an object implementing this, and that object in Providers.all.
 */
interface TitleProvider {
    /** "trakt": for telemetry. */
    val id: String

    /** "Trakt": shown to people. */
    val name: String

    /** Whether `link` is one of this site's title pages. Called while typing, on the main thread: no network. */
    fun matches(link: String): Boolean

    /** Off the main thread. The title `link` points at (its name can be left empty: Cinemeta fills it), or null. */
    suspend fun resolve(link: String): Found?

    /** Words to search for the title when resolve finds nothing (the results shown under the box); null: none. */
    fun searchWords(link: String): String? = null

    /** `episode`: the link names the episode, so it opens straight away (no season and episode to pick). */
    data class Found(val title: Title, val episode: Boolean = false)
}
