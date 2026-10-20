package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletLayoutTest {
    @Test fun `a narrow screen gets the compact layout unless the tablet layout is on`() {
        assertTrue(TabletLayout.isCompact(411, 890, tabletLayout = false))
        assertTrue(TabletLayout.isCompact(599, 1200, tabletLayout = false))
        assertFalse(TabletLayout.isCompact(411, 890, tabletLayout = true))
        assertFalse(TabletLayout.isCompact(599, 1200, tabletLayout = true))
    }

    @Test fun `a screen that is low gets the compact layout too, however wide it is`() {
        // A phone held sideways: wide enough, but the pen strip and the toolbars do not fit in 369 dp.
        assertTrue(TabletLayout.isNarrow(800, 369))
        assertTrue(TabletLayout.isCompact(800, 369, tabletLayout = false))
        assertTrue(TabletLayout.isCompact(1280, 479, tabletLayout = false))
        assertFalse(TabletLayout.isCompact(800, 369, tabletLayout = true))
    }

    @Test fun `a screen from 600 x 480 dp is never compact`() {
        assertFalse(TabletLayout.isCompact(600, 480, tabletLayout = false))
        assertFalse(TabletLayout.isCompact(1280, 800, tabletLayout = false))
        assertFalse(TabletLayout.isCompact(1280, 800, tabletLayout = true))
        assertFalse(TabletLayout.isNarrow(600, 480))
    }

    @Test fun `the interface is scaled so 600 dp fit across a narrow screen`() {
        val scale = TabletLayout.scale(411, 890, tabletLayout = true)
        assertEquals(411f / 600f, scale, 1e-6f)
        // What the layout then measures: the screen's width in the scaled dp.
        assertEquals(600f, 411f / scale, 1e-3f)
    }

    @Test fun `the interface is scaled so 480 dp fit down a low screen`() {
        val scale = TabletLayout.scale(800, 369, tabletLayout = true)
        assertEquals(369f / 480f, scale, 1e-6f)
        // What the layout then measures: the screen's height in the scaled dp.
        assertEquals(480f, 369f / scale, 1e-3f)
        // And it is wider than 600 dp then, not narrower.
        assertTrue(800f / scale >= 600f)
    }

    @Test fun `the tighter of width and height decides the scale`() {
        // Small both ways: the width is the tighter one (0.5 against 0.75).
        assertEquals(0.5f, TabletLayout.scale(300, 360, tabletLayout = true), 1e-6f)
        // Small both ways: the height is the tighter one (0.75 against 0.9).
        assertEquals(0.75f, TabletLayout.scale(540, 360, tabletLayout = true), 1e-6f)
    }

    @Test fun `a small screen is never scaled up`() {
        for ((w, h) in listOf(411 to 890, 800 to 369, 599 to 479, 300 to 200, 599 to 4000, 4000 to 479)) {
            assertTrue("$w x $h", TabletLayout.scale(w, h, tabletLayout = true) < 1f)
        }
    }

    @Test fun `nothing is scaled with the switch off or on a screen that is big enough`() {
        assertEquals(1f, TabletLayout.scale(411, 890, tabletLayout = false), 0f)
        assertEquals(1f, TabletLayout.scale(800, 369, tabletLayout = false), 0f)
        assertEquals(1f, TabletLayout.scale(600, 480, tabletLayout = true), 0f)
        assertEquals(1f, TabletLayout.scale(1280, 800, tabletLayout = true), 0f)
    }

    @Test fun `a size of nothing is left alone`() {
        assertEquals(1f, TabletLayout.scale(0, 890, tabletLayout = true), 0f)
        assertEquals(1f, TabletLayout.scale(800, 0, tabletLayout = true), 0f)
        // A height that is not known yet does not make a wide screen compact.
        assertFalse(TabletLayout.isNarrow(800, 0))
    }
}
