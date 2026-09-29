package io.github.kjly.brna.render

import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.model.StrokePoint

/** A brush's path builder while the pen is down: Rnote's `Buildable` for pen paths. */
internal interface PenPathBuilding {
    /** A sample while the pen is down; the points it adds to the stroke. */
    fun move(point: StrokePoint, now: Double): List<StrokePoint>

    /** The pen lifted at [point]; the stroke's last points. */
    fun up(point: StrokePoint, now: Double): List<StrokePoint>

    /** Drawn after the stroke while it is being drawn, never kept; empty once the pen is lifted. */
    val prediction: List<StrokePoint>
}

/**
 * Rnote's `PenPathCurvedBuilder`: every segment a cubic Bézier through the pen's samples,
 * its control points from the samples either side of it (Catmull–Rom, `new_w_catmull_rom`),
 * so one segment runs smoothly into the next. A segment needs the sample after it, so the
 * stroke trails the pen by one; the samples not yet made into segments are what
 * [prediction] shows.
 *
 * Ported as Rnote has it, quirks included: the first segment is a line back to the start,
 * as with Rnote's other builders, and the first curve is drawn from there with the control
 * points of the curve from the second sample on.
 */
internal class CurvedPathBuilder(start: StrokePoint) : PenPathBuilding {

    private val buffer = arrayListOf(start)

    /** The first sample not yet made into a segment's start. */
    private var i = 0

    /** Rnote's `During` state: past the first segment. */
    private var during = false

    override var prediction: List<StrokePoint> = emptyList()
        private set

    override fun move(point: StrokePoint, now: Double): List<StrokePoint> {
        buffer += point
        val out = if (!during) {
            // `try_build_segments_start`: the line back to the start, once there is a second sample.
            if (buffer.size - 1 > i) {
                during = true
                listOf(buffer[i].copy(curve = null))
            } else {
                emptyList()
            }
        } else {
            // `try_build_segments_during`: a curve for every sample with one after it.
            val segments = ArrayList<StrokePoint>()
            while (buffer.size - 1 >= i + 3) {
                segments += segment(i)
                i += 1
            }
            segments
        }
        // Rnote draws what is left of the buffer after the stroke, as it goes.
        prediction = if (during && buffer.size > i + 2) buffer.subList(i + 2, buffer.size).toList() else emptyList()
        return out
    }

    override fun up(point: StrokePoint, now: Double): List<StrokePoint> {
        buffer += point
        // `try_build_segments_end`: curves while there are samples enough, then lines.
        val last = buffer.size - 1
        val out = ArrayList<StrokePoint>()
        while (true) {
            when {
                last > i + 2 -> {
                    out += segment(i)
                    i += 1
                }
                last > i + 1 -> {
                    out += buffer[i + 1].copy(curve = null)
                    i += 2
                }
                last > i -> {
                    out += buffer[i].copy(curve = null)
                    i += 1
                }
                else -> break
            }
        }
        prediction = emptyList()
        return out
    }

    /**
     * The segment from `buffer[i + 1]` to `buffer[i + 2]`, as `CubicBezier::new_w_catmull_rom`
     * builds it with a tension of 1, or a plain line where it would have no length.
     */
    private fun segment(i: Int): StrokePoint {
        val p0 = buffer[i]
        val p1 = buffer[i + 1]
        val p2 = buffer[i + 2]
        val p3 = buffer[i + 3]
        if (p2.x == p1.x && p2.y == p1.y) return p2.copy(curve = null)
        val c1x = p1.x.toDouble() + (p2.x.toDouble() - p0.x.toDouble()) / 6.0
        val c1y = p1.y.toDouble() + (p2.y.toDouble() - p0.y.toDouble()) / 6.0
        val c2x = p2.x.toDouble() - (p3.x.toDouble() - p1.x.toDouble()) / 6.0
        val c2y = p2.y.toDouble() - (p3.y.toDouble() - p1.y.toDouble()) / 6.0
        return StrokePoint(p2.x, p2.y, p2.pressure, SegmentCurve.Cubic(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat()))
    }
}
