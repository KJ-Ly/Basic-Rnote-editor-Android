package io.github.kjly.brna.render

import io.github.kjly.brna.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The modeled builder against Rnote's own `PenPathModeledBuilder`, driven by a Rust
 * program with the same samples at the same times (rounded to floats, as this app keeps
 * them): the same points handed back after every sample, to a float's precision.
 */
class ModeledPathBuilderTest {

    private fun sample(i: Int): StrokePoint {
        val t = i.toDouble()
        val angle = t / 39.0 * Math.PI / 2
        return StrokePoint(
            (100.0 + 80.0 * Math.cos(angle)).toFloat(),
            (50.0 + 80.0 * Math.sin(angle) + (i % 2) * 0.3).toFloat(),
            (0.2 + 0.6 * Math.sin(t / 39.0 * Math.PI)).toFloat()
        )
    }

    /** Uneven, with a short pause after the 25th sample and a long one after the 30th. */
    private fun seconds(i: Int): Double {
        val base = i * 4L + if (i % 3 == 0) 1 else 0
        return (if (i > 30) base + 2000 else if (i > 25) base + 30 else base) / 1000.0
    }

    private fun assertPoints(expected: List<List<Double>>, actual: List<StrokePoint>) {
        assertEquals(expected.size, actual.size)
        for ((row, point) in expected.zip(actual)) {
            assertEquals(row[0], point.x.toDouble(), 1e-4)
            assertEquals(row[1], point.y.toDouble(), 1e-4)
            assertEquals(row[2], point.pressure.toDouble(), 1e-4)
        }
    }

    @Test
    fun `a stroke comes out as Rnote's builder puts it together`() {
        val builder = ModeledPathBuilder(sample(0), 0.0)
        val handed = (1 until 40).map { i ->
            // The 17th sample repeats the 16th's place, a moment later.
            val point = sample(if (i == 17) 16 else i)
            if (i == 39) builder.up(point, seconds(i)) else builder.move(point, seconds(i))
        }
        // How many points each sample adds: the first repeats the pen-down sample, as
        // Rnote's first segment does; the pause is filled in; the lift brings the tip home.
        assertEquals(listOf(2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 5, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 7), handed.map { it.size })
        assertPoints(listOf(
            listOf(180.0, 50.0, 0.20000000298023224),
            listOf(179.9969423650568, 50.16594806685014, 0.20227530493086035),
        ), handed.first())
        // After the long pause the model starts over, from the sample itself.
        assertPoints(listOf(
            listOf(125.33344268798828, 126.18291473388672, 0.5604453682899475),
        ), handed[30])
        assertPoints(listOf(
            listOf(115.21829372314451, 128.25623299573496, 0.42527507937691283),
            listOf(110.16388426503148, 128.9952750192985, 0.35144426726206923),
            listOf(106.06314415484802, 129.5577680171815, 0.29043602100606897),
            listOf(103.18265953364663, 129.93458601886167, 0.24770940330216484),
            listOf(101.3794671441929, 130.16005761262613, 0.22064788756214818),
            listOf(100.37602645437195, 130.27887145359068, 0.2055998503289314),
            listOf(100.00559358742696, 130.32153689101636, 0.2000466814669742),
        ), handed.last())
        assertTrue(builder.prediction.isEmpty())
    }

    @Test
    fun `while the pen is down the tip's catching up is predicted, and never kept`() {
        val builder = ModeledPathBuilder(sample(0), 0.0)
        for (i in 1..10) builder.move(sample(i), seconds(i))
        val prediction = builder.prediction
        assertTrue(prediction.isNotEmpty())
        // It heads for where the pen is.
        val last = prediction.last()
        val pen = sample(10)
        assertEquals(pen.x, last.x, 1f)
        assertEquals(pen.y, last.y, 1f)
        // A sample at the same moment and place as the last adds nothing and changes nothing.
        assertTrue(builder.move(sample(10), seconds(10)).isEmpty())
        assertEquals(prediction, builder.prediction)
    }
}
