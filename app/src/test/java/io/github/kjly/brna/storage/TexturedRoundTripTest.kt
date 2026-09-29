package io.github.kjly.brna.storage

import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.NoteDocument
import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.model.TexturedDistribution
import io.github.kjly.brna.model.TexturedStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * A textured stroke's dots come from its seed alone, so the seed — a u64, past what a
 * Long or a double holds — must come back out of a save exactly as it went in, and the
 * style must stay `textured` rather than becoming `smooth`.
 */
class TexturedRoundTripTest {

    /** Rnote's largest seed, the one most likely to lose digits on the way. */
    private val maxSeed = (-1L)

    private fun documentWith(textured: TexturedStyle?) = NoteDocument(
        strokes = listOf(
            Stroke(
                points = listOf(StrokePoint(10f, 20f, 0.5f), StrokePoint(60f, 90f, 0.8f)),
                color = Color(0xFF224466),
                strokeWidth = 6f,
                pressureCurve = PressureCurve.LINEAR,
                textured = textured
            )
        )
    )

    private fun writtenJson(doc: NoteDocument): String = ByteArrayOutputStream()
        .also { RnoteNativeSerializer.serialize(it, RnoteNativeSerializer.bridgeToNative(doc)) }
        .let { GZIPInputStream(ByteArrayInputStream(it.toByteArray())).readBytes().toString(Charsets.UTF_8) }

    private fun throughRnote(doc: NoteDocument): NoteDocument {
        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, RnoteNativeSerializer.bridgeToNative(doc)) }
            .toByteArray()
        return FileManager.bridgeNativeToNoteDocument(RnoteNativeParser.parse(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `a textured stroke is written as Rnote writes one`() {
        val json = writtenJson(documentWith(TexturedStyle(maxSeed, 2.5, TexturedDistribution.REVERSE_EXPONENTIAL)))
        // Rnote 0.14's own serde output for these options, less the colour.
        assertTrue(json, json.contains("""{"textured":{"seed":18446744073709551615,"stroke_width":6.0,"stroke_color":"""))
        assertTrue(json, json.contains(""""density":2.5,"distribution":"ReverseExponential","pressure_curve":"linear"}}"""))
    }

    @Test
    fun `seed, density and distribution survive save and reopen`() {
        val style = TexturedStyle(maxSeed, 2.5, TexturedDistribution.REVERSE_EXPONENTIAL)
        val stroke = throughRnote(documentWith(style)).strokes.single()
        assertEquals(style, stroke.textured)
        assertEquals(6f, stroke.strokeWidth, 1e-3f)
    }

    @Test
    fun `a stroke with no seed keeps having none`() {
        val stroke = throughRnote(documentWith(TexturedStyle(null))).strokes.single()
        assertEquals(TexturedStyle(null), stroke.textured)
    }

    @Test
    fun `a solid stroke stays solid`() {
        val doc = documentWith(null)
        assertTrue(writtenJson(doc).contains("""{"smooth":{"""))
        assertNull(throughRnote(doc).strokes.single().textured)
    }

    @Test
    fun `the app's own json keeps the texture too`() {
        val style = TexturedStyle(maxSeed, 7.5, TexturedDistribution.UNIFORM)
        val reloaded = DocumentSerializer.parseJson(DocumentSerializer.toJson(documentWith(style)))
        assertEquals(style, reloaded.strokes.single().textured)
    }
}
