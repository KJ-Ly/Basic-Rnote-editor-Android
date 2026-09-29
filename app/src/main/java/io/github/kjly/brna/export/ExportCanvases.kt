package io.github.kjly.brna.export

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.LineShape
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.TextFormatting
import io.github.kjly.brna.model.TextRun
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.PathOp
import io.github.kjly.brna.model.PathShape
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.render.NativeElementRenderer
import io.github.kjly.brna.render.RoughOp
import io.github.kjly.brna.render.RoughSetType
import io.github.kjly.brna.render.RoughShapes
import io.github.kjly.brna.render.VectorImageRenderer
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
            androidStrokePath(stroke.points, stroke.strokeWidth, stroke.pressureCurve, stroke.textured),
            fillPaint
        )
    }

    private val nativeRenderer = NativeElementRenderer()

    override fun drawNative(element: NativeCanvasElement) {
        when (element) {
            // Drawn as vectors, so a PDF export keeps an imported page's text sharp.
            is NativeVectorImageElement -> VectorImageRenderer.renderVector(canvas, element)
            // Not recycled: a PDF page canvas may still be holding on to it until the page
            // is finished.
            is NativeBitmapElement -> NativeElementRenderer.decodeBitmap(element)
                ?.let { nativeRenderer.drawBitmap(canvas, element, it) }
            is NativeTextElement -> nativeRenderer.drawText(canvas, element)
            is NativeShapeElement -> nativeRenderer.drawShape(canvas, element)
            is NativeBrushStroke -> Unit
        }
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
        val pathData = svgStrokePathData(stroke.points, stroke.strokeWidth, stroke.pressureCurve, stroke.textured)
        if (pathData.isEmpty()) return
        sb.append("  <path d=\"$pathData\" fill=\"${hex(stroke.color)}\" fill-rule=\"nonzero\"")
            .append(opacityAttr(stroke.color, "fill-opacity"))
            .append(" />\n")
    }

    override fun drawNative(element: NativeCanvasElement) {
        when (element) {
            is NativeVectorImageElement -> svgVectorImage(element)
            is NativeBitmapElement -> svgBitmap(element)
            is NativeTextElement -> svgText(element)
            is NativeShapeElement -> svgShape(element)
            is NativeBrushStroke -> Unit
        }
    }

    private fun matrixAttr(t: FloatArray) =
        "matrix(${n(t[0])} ${n(t[1])} ${n(t[2])} ${n(t[3])} ${n(t[4])} ${n(t[5])})"

    private fun hexOf(c: RnoteNativeColor) =
        String.format(Locale.ROOT, "#%06X", 0xFFFFFF and NativeElementRenderer.argb(c))

    private fun esc(text: String) = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * The page's own SVG nested in place, exactly as Rnote's `VectorImage::gen_svg` builds
     * it: stretched over the rectangle, then transformed.
     */
    private fun svgVectorImage(el: NativeVectorImageElement) {
        val data = el.svgData.trimStart().let { if (it.startsWith("<?xml")) it.substringAfter("?>") else it }
        sb.append("  <g transform=\"${matrixAttr(el.transform)}\">")
            .append("<svg x=\"${n(-el.halfExtentX)}\" y=\"${n(-el.halfExtentY)}\" ")
            .append("width=\"${n(2f * el.halfExtentX)}\" height=\"${n(2f * el.halfExtentY)}\" ")
            .append("viewBox=\"0 0 ${n(el.intrinsicWidth)} ${n(el.intrinsicHeight)}\" preserveAspectRatio=\"none\">")
            .append(data)
            .append("</svg></g>\n")
    }

    private fun svgBitmap(el: NativeBitmapElement) {
        val bitmap = NativeElementRenderer.decodeBitmap(el) ?: return
        val png = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png)
        bitmap.recycle()
        val b64 = android.util.Base64.encodeToString(png.toByteArray(), android.util.Base64.NO_WRAP)
        val rect = el.rect
        if (rect != null) {
            sb.append("  <image transform=\"${matrixAttr(rect.transform)}\" ")
                .append("x=\"${n(-rect.halfExtentX)}\" y=\"${n(-rect.halfExtentY)}\" ")
                .append("width=\"${n(2f * rect.halfExtentX)}\" height=\"${n(2f * rect.halfExtentY)}\" ")
        } else {
            sb.append("  <image x=\"${n(el.minX)}\" y=\"${n(el.minY)}\" ")
                .append("width=\"${n(el.maxX - el.minX)}\" height=\"${n(el.maxY - el.minY)}\" ")
        }
        sb.append("preserveAspectRatio=\"none\" href=\"data:image/png;base64,").append(b64).append("\" />\n")
    }

    /** One `tspan` per line; wrapping at the text box's width is left to the viewer. */
    private fun svgText(el: NativeTextElement) {
        val width = el.maxWidth
        val (anchor, x) = when {
            width != null && el.alignment == "center" -> "middle" to width / 2f
            width != null && el.alignment == "end" -> "end" to width
            else -> "start" to 0f
        }
        sb.append("  <text transform=\"${matrixAttr(el.transform)}\" xml:space=\"preserve\" ")
            .append("font-family=\"${esc(el.fontFamily)}\" font-size=\"${n(el.fontSize)}\" ")
            .append("font-weight=\"${el.fontWeight}\" font-style=\"${if (el.italic) "italic" else "normal"}\" ")
            .append("text-anchor=\"$anchor\" fill=\"${hexOf(el.color)}\"")
        if (el.color.a < 1f) sb.append(" fill-opacity=\"${n(el.color.a)}\"")
        sb.append(">")
        // Each line in a tspan of its own; within it, a nested tspan for every stretch
        // whose ranged attributes set it apart from the box's own style.
        val runs = TextFormatting.runs(el)
        var lineStart = 0
        el.text.split('\n').forEachIndexed { i, line ->
            val lineEnd = lineStart + line.length
            sb.append("<tspan x=\"${n(x)}\" dy=\"${if (i == 0) "1em" else "1.25em"}\">")
            for (run in runs) {
                val a = maxOf(run.start, lineStart)
                val b = minOf(run.end, lineEnd)
                if (b <= a) continue
                val piece = esc(el.text.substring(a, b))
                val attrs = svgRunAttributes(run, el)
                if (attrs.isEmpty()) sb.append(piece) else sb.append("<tspan").append(attrs).append(">").append(piece).append("</tspan>")
            }
            sb.append("</tspan>")
            lineStart = lineEnd + 1
        }
        sb.append("</text>\n")
    }

    /** SVG attributes for what sets [run] apart from [el]'s own style; empty when nothing does. */
    private fun svgRunAttributes(run: TextRun, el: NativeTextElement): String = buildString {
        if (run.family != el.fontFamily) append(" font-family=\"${esc(run.family)}\"")
        if (run.size != el.fontSize) append(" font-size=\"${n(run.size)}\"")
        if (run.weight != el.fontWeight) append(" font-weight=\"${run.weight}\"")
        if (run.italic != el.italic) append(" font-style=\"${if (run.italic) "italic" else "normal"}\"")
        val decoration = listOfNotNull("underline".takeIf { run.underline }, "line-through".takeIf { run.strikethrough })
        if (decoration.isNotEmpty()) append(" text-decoration=\"${decoration.joinToString(" ")}\"")
        if (run.color != el.color) {
            append(" fill=\"${hexOf(run.color)}\"")
            if (run.color.a < 1f) append(" fill-opacity=\"${n(run.color.a)}\"")
        }
    }

    private fun svgShape(el: NativeShapeElement) {
        if (el.rough != null && svgRoughShape(el)) return
        val shape = el.shape
        val geometry = when (shape) {
            is LineShape ->
                "<path d=\"M ${n(shape.x1)} ${n(shape.y1)} L ${n(shape.x2)} ${n(shape.y2)}\""
            is RectShape ->
                "<rect transform=\"${matrixAttr(shape.transform)}\" " +
                    "x=\"${n(-shape.halfExtentX)}\" y=\"${n(-shape.halfExtentY)}\" " +
                    "width=\"${n(2f * shape.halfExtentX)}\" height=\"${n(2f * shape.halfExtentY)}\""
            is EllipseShape ->
                "<ellipse transform=\"${matrixAttr(shape.transform)}\" cx=\"0\" cy=\"0\" " +
                    "rx=\"${n(shape.radiusX)}\" ry=\"${n(shape.radiusY)}\""
            is PathShape -> {
                val d = shape.ops.joinToString(" ") { op ->
                    when (op) {
                        is PathOp.MoveTo -> "M ${n(op.x)} ${n(op.y)}"
                        is PathOp.LineTo -> "L ${n(op.x)} ${n(op.y)}"
                        is PathOp.QuadTo -> "Q ${n(op.x1)} ${n(op.y1)} ${n(op.x)} ${n(op.y)}"
                        is PathOp.CubicTo ->
                            "C ${n(op.x1)} ${n(op.y1)} ${n(op.x2)} ${n(op.y2)} ${n(op.x)} ${n(op.y)}"
                        PathOp.Close -> "Z"
                    }
                }
                "<path d=\"$d\""
            }
        }
        sb.append("  ").append(geometry)
        if (el.fillColor.a > 0f) {
            sb.append(" fill=\"${hexOf(el.fillColor)}\"")
            if (el.fillColor.a < 1f) sb.append(" fill-opacity=\"${n(el.fillColor.a)}\"")
        } else {
            sb.append(" fill=\"none\"")
        }
        if (el.strokeWidth > 0f && el.color.a > 0f) {
            sb.append(" stroke=\"${hexOf(el.color)}\" stroke-width=\"${n(el.strokeWidth)}\"")
            if (el.color.a < 1f) sb.append(" stroke-opacity=\"${n(el.color.a)}\"")
            sb.append(" stroke-linejoin=\"round\" stroke-linecap=\"${if (el.roundCap) "round" else "butt"}\"")
            NativeElementRenderer.dashPattern(el.lineStyle, el.strokeWidth, el.roundCap)?.let {
                sb.append(" stroke-dasharray=\"${n(it[0])} ${n(it[1])}\"")
            }
        }
        sb.append(" />\n")
    }

    /** A rough shape as [NativeElementRenderer] draws it, set by set; false when it has no rough drawing. */
    private fun svgRoughShape(el: NativeShapeElement): Boolean {
        val composed = RoughShapes.compose(el) ?: return false
        val indent = if (composed.transform != null) "    " else "  "
        composed.transform?.let { sb.append("  <g transform=\"${matrixAttr(it)}\">\n") }
        for (drawable in composed.drawables) {
            for (set in drawable.sets) {
                val d = set.ops.joinToString(" ") { op ->
                    when (op) {
                        is RoughOp.Move -> "M ${n(op.x.toFloat())} ${n(op.y.toFloat())}"
                        is RoughOp.Line -> "L ${n(op.x.toFloat())} ${n(op.y.toFloat())}"
                        is RoughOp.Curve -> "C ${n(op.x1.toFloat())} ${n(op.y1.toFloat())} " +
                            "${n(op.x2.toFloat())} ${n(op.y2.toFloat())} ${n(op.x.toFloat())} ${n(op.y.toFloat())}"
                    }
                }
                sb.append(indent).append("<path d=\"").append(d).append('"')
                when (set.type) {
                    RoughSetType.FILL_PATH -> {
                        sb.append(" fill=\"${hexOf(el.fillColor)}\"")
                        if (el.fillColor.a < 1f) sb.append(" fill-opacity=\"${n(el.fillColor.a)}\"")
                        if (drawable.evenOdd) sb.append(" fill-rule=\"evenodd\"")
                    }
                    RoughSetType.PATH, RoughSetType.FILL_SKETCH -> {
                        val color = if (set.type == RoughSetType.PATH) el.color else el.fillColor
                        val width = if (set.type == RoughSetType.PATH) drawable.options.strokeWidth else drawable.fillWeight
                        sb.append(" fill=\"none\" stroke=\"${hexOf(color)}\" stroke-width=\"${n(width)}\"")
                        if (color.a < 1f) sb.append(" stroke-opacity=\"${n(color.a)}\"")
                        sb.append(" stroke-linecap=\"butt\" stroke-linejoin=\"miter\" stroke-miterlimit=\"10\"")
                    }
                }
                sb.append(" />\n")
            }
        }
        if (composed.transform != null) sb.append("  </g>\n")
        return true
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
