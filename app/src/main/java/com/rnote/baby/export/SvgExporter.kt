package com.rnote.baby.export

import androidx.compose.ui.graphics.toArgb
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.model.ToolType
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
            val opacity = stroke.alpha
            val strokeWidth = stroke.width
            val strokeCap = if (stroke.toolType == ToolType.HIGHLIGHTER) "square" else "round"

            // Construct SVG path data string from points using smooth Bézier curves
            val pathData = buildSvgPathData(stroke.points)

            sb.append("  <path d=\"$pathData\" ")
            sb.append("fill=\"none\" ")
            sb.append("stroke=\"$colorHex\" ")
            sb.append("stroke-width=\"$strokeWidth\" ")
            sb.append("stroke-linecap=\"$strokeCap\" ")
            sb.append("stroke-linejoin=\"round\" ")
            if (opacity < 1.0f) {
                sb.append("stroke-opacity=\"$opacity\" ")
            }
            sb.append("/>\n")
        }

        sb.append("</svg>")
        return sb.toString()
    }

    private fun buildSvgPathData(points: List<com.rnote.baby.model.InkPoint>): String {
        if (points.isEmpty()) return ""
        if (points.size == 1) {
            val pt = points[0]
            return "M ${pt.x} ${pt.y} L ${pt.x + 0.1f} ${pt.y + 0.1f}"
        }

        val sb = StringBuilder()
        sb.append(String.format(Locale.ROOT, "M %.2f %.2f", points[0].x, points[0].y))

        for (i in 1 until points.size - 1) {
            val curr = points[i]
            val next = points[i + 1]
            val midX = (curr.x + next.x) / 2f
            val midY = (curr.y + next.y) / 2f

            sb.append(String.format(Locale.ROOT, " Q %.2f %.2f, %.2f %.2f", curr.x, curr.y, midX, midY))
        }

        val last = points.last()
        sb.append(String.format(Locale.ROOT, " L %.2f %.2f", last.x, last.y))

        return sb.toString()
    }
}
