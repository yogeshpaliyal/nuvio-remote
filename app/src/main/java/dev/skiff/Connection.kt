package dev.skiff

import android.content.Context

/**
 * How each TV is reached, for the remote and for links (Settings): Automatic (paired: the Google TV remote protocol,
 * else the TV's ADB), Pairing only, or USB debugging (ADB) only. IMDb titles follow the links' choice
 * (over pairing, Nuvio's own link: nuvio://movie/tt…).
 */
object Connection {
    enum class Via(val label: String) { AUTO("Automatic"), PAIRING("Pairing"), ADB("USB debugging") }

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
