package io.github.kjly.brna.storage

import io.github.kjly.brna.model.NativeBackgroundConfig
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativePatternType
import io.github.kjly.brna.model.NativeStrokePoint
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RnoteNativeDocument
import io.github.kjly.brna.model.TexturedStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Xournal++ file to a note and back, as Rnote's `load_from_xopp_bytes`,
 * `Stroke::from_xopp*`, `export_doc_as_xopp_bytes` and `Stroke::into_xopp` do it. The
 * numbers are worked out by hand from Rnote's code: 72 DPI in the file, 96 in the note.
 */
class XoppConvertTest {

    private val black = XoppFile.Color(0, 0, 0, 255)
    private fun page(vararg layers: XoppFile.Layer) = XoppFile.Page(595.2755910, 841.8897640, layers = layers.toList())

    private val noImages: (ByteArray) -> XoppConvert.Pixels? = { null }

    @Test
    fun `a stroke with pressure has its widths as pressures and loses its last point`() {
        val stroke = XoppFile.Stroke(
            color = XoppFile.Color(0x33, 0x33, 0xcc, 0xff),
            width = listOf(1.41, 1.2, 1.3),
            coords = listOf(10.0 to 20.0, 30.0 to 40.0, 50.0 to 60.0)
        )
        val el = XoppConvert.brushStroke(stroke, 0.0)!!
        // The largest of the point widths is the stroke's width, in the note's units.
        assertEquals(1.3 * 4 / 3, el.strokeWidth.toDouble(), 1e-6)
        // Xournal++ has one width fewer than points; Rnote zips them, so two points are left.
        assertEquals(2, el.points.size)
        assertEquals(1.2 / 1.3, el.points[0].pressure.toDouble(), 1e-6)
        assertEquals(1f, el.points[1].pressure, 0f)
        assertEquals(40.0, el.points[1].x.toDouble(), 1e-5)
        assertEquals(160.0 / 3, el.points[1].y.toDouble(), 1e-5)
        assertEquals(RnoteNativeColor(0.2f, 0.2f, 0.8f, 1f), el.color)
        assertEquals(PressureCurve.LINEAR, el.pressureCurve)
    }

    @Test
    fun `a stroke without pressure is full width all along`() {
        val stroke = XoppFile.Stroke(color = black, width = listOf(3.0), coords = listOf(0.0 to 0.0, 72.0 to 0.0))
        val el = XoppConvert.brushStroke(stroke, 100.0)!!
        assertEquals(4f, el.strokeWidth, 1e-6f)
        assertEquals(listOf(1f, 1f), el.points.map { it.pressure })
        assertEquals(96f, el.points[1].x, 1e-5f)
        // Pages lie one below the other.
        assertEquals(100f, el.points[1].y, 1e-5f)
        assertNull(XoppConvert.brushStroke(stroke.copy(width = emptyList()), 0.0))
    }

    @Test
    fun `a highlighter is half see-through on the highlighter layer, the eraser white`() {
        val base = XoppFile.Stroke(color = XoppFile.Color(255, 255, 0, 255), width = listOf(8.0), coords = listOf(0.0 to 0.0, 1.0 to 1.0))
        val marker = XoppConvert.brushStroke(base.copy(tool = XoppFile.Tool.HIGHLIGHTER), 0.0)!!
        assertTrue(marker.isHighlighter)
        assertEquals(RnoteNativeColor(1f, 1f, 0f, 0.5f), marker.color)
        val eraser = XoppConvert.brushStroke(base.copy(tool = XoppFile.Tool.ERASER), 0.0)!!
        assertEquals(RnoteNativeColor.WHITE, eraser.color)
    }

    @Test
    fun `the pages become one infinite canvas, as Rnote imports them`() {
        val stroke = XoppFile.Stroke(color = black, width = listOf(1.0), coords = listOf(0.0 to 0.0, 10.0 to 10.0))
        val text = XoppFile.Text("Sans", 12.0, 100.0, 120.0, XoppFile.Color(255, 0, 0, 255), "Hallo")
        val root = XoppFile.Root(pages = listOf(page(), page(XoppFile.Layer(strokes = listOf(stroke), texts = listOf(text)))))
        val doc = XoppConvert.toNative(root, noImages)

        assertEquals("infinite", doc.layout)
        assertEquals(NativePatternType.BLANK, doc.background.pattern)
        assertEquals(595.2755910 * 4 / 3, doc.pageWidth.toDouble(), 1e-3)
        assertEquals(841.8897640 * 4 / 3, doc.pageHeight.toDouble(), 1e-3)
        assertEquals(2 * 841.8897640 * 4 / 3, doc.totalHeight.toDouble(), 1e-3)

        val ink = doc.elements.filterIsInstance<NativeBrushStroke>().single()
        // On the second page: moved down by the first.
        assertEquals(841.8897640 * 4 / 3, ink.points[0].y.toDouble(), 1e-3)

        val box = doc.elements.filterIsInstance<NativeTextElement>().single()
        assertEquals("Hallo", box.text)
        assertEquals("Sans", box.fontFamily)
        assertEquals(16f, box.fontSize, 1e-5f)
        assertEquals(RnoteNativeColor(1f, 0f, 0f, 1f), box.color)
        assertEquals(400f / 3, box.transform[4], 1e-3f)
        assertEquals(160f + 841.889764f * 4 / 3, box.transform[5], 1e-2f)
    }

    @Test
    fun `an image keeps its own pixels, stretched over its rectangle`() {
        val image = XoppFile.Image(10.0, 20.0, 110.0, 70.0, java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))
        val el = XoppConvert.bitmap(image, 0.0) { XoppConvert.Pixels("AAAAAAAAAAA=", 2, 1) }!!
        assertEquals(2, el.bmpWidth)
        assertEquals(1, el.bmpHeight)
        assertEquals(40f / 3, el.minX, 1e-4f)
        assertEquals(80f / 3, el.minY, 1e-4f)
        assertEquals(440f / 3, el.maxX, 1e-4f)
        assertEquals(280f / 3, el.maxY, 1e-4f)
        // One it can't read is left out.
        assertNull(XoppConvert.bitmap(image, 0.0) { null })
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private fun stroke(
        vararg pts: Triple<Float, Float, Float>,
        width: Float = 4f,
        curve: PressureCurve = PressureCurve.LINEAR,
        highlighter: Boolean = false,
        textured: TexturedStyle? = null
    ) = NativeBrushStroke(
        points = pts.map { NativeStrokePoint(it.first, it.second, it.third) },
        strokeWidth = width,
        color = RnoteNativeColor(0.2f, 0.4f, 0.6f, 1f),
        isHighlighter = highlighter,
        minX = pts.minOf { it.first }, minY = pts.minOf { it.second },
        maxX = pts.maxOf { it.first }, maxY = pts.maxOf { it.second },
        pressureCurve = curve,
        textured = textured
    )

    private val a4 = listOf(
        XoppConvert.PageRect(0.0, 0.0, 793.7, 1122.5),
        XoppConvert.PageRect(0.0, 1122.5, 793.7, 2245.0),
        XoppConvert.PageRect(0.0, 2245.0, 793.7, 3367.5)
    )

    private fun export(vararg els: NativeCanvasElement, render: (NativeCanvasElement) -> ByteArray? = { null }) =
        XoppConvert.fromNative(RnoteNativeDocument(elements = els.toList()), a4, render)

    @Test
    fun `a stroke goes out at 72 DPI, its widths through its pressure curve`() {
        val root = export(stroke(Triple(96f, 192f, 0.5f), Triple(192f, 96f, 1f)))
        val page = root.pages.single()
        assertEquals(793.7 * 0.75, page.width, 1e-9)
        assertEquals(XoppFile.Background.Solid(XoppFile.Color(255, 255, 255, 255), "plain"), page.background)
        val out = page.layers[1].strokes.single()
        assertEquals(XoppFile.Tool.PEN, out.tool)
        assertEquals(listOf(3.0, 1.5, 3.0), out.width)
        assertEquals(listOf(72.0 to 144.0, 144.0 to 72.0), out.coords)
        // floor(c × 255), as Rnote's `xoppcolor_from_color`.
        assertEquals(XoppFile.Color(51, 102, 153, 255), out.color)
    }

    @Test
    fun `the Marker's constant curve and the Textured brush's plain pressure`() {
        val flat = export(stroke(Triple(0f, 0f, 0.5f), Triple(10f, 0f, 0.25f), curve = PressureCurve.CONST))
        assertEquals(listOf(3.0, 3.0, 3.0), flat.pages.single().layers[1].strokes.single().width)
        val textured = TexturedStyle(1L)
        val grain = export(stroke(Triple(0f, 0f, 0.5f), Triple(10f, 0f, 0.25f), curve = PressureCurve.CONST, textured = textured))
        assertEquals(listOf(3.0, 1.5, 0.75), grain.pages.single().layers[1].strokes.single().width)
    }

    @Test
    fun `only pages with something on them go, each with its own coordinates`() {
        val root = export(
            stroke(Triple(100f, 100f, 1f), Triple(200f, 200f, 1f)),
            stroke(Triple(100f, 2300f, 1f), Triple(200f, 2400f, 1f)),
            // A single point: Xournal++ needs two, so Rnote leaves it out.
            stroke(Triple(300f, 300f, 1f))
        )
        assertEquals(2, root.pages.size)
        val third = root.pages[1].layers[1].strokes.single()
        assertEquals((2300.0 - 2245.0) * 0.75, third.coords[0].second, 1e-4)
        assertEquals(1, root.pages[0].layers[1].strokes.size)
    }

    @Test
    fun `an empty note still has a page`() {
        val root = export()
        assertEquals(1, root.pages.size)
        assertEquals(1122.5 * 0.75, root.pages.single().height, 1e-9)
    }

    @Test
    fun `highlighters go under the ink, and everything else in as pictures underneath`() {
        val ink = stroke(Triple(10f, 10f, 1f), Triple(20f, 20f, 1f))
        val marker = stroke(Triple(30f, 30f, 1f), Triple(40f, 40f, 1f), highlighter = true)
        val text = NativeTextElement(
            text = "x", fontFamily = "serif", fontSize = 32f, color = RnoteNativeColor.BLACK,
            minX = 96f, minY = 48f, maxX = 192f, maxY = 96f
        )
        val shown = ArrayList<NativeCanvasElement>()
        val root = export(ink, text, marker) { shown += it; byteArrayOf(9) }
        val layers = root.pages.single().layers
        assertEquals(listOf(marker.points[0].x * 0.75, ink.points[0].x * 0.75), layers[1].strokes.map { it.coords[0].first })
        val picture = layers[0].images.single()
        assertEquals(listOf<NativeCanvasElement>(text), shown)
        assertEquals(XoppFile.Image(72.0, 36.0, 144.0, 72.0, "CQ=="), picture)
    }

    @Test
    fun `a stroke comes back from Xournal++ where it went`() {
        val out = export(stroke(Triple(96f, 192f, 0.5f), Triple(192f, 96f, 1f), Triple(300f, 400f, 0.75f)))
        val back = XoppConvert.toNative(XoppFile.load(XoppFile.save(out)), noImages)
        val el = back.elements.filterIsInstance<NativeBrushStroke>().single()
        assertEquals(96f, el.points[0].x, 1e-3f)
        assertEquals(192f, el.points[0].y, 1e-3f)
        assertEquals(4f, el.strokeWidth, 1e-3f)
        assertEquals(0.5f, el.points[0].pressure, 1e-3f)
        assertEquals(RnoteNativeColor(0.2f, 0.4f, 0.6f, 1f), el.color)
        assertTrue(back.elements.none { it is NativeBitmapElement })
        assertEquals(NativeBackgroundConfig().patternColor, back.background.patternColor)
    }
}
