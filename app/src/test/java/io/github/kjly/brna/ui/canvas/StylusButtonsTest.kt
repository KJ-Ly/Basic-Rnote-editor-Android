package io.github.kjly.brna.ui.canvas

import android.view.MotionEvent
import io.github.kjly.brna.model.ShortcutKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** The buttons of a pointer event, as Rnote's shortcut keys (see [StylusButtons]). */
class StylusButtonsTest {

    private val stylus = MotionEvent.TOOL_TYPE_STYLUS

    @Test
    fun `the pen's barrel button is its primary, whichever bit it sets`() {
        val primary = setOf(ShortcutKey.STYLUS_PRIMARY_BUTTON)
        assertEquals(primary, StylusButtons.keysOf(stylus, MotionEvent.BUTTON_STYLUS_PRIMARY))
        // As GTK reads it, and as some pens report it.
        assertEquals(primary, StylusButtons.keysOf(stylus, MotionEvent.BUTTON_SECONDARY))
        assertEquals(primary, StylusButtons.keysOf(MotionEvent.TOOL_TYPE_ERASER, MotionEvent.BUTTON_STYLUS_PRIMARY))
    }

    @Test
    fun `the pen's second button is its secondary`() {
        val secondary = setOf(ShortcutKey.STYLUS_SECONDARY_BUTTON)
        assertEquals(secondary, StylusButtons.keysOf(stylus, MotionEvent.BUTTON_STYLUS_SECONDARY))
        assertEquals(secondary, StylusButtons.keysOf(stylus, MotionEvent.BUTTON_TERTIARY))
        assertEquals(
            setOf(ShortcutKey.STYLUS_PRIMARY_BUTTON, ShortcutKey.STYLUS_SECONDARY_BUTTON),
            StylusButtons.keysOf(stylus, MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)
        )
    }

    @Test
    fun `a mouse has its right button, and its left is just drawing`() {
        assertEquals(
            setOf(ShortcutKey.MOUSE_SECONDARY_BUTTON),
            StylusButtons.keysOf(MotionEvent.TOOL_TYPE_MOUSE, MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY)
        )
        assertEquals(emptySet<ShortcutKey>(), StylusButtons.keysOf(MotionEvent.TOOL_TYPE_MOUSE, MotionEvent.BUTTON_PRIMARY))
    }

    @Test
    fun `a finger has no buttons`() {
        assertEquals(emptySet<ShortcutKey>(), StylusButtons.keysOf(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.BUTTON_SECONDARY))
        assertEquals(emptySet<ShortcutKey>(), StylusButtons.keysOf(stylus, 0))
    }
}
