package io.github.kjly.brna.ui

import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * The S Pen's Air Actions — its button and gestures — reach apps as Page Up, Page Down
 * and Play/Pause, which this app takes as redo and undo. So do the same keys on a
 * keyboard, a presentation clicker or a headset, where they undid a stroke for pressing
 * Page Down or pausing the music. Only the pen's own count now.
 */
object PenRemote {

    /** Whether [keyCode] is one the pen's Air Actions send. */
    fun isPenKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_UP ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE

    /**
     * Whether a key came from the pen: never from a typing keyboard; from a device that
     * calls itself a pen ("S Pen", "sec_e-pen"); or put in by the system rather than sent
     * by a device at all — [deviceId] [KeyCharacterMap.VIRTUAL_KEYBOARD], or no device —
     * which is how Samsung's S Pen service hands its remote's presses on.
     */
    fun isFromPen(deviceName: String?, deviceId: Int, typingKeyboard: Boolean): Boolean {
        if (typingKeyboard) return false
        if (deviceName?.contains("pen", ignoreCase = true) == true) return true
        return deviceName == null || deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD
    }
}
