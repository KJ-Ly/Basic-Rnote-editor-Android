package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A page given a custom size, from the page settings (see [PaperStyle.withCustomPageSize]). */
class PaperStyleSizeTest {

    private val eps = 1e-3f

    @Test
    fun `the width set is the width, in landscape too`() {
        // A landscape file comes in as a custom size turned on its side; setting its width
        // used to set the side that is its height.
        val landscape = PaperStyle(pageSize = PageSize.CUSTOM, isLandscape = true, customWidthPx = 600f, customHeightPx = 800f)
        assertEquals(800f, landscape.effectivePageWidthPx, eps)
        val wider = landscape.withCustomPageSize(1000f, landscape.effectivePageHeightPx)
        assertEquals(1000f, wider.effectivePageWidthPx, eps)
        assertEquals(600f, wider.effectivePageHeightPx, eps)
        assertTrue(wider.isLandscape)
    }

    @Test
    fun `the orientation is whichever the size makes it, as in Rnote`() {
        val portrait = PaperStyle().withCustomPageSize(500f, 700f)
        assertFalse(portrait.isLandscape)
        val turned = portrait.withCustomPageSize(900f, portrait.effectivePageHeightPx)
        assertTrue(turned.isLandscape)
        assertEquals(900f, turned.effectivePageWidthPx, eps)
        assertEquals(700f, turned.effectivePageHeightPx, eps)
    }

    @Test
    fun `Custom starts from the size the page has`() {
        val a4Landscape = PaperStyle(pageSize = PageSize.A4, isLandscape = true).customSized()
        assertEquals(PageSize.CUSTOM, a4Landscape.pageSize)
        assertEquals(PageSize.A4.heightPx, a4Landscape.effectivePageWidthPx, eps)
        assertEquals(PageSize.A4.widthPx, a4Landscape.effectivePageHeightPx, eps)
        assertTrue(a4Landscape.isLandscape)
    }

    @Test
    fun `Custom on a page of no size starts at an A3 page, not at none`() {
        // Custom with no size set was a page 0 by 0.
        for (style in listOf(PaperStyle(pageSize = PageSize.INFINITE), PaperStyle(pageSize = PageSize.CUSTOM))) {
            val custom = style.customSized()
            assertEquals(PageSize.A3.widthPx, custom.effectivePageWidthPx, eps)
            assertEquals(PageSize.A3.heightPx, custom.effectivePageHeightPx, eps)
        }
    }
}
