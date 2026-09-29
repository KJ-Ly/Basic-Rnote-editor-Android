package io.github.kjly.brna.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserHitTestTest {

    private val eps = 1e-4f

    private fun stroke(
        vararg points: Pair<Float, Float>,
        width: Float = 4f,
        pressure: Float = 1f,
        id: String = "s"
    ) = Stroke(
        id = id,
        points = points.map { StrokePoint(it.first, it.second, pressure) },
        color = Color.Black,
        strokeWidth = width,
        pressureCurve = PressureCurve.LINEAR
    )

    private fun hits(center: Offset, eraserWidth: Float, vararg strokes: Stroke) =
        EraserHitTest.collidingStrokes(
            EraserHitTest.eraserBounds(center, eraserWidth),
            strokes.toList()
        )

    @Test
    fun `eraser bounds are an axis-aligned square of half extents`() {
        val bounds = EraserHitTest.eraserBounds(Offset(10f, -5f), 8f)
        assertEquals(6f, bounds.left, eps)
        assertEquals(-9f, bounds.top, eps)
        assertEquals(14f, bounds.right, eps)
        assertEquals(-1f, bounds.bottom, eps)
    }

    @Test
    fun `the square erases at its corners, where a circle of the same width would miss`() {
        // Rnote's eraser is a square (Aabb::from_half_extents), not a circle. (9, 9) is
        // 12.7 units from the centre — outside a radius-10 circle, inside the square.
        val s = stroke(9f to 9f, 9f to 30f, width = 0.02f)
        assertEquals(listOf(s), hits(Offset.Zero, 20f, s))
    }

    @Test
    fun `the middle of a long straight stroke can be erased`() {
        // A fast stylus stroke records only its two endpoints. Testing sampled points for
        // proximity cannot erase anywhere between them; testing segments can.
        val s = stroke(0f to 0f, 0f to 400f)
        assertEquals(listOf(s), hits(Offset(0f, 200f), 8f, s))
    }

    @Test
    fun `a stroke nowhere near the eraser is rejected`() {
        val s = stroke(0f to 0f, 10f to 10f)
        assertTrue(hits(Offset(500f, 500f), 20f, s).isEmpty())
    }

    @Test
    fun `hitboxes are loosened by half the nominal stroke width`() {
        val s = stroke(0f to 0f, 100f to 0f, width = 20f)
        assertFalse(hits(Offset(50f, 9f), 1f, s).isEmpty())    // inside the 10-unit loosening
        assertTrue(hits(Offset(50f, 11f), 1f, s).isEmpty())    // outside it
    }

    @Test
    fun `loosening uses the nominal width, not the pressure-adjusted painted width`() {
        // Upstream's gen_hitboxes_int reads style.stroke_width() directly. At pressure 0.1
        // this stroke is painted 2 units wide but still collides out to 10 units.
        val s = stroke(0f to 0f, 100f to 0f, width = 20f, pressure = 0.1f)
        assertFalse(hits(Offset(50f, 9f), 1f, s).isEmpty())
    }

    @Test
    fun `a single-tap dot is sized by its pressure`() {
        // A path with no segments gets one box at the start point, half-extent = pressure.
        val dot = stroke(0f to 0f, width = 20f, pressure = 5f)
        assertFalse(hits(Offset(3f, 0f), 1f, dot).isEmpty())
        assertTrue(hits(Offset(7f, 0f), 1f, dot).isEmpty())
    }

    @Test
    fun `a stroke with no points is never hit`() {
        val empty = Stroke(points = emptyList(), color = Color.Black)
        assertTrue(hits(Offset.Zero, 100f, empty).isEmpty())
    }

    @Test
    fun `colliding strokes come back in draw order`() {
        val first = stroke(0f to 0f, 50f to 0f, id = "first")
        val second = stroke(0f to 1f, 50f to 1f, id = "second")
        val elsewhere = stroke(0f to 900f, 50f to 900f, id = "elsewhere")
        assertEquals(
            listOf("first", "second"),
            hits(Offset(25f, 0f), 10f, first, second, elsewhere).map { it.id }
        )
    }

    @Test
    fun `a diagonal stroke is hit between its recorded points`() {
        // Long segments are split into sub-segments so a hitbox stays tight to the line;
        // a single loose bounding box would erase in the empty corner off the diagonal.
        val s = stroke(0f to 0f, 200f to 200f, width = 2f)
        assertFalse(hits(Offset(100f, 100f), 4f, s).isEmpty())
        assertTrue(hits(Offset(190f, 10f), 4f, s).isEmpty())
    }

    // ── Split eraser ──────────────────────────────────────────────────────────

    private fun split(center: Offset, eraserWidth: Float, s: Stroke) =
        EraserHitTest.splitStroke(EraserHitTest.eraserBounds(center, eraserWidth), s)

    @Test
    fun `the split eraser cuts a stroke in two and keeps both ends`() {
        val s = stroke(*(0..10).map { it * 10f to 0f }.toTypedArray(), width = 1f)
        val pieces = split(Offset(50f, 0f), 4f, s)!!
        assertEquals(2, pieces.size)
        // Segments 4 (40-50) and 5 (50-60) are cut out, with the point between them.
        assertEquals(listOf(0f, 10f, 20f, 30f, 40f), pieces[0].points.map { it.x })
        assertEquals(listOf(60f, 70f, 80f, 90f, 100f), pieces[1].points.map { it.x })
        // The start keeps the stroke's identity; the rest are new strokes of the same style.
        assertEquals(s.id, pieces[0].id)
        assertTrue(pieces[1].id != s.id)
        assertEquals(s.strokeWidth, pieces[1].strokeWidth, 0f)
        assertEquals(s.color, pieces[1].color)
    }

    @Test
    fun `cutting the start leaves only the rest`() {
        val s = stroke(*(0..5).map { it * 10f to 0f }.toTypedArray(), width = 1f)
        val pieces = split(Offset(0f, 0f), 4f, s)!!
        assertEquals(1, pieces.size)
        assertEquals(listOf(10f, 20f, 30f, 40f, 50f), pieces[0].points.map { it.x })
    }

    @Test
    fun `a stroke the split eraser misses is left alone`() {
        val s = stroke(0f to 0f, 100f to 0f, width = 1f)
        assertEquals(null, split(Offset(50f, 50f), 4f, s))
    }

    @Test
    fun `a stroke with nothing left worth keeping goes entirely`() {
        val s = stroke(0f to 0f, 10f to 0f, 20f to 0f, width = 1f)
        assertEquals(emptyList<Stroke>(), split(Offset(10f, 0f), 40f, s))
        val dot = stroke(5f to 5f)
        assertEquals(emptyList<Stroke>(), split(Offset(5f, 5f), 4f, dot))
    }

    @Test
    fun `leftovers of a single segment between two cuts are dropped, as in Rnote`() {
        // Cuts at segments 1 and 3 leave only segment 2 between them: Rnote needs two
        // segments to make a piece there (one's end becomes the start of the next).
        val s = stroke(*(0..6).map { it * 100f to 0f }.toTypedArray(), width = 1f)
        val bounds1 = EraserHitTest.eraserBounds(Offset(150f, 0f), 4f)
        val first = EraserHitTest.splitStroke(bounds1, s)!!
        assertEquals(2, first.size)
        assertEquals(listOf(0f, 100f), first[0].points.map { it.x })
        assertEquals(listOf(200f, 300f, 400f, 500f, 600f), first[1].points.map { it.x })
    }
}
