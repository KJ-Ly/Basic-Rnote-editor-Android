package com.rnote.baby.storage

import com.rnote.baby.model.NativeBackgroundConfig
import com.rnote.baby.model.EllipseShape
import com.rnote.baby.model.LineShape
import com.rnote.baby.model.NativeBrushStroke
import com.rnote.baby.model.NativeShapeElement
import com.rnote.baby.model.RectShape
import com.rnote.baby.model.NativePatternType
import com.rnote.baby.model.NativeStrokePoint
import com.rnote.baby.model.PressureCurve
import com.rnote.baby.model.RnoteNativeColor
import com.rnote.baby.model.RnoteNativeDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * File-format interoperability is this app's reason to exist, so the writer and the
 * reader are checked against each other rather than against a snapshot of their own
 * output — a snapshot would happily lock in a file desktop Rnote refuses to open.
 */
class RnoteNativeRoundTripTest {

    private val eps = 1e-3f

    private fun roundTrip(doc: RnoteNativeDocument): RnoteNativeDocument {
        val bytes = ByteArrayOutputStream().also { RnoteNativeSerializer.serialize(it, doc) }
        return RnoteNativeParser.parse(ByteArrayInputStream(bytes.toByteArray()))
    }

    /** Parses a hand-written snapshot, for shapes the serializer would never emit. */
    private fun parseJson(json: String): RnoteNativeDocument {
        val gzipped = ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gzipped).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return RnoteNativeParser.parse(ByteArrayInputStream(gzipped.toByteArray()))
    }

    private fun brushStroke(
        points: List<NativeStrokePoint>,
        width: Float = 3.5f,
        color: RnoteNativeColor = RnoteNativeColor(0.2f, 0.4f, 0.6f, 0.8f),
        isHighlighter: Boolean = false,
        curve: PressureCurve = PressureCurve.LINEAR
    ) = NativeBrushStroke(
        points = points,
        strokeWidth = width,
        color = color,
        isHighlighter = isHighlighter,
        minX = points.minOf { it.x }, minY = points.minOf { it.y },
        maxX = points.maxOf { it.x }, maxY = points.maxOf { it.y },
        pressureCurve = curve
    )

    private fun docWith(vararg strokes: NativeBrushStroke) = RnoteNativeDocument(
        pageWidth = 793.7f,
        pageHeight = 1122.5f,
        totalHeight = 2245f,
        background = NativeBackgroundConfig(
            color = RnoteNativeColor(0.1f, 0.1f, 0.12f, 1f),
            pattern = NativePatternType.ISO_DOTS,
            patternWidth = 19.2f,
            patternHeight = 24f,
            patternColor = RnoteNativeColor(0.8f, 0.9f, 1f, 0.5f)
        ),
        elements = strokes.toList(),
        layout = "continuous_vertical",
        originX = -120f,
        originY = -45f,
        totalWidth = 1600f,
        borderColor = RnoteNativeColor(0.4f, 0.4f, 0.45f, 1f),
        showBorders = false,
        showOriginIndicator = true
    )

    @Test
    fun `a rotated rectangle keeps its rotation`() {
        // The old model was an axis-aligned x/y/w/h, so a shape the desktop had turned
        // came back square. Half a right angle, scaled, and moved off the origin.
        val c = kotlin.math.cos(0.7853982f)
        val s = kotlin.math.sin(0.7853982f)
        val transform = floatArrayOf(c, s, -s, c, 300f, 200f)
        val rect = NativeShapeElement(
            shape = RectShape(40f, 20f, transform),
            color = RnoteNativeColor(0.1f, 0.2f, 0.3f, 1f),
            strokeWidth = 3f,
            minX = 0f, minY = 0f, maxX = 0f, maxY = 0f,
            fillColor = RnoteNativeColor(0.9f, 0.5f, 0.1f, 0.75f)
        )
        val parsed = roundTrip(docWith().copy(elements = listOf(rect)))
            .elements.filterIsInstance<NativeShapeElement>().single()
        val kind = parsed.shape as RectShape
        assertEquals(40f, kind.halfExtentX, eps)
        assertEquals(20f, kind.halfExtentY, eps)
        transform.forEachIndexed { i, v -> assertEquals(v, kind.transform[i], eps) }
        assertEquals(0.75f, parsed.fillColor.a, eps)
        assertEquals(0.9f, parsed.fillColor.r, eps)
        assertEquals(3f, parsed.strokeWidth, eps)
        // Bounds are recomputed from the turned shape, not from its unturned extents.
        assertTrue("a turned rect is wider than its half-extent", parsed.maxX - parsed.minX > 80f)
    }

    @Test
    fun `an ellipse and a line survive a round trip`() {
        val ellipse = NativeShapeElement(
            shape = EllipseShape(50f, 25f, floatArrayOf(1f, 0f, 0f, 1f, 120f, 90f)),
            color = RnoteNativeColor.BLACK, strokeWidth = 2f,
            minX = 0f, minY = 0f, maxX = 0f, maxY = 0f
        )
        val line = NativeShapeElement(
            shape = LineShape(10f, 20f, 300f, 400f),
            color = RnoteNativeColor.BLACK, strokeWidth = 2f,
            minX = 0f, minY = 0f, maxX = 0f, maxY = 0f
        )
        val parsed = roundTrip(docWith().copy(elements = listOf(ellipse, line)))
            .elements.filterIsInstance<NativeShapeElement>()
        assertEquals(2, parsed.size)

        val e = parsed.map { it.shape }.filterIsInstance<EllipseShape>().single()
        assertEquals(50f, e.radiusX, eps)
        assertEquals(25f, e.radiusY, eps)
        assertEquals(120f, e.transform[4], eps)

        val l = parsed.map { it.shape }.filterIsInstance<LineShape>().single()
        assertEquals(10f, l.x1, eps)
        assertEquals(400f, l.y2, eps)
    }

    @Test
    fun `a text box keeps the transform it was placed with`() {
        // Rnote writes the transform as a nine-float affine; this reader only knew a
        // six-float "matrix", so every transform silently became the identity and a
        // rotated, scaled text box came back square at the origin.
        val transform = floatArrayOf(1.5f, 0.25f, -0.25f, 1.5f, 640f, 480f)
        val text = com.rnote.baby.model.NativeTextElement(
            text = "Marginalia", fontFamily = "Cantarell", fontSize = 18f,
            color = RnoteNativeColor(0.2f, 0.2f, 0.2f, 1f), transform = transform,
            minX = 640f, minY = 480f, maxX = 820f, maxY = 498f
        )
        val parsed = roundTrip(docWith().copy(elements = listOf(text)))
            .elements.filterIsInstance<com.rnote.baby.model.NativeTextElement>().single()
        assertEquals("Marginalia", parsed.text)
        transform.forEachIndexed { i, v -> assertEquals(v, parsed.transform[i], eps) }
    }

    @Test
    fun `the output is gzipped, as desktop Rnote expects`() {
        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, docWith()) }
            .toByteArray()
        assertEquals(0x1f.toByte(), bytes[0])
        assertEquals(0x8b.toByte(), bytes[1])
    }

    @Test
    fun `stroke points survive a round trip exactly`() {
        val points = listOf(
            NativeStrokePoint(12.5f, 30.25f, 0.4f),
            NativeStrokePoint(40f, 55.5f, 0.75f),
            NativeStrokePoint(90.125f, 12f, 1f)
        )
        val parsed = roundTrip(docWith(brushStroke(points)))
        val stroke = parsed.elements.single() as NativeBrushStroke
        assertEquals(points.size, stroke.points.size)
        points.forEachIndexed { i, expected ->
            assertEquals(expected.x, stroke.points[i].x, eps)
            assertEquals(expected.y, stroke.points[i].y, eps)
            assertEquals(expected.pressure, stroke.points[i].pressure, eps)
        }
    }

    @Test
    fun `stroke width, colour and pressure curve survive a round trip`() {
        val original = brushStroke(
            listOf(NativeStrokePoint(0f, 0f, 0.5f), NativeStrokePoint(10f, 10f, 0.5f)),
            width = 7.25f,
            color = RnoteNativeColor(0.9f, 0.1f, 0.35f, 0.35f),
            curve = PressureCurve.CONST
        )
        val stroke = roundTrip(docWith(original)).elements.single() as NativeBrushStroke
        assertEquals(7.25f, stroke.strokeWidth, eps)
        assertEquals(0.9f, stroke.color.r, eps)
        assertEquals(0.1f, stroke.color.g, eps)
        assertEquals(0.35f, stroke.color.b, eps)
        assertEquals(0.35f, stroke.color.a, eps)
        // A Marker's CONST curve written as "linear" is what made markers taper in desktop
        // Rnote; dropping the field entirely would make painted width unknowable.
        assertEquals(PressureCurve.CONST, stroke.pressureCurve)
    }

    @Test
    fun `highlighter strokes stay on the highlighter layer`() {
        val parsed = roundTrip(
            docWith(
                brushStroke(listOf(NativeStrokePoint(0f, 0f, 1f), NativeStrokePoint(5f, 5f, 1f))),
                brushStroke(
                    listOf(NativeStrokePoint(1f, 1f, 1f), NativeStrokePoint(6f, 6f, 1f)),
                    isHighlighter = true
                )
            )
        )
        val flags = parsed.elements.map { (it as NativeBrushStroke).isHighlighter }
        assertEquals(listOf(false, true), flags)
    }

    @Test
    fun `draw order is preserved across the slotmap's reserved sentinel slot`() {
        // Real files carry a leading {"value":null} placeholder at index 0 and start real
        // elements at index 1; getting that offset wrong reorders or drops every stroke.
        val strokes = (0 until 5).map { i ->
            brushStroke(
                listOf(
                    NativeStrokePoint(i.toFloat(), 0f, 1f),
                    NativeStrokePoint(i.toFloat(), 10f, 1f)
                )
            )
        }
        val parsed = roundTrip(docWith(*strokes.toTypedArray()))
        assertEquals(5, parsed.elements.size)
        parsed.elements.forEachIndexed { i, el ->
            assertEquals(i.toFloat(), (el as NativeBrushStroke).points.first().x, eps)
        }
    }

    @Test
    fun `page format and background survive a round trip`() {
        val parsed = roundTrip(docWith())
        assertEquals(793.7f, parsed.pageWidth, eps)
        assertEquals(1122.5f, parsed.pageHeight, eps)
        assertEquals(2245f, parsed.totalHeight, eps)
        assertEquals(NativePatternType.ISO_DOTS, parsed.background.pattern)
        assertEquals(19.2f, parsed.background.patternWidth, eps)
        assertEquals(24f, parsed.background.patternHeight, eps)
        assertEquals(0.12f, parsed.background.color.b, eps)
        assertEquals(0.5f, parsed.background.patternColor.a, eps)
        assertEquals("continuous_vertical", parsed.layout)
        assertEquals(-120f, parsed.originX, eps)
        assertEquals(-45f, parsed.originY, eps)
        assertEquals(1600f, parsed.totalWidth, eps)
        assertEquals(false, parsed.showBorders)
    }

    @Test
    fun `every pattern type round-trips through its api name`() {
        for (pattern in NativePatternType.entries) {
            val parsed = roundTrip(
                RnoteNativeDocument(background = NativeBackgroundConfig(pattern = pattern))
            )
            assertEquals(pattern, parsed.background.pattern)
        }
    }

    @Test
    fun `the layout mode survives a round trip`() {
        // Read on open since day one, never written on save -- so an infinite document
        // came back as a single fixed page the next time it was opened.
        for (layout in listOf("infinite", "fixed_size", "continuous_vertical")) {
            val parsed = roundTrip(RnoteNativeDocument(layout = layout))
            assertEquals(layout, parsed.layout)
        }
    }

    @Test
    fun `the document extent survives a round trip`() {
        val parsed = roundTrip(
            RnoteNativeDocument(
                originX = -4588f, originY = -6444f,
                totalWidth = 9784f, totalHeight = 13296f
            )
        )
        assertEquals(-4588f, parsed.originX, eps)
        assertEquals(-6444f, parsed.originY, eps)
        assertEquals(9784f, parsed.totalWidth, eps)
        assertEquals(13296f, parsed.totalHeight, eps)
    }

    @Test
    fun `format border settings survive a round trip`() {
        val parsed = roundTrip(
            RnoteNativeDocument(
                borderColor = RnoteNativeColor(0.25f, 0.5f, 0.75f, 1f),
                showBorders = false,
                showOriginIndicator = false
            )
        )
        assertEquals(0.25f, parsed.borderColor.r, eps)
        assertEquals(0.5f, parsed.borderColor.g, eps)
        assertEquals(0.75f, parsed.borderColor.b, eps)
        assertEquals(false, parsed.showBorders)
        assertEquals(false, parsed.showOriginIndicator)
    }

    @Test
    fun `a file carrying no extent falls back to the page format`() {
        // Older files, and anything hand-built, may not carry the document rect at all.
        val parsed = parseJson(
            """{"data":{"engine_snapshot":{"document":{"config":{"format":""" +
            """{"width":500.0,"height":700.0}}}}}}"""
        )
        assertEquals(500f, parsed.pageWidth, eps)
        assertEquals(700f, parsed.pageHeight, eps)
        assertEquals(500f, parsed.totalWidth, eps)
        assertEquals(700f, parsed.totalHeight, eps)
    }

    @Test
    fun `a file carrying no layout or border settings uses the documented defaults`() {
        val parsed = parseJson("""{"data":{"engine_snapshot":{"document":{"config":{}}}}}""")
        assertEquals("", parsed.layout)
        assertEquals(true, parsed.showBorders)
        assertEquals(true, parsed.showOriginIndicator)
    }

    @Test
    fun `the emitted file is strict, well-formed JSON with the keys Rnote names`() {
        // RnoteNativeParser runs with isLenient = true, so it would happily read back a
        // file that serde_json rejects outright. The JSON is hand-built with a
        // StringBuilder, which makes that a real way to ship an unopenable file.
        val bytes = ByteArrayOutputStream()
            .also {
                RnoteNativeSerializer.serialize(
                    it,
                    docWith(
                        brushStroke(
                            listOf(
                                NativeStrokePoint(0f, 0f, 1f),
                                NativeStrokePoint(5f, 5f, 0.5f)
                            )
                        )
                    )
                )
            }
            .toByteArray()
        val text = java.util.zip.GZIPInputStream(ByteArrayInputStream(bytes))
            .bufferedReader(Charsets.UTF_8).readText()

        val document = org.json.JSONObject(text)
            .getJSONObject("data")
            .getJSONObject("engine_snapshot")
            .getJSONObject("document")
        val config = document.getJSONObject("config")

        assertEquals("continuous_vertical", config.getString("layout"))
        assertEquals(-120.0, document.getDouble("x"), 1e-3)
        assertEquals(-45.0, document.getDouble("y"), 1e-3)
        assertEquals(1600.0, document.getDouble("width"), 1e-3)

        val format = config.getJSONObject("format")
        assertEquals(false, format.getBoolean("show_borders"))
        assertEquals(true, format.getBoolean("show_origin_indicator"))
        assertEquals(0.45, format.getJSONObject("border_color").getDouble("b"), 1e-3)
    }

    @Test
    fun `every element kind we can write is strict, well-formed JSON`() {
        // The strictness check above only ever held a brush stroke, which is how
        // appendTextElement shipped a stray closing brace: a file with a text box in it
        // was invalid JSON outright, so desktop would have refused the whole document,
        // not just the text. Every kind the writer can emit belongs in this check.
        val doc = docWith().copy(
            elements = listOf(
                brushStroke(listOf(NativeStrokePoint(0f, 0f, 1f), NativeStrokePoint(5f, 5f, 0.5f))),
                com.rnote.baby.model.NativeTextElement(
                    text = "A note in the margin", fontFamily = "Cantarell", fontSize = 16f,
                    color = RnoteNativeColor.BLACK,
                    transform = floatArrayOf(1f, 0f, 0f, 1f, 40f, 60f),
                    minX = 40f, minY = 60f, maxX = 200f, maxY = 76f
                ),
                NativeShapeElement(
                    shape = RectShape(30f, 15f, floatArrayOf(1f, 0f, 0f, 1f, 90f, 120f)),
                    color = RnoteNativeColor.BLACK, strokeWidth = 2f,
                    minX = 60f, minY = 105f, maxX = 120f, maxY = 135f,
                    fillColor = RnoteNativeColor(0.6f, 0.75f, 0.94f, 1f)
                )
            )
        )
        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, doc) }
            .toByteArray()
        val text = java.util.zip.GZIPInputStream(ByteArrayInputStream(bytes))
            .bufferedReader(Charsets.UTF_8).readText()

        val components = org.json.JSONObject(text)
            .getJSONObject("data")
            .getJSONObject("engine_snapshot")
            .getJSONArray("stroke_components")
        // The reserved sentinel slot, then one entry per element.
        assertEquals(4, components.length())

        val shape = components.getJSONObject(3).getJSONObject("value").getJSONObject("shapestroke")
        val rect = shape.getJSONObject("shape").getJSONObject("rect")
        assertEquals(30.0, rect.getJSONObject("cuboid").getJSONArray("half_extents").getDouble(0), 1e-3)
        // Nine floats, column-major, translation in the third column.
        val affine = rect.getJSONObject("transform").getJSONArray("affine")
        assertEquals(9, affine.length())
        assertEquals(90.0, affine.getDouble(6), 1e-3)
        assertEquals(120.0, affine.getDouble(7), 1e-3)
        assertEquals(
            0.6,
            shape.getJSONObject("style").getJSONObject("smooth")
                .getJSONObject("fill_color").getDouble("r"),
            1e-3
        )

        val textStroke = components.getJSONObject(2).getJSONObject("value").getJSONObject("textstroke")
        assertEquals("A note in the margin", textStroke.getString("text"))
        assertEquals(
            40.0,
            textStroke.getJSONObject("transform").getJSONArray("affine").getDouble(6),
            1e-3
        )
    }

    @Test
    fun `shapes from an older Rnote still read, in the shape the model holds now`() {
        // The capitalised variants and the corner-and-size rectangle an older Rnote
        // wrote. A freehand "shape" has no 0.14 form to be written back as, so it is
        // read as the brush stroke it effectively is.
        val parsed = parseJson(
            """{"data":{"engine_snapshot":{"document":{"config":{}},"stroke_components":[""" +
            """{"value":null,"version":0},""" +
            """{"value":{"shapestroke":{"shape":{"Rectangle":{"top_left":[10.0,20.0],""" +
            """"size":[100.0,50.0]}},"style":{"Smooth":{"stroke_width":4.0}}}},"version":1},""" +
            """{"value":{"shapestroke":{"shape":{"FreehandPen":{"start":""" +
            """{"pos":[1.0,2.0],"pressure":0.5},"segments":[{"lineto":{"end":""" +
            """{"pos":[9.0,12.0],"pressure":0.5}}}]}},"style":{"Smooth":{"stroke_width":3.0}}}},""" +
            """"version":1}]}}}"""
        )

        val rect = parsed.elements.filterIsInstance<NativeShapeElement>().single()
        val kind = rect.shape as RectShape
        assertEquals(50f, kind.halfExtentX, eps)
        assertEquals(25f, kind.halfExtentY, eps)
        // Re-centred: a corner at (10,20) with a 100x50 size sits centred on (60,45).
        assertEquals(60f, kind.transform[4], eps)
        assertEquals(45f, kind.transform[5], eps)
        assertEquals(4f, rect.strokeWidth, eps)
        assertEquals(10f, rect.minX, eps)
        assertEquals(70f, rect.maxY, eps)

        val freehand = parsed.elements.filterIsInstance<NativeBrushStroke>().single()
        assertEquals(2, freehand.points.size)
        assertEquals(3f, freehand.strokeWidth, eps)
        assertEquals(9f, freehand.maxX, eps)
    }

    @Test
    fun `an empty document round-trips`() {
        assertTrue(roundTrip(RnoteNativeDocument()).elements.isEmpty())
    }
}
