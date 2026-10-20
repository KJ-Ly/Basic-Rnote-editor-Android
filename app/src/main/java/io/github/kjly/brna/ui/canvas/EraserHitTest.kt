package io.github.kjly.brna.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.render.BezierLines
import java.util.UUID
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
            if (segmentHit(eraserBounds, points[i - 1], points[i], loosen)) return true
        }
        return false
    }

    /**
     * Rnote's `EraserStyle::SplitCollidingStrokes` for one stroke: the pieces left once
     * every segment the eraser square touches is cut out, or null if it touches none.
     * Empty means nothing is left. Follows `split_colliding_strokes`: a piece needs at
     * least one whole segment, the piece that begins where the stroke began keeps the
     * stroke's id, and the rest are new strokes in the same style.
     *
     * A lone dot has no segments, so Rnote's splitter never hits it and it can only be
     * trashed; here it is removed, so a dot doesn't need a change of eraser to get rid of.
     */
    fun splitStroke(eraserBounds: Rect, stroke: Stroke): List<Stroke>? {
        val points = stroke.points
        if (points.isEmpty()) return null
        val loosen = stroke.strokeWidth * 0.5f
        if (!eraserBounds.overlaps(strokeBounds(stroke, loosen))) return null
        if (points.size == 1) return if (collides(eraserBounds, stroke)) emptyList() else null

        // Segment i runs from points[i] to points[i + 1].
        val hits = (0 until points.size - 1).filter { i ->
            segmentHit(eraserBounds, points[i], points[i + 1], loosen)
        }
        if (hits.isEmpty()) return null

        val pieces = mutableListOf<Stroke>()
        val first = hits.first()
        // Each piece keeps the curves of the segments it keeps; its first point starts it
        // and brings no segment along. Cut, it is no longer what the file had.
        if (first > 0) pieces += stroke.copy(points = points.subList(0, first + 1).toList(), source = null)
        for (k in hits.indices) {
            val cut = hits[k]
            val nextCut = if (k + 1 < hits.size) hits[k + 1] else points.size - 1
            // Between two cut segments: from the first one's end to the next one's start.
            if (nextCut - cut > 1) {
                pieces += stroke.copy(
                    id = UUID.randomUUID().toString(),
                    points = points.subList(cut + 1, nextCut + 1).toList(),
                    source = null
                )
            }
        }
        return pieces
    }

    /** Whether the eraser square hits the segment [a]–[b], checked as Rnote's hitboxes. */
    private fun segmentHit(eraserBounds: Rect, a: StrokePoint, b: StrokePoint, loosen: Float): Boolean {
        // A curve is its pieces, each piece's box a hitbox, as in Rnote's `hitboxes_w_segs_indices`.
        b.curve?.let { curve ->
            return BezierLines.hitboxLines(a.x, a.y, b.x, b.y, curve).any { piece ->
                eraserBounds.overlaps(
                    Rect(
                        min(piece[0], piece[2]) - loosen, min(piece[1], piece[3]) - loosen,
                        max(piece[0], piece[2]) + loosen, max(piece[1], piece[3]) + loosen
                    )
                )
            }
        }
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
        return false
    }

    internal fun strokeBounds(stroke: Stroke, loosen: Float): Rect {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in stroke.points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
            // A curve stays within its control points, so they bound it too.
            when (val c = p.curve) {
                null -> Unit
                is SegmentCurve.Quad -> {
                    minX = min(minX, c.cx); maxX = max(maxX, c.cx)
                    minY = min(minY, c.cy); maxY = max(maxY, c.cy)
                }
                is SegmentCurve.Cubic -> {
                    minX = min(minX, min(c.c1x, c.c2x)); maxX = max(maxX, max(c.c1x, c.c2x))
                    minY = min(minY, min(c.c1y, c.c2y)); maxY = max(maxY, max(c.c1y, c.c2y))
                }
            }
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
