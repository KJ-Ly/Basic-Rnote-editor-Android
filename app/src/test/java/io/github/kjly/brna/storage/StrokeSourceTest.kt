package io.github.kjly.brna.storage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.NoteDocument
import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.ui.canvas.EraserHitTest
import io.github.kjly.brna.ui.canvas.SelectionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Desktop strokes through the tablet and back, with the files desktop Rnote wrote in
 * `tests/`: H3 and L2b were drawn with its "Curved" pen path, all cubic segments. A
 * stroke nothing touched must come back byte for byte; one that was edited, as Rnote
 * would write it, curves and all.
 */
class StrokeSourceTest {

    private fun fixtureText(name: String): String {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(4)
            .flatMap { dir -> sequenceOf(File(dir, name), File(File(dir, "tests"), name)) }
            .firstOrNull { it.isFile }
            ?: throw AssertionError("$name not found at or above ${File("").absolutePath}")
        return GZIPInputStream(file.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun opened(name: String): NoteDocument {
        val text = fixtureText(name)
        val native = RnoteNativeParser.parse(ByteArrayInputStream(gzip(text)))
        return FileManager.bridgeNativeToNoteDocument(native)
    }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream()
        .also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) } }
        .toByteArray()

    private fun saved(doc: NoteDocument): String = ByteArrayOutputStream()
        .also { RnoteNativeSerializer.serialize(it, RnoteNativeSerializer.bridgeToNative(doc)) }
        .let { GZIPInputStream(ByteArrayInputStream(it.toByteArray())).readBytes().toString(Charsets.UTF_8) }

    /** Every `brushstroke` value in [json], cut out of the text itself by matching braces. */
    private fun brushStrokes(json: String): List<String> {
        val out = mutableListOf<String>()
        val key = "\"brushstroke\":"
        var from = json.indexOf(key)
        while (from >= 0) {
            val start = from + key.length
            var depth = 0
            var i = start
            do {
                when (json[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            } while (depth > 0)
            out += json.substring(start, i)
            from = json.indexOf(key, i)
        }
        return out
    }

    @Test
    fun `strokes opened and saved unchanged are written back byte for byte`() {
        for (name in listOf("H3.rnote", "L2b.rnote", "test.rnote")) {
            val original = brushStrokes(fixtureText(name))
            assertTrue(name, original.isNotEmpty())
            val written = brushStrokes(saved(opened(name)))
            // In draw order rather than the file's slot order, so compared as a whole.
            assertEquals(name, original.sorted(), written.sorted())
        }
    }

    @Test
    fun `curved segments are read with their control points`() {
        val stroke = opened("H3.rnote").strokes.first { s -> s.points.any { it.curve != null } }
        // Rnote's Curved path: every segment after the first few is a cubic.
        val curves = stroke.points.drop(1).mapNotNull { it.curve }
        assertTrue(curves.isNotEmpty())
        assertTrue(curves.all { it is SegmentCurve.Cubic })
    }

    @Test
    fun `a moved stroke keeps its curves, moved with it`() {
        val doc = opened("H3.rnote")
        val stroke = doc.strokes.first { s -> s.points.any { it.curve != null } }
        val moved = SelectionManager.translateStrokes(listOf(stroke), Offset(10f, 20f)).single()
        assertNull(moved.source)
        val i = stroke.points.indexOfFirst { it.curve != null }
        val before = stroke.points[i].curve as SegmentCurve.Cubic
        val after = moved.points[i].curve as SegmentCurve.Cubic
        assertEquals(before.c1x + 10f, after.c1x, 1e-3f)
        assertEquals(before.c2y + 20f, after.c2y, 1e-3f)

        // Written anew, still as Rnote's cubic segments, and no longer as the original.
        val json = saved(doc.copy(strokes = doc.strokes.map { if (it.id == stroke.id) moved else it }))
        val originalText = brushStrokes(fixtureText("H3.rnote"))
        val written = brushStrokes(json)
        assertEquals(originalText.size, written.size)
        val changed = written.filter { it !in originalText }
        assertEquals(1, changed.size)
        assertEquals(
            stroke.points.drop(1).count { it.curve is SegmentCurve.Cubic },
            Regex("\"cubbezto\"").findAll(changed.single()).count()
        )
    }

    @Test
    fun `any change to a stroke means it is written anew`() {
        val stroke = opened("H3.rnote").strokes.first()
        assertNotNull(stroke.source)
        val source = stroke.source!!
        assertTrue(source.describes(stroke))
        // Whatever the change, and however it came about, the original no longer applies.
        assertFalse(source.describes(stroke.copy(color = Color.Red)))
        assertFalse(source.describes(stroke.copy(strokeWidth = stroke.strokeWidth + 1f)))
        assertFalse(source.describes(stroke.copy(points = stroke.points.dropLast(1))))
        assertFalse(source.describes(stroke.copy(isHighlighter = !stroke.isHighlighter)))
        // A copy under another id is still the same stroke.
        assertTrue(source.describes(stroke.copy(id = "another")))
    }

    @Test
    fun `recoloured and erased strokes let go of the original`() {
        val stroke = opened("L2b.rnote").strokes.first { s -> s.points.any { it.curve != null } }
        assertNull(SelectionManager.recolored(listOf(stroke), Color.Blue).single().source)
        val middle = stroke.points[stroke.points.size / 2]
        val pieces = EraserHitTest.splitStroke(
            androidx.compose.ui.geometry.Rect(middle.x - 1f, middle.y - 1f, middle.x + 1f, middle.y + 1f), stroke
        )
        assertNotNull(pieces)
        pieces!!.forEach { assertNull(it.source) }
        // The pieces keep the curves of the segments they keep.
        assertTrue(pieces.any { piece -> piece.points.drop(1).any { it.curve != null } })
    }
}
