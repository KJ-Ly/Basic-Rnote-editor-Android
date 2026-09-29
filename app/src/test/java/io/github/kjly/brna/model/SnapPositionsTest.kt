package io.github.kjly.brna.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class SnapPositionsTest {

    private fun paper(pattern: PaperPattern, spacing: Float = 20f, height: Float = 0f) =
        PaperStyle(pattern = pattern, customGridSpacingPx = spacing, customPatternHeightPx = height)

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 1e-3f)
        assertEquals(expected.y, actual.y, 1e-3f)
    }

    @Test
    fun `on a grid or dots a point goes to the nearest crossing`() {
        assertOffset(Offset(40f, 40f), SnapPositions.snap(Offset(33f, 47f), paper(PaperPattern.GRID)))
        assertOffset(Offset(60f, 100f), SnapPositions.snap(Offset(52f, 95f), paper(PaperPattern.DOTS)))
    }

    @Test
    fun `a pattern taller than it is wide snaps across by its width and down by its height`() {
        assertOffset(Offset(40f, 30f), SnapPositions.snap(Offset(33f, 37f), paper(PaperPattern.GRID, 20f, 30f)))
    }

    @Test
    fun `on ruled paper only the height snaps, to the nearest line`() {
        assertOffset(Offset(33.5f, 40f), SnapPositions.snap(Offset(33.5f, 47f), paper(PaperPattern.LINES)))
    }

    @Test
    fun `on blank paper only the page edges pull`() {
        val blank = paper(PaperPattern.BLANK)
        assertOffset(Offset(33.5f, 47f), SnapPositions.snap(Offset(33.5f, 47f), blank))
        // Within Rnote's DOCUMENT_SNAP_DIST of the page's left and top edge.
        assertOffset(Offset(0f, 0f), SnapPositions.snap(Offset(6f, -4f), blank))
    }

    @Test
    fun `near a page edge the edge wins over the pattern`() {
        val grid = paper(PaperPattern.GRID, spacing = 30f)
        val pageWidth = grid.effectivePageWidthPx
        val snapped = SnapPositions.snap(Offset(pageWidth - 7f, 47f), grid)
        assertEquals(pageWidth, snapped.x, 1e-3f)
        assertEquals(60f, snapped.y, 1e-3f)
    }

    @Test
    fun `on an isometric pattern a point goes to the nearest corner of the triangles`() {
        val iso = paper(PaperPattern.ISO_GRID)
        val column = 20f * sqrt(3f) / 2f
        // Odd columns sit half a step down, as Rnote's pattern (and now the tablet's) has them.
        assertOffset(Offset(column, 10f), SnapPositions.snap(Offset(18f, 11f), iso))
        assertOffset(Offset(2 * column, 20f), SnapPositions.snap(Offset(33f, 29f), iso))
        assertOffset(Offset(6 * column, 60f), SnapPositions.snap(Offset(100f, 57f), paper(PaperPattern.ISO_DOTS)))
    }

    @Test
    fun `a moved selection snaps by its corner nearest where it was taken`() {
        val box = Rect(10f, 20f, 110f, 220f)
        assertOffset(Offset(10f, 20f), SnapPositions.nearestCorner(box, Offset(30f, 40f)))
        assertOffset(Offset(110f, 220f), SnapPositions.nearestCorner(box, Offset(100f, 200f)))
        assertOffset(Offset(110f, 20f), SnapPositions.nearestCorner(box, Offset(90f, 30f)))
    }
}
