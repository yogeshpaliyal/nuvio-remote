package dev.skiff

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A remote connection to the TV, whichever way it goes: the Google TV remote protocol (paired with a code, no
 * developer options: atv.Atv.Remote) or the TV's ADB (TvLink.Remote). Keys are Android's KeyEvent codes.
 */
interface TvKeys : AutoCloseable {
    suspend fun key(code: Int)

    /** Over the remote protocol with a field focused, replaces the field's text; otherwise types it in. */
    suspend fun text(s: String)

    /**
     * The text of the TV's focused field, as the TV tells it; null when it doesn't (ADB, or no field focused). While
     * it's known, [text] replaces the field's text, so the phone's field can mirror it as you type.
     */
    val field: StateFlow<String?> get() = NO_FIELD

    companion object {
        private val NO_FIELD: StateFlow<String?> = MutableStateFlow(null)
    }
}
