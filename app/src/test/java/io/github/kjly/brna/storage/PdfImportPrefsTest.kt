package io.github.kjly.brna.storage

import io.github.kjly.brna.storage.PdfPageLayout.Placed
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rnote's PDF import: its preferences and defaults, and where `VectorImage::from_pdf_bytes`
 * puts the pages — all at the zoom that gives the PDF's first page the width asked for.
 */
class PdfImportPrefsTest {

    /** Two A4 pages in points, and a landscape one after them. */
    private val a4 = 595f to 842f
    private val sizes = listOf(a4, a4, 842f to 595f)
    private val formatW = 793.7f
    private val formatH = 1122.5f

    @Test
    fun `Rnote's defaults, and a choice kept as it was made`() {
        assertEquals(PdfImportPrefs(50, PdfPageSpacing.CONTINUOUS, false), PdfImportPrefs())
        val chosen = PdfImportPrefs(80, PdfPageSpacing.ONE_PER_DOCUMENT_PAGE, true)
        assertEquals(chosen, PdfImportPrefs.decode(chosen.encode()))
        assertEquals(PdfImportPrefs(), PdfImportPrefs.decode(null))
        assertEquals(PdfImportPrefs(100, PdfPageSpacing.CONTINUOUS, false), PdfImportPrefs.decode("250;nonsense;maybe"))
    }

    @Test
    fun `half the format's width, one below the other, 16 apart`() {
        val placed = PdfPageLayout.place(sizes, 0, 2, PdfImportPrefs(), formatW, formatH, 40f, 60f)
        val zoom = formatW * 0.5f / 595f
        assertEquals(3, placed.size)
        assertEquals(Placed(0, 40f, 60f, 595f * zoom, 842f * zoom), placed[0])
        assertEquals(60f + 842f * zoom + 16f, placed[1].y, 1e-3f)
        // The landscape page at the same zoom, not stretched to the width.
        assertEquals(842f * zoom, placed[2].width, 1e-3f)
        assertEquals(595f * zoom, placed[2].height, 1e-3f)
    }

    @Test
    fun `one per document page steps by the format's height`() {
        val prefs = PdfImportPrefs(100, PdfPageSpacing.ONE_PER_DOCUMENT_PAGE, false)
        val placed = PdfPageLayout.place(sizes, 1, 2, prefs, formatW, formatH, 0f, 0f)
        assertEquals(listOf(1, 2), placed.map { it.index })
        assertEquals(listOf(0f, formatH), placed.map { it.y })
        assertEquals(formatW, placed[0].width, 1e-3f)
    }

    @Test
    fun `the zoom comes from the PDF's first page, whichever pages are imported`() {
        val wide = listOf(1190f to 842f, a4)
        val placed = PdfPageLayout.place(wide, 1, 1, PdfImportPrefs(100), formatW, formatH, 0f, 0f).single()
        assertEquals(595f * formatW / 1190f, placed.width, 1e-3f)
    }

    @Test
    fun `adjusting the document puts the pages at the origin, the format's width, edge to edge`() {
        val prefs = PdfImportPrefs(30, PdfPageSpacing.ONE_PER_DOCUMENT_PAGE, adjustDocument = true)
        val placed = PdfPageLayout.place(sizes, 0, 1, prefs, formatW, formatH, 300f, 400f)
        assertEquals(0f, placed[0].x, 0f)
        assertEquals(0f, placed[0].y, 0f)
        assertEquals(formatW, placed[0].width, 1e-3f)
        assertEquals(placed[0].height, placed[1].y, 1e-3f)
    }

    @Test
    fun `a range past the end is cut to the PDF`() {
        assertEquals(3, PdfPageLayout.place(sizes, -1, 9, PdfImportPrefs(), formatW, formatH, 0f, 0f).size)
        assertEquals(emptyList<Placed>(), PdfPageLayout.place(emptyList(), 0, 0, PdfImportPrefs(), formatW, formatH, 0f, 0f))
    }
}
