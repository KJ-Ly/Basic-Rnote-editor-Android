package io.github.kjly.brna.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardShortcutsTest {

    private fun ctrl(char: Char, shift: Boolean = false, keyCode: Int = KeyEvent.KEYCODE_UNKNOWN) =
        KeyboardShortcuts.of(char, keyCode, ctrl = true, shift = shift)

    @Test
    fun `undo and redo as in Rnote, and Ctrl+Y as in Windows programs`() {
        assertEquals(Shortcut.UNDO, ctrl('z'))
        assertEquals(Shortcut.REDO, ctrl('z', shift = true))
        assertEquals(Shortcut.REDO, ctrl('y'))
    }

    @Test
    fun `the key that types z is undo, wherever a German keyboard puts it`() {
        // QWERTZ: the key labelled Z sits where a US keyboard has its Y.
        assertEquals(Shortcut.UNDO, ctrl('z', keyCode = KeyEvent.KEYCODE_Y))
        assertEquals(Shortcut.REDO, ctrl('y', keyCode = KeyEvent.KEYCODE_Z))
    }

    @Test
    fun `zoom keys on a German keyboard, a US one and the number pad`() {
        assertEquals(Shortcut.ZOOM_IN, ctrl('+'))
        assertEquals(Shortcut.ZOOM_IN, ctrl('=', shift = true))
        assertEquals(Shortcut.ZOOM_OUT, ctrl('-'))
        assertEquals(Shortcut.ZOOM_RESET, ctrl('0'))
        assertEquals(Shortcut.ZOOM_IN, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_NUMPAD_ADD, ctrl = true, shift = false))
        assertEquals(Shortcut.ZOOM_OUT, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, ctrl = true, shift = false))
    }

    @Test
    fun `files, printing and importing`() {
        assertEquals(Shortcut.OPEN, ctrl('o'))
        assertEquals(Shortcut.PAGE_OVERVIEW, ctrl('o', shift = true))
        assertEquals(Shortcut.SAVE, ctrl('s'))
        assertEquals(Shortcut.SAVE_AS, ctrl('s', shift = true))
        assertEquals(Shortcut.NEW, ctrl('n'))
        assertEquals(Shortcut.PRINT, ctrl('p'))
        assertEquals(Shortcut.IMPORT, ctrl('i', shift = true))
        assertEquals(Shortcut.CLEAR, ctrl('l'))
    }

    @Test
    fun `Ctrl+Shift+P switches Snap Positions, as in Rnote's canvas menu`() {
        assertEquals(Shortcut.SNAP_POSITIONS, ctrl('p', shift = true))
        assertEquals(Shortcut.PRINT, ctrl('p'))
    }

    @Test
    fun `tabs as in Rnote's tab bar`() {
        assertEquals(Shortcut.NEW, ctrl('t'))
        assertEquals(Shortcut.NEW, ctrl('n'))
        assertEquals(Shortcut.CLOSE_TAB, ctrl('w'))
        assertEquals(Shortcut.NEXT_TAB, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_TAB, ctrl = true, shift = false))
        assertEquals(Shortcut.PREVIOUS_TAB, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_TAB, ctrl = true, shift = true))
        // Tab alone moves the focus, as it always does.
        assertNull(KeyboardShortcuts.of(null, KeyEvent.KEYCODE_TAB, ctrl = false, shift = false))
    }

    @Test
    fun `Ctrl+I alone is left to the text box, where it is italic`() {
        assertNull(ctrl('i'))
        assertNull(ctrl('b'))
    }

    @Test
    fun `selection keys`() {
        assertEquals(Shortcut.COPY, ctrl('c'))
        assertEquals(Shortcut.CUT, ctrl('x'))
        assertEquals(Shortcut.PASTE, ctrl('v'))
        assertEquals(Shortcut.SELECT_ALL, ctrl('a'))
        assertEquals(Shortcut.DUPLICATE, ctrl('d'))
        assertEquals(Shortcut.DELETE_SELECTION, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_FORWARD_DEL, ctrl = false, shift = false))
        assertEquals(Shortcut.DELETE_SELECTION, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_DEL, ctrl = false, shift = false))
        assertEquals(Shortcut.DESELECT, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_ESCAPE, ctrl = false, shift = false))
    }

    @Test
    fun `Ctrl and a number picks a pen, in the pen picker's order`() {
        assertEquals(
            listOf(Shortcut.BRUSH, Shortcut.SHAPER, Shortcut.TYPEWRITER, Shortcut.ERASER, Shortcut.SELECTOR, Shortcut.TOOLS),
            ('1'..'6').map { ctrl(it) }
        )
        assertEquals(Shortcut.ERASER, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_NUMPAD_4, ctrl = true, shift = false))
        assertNull(ctrl('7'))
    }

    @Test
    fun `keys without Ctrl, or with Alt, are left alone`() {
        assertNull(KeyboardShortcuts.of('z', KeyEvent.KEYCODE_Z, ctrl = false, shift = false))
        assertNull(KeyboardShortcuts.of('s', KeyEvent.KEYCODE_S, ctrl = true, shift = false, alt = true))
        assertNull(KeyboardShortcuts.of(null, KeyEvent.KEYCODE_DEL, ctrl = false, shift = true))
    }

    @Test
    fun `only undo, redo and zoom repeat when held`() {
        assertTrue(Shortcut.UNDO.repeats)
        assertTrue(Shortcut.ZOOM_IN.repeats)
        assertFalse(Shortcut.SAVE.repeats)
        assertFalse(Shortcut.PRINT.repeats)
    }

    @Test
    fun `Ctrl+Shift+A and Ctrl+Shift+R add and remove a page, as in Rnote`() {
        assertEquals(Shortcut.ADD_PAGE, ctrl('a', shift = true))
        assertEquals(Shortcut.REMOVE_PAGE, ctrl('r', shift = true))
        // Without Shift they stay what they were.
        assertEquals(Shortcut.SELECT_ALL, ctrl('a'))
        assertNull(ctrl('r'))
    }

    @Test
    fun `F11 switches fullscreen, with no Ctrl`() {
        assertEquals(Shortcut.FULLSCREEN, KeyboardShortcuts.of(null, KeyEvent.KEYCODE_F11, ctrl = false, shift = false))
    }
}
