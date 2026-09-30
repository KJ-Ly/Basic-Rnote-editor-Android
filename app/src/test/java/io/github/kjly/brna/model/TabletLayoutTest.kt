package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletLayoutTest {
    @Test fun `a narrow screen gets the compact layout unless the tablet layout is on`() {
        assertTrue(TabletLayout.isCompact(411, tabletLayout = false))
        assertTrue(TabletLayout.isCompact(599, tabletLayout = false))
        assertFalse(TabletLayout.isCompact(411, tabletLayout = true))
        assertFalse(TabletLayout.isCompact(599, tabletLayout = true))
    }

    @Test fun `a screen from 600 dp is never compact`() {
        assertFalse(TabletLayout.isCompact(600, tabletLayout = false))
        assertFalse(TabletLayout.isCompact(1280, tabletLayout = false))
        assertFalse(TabletLayout.isCompact(1280, tabletLayout = true))
    }

    @Test fun `the interface is scaled so 600 dp fit across a narrow screen`() {
        val scale = TabletLayout.scale(411, tabletLayout = true)
        assertEquals(411f / 600f, scale, 1e-6f)
        // What the layout then measures: the screen's width in the scaled dp.
        assertEquals(600f, 411f / scale, 1e-3f)
    }

    @Test fun `nothing is scaled with the switch off or on a screen that is wide enough`() {
        assertEquals(1f, TabletLayout.scale(411, tabletLayout = false), 0f)
        assertEquals(1f, TabletLayout.scale(600, tabletLayout = true), 0f)
        assertEquals(1f, TabletLayout.scale(1280, tabletLayout = true), 0f)
    }

    @Test fun `a width of nothing is left alone`() {
        assertEquals(1f, TabletLayout.scale(0, tabletLayout = true), 0f)
    }
}
