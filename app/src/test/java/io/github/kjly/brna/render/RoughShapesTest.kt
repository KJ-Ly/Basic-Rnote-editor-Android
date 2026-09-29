package io.github.kjly.brna.render

import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.LineShape
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeShapeKind
import io.github.kjly.brna.model.PathOp
import io.github.kjly.brna.model.PathShape
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RoughFillStyle
import io.github.kjly.brna.model.RoughStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of roughr's shapes each shape is drawn as, and with what: Rnote's rough composer.
 * The rectangle is checked against roughr's own numbers for it (see [RoughGeneratorTest]),
 * so the element's width and angle reach roughr exactly as Rnote passes them.
 */
class RoughShapesTest {

    private val black = RnoteNativeColor.BLACK
    private val blue = RnoteNativeColor(0.2f, 0.4f, 0.6f, 1f)

    private fun element(
        shape: NativeShapeKind,
        rough: RoughStyle? = RoughStyle(RoughFillStyle.HACHURE, RoughStyle.HACHURE_ANGLE_DEFAULT, 11L),
        fill: RnoteNativeColor = RnoteNativeColor.TRANSPARENT
    ) = NativeShapeElement(shape, black, 2.4f, 0f, 0f, 1f, 1f, fill, rough = rough)

    @Test
    fun `a rectangle is drawn about its centre, then moved there`() {
        val transform = floatArrayOf(1f, 0f, 0f, 1f, 200f, 100f)
        val composed = RoughShapes.compose(element(RectShape(50f, 30f, transform), fill = blue))!!
        assertArrayEquals(transform, composed.transform, 0f)
        val sets = composed.drawables.single().sets
        // roughr's rectangle(-50, -30, 100, 60) with seed 11: hachure first, then the outline.
        assertEquals(RoughSetType.FILL_SKETCH, sets[0].type)
        assertEquals(48, sets[0].ops.size)
        assertEquals(RoughSetType.PATH, sets[1].type)
        assertEquals(16, sets[1].ops.size)
        val start = sets[1].ops.first() as RoughOp.Move
        assertEquals(-51.23586565256119, start.x, 1e-9)
        assertEquals(-30.251023292541504, start.y, 1e-9)
    }

    @Test
    fun `without a fill colour there is only the outline`() {
        val composed = RoughShapes.compose(element(RectShape(50f, 30f)))!!
        assertEquals(listOf(RoughSetType.PATH), composed.drawables.single().sets.map { it.type })
    }

    @Test
    fun `an ellipse is drawn at the origin with its full size`() {
        val composed = RoughShapes.compose(element(EllipseShape(50f, 30f, floatArrayOf(1f, 0f, 0f, 1f, 5f, 6f))))!!
        assertEquals("ellipse", composed.drawables.single().shape)
        assertEquals(5f, composed.transform!![4], 0f)
        // roughr's ellipse(0, 0, 100, 60): its outline stays within the radii and a wobble.
        for (op in composed.drawables.single().sets.single().ops) {
            val (x, y) = when (op) {
                is RoughOp.Move -> op.x to op.y
                is RoughOp.Line -> op.x to op.y
                is RoughOp.Curve -> op.x to op.y
            }
            assertTrue(x in -60.0..60.0 && y in -40.0..40.0)
        }
    }

    @Test
    fun `a line is drawn where it lies`() {
        val composed = RoughShapes.compose(element(LineShape(10f, 20f, 200f, 120f)))!!
        assertNull(composed.transform)
        assertEquals("line", composed.drawables.single().shape)
    }

    @Test
    fun `an arrow is its stem and then its head, each from the seed afresh`() {
        val arrow = PathShape(listOf(
            PathOp.MoveTo(0f, 0f), PathOp.LineTo(100f, 0f),
            PathOp.MoveTo(90f, -5f), PathOp.LineTo(100f, 0f), PathOp.LineTo(90f, 5f)
        ))
        val composed = RoughShapes.compose(element(arrow))!!
        assertEquals(listOf("line", "linear_path"), composed.drawables.map { it.shape })
        // A stem as roughr draws the line on its own, with the same seed.
        val alone = RoughGenerator.line(0.0, 0.0, 100.0, 0.0, RoughShapes.optionsFor(element(arrow), element(arrow).rough!!))
        assertEquals(alone.sets.single().ops, composed.drawables[0].sets.single().ops)
    }

    @Test
    fun `curves, polylines and polygons are told apart by their outline`() {
        val quad = PathShape(listOf(PathOp.MoveTo(0f, 0f), PathOp.QuadTo(50f, -60f, 100f, 0f)))
        val cubic = PathShape(listOf(PathOp.MoveTo(0f, 0f), PathOp.CubicTo(30f, -40f, 70f, 40f, 100f, 0f)))
        val corners = listOf(PathOp.MoveTo(0f, 0f), PathOp.LineTo(80f, 10f), PathOp.LineTo(60f, 70f))
        val polyline = PathShape(corners)
        val polygon = PathShape(corners + PathOp.Close)
        assertEquals("curve", RoughShapes.compose(element(quad))!!.drawables.single().shape)
        assertEquals("curve", RoughShapes.compose(element(cubic))!!.drawables.single().shape)
        assertEquals("linear_path", RoughShapes.compose(element(polyline))!!.drawables.single().shape)
        val drawnPolygon = RoughShapes.compose(element(polygon))!!.drawables.single()
        assertEquals("polygon", drawnPolygon.shape)
        // rough_piet fills polygons and curves even-odd.
        assertTrue(drawnPolygon.evenOdd)
        assertFalse(RoughShapes.compose(element(RectShape(5f, 5f)))!!.drawables.single().evenOdd)
    }

    @Test
    fun `a smooth shape has no rough drawing`() {
        assertNull(RoughShapes.compose(element(RectShape(5f, 5f), rough = null)))
    }

    @Test
    fun `options come from the shape as Rnote hands them to roughr`() {
        val el = element(RectShape(5f, 5f), fill = blue)
        val options = RoughShapes.optionsFor(el, RoughStyle(RoughFillStyle.DOTS, -0.716, null))
        assertEquals(2.4f, options.strokeWidth, 0f)
        assertEquals((-0.716 * 180.0 / Math.PI).toFloat(), options.hachureAngle, 1e-6f)
        assertEquals(RoughFill.DOTS, options.fillStyle)
        assertTrue(options.fill)
        // No seed of its own: roughr's.
        assertEquals(345L, options.seed)
    }

    @Test
    fun `a rough shape keeps room round it for its wobble`() {
        assertEquals(1.2f + 20f, RoughShapes.margin(element(RectShape(5f, 5f))), 1e-6f)
        assertEquals(1.2f, RoughShapes.margin(element(RectShape(5f, 5f), rough = null)), 1e-6f)
    }
}
