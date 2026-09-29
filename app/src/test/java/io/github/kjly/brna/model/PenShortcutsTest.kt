package io.github.kjly.brna.model

import io.github.kjly.brna.model.ShortcutKey.KEYBOARD_CTRL_SPACE
import io.github.kjly.brna.model.ShortcutKey.MOUSE_SECONDARY_BUTTON
import io.github.kjly.brna.model.ShortcutKey.STYLUS_PRIMARY_BUTTON
import io.github.kjly.brna.model.ShortcutKey.STYLUS_SECONDARY_BUTTON
import io.github.kjly.brna.model.ShortcutKey.TOUCH_TWO_FINGER_LONG_PRESS
import io.github.kjly.brna.model.ToolType.BRUSH
import io.github.kjly.brna.model.ToolType.ERASER
import io.github.kjly.brna.model.ToolType.SELECTOR
import io.github.kjly.brna.model.ToolType.SHAPER
import io.github.kjly.brna.model.ToolType.TOOLS
import io.github.kjly.brna.model.ToolType.TYPEWRITER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rnote's button shortcuts: its defaults, and what `PenHolder::handle_pressed_shortcut_key`
 * does with each mode — played through [PenShortcutState] with the pen the app would have.
 */
class PenShortcutsTest {

    /** The app's pen, switched as the state says. */
    private class Pen(var tool: ToolType = BRUSH, val shortcuts: PenShortcuts = PenShortcuts()) {
        val state = PenShortcutState()
        fun press(key: ShortcutKey) { state.press(key, shortcuts, tool)?.let { tool = it } }
        fun release(key: ShortcutKey, busy: Boolean = false) { state.release(key, busy)?.let { tool = it } }
        fun gestureEnded(busy: Boolean = false) { state.gestureEnded(busy)?.let { tool = it } }
        fun settle(busy: Boolean = false) { state.settle(busy)?.let { tool = it } }
        fun pick(new: ToolType) { state.picked(); tool = new }
    }

    private fun with(key: ShortcutKey, tool: ToolType, mode: ShortcutMode) =
        PenShortcuts().with(key, ShortcutAction(tool, mode))

    @Test
    fun `Rnote's defaults`() {
        val defaults = PenShortcuts()
        assertEquals(ShortcutAction(ERASER, ShortcutMode.TEMPORARY), defaults[STYLUS_PRIMARY_BUTTON])
        assertEquals(ShortcutAction(SELECTOR, ShortcutMode.TEMPORARY), defaults[STYLUS_SECONDARY_BUTTON])
        assertEquals(ShortcutAction(SHAPER, ShortcutMode.TEMPORARY), defaults[MOUSE_SECONDARY_BUTTON])
        assertEquals(ShortcutAction(ERASER, ShortcutMode.TOGGLE), defaults[TOUCH_TWO_FINGER_LONG_PRESS])
        assertEquals(ShortcutAction(TOOLS, ShortcutMode.TOGGLE), defaults[KEYBOARD_CTRL_SPACE])
    }

    @Test
    fun `settings are kept in Rnote's names and read back`() {
        val shortcuts = with(STYLUS_PRIMARY_BUTTON, TYPEWRITER, ShortcutMode.PERMANENT)
            .with(KEYBOARD_CTRL_SPACE, ShortcutAction(BRUSH, ShortcutMode.DISABLED))
        val text = shortcuts.encode()
        assertEquals(
            "stylus_primary_button=typewriter:permanent\n" +
                "stylus_secondary_button=selector:temporary\n" +
                "mouse_secondary_button=shaper:temporary\n" +
                "touch_two_finger_long_press=eraser:toggle\n" +
                "keyboard_ctrl_space=brush:disabled",
            text
        )
        assertEquals(shortcuts, PenShortcuts.decode(text))
    }

    @Test
    fun `what can't be read keeps the default`() {
        assertEquals(PenShortcuts(), PenShortcuts.decode(null))
        val read = PenShortcuts.decode("stylus_primary_button=pencil:temporary\ndrawing_pad_button_0=brush:permanent\nnonsense")
        assertEquals(PenShortcuts(), read)
        val one = PenShortcuts.decode("mouse_secondary_button=eraser:toggle")
        assertEquals(ShortcutAction(ERASER, ShortcutMode.TOGGLE), one[MOUSE_SECONDARY_BUTTON])
        assertEquals(PenShortcuts.DEFAULTS[STYLUS_PRIMARY_BUTTON], one[STYLUS_PRIMARY_BUTTON])
    }

    @Test
    fun `temporary is the pen while the button is held`() {
        val pen = Pen()
        pen.press(STYLUS_PRIMARY_BUTTON)
        assertEquals(ERASER, pen.tool)
        // Erasing with it held: still the eraser afterwards.
        pen.gestureEnded()
        assertEquals(ERASER, pen.tool)
        pen.release(STYLUS_PRIMARY_BUTTON)
        assertEquals(BRUSH, pen.tool)
        assertNull(pen.state.underlying)
    }

    @Test
    fun `a temporary selector stays until its selection is let go`() {
        val pen = Pen(TYPEWRITER)
        pen.press(STYLUS_SECONDARY_BUTTON)
        assertEquals(SELECTOR, pen.tool)
        // The lasso drawn, the button let go: the selection is there to move with the pen tip.
        pen.gestureEnded(busy = true)
        pen.release(STYLUS_SECONDARY_BUTTON, busy = true)
        assertEquals(SELECTOR, pen.tool)
        // Tapped away: back to the pen it was.
        pen.settle(busy = false)
        assertEquals(TYPEWRITER, pen.tool)
    }

    @Test
    fun `a momentary shortcut's temporary pen lasts for one thing drawn`() {
        val pen = Pen(shortcuts = with(TOUCH_TWO_FINGER_LONG_PRESS, SHAPER, ShortcutMode.TEMPORARY))
        pen.press(TOUCH_TWO_FINGER_LONG_PRESS)
        assertEquals(SHAPER, pen.tool)
        // Nothing held that could come up, and nothing drawn yet.
        pen.settle()
        assertEquals(SHAPER, pen.tool)
        pen.gestureEnded()
        assertEquals(BRUSH, pen.tool)
    }

    @Test
    fun `permanent switches for good`() {
        val pen = Pen(shortcuts = with(STYLUS_PRIMARY_BUTTON, SHAPER, ShortcutMode.PERMANENT))
        pen.press(STYLUS_PRIMARY_BUTTON)
        pen.release(STYLUS_PRIMARY_BUTTON)
        pen.gestureEnded()
        assertEquals(SHAPER, pen.tool)
    }

    @Test
    fun `toggle switches, and the same button switches back`() {
        val pen = Pen()
        pen.press(TOUCH_TWO_FINGER_LONG_PRESS)
        assertEquals(ERASER, pen.tool)
        pen.gestureEnded()
        assertEquals(ERASER, pen.tool)
        pen.press(TOUCH_TWO_FINGER_LONG_PRESS)
        assertEquals(BRUSH, pen.tool)
    }

    @Test
    fun `another toggle button goes on to its own pen, not back`() {
        val pen = Pen()
        pen.press(TOUCH_TWO_FINGER_LONG_PRESS)
        pen.press(KEYBOARD_CTRL_SPACE)
        pen.release(KEYBOARD_CTRL_SPACE)
        assertEquals(TOOLS, pen.tool)
        // Then that one again: back to the pen before the first toggle, as in Rnote.
        pen.press(KEYBOARD_CTRL_SPACE)
        assertEquals(BRUSH, pen.tool)
    }

    @Test
    fun `a pen picked by hand ends a toggle`() {
        val pen = Pen()
        pen.press(TOUCH_TWO_FINGER_LONG_PRESS)
        pen.pick(SELECTOR)
        pen.press(TOUCH_TWO_FINGER_LONG_PRESS)
        // Not back to the brush: toggled afresh from the selector.
        assertEquals(ERASER, pen.tool)
        pen.press(TOUCH_TWO_FINGER_LONG_PRESS)
        assertEquals(SELECTOR, pen.tool)
    }

    @Test
    fun `a pen picked by hand takes a temporary pen off for good`() {
        val pen = Pen()
        pen.press(STYLUS_PRIMARY_BUTTON)
        pen.pick(TYPEWRITER)
        pen.release(STYLUS_PRIMARY_BUTTON)
        assertEquals(TYPEWRITER, pen.tool)
    }

    @Test
    fun `a permanent pen chosen under a temporary one comes out when that is done`() {
        val pen = Pen(shortcuts = with(STYLUS_SECONDARY_BUTTON, SHAPER, ShortcutMode.PERMANENT))
        pen.press(STYLUS_PRIMARY_BUTTON)
        pen.press(STYLUS_SECONDARY_BUTTON)
        assertEquals(ERASER, pen.tool)
        pen.release(STYLUS_SECONDARY_BUTTON)
        pen.release(STYLUS_PRIMARY_BUTTON)
        assertEquals(SHAPER, pen.tool)
    }

    @Test
    fun `disabled does nothing`() {
        val pen = Pen(shortcuts = with(STYLUS_PRIMARY_BUTTON, ERASER, ShortcutMode.DISABLED))
        pen.press(STYLUS_PRIMARY_BUTTON)
        assertEquals(BRUSH, pen.tool)
        pen.release(STYLUS_PRIMARY_BUTTON)
        assertEquals(BRUSH, pen.tool)
    }
}
