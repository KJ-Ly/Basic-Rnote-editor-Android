package io.github.kjly.brna.render

import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.LineShape
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.PathOp
import io.github.kjly.brna.model.PathShape
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RoughFillStyle
import io.github.kjly.brna.model.RoughStyle

/**
 * Desktop Rnote's `Composer<RoughOptions>` for its shapes (rnote-compose/src/style/rough):
 * which of roughr's shapes each shape is drawn as, with which options, and under which
 * transform. A rectangle and an ellipse are drawn about the origin and then transformed,
 * line widths and all, as Rnote draws them; everything else where it lies.
 */
internal object RoughShapes {

    /** roughr's drawables for a shape, drawn in order under [transform] (null for none). */
    class Composed(val drawables: List<RoughDrawable>, val transform: FloatArray?)

    /** `f64::to_degrees`' factor, which is also the nearest double to 180 / π. */
    private const val DEGREES_PER_RADIAN = 57.29577951308232

    /** The rough drawing of [el]; null when it is smooth or a path no rough shape makes. */
    fun compose(el: NativeShapeElement): Composed? {
        val style = el.rough ?: return null
        // Every drawable starts from the seed afresh: Rnote builds its options anew for each.
        val options = { optionsFor(el, style) }
        return when (val s = el.shape) {
            is LineShape -> Composed(
                listOf(RoughGenerator.line(s.x1.toDouble(), s.y1.toDouble(), s.x2.toDouble(), s.y2.toDouble(), options())),
                null
            )
            is RectShape -> {
                val hx = s.halfExtentX.toDouble()
                val hy = s.halfExtentY.toDouble()
                Composed(listOf(RoughGenerator.rectangle(-hx, -hy, hx * 2.0, hy * 2.0, options())), s.transform)
            }
            is EllipseShape -> Composed(
                listOf(RoughGenerator.ellipse(0.0, 0.0, s.radiusX.toDouble() * 2.0, s.radiusY.toDouble() * 2.0, options())),
                s.transform
            )
            is PathShape -> composePath(s.ops, options)?.let { Composed(it, null) }
        }
    }

    /**
     * Rnote's `generate_roughr_options`: the line width and the hachure angle in degrees,
     * both as `f32`; the seed, or roughr's own 345 without one; and a fill only for a
     * colour that isn't transparent.
     */
    fun optionsFor(el: NativeShapeElement, style: RoughStyle): RoughOptions = RoughOptions(
        strokeWidth = el.strokeWidth,
        hachureAngle = (style.hachureAngle * DEGREES_PER_RADIAN).toFloat(),
        fillStyle = fillOf(style.fillStyle),
        seed = style.seed ?: 345L,
        fill = el.fillColor.a > 0f
    )

    private fun fillOf(style: RoughFillStyle): RoughFill = when (style) {
        RoughFillStyle.SOLID -> RoughFill.SOLID
        RoughFillStyle.HACHURE -> RoughFill.HACHURE
        RoughFillStyle.ZIG_ZAG -> RoughFill.ZIG_ZAG
        RoughFillStyle.ZIG_ZAG_LINE -> RoughFill.ZIG_ZAG_LINE
        RoughFillStyle.CROSSHATCH -> RoughFill.CROSS_HATCH
        RoughFillStyle.DOTS -> RoughFill.DOTS
        RoughFillStyle.DASHED -> RoughFill.DASHED
    }

    /**
     * The shapes a [PathShape] holds, told apart by their outline: an arrow is its stem and
     * then its head from one side through the tip to the other; a curve is one Bézier; a
     * polygon's corners end in a close, a polyline's don't.
     */
    private fun composePath(ops: List<PathOp>, options: () -> RoughOptions): List<RoughDrawable>? {
        val first = ops.firstOrNull() as? PathOp.MoveTo ?: return null
        val start = RoughPoint(first.x.toDouble(), first.y.toDouble())
        val rest = ops.drop(1)
        if (rest.size == 4) {
            val tip = rest[0] as? PathOp.LineTo
            val left = rest[1] as? PathOp.MoveTo
            val back = rest[2] as? PathOp.LineTo
            val right = rest[3] as? PathOp.LineTo
            if (tip != null && left != null && back != null && right != null) {
                val t = RoughPoint(tip.x.toDouble(), tip.y.toDouble())
                return listOf(
                    RoughGenerator.line(start.x, start.y, t.x, t.y, options()),
                    RoughGenerator.linearPath(
                        listOf(RoughPoint(left.x.toDouble(), left.y.toDouble()), t, RoughPoint(right.x.toDouble(), right.y.toDouble())),
                        false,
                        options()
                    )
                )
            }
        }
        if (rest.size == 1) {
            when (val op = rest[0]) {
                is PathOp.QuadTo -> return listOf(
                    RoughGenerator.bezierQuadratic(
                        start, RoughPoint(op.x1.toDouble(), op.y1.toDouble()), RoughPoint(op.x.toDouble(), op.y.toDouble()), options()
                    )
                )
                is PathOp.CubicTo -> return listOf(
                    RoughGenerator.bezierCubic(
                        start,
                        RoughPoint(op.x1.toDouble(), op.y1.toDouble()),
                        RoughPoint(op.x2.toDouble(), op.y2.toDouble()),
                        RoughPoint(op.x.toDouble(), op.y.toDouble()),
                        options()
                    )
                )
                else -> Unit
            }
        }
        val closed = rest.lastOrNull() == PathOp.Close
        val corners = if (closed) rest.dropLast(1) else rest
        if (corners.isEmpty()) return null
        val points = ArrayList<RoughPoint>(corners.size + 1)
        points += start
        for (op in corners) {
            val corner = op as? PathOp.LineTo ?: return null
            points += RoughPoint(corner.x.toDouble(), corner.y.toDouble())
        }
        return listOf(
            if (closed) RoughGenerator.polygon(points, options()) else RoughGenerator.linearPath(points, false, options())
        )
    }

    /** The space Rnote keeps round a rough shape for its wobble: its composed bounds' margin. */
    fun margin(el: NativeShapeElement): Float =
        if (el.rough != null) el.strokeWidth / 2f + RoughStyle.BOUNDS_MARGIN else el.strokeWidth / 2f
}
