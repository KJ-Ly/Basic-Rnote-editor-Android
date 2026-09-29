package io.github.kjly.brna.render

import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.model.TexturedDistribution
import io.github.kjly.brna.model.TexturedStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A textured stroke's dots, against the dots rnote-compose 0.14's own
 * `compose_textured_line_path` puts down for the same stroke and seed (printed by a Rust
 * program built against it, with the pressures rounded to floats as this app keeps them).
 */
class TexturedDotsTest {

    private val stroke = listOf(
        StrokePoint(10f, 10f, 0.5f),
        StrokePoint(30f, 15f, 0.8f),
        StrokePoint(60f, 40f, 1.0f),
        StrokePoint(61f, 41f, 0.9f)
    )

    private fun dots(distribution: TexturedDistribution) =
        TexturedDots.dots(stroke, 6f, PressureCurve.LINEAR, TexturedStyle(12345L, 5.0, distribution))

    private fun assertDot(expected: DoubleArray, actual: TexturedDots.Dot) {
        val eps = 1e-9
        assertEquals(expected[0], actual.x, eps)
        assertEquals(expected[1], actual.y, eps)
        assertEquals(expected[2], actual.rx, eps)
        assertEquals(expected[3], actual.ry, eps)
        assertEquals(expected[4], actual.angle, eps)
    }

    @Test
    fun `normal distribution, Rnote's default`() {
        val dots = dots(TexturedDistribution.NORMAL)
        assertEquals(149, dots.size)
        assertDot(doubleArrayOf(14.536649260733881, 11.402523935244014, 1.7340690439856883, 0.4063732973104798, 0.0004086904835586824), dots[0])
        assertDot(doubleArrayOf(38.71848422908505, 25.707679784329528, 1.9066801807793763, 0.43829019407319847, 0.549755396944373), dots[70])
        assertDot(doubleArrayOf(60.16612451756303, 40.434953165490015, 1.7415426972508508, 0.4913362926868955, 0.4440052352703828), dots[148])
    }

    @Test
    fun `uniform distribution`() {
        val dots = dots(TexturedDistribution.UNIFORM)
        assertEquals(149, dots.size)
        assertDot(doubleArrayOf(14.44856961645934, 11.754842512342183, 1.7340690439856883, 0.4063732973104798, 0.0004086904835586824), dots[0])
        assertDot(doubleArrayOf(57.57034697992641, 39.42932008325352, 1.7670042055876543, 0.5726027206073183, 0.46606688900719123), dots[70])
    }

    @Test
    fun `exponential distribution`() {
        val dots = dots(TexturedDistribution.EXPONENTIAL)
        assertDot(doubleArrayOf(31.603372666932152, 14.939919576698287, 2.2443403948469642, 0.5481374735822329, 0.4772284741516438), dots[70])
        assertDot(doubleArrayOf(61.147178192143556, 39.405647250613974, 2.1809338170717036, 0.4447372946574434, 0.7017441164082308), dots[148])
    }

    @Test
    fun `reverse exponential distribution`() {
        val dots = dots(TexturedDistribution.REVERSE_EXPONENTIAL)
        assertDot(doubleArrayOf(14.783804738199663, 10.41390202538089, 1.625493189241919, 0.41594164018408847, 0.270477462602005), dots[0])
        assertDot(doubleArrayOf(29.87487477639067, 17.01411704534807, 2.2443403948469642, 0.5481374735822329, 0.4772284741516438), dots[70])
    }

    @Test
    fun `a stroke of one point, or of no length, has no dots`() {
        val style = TexturedStyle(1L)
        assertTrue(TexturedDots.dots(listOf(StrokePoint(5f, 5f)), 6f, PressureCurve.LINEAR, style).isEmpty())
        assertTrue(TexturedDots.dots(listOf(StrokePoint(5f, 5f), StrokePoint(5f, 5f)), 6f, PressureCurve.LINEAR, style).isEmpty())
    }

    @Test
    fun `every dot is drawn as a closed ellipse of four curves`() {
        var moves = 0
        var curves = 0
        var closes = 0
        TexturedDots.emit(stroke, 6f, PressureCurve.LINEAR, TexturedStyle(12345L), object : StrokeOutline.Sink {
            override fun moveTo(x: Float, y: Float) { moves++ }
            override fun lineTo(x: Float, y: Float) {}
            override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) { curves++ }
            override fun close() { closes++ }
            override fun circle(cx: Float, cy: Float, radius: Float) {}
        })
        assertEquals(149, moves)
        assertEquals(4 * 149, curves)
        assertEquals(149, closes)
    }
}
