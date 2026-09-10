package io.github.kjly.brna.render

import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StrokeOutline] is the single definition of stroke geometry shared by the canvas and
 * both exporters, so these assertions cover what all three of them paint.
 */
class StrokeOutlineTest {

    private val eps = 1e-4f

    private fun emit(
        points: List<StrokePoint>,
        strokeWidth: Float = 10f,
        curve: PressureCurve = PressureCurve.CONST
    ) = RecordingSink().also { StrokeOutline.emit(points, strokeWidth, curve, it) }

    @Test
    fun `an empty path emits nothing`() {
        assertTrue(emit(emptyList()).commands.isEmpty())
    }

    @Test
    fun `a horizontal segment is a quad offset by half the painted width`() {
        val sink = emit(
            listOf(StrokePoint(0f, 0f, 1f), StrokePoint(10f, 0f, 1f)),
            strokeWidth = 4f
        )
        // moveTo start-side, cap cubic, lineTo far side, cap cubic, lineTo back, close.
        val cmds = sink.commands
        assertEquals(6, cmds.size)
        val move = cmds[0] as RecordingSink.Cmd.MoveTo
        assertEquals(0f, move.x, eps)
        assertEquals(-2f, move.y, eps)          // half of the 4px width
        val across = cmds[2] as RecordingSink.Cmd.LineTo
        assertEquals(10f, across.x, eps)
        assertEquals(2f, across.y, eps)
        assertTrue(cmds[1] is RecordingSink.Cmd.CubicTo)   // round start cap
        assertTrue(cmds[3] is RecordingSink.Cmd.CubicTo)   // round end cap
        assertTrue(cmds.last() is RecordingSink.Cmd.Close)
    }

    @Test
    fun `half width at each end follows that end's pressure`() {
        // The whole point of filling a polygon rather than stroking a path: a
        // pressure-varying stroke is a different thickness at every point.
        val sink = emit(
            listOf(StrokePoint(0f, 0f, 1f), StrokePoint(10f, 0f, 0.25f)),
            strokeWidth = 8f,
            curve = PressureCurve.LINEAR
        )
        val start = sink.commands[0] as RecordingSink.Cmd.MoveTo
        val end = sink.commands[2] as RecordingSink.Cmd.LineTo
        assertEquals(-4f, start.y, eps)   // 8 * 1.0 / 2
        assertEquals(1f, end.y, eps)      // 8 * 0.25 / 2
    }

    @Test
    fun `each segment becomes its own closed quad`() {
        val sink = emit(
            listOf(StrokePoint(0f, 0f), StrokePoint(10f, 0f), StrokePoint(10f, 10f))
        )
        assertEquals(2, sink.subPaths.size)
        assertEquals(2, sink.commands.count { it is RecordingSink.Cmd.Close })
    }

    @Test
    fun `a segment ending back at the path's start is skipped without advancing`() {
        // Files written by Rnote lead with exactly such a segment; drawing it would paint
        // a stray blob at the origin, and advancing past it would misplace the next quad.
        val sink = emit(
            listOf(
                StrokePoint(0f, 0f),
                StrokePoint(10f, 0f),
                StrokePoint(0f, 0f),   // back to start — dropped
                StrokePoint(20f, 0f)
            ),
            strokeWidth = 4f
        )
        assertEquals(2, sink.subPaths.size)
        val secondQuadStart = sink.subPaths[1].first() as RecordingSink.Cmd.MoveTo
        // Continues from (10,0), not from the skipped (0,0).
        assertEquals(10f, secondQuadStart.x, eps)
    }

    @Test
    fun `zero-length segments are dropped`() {
        val sink = emit(
            listOf(StrokePoint(5f, 5f), StrokePoint(5f, 5f), StrokePoint(15f, 5f))
        )
        assertEquals(1, sink.subPaths.size)
    }

    @Test
    fun `a path that never leaves its start position is drawn as a dot`() {
        val sink = emit(
            listOf(StrokePoint(3f, 4f, 0.5f), StrokePoint(3f, 4f, 0.5f)),
            strokeWidth = 12f,
            curve = PressureCurve.LINEAR
        )
        assertEquals(1, sink.circles.size)
        val dot = sink.circles.single()
        assertEquals(3f, dot.cx, eps)
        assertEquals(4f, dot.cy, eps)
        assertEquals(3f, dot.radius, eps)   // 12 * 0.5 / 2
    }

    @Test
    fun `a dot with no painted width is not drawn at all`() {
        val sink = emit(
            listOf(StrokePoint(3f, 4f, 0f)),
            strokeWidth = 12f,
            curve = PressureCurve.LINEAR
        )
        assertTrue(sink.commands.isEmpty())
    }

    @Test
    fun `the outline is symmetric about the segment for any direction`() {
        val sink = emit(
            listOf(StrokePoint(0f, 0f, 1f), StrokePoint(6f, 8f, 1f)),
            strokeWidth = 5f
        )
        val neg = sink.commands[0] as RecordingSink.Cmd.MoveTo
        val pos = (sink.commands[1] as RecordingSink.Cmd.CubicTo)
        // Start-cap cubic lands on the opposite offset of the same start point.
        assertEquals(0f, (neg.x + pos.x) / 2f, eps)
        assertEquals(0f, (neg.y + pos.y) / 2f, eps)
        // ...and each is half a width away from the segment's start.
        assertEquals(2.5f, kotlin.math.hypot(neg.x, neg.y), eps)
    }

    @Test
    fun `svg export emits the same geometry as a path data string`() {
        val data = svgStrokePathData(
            listOf(StrokePoint(0f, 0f, 1f), StrokePoint(10f, 0f, 1f)),
            strokeWidth = 4f,
            curve = PressureCurve.CONST
        )
        assertTrue(data, data.startsWith("M0.000,-2.000"))
        assertTrue(data, data.contains("L10.000,2.000"))
        assertTrue(data, data.endsWith("Z"))
    }
}
