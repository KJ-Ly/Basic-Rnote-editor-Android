package io.github.kjly.brna.storage

import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.NoteDocument
import io.github.kjly.brna.model.PageSize
import io.github.kjly.brna.model.PaperPattern
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.model.ToolType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's own `.json` save format — the one used for anything not written as `.rnote`. */
class DocumentSerializerTest {

    private val eps = 1e-3f

    private val document = NoteDocument(
        id = "doc-1",
        title = "Meeting notes",
        createdAt = 1_700_000_000_000L,
        paperStyle = PaperStyle(
            pattern = PaperPattern.ISO_GRID,
            isDarkMode = false,
            pageSize = PageSize.A4,
            dotDensityDpi = 8
        ),
        strokes = listOf(
            Stroke(
                id = "stroke-1",
                points = listOf(
                    StrokePoint(1.5f, 2.25f, 0.4f),
                    StrokePoint(30f, 40f, 0.9f)
                ),
                color = Color(0xFF3366CC),
                strokeWidth = 4.5f,
                toolType = ToolType.BRUSH
            )
        )
    )

    private fun roundTrip(doc: NoteDocument) = DocumentSerializer.parseJson(DocumentSerializer.toJson(doc))

    @Test
    fun `identity and paper style survive a round trip`() {
        val parsed = roundTrip(document)
        assertEquals("doc-1", parsed.id)
        assertEquals("Meeting notes", parsed.title)
        assertEquals(1_700_000_000_000L, parsed.createdAt)
        assertEquals(PaperPattern.ISO_GRID, parsed.paperStyle.pattern)
        assertEquals(false, parsed.paperStyle.isDarkMode)
        assertEquals(PageSize.A4, parsed.paperStyle.pageSize)
        assertEquals(8, parsed.paperStyle.dotDensityDpi)
    }

    @Test
    fun `strokes survive a round trip`() {
        val stroke = roundTrip(document).strokes.single()
        assertEquals("stroke-1", stroke.id)
        assertEquals(4.5f, stroke.strokeWidth, eps)
        assertEquals(ToolType.BRUSH, stroke.toolType)
        assertEquals(Color(0xFF3366CC), stroke.color)
        assertEquals(2, stroke.points.size)
        assertEquals(1.5f, stroke.points[0].x, eps)
        assertEquals(2.25f, stroke.points[0].y, eps)
        assertEquals(0.4f, stroke.points[0].pressure, eps)
        assertEquals(0.9f, stroke.points[1].pressure, eps)
    }

    @Test
    fun `alpha is carried in the colour, not dropped`() {
        // A Marker's translucency lives in its stroke colour and nowhere else; losing the
        // alpha channel here is the difference between a highlighter and an opaque line.
        val translucent = document.copy(
            strokes = listOf(document.strokes.single().copy(color = Color(0x593366CC)))
        )
        assertEquals(Color(0x593366CC), roundTrip(translucent).strokes.single().color)
    }

    @Test
    fun `saving stamps a fresh modified time`() {
        val before = System.currentTimeMillis()
        val json = JSONObject(DocumentSerializer.toJson(document))
        assertTrue(json.getLong("modifiedAt") >= before)
    }

    @Test
    fun `pre-redesign tool names from older saves still load`() {
        val json = DocumentSerializer.toJson(document)
        assertEquals(ToolType.BRUSH, DocumentSerializer.parseJson(json.replace("\"BRUSH\"", "\"PEN\"")).strokes.single().toolType)
        assertEquals(ToolType.BRUSH, DocumentSerializer.parseJson(json.replace("\"BRUSH\"", "\"HIGHLIGHTER\"")).strokes.single().toolType)
        assertEquals(ToolType.SELECTOR, DocumentSerializer.parseJson(json.replace("\"BRUSH\"", "\"SELECT\"")).strokes.single().toolType)
    }

    @Test
    fun `an unknown tool or pattern name falls back instead of failing to load`() {
        val json = DocumentSerializer.toJson(document)
        val mangled = json
            .replace("\"BRUSH\"", "\"SOME_FUTURE_TOOL\"")
            .replace("\"ISO_GRID\"", "\"SOME_FUTURE_PATTERN\"")
        val parsed = DocumentSerializer.parseJson(mangled)
        assertEquals(ToolType.BRUSH, parsed.strokes.single().toolType)
        assertEquals(PaperPattern.DOTS, parsed.paperStyle.pattern)
    }

    @Test
    fun `an empty document round-trips`() {
        val parsed = roundTrip(NoteDocument(id = "empty", strokes = emptyList()))
        assertEquals("empty", parsed.id)
        assertTrue(parsed.strokes.isEmpty())
    }
}
