package dev.skiff

import android.view.KeyEvent

/**
 * The phone's volume buttons, for the TV while the remote is open and connected (the remote sets [volume]; null:
 * they're the phone's again). MainActivity hands them over before Android turns the phone's volume.
 */
object HardwareKeys {
    /** Called for each press of volume up or down (held: repeated); returns true when it was used. */
    var volume: ((code: Int) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val handler = volume ?: return false
        // The press (and its repeats) goes to the TV; the release is swallowed with it.
        return if (event.action == KeyEvent.ACTION_DOWN) handler(code) else true
    }
}
