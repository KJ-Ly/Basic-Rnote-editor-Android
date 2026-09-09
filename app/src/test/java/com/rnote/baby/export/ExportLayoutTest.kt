package com.rnote.baby.export

import androidx.compose.ui.graphics.Color
import com.rnote.baby.model.LayoutMode
import com.rnote.baby.model.PageSize
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.Stroke
import com.rnote.baby.model.StrokePoint
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

    @Test
    fun `a document export covers every page`() {
        val bounds = ExportLayout.documentBounds(paper(LayoutMode.INFINITE), listOf(dot(150f, 250f)))
        assertEquals(0f, bounds.left, 0f)
        assertEquals(0f, bounds.top, 0f)
        assertEquals(200f, bounds.right, 0f)
        assertEquals(400f, bounds.bottom, 0f)
    }
}
