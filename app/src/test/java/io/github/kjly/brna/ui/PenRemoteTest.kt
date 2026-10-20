package io.github.kjly.brna.ui

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenRemoteTest {

    @Test
    fun `the pen's own presses count`() {
        assertTrue(PenRemote.isFromPen("S Pen", 12, typingKeyboard = false))
        assertTrue(PenRemote.isFromPen("sec_e-pen", 5, typingKeyboard = false))
        assertTrue(PenRemote.isFromPen("S Pen Pro", 6, typingKeyboard = false))
        // Put in by Samsung's S Pen service rather than sent by a device.
        assertTrue(PenRemote.isFromPen("Virtual", KeyCharacterMap.VIRTUAL_KEYBOARD, typingKeyboard = false))
        assertTrue(PenRemote.isFromPen(null, 0, typingKeyboard = false))
    }

    @Test
    fun `a keyboard's Page Down, a clicker or headphones no longer undo`() {
        assertFalse(PenRemote.isFromPen("Logitech K380", 9, typingKeyboard = true))
        assertFalse(PenRemote.isFromPen("Logitech R400", 10, typingKeyboard = false))
        assertFalse(PenRemote.isFromPen("WH-1000XM4", 11, typingKeyboard = false))
        // "Pen" inside another word is no pen: headphones called OpenRun.
        assertFalse(PenRemote.isFromPen("OpenRun by Shokz", 14, typingKeyboard = false))
        assertFalse(PenRemote.isFromPen("Happening Remote", 15, typingKeyboard = false))
        // Whatever it is called, a typing keyboard is not the pen.
        assertFalse(PenRemote.isFromPen("Pen & Keyboard Combo", 13, typingKeyboard = true))
    }

    @Test
    fun `only the pen's keys are looked at`() {
        assertTrue(PenRemote.isPenKey(KeyEvent.KEYCODE_PAGE_DOWN))
        assertTrue(PenRemote.isPenKey(KeyEvent.KEYCODE_PAGE_UP))
        assertTrue(PenRemote.isPenKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertFalse(PenRemote.isPenKey(KeyEvent.KEYCODE_Z))
        assertFalse(PenRemote.isPenKey(KeyEvent.KEYCODE_MEDIA_NEXT))
    }
}
