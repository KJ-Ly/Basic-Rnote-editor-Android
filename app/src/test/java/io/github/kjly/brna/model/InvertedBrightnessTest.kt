package io.github.kjly.brna.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvertedBrightnessTest {

    private val eps = 2e-3f

    private fun inverted(r: Float, g: Float, b: Float) = InvertedBrightness.of(r, g, b)

    @Test
    fun `black and white trade places`() {
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), inverted(0f, 0f, 0f), eps)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), inverted(1f, 1f, 1f), eps)
    }

    @Test
    fun `a grey stays grey, lightness mirrored through Okhsv's toe`() {
        // Worked out by hand from Ottosson's formulas: value toe(L) = 0.5338 for this grey,
        // 1 − 0.5338 back through the toe and the sRGB curve.
        val grey = inverted(0.5f, 0.5f, 0.5f)
        assertEquals(grey[0], grey[1], eps)
        assertEquals(grey[1], grey[2], eps)
        assertEquals(0.4327f, grey[0], eps)
    }

    @Test
    fun `a dark blue turns light blue`() {
        val light = inverted(0.1f, 0.2f, 0.6f)
        assertArrayEquals(floatArrayOf(0.3971f, 0.5343f, 0.8924f), light, eps)
        assertTrue("still bluest", light[2] > light[1] && light[1] > light[0])
    }

    @Test
    fun `a colour as vivid as sRGB has stays as it is`() {
        // Neither white nor black in it, so there is nothing to swap.
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), inverted(0f, 0f, 1f), eps)
    }

    @Test
    fun `inverting twice gives the colour back`() {
        for (c in listOf(
            floatArrayOf(0.1f, 0.2f, 0.6f),
            floatArrayOf(0.2f, 0.3f, 0.1f),
            floatArrayOf(0.9f, 0.8f, 0.2f),
            floatArrayOf(0.2f, 0.5f, 0.8f),
            floatArrayOf(0.5f, 0.5f, 0.5f)
        )) {
            val once = inverted(c[0], c[1], c[2])
            assertArrayEquals(c, inverted(once[0], once[1], once[2]), eps)
        }
    }
}
