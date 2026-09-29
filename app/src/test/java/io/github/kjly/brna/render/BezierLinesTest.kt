package io.github.kjly.brna.render

import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.model.PressureCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A curved segment is cut into the pieces rnote-compose 0.14 cuts it into — the numbers
 * are what a Rust program built against it printed for the same curves: kurbo's length,
 * `no_subsegments_for_segment_len`'s count, and `approx_with_lines`' piece ends.
 */
class BezierLinesTest {

    private fun cubic(vararg p: Double) = Triple(
        floatArrayOf(p[0].toFloat(), p[1].toFloat(), p[6].toFloat(), p[7].toFloat()),
        SegmentCurve.Cubic(p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()),
        Unit
    )

    private fun check(
        ends: FloatArray,
        curve: SegmentCurve,
        length: Double,
        count: Int,
        pieceEnds: List<Pair<Double, Double>>
    ) {
        assertEquals(length, BezierLines.length(ends[0], ends[1], ends[2], ends[3], curve), 1e-6)
        val lines = BezierLines.lines(ends[0], ends[1], ends[2], ends[3], curve)
        assertEquals(count, lines.size)
        for ((line, end) in lines.zip(pieceEnds)) {
            assertEquals(end.first, line[2].toDouble(), 1e-4)
            assertEquals(end.second, line[3].toDouble(), 1e-4)
        }
        // Each piece starts where the last one ended, the first at the segment's start.
        assertEquals(ends[0], lines.first()[0], 0f)
        for (i in 1 until lines.size) assertEquals(lines[i - 1][2], lines[i][0], 0f)
    }

    @Test
    fun `a gentle cubic is cut into three`() {
        val (ends, curve) = cubic(10.0, 10.0, 20.0, 5.0, 30.0, 15.0, 40.0, 10.0)
        check(ends, curve, 30.731306351075812, 3, listOf(20.0 to 8.88888888888889, 30.0 to 11.11111111111111, 40.0 to 10.0))
    }

    @Test
    fun `a long cubic is cut into five at most`() {
        val (ends, curve) = cubic(0.0, 0.0, 0.0, 50.0, 100.0, 50.0, 100.0, 0.0)
        check(
            ends, curve, 139.46591374330947, 5,
            listOf(10.4 to 24.0, 35.2 to 36.0, 64.8 to 36.0, 89.6 to 24.0, 100.0 to 0.0)
        )
    }

    @Test
    fun `a tiny cubic is still cut in two`() {
        val (ends, curve) = cubic(5.5, 7.25, 6.0, 7.5, 6.5, 7.0, 7.0, 7.25)
        check(ends, curve, 1.5365653175537906, 2, listOf(6.25 to 7.25, 7.0 to 7.25))
    }

    @Test
    fun `a looping cubic`() {
        val (ends, curve) = cubic(100.0, 100.0, 140.0, 60.0, 60.0, 60.0, 100.0, 100.0)
        check(
            ends, curve, 81.46774020432012, 5,
            listOf(111.52 to 80.8, 105.76 to 71.2, 94.24 to 71.2, 88.48 to 80.8, 100.0 to 100.0)
        )
    }

    @Test
    fun `quadratic curves`() {
        check(
            floatArrayOf(0f, 0f, 100f, 0f), SegmentCurve.Quad(50f, 80f), 133.37054031759345, 5,
            listOf(20.0 to 25.6, 40.0 to 38.4, 60.0 to 38.4, 80.0 to 25.6, 100.0 to 0.0)
        )
        check(
            floatArrayOf(1f, 1f, 3f, 1f), SegmentCurve.Quad(2f, 1.5f), 2.0804576388691016, 2,
            listOf(2.0 to 1.25, 3.0 to 1.0)
        )
    }

    @Test
    fun `a curved segment is outlined as one piece along its curve`() {
        val sink = RecordingSink()
        val points = listOf(
            StrokePoint(0f, 0f, 1f),
            StrokePoint(100f, 0f, 1f, SegmentCurve.Cubic(0f, 50f, 100f, 50f))
        )
        StrokeOutline.emit(points, 4f, PressureCurve.CONST, sink)
        // One closed outline, capped at both ends, not one per piece.
        assertEquals(1, sink.subPaths.size)
        assertEquals(2, sink.commands.count { it is RecordingSink.Cmd.CubicTo })
        // It follows the curve: the middle piece runs level at 36, half the width above it.
        val ys = sink.commands.mapNotNull { (it as? RecordingSink.Cmd.LineTo)?.y }
        assertEquals(36f + 2f, ys.max(), 1e-3f)
        assertTrue(ys.min() < 0f)
    }

    @Test
    fun `hit tests see a curve's pieces, a short curve as one`() {
        // Rnote's hitboxes take the count as it is: one piece for a curve under 15 long.
        assertEquals(1, BezierLines.hitboxCount(1.5))
        assertEquals(3, BezierLines.hitboxCount(30.7))
        assertEquals(5, BezierLines.hitboxCount(139.5))
        val points = listOf(
            StrokePoint(0f, 0f, 0.2f),
            StrokePoint(100f, 0f, 0.6f, SegmentCurve.Cubic(0f, 50f, 100f, 50f)),
            StrokePoint(110f, 0f, 0.6f)
        )
        val flat = BezierLines.flattened(points)
        // The curve's four inner piece ends, then its end, then the straight segment's.
        assertEquals(7, flat.size)
        assertTrue(flat.all { it.curve == null })
        assertEquals(24.0f, flat[1].y, 1e-3f)
        assertEquals(36.0f, flat[2].y, 1e-3f)
        assertEquals(0.28f, flat[1].pressure, 1e-6f)
        // Nothing curved: the very same list.
        val straight = listOf(StrokePoint(0f, 0f), StrokePoint(1f, 1f))
        assertTrue(BezierLines.flattened(straight) === straight)
    }
}
