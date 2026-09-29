package io.github.kjly.brna.storage

import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RnoteNativeDocument
import io.github.kjly.brna.model.RoughFillStyle
import io.github.kjly.brna.model.RoughStyle
import io.github.kjly.brna.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * A rough shape's wobble comes from its seed alone, so the seed — a u64, past what a Long
 * or a double holds — has to reach the file exactly, and the style has to stay `rough`,
 * with its fill style and hachure angle, through every edit and every save.
 */
class RoughRoundTripTest {

    private val black = RnoteNativeColor.BLACK
    private val blue = RnoteNativeColor(0.2f, 0.4f, 0.6f, 1f)

    /** Rnote's largest seed, the one most likely to lose digits on the way. */
    private val maxSeed = -1L

    private fun roughRect(style: RoughStyle, fill: RnoteNativeColor = blue) =
        NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 100f, 60f, black, 2.4f, fill, rough = style)!!

    @Test
    fun `a rough shape is written as Rnote's shaper writes one`() {
        val rect = roughRect(RoughStyle(RoughFillStyle.DOTS, RoughStyle.hachureAngleOf(-41), maxSeed))
        val json = rect.raw.toString()
        // Rnote 0.14's `RoughOptions`, in its order, the angle to three places.
        assertTrue(json, json.contains(""""style":{"rough":{"stroke_color":"""))
        assertTrue(json, json.contains(""""stroke_width":2.400,"fill_color":"""))
        assertTrue(json, json.contains(""""fill_style":"dots","hachure_angle":-0.716,"seed":18446744073709551615}}"""))
        // And read back as the renderer will draw it.
        assertEquals(RoughStyle(RoughFillStyle.DOTS, -0.716, maxSeed), rect.rough)
    }

    @Test
    fun `a smooth shape stays smooth`() {
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 100f, 60f, black, 2f)!!
        assertNull(rect.rough)
        assertTrue(rect.raw.toString().contains(""""style":{"smooth":{"""))
    }

    @Test
    fun `a rough shape saved from desktop Rnote is read with its style`() {
        val el = RnoteNativeParser.parseElementJson(
            """{"shapestroke":{"shape":{"ellipse":{"radii":[50.0,30.0],"transform":{"affine":""" +
                """[1.0,0.0,0.0,0.0,1.0,0.0,100.0,100.0,1.0]}}},"style":{"rough":{"stroke_color":""" +
                """{"r":0.1,"g":0.2,"b":0.3,"a":1.0},"stroke_width":3.0,"fill_color":{"r":0.5,"g":0.5,""" +
                """"b":0.5,"a":1.0},"fill_style":"crosshatch","hachure_angle":0.5,"seed":9223372036854775808}}}}"""
        ) as NativeShapeElement
        assertTrue(el.shape is EllipseShape)
        assertEquals(RoughStyle(RoughFillStyle.CROSSHATCH, 0.5, Long.MIN_VALUE), el.rough)
        assertEquals(3f, el.strokeWidth, 0f)
        assertEquals(0.5f, el.fillColor.g, 0f)
    }

    @Test
    fun `an old rough shape reads as Rnote reads it`() {
        // No width, no seed, no line colour, and the fill style Rnote wrote before 0.5.9.
        val el = RnoteNativeParser.parseElementJson(
            """{"shapestroke":{"shape":{"rect":{"cuboid":{"half_extents":[10.0,10.0]},"transform":""" +
                """{"affine":[1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0]}}},"style":{"rough":{"stroke_color":null,""" +
                """"fill_color":null,"fill_style":"Hachure","seed":null}}}}"""
        ) as NativeShapeElement
        assertEquals(RoughStyle(RoughFillStyle.SOLID, RoughStyle.HACHURE_ANGLE_DEFAULT, null), el.rough)
        // roughr's own line colour, and Rnote's rough default width.
        assertEquals(RnoteNativeColor.BLACK, el.color)
        assertEquals(RoughStyle.STROKE_WIDTH_DEFAULT, el.strokeWidth, 0f)
        assertEquals(0f, el.fillColor.a, 0f)
    }

    @Test
    fun `moving and recolouring keep the style and its seed`() {
        val style = RoughStyle(RoughFillStyle.ZIG_ZAG, -0.716, maxSeed)
        val rect = roughRect(style)
        val moved = NativeEditing.translate(rect, 5f, 7f) as NativeShapeElement
        assertEquals(style, moved.rough)
        val red = RnoteNativeColor(1f, 0f, 0f, 1f)
        val recoloured = NativeEditing.withStrokeColor(moved, red) as NativeShapeElement
        assertEquals(style, recoloured.rough)
        assertEquals(red, recoloured.color)
        val json = recoloured.raw.toString()
        assertTrue(json, json.contains(""""seed":18446744073709551615"""))
    }

    @Test
    fun `a rough shape survives a save and a reload`() {
        val rect = roughRect(RoughStyle(RoughFillStyle.HACHURE, -0.716, 12345L))
        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, RnoteNativeDocument(elements = listOf(rect))) }
            .toByteArray()
        val reread = RnoteNativeParser.parse(ByteArrayInputStream(bytes))
            .elements.filterIsInstance<NativeShapeElement>().single()
        assertEquals(rect.rough, reread.rough)
        assertTrue(reread.shape is RectShape)
    }

    @Test
    fun `a rough shape without JSON of its own is still written rough`() {
        val bare = NativeShapeElement(
            shape = RectShape(10f, 10f, floatArrayOf(1f, 0f, 0f, 1f, 20f, 20f)),
            color = black, strokeWidth = 2.4f,
            minX = 10f, minY = 10f, maxX = 30f, maxY = 30f,
            rough = RoughStyle(RoughFillStyle.DASHED, -0.716, maxSeed)
        )
        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, RnoteNativeDocument(elements = listOf(bare))) }
            .toByteArray()
        val json = GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().toString(Charsets.UTF_8)
        assertTrue(json, json.contains(""""fill_style":"dashed","hachure_angle":-0.716,"seed":18446744073709551615}}"""))
        val reread = RnoteNativeParser.parse(ByteArrayInputStream(bytes))
            .elements.filterIsInstance<NativeShapeElement>().single()
        assertEquals(bare.rough, reread.rough)
    }
}
