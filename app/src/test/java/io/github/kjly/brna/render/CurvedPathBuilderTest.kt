package io.github.kjly.brna.render

import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The curved builder against Rnote's own `PenPathCurvedBuilder`, driven by a Rust program
 * with the same samples (rounded to floats, as this app keeps them): the same segments
 * after every sample, lines and curves alike, to a float's precision.
 */
class CurvedPathBuilderTest {

    private fun sample(i: Int): StrokePoint {
        val t = i.toDouble()
        val angle = t / 11.0 * Math.PI
        return StrokePoint(
            (50.0 + 10.0 * t + 5.0 * Math.sin(angle)).toFloat(),
            (80.0 - 30.0 * Math.sin(angle)).toFloat(),
            (0.3 + 0.05 * t).toFloat()
        )
    }

    private fun line(x: Double, y: Double, p: Double) = StrokePoint(x.toFloat(), y.toFloat(), p.toFloat())

    private fun cubic(c1x: Double, c1y: Double, c2x: Double, c2y: Double, x: Double, y: Double, p: Double) =
        StrokePoint(x.toFloat(), y.toFloat(), p.toFloat(), SegmentCurve.Cubic(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat()))

    /** [samples] samples through the builder, the last one lifting the pen; what each hands back. */
    private fun drawn(samples: Int): List<List<StrokePoint>> {
        val builder = CurvedPathBuilder(sample(0))
        return (1 until samples).map { i ->
            // The sixth sample repeats the fifth, which makes no curve.
            val point = sample(if (i == 6) 5 else i)
            if (i == samples - 1) builder.up(point, 0.0) else builder.move(point, 0.0)
        }
    }

    private fun assertSegments(expected: List<List<StrokePoint>>, actual: List<List<StrokePoint>>) {
        assertEquals(expected.map { it.size }, actual.map { it.size })
        for ((e, a) in expected.flatten().zip(actual.flatten())) {
            assertEquals(e.x, a.x, 1e-4f)
            assertEquals(e.y, a.y, 1e-4f)
            assertEquals(e.pressure, a.pressure, 1e-6f)
            assertEquals(e.curve == null, a.curve == null)
            val ec = e.curve as? SegmentCurve.Cubic ?: continue
            val ac = a.curve as SegmentCurve.Cubic
            assertEquals(ec.c1x, ac.c1x, 1e-4f)
            assertEquals(ec.c1y, ac.c1y, 1e-4f)
            assertEquals(ec.c2x, ac.c2x, 1e-4f)
            assertEquals(ec.c2y, ac.c2y, 1e-4f)
        }
    }

    @Test
    fun `a stroke comes out as Rnote's builder puts it together`() {
        assertSegments(listOf(
            listOf(line(50.0, 80.0, 0.30000001192092896)),
            emptyList(),
            listOf(cubic(65.1925277709961, 68.84482320149739, 68.974853515625, 66.15086237589519, 72.70320129394531, 63.78077697753906, 0.4000000059604645)),
            listOf(cubic(76.43154907226563, 61.410691579182945, 80.13792165120442, 59.17247072855631, 83.77874755859375, 57.3275146484375, 0.44999998807907104)),
            listOf(cubic(87.41957346598308, 55.48255856831869, 91.0197639465332, 53.88140042622884, 94.54815673828125, 52.71104049682617, 0.5)),
            listOf(cubic(98.0765495300293, 51.5406805674235, 103.2156130472819, 50.706302642822266, 104.94910430908203, 50.305355072021484, 0.550000011920929)),
            listOf(line(104.94910430908203, 50.305355072021484, 0.550000011920929)),
            listOf(cubic(108.2156130472819, 50.706302642822266, 119.74321619669597, 51.5406805674235, 124.54815673828125, 52.71104049682617, 0.6499999761581421)),
            listOf(cubic(129.35309727986655, 53.88140042622884, 130.7529067993164, 55.48255856831869, 133.77874755859375, 57.3275146484375, 0.699999988079071)),
            listOf(cubic(136.8045883178711, 59.17247072855631, 139.76488240559897, 61.410691579182945, 142.7032012939453, 63.78077697753906, 0.75)),
            listOf(cubic(145.64152018229166, 66.15086237589519, 148.52586110432944, 68.84482320149739, 151.40866088867188, 71.54802703857422, 0.800000011920929), line(151.40866088867188, 71.54802703857422, 0.800000011920929)),
        ), drawn(12))
    }

    @Test
    fun `short strokes end in lines, as Rnote ends them`() {
        assertSegments(listOf(
            listOf(line(50.0, 80.0, 0.30000001192092896)),
        ), drawn(2))
        assertSegments(listOf(
            listOf(line(50.0, 80.0, 0.30000001192092896)),
            listOf(line(61.408660888671875, 71.54802703857422, 0.3499999940395355)),
        ), drawn(3))
    }

    @Test
    fun `the samples not yet made into curves are shown ahead of the stroke`() {
        val builder = CurvedPathBuilder(sample(0))
        for (i in 1..4) builder.move(sample(i), 0.0)
        // The last curve ended at the third sample; the fourth waits for the fifth.
        assertEquals(listOf(sample(4)), builder.prediction)
        builder.up(sample(5), 0.0)
        assertTrue(builder.prediction.isEmpty())
    }
}
