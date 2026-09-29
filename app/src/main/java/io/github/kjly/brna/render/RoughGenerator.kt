package io.github.kjly.brna.render

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * rand 0.8's `StdRng` — ChaCha with 12 rounds, rand_chacha 0.3 — seeded through
 * rand_core 0.6's `seed_from_u64`: the generator roughr 0.12 draws a rough shape's
 * wobble from. Its output is read as rand_core's `BlockRng` reads it, four blocks at a
 * time, two words to a `u64`.
 */
internal class ChaCha12Rng private constructor(
    private val input: IntArray,
    private val results: IntArray,
    private var index: Int
) {
    constructor(seed: Long) : this(seedInput(seed), IntArray(BUFFER_WORDS), BUFFER_WORDS)

    fun copy(): ChaCha12Rng = ChaCha12Rng(input.copyOf(), results.copyOf(), index)

    fun nextU64(): Long {
        val len = BUFFER_WORDS
        return when {
            index < len - 1 -> {
                val lo = results[index].toLong() and MASK
                val hi = results[index + 1].toLong()
                index += 2
                (hi shl 32) or lo
            }
            index >= len -> {
                generate()
                index = 2
                (results[1].toLong() shl 32) or (results[0].toLong() and MASK)
            }
            else -> {
                val x = results[len - 1].toLong() and MASK
                generate()
                index = 1
                (results[0].toLong() shl 32) or x
            }
        }
    }

    /** rand's standard `f64`: 53 random bits in [0, 1). */
    fun nextDouble(): Double = (nextU64() ushr 11).toDouble() * (1.0 / (1L shl 53).toDouble())

    /** rand_chacha's `refill4`: four blocks from the counter on, and the counter moved past them. */
    private fun generate() {
        val x = IntArray(16)
        for (block in 0 until BLOCKS) {
            input.copyInto(x)
            for (round in 0 until DOUBLE_ROUNDS) {
                quarter(x, 0, 4, 8, 12); quarter(x, 1, 5, 9, 13); quarter(x, 2, 6, 10, 14); quarter(x, 3, 7, 11, 15)
                quarter(x, 0, 5, 10, 15); quarter(x, 1, 6, 11, 12); quarter(x, 2, 7, 8, 13); quarter(x, 3, 4, 9, 14)
            }
            for (i in 0 until 16) results[block * 16 + i] = x[i] + input[i]
            // The 64-bit block counter, words 12 and 13.
            val counter = ((input[13].toLong() shl 32) or (input[12].toLong() and MASK)) + 1
            input[12] = counter.toInt()
            input[13] = (counter ushr 32).toInt()
        }
    }

    private companion object {
        const val BLOCKS = 4
        const val BUFFER_WORDS = 16 * BLOCKS
        const val DOUBLE_ROUNDS = 6
        const val MASK = 0xFFFFFFFFL

        fun quarter(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
            x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
            x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
            x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
            x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
        }

        /** The key from rand_core's PCG32 seed filler; constants, counter and nonce as ChaCha has them. */
        fun seedInput(seed: Long): IntArray {
            val input = IntArray(16)
            input[0] = 0x61707865; input[1] = 0x3320646e; input[2] = 0x79622d32; input[3] = 0x6b206574
            var state = seed
            for (i in 0 until 8) {
                state = state * 0x5851F42D4C957F2DL + 0xA17654E46FBE17F3uL.toLong()
                val xorShifted = (((state ushr 18) xor state) ushr 27).toInt()
                input[4 + i] = Integer.rotateRight(xorShifted, (state ushr 59).toInt())
            }
            return input
        }
    }
}

/** roughr's fill styles. */
internal enum class RoughFill { SOLID, HACHURE, ZIG_ZAG, ZIG_ZAG_LINE, CROSS_HATCH, DOTS, DASHED }

/**
 * roughr's `Options`, with the builder's defaults, and its randomizer: created from the
 * seed on the first draw, and carried along — so every step that wobbles a line takes the
 * next numbers in turn, as in roughr.
 */
internal class RoughOptions(
    var maxRandomnessOffset: Float = 2f,
    var roughness: Float = 1f,
    var bowing: Float = 2f,
    var strokeWidth: Float = 1f,
    var curveFitting: Float = 0.95f,
    var curveTightness: Float = 0f,
    var curveStepCount: Float = 9f,
    var fill: Boolean = false,
    var fillStyle: RoughFill? = null,
    var fillWeight: Float = -1f,
    var hachureAngle: Float = -41f,
    var hachureGap: Float = -1f,
    var dashOffset: Float = -1f,
    var dashGap: Float = -1f,
    var zigzagOffset: Float = -1f,
    var seed: Long = 345L,
    var disableMultiStroke: Boolean = false,
    var disableMultiStrokeFill: Boolean = false,
    var preserveVertices: Boolean = false,
    private var rng: ChaCha12Rng? = null
) {
    fun random(): Double = (rng ?: ChaCha12Rng(seed).also { rng = it }).nextDouble()

    /** Rust's `clone`, the randomizer's state included. */
    fun copy(): RoughOptions = RoughOptions(
        maxRandomnessOffset, roughness, bowing, strokeWidth, curveFitting, curveTightness, curveStepCount,
        fill, fillStyle, fillWeight, hachureAngle, hachureGap, dashOffset, dashGap, zigzagOffset, seed,
        disableMultiStroke, disableMultiStrokeFill, preserveVertices, rng?.copy()
    )
}

internal data class RoughPoint(val x: Double, val y: Double)

internal sealed class RoughOp {
    data class Move(val x: Double, val y: Double) : RoughOp()
    data class Line(val x: Double, val y: Double) : RoughOp()
    data class Curve(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x: Double, val y: Double) : RoughOp()
}

internal enum class RoughSetType { PATH, FILL_PATH, FILL_SKETCH }

internal class RoughSet(val type: RoughSetType, val ops: MutableList<RoughOp>)

/**
 * A rough shape as roughr hands it to rough_piet: its sets, the shape's name (which picks
 * the fill rule), and the widths its lines are drawn at.
 */
internal class RoughDrawable(val shape: String, val sets: List<RoughSet>, val options: RoughOptions) {
    /** The width a fill sketch's lines get: roughr's fill weight, or half the stroke's. */
    val fillWeight: Float get() = if (options.fillWeight < 0f) options.strokeWidth / 2f else options.fillWeight

    /** rough_piet fills curves, polygons and paths even-odd, everything else non-zero. */
    val evenOdd: Boolean get() = shape == "curve" || shape == "polygon" || shape == "path"
}

/**
 * roughr 0.12 (a port of rough.js): its `Generator` and `renderer`, the fillers and the
 * `points_on_curve` helpers it uses, as far as Rnote's rough shapes need them. Every
 * random draw happens where it happens there, in the same order, so a rough shape comes
 * out with the same wobble as on the laptop.
 *
 * Constants roughr writes as `_c(x)` are `f32` values widened; they are written here as
 * float literals widened the same way.
 */
internal object RoughGenerator {

    private val PI_F = Math.PI.toFloat()
    private fun c(v: Float): Double = v.toDouble()

    // ── Generator ─────────────────────────────────────────────────────────────

    fun line(x1: Double, y1: Double, x2: Double, y2: Double, options: RoughOptions): RoughDrawable {
        val o = options.copy()
        return RoughDrawable("line", listOf(RoughSet(RoughSetType.PATH, doubleLine(x1, y1, x2, y2, o, false))), options.copy())
    }

    fun rectangle(x: Double, y: Double, width: Double, height: Double, options: RoughOptions): RoughDrawable {
        val o = options.copy()
        val paths = mutableListOf<RoughSet>()
        val outline = rectangleOps(x, y, width, height, o)
        if (o.fill) {
            val points = mutableListOf(
                RoughPoint(x, y), RoughPoint(x + width, y), RoughPoint(x + width, y + height), RoughPoint(x, y + height)
            )
            paths += if (o.fillStyle == RoughFill.SOLID) solidFillPolygon(listOf(points), o)
            else patternFillPolygons(mutableListOf(points), o)
        }
        paths += outline
        return RoughDrawable("rectangle", paths, o)
    }

    fun ellipse(x: Double, y: Double, width: Double, height: Double, options: RoughOptions): RoughDrawable {
        val o = options.copy()
        val paths = mutableListOf<RoughSet>()
        val params = ellipseParams(width, height, o)
        val response = ellipseWithParams(x, y, o, params)
        if (o.fill) {
            if (o.fillStyle == RoughFill.SOLID) {
                val shape = ellipseWithParams(x, y, o, params).set
                paths += RoughSet(RoughSetType.FILL_PATH, shape.ops)
            } else {
                paths += patternFillPolygons(mutableListOf(response.estimatedPoints.toMutableList()), o)
            }
        }
        paths += response.set
        return RoughDrawable("ellipse", paths, o)
    }

    fun linearPath(points: List<RoughPoint>, close: Boolean, options: RoughOptions): RoughDrawable {
        val o = options.copy()
        return RoughDrawable("linear_path", listOf(linearPathOps(points, close, o)), o)
    }

    fun polygon(points: List<RoughPoint>, options: RoughOptions): RoughDrawable {
        val o = options.copy()
        val paths = mutableListOf<RoughSet>()
        val outline = linearPathOps(points, true, o)
        if (o.fill) {
            paths += if (o.fillStyle == RoughFill.SOLID) solidFillPolygon(listOf(points), o)
            else patternFillPolygons(mutableListOf(points.toMutableList()), o)
        }
        paths += outline
        return RoughDrawable("polygon", paths, o)
    }

    fun bezierQuadratic(start: RoughPoint, cp: RoughPoint, end: RoughPoint, options: RoughOptions): RoughDrawable {
        val o = options.copy()
        val paths = mutableListOf<RoughSet>()
        val outline = RoughSet(RoughSetType.PATH, bezierQuadraticTo(cp.x, cp.y, end.x, end.y, start, o))
        if (o.fill) {
            val cubic = quadraticToCubic(start, cp, end)
            paths += curveFill(cubic, o)
        }
        paths += outline
        return RoughDrawable("curve", paths, o)
    }

    fun bezierCubic(start: RoughPoint, cp1: RoughPoint, cp2: RoughPoint, end: RoughPoint, options: RoughOptions): RoughDrawable {
        val o = options.copy()
        val paths = mutableListOf<RoughSet>()
        val outline = RoughSet(RoughSetType.PATH, bezierTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y, start, o))
        if (o.fill) paths += curveFill(listOf(start, cp1, cp2, end), o)
        paths += outline
        return RoughDrawable("curve", paths, o)
    }

    private fun curveFill(curve: List<RoughPoint>, o: RoughOptions): RoughSet {
        val polyPoints = pointsOnBezierCurves(curve, c(10f), c(1f) + c(o.roughness) / c(2f))
        return if (o.fillStyle == RoughFill.SOLID) solidFillPolygon(listOf(polyPoints), o)
        else patternFillPolygons(mutableListOf(polyPoints.toMutableList()), o)
    }

    // ── renderer ──────────────────────────────────────────────────────────────

    private fun linearPathOps(points: List<RoughPoint>, close: Boolean, o: RoughOptions): RoughSet {
        val len = points.size
        return when {
            len > 2 -> {
                val ops = mutableListOf<RoughOp>()
                for (i in 0 until len - 1) {
                    ops += doubleLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, o, false)
                }
                if (close) ops += doubleLine(points[len - 1].x, points[len - 1].y, points[0].x, points[0].y, o, false)
                RoughSet(RoughSetType.PATH, ops)
            }
            len == 2 -> RoughSet(RoughSetType.PATH, doubleLine(points[0].x, points[0].y, points[1].x, points[1].y, o, false))
            else -> RoughSet(RoughSetType.PATH, mutableListOf())
        }
    }

    private fun rectangleOps(x: Double, y: Double, width: Double, height: Double, o: RoughOptions): RoughSet =
        linearPathOps(
            listOf(RoughPoint(x, y), RoughPoint(x + width, y), RoughPoint(x + width, y + height), RoughPoint(x, y + height)),
            true, o
        )

    private class EllipseParams(val rx: Double, val ry: Double, val increment: Double)
    private class EllipseResult(val set: RoughSet, val estimatedPoints: List<RoughPoint>)

    private fun ellipseParams(width: Double, height: Double, o: RoughOptions): EllipseParams {
        val halfW = width / c(2f)
        val halfH = height / c(2f)
        val psq = sqrt(c(PI_F) * c(2f) * sqrt((halfW * halfW + halfH * halfH) / c(2f)))
        val stepCount = ceil(max(c(o.curveStepCount), c(o.curveStepCount / sqrt(200f)) * psq))
        val increment = (c(PI_F) * c(2f)) / stepCount
        var rx = abs(width / c(2f))
        var ry = abs(height / c(2f))
        val curveFitRandomness = c(1f) - c(o.curveFitting)
        rx += offsetOpt(rx * curveFitRandomness, o)
        ry += offsetOpt(ry * curveFitRandomness, o)
        return EllipseParams(rx, ry, increment)
    }

    private fun ellipseWithParams(x: Double, y: Double, o: RoughOptions, params: EllipseParams): EllipseResult {
        val inner = offset(c(0.4f), c(1f), o)
        val overlap = params.increment * offset(c(0.1f), inner, o)
        val (all, core) = computeEllipsePoints(params.increment, x, y, params.rx, params.ry, c(1f), overlap, o)
        val ops = curveOps(all, o)
        if (!o.disableMultiStroke && o.roughness != 0f) {
            val (innerAll, _) = computeEllipsePoints(params.increment, x, y, params.rx, params.ry, c(1.5f), 0.0, o)
            ops += curveOps(innerAll, o)
        }
        return EllipseResult(RoughSet(RoughSetType.PATH, ops), core)
    }

    /** The ellipse as roughr renders it on its own: a dot of the dot filler. */
    private fun ellipseOps(x: Double, y: Double, width: Double, height: Double, o: RoughOptions): List<RoughOp> =
        ellipseWithParams(x, y, o, ellipseParams(width, height, o)).set.ops

    private fun computeEllipsePoints(
        increment: Double, cx: Double, cy: Double, rx: Double, ry: Double,
        offset: Double, overlap: Double, o: RoughOptions
    ): Pair<List<RoughPoint>, List<RoughPoint>> {
        val core = mutableListOf<RoughPoint>()
        val all = mutableListOf<RoughPoint>()
        if (o.roughness == 0f) {
            val incrementInner = increment / c(4f)
            all += RoughPoint(cx + rx * cos(-incrementInner), cy + ry * sin(-incrementInner))
            var angle = 0.0
            while (angle <= c(PI_F * 2f)) {
                val p = RoughPoint(cx + rx * cos(angle), cy + ry * sin(angle))
                core += p
                all += p
                angle += incrementInner
            }
            all += RoughPoint(cx + rx * cos(0.0), cy + ry * sin(0.0))
            all += RoughPoint(cx + rx * cos(incrementInner), cy + ry * sin(incrementInner))
        } else {
            val radOffset = offsetOpt(c(0.5f), o) - (c(PI_F) / c(2f))
            all += RoughPoint(
                offsetOpt(offset, o) + cx + c(0.9f) * rx * cos(radOffset - increment),
                offsetOpt(offset, o) + cy + c(0.9f) * ry * sin(radOffset - increment)
            )
            val endAngle = c(PI_F) * c(2f) + radOffset - c(0.01f)
            var angle = radOffset
            while (angle < endAngle) {
                val p = RoughPoint(
                    offsetOpt(offset, o) + cx + rx * cos(angle),
                    offsetOpt(offset, o) + cy + ry * sin(angle)
                )
                core += p
                all += p
                angle += increment
            }
            all += RoughPoint(
                offsetOpt(offset, o) + cx + rx * cos(radOffset + c(PI_F) * c(2f) + overlap * c(0.5f)),
                offsetOpt(offset, o) + cy + ry * sin(radOffset + c(PI_F) * c(2f) + overlap * c(0.5f))
            )
            all += RoughPoint(
                offsetOpt(offset, o) + cx + c(0.98f) * rx * cos(radOffset + overlap),
                offsetOpt(offset, o) + cy + c(0.98f) * ry * sin(radOffset + overlap)
            )
            all += RoughPoint(
                offsetOpt(offset, o) + cx + c(0.9f) * rx * cos(radOffset + overlap * c(0.5f)),
                offsetOpt(offset, o) + cy + c(0.9f) * ry * sin(radOffset + overlap * c(0.5f))
            )
        }
        return all to core
    }

    /** roughr's `_curve`, with no closing point: a Catmull-Rom spline through [points]. */
    private fun curveOps(points: List<RoughPoint>, o: RoughOptions): MutableList<RoughOp> {
        val len = points.size
        val ops = mutableListOf<RoughOp>()
        if (len > 3) {
            val s = c(1f) - c(o.curveTightness)
            ops += RoughOp.Move(points[1].x, points[1].y)
            var i = 1
            while (i + 2 < len) {
                val p = points[i]
                ops += RoughOp.Curve(
                    p.x + (s * points[i + 1].x - s * points[i - 1].x) / c(6f),
                    p.y + (s * points[i + 1].y - s * points[i - 1].y) / c(6f),
                    points[i + 1].x + (s * points[i].x - s * points[i + 2].x) / c(6f),
                    points[i + 1].y + (s * points[i].y - s * points[i + 2].y) / c(6f),
                    points[i + 1].x, points[i + 1].y
                )
                i++
            }
        } else if (len == 3) {
            ops += RoughOp.Move(points[1].x, points[1].y)
            ops += RoughOp.Curve(points[1].x, points[1].y, points[2].x, points[2].y, points[2].x, points[2].y)
        } else if (len == 2) {
            ops += doubleLine(points[0].x, points[0].y, points[1].x, points[1].y, o, false)
        }
        return ops
    }

    private fun quadraticToCubic(start: RoughPoint, cp: RoughPoint, end: RoughPoint): List<RoughPoint> {
        val twoThirds = c(2f / 3f)
        return listOf(
            start,
            RoughPoint(start.x + twoThirds * (cp.x - start.x), start.y + twoThirds * (cp.y - start.y)),
            RoughPoint(end.x + twoThirds * (cp.x - end.x), end.y + twoThirds * (cp.y - end.y)),
            end
        )
    }

    private fun bezierQuadraticTo(x1: Double, y1: Double, x: Double, y: Double, current: RoughPoint, o: RoughOptions): MutableList<RoughOp> {
        val cubic = quadraticToCubic(current, RoughPoint(x1, y1), RoughPoint(x, y))
        return bezierTo(cubic[1].x, cubic[1].y, cubic[2].x, cubic[2].y, cubic[3].x, cubic[3].y, cubic[0], o)
    }

    private fun bezierTo(
        x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double,
        current: RoughPoint, o: RoughOptions
    ): MutableList<RoughOp> {
        val ops = mutableListOf<RoughOp>()
        val ros = doubleArrayOf(c(o.maxRandomnessOffset), c(o.maxRandomnessOffset + 0.3f))
        val iterations = if (o.disableMultiStroke) 1 else 2
        for (i in 0 until iterations) {
            ops += if (i == 0) {
                RoughOp.Move(current.x, current.y)
            } else {
                RoughOp.Move(
                    current.x + if (o.preserveVertices) 0.0 else offsetOpt(ros[0], o),
                    current.y + if (o.preserveVertices) 0.0 else offsetOpt(ros[0], o)
                )
            }
            val fx: Double
            val fy: Double
            if (o.preserveVertices) {
                fx = x; fy = y
            } else {
                fx = x + offsetOpt(ros[i], o)
                fy = y + offsetOpt(ros[i], o)
            }
            ops += RoughOp.Curve(
                x1 + offsetOpt(ros[i], o), y1 + offsetOpt(ros[i], o),
                x2 + offsetOpt(ros[i], o), y2 + offsetOpt(ros[i], o),
                fx, fy
            )
        }
        return ops
    }

    private fun solidFillPolygon(polygons: List<List<RoughPoint>>, o: RoughOptions): RoughSet {
        val ops = mutableListOf<RoughOp>()
        for (polygon in polygons) {
            if (polygon.size > 2) {
                val randOffset = c(o.maxRandomnessOffset)
                polygon.forEachIndexed { i, p ->
                    val x = p.x + offsetOpt(randOffset, o)
                    val y = p.y + offsetOpt(randOffset, o)
                    ops += if (i == 0) RoughOp.Move(x, y) else RoughOp.Line(x, y)
                }
            }
        }
        return RoughSet(RoughSetType.FILL_PATH, ops)
    }

    private fun offset(min: Double, max: Double, o: RoughOptions, roughnessGain: Double = 1.0): Double =
        c(o.roughness) * roughnessGain * ((c(o.random().toFloat()) * (max - min)) + min)

    private fun offsetOpt(x: Double, o: RoughOptions, roughnessGain: Double = 1.0): Double =
        offset(-x, x, o, roughnessGain)

    /** roughr's `_line`: one wobbly stroke from the first point to the second. */
    private fun lineOps(
        x1: Double, y1: Double, x2: Double, y2: Double,
        o: RoughOptions, mover: Boolean, overlay: Boolean
    ): List<RoughOp> {
        val lengthSq = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
        val length = sqrt(lengthSq)
        val gain = when {
            length < c(200f) -> c(1f)
            length > c(500f) -> c(0.4f)
            else -> c(-0.0016668f) * length + c(1.233334f)
        }
        var offset = c(o.maxRandomnessOffset)
        if (offset * offset * c(100f) > lengthSq) offset = length / c(10f)
        val halfOffset = offset / c(2f)
        val divergePoint = c(0.2f) + c(o.random().toFloat()) * c(0.2f)
        var midDispX = c(o.bowing) * c(o.maxRandomnessOffset) * (y2 - y1) / c(200f)
        var midDispY = c(o.bowing) * c(o.maxRandomnessOffset) * (x1 - x2) / c(200f)
        midDispX = offsetOpt(midDispX, o, gain)
        midDispY = offsetOpt(midDispY, o, gain)
        val ops = mutableListOf<RoughOp>()
        val step = if (overlay) halfOffset else offset
        if (mover) {
            ops += RoughOp.Move(
                x1 + if (o.preserveVertices) 0.0 else offsetOpt(step, o, gain),
                y1 + if (o.preserveVertices) 0.0 else offsetOpt(step, o, gain)
            )
        }
        ops += RoughOp.Curve(
            midDispX + x1 + (x2 - x1) * divergePoint + offsetOpt(step, o, gain),
            midDispY + y1 + (y2 - y1) * divergePoint + offsetOpt(step, o, gain),
            midDispX + x1 + c(2f) * (x2 - x1) * divergePoint + offsetOpt(step, o, gain),
            midDispY + y1 + c(2f) * (y2 - y1) * divergePoint + offsetOpt(step, o, gain),
            x2 + if (o.preserveVertices) 0.0 else offsetOpt(step, o, gain),
            y2 + if (o.preserveVertices) 0.0 else offsetOpt(step, o, gain)
        )
        return ops
    }

    /** roughr's `_double_line`: the stroke, and a second, gentler one over it. */
    private fun doubleLine(
        x1: Double, y1: Double, x2: Double, y2: Double, o: RoughOptions, filling: Boolean
    ): MutableList<RoughOp> {
        val singleStroke = if (filling) o.disableMultiStrokeFill else o.disableMultiStroke
        val ops = lineOps(x1, y1, x2, y2, o, mover = true, overlay = false).toMutableList()
        if (!singleStroke) ops += lineOps(x1, y1, x2, y2, o, mover = true, overlay = true)
        return ops
    }

    // ── Fillers ───────────────────────────────────────────────────────────────

    private class HLine(val start: RoughPoint, val end: RoughPoint) {
        fun length(): Double {
            val dx = end.x - start.x
            val dy = end.y - start.y
            return sqrt(dx * dx + dy * dy)
        }
    }

    private fun patternFillPolygons(polygons: MutableList<MutableList<RoughPoint>>, o: RoughOptions): RoughSet {
        val ops: MutableList<RoughOp> = when (o.fillStyle) {
            RoughFill.DASHED -> dashedFill(polygons, o)
            RoughFill.DOTS -> dotFill(polygons, o)
            RoughFill.CROSS_HATCH -> {
                val first = hachureFill(polygons, o)
                o.hachureAngle += 90f
                first.also { it += hachureFill(polygons, o) }
            }
            RoughFill.ZIG_ZAG -> zigZagFill(polygons, o)
            RoughFill.ZIG_ZAG_LINE -> zigZagLineFill(polygons, o)
            else -> hachureFill(polygons, o)
        }
        return RoughSet(RoughSetType.FILL_SKETCH, ops)
    }

    private fun hachureFill(polygons: MutableList<MutableList<RoughPoint>>, o: RoughOptions): MutableList<RoughOp> {
        val ops = mutableListOf<RoughOp>()
        for (l in polygonHachureLines(polygons, o)) ops += doubleLine(l.start.x, l.start.y, l.end.x, l.end.y, o, true)
        return ops
    }

    /** roughr's `polygon_hachure_lines`, which turns the polygons — in place — to the angle and back. */
    private fun polygonHachureLines(polygons: MutableList<MutableList<RoughPoint>>, o: RoughOptions): List<HLine> {
        val angle = o.hachureAngle + 90f
        var gap = o.hachureGap
        if (gap < 0f) gap = o.strokeWidth * 4f
        gap = max(gap, 0.1f)
        if (angle != 0f) {
            for (i in polygons.indices) polygons[i] = rotatePoints(polygons[i], c(angle)).toMutableList()
        }
        var lines = straightHachureLines(polygons, c(gap))
        if (angle != 0f) {
            for (i in polygons.indices) polygons[i] = rotatePoints(polygons[i], c(-angle)).toMutableList()
            lines = lines.map { HLine(rotatePoint(it.start, c(-angle)), rotatePoint(it.end, c(-angle))) }
        }
        return lines
    }

    /** euclid's rotation about the origin, by [degrees]. */
    private fun rotatePoints(points: List<RoughPoint>, degrees: Double): List<RoughPoint> = points.map { rotatePoint(it, degrees) }

    private fun rotatePoint(p: RoughPoint, degrees: Double): RoughPoint {
        val radians = degrees * (Math.PI / 180.0)
        val s = sin(radians)
        val co = cos(radians)
        return RoughPoint(p.x * co + p.y * -s + 0.0, p.x * s + p.y * co + 0.0)
    }

    private class Edge(val ymin: Double, val ymax: Double, var x: Double, val islope: Double)

    private fun straightHachureLines(polygons: MutableList<MutableList<RoughPoint>>, gapIn: Double): List<HLine> {
        val vertexArray = mutableListOf<List<RoughPoint>>()
        for (polygon in polygons) {
            if (polygon.firstOrNull() != polygon.lastOrNull()) polygon += polygon.first()
            if (polygon.size > 2) vertexArray += polygon.toList()
        }
        val lines = mutableListOf<HLine>()
        val gap = max(gapIn, c(0.1f))
        val edges = mutableListOf<Edge>()
        for (vertices in vertexArray) {
            for (i in 0 until vertices.size - 1) {
                val p1 = vertices[i]
                val p2 = vertices[i + 1]
                if (p1.y != p2.y) {
                    val ymin = min(p1.y, p2.y)
                    edges += Edge(ymin, max(p1.y, p2.y), if (ymin == p1.y) p1.x else p2.x, (p2.x - p1.x) / (p2.y - p1.y))
                }
            }
        }
        edges.sortWith { e1, e2 ->
            when {
                e1.ymin < e2.ymin -> -1
                e1.ymin > e2.ymin -> 1
                e1.x < e2.x -> -1
                e1.x > e2.x -> 1
                e1.ymax == e2.ymax -> 0
                else -> {
                    val ordering = (e1.ymax - e2.ymax) / abs(e1.ymax - e2.ymax)
                    if (ordering > 0.0) 1 else if (ordering < 0.0) -1 else 0
                }
            }
        }
        if (edges.isEmpty()) return lines
        val active = mutableListOf<Edge>()
        var y = edges.first().ymin
        while (true) {
            if (edges.isNotEmpty()) {
                val ix = edges.indexOfFirst { it.ymin > y }
                val taken = if (ix >= 0) ix else edges.size
                repeat(taken) { active += edges.removeAt(0) }
            }
            active.removeAll { it.ymax <= y }
            active.sortWith { a, b ->
                if (a.x == b.x) 0 else if ((a.x - b.x) / abs(a.x - b.x) > 0.0) 1 else -1
            }
            if (active.size > 1) {
                var i = 0
                while (i + 1 < active.size) {
                    lines += HLine(RoughPoint(active[i].x, y), RoughPoint(active[i + 1].x, y))
                    i += 2
                }
            }
            y += gap
            for (e in active) e.x = e.x + (gap * e.islope)
            if (edges.isEmpty() && active.isEmpty()) break
        }
        return lines
    }

    private fun zigZagFill(polygons: MutableList<MutableList<RoughPoint>>, o: RoughOptions): MutableList<RoughOp> {
        var gap = c(o.hachureGap)
        if (gap < 0.0) gap = c(o.strokeWidth) * c(4f)
        gap = max(gap, c(0.1f))
        val o2 = o.copy().also { it.hachureGap = gap.toFloat() }
        val lines = polygonHachureLines(polygons, o2)
        val zigZagAngle = (c(PI_F) / c(180f)) * c(o.hachureAngle)
        val dgx = gap * c(0.5f) * cos(zigZagAngle)
        val dgy = gap * c(0.5f) * sin(zigZagAngle)
        val zigZagLines = mutableListOf<HLine>()
        for (line in lines) {
            if (line.length() > 0.0) {
                zigZagLines += HLine(RoughPoint(line.start.x - dgx, line.start.y + dgy), line.end)
                zigZagLines += HLine(RoughPoint(line.start.x + dgx, line.start.y - dgy), line.end)
            }
        }
        val ops = mutableListOf<RoughOp>()
        for (l in zigZagLines) ops += doubleLine(l.start.x, l.start.y, l.end.x, l.end.y, o, true)
        return ops
    }

    private fun zigZagLineFill(polygons: MutableList<MutableList<RoughPoint>>, o: RoughOptions): MutableList<RoughOp> {
        var gap = c(o.hachureGap)
        if (gap < 0.0) gap = c(o.strokeWidth) * c(4f)
        gap = max(gap, c(0.1f))
        var zigZagOffset = c(o.zigzagOffset)
        if (zigZagOffset < 0.0) zigZagOffset = gap
        o.hachureGap = (gap + zigZagOffset).toFloat()
        val lines = polygonHachureLines(polygons, o)
        val ops = mutableListOf<RoughOp>()
        for (line in lines) {
            val length = line.length()
            val count = length / (c(2f) * zigZagOffset)
            var p1 = line.start
            var p2 = line.end
            if (p1.x > p2.x) {
                p1 = line.end
                p2 = line.start
            }
            val alpha = atan((p2.y - p1.y) / (p2.x - p1.x))
            for (i in 0 until count.toLong()) {
                val lstart = c(i.toFloat()) * c(2f) * zigZagOffset
                val lend = c((i + 1).toFloat()) * c(2f) * zigZagOffset
                val dz = sqrt(zigZagOffset * zigZagOffset * c(2f))
                val start = RoughPoint(p1.x + lstart * cos(alpha), p1.y + lstart * sin(alpha))
                val end = RoughPoint(p1.x + lend * cos(alpha), p1.y + lend * sin(alpha))
                val middle = RoughPoint(
                    start.x + dz * cos(alpha + c(PI_F / 4f)),
                    start.y + dz * sin(alpha + c(PI_F / 4f))
                )
                ops += doubleLine(start.x, start.y, middle.x, middle.y, o, false)
                ops += doubleLine(middle.x, middle.y, end.x, end.y, o, false)
            }
        }
        return ops
    }

    private fun dotFill(polygons: MutableList<MutableList<RoughPoint>>, o: RoughOptions): MutableList<RoughOp> {
        o.hachureAngle = 0f
        val lines = polygonHachureLines(polygons, o)
        val ops = mutableListOf<RoughOp>()
        var gap = c(o.hachureGap)
        if (gap < 0.0) gap = c(o.strokeWidth) * c(4f)
        gap = max(gap, c(0.1f))
        var fillWeight = c(o.fillWeight)
        if (fillWeight < 0.0) fillWeight = c(o.strokeWidth) / c(2f)
        val ro = gap / c(4f)
        for (line in lines) {
            val length = line.length()
            val count = ceil(length / gap) - 1.0
            if (count < 0.0) continue
            val offset = length - (count * gap)
            val x = ((line.start.x + line.end.x) / c(2f)) - (gap / c(4f))
            val minY = min(line.start.y, line.end.y)
            for (i in 0 until count.toLong()) {
                val y = minY + offset + (i.toDouble() * gap)
                val cx = (x - ro) + o.random() * c(2f) * ro
                val cy = (y - ro) + o.random() * c(2f) * ro
                ops += ellipseOps(cx, cy, fillWeight, fillWeight, o)
            }
        }
        return ops
    }

    private fun dashedFill(polygons: MutableList<MutableList<RoughPoint>>, o: RoughOptions): MutableList<RoughOp> {
        val lines = polygonHachureLines(polygons, o)
        val offset = when {
            o.dashOffset >= 0f -> c(o.dashOffset)
            o.hachureGap >= 0f -> c(o.hachureGap)
            else -> c(o.strokeWidth) * c(4f)
        }
        val gap = when {
            o.dashGap >= 0f -> c(o.dashGap)
            o.hachureGap >= 0f -> c(o.hachureGap)
            else -> c(o.strokeWidth) * c(4f)
        }
        val ops = mutableListOf<RoughOp>()
        for (line in lines) {
            val length = line.length()
            val count = floor(length / (offset + gap))
            val startOffset = (length + gap - (count * (offset + gap))) / c(2f)
            var p1 = line.start
            var p2 = line.end
            if (p1.x > p2.x) {
                p1 = line.end
                p2 = line.start
            }
            val alpha = atan((p2.y - p1.y) / (p2.x - p1.x))
            for (i in 0 until count.toLong()) {
                val lstart = i.toDouble() * (offset + gap)
                val lend = lstart + offset
                val start = RoughPoint(
                    p1.x + (lstart * cos(alpha)) + (startOffset * cos(alpha)),
                    p1.y + lstart * sin(alpha) + (startOffset * sin(alpha))
                )
                val end = RoughPoint(
                    p1.x + (lend * cos(alpha)) + (startOffset * cos(alpha)),
                    p1.y + (lend * sin(alpha)) + (startOffset * sin(alpha))
                )
                ops += doubleLine(start.x, start.y, end.x, end.y, o, false)
            }
        }
        return ops
    }

    // ── points_on_curve 0.7 ───────────────────────────────────────────────────

    private fun distance(a: RoughPoint, b: RoughPoint): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    /** euclid's `lerp`. */
    private fun lerp(a: RoughPoint, b: RoughPoint, t: Double): RoughPoint {
        val oneT = 1.0 - t
        return RoughPoint(oneT * a.x + t * b.x, oneT * a.y + t * b.y)
    }

    private fun distanceToSegmentSquared(p: RoughPoint, v: RoughPoint, w: RoughPoint): Double {
        val vw = distance(v, w)
        val l2 = vw * vw
        if (l2 == 0.0) {
            val d = distance(p, v)
            return d * d
        }
        var t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
        t = max(0.0, min(1.0, t))
        val d = distance(p, lerp(v, w, t))
        return d * d
    }

    private fun flatness(points: List<RoughPoint>, offset: Int): Double {
        val p1 = points[offset]
        val p2 = points[offset + 1]
        val p3 = points[offset + 2]
        val p4 = points[offset + 3]
        var ux = 3.0 * p2.x - 2.0 * p1.x - p4.x
        ux *= ux
        var uy = 3.0 * p2.y - 2.0 * p1.y - p4.y
        uy *= uy
        var vx = 3.0 * p3.x - 2.0 * p4.x - p1.x
        vx *= vx
        var vy = 3.0 * p3.y - 2.0 * p4.y - p1.y
        vy *= vy
        if (ux < vx) ux = vx
        if (uy < vy) uy = vy
        return ux + uy
    }

    private fun pointsOnBezierWithSplitting(
        points: List<RoughPoint>, offset: Int, tolerance: Double, newPoints: MutableList<RoughPoint>
    ) {
        if (flatness(points, offset) < tolerance) {
            val p0 = points[offset]
            if (newPoints.isNotEmpty()) {
                if (distance(newPoints.last(), p0) > 1.0) newPoints += p0
            } else {
                newPoints += p0
            }
            newPoints += points[offset + 3]
        } else {
            val t = 0.5
            val p1 = points[offset]
            val p2 = points[offset + 1]
            val p3 = points[offset + 2]
            val p4 = points[offset + 3]
            val q1 = lerp(p1, p2, t)
            val q2 = lerp(p2, p3, t)
            val q3 = lerp(p3, p4, t)
            val r1 = lerp(q1, q2, t)
            val r2 = lerp(q2, q3, t)
            val red = lerp(r1, r2, t)
            pointsOnBezierWithSplitting(listOf(p1, q1, r1, red), 0, tolerance, newPoints)
            pointsOnBezierWithSplitting(listOf(red, r2, q3, p4), 0, tolerance, newPoints)
        }
    }

    private fun simplifyPoints(
        points: List<RoughPoint>, start: Int, end: Int, epsilon: Double, newPoints: MutableList<RoughPoint>
    ): List<RoughPoint> {
        val s = points[start]
        val e = points[end - 1]
        var maxDistSq = 0.0
        var maxIndex = 0
        for (i in start + 1 until end - 1) {
            val distanceSq = distanceToSegmentSquared(points[i], s, e)
            if (distanceSq > maxDistSq) {
                maxDistSq = distanceSq
                maxIndex = i
            }
        }
        if (sqrt(maxDistSq) > epsilon) {
            simplifyPoints(points, start, maxIndex + 1, epsilon, newPoints)
            simplifyPoints(points, maxIndex, end, epsilon, newPoints)
        } else {
            if (newPoints.isEmpty()) newPoints += s
            newPoints += e
        }
        return newPoints.toList()
    }

    private fun pointsOnBezierCurves(points: List<RoughPoint>, tolerance: Double, distance: Double?): List<RoughPoint> {
        val newPoints = mutableListOf<RoughPoint>()
        for (i in 0 until points.size / 3) pointsOnBezierWithSplitting(points, i * 3, tolerance, newPoints)
        if (distance != null && distance > 0.0) return simplifyPoints(newPoints, 0, newPoints.size, distance, mutableListOf())
        return newPoints
    }
}
