package io.github.kjly.brna.render

import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.SegmentCurve
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

            val startWidth = curve.apply(strokeWidth, prev.pressure)
            val endWidth = curve.apply(strokeWidth, end.pressure)
            val bend = end.curve
            if (bend == null) {
                emitSegment(sink, prev, end, startWidth, endWidth)
            } else {
                emitCurve(sink, prev, end, bend, startWidth, endWidth)
            }
            prev = end
        }

        if (singlePos) {
            val width = curve.apply(strokeWidth, start.pressure)
            if (width > 0f) sink.circle(start.x, start.y, width * 0.5f)
        }
    }

    /**
     * A `quadbezto` or `cubbezto` segment, as Rnote composes one: the curve cut into
     * [BezierLines.count] straight pieces, the width running from [startWidth] to
     * [endWidth] along them, and the lot outlined as one piece with a cap at either end
     * (`compose_lines_variable_width`).
     */
    private fun emitCurve(
        sink: Sink,
        from: StrokePoint,
        to: StrokePoint,
        bend: SegmentCurve,
        startWidth: Float,
        endWidth: Float
    ) {
        val all = BezierLines.lines(from.x, from.y, to.x, to.y, bend)
        // Upstream drops the zero-length pieces before anything else.
        val lines = all.filter { it[2] != it[0] || it[3] != it[1] }
        val n = lines.size
        if (n == 0) return

        val pos = ArrayList<FloatArray>(2 * n)
        val neg = ArrayList<FloatArray>(2 * n)
        for ((i, line) in lines.withIndex()) {
            val lineStartWidth = startWidth + (endWidth - startWidth) * (i.toFloat() / n)
            val lineEndWidth = startWidth + (endWidth - startWidth) * ((i + 1).toFloat() / n)
            val dx = line[2] - line[0]
            val dy = line[3] - line[1]
            val len = hypot(dx, dy)
            val nx = -dy / len
            val ny = dx / len
            pos += floatArrayOf(line[0] + nx * lineStartWidth * 0.5f, line[1] + ny * lineStartWidth * 0.5f)
            neg += floatArrayOf(line[0] - nx * lineStartWidth * 0.5f, line[1] - ny * lineStartWidth * 0.5f)
            pos += floatArrayOf(line[2] + nx * lineEndWidth * 0.5f, line[3] + ny * lineEndWidth * 0.5f)
            neg += floatArrayOf(line[2] - nx * lineEndWidth * 0.5f, line[3] - ny * lineEndWidth * 0.5f)
        }
        val first = lines.first()
        val last = lines.last()
        val startLen = hypot(first[2] - first[0], first[3] - first[1])
        val endLen = hypot(last[2] - last[0], last[3] - last[1])
        val startDirX = (first[2] - first[0]) / startLen
        val startDirY = (first[3] - first[1]) / startLen
        val endDirX = (last[2] - last[0]) / endLen
        val endDirY = (last[3] - last[1]) / endLen
        val startPos = pos.first()
        val startNeg = neg.first()
        val endPos = pos.last()
        val endNeg = neg.last()

        if (startWidth > 0f && !startPos.contentEquals(startNeg)) {
            val cap = startWidth * (2f / 3f)
            sink.moveTo(startNeg[0], startNeg[1])
            sink.cubicTo(
                startNeg[0] - startDirX * cap, startNeg[1] - startDirY * cap,
                startPos[0] - startDirX * cap, startPos[1] - startDirY * cap,
                startPos[0], startPos[1]
            )
        } else {
            sink.moveTo(startPos[0], startPos[1])
        }
        for (p in pos) sink.lineTo(p[0], p[1])
        if (endWidth > 0f && !endPos.contentEquals(endNeg)) {
            val cap = endWidth * (2f / 3f)
            sink.cubicTo(
                endPos[0] + endDirX * cap, endPos[1] + endDirY * cap,
                endNeg[0] + endDirX * cap, endNeg[1] + endDirY * cap,
                endNeg[0], endNeg[1]
            )
        } else {
            sink.lineTo(endNeg[0], endNeg[1])
        }
        for (k in neg.indices.reversed()) sink.lineTo(neg[k][0], neg[k][1])
        sink.close()
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
