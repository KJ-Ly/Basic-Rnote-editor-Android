package com.rnote.baby.storage

import com.rnote.baby.model.LineShape
import com.rnote.baby.model.NativeBrushStroke
import com.rnote.baby.model.NativeShapeElement
import com.rnote.baby.model.RectShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reads `test.rnote` — a v0.14 file written by desktop Rnote, kept in `tests/`.
 * The round-trip tests only prove the writer and reader agree with each other; this is
 * the one that proves they agree with the real application.
 */
class RnoteFixtureTest {

    private fun fixture(name: String = FIXTURE_NAME): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .take(4)
            // Each level and its `tests/`, since that is where the sample files live.
            .flatMap { dir ->
                sequenceOf(File(dir, name), File(File(dir, "tests"), name))
            }
            .firstOrNull { it.isFile }
            ?: throw AssertionError(
                "$name not found at or above ${File("").absolutePath}"
            )

    private fun parseFixture(name: String = FIXTURE_NAME) =
        fixture(name).inputStream().use { RnoteNativeParser.parse(it) }

    @Test
    fun `a desktop-written file parses into strokes on a sane page`() {
        val doc = parseFixture()

        assertTrue("expected a page wider than nothing", doc.pageWidth > 1f)
        assertTrue("expected a page taller than nothing", doc.pageHeight > 1f)
        assertTrue("expected at least one element", doc.elements.isNotEmpty())

        val strokes = doc.elements.filterIsInstance<NativeBrushStroke>()
        assertTrue("expected brush strokes", strokes.isNotEmpty())
        for (stroke in strokes) {
            assertTrue("a stroke with no points", stroke.points.isNotEmpty())
            assertTrue("non-positive stroke width", stroke.strokeWidth > 0f)
            for (p in stroke.points) {
                assertTrue("pressure out of range: ${p.pressure}", p.pressure in 0f..1f)
            }
        }
    }

    @Test
    fun `the fixture is an infinite-layout document with a real extent`() {
        val doc = parseFixture()
        assertEquals("infinite", doc.layout)
        // Desktop grew the document well past the page, into negative coordinates.
        assertTrue("expected a negative origin, got ${doc.originX}", doc.originX < 0f)
        assertTrue("expected a negative origin, got ${doc.originY}", doc.originY < 0f)
        assertTrue(doc.totalWidth > doc.pageWidth)
        assertTrue(doc.totalHeight > doc.pageHeight)
    }

    @Test
    fun `the bounds reported for a stroke enclose its own points`() {
        for (stroke in parseFixture().elements.filterIsInstance<NativeBrushStroke>()) {
            assertEquals(stroke.points.minOf { it.x }, stroke.minX, 1e-3f)
            assertEquals(stroke.points.minOf { it.y }, stroke.minY, 1e-3f)
            assertEquals(stroke.points.maxOf { it.x }, stroke.maxX, 1e-3f)
            assertEquals(stroke.points.maxOf { it.y }, stroke.maxY, 1e-3f)
        }
    }

    @Test
    fun `a desktop file survives being re-saved and re-read`() {
        val original = parseFixture()
        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, original) }
            .toByteArray()
        val reparsed = RnoteNativeParser.parse(ByteArrayInputStream(bytes))

        val originalStrokes = original.elements.filterIsInstance<NativeBrushStroke>()
        val reparsedStrokes = reparsed.elements.filterIsInstance<NativeBrushStroke>()
        assertEquals(originalStrokes.size, reparsedStrokes.size)
        originalStrokes.forEachIndexed { i, stroke ->
            assertEquals(stroke.points.size, reparsedStrokes[i].points.size)
            assertEquals(stroke.strokeWidth, reparsedStrokes[i].strokeWidth, 1e-3f)
            assertEquals(stroke.pressureCurve, reparsedStrokes[i].pressureCurve)
        }
        assertEquals(original.pageWidth, reparsed.pageWidth, 1e-3f)
        assertEquals(original.background.pattern, reparsed.background.pattern)

        // The fields the writer used to drop on the floor.
        assertEquals(original.layout, reparsed.layout)
        assertEquals(original.originX, reparsed.originX, 1e-3f)
        assertEquals(original.originY, reparsed.originY, 1e-3f)
        assertEquals(original.totalWidth, reparsed.totalWidth, 1e-3f)
        assertEquals(original.totalHeight, reparsed.totalHeight, 1e-3f)
        assertEquals(original.showBorders, reparsed.showBorders)
        assertEquals(original.showOriginIndicator, reparsed.showOriginIndicator)
        assertEquals(original.borderColor.r, reparsed.borderColor.r, 1e-3f)
    }

    @Test
    fun `a stroke drawn as bezier segments keeps all of its points`() {
        // Desktop Rnote records a pen stroke as `cubbezto` segments; only the slowest
        // scribbles come out as pure `lineto`, which is all test.rnote happens to hold.
        // A parser that knows just `lineto` reads such a stroke as its two endpoints —
        // in L2b.rnote the smile of a face came through as a 3px dash.
        val strokes = parseFixture(CURVE_FIXTURE_NAME).elements
            .filterIsInstance<NativeBrushStroke>()
        val longest = strokes.maxByOrNull { it.points.size }
            ?: throw AssertionError("expected brush strokes in $CURVE_FIXTURE_NAME")

        assertEquals(206, longest.points.size)
        // The curve sweeps the width of the face; its endpoints are 3px apart.
        assertTrue("collapsed to its endpoints", longest.maxX - longest.minX > 200f)
        assertTrue("collapsed to its endpoints", longest.maxY - longest.minY > 100f)
    }

    @Test
    fun `shapes drawn in desktop survive being read and written back`() {
        // H3.rnote holds two brush strokes, a line, and a rect filled with a pale blue.
        // Every shape in it used to be dropped at parse: the variant names this reader
        // matched were the ones an older Rnote wrote, so nothing matched and a desktop
        // document lost its shapes the first time it was saved from here.
        val doc = parseFixture(SHAPE_FIXTURE_NAME)
        val shapes = doc.elements.filterIsInstance<NativeShapeElement>()
        assertEquals(2, shapes.size)

        val line = shapes.map { it.shape }.filterIsInstance<LineShape>().single()
        assertEquals(145.115f, line.x1, 1e-2f)
        assertEquals(570.079f, line.y1, 1e-2f)

        val rect = shapes.single { it.shape is RectShape }
        val kind = rect.shape as RectShape
        assertEquals(17.808f, kind.halfExtentX, 1e-2f)
        assertEquals(16.471f, kind.halfExtentY, 1e-2f)
        // The transform is where the rect actually sits — it carries no corner.
        assertEquals(208.542f, kind.transform[4], 1e-2f)
        assertEquals(61.537f, kind.transform[5], 1e-2f)
        assertEquals(1f, rect.fillColor.a, 1e-3f)
        assertEquals(0.597f, rect.fillColor.r, 1e-2f)
        // Bounds come out of the transform, and the extent maths depends on them.
        assertEquals(208.542f - 17.808f, rect.minX, 1e-2f)
        assertEquals(61.537f + 16.471f, rect.maxY, 1e-2f)

        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, doc) }
            .toByteArray()
        val reparsed = RnoteNativeParser.parse(ByteArrayInputStream(bytes))
        val rewritten = reparsed.elements.filterIsInstance<NativeShapeElement>()
        assertEquals(2, rewritten.size)
        val rewrittenRect = rewritten.single { it.shape is RectShape }
        assertEquals(kind.halfExtentX, (rewrittenRect.shape as RectShape).halfExtentX, 1e-3f)
        assertEquals(rect.fillColor.r, rewrittenRect.fillColor.r, 1e-3f)
    }

    private companion object {
        const val FIXTURE_NAME = "test.rnote"
        const val CURVE_FIXTURE_NAME = "L2b.rnote"
        const val SHAPE_FIXTURE_NAME = "H3.rnote"
    }
}
