package io.github.kjly.brna.render

import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.render.StrokeModeler.EventType
import io.github.kjly.brna.render.StrokeModeler.Rejection

/**
 * Rnote's `PenPathModeledBuilder`, its default for the brush: the pen's samples go through
 * [StrokeModeler] with Rnote's own parameters, and the stroke is made of what comes out.
 *
 * The stroke starts at the pen-down sample, as Rnote's brush starts one; the first points
 * handed back begin with that sample again, as Rnote's first segment does. Times are in
 * seconds, on any clock that only moves forward.
 */
internal class ModeledPathBuilder(start: StrokePoint, now: Double) : PenPathBuilding {

    private val modeler = StrokeModeler(PARAMS)
    private var startTime = now
    private val buffer = ArrayList<StrokePoint>()

    /**
     * Where the tip would get to if the pen stopped now: drawn after the stroke while it is
     * being drawn, never kept. Empty once the pen is lifted.
     */
    override var prediction: List<StrokePoint> = emptyList()
        private set

    init {
        restart(start, now)
    }

    /** A sample while the pen is down; the points it adds to the stroke. */
    override fun move(point: StrokePoint, now: Double): List<StrokePoint> {
        feed(point, EventType.MOVE, now)
        return drain()
    }

    /** The pen lifted at [point]; the stroke's last points, the tip catching up included. */
    override fun up(point: StrokePoint, now: Double): List<StrokePoint> {
        feed(point, EventType.UP, now)
        return drain()
    }

    private fun drain(): List<StrokePoint> {
        val out = buffer.toList()
        buffer.clear()
        return out
    }

    /** `update_modeler_w_element`, with Rnote's handling of what the modeler turns away. */
    private fun feed(point: StrokePoint, type: EventType, now: Double) {
        val input = StrokeModeler.Input(type, point.x.toDouble(), point.y.toDouble(), now - startTime, point.pressure.toDouble())
        try {
            modeler.update(input).mapTo(buffer) { it.toPoint() }
        } catch (e: StrokeModeler.RejectedException) {
            when (e.rejection) {
                // Nothing to add, and the prediction stands.
                Rejection.DUPLICATE, Rejection.NEGATIVE_TIME_DELTA -> return
                // A long pause: the model starts over from here, as Rnote's does.
                Rejection.TOO_FAR_APART -> restart(point, now)
                Rejection.ORDER -> Unit
            }
        }
        prediction = if (type == EventType.UP) emptyList() else modeler.predict().map { it.toPoint() }
    }

    private fun restart(point: StrokePoint, now: Double) {
        buffer.clear()
        prediction = emptyList()
        startTime = now
        modeler.reset(PARAMS)
        modeler.update(StrokeModeler.Input(EventType.DOWN, point.x.toDouble(), point.y.toDouble(), 0.0, point.pressure.toDouble()))
            .mapTo(buffer) { it.toPoint() }
    }

    private fun StrokeModeler.Result.toPoint() = StrokePoint(x.toFloat(), y.toFloat(), pressure.toFloat())

    companion object {
        /** Rnote's `MODELER_PARAMS`: the crate's suggestions, but sampled at 120 per second and finer at the end. */
        val PARAMS = StrokeModeler.Params.SUGGESTED.copy(
            minOutputRate = 120.0,
            endOfStrokeStoppingDistance = 0.01,
            endOfStrokeMaxIterations = 20,
            maxOutputsPerCall = 200,
            stateModelerMaxInputSamples = 20
        )
    }
}
