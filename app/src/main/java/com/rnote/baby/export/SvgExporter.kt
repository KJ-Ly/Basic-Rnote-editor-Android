package com.rnote.baby.export

import androidx.compose.ui.graphics.toArgb
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.render.svgStrokePathData
import java.util.Locale

object SvgExporter {

    /**
     * Converts a NoteDocument into a W3C compliant SVG vector XML string.
     */
    fun exportToSvg(document: NoteDocument, widthPx: Float = 0f, heightPx: Float = 0f): String {
        val w = if (widthPx > 0f) widthPx else document.paperStyle.effectivePageWidthPx.coerceAtLeast(1920f)
        val h = if (heightPx > 0f) heightPx else document.paperStyle.effectivePageHeightPx.coerceAtLeast(1080f)
        val sb = StringBuilder()
        val bgHex = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and document.paperStyle.currentBackgroundColor.toArgb())

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        sb.append("<svg width=\"${w.toInt()}\" height=\"${h.toInt()}\" viewBox=\"0 0 $w $h\" xmlns=\"http://www.w3.org/2000/svg\">\n")

        // Background Rect
        sb.append("  <rect width=\"100%\" height=\"100%\" fill=\"$bgHex\" />\n")

        // Render Vector Strokes as SVG <path> elements
        for (stroke in document.strokes) {
            if (stroke.points.isEmpty()) continue

            val colorHex = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and stroke.color.toArgb())
            val opacity = stroke.color.alpha

            // A filled outline rather than a stroked centreline: stroke.strokeWidth is a
            // nominal maximum that the pressure curve scales at every point, so no single
            // stroke-width attribute could be correct. See StrokeOutline.
            val pathData = svgStrokePathData(stroke.points, stroke.strokeWidth, stroke.pressureCurve)
            if (pathData.isEmpty()) continue

            sb.append("  <path d=\"$pathData\" ")
            sb.append("fill=\"$colorHex\" ")
            sb.append("fill-rule=\"nonzero\" ")
            if (opacity < 1.0f) {
                sb.append("fill-opacity=\"$opacity\" ")
            }
            sb.append("/>\n")
        }

        sb.append("</svg>")
        return sb.toString()
    }
}
