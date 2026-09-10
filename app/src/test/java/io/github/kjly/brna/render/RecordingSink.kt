package io.github.kjly.brna.render

/**
 * A [StrokeOutline.Sink] that records the path commands it is handed, so tests can assert
 * on the geometry itself rather than on a rendered bitmap.
 */
class RecordingSink : StrokeOutline.Sink {

    sealed class Cmd {
        data class MoveTo(val x: Float, val y: Float) : Cmd()
        data class LineTo(val x: Float, val y: Float) : Cmd()
        data class CubicTo(
            val c1x: Float, val c1y: Float,
            val c2x: Float, val c2y: Float,
            val x: Float, val y: Float
        ) : Cmd()
        object Close : Cmd()
        data class Circle(val cx: Float, val cy: Float, val radius: Float) : Cmd()
    }

    val commands = mutableListOf<Cmd>()

    /** One entry per closed sub-path, i.e. per emitted segment quad. */
    val subPaths: List<List<Cmd>>
        get() {
            val out = mutableListOf<List<Cmd>>()
            var current = mutableListOf<Cmd>()
            for (cmd in commands) {
                if (cmd is Cmd.MoveTo && current.isNotEmpty()) {
                    out.add(current)
                    current = mutableListOf()
                }
                current.add(cmd)
            }
            if (current.isNotEmpty()) out.add(current)
            return out
        }

    val circles: List<Cmd.Circle> get() = commands.filterIsInstance<Cmd.Circle>()

    override fun moveTo(x: Float, y: Float) { commands += Cmd.MoveTo(x, y) }
    override fun lineTo(x: Float, y: Float) { commands += Cmd.LineTo(x, y) }
    override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        commands += Cmd.CubicTo(c1x, c1y, c2x, c2y, x, y)
    }
    override fun close() { commands += Cmd.Close }
    override fun circle(cx: Float, cy: Float, radius: Float) {
        commands += Cmd.Circle(cx, cy, radius)
    }
}
