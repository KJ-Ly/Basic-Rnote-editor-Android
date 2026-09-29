package io.github.kjly.brna.export

import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.LayoutMode
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.PageSize
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportLayoutTest {

    /** A 100 × 200 page, so page boundaries are easy to read off the assertions. */
    private fun paper(mode: LayoutMode) = PaperStyle(
        pageSize = PageSize.CUSTOM,
        layoutMode = mode,
        customWidthPx = 100f,
        customHeightPx = 200f
    )

    private fun dot(x: Float, y: Float) = Stroke(
        points = listOf(StrokePoint(x, y)),
        color = Color.Black,
        strokeWidth = 0f
    )

    @Test
    fun `a fixed-size document is one page whatever the content does`() {
        val pages = ExportLayout.pageRects(paper(LayoutMode.FIXED_SIZE), listOf(dot(950f, 950f)))
        assertEquals(1, pages.size)
        assertEquals(0f, pages[0].left, 0f)
        assertEquals(100f, pages[0].right, 0f)
    }

    @Test
    fun `continuous vertical grows down only`() {
        val pages = ExportLayout.pageRects(
            paper(LayoutMode.CONTINUOUS_VERTICAL),
            listOf(dot(50f, 450f), dot(950f, 10f))   // the far-right dot must not add a column
        )
        assertEquals(3, pages.size)
        assertTrue(pages.all { it.left == 0f && it.right == 100f })
        assertEquals(listOf(0f, 200f, 400f), pages.map { it.top })
    }

    @Test
    fun `an infinite layout spans both axes and always keeps the origin page`() {
        val pages = ExportLayout.pageRects(paper(LayoutMode.INFINITE), listOf(dot(150f, 250f)))
        // Content reaches col 1 / row 1, and col 0 / row 0 stay in: 2 × 2.
        assertEquals(4, pages.size)
        assertEquals(setOf(0f, 100f), pages.map { it.left }.toSet())
        assertEquals(setOf(0f, 200f), pages.map { it.top }.toSet())
    }

    @Test
    fun `negative coordinates are not dropped`() {
        val pages = ExportLayout.pageRects(paper(LayoutMode.INFINITE), listOf(dot(-50f, -10f)))
        assertEquals(4, pages.size)
        assertTrue(pages.any { it.left == -100f && it.top == -200f })
    }

    @Test
    fun `page order decides the sequence, not the set`() {
        val strokes = listOf(dot(150f, 250f))
        val style = paper(LayoutMode.INFINITE)

        val rowMajor = ExportLayout.pageRects(style, strokes, SplitOrder.ROW_MAJOR)
        val colMajor = ExportLayout.pageRects(style, strokes, SplitOrder.COLUMN_MAJOR)
        val reversed = ExportLayout.pageRects(style, strokes, SplitOrder.ROW_MAJOR_REVERSE)

        assertEquals(rowMajor.toSet(), colMajor.toSet())
        assertEquals(rowMajor.reversed(), reversed)
        // Row major walks the top row left to right first; column major walks the left
        // column top to bottom first.
        assertEquals(0f to 0f, rowMajor[0].left to rowMajor[0].top)
        assertEquals(100f to 0f, rowMajor[1].left to rowMajor[1].top)
        assertEquals(0f to 200f, colMajor[1].left to colMajor[1].top)
    }

    @Test
    fun `an infinite canvas has no pages at all`() {
        val style = PaperStyle(pageSize = PageSize.INFINITE)
        assertTrue(ExportLayout.pageRects(style, listOf(dot(10f, 10f))).isEmpty())
        // ...and then the document region is the content plus a margin.
        val bounds = ExportLayout.documentBounds(style, listOf(dot(10f, 10f)))
        assertTrue(bounds.left < 10f && bounds.right > 10f)
    }

    @Test
    fun `content bounds allow for the stroke width`() {
        val stroke = Stroke(points = listOf(StrokePoint(50f, 50f)), color = Color.Black, strokeWidth = 10f)
        val bounds = ExportLayout.contentBounds(listOf(stroke))!!
        assertEquals(45f, bounds.left, 0f)
        assertEquals(55f, bounds.right, 0f)
    }

    @Test
    fun `nothing to bound is null, not an empty rect at the origin`() {
        assertNull(ExportLayout.contentBounds(emptyList()))
        assertNull(ExportLayout.selectionBounds(emptyList(), 12f))
    }

    /** An imported page covering left..right × top..bottom, as Rnote places one. */
    private fun importedPage(left: Float, top: Float, right: Float, bottom: Float): NativeVectorImageElement {
        val hx = (right - left) / 2f
        val hy = (bottom - top) / 2f
        return NativeVectorImageElement(
            "<svg/>", 2 * hx, 2 * hy, hx, hy,
            floatArrayOf(1f, 0f, 0f, 1f, left + hx, top + hy), "document",
            left, top, right, bottom
        )
    }

    @Test
    fun `imported PDF pages become the export pages, widened for the notes beside them`() {
        val pages = ExportLayout.pageRects(
            paper(LayoutMode.SEMI_INFINITE),
            // A note to the right of the second page, level with it.
            listOf(dot(700f, 1500f)),
            nativeElements = listOf(
                importedPage(0f, 1000f, 500f, 2000f),
                importedPage(0f, 0f, 500f, 1000f)
            ),
            followImportedPages = true
        )
        assertEquals(2, pages.size)
        // Reading order, whatever order the file listed them in.
        assertEquals(0f, pages[0].top, 0f)
        assertEquals(500f, pages[0].right, 0f)
        assertEquals(1000f, pages[1].top, 0f)
        assertEquals(724f, pages[1].right, 0f)
    }

    @Test
    fun `without imported pages the format grid is used as before`() {
        val grid = ExportLayout.pageRects(paper(LayoutMode.INFINITE), listOf(dot(150f, 250f)))
        val following = ExportLayout.pageRects(
            paper(LayoutMode.INFINITE), listOf(dot(150f, 250f)), followImportedPages = true
        )
        assertEquals(grid, following)
    }

    @Test
    fun `a document export covers every page`() {
        val bounds = ExportLayout.documentBounds(paper(LayoutMode.INFINITE), listOf(dot(150f, 250f)))
        assertEquals(0f, bounds.left, 0f)
        assertEquals(0f, bounds.top, 0f)
        assertEquals(200f, bounds.right, 0f)
        assertEquals(400f, bounds.bottom, 0f)
    }

    @Test
    fun `a selection export covers the selected desktop elements as well as the ink`() {
        // An image (standing in for any text box, shape or picture) away from the ink.
        val bounds = ExportLayout.selectionBounds(
            listOf(dot(10f, 10f)), 12f, listOf(importedPage(100f, 200f, 300f, 400f))
        )!!
        assertEquals(-2f, bounds.left, 0f)
        assertEquals(-2f, bounds.top, 0f)
        assertEquals(312f, bounds.right, 0f)
        assertEquals(412f, bounds.bottom, 0f)
        // Desktop elements alone are a selection too.
        assertEquals(88f, ExportLayout.selectionBounds(emptyList(), 12f, listOf(importedPage(100f, 200f, 300f, 400f)))!!.left, 0f)
    }

    @Test
    fun `this page is the one showing the most of itself`() {
        val pages = listOf(
            androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 200f),
            androidx.compose.ui.geometry.Rect(0f, 200f, 100f, 400f)
        )
        // Scrolled so the second page fills most of the view.
        assertEquals(1, ExportLayout.pageInView(pages, androidx.compose.ui.geometry.Rect(0f, 150f, 100f, 350f)))
        // Mostly the first.
        assertEquals(0, ExportLayout.pageInView(pages, androidx.compose.ui.geometry.Rect(0f, 50f, 100f, 250f)))
        // Off past the last page, or no pages at all.
        assertNull(ExportLayout.pageInView(pages, androidx.compose.ui.geometry.Rect(0f, 500f, 100f, 600f)))
        assertNull(ExportLayout.pageInView(emptyList(), androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f)))
    }

    @Test
    fun `a fixed-size document exports each of its pages, one below the other`() {
        val pages = ExportLayout.pageRects(paper(LayoutMode.FIXED_SIZE).copy(fixedPageCount = 3), emptyList())
        assertEquals(3, pages.size)
        assertEquals(0f, pages[0].top, 0f)
        assertEquals(400f, pages[2].top, 0f)
        assertEquals(600f, pages[2].bottom, 0f)
    }
}
