package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LayoutModeTest {

    @Test
    fun `api names round-trip`() {
        for (mode in LayoutMode.entries) {
            assertSame(mode, LayoutMode.fromApiName(mode.apiName))
        }
    }

    @Test
    fun `api names match what desktop Rnote writes`() {
        assertEquals("fixed_size", LayoutMode.FIXED_SIZE.apiName)
        assertEquals("continuous_vertical", LayoutMode.CONTINUOUS_VERTICAL.apiName)
        assertEquals("infinite", LayoutMode.INFINITE.apiName)
    }

    @Test
    fun `a missing layout falls back to the same default PaperStyle uses`() {
        // Not FIXED_SIZE: files written before the layout key was emitted carry no layout,
        // and collapsing those to a single page is the bug this fallback exists to avoid.
        assertSame(LayoutMode.INFINITE, LayoutMode.DEFAULT)
        assertSame(LayoutMode.DEFAULT, PaperStyle().layoutMode)
        assertSame(LayoutMode.DEFAULT, LayoutMode.fromApiName(""))
        assertSame(LayoutMode.DEFAULT, LayoutMode.fromApiName("something_new"))
    }

    @Test
    fun `Rnote's semi-infinite layout maps to the unbounded mode`() {
        assertSame(LayoutMode.INFINITE, LayoutMode.fromApiName("semi_infinite"))
    }

    @Test
    fun `api name matching is case and whitespace tolerant`() {
        assertSame(LayoutMode.CONTINUOUS_VERTICAL, LayoutMode.fromApiName(" Continuous_Vertical "))
    }
}
