package io.github.kjly.brna.model

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedPagesTest {

    /** Rnote's A4 at 96 dpi, which is not a whole number of units tall. */
    private val a4 = 1122.5197f

    @Test
    fun `a document as tall as three pages has three`() {
        assertEquals(3, FixedPages.countFor(3 * a4, a4))
    }

    @Test
    fun `a height a hair over whole pages, as a float writes it, is not an extra page`() {
        // Rnote writes the height as a double; read back as a float it can land just past
        // three pages, which rounding up would turn into four.
        assertEquals(3, FixedPages.countFor(3 * a4 * 1.000001f, a4))
    }

    @Test
    fun `part of a page counts as a page, as Rnote rounds up`() {
        assertEquals(2, FixedPages.countFor(1.5f * a4, a4))
    }

    @Test
    fun `no height or no format still leaves one page`() {
        assertEquals(1, FixedPages.countFor(0f, a4))
        assertEquals(1, FixedPages.countFor(3 * a4, 0f))
    }

    @Test
    fun `an empty document fits on one page`() {
        assertEquals(1, FixedPages.fitting(null, 200f))
    }

    @Test
    fun `content is fitted with as many pages as reach its bottom`() {
        assertEquals(1, FixedPages.fitting(Rect(10f, 10f, 90f, 199f), 200f))
        assertEquals(3, FixedPages.fitting(Rect(10f, 10f, 90f, 450f), 200f))
    }

    @Test
    fun `content above the origin adds to the height, as in Rnote's calc_height`() {
        // From -100 to 350 is 450 units: three pages of 200.
        assertEquals(3, FixedPages.fitting(Rect(0f, -100f, 50f, 350f), 200f))
    }

    @Test
    fun `remove page takes what lies wholly below the new last page, nothing reaching onto it`() {
        assertTrue(FixedPages.goesWithRemovedPage(top = 401f, bottom = 400f))
        assertFalse(FixedPages.goesWithRemovedPage(top = 399f, bottom = 400f))
        assertFalse(FixedPages.goesWithRemovedPage(top = 400f, bottom = 400f))
    }
}
