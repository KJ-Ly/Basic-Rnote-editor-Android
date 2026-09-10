package io.github.kjly.brna.export

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.render.androidStrokePath
import io.github.kjly.brna.render.svgStrokePathData
import java.util.Locale

/** The two [ExportCanvas] targets: android.graphics (PNG/JPEG/PDF) and SVG text. */

/**
 * Paints onto an `android.graphics.Canvas`. The canvas is expected to already carry the
 * scale and the translation that put the exported region at its origin, so everything
 * here is in plain document coordinates.
 */
class AndroidExportCanvas(private val canvas: android.graphics.Canvas) : ExportCanvas {

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    override fun fillRect(rect: Rect, color: Color) {
        fillPaint.color = color.toArgb()
        canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint)
    }

    override fun fillRoundRect(rect: Rect, cornerRadius: Float, color: Color) {
        fillPaint.color = color.toArgb()
        canvas.drawRoundRect(
            rect.left, rect.top, rect.right, rect.bottom,
            cornerRadius, cornerRadius, fillPaint
        )
    }

    override fun fillPolygon(points: List<Offset>, color: Color) {
        if (points.size < 3) return
        fillPaint.color = color.toArgb()
        val path = android.graphics.Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color) {
        linePaint.color = color.toArgb()
        linePaint.strokeWidth = width
        canvas.drawLine(x1, y1, x2, y2, linePaint)
    }

    override fun fillStroke(stroke: Stroke) {
        if (stroke.points.isEmpty()) return
        // toArgb() carries the alpha; a marker's transparency lives in its colour.
        fillPaint.color = stroke.color.toArgb()
        canvas.drawPath(
            androidStrokePath(stroke.points, stroke.strokeWidth, stroke.pressureCurve),
            fillPaint
        )
    }

    override fun clipped(rect: Rect, block: () -> Unit) {
        canvas.save()
        canvas.clipRect(rect.left, rect.top, rect.right, rect.bottom)
        block()
        canvas.restore()
    }
}

/**
 * Appends SVG elements to [sb]. Emits body elements only — [SvgExporter] writes the
 * document element around them, including the translation that moves the exported
 * region's top-left corner to (0,0).
 */
class SvgExportCanvas(private val sb: StringBuilder) : ExportCanvas {

    private var clipIdCounter = 0

    private fun n(v: Float) = String.format(Locale.ROOT, "%.3f", v)

    private fun hex(color: Color) =
        String.format(Locale.ROOT, "#%06X", 0xFFFFFF and color.toArgb())

    /** SVG carries opacity beside the colour rather than inside it. */
    private fun opacityAttr(color: Color, attr: String) =
        if (color.alpha < 1f) " $attr=\"${n(color.alpha)}\"" else ""

    override fun fillRect(rect: Rect, color: Color) {
        sb.append("  <rect x=\"${n(rect.left)}\" y=\"${n(rect.top)}\" ")
            .append("width=\"${n(rect.width)}\" height=\"${n(rect.height)}\" ")
            .append("fill=\"${hex(color)}\"${opacityAttr(color, "fill-opacity")} />\n")
    }

    override fun fillRoundRect(rect: Rect, cornerRadius: Float, color: Color) {
        sb.append("  <rect x=\"${n(rect.left)}\" y=\"${n(rect.top)}\" ")
            .append("width=\"${n(rect.width)}\" height=\"${n(rect.height)}\" ")
            .append("rx=\"${n(cornerRadius)}\" ry=\"${n(cornerRadius)}\" ")
            .append("fill=\"${hex(color)}\"${opacityAttr(color, "fill-opacity")} />\n")
    }

    override fun fillPolygon(points: List<Offset>, color: Color) {
        if (points.size < 3) return
        val pts = points.joinToString(" ") { "${n(it.x)},${n(it.y)}" }
        sb.append("  <polygon points=\"$pts\" ")
            .append("fill=\"${hex(color)}\"${opacityAttr(color, "fill-opacity")} />\n")
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color) {
        sb.append("  <line x1=\"${n(x1)}\" y1=\"${n(y1)}\" x2=\"${n(x2)}\" y2=\"${n(y2)}\" ")
            .append("stroke=\"${hex(color)}\"${opacityAttr(color, "stroke-opacity")} ")
            .append("stroke-width=\"${n(width)}\" />\n")
    }

    override fun fillStroke(stroke: Stroke) {
        if (stroke.points.isEmpty()) return
        // A filled outline rather than a stroked centreline: stroke.strokeWidth is a
        // nominal maximum that the pressure curve scales at every point, so no single
        // stroke-width attribute could be correct. See StrokeOutline.
        val pathData = svgStrokePathData(stroke.points, stroke.strokeWidth, stroke.pressureCurve)
        if (pathData.isEmpty()) return
        sb.append("  <path d=\"$pathData\" fill=\"${hex(stroke.color)}\" fill-rule=\"nonzero\"")
            .append(opacityAttr(stroke.color, "fill-opacity"))
            .append(" />\n")
    }

    override fun clipped(rect: Rect, block: () -> Unit) {
        val id = "clip${clipIdCounter++}"
        sb.append("  <clipPath id=\"$id\">")
            .append("<rect x=\"${n(rect.left)}\" y=\"${n(rect.top)}\" ")
            .append("width=\"${n(rect.width)}\" height=\"${n(rect.height)}\" />")
            .append("</clipPath>\n")
        sb.append("  <g clip-path=\"url(#$id)\">\n")
        block()
        sb.append("  </g>\n")
    }
}
