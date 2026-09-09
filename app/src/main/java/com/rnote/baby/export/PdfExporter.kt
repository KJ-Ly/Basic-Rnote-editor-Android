package com.rnote.baby.export

import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Rect
import com.rnote.baby.model.CANVAS_DPI
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.Stroke
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * PDF is one of desktop Rnote's document export formats, and like there it is paginated:
 * one PDF page per document page, in the chosen page order.
 *
 * `PdfDocument` records the draw calls rather than rasterising them, so the ink stays
 * vector — the same reason Rnote's own PDF export is not a screenshot.
 */
object PdfExporter {

    /** PDF's unit is the point, 1/72". Canvas px are [CANVAS_DPI] to the inch. */
    private const val POINTS_PER_CANVAS_PX = 72f / CANVAS_DPI

    fun export(
        paperStyle: PaperStyle,
        strokes: List<Stroke>,
        pages: List<Rect>,
        prefs: ExportPrefs,
        out: OutputStream
    ): Boolean {
        if (pages.isEmpty()) return false
        val pdf = PdfDocument()
        return try {
            pages.forEachIndexed { index, page ->
                val widthPt = (page.width * POINTS_PER_CANVAS_PX).roundToInt().coerceAtLeast(1)
                val heightPt = (page.height * POINTS_PER_CANVAS_PX).roundToInt().coerceAtLeast(1)
                val pdfPage = pdf.startPage(
                    PdfDocument.PageInfo.Builder(widthPt, heightPt, index + 1).create()
                )
                val canvas = pdfPage.canvas
                canvas.scale(POINTS_PER_CANVAS_PX, POINTS_PER_CANVAS_PX)
                canvas.translate(-page.left, -page.top)

                // `pages` is passed as this one page: the pattern is aligned to the
                // document origin either way, and clipping it to the page keeps a
                // non-dividing spacing from bleeding over the edge.
                DocumentPainter.paint(
                    AndroidExportCanvas(canvas), paperStyle, strokes, page, prefs, listOf(page)
                )
                pdf.finishPage(pdfPage)
            }
            pdf.writeTo(out)
            out.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pdf.close()
        }
    }
}
