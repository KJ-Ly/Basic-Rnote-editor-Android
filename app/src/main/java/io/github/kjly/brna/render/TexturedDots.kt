package io.github.kjly.brna.render

import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.model.TexturedDistribution
import io.github.kjly.brna.model.TexturedStyle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Desktop Rnote's Textured brush (rnote-compose/src/style/textured): a port of
 * `compose_textured_line_path` and `Composer<TexturedOptions> for PenPath`.
 *
 * Every segment of the stroke is a rectangle as long as the segment and as wide as the
 * stroke is there, strewn with small tilted ellipses — as many as its area and the
 * density call for, placed along it at random and across it by the chosen distribution.
 * The randomness is Rnote's own ([Pcg64] and its samplers), seeded per segment from the
 * stroke's seed, drawn in the same order; so the dots here are the dots on the laptop.
 * All of them are filled as one path, as Rnote fills them.
 */
object TexturedDots {

    /** One dot: an ellipse about ([x], [y]) with radii [rx] and [ry], turned by [angle]. */
    data class Dot(val x: Double, val y: Double, val rx: Double, val ry: Double, val angle: Double)

    /** `TexturedOptions::DOTS_RADII_DEFAULT` and `STROKE_WIDTH_RADII_WEIGHT`. */
    private const val DOT_RADIUS_X = 1.2
    private const val DOT_RADIUS_Y = 0.3
    private const val WIDTH_RADII_WEIGHT = 0.1

    /** How far a dot turns from its segment either way: an eighth of pi. */
    private const val DOT_TURN = PI / 8.0

    /** Magic number for four cubic Béziers that make an ellipse. */
    private const val KAPPA = 0.5522847498307936

    fun dots(points: List<StrokePoint>, strokeWidth: Float, curve: PressureCurve, style: TexturedStyle): List<Dot> {
        val out = ArrayList<Dot>()
        forEachDot(points, strokeWidth, curve, style) { out.add(it) }
        return out
    }

    /** The dots of the stroke as closed paths, into [sink]. */
    fun emit(
        points: List<StrokePoint>,
        strokeWidth: Float,
        curve: PressureCurve,
        style: TexturedStyle,
        sink: StrokeOutline.Sink
    ) {
        forEachDot(points, strokeWidth, curve, style) { dot -> emitEllipse(dot, sink) }
    }

    private fun forEachDot(
        points: List<StrokePoint>,
        strokeWidth: Float,
        curve: PressureCurve,
        style: TexturedStyle,
        each: (Dot) -> Unit
    ) {
        if (points.size < 2) return
        val start = points.first()
        var prev = start
        // A stroke with no seed of its own is scattered anew by Rnote every time it is
        // drawn; here it keeps one pattern rather than shimmering on every frame.
        var seed = style.seed ?: 0L
        for (i in 1 until points.size) {
            val end = points[i]
            // As for Rnote's solid strokes: a segment back to the start is skipped, and
            // `prev` stays where it was — but the seed moves on.
            if (end.x == start.x && end.y == start.y) {
                seed = RnoteRandom.seedAdvance(seed)
                continue
            }
            val width = curve.apply(strokeWidth.toDouble(), (prev.pressure.toDouble() + end.pressure.toDouble()) * 0.5)
            segment(
                prev.x.toDouble(), prev.y.toDouble(), end.x.toDouble(), end.y.toDouble(),
                width, style.density, style.distribution, seed, each
            )
            prev = end
            seed = RnoteRandom.seedAdvance(seed)
        }
    }

    /** `compose_textured_line_path`. */
    private fun segment(
        sx: Double, sy: Double, ex: Double, ey: Double,
        width: Double,
        density: Double,
        distribution: TexturedDistribution,
        seed: Long,
        each: (Dot) -> Unit
    ) {
        val dx = ex - sx
        val dy = ey - sy
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.0) return
        val rng = Pcg64.seedFromU64(seed)

        // `Line::line_w_width_to_rect`: centred on the segment, turned along it. The angle
        // is nalgebra's `rotation_between` the x axis and the normalised segment, read
        // back off the rotation, as Rnote takes it.
        val turned = atan2(dy / length, dx / length)
        val angle = atan2(sin(turned), cos(turned))
        val c = cos(angle)
        val s = sin(angle)
        val cx = sx + dx * 0.5
        val cy = sy + dy * 0.5
        val halfX = length * 0.5
        val halfY = width * 0.5
        val area = 4.0 * halfX * halfY

        val weight = 1.0 + width * WIDTH_RADII_WEIGHT
        val radiusX = DOT_RADIUS_X * weight
        val radiusY = DOT_RADIUS_Y * weight

        val alongX = UniformDouble(-halfX, halfX)
        val turn = UniformDouble(-DOT_TURN, DOT_TURN)
        val sizeX = UniformDouble(radiusX * 0.8, radiusX * 1.25)
        val sizeY = UniformDouble(radiusY * 0.8, radiusY * 1.25)

        val count = roundHalfAway(area * 0.1 * density)
        for (n in 0 until count) {
            val x = alongX.sample(rng)
            val y = sampleAcross(distribution, rng, -halfY, halfY)
            val dotAngle = angle + turn.sample(rng)
            val rx = sizeX.sample(rng)
            val ry = sizeY.sample(rng)
            each(Dot(c * x - s * y + cx, s * x + c * y + cy, rx, ry, dotAngle))
        }
    }

    /** `TexturedDotsDistribution::sample_for_range_symmetrical_clipped`. */
    private fun sampleAcross(distribution: TexturedDistribution, rng: Pcg64, start: Double, end: Double): Double {
        val sample = when (distribution) {
            TexturedDistribution.UNIFORM -> UniformDouble(start, end).sample(rng)
            TexturedDistribution.NORMAL -> {
                val mean = (end + start) * 0.5
                val stdDev = ((end - start) * 0.5) / 3.0
                mean + stdDev * RnoteRandom.standardNormal(rng)
            }
            TexturedDistribution.EXPONENTIAL -> {
                val mid = (end + start) * 0.5
                val width = (end - start) / 4.0
                val sign = if (RnoteRandom.standardBool(rng)) 1.0 else -1.0
                mid + sign * width * RnoteRandom.exp1(rng)
            }
            TexturedDistribution.REVERSE_EXPONENTIAL -> {
                val width = (end - start) / 4.0
                val positive = RnoteRandom.standardBool(rng)
                val sign = if (positive) 1.0 else -1.0
                val offset = if (positive) start else end
                offset + (sign * width * RnoteRandom.exp1(rng))
            }
        }
        // Outside the stroke — the open-ended distributions can land there — a uniform
        // sample takes its place.
        return if (sample >= start && sample < end) sample else UniformDouble(start, end).sample(rng)
    }

    /** Rust's `f64::round`, halves away from zero, for the positive counts it is used on. */
    private fun roundHalfAway(v: Double): Int {
        val down = floor(v)
        return (if (v - down >= 0.5) down + 1.0 else down).toInt()
    }

    private fun emitEllipse(dot: Dot, sink: StrokeOutline.Sink) {
        val c = cos(dot.angle)
        val s = sin(dot.angle)
        fun x(u: Double, v: Double) = (dot.x + c * u - s * v).toFloat()
        fun y(u: Double, v: Double) = (dot.y + s * u + c * v).toFloat()
        val a = dot.rx
        val b = dot.ry
        val ka = KAPPA * a
        val kb = KAPPA * b
        sink.moveTo(x(a, 0.0), y(a, 0.0))
        sink.cubicTo(x(a, kb), y(a, kb), x(ka, b), y(ka, b), x(0.0, b), y(0.0, b))
        sink.cubicTo(x(-ka, b), y(-ka, b), x(-a, kb), y(-a, kb), x(-a, 0.0), y(-a, 0.0))
        sink.cubicTo(x(-a, -kb), y(-a, -kb), x(-ka, -b), y(-ka, -b), x(0.0, -b), y(0.0, -b))
        sink.cubicTo(x(ka, -b), y(ka, -b), x(a, -kb), y(a, -kb), x(a, 0.0), y(a, 0.0))
        sink.close()
    }
}
