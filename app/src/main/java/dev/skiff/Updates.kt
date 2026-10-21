package dev.skiff

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * Newer versions of Skiff, from where this build is distributed (BuildConfig.STORE): GitHub releases (the github
 * build: the APK, in the browser) or Google Play's in-app updates (the play build; Play doesn't allow other ways).
 * The store-specific part is StoreUpdates, in src/github and src/play.
 */
object Updates {
    /**
     * A newer release: its version ("1.9.0"; blank when the store doesn't say), what's new (Markdown; blank when the
     * store doesn't say), and an id for "Later" (the version, or Play's versionCode).
     */
    data class Release(val version: String, val notes: String, val apkUrl: String?, val pageUrl: String, val id: String = version)

    /** Updates come from Google Play (the play build). */
    val viaPlay: Boolean get() = BuildConfig.STORE == "play"

    private val available = MutableStateFlow<Release?>(null)
    /** The newer release, when there's one; null when up to date or not checked yet. */
    val update: StateFlow<Release?> = available

    internal fun prefs(context: Context) = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    // The release you said "Later" to: the Settings icon's dot stays off for it (Settings still offers it).
    private val laterFor = MutableStateFlow<String?>(null)

    /** Whether to put the dot on the Settings icon: a newer release, not one you said "Later" to. */
    val badge: Flow<Boolean> = combine(available, laterFor) { r, later -> r != null && r.id != later }

    /** "Later" for this release: no dot for it; a newer one brings it back. */
    fun later(context: Context, r: Release) {
        prefs(context).edit().putString("later", r.id).apply()
        laterFor.value = r.id
    }

    /** This build's version: "1.8.0" (a debug build's "-debug" left off). */
    fun current(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().substringBefore('-')
    }.getOrDefault("")

    /** True when `a` is a later version than `b` ("1.10.0" > "1.9.2"). */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /**
     * Off the main thread. Asks the store (`force`, or when the last check is old), and updates [update]. Returns the
     * newer release, or null when up to date. Throws when the store can't be reached and `force` is set.
     */
    suspend fun check(context: Context, force: Boolean = false): Release? {
        laterFor.value = prefs(context).getString("later", null)
        val newer = StoreUpdates.check(context, force)
        available.value = newer
        return newer
    }

    /** How getting the update goes: a download's progress (0–1), installing, or what went wrong. */
    sealed interface Progress {
        data class Downloading(val fraction: Float?) : Progress
        data object Installing : Progress
        data class Problem(val message: String) : Progress
    }

    internal val progressState = MutableStateFlow<Progress?>(null)
    /** Getting the update, while it's under way (null otherwise). */
    val progress: StateFlow<Progress?> = progressState

    /** Gets the update, the store's way: Play's in-app update, or the APK downloaded and installed by Skiff. */
    suspend fun open(context: Context, r: Release) = StoreUpdates.open(context, r)
}
