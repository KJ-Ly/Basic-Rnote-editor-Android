package io.github.kjly.brna.ui

import android.view.KeyEvent

/** What a keyboard shortcut does; see [KeyboardShortcuts]. */
enum class Shortcut {
    OPEN, SAVE, SAVE_AS, NEW, PRINT, IMPORT, CLEAR, PAGE_OVERVIEW, SNAP_POSITIONS,
    ADD_PAGE, REMOVE_PAGE, FULLSCREEN,
    CLOSE_TAB, NEXT_TAB, PREVIOUS_TAB,
    UNDO, REDO,
    COPY, CUT, PASTE, SELECT_ALL, DUPLICATE, DELETE_SELECTION, DESELECT,
    ZOOM_IN, ZOOM_OUT, ZOOM_RESET,
    BRUSH, SHAPER, TYPEWRITER, ERASER, SELECTOR, TOOLS,
    COLOR_1, COLOR_2, COLOR_3, COLOR_4, COLOR_5, COLOR_6, COLOR_7, COLOR_8, COLOR_9;

    /** Held down, these repeat — undoing step after step, zooming on — as GTK's accelerators do. */
    val repeats: Boolean get() = this == UNDO || this == REDO || this == ZOOM_IN || this == ZOOM_OUT

    /** Which of the color picker's swatches this picks, 0 to 8, or null for any other shortcut. */
    val colorSlot: Int? get() = (ordinal - COLOR_1.ordinal).takeIf { it in 0..8 }
}

/**
 * Desktop Rnote's keyboard shortcuts, for a keyboard on the tablet: the accelerators in
 * rnote-ui's `appwindow/actions.rs` (F11 among them, for Fullscreen), the selector's own
 * keys (Delete, Escape, Ctrl+A, Ctrl+D) and its tab bar's Ctrl+Tab. Ctrl+Y redoes as
 * well, as it does in most Windows programs, and Ctrl+N opens a new tab as Ctrl+T does —
 * a new window, which is Ctrl+N in Rnote, is a new tab here. The digits 1 to 9 pick the
 * color picker's swatches, as they do since Rnote 0.15. A text box being typed into
 * takes its own keys first — Ctrl+C there copies text, a 1 there is a 1.
 */
object KeyboardShortcuts {

    /**
     * The shortcut for a key press, or null. [char] is what the key types with no
     * modifier held, lower case — going by it rather than by the key's code is what
     * makes Ctrl+Z the key labelled Z on a German keyboard too, and Ctrl++ its own "+"
     * key. [keyCode] covers the keys that type nothing: Delete, Escape, the number pad.
     */
    fun of(char: Char?, keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean = false): Shortcut? {
        if (alt) return null
        if (!ctrl) {
            if (shift) return null
            return when (keyCode) {
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> Shortcut.DELETE_SELECTION
                KeyEvent.KEYCODE_ESCAPE -> Shortcut.DESELECT
                KeyEvent.KEYCODE_F11 -> Shortcut.FULLSCREEN
                // The number pad's digits are left alone, as Rnote leaves KP_1 without Ctrl:
                // only the row above the letters picks a color.
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> null
                else -> if (char in '1'..'9') COLORS[char!! - '1'] else null
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_TAB -> return if (shift) Shortcut.PREVIOUS_TAB else Shortcut.NEXT_TAB
            KeyEvent.KEYCODE_NUMPAD_ADD -> return Shortcut.ZOOM_IN
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> return Shortcut.ZOOM_OUT
            KeyEvent.KEYCODE_NUMPAD_0 -> return Shortcut.ZOOM_RESET
            // Rnote gives Ctrl+KP_1 to KP_9 to the colors as well, but 1 to 6 went to the
            // pens first, and GTK acts on the first; so the pad's 7 to 9 are colors.
            in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> return when {
                shift -> null
                keyCode <= KeyEvent.KEYCODE_NUMPAD_6 -> PENS[keyCode - KeyEvent.KEYCODE_NUMPAD_1]
                else -> COLORS[keyCode - KeyEvent.KEYCODE_NUMPAD_1]
            }
        }
        val c = char?.lowercaseChar()
        when (c) {
            // "+" is a shifted "=" on a US keyboard, so these don't care about Shift.
            '+', '=' -> return Shortcut.ZOOM_IN
            '-' -> return Shortcut.ZOOM_OUT
            '0' -> return Shortcut.ZOOM_RESET
        }
        if (shift) return when (c) {
            'o' -> Shortcut.PAGE_OVERVIEW
            's' -> Shortcut.SAVE_AS
            'z' -> Shortcut.REDO
            'i' -> Shortcut.IMPORT
            'p' -> Shortcut.SNAP_POSITIONS
            'a' -> Shortcut.ADD_PAGE
            'r' -> Shortcut.REMOVE_PAGE
            else -> null
        }
        return when (c) {
            'o' -> Shortcut.OPEN
            's' -> Shortcut.SAVE
            'n', 't' -> Shortcut.NEW
            'w' -> Shortcut.CLOSE_TAB
            'p' -> Shortcut.PRINT
            'l' -> Shortcut.CLEAR
            'z' -> Shortcut.UNDO
            'y' -> Shortcut.REDO
            'c' -> Shortcut.COPY
            'x' -> Shortcut.CUT
            'v' -> Shortcut.PASTE
            'a' -> Shortcut.SELECT_ALL
            'd' -> Shortcut.DUPLICATE
            in '1'..'6' -> PENS[c!! - '1']
            else -> null
        }
    }

    /** Ctrl+1 to Ctrl+6: Rnote's pens, in the pen picker's order. */
    private val PENS = listOf(
        Shortcut.BRUSH, Shortcut.SHAPER, Shortcut.TYPEWRITER, Shortcut.ERASER, Shortcut.SELECTOR, Shortcut.TOOLS
    )

    /** 1 to 9: the color picker's swatches, left to right — Rnote's `set-color-1` to `-9`. */
    private val COLORS = listOf(
        Shortcut.COLOR_1, Shortcut.COLOR_2, Shortcut.COLOR_3, Shortcut.COLOR_4, Shortcut.COLOR_5,
        Shortcut.COLOR_6, Shortcut.COLOR_7, Shortcut.COLOR_8, Shortcut.COLOR_9
    )
}
