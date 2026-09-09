package com.rnote.baby.export

import androidx.compose.ui.geometry.Rect
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.Stroke
import java.util.Locale

object SvgExporter {

    /**
     * Renders one region of a document to a W3C compliant SVG string. The region's
     * top-left corner becomes the SVG origin, so the file has no leading whitespace
     * whichever part of the canvas it came from.
     */
    fun export(
        paperStyle: PaperStyle,
        strokes: List<Stroke>,
        region: Rect,
        prefs: ExportPrefs,
        pages: List<Rect> = emptyList()
    ): String {
        val w = region.width.coerceAtLeast(1f)
        val h = region.height.coerceAtLeast(1f)
        fun n(v: Float) = String.format(Locale.ROOT, "%.3f", v)

        val body = StringBuilder()
        DocumentPainter.paint(SvgExportCanvas(body), paperStyle, strokes, region, prefs, pages)

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
            append("<svg width=\"${n(w)}\" height=\"${n(h)}\" ")
            append("viewBox=\"0 0 ${n(w)} ${n(h)}\" xmlns=\"http://www.w3.org/2000/svg\">\n")
            append("<g transform=\"translate(${n(-region.left)},${n(-region.top)})\">\n")
            append(body)
            append("</g>\n")
            append("</svg>")
        }
    }
}
