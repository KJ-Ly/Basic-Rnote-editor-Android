package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The curve decides how wide a stroke is actually painted, so a wrong value here is not
 * a rounding difference — it is every stroke in the document coming out the wrong size.
 * The expectations are desktop Rnote's `PressureCurve::apply`.
 */
class PressureCurveTest {

    private val eps = 1e-5f

    @Test
    fun `const ignores pressure entirely`() {
        assertEquals(10f, PressureCurve.CONST.apply(10f, 0f), eps)
        assertEquals(10f, PressureCurve.CONST.apply(10f, 0.25f), eps)
        assertEquals(10f, PressureCurve.CONST.apply(10f, 1f), eps)
    }

    @Test
    fun `linear at Rnote's fallback pressure is exactly half width`() {
        // A desktop mouse stroke records PRESSURE_DEFAULT throughout. Painting it at the
        // nominal width instead of half of it is the "everything is twice as thick" bug.
        assertEquals(5f, PressureCurve.LINEAR.apply(10f, StrokePoint.PRESSURE_DEFAULT), eps)
    }

    @Test
    fun `root and power curves match upstream`() {
        assertEquals(5f, PressureCurve.SQRT.apply(10f, 0.25f), eps)
        assertEquals(5f, PressureCurve.CBRT.apply(10f, 0.125f), eps)
        assertEquals(2.5f, PressureCurve.POW2.apply(10f, 0.5f), eps)
        assertEquals(1.25f, PressureCurve.POW3.apply(10f, 0.5f), eps)
    }

    @Test
    fun `pressure outside zero to one is clamped`() {
        // Android stylus pressure is not bounded at 1.0 the way Rnote's is, so an
        // unclamped hard press would paint wider than the nominal stroke width.
        assertEquals(10f, PressureCurve.LINEAR.apply(10f, 2.5f), eps)
        assertEquals(0f, PressureCurve.LINEAR.apply(10f, -1f), eps)
    }

    @Test
    fun `api names round-trip and unknown names fall back to Rnote's default`() {
        for (curve in PressureCurve.entries) {
            assertSame(curve, PressureCurve.fromApiName(curve.apiName))
        }
        assertSame(PressureCurve.LINEAR, PressureCurve.DEFAULT)
        assertSame(PressureCurve.DEFAULT, PressureCurve.fromApiName("no_such_curve"))
        assertSame(PressureCurve.SQRT, PressureCurve.fromApiName("SQRT"))
    }
}
