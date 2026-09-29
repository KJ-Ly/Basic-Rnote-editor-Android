package io.github.kjly.brna.export

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Rect
import io.github.kjly.brna.model.NoteDocument
import kotlin.math.roundToInt

/** Small pictures of a note's pages, for the page overview. */
object PageThumbnails {

    /** A thumbnail can't be taller than this many times its width: a strip, not a scroll. */
    private const val MAX_ASPECT = 4f

    /**
     * [page] of [document] drawn [widthPx] wide, exactly as a page export would draw it
     * (paper, pattern, PDF pages, ink, text) only smaller. Null if it can't be made — out
     * of memory, most likely. Call off the main thread: a PDF page is a big SVG to draw.
     */
    fun render(document: NoteDocument, page: Rect, widthPx: Int): Bitmap? = try {
        val w = page.width.coerceAtLeast(1f)
        val h = page.height.coerceAtLeast(1f).coerceAtMost(w * MAX_ASPECT)
        val scale = widthPx / w
        val bitmap = Bitmap.createBitmap(widthPx, (h * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-page.left, -page.top)
        val region = Rect(page.left, page.top, page.right, page.top + h)
        DocumentPainter.paint(
            AndroidExportCanvas(canvas), document.paperStyle, document.strokes, region,
            ExportPrefs(), listOf(page), document.nativeElements
        )
        bitmap
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}
