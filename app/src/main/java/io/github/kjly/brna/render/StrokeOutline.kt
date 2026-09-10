package io.github.kjly.brna.render

import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.StrokePoint
import kotlin.math.hypot

/**
 * Builds the filled outline of a brush stroke the way desktop Rnote does.
 *
 * Port of `compose_lines_variable_width` and the `Composer<SmoothOptions> for PenPath`
 * impl in crates/rnote-compose/src/style/smooth/mod.rs. Rnote does not stroke a path at
 * a constant width — it *fills* a polygon whose half-width at each end of each segment
 * is `pressure_curve.apply(stroke_width, pressure) * 0.5`. Reproducing that is the only
 * way a stroke can be the same size in both apps, because a constant-width stroke has no
 * width to be "correct" at: a pressure-varying stroke is a different thickness at every
 * point.
 *
 * Each segment becomes its own closed, capped quad, exactly as upstream does; they are
 * unioned by the non-zero fill rule rather than joined, so the caps double as the joins.
 * Emitting through [Sink] keeps one definition of stroke geometry shared by the canvas,
 * the PNG exporter and the SVG exporter.
 */
object StrokeOutline {

    /** Receives outline geometry as plain path commands. */
    interface Sink {
        fun moveTo(x: Float, y: Float)
        fun lineTo(x: Float, y: Float)
        fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float)
        fun close()
        /** A lone point, drawn by Rnote as a filled circle rather than as a segment. */
        fun circle(cx: Float, cy: Float, radius: Float)
    }

    fun emit(points: List<StrokePoint>, strokeWidth: Float, curve: PressureCurve, sink: Sink) {
        if (points.isEmpty()) return
        val start = points.first()
        var prev = start
        var singlePos = true

        for (i in 1 until points.size) {
            val end = points[i]
            // Upstream skips any segment ending back at the path's start position, and
            // does so without advancing `prev`. Files written by Rnote lead with exactly
            // such a segment, so dropping this check would paint a stray blob there.
            if (end.x == start.x && end.y == start.y) continue
            singlePos = false

            emitSegment(
                sink,
                prev, end,
                curve.apply(strokeWidth, prev.pressure),
                curve.apply(strokeWidth, end.pressure)
            )
            prev = end
        }

        if (singlePos) {
            val width = curve.apply(strokeWidth, start.pressure)
            if (width > 0f) sink.circle(start.x, start.y, width * 0.5f)
        }
    }

    private fun emitSegment(
        sink: Sink,
        from: StrokePoint,
        to: StrokePoint,
        startWidth: Float,
        endWidth: Float
    ) {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = hypot(dx, dy)
        // Upstream filters zero-length lines out before building any offsets.
        if (len <= 0f) return

        val dirX = dx / len
        val dirY = dy / len
        // Rnote's `orth_unit()`: the unit normal. Which side is "positive" is arbitrary —
        // the outline is symmetric about the segment either way.
        val nx = -dirY
        val ny = dirX

        val startHalf = startWidth * 0.5f
        val endHalf = endWidth * 0.5f
        val startPosX = from.x + nx * startHalf; val startPosY = from.y + ny * startHalf
        val startNegX = from.x - nx * startHalf; val startNegY = from.y - ny * startHalf
        val endPosX = to.x + nx * endHalf;       val endPosY = to.y + ny * endHalf
        val endNegX = to.x - nx * endHalf;       val endNegY = to.y - ny * endHalf

        // Cubic control points sit two-thirds of a width beyond the ends — upstream's
        // circular-arc approximation for the round caps.
        val startCap = startWidth * (2f / 3f)
        val endCap = endWidth * (2f / 3f)

        if (startWidth > 0f) {
            sink.moveTo(startNegX, startNegY)
            sink.cubicTo(
                startNegX - dirX * startCap, startNegY - dirY * startCap,
                startPosX - dirX * startCap, startPosY - dirY * startCap,
                startPosX, startPosY
            )
        } else {
            sink.moveTo(startPosX, startPosY)
        }

        sink.lineTo(endPosX, endPosY)

        if (endWidth > 0f) {
            sink.cubicTo(
                endPosX + dirX * endCap, endPosY + dirY * endCap,
                endNegX + dirX * endCap, endNegY + dirY * endCap,
                endNegX, endNegY
            )
        } else {
            sink.lineTo(endNegX, endNegY)
        }

        sink.lineTo(startNegX, startNegY)
        sink.close()
    }
}
