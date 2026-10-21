package dev.nuvioremote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Every title you've used (shared, pasted or played), and the ones saved for later. One entry per title: using it
 * again moves it to the top and keeps its latest season and episode. Stored on the phone, in a small JSON file.
 * Main thread only (the file is tiny).
 */
class Library private constructor(context: Context) {
    data class Entry(
        val imdbId: String,
        val name: String,
        val series: Boolean,
        val season: Int = 1,
        val episode: Int = 1,
        val poster: String? = null,
        val year: String? = null,
        val saved: Boolean = false,
        val inHistory: Boolean = true,      // false: only saved for later (removed from history, or never used)
        val plays: Int = 0,
        val lastUsed: Long = System.currentTimeMillis(),
    ) {
        fun title() = Title(imdbId, name, series, season, episode)
    }

    private val file = File(context.filesDir, "library.json")
    private val state = MutableStateFlow(load())
    val entries: StateFlow<List<Entry>> = state

    /** A title used: shared, pasted or looked up (`played` false), or opened on the TV (true). */
    fun use(t: Title, meta: Meta.Found?, played: Boolean) {
        val old = find(t.imdbId)
        val entry = (old ?: Entry(t.imdbId, t.name, t.series)).copy(
            name = t.name.ifBlank { old?.name.orEmpty() },
            series = t.series,
            season = if (t.series) t.season else 1,
            episode = if (t.series) t.episode else 1,
            poster = meta?.poster ?: old?.poster,
            year = meta?.year ?: old?.year,
            inHistory = true,
            plays = (old?.plays ?: 0) + if (played) 1 else 0,
            lastUsed = System.currentTimeMillis(),
        )
        put(entry)
    }

    /** Saved for later, or not; a title not in the library yet comes in saved (not in history). */
    fun setSaved(t: Title, meta: Meta.Found?, saved: Boolean) {
        val old = find(t.imdbId)
        put(
            (old ?: Entry(t.imdbId, t.name, t.series, inHistory = false, poster = meta?.poster, year = meta?.year))
                .copy(saved = saved),
            keepTime = old != null,
        )
    }

    /** Changed by hand (name, type, season, episode). */
    fun edit(entry: Entry) = put(entry, keepTime = true)

    /** Out of history; still in Saved when saved (and gone when not). */
    fun removeFromHistory(imdbId: String) {
        val old = find(imdbId) ?: return
        if (old.saved) put(old.copy(inHistory = false), keepTime = true) else delete(imdbId)
    }

    /** Back as it was (Undo). */
    fun restore(entry: Entry) = put(entry, keepTime = true)

    fun find(imdbId: String): Entry? = state.value.firstOrNull { it.imdbId == imdbId }

    private fun put(entry: Entry, keepTime: Boolean = false) {
        val e = if (keepTime) entry else entry.copy(lastUsed = System.currentTimeMillis())
        state.value = (listOf(e) + state.value.filter { it.imdbId != e.imdbId })
            .filter { it.inHistory || it.saved }
            .sortedByDescending { it.lastUsed }
            .take(MAX)
        save()
    }

    private fun delete(imdbId: String) {
        state.value = state.value.filter { it.imdbId != imdbId }
        save()
    }

    private fun load(): List<Entry> = runCatching {
        val a = JSONArray(file.readText())
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Entry(
                imdbId = o.getString("imdbId"),
                name = o.optString("name"),
                series = o.optBoolean("series"),
                season = o.optInt("season", 1),
                episode = o.optInt("episode", 1),
                poster = o.optString("poster").takeIf { it.isNotEmpty() },
                year = o.optString("year").takeIf { it.isNotEmpty() },
                saved = o.optBoolean("saved"),
                inHistory = o.optBoolean("inHistory", true),
                plays = o.optInt("plays"),
                lastUsed = o.optLong("lastUsed"),
            )
        }.sortedByDescending { it.lastUsed }
    }.getOrDefault(emptyList())

    /** Written to a temporary file, then renamed: never half a file. */
    private fun save() {
        val a = JSONArray()
        for (e in state.value) {
            a.put(
                JSONObject().put("imdbId", e.imdbId).put("name", e.name).put("series", e.series)
                    .put("season", e.season).put("episode", e.episode).put("poster", e.poster ?: "")
                    .put("year", e.year ?: "").put("saved", e.saved).put("inHistory", e.inHistory)
                    .put("plays", e.plays).put("lastUsed", e.lastUsed),
            )
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }

    companion object {
        private const val MAX = 300
        @Volatile private var instance: Library? = null

        fun get(context: Context): Library =
            instance ?: synchronized(this) { instance ?: Library(context.applicationContext).also { instance = it } }
    }
}
