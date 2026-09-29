package io.github.kjly.brna.render

import io.github.kjly.brna.render.StrokeModeler.EventType
import io.github.kjly.brna.render.StrokeModeler.Input
import io.github.kjly.brna.render.StrokeModeler.Rejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The stroke modeler against ink-stroke-modeler-rs 0.1.0 itself, run with Rnote's
 * parameters on the same inputs by a Rust program built against it: the same number of
 * points, at the same places and times, with the same pressures — to the last bit.
 */
class StrokeModelerTest {

    /** A quarter circle, sampled unevenly with a pause, the pressure rising and falling. */
    private fun inputs(): List<Input> = (0 until 40).map { i ->
        val t = i.toDouble()
        val time = t * 0.004 + (if (i % 3 == 0) 0.001 else 0.0) + (if (i > 25) 0.03 else 0.0)
        val angle = t / 39.0 * Math.PI / 2
        val type = when (i) { 0 -> EventType.DOWN; 39 -> EventType.UP; else -> EventType.MOVE }
        Input(
            type, 100.0 + 80.0 * Math.cos(angle), 50.0 + 80.0 * Math.sin(angle) + (i % 2) * 0.3,
            time, 0.2 + 0.6 * Math.sin(t / 39.0 * Math.PI)
        )
    }

    private fun modeler() = StrokeModeler(ModeledPathBuilder.PARAMS)

    @Test
    fun `a stroke is modeled as the crate models it`() {
        val modeler = modeler()
        val results = ArrayList<Pair<Int, StrokeModeler.Result>>()
        inputs().forEachIndexed { i, input -> modeler.update(input).forEach { results += i to it } }
        assertEquals(52, results.size)
        // Input index, x, y, time, pressure, for points from the start, the middle, the
        // upsampled pause and the tip catching up at the end.
        val expected = listOf(
            listOf(0.0, 180.0, 50.0, 0.001, 0.2),
            listOf(1.0, 179.99828008630422, 50.09334580462477, 0.004, 0.2012798573511526),
            listOf(10.0, 178.00474669510308, 64.9067492952447, 0.04, 0.4183068356711416),
            listOf(25.0, 155.85920819979535, 107.1628286623278, 0.1, 0.7995133989029417),
            listOf(26.0, 142.20626187212414, 118.34705366313514, 0.134, 0.7370685955899167),
            listOf(39.0, 105.98590292182121, 129.62852345370604, 0.20366666666666666, 0.28931978751869003),
            listOf(39.0, 100.01582317434506, 130.37624839955186, 0.23035937499999998, 0.20010634366525737),
        ).let { rows -> listOf(0, 1, 10, 25, 30, 45, 51).zip(rows) }
        for ((at, row) in expected) {
            val (input, result) = results[at]
            assertEquals(row[0].toInt(), input)
            assertEquals(row[1], result.x, 0.0)
            assertEquals(row[2], result.y, 0.0)
            assertEquals(row[3], result.time, 0.0)
            assertEquals(row[4], result.pressure, 0.0)
        }
    }

    @Test
    fun `the prediction is the tip catching up, and moves nothing`() {
        val modeler = modeler()
        val inputs = inputs()
        for (input in inputs.take(21)) modeler.update(input)
        val expected = listOf(
            listOf(162.47973817714194, 99.47527180106715, 0.08833333333333333, 0.7830103286217288),
            listOf(159.5869665703354, 103.17906722678127, 0.09666666666666666, 0.7959480069492844),
            listOf(157.5771041865649, 105.58454351176044, 0.105, 0.7994820872662427),
            listOf(156.33151370687096, 106.97866290033626, 0.11333333333333333, 0.7995133989029417),
            listOf(155.64641185965414, 107.68307874632386, 0.12166666666666666, 0.7995133989029417),
            listOf(155.56646678377922, 107.7645687508779, 0.12270833333333332, 0.7995133989029417),
            listOf(155.52787454888664, 107.80373118518331, 0.12322916666666665, 0.7995133989029417),
        )
        repeat(2) {
            val predicted = modeler.predict()
            assertEquals(expected.size, predicted.size)
            for ((row, result) in expected.zip(predicted)) {
                assertEquals(row[0], result.x, 0.0)
                assertEquals(row[1], result.y, 0.0)
                assertEquals(row[2], result.time, 0.0)
                assertEquals(row[3], result.pressure, 0.0)
            }
        }
    }

    private fun assertRejected(rejection: Rejection, block: () -> Unit) {
        try {
            block()
            fail("expected $rejection")
        } catch (e: StrokeModeler.RejectedException) {
            assertEquals(rejection, e.rejection)
        }
    }

    @Test
    fun `inputs the crate turns away are turned away, and the model stays as it was`() {
        val modeler = modeler()
        assertRejected(Rejection.ORDER) { modeler.update(Input(EventType.MOVE, 0.0, 0.0, 0.0, 0.5)) }
        modeler.update(Input(EventType.DOWN, 0.0, 0.0, 0.0, 0.5))
        assertRejected(Rejection.ORDER) { modeler.update(Input(EventType.DOWN, 0.0, 0.0, 0.0, 0.5)) }
        val move = Input(EventType.MOVE, 1.0, 0.0, 0.01, 0.5)
        assertEquals(2, modeler.update(move).size)
        assertRejected(Rejection.DUPLICATE) { modeler.update(move) }
        assertRejected(Rejection.TOO_FAR_APART) { modeler.update(Input(EventType.MOVE, 2.0, 0.0, 2.0, 0.5)) }
        assertRejected(Rejection.NEGATIVE_TIME_DELTA) { modeler.update(Input(EventType.MOVE, 2.0, 0.0, 0.005, 0.5)) }
        // A lift where and when the pen last was still ends the stroke, the tip catching up.
        val expected = listOf(
            listOf(0.37198678625093917, 0.0, 0.018333333333333333),
            listOf(0.5967159391008128, 0.0, 0.026666666666666665),
            listOf(0.7690975217883232, 0.0, 0.034999999999999996),
            listOf(0.8852802072248067, 0.0, 0.04333333333333333),
            listOf(0.9552186935579624, 0.0, 0.05166666666666666),
            listOf(0.9923539007725506, 0.0, 0.05999999999999999),
        )
        val up = modeler.update(Input(EventType.UP, 1.0, 0.0, 0.01, 0.5))
        assertEquals(expected.size, up.size)
        for ((row, result) in expected.zip(up)) {
            assertEquals(row[0], result.x, 0.0)
            assertEquals(row[1], result.y, 0.0)
            assertEquals(row[2], result.time, 0.0)
        }
        assertTrue(modeler.predict().isEmpty())
    }
}
