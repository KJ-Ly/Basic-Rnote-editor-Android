package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** A page side typed into the page settings (see [PageDimensions]). */
class PageDimensionsTest {

    @Test
    fun `a decimal comma is read as well as a point`() {
        // "148,5" was not a number at all, on a German keyboard, and silently did nothing.
        assertEquals(148.5f, PageDimensions.parse("148,5")!!, 0f)
        assertEquals(148.5f, PageDimensions.parse("148.5")!!, 0f)
        assertEquals(210f, PageDimensions.parse(" 210 ")!!, 0f)
        assertEquals(148f, PageDimensions.parse("148,")!!, 0f)
    }

    @Test
    fun `nothing, 0 or less, and what isn't a number are no size`() {
        assertNull(PageDimensions.parse(""))
        assertNull(PageDimensions.parse("0"))
        assertNull(PageDimensions.parse("0,0"))
        assertNull(PageDimensions.parse("-5"))
        assertNull(PageDimensions.parse("abc"))
        assertNull(PageDimensions.parse("1,2,3"))
        assertNull(PageDimensions.parse("Infinity"))
    }

    @Test
    fun `shown to a tenth, as the language writes numbers`() {
        assertEquals("148,5", PageDimensions.format(148.5f, Locale.GERMANY))
        assertEquals("148.5", PageDimensions.format(148.5f, Locale.US))
        // A4's width, which is 209.99998 mm after the trip through document units.
        assertEquals("210", PageDimensions.format(209.99998f, Locale.GERMANY))
        assertEquals("794", PageDimensions.format(793.9999f, Locale.US))
        assertEquals("0,1", PageDimensions.format(0.1f, Locale.GERMANY))
    }

    @Test
    fun `what is typed stays as typed while it is the size shown`() {
        assertTrue(PageDimensions.shows("148,5", 148.49998f))
        assertTrue(PageDimensions.shows("148.5", 148.50002f))
        assertTrue(PageDimensions.shows("148,", 148f))
        // Another size: a unit changed, or a size Rnote's limits held it to.
        assertFalse(PageDimensions.shows("210", 793.7f))
        assertFalse(PageDimensions.shows("40000", 30000f))
        assertFalse(PageDimensions.shows("", 210f))
    }
}
