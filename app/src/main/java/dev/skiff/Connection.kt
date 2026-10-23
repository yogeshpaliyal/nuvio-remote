package dev.skiff

import android.content.Context
import androidx.annotation.StringRes

/**
 * How each TV is reached, for the remote and for links (Settings): Automatic (the TV's ADB when this phone is allowed,
 * else paired: the Google TV remote protocol), Pairing only, or USB debugging (ADB) only. IMDb titles follow the links' choice
 * (over pairing, Nuvio's own link: nuvio://movie/tt…).
 */
object Connection {
    enum class Via(@param:StringRes val label: Int) { AUTO(R.string.via_automatic), PAIRING(R.string.via_pairing), ADB(R.string.via_adb) }

    fun remote(context: Context, host: String) = get(context, "remote:", host)
    fun setRemote(context: Context, host: String, via: Via) = set(context, "remote:", host, via)
    fun link(context: Context, host: String) = get(context, "link:", host)
    fun setLink(context: Context, host: String, via: Via) = set(context, "link:", host, via)

    private fun get(context: Context, kind: String, host: String): Via =
        prefs(context).getString(kind + host.trim(), null)?.let { runCatching { Via.valueOf(it) }.getOrNull() } ?: Via.AUTO

    private fun set(context: Context, kind: String, host: String, via: Via) =
        prefs(context).edit().putString(kind + host.trim(), via.name).apply()

    private fun prefs(context: Context) = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
}
