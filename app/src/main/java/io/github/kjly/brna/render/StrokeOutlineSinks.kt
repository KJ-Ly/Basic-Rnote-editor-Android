package io.github.kjly.brna.render

import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.model.TexturedStyle
import java.util.Locale

/**
 * The three [StrokeOutline.Sink] targets the app renders to. Every one of them fills the
 * outline with the non-zero rule (the default in all three), matching `cx.fill` upstream.
 */

/** Compose canvas. */
fun composeStrokePath(
    points: List<StrokePoint>,
    strokeWidth: Float,
    curve: PressureCurve,
    /** Set for a stroke in Rnote's Textured style: its dots, not an outline. */
    textured: TexturedStyle? = null
): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    emitOutline(points, strokeWidth, curve, textured, object : StrokeOutline.Sink {
        override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
        override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
        override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
            path.cubicTo(c1x, c1y, c2x, c2y, x, y)
        override fun close() = path.close()
        override fun circle(cx: Float, cy: Float, radius: Float) {
            path.addOval(
                androidx.compose.ui.geometry.Rect(
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = radius
                )
            )
        }
    })
    return path
}

/** PNG export, via android.graphics. */
fun androidStrokePath(
    points: List<StrokePoint>,
    strokeWidth: Float,
    curve: PressureCurve,
    /** Set for a stroke in Rnote's Textured style: its dots, not an outline. */
    textured: TexturedStyle? = null
): android.graphics.Path {
    val path = android.graphics.Path()
    emitOutline(points, strokeWidth, curve, textured, object : StrokeOutline.Sink {
        override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
        override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
        override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
            path.cubicTo(c1x, c1y, c2x, c2y, x, y)
        override fun close() = path.close()
        override fun circle(cx: Float, cy: Float, radius: Float) =
            path.addCircle(cx, cy, radius, android.graphics.Path.Direction.CW)
    })
    return path
}

/** SVG export: the `d` attribute of a single filled path. */
fun svgStrokePathData(
    points: List<StrokePoint>,
    strokeWidth: Float,
    curve: PressureCurve,
    /** Set for a stroke in Rnote's Textured style: its dots, not an outline. */
    textured: TexturedStyle? = null
): String {
    val sb = StringBuilder()
    fun n(v: Float) = String.format(Locale.ROOT, "%.3f", v)
    emitOutline(points, strokeWidth, curve, textured, object : StrokeOutline.Sink {
        override fun moveTo(x: Float, y: Float) { sb.append("M${n(x)},${n(y)} ") }
        override fun lineTo(x: Float, y: Float) { sb.append("L${n(x)},${n(y)} ") }
        override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
            sb.append("C${n(c1x)},${n(c1y)} ${n(c2x)},${n(c2y)} ${n(x)},${n(y)} ")
        }
        override fun close() { sb.append("Z ") }
        override fun circle(cx: Float, cy: Float, radius: Float) {
            // Two half-arcs — keeps a lone point inside the same <path> as everything else.
            sb.append("M${n(cx - radius)},${n(cy)} ")
            sb.append("A${n(radius)},${n(radius)} 0 1,0 ${n(cx + radius)},${n(cy)} ")
            sb.append("A${n(radius)},${n(radius)} 0 1,0 ${n(cx - radius)},${n(cy)} Z ")
        }
    })
    return sb.toString().trim()
}

/** The stroke's filled geometry: Rnote's textured dots when it has them, else its outline. */
private fun emitOutline(
    points: List<StrokePoint>,
    strokeWidth: Float,
    curve: PressureCurve,
    textured: TexturedStyle?,
    sink: StrokeOutline.Sink
) {
    if (textured != null) {
        TexturedDots.emit(points, strokeWidth, curve, textured, sink)
    } else {
        StrokeOutline.emit(points, strokeWidth, curve, sink)
    }
}
