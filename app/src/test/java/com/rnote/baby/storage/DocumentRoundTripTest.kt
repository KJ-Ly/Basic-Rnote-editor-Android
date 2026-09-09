package com.rnote.baby.storage

import androidx.compose.ui.graphics.Color
import com.rnote.baby.model.LayoutMode
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.model.PageSize
import com.rnote.baby.model.PaperPattern
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.Stroke
import com.rnote.baby.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The whole save-and-reopen loop for an edited document, both ways out of the app:
 * `NoteDocument -> .rnote -> NoteDocument`, and the same through the app's own JSON.
 *
 * The layer-by-layer tests each passed while the loop as a whole lost the page layout,
 * because the loss happened in the seams: [RnoteNativeSerializer.bridgeToNative] never
 * read `layoutMode`, and `appendDocument` never wrote the key that
 * [FileManager.bridgeNativeToNoteDocument] reads back.
 */
class DocumentRoundTripTest {

    private val eps = 1e-3f

    private fun documentWith(
        layout: LayoutMode,
        strokes: List<Stroke> = listOf(
            Stroke(
                points = listOf(StrokePoint(10f, 20f, 0.5f), StrokePoint(60f, 90f, 0.8f)),
                color = Color(0xFF224466),
                strokeWidth = 3f
            )
        )
    ) = NoteDocument(
        title = "Layout probe",
        paperStyle = PaperStyle(
            pattern = PaperPattern.GRID,
            pageSize = PageSize.CUSTOM,
            customWidthPx = 1123f,
            customHeightPx = 1587f,
            layoutMode = layout,
            showFormatBorders = false,
            showOriginIndicator = false,
            formatBorderColor = Color(0xFF8899AA)
        ),
        strokes = strokes
    )

    private fun throughRnote(doc: NoteDocument): NoteDocument {
        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, RnoteNativeSerializer.bridgeToNative(doc)) }
            .toByteArray()
        return FileManager.bridgeNativeToNoteDocument(
            RnoteNativeParser.parse(ByteArrayInputStream(bytes))
        )
    }

    @Test
    fun `every layout mode survives save and reopen as rnote`() {
        for (layout in LayoutMode.entries) {
            assertEquals(
                "layout $layout did not survive the round trip",
                layout,
                throughRnote(documentWith(layout)).paperStyle.layoutMode
            )
        }
    }

    @Test
    fun `every layout mode survives save and reopen as json`() {
        for (layout in LayoutMode.entries) {
            val reloaded = DocumentSerializer.parseJson(
                DocumentSerializer.toJson(documentWith(layout))
            )
            assertEquals(layout, reloaded.paperStyle.layoutMode)
        }
    }

    @Test
    fun `format border settings survive save and reopen as rnote`() {
        val reloaded = throughRnote(documentWith(LayoutMode.INFINITE))
        assertEquals(false, reloaded.paperStyle.showFormatBorders)
        assertEquals(false, reloaded.paperStyle.showOriginIndicator)
        assertEquals(Color(0xFF8899AA), reloaded.paperStyle.formatBorderColor)
    }

    @Test
    fun `the page format survives save and reopen as rnote`() {
        val reloaded = throughRnote(documentWith(LayoutMode.CONTINUOUS_VERTICAL))
        assertEquals(1123f, reloaded.paperStyle.effectivePageWidthPx, eps)
        assertEquals(1587f, reloaded.paperStyle.effectivePageHeightPx, eps)
        assertEquals(PaperPattern.GRID, reloaded.paperStyle.pattern)
    }

    @Test
    fun `the written extent covers content outside the page`() {
        // An infinite-layout document routinely holds strokes at negative coordinates;
        // a page-sized rect pinned at the origin does not describe it. The bounds are the
        // ink's, so a 2px-wide stroke reaches half a width past each of its endpoints.
        val doc = documentWith(
            LayoutMode.INFINITE,
            strokes = listOf(
                Stroke(
                    points = listOf(StrokePoint(-400f, -250f, 1f), StrokePoint(2000f, 3000f, 1f)),
                    color = Color.Black,
                    strokeWidth = 2f
                )
            )
        )
        val native = RnoteNativeSerializer.bridgeToNative(doc)
        assertEquals(-401f, native.originX, eps)
        assertEquals(-251f, native.originY, eps)
        assertEquals(2402f, native.totalWidth, eps)
        assertEquals(3252f, native.totalHeight, eps)
    }

    @Test
    fun `a continuous vertical document keeps a page of room below its content`() {
        // Desktop Rnote's own rule (Document::resize_autoexpand): the width is the
        // format's and the height is the ink plus one more page to write on. Writing the
        // bare page height instead is what made a continuous note open looking fixed.
        val native = RnoteNativeSerializer.bridgeToNative(
            documentWith(
                LayoutMode.CONTINUOUS_VERTICAL,
                strokes = listOf(
                    Stroke(
                        points = listOf(StrokePoint(40f, 100f, 1f), StrokePoint(300f, 2000f, 1f)),
                        color = Color.Black,
                        strokeWidth = 2f
                    )
                )
            )
        )
        assertEquals(0f, native.originX, eps)
        assertEquals(0f, native.originY, eps)
        assertEquals(1123f, native.totalWidth, eps)
        assertEquals(2001f + 1587f, native.totalHeight, eps)
    }

    @Test
    fun `an empty continuous vertical document is exactly one page`() {
        val native = RnoteNativeSerializer.bridgeToNative(
            documentWith(LayoutMode.CONTINUOUS_VERTICAL, strokes = emptyList())
        )
        assertEquals(1123f, native.totalWidth, eps)
        assertEquals(1587f, native.totalHeight, eps)
    }

    @Test
    fun `a fixed size document stays the format box whatever it holds`() {
        // Rnote keeps strokes that stray off the page but does not grow the page for them.
        val native = RnoteNativeSerializer.bridgeToNative(
            documentWith(
                LayoutMode.FIXED_SIZE,
                strokes = listOf(
                    Stroke(
                        points = listOf(StrokePoint(-500f, -500f, 1f), StrokePoint(4000f, 5000f, 1f)),
                        color = Color.Black,
                        strokeWidth = 2f
                    )
                )
            )
        )
        assertEquals(0f, native.originX, eps)
        assertEquals(0f, native.originY, eps)
        assertEquals(1123f, native.totalWidth, eps)
        assertEquals(1587f, native.totalHeight, eps)
    }

    @Test
    fun `the extent never shrinks below the page itself`() {
        val native = RnoteNativeSerializer.bridgeToNative(
            documentWith(LayoutMode.FIXED_SIZE, strokes = emptyList())
        )
        assertEquals(0f, native.originX, eps)
        assertEquals(0f, native.originY, eps)
        assertEquals(1123f, native.totalWidth, eps)
        assertEquals(1587f, native.totalHeight, eps)
    }

    @Test
    fun `strokes survive the whole loop`() {
        val reloaded = throughRnote(documentWith(LayoutMode.INFINITE))
        val stroke = reloaded.strokes.single()
        assertEquals(3f, stroke.strokeWidth, eps)
        assertEquals(2, stroke.points.size)
        assertEquals(10f, stroke.points[0].x, eps)
        assertEquals(0.8f, stroke.points[1].pressure, eps)
        assertTrue(stroke.color.red > 0.1f)
    }

    @Test
    fun `a file written without a layout key still opens unbounded`() {
        // Every .rnote this app wrote before the fix has no layout key. Those must not
        // collapse to a single page now that the reader has a say in the fallback.
        val legacy = RnoteNativeSerializer
            .bridgeToNative(documentWith(LayoutMode.INFINITE))
            .copy(layout = "")
        val bytes = ByteArrayOutputStream()
            .also { RnoteNativeSerializer.serialize(it, legacy) }
            .toByteArray()
        val reloaded = FileManager.bridgeNativeToNoteDocument(
            RnoteNativeParser.parse(ByteArrayInputStream(bytes))
        )
        assertEquals(LayoutMode.INFINITE, reloaded.paperStyle.layoutMode)
    }
}
