package io.github.kjly.brna.storage

import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeBuildersTest {

    @Test
    fun `a coordinate system has its origin below the start and level with the end`() {
        // Pen down at the tip of the vertical axis (100, 0), up at the tip of the horizontal one (300, 200).
        val axes = ShapeBuilders.axes(ShapeKind.COORD_SYSTEM_2D, 100f, 0f, 300f, 200f)
        assertEquals(
            listOf(
                ShapeBuilders.Segment(100f, 200f, 100f, 0f),    // up
                ShapeBuilders.Segment(100f, 200f, 100f, 400f),  // down, mirrored
                ShapeBuilders.Segment(100f, 200f, 300f, 200f),  // right
                ShapeBuilders.Segment(100f, 200f, -100f, 200f)  // left, mirrored
            ),
            axes
        )
    }

    @Test
    fun `a quadrant is the upper and right axes only`() {
        val axes = ShapeBuilders.axes(ShapeKind.QUADRANT, 0f, 0f, 50f, 80f)
        assertEquals(
            listOf(ShapeBuilders.Segment(0f, 80f, 0f, 0f), ShapeBuilders.Segment(0f, 80f, 50f, 80f)),
            axes
        )
    }

    @Test
    fun `the third axis runs diagonally, a quarter of the others' length`() {
        // Up axis 200 long, right axis 200 long: the diagonal reaches (200 + 200) / 4 = 100 each way.
        val axes = ShapeBuilders.axes(ShapeKind.COORD_SYSTEM_3D, 0f, 0f, 200f, 200f)
        assertEquals(6, axes.size)
        assertEquals(ShapeBuilders.Segment(0f, 200f, -100f, 300f), axes[4])
        assertEquals(ShapeBuilders.Segment(0f, 200f, 100f, 100f), axes[5])
    }

    @Test
    fun `other shapes have no axes`() {
        assertTrue(ShapeBuilders.axes(ShapeKind.RECTANGLE, 0f, 0f, 10f, 10f).isEmpty())
    }

    @Test
    fun `a grid repeats the first cell as often as it fits, one line per cell edge`() {
        // 10 × 20 cells from (0, 0), dragged to (35, 45): 3 columns, 2 rows.
        val lines = ShapeBuilders.grid(0f, 0f, 10f, 20f, 35f, 45f)
        // Top edges 3, left edges 2, then a right and a bottom edge for each of 6 cells.
        assertEquals(3 + 2 + 2 * 6, lines.size)
        assertEquals(ShapeBuilders.Segment(0f, 0f, 10f, 0f), lines[0])
        assertEquals(ShapeBuilders.Segment(0f, 0f, 0f, 20f), lines[3])
        // The last cell's bottom edge closes the grid at its far corner.
        assertEquals(ShapeBuilders.Segment(20f, 40f, 30f, 40f), lines.last())
    }

    @Test
    fun `a grid dragged against its first cell's direction is empty, and it is capped`() {
        assertTrue(ShapeBuilders.grid(0f, 0f, 10f, 10f, -50f, 50f).isEmpty())
        // A cell drawn leftwards and dragged further left still works.
        assertEquals(3, ShapeBuilders.grid(0f, 0f, -10f, 10f, -35f, 10f).count { it.y1 == 0f && it.y2 == 0f })
        val huge = ShapeBuilders.grid(0f, 0f, 1f, 1f, 10_000f, 1f)
        assertEquals(ShapeBuilders.GRID_CELLS_MAX, huge.count { it.y1 == 0f && it.y2 == 0f })
    }

    @Test
    fun `lines snap to 15 degree steps and keep their length`() {
        // 20° off level turns to 15°.
        val (x, y) = ShapeBuilders.snapAngle(0f, 0f, 100f * cos(20.0), 100f * sin(20.0))
        assertEquals(100f * cos(15.0), x, 1e-3f)
        assertEquals(100f * sin(15.0), y, 1e-3f)
        // Nearly upright becomes upright.
        val (ux, uy) = ShapeBuilders.snapAngle(10f, 10f, 12f, 110f)
        assertEquals(10f, ux, 1e-3f)
        assertEquals(10f + kotlin.math.hypot(2f, 100f), uy, 1e-3f)
        // No length, nothing to turn.
        assertEquals(5f to 5f, ShapeBuilders.snapAngle(5f, 5f, 5f, 5f))
    }

    @Test
    fun `every line of a coordinate system becomes a line shape, the multi-line kinds none themselves`() {
        val black = RnoteNativeColor(0f, 0f, 0f, 1f)
        val lines = ShapeBuilders.axes(ShapeKind.COORD_SYSTEM_2D, 100f, 0f, 300f, 200f).mapNotNull {
            NativeEditing.createShape(ShapeKind.LINE, it.x1, it.y1, it.x2, it.y2, black, 2f)
        }
        assertEquals(4, lines.size)
        assertTrue(lines.all { it.raw!!.asJsonObject.getAsJsonObject("shape").has("line") })
        assertNull(NativeEditing.createShape(ShapeKind.GRID, 0f, 0f, 10f, 10f, black, 2f))
    }

    private fun cos(degrees: Double) = kotlin.math.cos(Math.toRadians(degrees)).toFloat()
    private fun sin(degrees: Double) = kotlin.math.sin(Math.toRadians(degrees)).toFloat()
}
