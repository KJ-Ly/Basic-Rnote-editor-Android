package io.github.kjly.brna.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Rect
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.model.Stroke
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageExporter {

    /** Rnote's bitmap scale-factor range. */
    const val MIN_SCALE = 0.1f
    const val MAX_SCALE = 10f

    /**
     * Ceiling on the output bitmap, in pixels. A whole infinite-layout document at scale
     * 10 is an easy way to ask for a few gigabytes; past this the scale is reduced to fit
     * rather than the export failing on an OutOfMemoryError.
     */
    private const val MAX_PIXELS = 64_000_000L

    /**
     * Renders one region of a document to PNG or JPEG.
     *
     * JPEG has no alpha channel, so a background-less JPEG is painted on white — the same
     * thing Rnote does, since the alternative is a black page.
     */
    fun exportBitmap(
        paperStyle: PaperStyle,
        strokes: List<Stroke>,
        region: Rect,
        prefs: ExportPrefs,
        out: OutputStream,
        pages: List<Rect> = emptyList()
    ): Boolean {
        val regionW = region.width.coerceAtLeast(1f)
        val regionH = region.height.coerceAtLeast(1f)
        val scale = effectiveScale(prefs.bitmapScaleFactor, regionW, regionH)

        val w = (regionW * scale).roundToInt().coerceAtLeast(1)
        val h = (regionH * scale).roundToInt().coerceAtLeast(1)

        var bitmap: Bitmap? = null
        return try {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (prefs.format == ExportFormat.JPEG && !prefs.withBackground) {
                canvas.drawColor(AndroidColor.WHITE)
            }
            canvas.scale(scale, scale)
            canvas.translate(-region.left, -region.top)

            DocumentPainter.paint(
                AndroidExportCanvas(canvas), paperStyle, strokes, region, prefs, pages
            )

            val compressFormat =
                if (prefs.format == ExportFormat.JPEG) Bitmap.CompressFormat.JPEG
                else Bitmap.CompressFormat.PNG
            val quality = if (prefs.format == ExportFormat.JPEG) prefs.jpegQuality.coerceIn(1, 100) else 100
            bitmap.compress(compressFormat, quality, out)
            out.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            bitmap?.recycle()
        }
    }

    /** The requested scale, clamped to the range and then to [MAX_PIXELS]. */
    fun effectiveScale(requested: Float, regionW: Float, regionH: Float): Float {
        val clamped = requested.coerceIn(MIN_SCALE, MAX_SCALE)
        val pixels = regionW.toDouble() * regionH.toDouble() * clamped * clamped
        if (pixels <= MAX_PIXELS) return clamped
        val fitted = sqrt(MAX_PIXELS / (regionW.toDouble() * regionH.toDouble())).toFloat()
        return min(clamped, fitted).coerceAtLeast(MIN_SCALE)
    }
}
