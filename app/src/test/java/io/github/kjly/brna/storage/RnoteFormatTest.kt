package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Test

/** Rnote's limits on a format, which a file written here keeps to (see [RnoteFormat]). */
class RnoteFormatTest {

    @Test
    fun `a side within Rnote's limits is left as it is`() {
        assertEquals(793.7f, RnoteFormat.side(793.7f, RnoteFormat.WIDTH_DEFAULT), 0f)
        assertEquals(1f, RnoteFormat.side(1f, RnoteFormat.WIDTH_DEFAULT), 0f)
        assertEquals(30000f, RnoteFormat.side(30000f, RnoteFormat.WIDTH_DEFAULT), 0f)
    }

    @Test
    fun `no side at all is Rnote's default, and one too long its longest`() {
        assertEquals(1123f, RnoteFormat.side(0f, RnoteFormat.WIDTH_DEFAULT), 0f)
        assertEquals(1587f, RnoteFormat.side(-3f, RnoteFormat.HEIGHT_DEFAULT), 0f)
        assertEquals(1587f, RnoteFormat.side(Float.NaN, RnoteFormat.HEIGHT_DEFAULT), 0f)
        assertEquals(1123f, RnoteFormat.side(0.5f, RnoteFormat.WIDTH_DEFAULT), 0f)
        assertEquals(30000f, RnoteFormat.side(1e9f, RnoteFormat.WIDTH_DEFAULT), 0f)
        assertEquals(30000f, RnoteFormat.side(Float.POSITIVE_INFINITY, RnoteFormat.WIDTH_DEFAULT), 0f)
    }

    @Test
    fun `a dpi Rnote can take`() {
        assertEquals(300f, RnoteFormat.dpi(300f), 0f)
        assertEquals(96f, RnoteFormat.dpi(0f), 0f)
        assertEquals(96f, RnoteFormat.dpi(Float.NaN), 0f)
        assertEquals(5000f, RnoteFormat.dpi(9000f), 0f)
    }
}
