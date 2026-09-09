package com.rnote.baby.storage

import com.rnote.baby.model.NativeBackgroundConfig
import com.rnote.baby.model.NativeBrushStroke
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
    fun `an empty document round-trips`() {
        assertTrue(roundTrip(RnoteNativeDocument()).elements.isEmpty())
    }
}
