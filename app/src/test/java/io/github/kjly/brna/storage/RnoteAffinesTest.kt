package io.github.kjly.brna.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.RectShape
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Rnote 0.15's files, which store a position as glam's flat `"affine"` where 0.14 has a
 * `"transform"`: read where their elements are, and written back as 0.14 writes them,
 * which both versions open — `maj0min15.rs` converts that layout, but gives up on a file
 * marked 0.14 that holds its own.
 */
class RnoteAffinesTest {

    private val eps = 1e-3f

    private val style = """{"font_family":"Microsoft Sans Serif","font_size":20.0,"font_weight":500,""" +
        """"font_style":"regular","color":{"r":0.0,"g":0.0,"b":0.0,"a":1.0},"max_width":684.3,""" +
        """"alignment":"start","ranged_text_attributes":[]}"""
    private val smooth = """{"smooth":{"stroke_width":2.0,"stroke_color":{"r":0.0,"g":0.0,"b":0.0,"a":1.0},""" +
        """"fill_color":{"r":0.0,"g":0.0,"b":0.0,"a":0.0},"pressure_curve":"linear","line_style":"solid","line_cap":"straight"}}"""

    // As Rnote 0.15 writes them.
    private val text015 = """{"textstroke":{"text":"Influenz","affine":[1.0,0.0,0.0,1.0,1386.224,1539.799],"text_style":$style}}"""
    private val bitmap015 = """{"bitmapimage":{"image":{"data":"AAAA/w==",""" +
        """"rectangle":{"cuboid":{"half_extents":[450.0,337.5]},"affine":[1.0,0.0,0.0,1.0,450.0,337.5]},""" +
        """"pixel_width":900,"pixel_height":675,"memory_format":"R8g8b8a8Premultiplied"},""" +
        """"rectangle":{"cuboid":{"half_extents":[450.0,337.5]},"affine":[0.755,0.0,0.0,0.755,989.664,3468.821]}}}"""
    private val rect015 = """{"shapestroke":{"shape":{"rect":{"cuboid":{"half_extents":[20.0,10.0]},""" +
        """"affine":[0.0,1.0,-1.0,0.0,300.0,400.0]}},"style":$smooth}}"""
    private val ellipse015 = """{"shapestroke":{"shape":{"ellipse":{"radii":[30.0,15.0],""" +
        """"affine":[1.0,0.0,0.0,1.0,50.0,60.0]}},"style":$smooth}}"""
    private val vector015 = """{"vectorimage":{"svg_data":"<svg xmlns=\"http://www.w3.org/2000/svg\"/>",""" +
        """"intrinsic_size":[595.0,842.0],"rectangle":{"cuboid":{"half_extents":[198.425,280.797]},""" +
        """"affine":[1.0,0.0,0.0,1.0,230.425,312.797]}}}"""

    private fun affineOf(obj: JsonObject): String = obj.getAsJsonObject("transform").getAsJsonArray("affine").toString()

    @Test
    fun `a text box from Rnote 0_15 is read where it was put`() {
        val text = RnoteNativeParser.parseElementJson(text015) as NativeTextElement
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 1f, 1386.224f, 1539.799f), text.transform, eps)
        assertEquals(1386.224f, text.minX, eps)
        assertEquals(1539.799f, text.minY, eps)

        // Kept for writing back as 0.14 has it, in the place the affine was.
        val raw = text.raw!!.asJsonObject
        assertEquals(listOf("text", "transform", "text_style"), raw.keySet().toList())
        assertEquals("[1.0,0.0,0.0,0.0,1.0,0.0,1386.224,1539.799,1.0]", affineOf(raw))
    }

    @Test
    fun `a bitmap image from Rnote 0_15 keeps its size and place, both rectangles as 0_14 has them`() {
        val image = RnoteNativeParser.parseElementJson(bitmap015) as NativeBitmapElement
        assertArrayEquals(floatArrayOf(0.755f, 0f, 0f, 0.755f, 989.664f, 3468.821f), image.transform, eps)
        assertEquals(989.664f - 450f * 0.755f, image.minX, eps)
        assertEquals(3468.821f + 337.5f * 0.755f, image.maxY, eps)

        val raw = image.raw!!.asJsonObject
        val grid = raw.getAsJsonObject("image").getAsJsonObject("rectangle")
        assertEquals("[1.0,0.0,0.0,0.0,1.0,0.0,450.0,337.5,1.0]", affineOf(grid))
        assertFalse(grid.has("affine"))
        assertEquals("[0.755,0.0,0.0,0.0,0.755,0.0,989.664,3468.821,1.0]", affineOf(raw.getAsJsonObject("rectangle")))
    }

    @Test
    fun `a rect and an ellipse from Rnote 0_15 keep their turn and place`() {
        val rect = RnoteNativeParser.parseElementJson(rect015) as NativeShapeElement
        assertArrayEquals(floatArrayOf(0f, 1f, -1f, 0f, 300f, 400f), (rect.shape as RectShape).transform, eps)
        val rectRaw = rect.raw!!.asJsonObject.getAsJsonObject("shape").getAsJsonObject("rect")
        assertEquals(listOf("cuboid", "transform"), rectRaw.keySet().toList())
        assertEquals("[0.0,1.0,0.0,-1.0,0.0,0.0,300.0,400.0,1.0]", affineOf(rectRaw))

        val ellipse = RnoteNativeParser.parseElementJson(ellipse015) as NativeShapeElement
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 1f, 50f, 60f), (ellipse.shape as EllipseShape).transform, eps)
        val ellipseRaw = ellipse.raw!!.asJsonObject.getAsJsonObject("shape").getAsJsonObject("ellipse")
        assertEquals("[1.0,0.0,0.0,0.0,1.0,0.0,50.0,60.0,1.0]", affineOf(ellipseRaw))
    }

    @Test
    fun `an element as Rnote 0_14 writes it is left exactly as it was`() {
        val json = """{"text":"a","transform":{"affine":[1.0,0.0,0.0,0.0,1.0,0.0,5.0,6.0,1.0]},"text_style":$style}"""
        val tree = JsonParser.parseString(json)
        RnoteAffines.textStroke(tree)
        assertEquals(JsonParser.parseString(json), tree)
        val text = RnoteNativeParser.parseElementJson("""{"textstroke":$json}""") as NativeTextElement
        assertEquals(5f, text.transform[4], 0f)
        assertEquals(6f, text.transform[5], 0f)
    }

    @Test
    fun `an affine is six numbers from 0_15 or nine from 0_14, nothing else`() {
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), RnoteAffines.fromArray(listOf(1f, 2f, 3f, 4f, 5f, 6f)), 0f)
        assertArrayEquals(
            floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
            RnoteAffines.fromArray(listOf(1f, 2f, 0f, 3f, 4f, 0f, 5f, 6f, 1f)),
            0f
        )
        assertNull(RnoteAffines.fromArray(listOf(1f, 2f, 3f)))
    }

    /** A whole file as Rnote 0.15 saves it: a PDF page, a text box, an image and a rect. */
    private fun file015(): ByteArray {
        val elements = listOf(vector015, text015, bitmap015, rect015)
        val strokes = elements.joinToString(",") { """{"value":$it,"version":1}""" }
        val chrono = elements.indices.joinToString(",") { i ->
            val layer = if (i == 0) "\"document\"" else "{\"user_layer\":0}"
            """{"value":{"t":${i + 1},"layer":$layer},"version":1}"""
        }
        val json = """{"version":"0.15.0","data":{"engine_snapshot":{""" +
            """"document":{"config":{"layout":"semi_infinite"},"x":0.0,"y":0.0,"width":2000.0,"height":5000.0},""" +
            """"camera":{"offset":[0.0,0.0],"size":[800.0,600.0],"zoom":1.0},""" +
            """"stroke_components":[{"value":null,"version":0},$strokes],""" +
            """"chrono_components":[{"value":null,"version":0},$chrono],"chrono_counter":${elements.size}}}}"""
        return ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.toByteArray()) } }.toByteArray()
    }

    /**
     * What `maj0min15.rs` needs of a file marked 0.14 to open it: a `transform` on every
     * text box, image rectangle, rect and ellipse — any one missing and the file won't open.
     */
    private fun assertOpensInRnote015(file: JsonObject) {
        fun hasTransform(obj: JsonObject?, what: String) {
            assertNotNull("$what is missing", obj)
            val affine = obj!!.getAsJsonObject("transform")?.getAsJsonArray("affine")
            assertNotNull("$what has no transform", affine)
            assertEquals(9, affine!!.size())
            assertFalse("$what still has 0.15's affine", obj.has("affine"))
        }
        val snapshot = file.getAsJsonObject("data").getAsJsonObject("engine_snapshot")
        for (component in snapshot.getAsJsonArray("stroke_components")) {
            val value = component.asJsonObject.get("value")
            if (value.isJsonNull) continue
            val element = value.asJsonObject
            element.getAsJsonObject("textstroke")?.let { hasTransform(it, "text box") }
            element.getAsJsonObject("vectorimage")?.let { hasTransform(it.getAsJsonObject("rectangle"), "vector image") }
            element.getAsJsonObject("bitmapimage")?.let {
                hasTransform(it.getAsJsonObject("image")?.getAsJsonObject("rectangle"), "pixel grid")
                hasTransform(it.getAsJsonObject("rectangle"), "bitmap image")
            }
            element.getAsJsonObject("shapestroke")?.getAsJsonObject("shape")?.let { shape ->
                shape.getAsJsonObject("rect")?.let { hasTransform(it, "rect") }
                shape.getAsJsonObject("ellipse")?.let { hasTransform(it, "ellipse") }
            }
        }
    }

    @Test
    fun `a file from Rnote 0_15 opens with everything in place and is saved as 0_14 writes it`() {
        val doc = RnoteNativeParser.parse(ByteArrayInputStream(file015()))
        val page = doc.elements.filterIsInstance<NativeVectorImageElement>().single()
        // Rnote's PDF import puts a page 32 into the view; the rect is centred on that.
        assertEquals(32f, page.minX, eps)
        assertEquals(32f, page.minY, eps)
        assertEquals(1386.224f, doc.elements.filterIsInstance<NativeTextElement>().single().transform[4], eps)

        val saved = ByteArrayOutputStream().also { RnoteNativeSerializer.serialize(it, doc) }.toByteArray()
        val json = JsonParser.parseString(GZIPInputStream(ByteArrayInputStream(saved)).readBytes().toString(Charsets.UTF_8)).asJsonObject
        assertEquals("0.14.2", json.get("version").asString)
        assertOpensInRnote015(json)

        // And read again, everything is where it was.
        val again = RnoteNativeParser.parse(ByteArrayInputStream(saved))
        assertEquals(32f, again.elements.filterIsInstance<NativeVectorImageElement>().single().minX, eps)
        assertArrayEquals(
            floatArrayOf(0.755f, 0f, 0f, 0.755f, 989.664f, 3468.821f),
            again.elements.filterIsInstance<NativeBitmapElement>().single().transform,
            eps
        )
        assertTrue(again.elements.filterIsInstance<NativeShapeElement>().single().shape is RectShape)
    }
}
