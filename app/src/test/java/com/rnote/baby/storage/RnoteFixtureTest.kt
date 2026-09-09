package com.rnote.baby.storage

import com.rnote.baby.model.NativeBrushStroke
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

    private fun fixture(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .take(4)
            // Each level and its `tests/`, since that is where the sample files live.
            .flatMap { dir ->
                sequenceOf(File(dir, FIXTURE_NAME), File(File(dir, "tests"), FIXTURE_NAME))
            }
            .firstOrNull { it.isFile }
            ?: throw AssertionError(
                "$FIXTURE_NAME not found at or above ${File("").absolutePath}"
            )

    private fun parseFixture() = fixture().inputStream().use { RnoteNativeParser.parse(it) }

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

    private companion object {
        const val FIXTURE_NAME = "test.rnote"
    }
}
