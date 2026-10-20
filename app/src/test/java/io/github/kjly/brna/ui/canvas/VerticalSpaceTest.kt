package io.github.kjly.brna.ui.canvas

import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalSpaceTest {

    private fun stroke(id: String, fromY: Float, toY: Float, width: Float = 4f, x: Float = 10f) = Stroke(
        id = id,
        points = listOf(StrokePoint(x, fromY), StrokePoint(x + 10f, toY)),
        color = Color.Black,
        strokeWidth = width
    )

    /** A picture covering top..bottom, and left..right. */
    private fun image(top: Float, bottom: Float, left: Float = 0f, right: Float = 100f) = NativeVectorImageElement(
        "<svg/>", right - left, bottom - top, (right - left) / 2f, (bottom - top) / 2f,
        floatArrayOf(1f, 0f, 0f, 1f, (left + right) / 2f, (top + bottom) / 2f), "image",
        left, top, right, bottom
    )

    /** Pages 1000 wide and 1500 high, as far as these tests are concerned. */
    private fun region(x: Float, y: Float, vertical: Boolean, horizontal: Boolean) =
        VerticalSpace.region(x, y, 1000f, 1500f, vertical, horizontal)

    @Test
    fun `everything reaching the line or below moves, a stroke crossing it included`() {
        val below = VerticalSpace.strokesBelow(
            listOf(stroke("above", 10f, 40f), stroke("crossing", 80f, 120f), stroke("below", 150f, 160f)),
            100f
        )
        assertEquals(setOf("crossing", "below"), below)
    }

    @Test
    fun `a stroke's width counts, as its bounds do in Rnote`() {
        // Ends at 98, but 4 wide: its outline reaches 100.
        assertEquals(setOf("s"), VerticalSpace.strokesBelow(listOf(stroke("s", 50f, 98f)), 100f))
        assertTrue(VerticalSpace.strokesBelow(listOf(stroke("s", 50f, 97f)), 100f).isEmpty())
    }

    @Test
    fun `desktop elements move by the same rule and are told apart by identity`() {
        val above = image(0f, 90f)
        val crossing = image(50f, 150f)
        val twin = image(50f, 150f)
        val below = VerticalSpace.nativesBelow(listOf(above, crossing), 100f)
        assertTrue(crossing in below)
        assertFalse(above in below)
        // Another element in the same place is not the one that moves.
        assertFalse(twin in below)
    }

    @Test
    fun `close to where the drag started nothing moves`() {
        assertEquals(0f, VerticalSpace.offset(100f, 105f), 0f)
        assertEquals(0f, VerticalSpace.offset(100f, 91f), 0f)
        assertEquals(40f, VerticalSpace.offset(100f, 140f), 0f)
        assertEquals(-30f, VerticalSpace.offset(100f, 70f), 0f)
    }

    @Test
    fun `with Snap Positions on the room made is whole steps of the pattern`() {
        val lines = io.github.kjly.brna.model.PaperStyle(
            pattern = io.github.kjly.brna.model.PaperPattern.LINES, customGridSpacingPx = 20f
        )
        val snap = { v: Float ->
            io.github.kjly.brna.model.SnapPositions.snap(androidx.compose.ui.geometry.Offset(300f, v), lines).y
        }
        assertEquals(40f, VerticalSpace.offset(100f, 147f, snap), 1e-3f)
        assertEquals(-20f, VerticalSpace.offset(100f, 77f, snap), 1e-3f)
        // Close to the start it still goes back to no room at all.
        assertEquals(0f, VerticalSpace.offset(100f, 106f, snap), 0f)
    }
    @Test
    fun `with no limit on, the box is everything from the pen down, as before`() {
        val strokes = listOf(stroke("above", 10f, 40f), stroke("left", 500f, 600f, x = -3000f), stroke("far", 9000f, 9100f, x = 7000f))
        assertEquals(
            VerticalSpace.strokesBelow(strokes, 100f),
            VerticalSpace.strokesIn(strokes, region(1200f, 100f, vertical = false, horizontal = false))
        )
        assertEquals(setOf("left", "far"), VerticalSpace.strokesIn(strokes, region(1200f, 100f, false, false)))
    }

    @Test
    fun `limited to the vertical page borders, only the page column of the pen moves`() {
        // The pen goes down in the second column of pages: x from 1000 to 2000.
        val strokes = listOf(
            stroke("left column", 200f, 300f, x = 100f),
            stroke("this column", 200f, 300f, x = 1200f),
            stroke("on the border", 200f, 300f, x = 990f),
            stroke("right column", 200f, 300f, x = 2500f),
            stroke("above", 10f, 40f, x = 1200f)
        )
        assertEquals(
            setOf("this column", "on the border"),
            VerticalSpace.strokesIn(strokes, region(1500f, 100f, vertical = true, horizontal = false))
        )
    }

    @Test
    fun `limited to the horizontal page borders, only down to the next border moves`() {
        // The pen goes down at 1700, on the second page: its border below is at 3000.
        val strokes = listOf(
            stroke("above", 1000f, 1200f),
            stroke("this page", 2000f, 2100f),
            stroke("across the border", 2900f, 3100f),
            stroke("next page", 3200f, 3300f)
        )
        assertEquals(
            setOf("this page", "across the border"),
            VerticalSpace.strokesIn(strokes, region(50f, 1700f, vertical = false, horizontal = true))
        )
    }

    @Test
    fun `both limits together keep it to the page the pen went down on and below it`() {
        val strokes = listOf(
            stroke("here", 2000f, 2100f, x = 1200f),
            stroke("left", 2000f, 2100f, x = 100f),
            stroke("next page", 3200f, 3300f, x = 1200f)
        )
        assertEquals(setOf("here"), VerticalSpace.strokesIn(strokes, region(1500f, 1700f, vertical = true, horizontal = true)))
    }

    @Test
    fun `a pen on a border belongs to the page below it, and above the origin to the page above`() {
        val below = region(0f, 1500f, vertical = false, horizontal = true)
        assertEquals(3000f, below.maxY, 0f)
        val overOrigin = region(0f, -10f, vertical = false, horizontal = true)
        assertEquals(0f, overOrigin.maxY, 0f)
        val leftOfOrigin = region(-10f, 0f, vertical = true, horizontal = false)
        assertEquals(-1000f, leftOfOrigin.minX, 0f)
        assertEquals(0f, leftOfOrigin.maxX, 0f)
    }

    @Test
    fun `desktop elements are held to the same box`() {
        val here = image(2000f, 2100f, left = 1100f, right = 1200f)
        val left = image(2000f, 2100f, left = 100f, right = 200f)
        val nextPage = image(3200f, 3300f, left = 1100f, right = 1200f)
        val inside = VerticalSpace.nativesIn(
            listOf(here, left, nextPage),
            region(1500f, 1700f, vertical = true, horizontal = true)
        )
        assertTrue(here in inside)
        assertFalse(left in inside)
        assertFalse(nextPage in inside)
    }

    @Test
    fun `a page with no size has no borders, so the limits leave it alone`() {
        val r = VerticalSpace.region(500f, 500f, 0f, 0f, limitVerticalBorders = true, limitHorizontalBorders = true)
        assertEquals(Float.NEGATIVE_INFINITY, r.minX, 0f)
        assertEquals(Float.POSITIVE_INFINITY, r.maxX, 0f)
        assertEquals(Float.POSITIVE_INFINITY, r.maxY, 0f)
        assertEquals(500f, r.minY, 0f)
    }
}
