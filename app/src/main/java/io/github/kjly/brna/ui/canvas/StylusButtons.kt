package io.github.kjly.brna.ui.canvas

import android.view.MotionEvent
import io.github.kjly.brna.model.ShortcutKey

/**
 * Which of Rnote's button shortcuts are held in a pointer event, as rnote-ui's
 * `retrieve_button_shortcut_key` reads GTK's buttons: a pen's second button is its
 * primary barrel button, its middle one the secondary; a mouse's second is its own.
 *
 * Android names the pen's buttons for what they are, but some pens report the barrel
 * button as a mouse's second, as GTK sees it — and Samsung's S Pen has reported either —
 * so each of the pen's two keys takes both bits.
 */
internal object StylusButtons {

    private const val PRIMARY = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY
    private const val SECONDARY = MotionEvent.BUTTON_STYLUS_SECONDARY or MotionEvent.BUTTON_TERTIARY

    fun keysOf(toolType: Int, buttonState: Int): Set<ShortcutKey> = when (toolType) {
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> buildSet {
            if (buttonState and PRIMARY != 0) add(ShortcutKey.STYLUS_PRIMARY_BUTTON)
            if (buttonState and SECONDARY != 0) add(ShortcutKey.STYLUS_SECONDARY_BUTTON)
        }
        MotionEvent.TOOL_TYPE_MOUSE ->
            if (buttonState and MotionEvent.BUTTON_SECONDARY != 0) setOf(ShortcutKey.MOUSE_SECONDARY_BUTTON) else emptySet()
        else -> emptySet()
    }
}
