package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rnote's "Stylus pen modes": a pen for each end of the stylus, each lockable (see [PenModes]). */
class PenModesTest {

    @Test
    fun `Rnote's defaults - the tip free with the brush, the eraser end locked to the eraser`() {
        val modes = PenModes()
        assertEquals(ToolType.BRUSH, modes.tool(PenMode.PEN))
        assertEquals(ToolType.ERASER, modes.tool(PenMode.ERASER))
        assertFalse(modes.locked(PenMode.PEN))
        assertTrue(modes.locked(PenMode.ERASER))
    }

    @Test
    fun `each end keeps its own pen and lock`() {
        val modes = PenModes()
            .withTool(PenMode.PEN, ToolType.SHAPER)
            .withTool(PenMode.ERASER, ToolType.SELECTOR)
            .withLock(PenMode.PEN, true)
            .withLock(PenMode.ERASER, false)
        assertEquals(ToolType.SHAPER, modes.tool(PenMode.PEN))
        assertEquals(ToolType.SELECTOR, modes.tool(PenMode.ERASER))
        assertTrue(modes.locked(PenMode.PEN))
        assertFalse(modes.locked(PenMode.ERASER))
    }

    @Test
    fun `a locked end refuses another pen from the picker, and leaves the one it has`() {
        val modes = PenModes()
        // The eraser end, locked by default.
        assertEquals(PenModes.Pick.LOCKED, modes.pick(PenMode.ERASER, ToolType.BRUSH, current = ToolType.ERASER))
        assertEquals(PenModes.Pick.NOTHING, modes.pick(PenMode.ERASER, ToolType.ERASER, current = ToolType.ERASER))
        // The tip, free by default, takes any pen, the one out as well.
        assertEquals(PenModes.Pick.SWITCH, modes.pick(PenMode.PEN, ToolType.SELECTOR, current = ToolType.BRUSH))
        assertEquals(PenModes.Pick.SWITCH, modes.pick(PenMode.PEN, ToolType.BRUSH, current = ToolType.BRUSH))
        assertEquals(
            PenModes.Pick.LOCKED,
            modes.withLock(PenMode.PEN, true).pick(PenMode.PEN, ToolType.SELECTOR, current = ToolType.BRUSH)
        )
    }

    @Test
    fun `the settings come back as they were saved`() {
        val modes = PenModes(ToolType.TYPEWRITER, ToolType.TOOLS, lockPen = true, lockEraser = false)
        assertEquals(modes, PenModes.decode(modes.encode()))
        assertEquals(PenModes(), PenModes.decode(PenModes().encode()))
    }

    @Test
    fun `nothing saved, or a line that can't be read, keeps Rnote's default`() {
        assertEquals(PenModes(), PenModes.decode(null))
        assertEquals(PenModes(), PenModes.decode(""))
        assertEquals(
            PenModes(penTool = ToolType.SHAPER),
            PenModes.decode("pen=shaper\neraser=laser\nlock_pen=maybe\nsomething=else")
        )
    }
}
