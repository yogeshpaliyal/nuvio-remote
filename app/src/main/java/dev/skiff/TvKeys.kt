package dev.skiff

/**
 * A remote connection to the TV, whichever way it goes: the Google TV remote protocol (paired with a code, no
 * developer options: atv.Atv.Remote) or the TV's ADB (TvLink.Remote). Keys are Android's KeyEvent codes.
 */
interface TvKeys : AutoCloseable {
    suspend fun key(code: Int)
    suspend fun text(s: String)
}
