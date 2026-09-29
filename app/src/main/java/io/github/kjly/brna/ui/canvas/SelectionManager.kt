package io.github.kjly.brna.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.kjly.brna.model.Affine
import io.github.kjly.brna.model.InvertedBrightness
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.render.BezierLines
import io.github.kjly.brna.storage.NativeEditing
import kotlin.math.hypot

object SelectionManager {

    /**
     * Determines which strokes are enclosed or intersected by the lasso polygon points.
     */
    fun findStrokesInLasso(lassoPoints: List<Offset>, strokes: List<Stroke>): List<Stroke> {
        if (lassoPoints.size < 3 || strokes.isEmpty()) return emptyList()

        return strokes.filter { stroke ->
            // A curved segment counts by its curve, not the line between its ends.
            val strokePoints = BezierLines.flattened(stroke.points)
            if (strokePoints.isEmpty()) false
            else {
                val insideCount = strokePoints.count { pt ->
                    isPointInPolygon(Offset(pt.x, pt.y), lassoPoints)
                }
                (insideCount.toFloat() / strokePoints.size) >= 0.25f
            }
        }
    }

    /**
     * Rnote's rectangle selection: the strokes lying wholly inside the box dragged from
     * [a] to [b], in either direction.
     */
    fun strokesInRect(a: Offset, b: Offset, strokes: List<Stroke>): List<Stroke> {
        val l = minOf(a.x, b.x); val r = maxOf(a.x, b.x)
        val t = minOf(a.y, b.y); val btm = maxOf(a.y, b.y)
        return strokes.filter { s ->
            s.points.isNotEmpty() && BezierLines.flattened(s.points).all { it.x in l..r && it.y in t..btm }
        }
    }

    /**
     * Rnote's intersecting-path selection: the strokes the drawn line [path] crosses
     * anywhere. As in Rnote, each piece of a stroke counts as the box around it, widened
     * by half the stroke's width, so touching the ink is enough.
     */
    fun strokesCrossedByPath(path: List<Offset>, strokes: List<Stroke>): List<Stroke> {
        if (path.size < 3) return emptyList()
        var pl = Float.MAX_VALUE; var pt = Float.MAX_VALUE; var pr = -Float.MAX_VALUE; var pb = -Float.MAX_VALUE
        for (p in path) { pl = minOf(pl, p.x); pt = minOf(pt, p.y); pr = maxOf(pr, p.x); pb = maxOf(pb, p.y) }
        return strokes.filter { s ->
            val pad = s.strokeWidth / 2f
            // One piece of the stroke, as its hitbox.
            fun crosses(p1: StrokePoint, p2: StrokePoint): Boolean {
                val l = minOf(p1.x, p2.x) - pad; val r = maxOf(p1.x, p2.x) + pad
                val t = minOf(p1.y, p2.y) - pad; val b = maxOf(p1.y, p2.y) + pad
                return l <= pr && r >= pl && t <= pb && b >= pt &&
                    (1 until path.size).any { j ->
                        NativeEditing.segmentHitsBox(path[j - 1].x, path[j - 1].y, path[j].x, path[j].y, l, t, r, b)
                    }
            }
            // A curved segment is its pieces, each a hitbox of its own.
            val pts = BezierLines.flattened(s.points)
            when (pts.size) {
                0 -> false
                1 -> crosses(pts[0], pts[0])
                else -> (1 until pts.size).any { crosses(pts[it - 1], pts[it]) }
            }
        }
    }

    /**
     * Rnote's single selection: of the strokes under [point], the one drawn last — the
     * one on top. [tolerance] is added to the ink's own half width, so a thin line can
     * still be hit with a pen.
     */
    fun strokeAt(point: Offset, strokes: List<Stroke>, tolerance: Float): Stroke? =
        strokes.lastOrNull { s ->
            val reach = s.strokeWidth / 2f + tolerance
            val pts = BezierLines.flattened(s.points)
            when (pts.size) {
                0 -> false
                1 -> hypot(point.x - pts[0].x, point.y - pts[0].y) <= reach
                else -> (1 until pts.size).any { i ->
                    distanceToSegment(point, pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y) <= reach
                }
            }
        }

    /** What a tap picked in single selection: a stroke of ink, or a desktop element. */
    sealed interface Pick {
        data class Ink(val stroke: Stroke) : Pick
        data class Element(val element: NativeCanvasElement) : Pick
    }

    /**
     * Rnote's single selection: the topmost thing at [point], in the order the canvas
     * draws — text boxes and shapes over the ink, the ink over pictures and PDF pages,
     * and within each the one added last on top.
     */
    fun pickAt(point: Offset, strokes: List<Stroke>, natives: List<NativeCanvasElement>, tolerance: Float): Pick? {
        fun isPicture(el: NativeCanvasElement) = el is NativeBitmapElement || el is NativeVectorImageElement
        natives.lastOrNull { !isPicture(it) && NativeEditing.hitAt(it, point.x, point.y, tolerance) }
            ?.let { return Pick.Element(it) }
        strokeAt(point, strokes, tolerance)?.let { return Pick.Ink(it) }
        return natives.lastOrNull { isPicture(it) && NativeEditing.hitAt(it, point.x, point.y, tolerance) }
            ?.let { Pick.Element(it) }
    }

    /** [strokes] in [color]; a Marker's stroke keeps its translucency, which is what makes it a marker. */
    fun recolored(strokes: List<Stroke>, color: androidx.compose.ui.graphics.Color): List<Stroke> =
        strokes.map { s -> s.copy(color = if (s.isHighlighter) color.copy(alpha = s.color.alpha) else color, source = null) }

    /** [strokes] in the colours Rnote's "Invert Color Brightness" gives them; see [InvertedBrightness]. */
    fun inverted(strokes: List<Stroke>): List<Stroke> = strokes.map { s ->
        val rgb = InvertedBrightness.of(s.color.red, s.color.green, s.color.blue)
        s.copy(color = androidx.compose.ui.graphics.Color(rgb[0], rgb[1], rgb[2], s.color.alpha), source = null)
    }

    private fun distanceToSegment(p: Offset, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        val lengthSq = dx * dx + dy * dy
        val u = if (lengthSq == 0f) 0f else (((p.x - x1) * dx + (p.y - y1) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(p.x - (x1 + u * dx), p.y - (y1 + u * dy))
    }

    /**
     * Calculates the tight bounding box surrounding a list of selected strokes.
     */
    fun calculateBoundingBox(selectedStrokes: List<Stroke>): Rect? {
        if (selectedStrokes.isEmpty()) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (stroke in selectedStrokes) {
            for (pt in BezierLines.flattened(stroke.points)) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }

        if (minX > maxX || minY > maxY) return null
        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * Moves a list of strokes by delta offset.
     */
    fun translateStrokes(strokes: List<Stroke>, delta: Offset): List<Stroke> {
        return strokes.map { stroke ->
            val newPoints = stroke.points.map { pt ->
                pt.copy(
                    x = pt.x + delta.x, y = pt.y + delta.y,
                    curve = pt.curve?.mapped({ x, _ -> x + delta.x }, { _, y -> y + delta.y })
                )
            }
            // Moved, it is no longer what the file had; see RnoteStrokeSource.
            stroke.copy(points = newPoints, source = null)
        }
    }

    /**
     * Scales a list of strokes relative to a center point.
     */
    fun scaleStrokes(strokes: List<Stroke>, center: Offset, scaleFactor: Float): List<Stroke> {
        return strokes.map { stroke ->
            fun scaledX(x: Float) = center.x + (x - center.x) * scaleFactor
            fun scaledY(y: Float) = center.y + (y - center.y) * scaleFactor
            val newPoints = stroke.points.map { pt ->
                pt.copy(
                    x = scaledX(pt.x), y = scaledY(pt.y),
                    curve = pt.curve?.mapped({ x, _ -> scaledX(x) }, { _, y -> scaledY(y) })
                )
            }
            stroke.copy(points = newPoints, strokeWidth = stroke.strokeWidth * scaleFactor, source = null)
        }
    }

    /**
     * Applies the affine [m] (see [Affine]) to strokes — the selector's scale and rotate.
     * Widths scale with the geometric mean of the scale factors, as Rnote's
     * `BrushStroke::scale` does, so a rotation leaves them alone.
     */
    fun transformStrokes(strokes: List<Stroke>, m: FloatArray): List<Stroke> {
        val widthFactor = Affine.widthFactor(m)
        return strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { pt ->
                    pt.copy(
                        x = Affine.mapX(m, pt.x, pt.y), y = Affine.mapY(m, pt.x, pt.y),
                        // A curve's control points go where the transform takes them, as
                        // Rnote's `Segment::transform` sends them.
                        curve = pt.curve?.mapped({ x, y -> Affine.mapX(m, x, y) }, { x, y -> Affine.mapY(m, x, y) })
                    )
                },
                strokeWidth = stroke.strokeWidth * widthFactor,
                source = null
            )
        }
    }

    /**
     * Ray-casting algorithm to determine if point P is inside polygon.
     */
    private fun isPointInPolygon(p: Offset, polygon: List<Offset>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > p.y) != (pj.y > p.y) &&
                (p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
