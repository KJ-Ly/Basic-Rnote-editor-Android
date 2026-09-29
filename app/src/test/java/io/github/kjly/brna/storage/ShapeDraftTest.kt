package io.github.kjly.brna.storage

import androidx.compose.ui.geometry.Offset
import io.github.kjly.brna.model.ConstraintRatio
import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.PathOp
import io.github.kjly.brna.model.PathShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.ShapeConstraints
import io.github.kjly.brna.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeDraftTest {

    private val black = RnoteNativeColor(0f, 0f, 0f, 1f)
    private val off = ShapeConstraints()

    /** One stroke of the pen: down at [from], dragged to [to], lifted. */
    private fun ShapeDraft?.stroke(kind: ShapeKind, from: Offset, to: Offset, c: ShapeConstraints = off): ShapeDraft.Lifted {
        val down = this?.down(from, c, ShapeDraft.FINISH_DISTANCE) ?: ShapeDraft.start(kind, from)
        return down.move(to, c).up()
    }

    @Test
    fun `a polyline takes a corner per stroke and finishes on its last corner`() {
        var lifted = null.stroke(ShapeKind.POLYLINE, Offset(0f, 0f), Offset(100f, 0f))
        assertNull(lifted.finished)
        lifted = lifted.draft.stroke(ShapeKind.POLYLINE, Offset(300f, 300f), Offset(100f, 80f))
        assertNull(lifted.finished)
        // The pen down on the last corner again: lifting it finishes.
        lifted = lifted.draft.stroke(ShapeKind.POLYLINE, Offset(103f, 81f), Offset(103f, 81f))
        assertNull(lifted.draft)
        assertEquals(listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 80f)), lifted.finished)
    }

    @Test
    fun `tapping the corner just placed adds no line of no length`() {
        val first = null.stroke(ShapeKind.POLYGON, Offset(0f, 0f), Offset(0f, 0f))
        assertEquals(1, first.draft!!.points.size)
    }

    @Test
    fun `a polyline and a polygon are written as Rnote writes them`() {
        val points = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 80f))
        val polyline = ShapeDraft.toShape(ShapeKind.POLYLINE, points, black, 2f, RnoteNativeColor.TRANSPARENT)!!
        val json = polyline.raw!!.asJsonObject.getAsJsonObject("shape").getAsJsonObject("polyline")
        assertEquals(2, json.getAsJsonArray("path").size())
        assertEquals(0.0, json.getAsJsonArray("start")[0].asDouble, 1e-6)
        val polygon = ShapeDraft.toShape(ShapeKind.POLYGON, points, black, 2f, RnoteNativeColor.TRANSPARENT)!!
        assertTrue(polygon.raw!!.asJsonObject.getAsJsonObject("shape").has("polygon"))
        assertTrue((polygon.shape as PathShape).ops.last() == PathOp.Close)
        // Two corners are no polygon.
        assertNull(ShapeDraft.toShape(ShapeKind.POLYGON, points.take(2), black, 2f, RnoteNativeColor.TRANSPARENT))
    }

    @Test
    fun `a quadratic curve is a stroke to the control point and one to the end`() {
        var lifted = null.stroke(ShapeKind.QUADBEZ, Offset(0f, 0f), Offset(50f, -80f))
        assertNull(lifted.finished)
        lifted = lifted.draft.stroke(ShapeKind.QUADBEZ, Offset(90f, 10f), Offset(100f, 0f))
        val points = lifted.finished!!
        assertEquals(listOf(Offset(0f, 0f), Offset(50f, -80f), Offset(100f, 0f)), points)
        val curve = ShapeDraft.toShape(ShapeKind.QUADBEZ, points, black, 2f, RnoteNativeColor.TRANSPARENT)!!
        val json = curve.raw!!.asJsonObject.getAsJsonObject("shape").getAsJsonObject("quadbez")
        assertEquals(-80.0, json.getAsJsonArray("cp")[1].asDouble, 1e-6)
    }

    @Test
    fun `a cubic curve takes a stroke per control point and one to the end`() {
        var lifted = null.stroke(ShapeKind.CUBBEZ, Offset(0f, 0f), Offset(30f, -50f))
        lifted = lifted.draft.stroke(ShapeKind.CUBBEZ, Offset(60f, -50f), Offset(70f, 50f))
        assertNull(lifted.finished)
        lifted = lifted.draft.stroke(ShapeKind.CUBBEZ, Offset(90f, 0f), Offset(100f, 0f))
        assertEquals(4, lifted.finished!!.size)
        val curve = ShapeDraft.toShape(ShapeKind.CUBBEZ, lifted.finished!!, black, 2f, RnoteNativeColor.TRANSPARENT)!!
        assertTrue(curve.raw!!.asJsonObject.getAsJsonObject("shape").getAsJsonObject("cubbez").has("cp2"))
    }

    @Test
    fun `an ellipse from two foci and a point is Rnote's ordinary ellipse, turned along the foci`() {
        var lifted = null.stroke(ShapeKind.FOCI_ELLIPSE, Offset(5f, 5f), Offset(0f, 0f))
        assertEquals(listOf(Offset(0f, 0f)), lifted.draft!!.points)
        lifted = lifted.draft.stroke(ShapeKind.FOCI_ELLIPSE, Offset(50f, 0f), Offset(60f, 0f))
        lifted = lifted.draft.stroke(ShapeKind.FOCI_ELLIPSE, Offset(30f, 30f), Offset(30f, 40f))
        val ellipse = ShapeDraft.toShape(ShapeKind.FOCI_ELLIPSE, lifted.finished!!, black, 2f, RnoteNativeColor.TRANSPARENT)!!
        val shape = ellipse.shape as EllipseShape
        // Foci 60 apart, the point 50 from each: semi-axes 50 and 40, centred between the foci.
        assertEquals(50f, shape.radiusX, 1e-2f)
        assertEquals(40f, shape.radiusY, 1e-2f)
        assertEquals(30f, shape.transform[4], 1e-2f)
        assertEquals(0f, shape.transform[5], 1e-2f)

        val upright = NativeEditing.createFociEllipse(0f, 0f, 0f, 60f, 40f, 30f, black, 2f)!!
        val t = (upright.shape as EllipseShape).transform
        // Turned a quarter: the long axis runs down the page.
        assertEquals(0f, t[0], 1e-3f)
        assertEquals(1f, t[1], 1e-3f)
        assertNotNull(upright.raw)
    }

    @Test
    fun `constraints bend each new point from the one before, level and upright always allowed`() {
        val square = ShapeConstraints(enabled = true, ratios = setOf(ConstraintRatio.ONE_TO_ONE))
        val lifted = null.stroke(ShapeKind.POLYLINE, Offset(0f, 0f), Offset(10f, 9f), square)
        assertEquals(Offset(10f, 10f), lifted.draft!!.points.last())
        val level = null.stroke(ShapeKind.POLYLINE, Offset(0f, 0f), Offset(40f, 2f), square)
        assertEquals(Offset(40f, 0f), level.draft!!.points.last())
    }

    @Test
    fun `a shape whose points are all in one spot is no shape`() {
        val same = listOf(Offset(5f, 5f), Offset(5.2f, 5.1f), Offset(5f, 5f))
        assertNull(ShapeDraft.toShape(ShapeKind.QUADBEZ, same, black, 2f, RnoteNativeColor.TRANSPARENT))
    }
}
