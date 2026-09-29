package io.github.kjly.brna.render

import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.model.StrokePoint
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The straight pieces Rnote draws a curved stroke segment with: its `approx_with_lines`,
 * cutting the curve at even steps of its parameter into as many pieces as
 * `no_subsegments_for_segment_len` gives for the curve's length — one per 15 units, at
 * least two and at most five.
 */
internal object BezierLines {

    /** `no_subsegments_for_segment_len`'s hitbox diagonal and cap, and Rnote's floor of two. */
    private const val MAX_HITBOX_DIAGONAL = 15.0
    private const val MAX_SUBSEGMENTS = 5
    private const val MIN_SUBSEGMENTS = 2

    /** How many pieces a curve [length] long is drawn with. */
    fun count(length: Double): Int = hitboxCount(length).coerceAtLeast(MIN_SUBSEGMENTS)

    /**
     * How many pieces a curve [length] long is hit-tested as: Rnote's hitboxes, which
     * take `no_subsegments_for_segment_len` as it is, one piece for a short curve.
     */
    fun hitboxCount(length: Double): Int =
        if (length < MAX_HITBOX_DIAGONAL * MAX_SUBSEGMENTS) {
            ceil(length / MAX_HITBOX_DIAGONAL).toInt().coerceAtLeast(1)
        } else {
            MAX_SUBSEGMENTS
        }

    /** The pieces the curve from ([sx], [sy]) to ([ex], [ey]) is drawn with, each `[x0, y0, x1, y1]`. */
    fun lines(sx: Float, sy: Float, ex: Float, ey: Float, curve: SegmentCurve): List<FloatArray> =
        lines(sx, sy, ex, ey, curve, count(length(sx, sy, ex, ey, curve)))

    /** The pieces the curve is hit-tested as: Rnote's hitboxes are their bounds. */
    fun hitboxLines(sx: Float, sy: Float, ex: Float, ey: Float, curve: SegmentCurve): List<FloatArray> =
        lines(sx, sy, ex, ey, curve, hitboxCount(length(sx, sy, ex, ey, curve)))

    /**
     * [points] with every curved segment replaced by the ends of its hitbox pieces, for
     * testing what a stroke covers — never for drawing or keeping. [points] itself when
     * nothing in it is curved.
     */
    fun flattened(points: List<StrokePoint>): List<StrokePoint> {
        if (points.none { it.curve != null }) return points
        val out = ArrayList<StrokePoint>(points.size * 2)
        for ((i, p) in points.withIndex()) {
            val curve = p.curve
            if (i == 0 || curve == null) {
                out += if (curve == null) p else p.copy(curve = null)
                continue
            }
            val prev = points[i - 1]
            val pieces = hitboxLines(prev.x, prev.y, p.x, p.y, curve)
            for (k in 0 until pieces.size - 1) {
                val t = (k + 1).toFloat() / pieces.size
                out += StrokePoint(pieces[k][2], pieces[k][3], prev.pressure + (p.pressure - prev.pressure) * t)
            }
            out += p.copy(curve = null)
        }
        return out
    }

    private fun lines(sx: Float, sy: Float, ex: Float, ey: Float, curve: SegmentCurve, n: Int): List<FloatArray> {
        val out = ArrayList<FloatArray>(n)
        var x0 = sx.toDouble()
        var y0 = sy.toDouble()
        for (i in 1..n) {
            val t = i.toDouble() / n
            val x1 = if (i == n) ex.toDouble() else at(sx, ex, curve, t, horizontal = true)
            val y1 = if (i == n) ey.toDouble() else at(sy, ey, curve, t, horizontal = false)
            out += floatArrayOf(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
            x0 = x1
            y0 = y1
        }
        return out
    }

    /**
     * The curve's arc length as kurbo 0.11 measures it for Rnote (`perimeter(0.25)`):
     * its `ParamCurveArclen` for `CubicBez` and `QuadBez`, ported, so a curve lands on the
     * same side of one of the count's steps as it does on the laptop.
     */
    fun length(sx: Float, sy: Float, ex: Float, ey: Float, curve: SegmentCurve): Double = when (curve) {
        is SegmentCurve.Quad -> quadArclen(
            sx.toDouble(), sy.toDouble(), curve.cx.toDouble(), curve.cy.toDouble(), ex.toDouble(), ey.toDouble()
        )
        is SegmentCurve.Cubic -> cubicArclen(
            doubleArrayOf(
                sx.toDouble(), sy.toDouble(), curve.c1x.toDouble(), curve.c1y.toDouble(),
                curve.c2x.toDouble(), curve.c2y.toDouble(), ex.toDouble(), ey.toDouble()
            ),
            ARCLEN_ACCURACY, 0
        )
    }

    /** The accuracy Rnote asks kurbo for. */
    private const val ARCLEN_ACCURACY = 0.25

    /** kurbo's `arclen_rec`: Gauss–Legendre quadrature, subdividing where its error estimate says to. */
    private fun cubicArclen(c: DoubleArray, accuracy: Double, depth: Int): Double {
        val d03x = c[6] - c[0]; val d03y = c[7] - c[1]
        val d01x = c[2] - c[0]; val d01y = c[3] - c[1]
        val d12x = c[4] - c[2]; val d12y = c[5] - c[3]
        val d23x = c[6] - c[4]; val d23y = c[7] - c[5]
        val lpLc = hypot(d01x, d01y) + hypot(d12x, d12y) + hypot(d23x, d23y) - hypot(d03x, d03y)
        val dd1x = d12x - d01x; val dd1y = d12y - d01y
        val dd2x = d23x - d12x; val dd2y = d23y - d12y
        // The first derivative at the midpoint, the second, and half the third, less the factor of 3.
        val dmX = 0.25 * (d01x + d23x) + 0.5 * d12x; val dmY = 0.25 * (d01y + d23y) + 0.5 * d12y
        val dm1X = 0.5 * (dd2x + dd1x); val dm1Y = 0.5 * (dd2y + dd1y)
        val dm2X = 0.25 * (dd2x - dd1x); val dm2Y = 0.25 * (dd2y - dd1y)

        var est = 0.0
        for (k in 0 until GL_8.size / 2) {
            val wi = GL_8[2 * k]
            val xi = GL_8[2 * k + 1]
            val dX = dmX + dm1X * xi + dm2X * (xi * xi)
            val dY = dmY + dm1Y * xi + dm2Y * (xi * xi)
            val ddX = dm1X + dm2X * (2.0 * xi)
            val ddY = dm1Y + dm2Y * (2.0 * xi)
            est += wi * ((ddX * ddX + ddY * ddY) / (dX * dX + dY * dY))
        }
        val est3 = est * est * est
        if (min(est3 * 2.5e-6, 3e-2) * lpLc < accuracy) return quadratureCore(GL_8_HALF, dmX, dmY, dm1X, dm1Y, dm2X, dm2Y)
        if (min(est3 * est3 * 1.5e-11, 9e-3) * lpLc < accuracy) return quadratureCore(GL_16_HALF, dmX, dmY, dm1X, dm1Y, dm2X, dm2Y)
        if (min(est3 * est3 * est3 * 3.5e-16, 3.5e-3) * lpLc < accuracy || depth >= 20) {
            return quadratureCore(GL_24_HALF, dmX, dmY, dm1X, dm1Y, dm2X, dm2Y)
        }
        // kurbo's `subdivide`: the halves at t = 0.5.
        val mt = 0.5
        val pmX = c[0] * (mt * mt * mt) + (c[2] * (mt * mt * 3.0) + (c[4] * (mt * 3.0) + c[6] * 0.5) * 0.5) * 0.5
        val pmY = c[1] * (mt * mt * mt) + (c[3] * (mt * mt * 3.0) + (c[5] * (mt * 3.0) + c[7] * 0.5) * 0.5) * 0.5
        val left = doubleArrayOf(
            c[0], c[1],
            0.5 * (c[0] + c[2]), 0.5 * (c[1] + c[3]),
            (c[0] + c[2] * 2.0 + c[4]) * 0.25, (c[1] + c[3] * 2.0 + c[5]) * 0.25,
            pmX, pmY
        )
        val right = doubleArrayOf(
            pmX, pmY,
            (c[2] + c[4] * 2.0 + c[6]) * 0.25, (c[3] + c[5] * 2.0 + c[7]) * 0.25,
            0.5 * (c[4] + c[6]), 0.5 * (c[5] + c[7]),
            c[6], c[7]
        )
        return cubicArclen(left, accuracy * 0.5, depth + 1) + cubicArclen(right, accuracy * 0.5, depth + 1)
    }

    /** kurbo's `arclen_quadrature_core`. */
    private fun quadratureCore(
        coeffs: DoubleArray,
        dmX: Double, dmY: Double, dm1X: Double, dm1Y: Double, dm2X: Double, dm2Y: Double
    ): Double {
        var sum = 0.0
        for (k in 0 until coeffs.size / 2) {
            val wi = coeffs[2 * k]
            val xi = coeffs[2 * k + 1]
            val dX = dmX + dm2X * (xi * xi)
            val dY = dmY + dm2Y * (xi * xi)
            val dpx = hypot(dX + dm1X * xi, dY + dm1Y * xi)
            val dmx = hypot(dX - dm1X * xi, dY - dm1Y * xi)
            sum += (sqrt(2.25) * wi) * (dpx + dmx)
        }
        return sum
    }

    /** kurbo's `QuadBez::arclen`: the closed form, or quadrature for a nearly straight curve. */
    private fun quadArclen(x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val d2x = x0 - 2.0 * x1 + x2
        val d2y = y0 - 2.0 * y1 + y2
        val a = d2x * d2x + d2y * d2y
        val d1x = x1 - x0
        val d1y = y1 - y0
        val c = d1x * d1x + d1y * d1y
        if (a < 5e-4 * c) {
            val v0 = hypot(
                -0.492943519233745 * x0 + 0.430331482911935 * x1 + 0.0626120363218102 * x2,
                -0.492943519233745 * y0 + 0.430331482911935 * y1 + 0.0626120363218102 * y2
            )
            val v1 = hypot((x2 - x0) * 0.4444444444444444, (y2 - y0) * 0.4444444444444444)
            val v2 = hypot(
                -0.0626120363218102 * x0 - 0.430331482911935 * x1 + 0.492943519233745 * x2,
                -0.0626120363218102 * y0 - 0.430331482911935 * y1 + 0.492943519233745 * y2
            )
            return v0 + v1 + v2
        }
        val b = 2.0 * (d2x * d1x + d2y * d1y)
        val sabc = sqrt(a + b + c)
        val a2 = a.pow(-0.5)
        val a32 = a2 * a2 * a2
        val c2 = 2.0 * sqrt(c)
        val baC2 = b * a2 + c2
        val v0 = 0.25 * a2 * a2 * b * (2.0 * sabc - c2) + sabc
        return if (baC2 < 1e-13) {
            v0
        } else {
            v0 + 0.25 * a32 * (4.0 * c * a - b * b) * ln(((2.0 * a + b) * a2 + 2.0 * sabc) / baC2)
        }
    }

    /** One coordinate of the curve at [t]: x from [start] and [end] x when [horizontal], else y. */
    private fun at(start: Float, end: Float, curve: SegmentCurve, t: Double, horizontal: Boolean): Double {
        val p0 = start.toDouble()
        val p3 = end.toDouble()
        val u = 1.0 - t
        return when (curve) {
            is SegmentCurve.Quad -> {
                val p1 = (if (horizontal) curve.cx else curve.cy).toDouble()
                u * u * p0 + 2.0 * u * t * p1 + t * t * p3
            }
            is SegmentCurve.Cubic -> {
                val p1 = (if (horizontal) curve.c1x else curve.c1y).toDouble()
                val p2 = (if (horizontal) curve.c2x else curve.c2y).toDouble()
                u * u * u * p0 + 3.0 * u * u * t * p1 + 3.0 * u * t * t * p2 + t * t * t * p3
            }
        }
    }

    /** kurbo's `GAUSS_LEGENDRE_COEFFS_8`: weight, abscissa, in turn. */
    private val GL_8 = doubleArrayOf(
        0.3626837833783620, -0.1834346424956498,
        0.3626837833783620, 0.1834346424956498,
        0.3137066458778873, -0.5255324099163290,
        0.3137066458778873, 0.5255324099163290,
        0.2223810344533745, -0.7966664774136267,
        0.2223810344533745, 0.7966664774136267,
        0.1012285362903763, -0.9602898564975363,
        0.1012285362903763, 0.9602898564975363
    )

    /** kurbo's `GAUSS_LEGENDRE_COEFFS_8_HALF`: weight, abscissa, in turn. */
    private val GL_8_HALF = doubleArrayOf(
        0.3626837833783620, 0.1834346424956498,
        0.3137066458778873, 0.5255324099163290,
        0.2223810344533745, 0.7966664774136267,
        0.1012285362903763, 0.9602898564975363
    )

    /** kurbo's `GAUSS_LEGENDRE_COEFFS_16_HALF`: weight, abscissa, in turn. */
    private val GL_16_HALF = doubleArrayOf(
        0.1894506104550685, 0.0950125098376374,
        0.1826034150449236, 0.2816035507792589,
        0.1691565193950025, 0.4580167776572274,
        0.1495959888165767, 0.6178762444026438,
        0.1246289712555339, 0.7554044083550030,
        0.0951585116824928, 0.8656312023878318,
        0.0622535239386479, 0.9445750230732326,
        0.0271524594117541, 0.9894009349916499
    )

    /** kurbo's `GAUSS_LEGENDRE_COEFFS_24_HALF`: weight, abscissa, in turn. */
    private val GL_24_HALF = doubleArrayOf(
        0.1279381953467522, 0.0640568928626056,
        0.1258374563468283, 0.1911188674736163,
        0.1216704729278034, 0.3150426796961634,
        0.1155056680537256, 0.4337935076260451,
        0.1074442701159656, 0.5454214713888396,
        0.0976186521041139, 0.6480936519369755,
        0.0861901615319533, 0.7401241915785544,
        0.0733464814110803, 0.8200019859739029,
        0.0592985849154368, 0.8864155270044011,
        0.0442774388174198, 0.9382745520027328,
        0.0285313886289337, 0.9747285559713095,
        0.0123412297999872, 0.9951872199970213
    )
}
