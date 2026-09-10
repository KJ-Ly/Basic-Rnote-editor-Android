package io.github.kjly.brna.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.kjly.brna.model.Stroke
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Desktop Rnote's eraser collision test.
 *
 * Rnote's eraser is an axis-aligned square, not a circle: `EraserConfig::eraser_bounds`
 * is `Aabb::from_half_extents(pos, splat(width * 0.5))`. A stroke is hit when that square
 * intersects any of the stroke's hitboxes, which `BrushStroke::gen_hitboxes_int` builds
 * per *segment* — each segment split into sub-segments, each sub-segment's bounding box
 * loosened by half the nominal stroke width.
 *
 * Testing segments rather than sampled points is the part that changes how the tool feels.
 * A point-proximity test cannot erase the middle of a long straight stroke at all, because
 * a fast stylus stroke records its two endpoints and nothing between them.
 */
object EraserHitTest {

    /** Rnote's `EraserConfig::eraser_bounds`, in canvas units. */
    fun eraserBounds(center: Offset, width: Float): Rect {
        val half = width * 0.5f
        return Rect(center.x - half, center.y - half, center.x + half, center.y + half)
    }

    /** Strokes the eraser square currently overlaps, in the order they were drawn. */
    fun collidingStrokes(eraserBounds: Rect, strokes: List<Stroke>): List<Stroke> =
        strokes.filter { stroke -> collides(eraserBounds, stroke) }

    private fun collides(eraserBounds: Rect, stroke: Stroke): Boolean {
        val points = stroke.points
        if (points.isEmpty()) return false

        // Rnote's nominal stroke_width, not the pressure-adjusted painted width — see
        // gen_hitboxes_int, which uses `self.style.stroke_width()` directly.
        val loosen = stroke.strokeWidth * 0.5f

        // Cheap reject on the whole stroke first, exactly as trash_colliding_strokes does.
        if (!eraserBounds.overlaps(strokeBounds(stroke, loosen))) return false

        if (points.size == 1) {
            val p = points.first()
            // A path with no segments gets one box at the start point; upstream sizes it
            // by pressure rather than width, which only matters for a single-tap dot.
            val half = p.pressure
            return eraserBounds.overlaps(
                Rect(p.x - half, p.y - half, p.x + half, p.y + half)
            )
        }

        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val splits = subsegmentCount(hypot(b.x - a.x, b.y - a.y))
            for (s in 0 until splits) {
                val t0 = s.toFloat() / splits
                val t1 = (s + 1).toFloat() / splits
                val x0 = a.x + (b.x - a.x) * t0; val y0 = a.y + (b.y - a.y) * t0
                val x1 = a.x + (b.x - a.x) * t1; val y1 = a.y + (b.y - a.y) * t1
                val box = Rect(
                    min(x0, x1) - loosen, min(y0, y1) - loosen,
                    max(x0, x1) + loosen, max(y0, y1) + loosen
                )
                if (eraserBounds.overlaps(box)) return true
            }
        }
        return false
    }

    private fun strokeBounds(stroke: Stroke, loosen: Float): Rect {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in stroke.points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return Rect(minX - loosen, minY - loosen, maxX + loosen, maxY + loosen)
    }

    /**
     * Rnote's `no_subsegments_for_segment_len`: aim for hitboxes no longer than
     * MAX_HITBOX_DIAGONAL, but cap the count so a stroke drawn while zoomed out doesn't
     * generate an enormous number of boxes.
     */
    private fun subsegmentCount(length: Float): Int {
        val maxHitboxDiagonal = 15f
        val maxSubsegments = 5
        return if (length < maxHitboxDiagonal * maxSubsegments) {
            max(ceil(length / maxHitboxDiagonal).toInt(), 1)
        } else {
            maxSubsegments
        }
    }
}
