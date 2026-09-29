package io.github.kjly.brna.storage

import io.github.kjly.brna.model.ShapeLineStyle
import io.github.kjly.brna.model.ShapeLine
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.PathShape
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEditingTest {

    private val black = RnoteNativeColor(0f, 0f, 0f, 1f)

    @Test
    fun `a dragged rectangle fills the dragged box`() {
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 10f, 20f, 110f, 70f, black, 2f)!!
        val shape = rect.shape as RectShape
        assertEquals(50f, shape.halfExtentX, 1e-3f)
        assertEquals(25f, shape.halfExtentY, 1e-3f)
        assertEquals(60f, shape.transform[4], 1e-3f)
        assertEquals(45f, shape.transform[5], 1e-3f)
        assertEquals(10f, rect.minX, 1e-3f)
        assertEquals(70f, rect.maxY, 1e-3f)
    }

    @Test
    fun `a new arrow is written as Rnote's arrow, not a polyline`() {
        val arrow = NativeEditing.createShape(ShapeKind.ARROW, 0f, 0f, 100f, 0f, black, 2f)!!
        assertTrue(arrow.shape is PathShape)
        val raw = arrow.raw!!.asJsonObject.getAsJsonObject("shape")
        assertTrue(raw.has("arrow"))
    }

    @Test
    fun `a shape is written with the fill it is drawn with`() {
        val blue = RnoteNativeColor(0.2f, 0.4f, 0.8f, 1f)
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 10f, 10f, black, 2f, blue)!!
        assertEquals(blue, rect.fillColor)
        val fill = rect.raw!!.asJsonObject.getAsJsonObject("style").getAsJsonObject("smooth").getAsJsonObject("fill_color")
        assertEquals(0.8, fill.get("b").asDouble, 1e-6)
        assertEquals(1.0, fill.get("a").asDouble, 1e-6)
        // Without one, Rnote's "no fill".
        val plain = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 10f, 10f, black, 2f)!!
        assertEquals(RnoteNativeColor.TRANSPARENT, plain.fillColor)
    }

    @Test
    fun `recolouring a shape changes only its colour, in the JSON written back`() {
        val red = RnoteNativeColor(1f, 0f, 0f, 1f)
        val green = RnoteNativeColor(0f, 1f, 0f, 1f)
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 10f, 10f, black, 3f)!!
        val lined = NativeEditing.withStrokeColor(rect, red) as NativeShapeElement
        val filled = NativeEditing.withFillColor(lined, green) as NativeShapeElement
        assertEquals(red, filled.color)
        assertEquals(green, filled.fillColor)
        val style = filled.raw!!.asJsonObject.getAsJsonObject("style").getAsJsonObject("smooth")
        assertEquals(1.0, style.getAsJsonObject("stroke_color").get("r").asDouble, 1e-6)
        assertEquals(1.0, style.getAsJsonObject("fill_color").get("g").asDouble, 1e-6)
        assertEquals(3.0, style.get("stroke_width").asDouble, 1e-6)
        assertEquals(rect.minX, filled.minX, 1e-3f)
        // Taking the fill off again is Rnote's transparent, not a missing field.
        val cleared = NativeEditing.withFillColor(filled, RnoteNativeColor.TRANSPARENT) as NativeShapeElement
        assertEquals(0f, cleared.fillColor.a, 0f)
    }

    @Test
    fun `recolouring text changes its colour and keeps its text`() {
        val red = RnoteNativeColor(1f, 0f, 0f, 1f)
        val text = NativeEditing.createText("Hallo", 10f, 20f, 32f, black, 600f)!!
        val recolored = NativeEditing.withStrokeColor(text, red) as NativeTextElement
        assertEquals(red, recolored.color)
        assertEquals("Hallo", recolored.text)
        assertEquals(10f, recolored.transform[4], 1e-3f)
        // Text has no fill; nor do pictures, and they come back as they were.
        assertTrue(NativeEditing.withFillColor(text, red) === text)
    }

    @Test
    fun `a tap is not a shape`() {
        assertNull(NativeEditing.createShape(ShapeKind.LINE, 5f, 5f, 5.2f, 5.1f, black, 2f))
    }

    @Test
    fun `moving a shape moves its written JSON too, but not its style`() {
        val line = NativeEditing.createShape(ShapeKind.LINE, 0f, 0f, 10f, 0f, black, 3f)!!
        val moved = NativeEditing.translate(line, 5f, 7f) as NativeShapeElement
        val shape = moved.raw!!.asJsonObject.getAsJsonObject("shape").getAsJsonObject("line")
        assertEquals(5.0, shape.getAsJsonArray("start")[0].asDouble, 1e-6)
        assertEquals(7.0, shape.getAsJsonArray("start")[1].asDouble, 1e-6)
        assertEquals(15.0, shape.getAsJsonArray("end")[0].asDouble, 1e-6)
        val style = moved.raw!!.asJsonObject.getAsJsonObject("style").getAsJsonObject("smooth")
        assertEquals(3.0, style.get("stroke_width").asDouble, 1e-6)
        // The original is untouched: undo keeps pointing at it.
        assertEquals(0.0, line.raw!!.asJsonObject.getAsJsonObject("shape")
            .getAsJsonObject("line").getAsJsonArray("start")[0].asDouble, 1e-6)
    }

    @Test
    fun `moving a text box shifts its transform and keeps its style`() {
        val json = """{"textstroke":{"text":"Hi","transform":{"affine":[1.0,0.0,0.0,0.0,1.0,0.0,10.0,20.0,1.0]},""" +
            """"text_style":{"font_family":"serif","font_size":32.0,"font_weight":700,"font_style":"regular",""" +
            """"color":{"r":0.0,"g":0.0,"b":0.0,"a":1.0},"max_width":null,"alignment":"start","ranged_text_attributes":[]}}}"""
        val text = RnoteNativeParser.parseElementJson(json) as NativeTextElement
        val moved = NativeEditing.translate(text, 100f, 50f) as NativeTextElement
        assertEquals(110f, moved.transform[4], 1e-3f)
        assertEquals(70f, moved.transform[5], 1e-3f)
        val affine = moved.raw!!.asJsonObject.getAsJsonObject("transform").getAsJsonArray("affine")
        assertEquals(110.0, affine[6].asDouble, 1e-6)
        assertEquals(70.0, affine[7].asDouble, 1e-6)
        assertEquals(32.0, moved.raw!!.asJsonObject.getAsJsonObject("text_style").get("font_size").asDouble, 1e-6)
    }

    @Test
    fun `moving an image moves both of its rectangles`() {
        val rect = """{"cuboid":{"half_extents":[50.0,50.0]},"transform":{"affine":[1.0,0.0,0.0,0.0,1.0,0.0,300.0,300.0,1.0]}}"""
        val json = """{"bitmapimage":{"image":{"data":"AAAAAAAAAAAAAAAAAAAAAA==","rectangle":$rect,""" +
            """"pixel_width":2,"pixel_height":2,"memory_format":"R8g8b8a8Premultiplied"},"rectangle":$rect}}"""
        val image = RnoteNativeParser.parseElementJson(json) as NativeBitmapElement
        val moved = NativeEditing.translate(image, -100f, 10f) as NativeBitmapElement
        assertEquals(200f, moved.rect!!.transform[4], 1e-3f)
        val raw = moved.raw!!.asJsonObject
        for (r in listOf(raw.getAsJsonObject("rectangle"), raw.getAsJsonObject("image").getAsJsonObject("rectangle"))) {
            val affine = r.getAsJsonObject("transform").getAsJsonArray("affine")
            assertEquals(200.0, affine[6].asDouble, 1e-6)
            assertEquals(310.0, affine[7].asDouble, 1e-6)
            assertEquals(50.0, r.getAsJsonObject("cuboid").getAsJsonArray("half_extents")[0].asDouble, 1e-6)
        }
    }

    /** 2 × 1 pixels of premultiplied RGBA: opaque red, then half-transparent white. */
    private val twoPixels = java.util.Base64.getEncoder().encodeToString(
        byteArrayOf(-1, 0, 0, -1, -128, -128, -128, -128)
    )

    @Test
    fun `a new image is written the way Rnote 0_14 writes one`() {
        val image = NativeEditing.createImage(twoPixels, 2, 1, NativeEditing.ImagePlacement(100f, 50f, 20f))!!
        val raw = image.raw!!.asJsonObject
        val inner = raw.getAsJsonObject("image")
        assertEquals(twoPixels, inner.get("data").asString)
        assertEquals(2, inner.get("pixel_width").asInt)
        assertEquals(1, inner.get("pixel_height").asInt)
        assertEquals("R8g8b8a8Premultiplied", inner.get("memory_format").asString)
        // As BitmapImage::from_image_bytes: the rectangle is the pixel grid, the scale and
        // the position are in its transform.
        val rect = raw.getAsJsonObject("rectangle")
        assertEquals(1.0, rect.getAsJsonObject("cuboid").getAsJsonArray("half_extents")[0].asDouble, 1e-6)
        assertEquals(0.5, rect.getAsJsonObject("cuboid").getAsJsonArray("half_extents")[1].asDouble, 1e-6)
        val affine = rect.getAsJsonObject("transform").getAsJsonArray("affine")
        assertEquals(20.0, affine[0].asDouble, 1e-6)
        assertEquals(20.0, affine[4].asDouble, 1e-6)
        assertEquals(120.0, affine[6].asDouble, 1e-6)
        assertEquals(60.0, affine[7].asDouble, 1e-6)
        // Read back as the 0.14 form, covering 40 × 20 from its top-left corner.
        assertEquals(twoPixels, image.rgbaBase64)
        assertEquals(100f, image.minX, 1e-3f)
        assertEquals(50f, image.minY, 1e-3f)
        assertEquals(140f, image.maxX, 1e-3f)
        assertEquals(70f, image.maxY, 1e-3f)
    }

    @Test
    fun `an image without pixels is not created`() {
        assertNull(NativeEditing.createImage("", 0, 0, NativeEditing.ImagePlacement(0f, 0f, 1f)))
    }

    @Test
    fun `an image that fits goes in at its own size, offset into the view`() {
        val p = NativeEditing.placeImage(200, 100, 1000f, 2000f, 3000f, 4000f, 32f, null, clampToOrigin = true)
        assertEquals(1032f, p.x, 1e-3f)
        assertEquals(2032f, p.y, 1e-3f)
        assertEquals(1f, p.scale, 1e-6f)
    }

    @Test
    fun `a large image shrinks to fit the view and the page`() {
        // A view of 800 × 600 with 32 kept free all round leaves 736 × 536.
        val inView = NativeEditing.placeImage(1600, 1200, 0f, 0f, 800f, 600f, 32f, null, clampToOrigin = true)
        assertEquals(536f / 1200f, inView.scale, 1e-6f)
        // On a page 400 wide, its right edge is the tighter limit.
        val onPage = NativeEditing.placeImage(1600, 1200, 0f, 0f, 800f, 600f, 32f, 400f, clampToOrigin = true)
        assertEquals((400f - 32f) / 1600f, onPage.scale, 1e-6f)
    }

    @Test
    fun `an image lands no further up or left than the origin, unless the layout is infinite`() {
        val bounded = NativeEditing.placeImage(10, 10, -500f, -300f, 500f, 300f, 32f, null, clampToOrigin = true)
        assertEquals(0f, bounded.x, 1e-3f)
        assertEquals(0f, bounded.y, 1e-3f)
        val infinite = NativeEditing.placeImage(10, 10, -500f, -300f, 500f, 300f, 32f, null, clampToOrigin = false)
        assertEquals(-468f, infinite.x, 1e-3f)
        assertEquals(-268f, infinite.y, 1e-3f)
    }

    @Test
    fun `the eraser hits a rectangle's outline but not its empty inside`() {
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 100f, 100f, black, 2f)!!
        assertTrue(NativeEditing.eraserHits(rect, -3f, 40f, 3f, 46f))
        assertFalse(NativeEditing.eraserHits(rect, 45f, 45f, 55f, 55f))
        assertFalse(NativeEditing.eraserHits(rect, 200f, 200f, 210f, 210f))
    }

    @Test
    fun `the lasso takes only what lies wholly inside it`() {
        val small = NativeEditing.createShape(ShapeKind.RECTANGLE, 10f, 10f, 20f, 20f, black, 2f)!!
        val big = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 500f, 500f, black, 2f)!!
        val lasso = listOf(0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f)
        assertTrue(NativeEditing.insideLasso(small, lasso))
        assertFalse(NativeEditing.insideLasso(big, lasso))
    }

    @Test
    fun `a shape without colours is read as transparent rather than failing`() {
        val json = """{"shapestroke":{"shape":{"line":{"start":[0.0,0.0],"end":[5.0,5.0]}},""" +
            """"style":{"smooth":{"stroke_width":2.0,"stroke_color":{"r":0.0,"g":0.0,"b":0.0,"a":1.0},""" +
            """"fill_color":null,"pressure_curve":"const","line_style":"solid","line_cap":"straight"}}}}"""
        val shape = RnoteNativeParser.parseElementJson(json) as NativeShapeElement
        assertEquals(0f, shape.fillColor.a, 0f)
    }

    // ── Typewriter ────────────────────────────────────────────────────────────

    @Test
    fun `new text is written the way Rnote's typewriter writes it`() {
        val text = NativeEditing.createText("Hallo \"Welt\"\nZeile 2", 100f, 200f, 32f, black, 600f)!!
        assertEquals("Hallo \"Welt\"\nZeile 2", text.text)
        assertEquals(32f, text.fontSize, 0f)
        assertEquals(100f, text.transform[4], 1e-3f)
        assertEquals(200f, text.transform[5], 1e-3f)
        assertEquals(100f, text.minX, 1e-3f)
        assertEquals(200f, text.minY, 1e-3f)
        val raw = text.raw!!.asJsonObject
        val style = raw.getAsJsonObject("text_style")
        assertEquals("serif", style.get("font_family").asString)
        assertEquals(600.0, style.get("max_width").asDouble, 1e-6)
        assertEquals(9, raw.getAsJsonObject("transform").getAsJsonArray("affine").size())
        assertTrue(style.getAsJsonArray("ranged_text_attributes").isEmpty)
    }

    @Test
    fun `blank text makes no text box`() {
        assertNull(NativeEditing.createText("  \n ", 0f, 0f, 32f, black, null))
    }

    @Test
    fun `short text is only as wide as it is, not as wide as its wrap width`() {
        val text = NativeEditing.createText("Hi", 0f, 0f, 20f, black, 600f)!!
        assertTrue(text.maxX < 100f)
    }

    @Test
    fun `editing text keeps its position, style and formatting`() {
        val json = """{"textstroke":{"text":"ab fett cd","transform":{"affine":[1.0,0.0,0.0,0.0,1.0,0.0,5.0,7.0,1.0]},""" +
            """"text_style":{"font_family":"Cantarell","font_size":18.0,"font_weight":500,"font_style":"regular",""" +
            """"color":{"r":1.0,"g":0.0,"b":0.0,"a":1.0},"max_width":300.0,"alignment":"center",""" +
            """"ranged_text_attributes":[{"range":{"start":3,"end":7},"attribute":{"font_weight":700}},""" +
            """{"range":{"start":8,"end":10},"attribute":{"underline":true}}]}}}"""
        val original = RnoteNativeParser.parseElementJson(json) as NativeTextElement
        val edited = NativeEditing.withText(original, "XYab fett cd")!!
        assertEquals("XYab fett cd", edited.text)
        assertEquals("Cantarell", edited.fontFamily)
        assertEquals("center", edited.alignment)
        assertEquals(5f, edited.transform[4], 1e-3f)
        val ranges = edited.raw!!.asJsonObject.getAsJsonObject("text_style").getAsJsonArray("ranged_text_attributes")
        assertEquals(2, ranges.size())
        val bold = ranges[0].asJsonObject.getAsJsonObject("range")
        assertEquals(5, bold.get("start").asInt)
        assertEquals(9, bold.get("end").asInt)
        // The original is left as it was: undo holds on to it.
        assertEquals("ab fett cd", original.raw!!.asJsonObject.get("text").asString)
    }

    @Test
    fun `typing inside a formatted range extends it, deleting it drops it`() {
        val ranges = com.google.gson.JsonParser.parseString(
            """[{"range":{"start":3,"end":7},"attribute":{"font_weight":700}}]"""
        ).asJsonArray
        val grown = NativeEditing.shiftRanges(ranges, "ab fett cd", "ab feeett cd")
        assertEquals(9, grown[0].asJsonObject.getAsJsonObject("range").get("end").asInt)
        assertEquals(0, NativeEditing.shiftRanges(ranges, "ab fett cd", "ab  cd").size())
    }

    @Test
    fun `ranges count UTF-8 bytes, as Rust does`() {
        // "text" starts at byte 4 of "abc text" and at byte 5 of "äbc text": ä is two bytes.
        val shifted = NativeEditing.shiftRanges(
            com.google.gson.JsonParser.parseString("""[{"range":{"start":4,"end":8},"attribute":{"underline":true}}]""").asJsonArray,
            "abc text", "äbc text"
        )
        assertEquals(5, shifted[0].asJsonObject.getAsJsonObject("range").get("start").asInt)
        assertEquals(9, shifted[0].asJsonObject.getAsJsonObject("range").get("end").asInt)
    }

    @Test
    fun `emptying a text box removes it`() {
        val text = NativeEditing.createText("weg", 0f, 0f, 32f, black, null)!!
        assertNull(NativeEditing.withText(text, "   "))
    }

    @Test
    fun `a tap finds the topmost text box under it`() {
        val below = NativeEditing.createText("unten", 0f, 0f, 32f, black, null)!!
        val above = NativeEditing.createText("oben", 10f, 5f, 32f, black, null)!!
        assertTrue(NativeEditing.textAt(listOf(below, above), 20f, 20f) === above)
        assertNull(NativeEditing.textAt(listOf(below, above), 500f, 500f))
    }

    @Test
    fun `text typed on a page wraps at its right edge`() {
        assertEquals(600f, NativeEditing.typewriterWrapWidth(50f, 793.7f), 0f)
        assertEquals(793.7f - 400f - 20f, NativeEditing.typewriterWrapWidth(400f, 793.7f), 1e-3f)
        // Too close to the edge for a useful column: the default width instead.
        assertEquals(600f, NativeEditing.typewriterWrapWidth(700f, 793.7f), 0f)
        // Pages repeat: the same holds on the page to the right.
        assertEquals(793.7f - 400f - 20f, NativeEditing.typewriterWrapWidth(793.7f + 400f, 793.7f), 1e-2f)
    }

    @Test
    fun `new text survives a save and a reload`() {
        val text = NativeEditing.createText("Mathe\nAufgabe 1", 30f, 40f, 24f, black, 500f)!!
        val reread = RnoteNativeParser.parseElementJson(
            com.google.gson.JsonObject().apply { add("textstroke", text.raw) }.toString()
        ) as NativeTextElement
        assertEquals(text.text, reread.text)
        assertEquals(24f, reread.fontSize, 0f)
        assertEquals(500f, reread.maxWidth!!, 0f)
    }

    @Test
    fun `text is aligned as Rnote aligns it, new or changed, and nothing else moves`() {
        val centred = NativeEditing.createText("Mitte", 0f, 0f, 32f, black, 400f, "center")!!
        assertEquals("center", centred.alignment)
        val bold = NativeEditing.toggleFormat(centred, 0, 3, io.github.kjly.brna.model.TextToggle.BOLD)
        val filled = NativeEditing.withAlignment(bold, "fill")
        assertEquals("fill", filled.alignment)
        assertEquals("Mitte", filled.text)
        assertEquals(bold.ranges, filled.ranges)
        val style = filled.raw!!.asJsonObject.getAsJsonObject("text_style")
        assertEquals("fill", style.get("alignment").asString)
        assertEquals(400.0, style.get("max_width").asDouble, 1e-6)
    }

    @Test
    fun `a shape is written with the line style and cap it is drawn with`() {
        val dotted = ShapeLine().withStyle(ShapeLineStyle.DOTTED)
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 10f, 10f, black, 2f, line = dotted)!!
        val style = rect.raw!!.asJsonObject.getAsJsonObject("style").getAsJsonObject("smooth")
        assertEquals("dotted", style.get("line_style").asString)
        assertEquals("rounded", style.get("line_cap").asString)
        // Read back as the renderer will draw it.
        assertEquals("dotted", rect.lineStyle)
        assertTrue(rect.roundCap)
    }

    @Test
    fun `a shape drawn without picking a line style is solid and straight, as before`() {
        val line = NativeEditing.createShape(ShapeKind.LINE, 0f, 0f, 10f, 0f, black, 2f)!!
        val style = line.raw!!.asJsonObject.getAsJsonObject("style").getAsJsonObject("smooth")
        assertEquals("solid", style.get("line_style").asString)
        assertEquals("straight", style.get("line_cap").asString)
    }

    @Test
    fun `inverting a shape turns its line and fill, in the JSON written back`() {
        val white = RnoteNativeColor(1f, 1f, 1f, 1f)
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 10f, 10f, black, 2f, white)!!
        val inverted = NativeEditing.withInvertedColors(rect) as NativeShapeElement
        assertEquals(1f, inverted.color.r, 2e-3f)
        assertEquals(0f, inverted.fillColor.g, 2e-3f)
        val style = inverted.raw!!.asJsonObject.getAsJsonObject("style").getAsJsonObject("smooth")
        assertEquals(1.0, style.getAsJsonObject("stroke_color").get("b").asDouble, 2e-3)
        assertEquals(0.0, style.getAsJsonObject("fill_color").get("r").asDouble, 2e-3)
    }

    @Test
    fun `inverting a shape with no fill leaves it without one`() {
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 10f, 10f, black, 2f)!!
        val inverted = NativeEditing.withInvertedColors(rect) as NativeShapeElement
        assertEquals(0f, inverted.fillColor.a, 0f)
    }

    @Test
    fun `with borders respected, an image stops short of the next page border`() {
        // A 200 x 50 image 10 in from the top-left of a 100 x 100 page, the view far larger.
        fun place(borders: NativeEditing.PageBorders?) = NativeEditing.placeImage(
            200, 50, 0f, 0f, 1000f, 1000f, offset = 10f,
            fixedPageWidth = null, clampToOrigin = true, borders = borders
        )
        assertEquals(1f, place(null).scale, 1e-6f)
        // Rnote's `calculate_resize_ratio`: 90 to the right-hand border for 200 pixels.
        assertEquals(0.45f, place(NativeEditing.PageBorders(100f, 100f)).scale, 1e-6f)
        // Past the first page, the next border is the one after.
        val further = NativeEditing.placeImage(
            200, 50, 150f, 0f, 1000f, 1000f, offset = 10f,
            fixedPageWidth = null, clampToOrigin = true, borders = NativeEditing.PageBorders(100f, 100f)
        )
        assertEquals(40f / 200f, further.scale, 1e-6f)
    }
}
