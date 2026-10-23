package dev.skiff

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.res.stringResource
import org.json.JSONArray
import org.json.JSONObject

/**
 * What Skiff sent to the TV, and how (Settings → Activity): the last [MAX], newest first, kept across launches.
 * Main thread only (it's Compose state).
 */
object ActivityLog {
    private const val MAX = 50

    /** `via`: "Pairing" or "USB debugging". `ok`: false when the TV couldn't be reached or said no. */
    data class Entry(val time: Long, val what: String, val via: String, val ok: Boolean)

    private val list = mutableStateListOf<Entry>()
    private var loaded = false

    fun entries(context: Context): List<Entry> {
        load(context)
        return list
    }

    fun add(context: Context, what: String, via: String, ok: Boolean = true) {
        load(context)
        list.add(0, Entry(System.currentTimeMillis(), what, via, ok))
        while (list.size > MAX) list.removeAt(list.lastIndex)
        save(context)
    }

    fun clear(context: Context) {
        list.clear()
        save(context)
    }

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val json = prefs(context).getString("entries", null) ?: return
        runCatching {
            val a = JSONArray(json)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list.add(Entry(o.getLong("t"), o.getString("w"), o.getString("v"), o.optBoolean("ok", true)))
            }
        }
    }

    private fun save(context: Context) {
        val a = JSONArray()
        for (e in list) a.put(JSONObject().put("t", e.time).put("w", e.what).put("v", e.via).put("ok", e.ok))
        prefs(context).edit().putString("entries", a.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("activity", Context.MODE_PRIVATE)
}

/** An entry's `via` in the app's language (it's kept as "Pairing" or "USB debugging"). */
@Composable
fun viaLabel(via: String): String = when (via) {
    "Pairing" -> stringResource(R.string.via_pairing)
    "USB debugging" -> stringResource(R.string.via_adb)
    else -> via
}
