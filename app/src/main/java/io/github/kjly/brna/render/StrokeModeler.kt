package io.github.kjly.brna.render

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * ink-stroke-modeler-rs 0.1.0, the stroke modeler behind Rnote's "Modeled" brush paths:
 * a port of its `StrokeModeler`, in three stages, as the crate has them.
 *
 * - Wobble smoothing: slow movement is pulled toward its moving average, which evens out
 *   the jitter of a digitiser; fast movement is left alone.
 * - Position modeling: the pen tip is a mass on a spring behind the (smoothed) input,
 *   stepped at least [Params.minOutputRate] times a second, so a stroke comes out as a
 *   smooth curve even where the input was sparse.
 * - Stylus state modeling: each modeled point takes the pressure of the nearest place on
 *   the last few raw inputs.
 *
 * It works in whatever units it is given; times are in seconds.
 */
internal class StrokeModeler(private var params: Params = Params.SUGGESTED) {

    /** `ModelerParams`. */
    data class Params(
        val wobbleTimeout: Double,
        val wobbleSpeedFloor: Double,
        val wobbleSpeedCeiling: Double,
        val springMassConstant: Double,
        val dragConstant: Double,
        val minOutputRate: Double,
        val endOfStrokeStoppingDistance: Double,
        val endOfStrokeMaxIterations: Int,
        val maxOutputsPerCall: Int,
        val stateModelerMaxInputSamples: Int
    ) {
        companion object {
            /** `ModelerParams::suggested()`. */
            val SUGGESTED = Params(
                wobbleTimeout = 0.04,
                wobbleSpeedFloor = 1.31,
                wobbleSpeedCeiling = 1.44,
                springMassConstant = 11.0 / 32400.0,
                dragConstant = 72.0,
                minOutputRate = 180.0,
                endOfStrokeStoppingDistance = 0.001,
                endOfStrokeMaxIterations = 20,
                maxOutputsPerCall = 20,
                stateModelerMaxInputSamples = 10
            )
        }
    }

    enum class EventType { DOWN, MOVE, UP }

    data class Input(val type: EventType, val x: Double, val y: Double, val time: Double, val pressure: Double)

    /** A modeled point: `ModelerResult`, less the velocity and acceleration nothing here reads. */
    data class Result(val x: Double, val y: Double, val time: Double, val pressure: Double)

    /** Why an input was turned away; the model is left as it was. */
    enum class Rejection {
        /** A move or lift with no stroke begun, or a second pen-down in one. */
        ORDER,
        /** Earlier than the input before it. */
        NEGATIVE_TIME_DELTA,
        /** The very input before it again. */
        DUPLICATE,
        /** So long after the input before that it would take more steps than one call allows. */
        TOO_FAR_APART
    }

    class RejectedException(val rejection: Rejection) : Exception(rejection.name)

    private class WobbleSample(
        val x: Double, val y: Double,
        val weightedX: Double, val weightedY: Double,
        val distance: Double,
        val duration: Double,
        val time: Double
    )

    /** `ModelerPartial`: the pen tip's state. */
    private class TipState(
        var x: Double, var y: Double,
        var vx: Double, var vy: Double,
        var ax: Double, var ay: Double,
        var time: Double
    ) {
        fun copy() = TipState(x, y, vx, vy, ax, ay, time)
    }

    private val wobble = ArrayDeque<WobbleSample>()
    private var wobbleWeightedX = 0.0
    private var wobbleWeightedY = 0.0
    private var wobbleDurationSum = 0.0
    private var wobbleDistanceSum = 0.0

    private var tip: TipState? = null
    private var lastEvent: Input? = null
    private var lastCorrectedX = 0.0
    private var lastCorrectedY = 0.0

    /** The state modeler's recent raw inputs. */
    private val recent = ArrayDeque<Input>()

    /** `reset_w_params`: any stroke in progress dropped, and [params] from now on. */
    fun reset(params: Params = this.params) {
        this.params = params
        wobble.clear()
        wobbleWeightedX = 0.0
        wobbleWeightedY = 0.0
        wobbleDurationSum = 0.0
        wobbleDistanceSum = 0.0
        tip = null
        lastEvent = null
        recent.clear()
    }

    /**
     * `update`: the modeled points [input] adds — the first as it is for a pen-down, as
     * many as the time since the last input calls for on a move, and those plus the tip
     * catching up with the pen on a lift.
     *
     * @throws RejectedException for an input the crate returns an error for.
     */
    fun update(input: Input): List<Result> {
        when (input.type) {
            EventType.DOWN -> {
                if (lastEvent != null) throw RejectedException(Rejection.ORDER)
                wobbleUpdate(input)
                tip = TipState(input.x, input.y, 0.0, 0.0, 0.0, 0.0, input.time)
                lastEvent = input
                lastCorrectedX = input.x
                lastCorrectedY = input.y
                recent.clear()
                remember(input)
                return listOf(Result(input.x, input.y, input.time, input.pressure))
            }
            EventType.MOVE, EventType.UP -> {
                val last = lastEvent ?: throw RejectedException(Rejection.ORDER)
                if (input.time - last.time < 0.0) throw RejectedException(Rejection.NEGATIVE_TIME_DELTA)
                // Every field counts, the event type too, as Rust's `PartialEq` compares them.
                if (input == last) throw RejectedException(Rejection.DUPLICATE)
                // The crate hands the input to the state modeler before it counts the steps,
                // so an input too far from the last is still remembered there.
                remember(input)
                val steps = ceil((input.time - last.time) * params.minOutputRate).toInt()
                if (steps > params.maxOutputsPerCall) throw RejectedException(Rejection.TOO_FAR_APART)
                val startX = lastCorrectedX
                val startY = lastCorrectedY
                val (endX, endY) = wobbleUpdate(input)
                val tip = tip!!
                val out = ArrayList<Result>()
                for (i in 1..steps) {
                    val frac = i.toDouble() / steps.toDouble()
                    step(tip, startX + frac * (endX - startX), startY + frac * (endY - startY), last.time + frac * (input.time - last.time))
                    out += Result(tip.x, tip.y, tip.time, query(tip.x, tip.y))
                }
                if (input.type == EventType.MOVE) {
                    lastEvent = input
                    lastCorrectedX = endX
                    lastCorrectedY = endY
                    return out
                }
                for (s in endOfStroke(tip, input.x, input.y)) out += Result(s.x, s.y, s.time, query(s.x, s.y))
                if (out.isEmpty()) {
                    // A lift at the same moment as the last move still ends the stroke with a point.
                    val time = tip.time + 1.0 / params.minOutputRate
                    out += Result(tip.x, tip.y, time, query(tip.x, tip.y))
                }
                lastEvent = null
                return out
            }
        }
    }

    /**
     * `predict`: where the tip would go if the pen stayed where it last was, without moving
     * the model; empty with no stroke in progress.
     */
    fun predict(): List<Result> {
        val last = lastEvent ?: return emptyList()
        val tip = tip ?: return emptyList()
        return endOfStroke(tip, last.x, last.y).map { Result(it.x, it.y, it.time, query(it.x, it.y)) }
    }

    // ── Wobble smoothing ──────────────────────────────────────────────────────

    private fun wobbleUpdate(event: Input): Pair<Double, Double> {
        val last = wobble.lastOrNull()
        if (last == null) {
            wobble.addLast(WobbleSample(event.x, event.y, 0.0, 0.0, 0.0, 0.0, event.time))
            return event.x to event.y
        }
        val duration = event.time - last.time
        val weightedX = event.x * duration
        val weightedY = event.y * duration
        val dx = event.x - last.x
        val dy = event.y - last.y
        val distance = sqrt(dx * dx + dy * dy)
        wobble.addLast(WobbleSample(event.x, event.y, weightedX, weightedY, distance, duration, event.time))
        wobbleWeightedX += weightedX
        wobbleWeightedY += weightedY
        wobbleDistanceSum += distance
        wobbleDurationSum += duration
        while (wobble.first().time < event.time - params.wobbleTimeout) {
            val front = wobble.removeFirst()
            wobbleWeightedX -= front.weightedX
            wobbleWeightedY -= front.weightedY
            wobbleDistanceSum -= front.distance
            wobbleDurationSum -= front.duration
        }
        if (wobbleDurationSum < 1e-12) return event.x to event.y
        val avgX = wobbleWeightedX / wobbleDurationSum
        val avgY = wobbleWeightedY / wobbleDurationSum
        val speed = wobbleDistanceSum / wobbleDurationSum
        val t = normalize01(params.wobbleSpeedFloor, params.wobbleSpeedCeiling, speed)
        return interp(avgX, event.x, t) to interp(avgY, event.y, t)
    }

    // ── Position modeling ─────────────────────────────────────────────────────

    /** `PositionModeler::update`: one step of the tip toward the anchor. */
    private fun step(tip: TipState, anchorX: Double, anchorY: Double, time: Double) {
        val dt = time - tip.time
        tip.ax = (anchorX - tip.x) / params.springMassConstant - params.dragConstant * tip.vx
        tip.ay = (anchorY - tip.y) / params.springMassConstant - params.dragConstant * tip.vy
        tip.vx += dt * tip.ax
        tip.vy += dt * tip.ay
        tip.x += dt * tip.vx
        tip.y += dt * tip.vy
        tip.time = time
    }

    /**
     * `model_end_of_stroke`: the tip catching up with the anchor, stepped until it stops
     * making headway, reaches it, or runs out of iterations — halving the step whenever it
     * would overshoot. The tip is left where it was.
     */
    private fun endOfStroke(tip: TipState, anchorX: Double, anchorY: Double): List<TipState> {
        val initial = tip.copy()
        var dt = 1.0 / params.minOutputRate
        val out = ArrayList<TipState>(params.endOfStrokeMaxIterations)
        try {
            repeat(params.endOfStrokeMaxIterations) {
                val previous = tip.copy()
                step(tip, anchorX, anchorY, previous.time + dt)
                if (dist(previous.x, previous.y, tip.x, tip.y) < params.endOfStrokeStoppingDistance) return out
                if (nearestPointOnSegment(previous.x, previous.y, tip.x, tip.y, anchorX, anchorY) < 1.0) {
                    dt *= 0.5
                    tip.set(previous)
                    return@repeat
                }
                out += tip.copy()
                if (dist(tip.x, tip.y, anchorX, anchorY) < params.endOfStrokeStoppingDistance) return out
            }
            return out
        } finally {
            tip.set(initial)
        }
    }

    private fun TipState.set(other: TipState) {
        x = other.x; y = other.y; vx = other.vx; vy = other.vy; ax = other.ax; ay = other.ay; time = other.time
    }

    // ── Stylus state modeling ─────────────────────────────────────────────────

    private fun remember(input: Input) {
        recent.addLast(input)
        if (recent.size > params.stateModelerMaxInputSamples.coerceAtLeast(1)) recent.removeFirst()
    }

    /** `StateModeler::query`: the pressure at the nearest place on the recent raw inputs. */
    private fun query(x: Double, y: Double): Double {
        when (recent.size) {
            0 -> return 1.0
            1 -> return recent.first().pressure
        }
        var distance = Double.POSITIVE_INFINITY
        var r = 0.0
        var startPressure = 1.0
        var endPressure = 1.0
        for (i in 0 until recent.size - 1) {
            val a = recent[i]
            val b = recent[i + 1]
            val rc = nearestPointOnSegment(a.x, a.y, b.x, b.y, x, y)
            val cx = a.x + rc.coerceIn(0.0, 1.0) * (b.x - a.x)
            val cy = a.y + rc.coerceIn(0.0, 1.0) * (b.y - a.y)
            val d = dist(x, y, cx, cy)
            if (d < distance) {
                distance = d
                r = rc
                startPressure = a.pressure
                endPressure = b.pressure
            }
        }
        return interp(startPressure, endPressure, r)
    }

    private companion object {
        fun normalize01(start: Double, end: Double, value: Double): Double =
            if (start == end) (if (value > start) 1.0 else 0.0)
            else ((value - start) / (end - start)).coerceIn(0.0, 1.0)

        fun interp(start: Double, end: Double, amount: Double): Double = start + (end - start) * amount.coerceIn(0.0, 1.0)

        fun dist(x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val dx = x1 - x2
            val dy = y1 - y2
            return sqrt(dx * dx + dy * dy)
        }

        /** How far along the segment from ([sx], [sy]) to ([ex], [ey]) the point nearest ([px], [py]) lies, 0 to 1. */
        fun nearestPointOnSegment(sx: Double, sy: Double, ex: Double, ey: Double, px: Double, py: Double): Double {
            if (sx == ex && sy == ey) return 0.0
            val segX = ex - sx
            val segY = ey - sy
            val projX = px - sx
            val projY = py - sy
            return ((projX * segX + projY * segY) / (segX * segX + segY * segY)).coerceIn(0.0, 1.0)
        }
    }
}
