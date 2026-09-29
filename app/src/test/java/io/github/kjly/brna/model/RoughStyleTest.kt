package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoughStyleTest {

    @Test
    fun `fill styles are read under Rnote's names, the old capitalised one as solid`() {
        assertEquals(RoughFillStyle.CROSSHATCH, RoughFillStyle.fromApiName("crosshatch"))
        assertEquals(RoughFillStyle.ZIG_ZAG_LINE, RoughFillStyle.fromApiName("zig_zag_line"))
        assertEquals(RoughFillStyle.SOLID, RoughFillStyle.fromApiName("Hachure"))
        assertEquals(RoughFillStyle.HACHURE, RoughFillStyle.fromApiName("something new"))
    }

    @Test
    fun `the hachure angle is kept in radians, within a half turn`() {
        assertEquals(-0.715585, RoughStyle.hachureAngleOf(-41), 1e-6)
        assertEquals(Math.PI, RoughStyle.hachureAngleOf(180), 1e-12)
        assertEquals(-Math.PI, RoughStyle.hachureAngleOf(-400), 1e-12)
    }

    @Test
    fun `each further shape of one stroke gets the next seed, and no seed stays none`() {
        val first = RoughStyle(seed = 42L)
        val second = first.advanced()
        assertNotEquals(first.seed, second.seed)
        assertEquals(second, RoughStyle(seed = 42L).advanced())
        assertEquals(first.fillStyle, second.fillStyle)
        assertNull(RoughStyle(seed = null).advanced().seed)
    }

    @Test
    fun `the Shaper draws rough shapes only in the rough style`() {
        val smooth = ToolConfig(activeTool = ToolType.SHAPER)
        assertNull(smooth.shapeRough(7L))
        val rough = smooth.copy(shaperStyle = ShaperStyle.ROUGH, roughFill = RoughFillStyle.DOTS, roughHachureDegrees = 30)
        val style = rough.shapeRough(7L)!!
        assertEquals(RoughFillStyle.DOTS, style.fillStyle)
        assertEquals(Math.toRadians(30.0), style.hachureAngle, 1e-12)
        assertEquals(7L, style.seed)
    }
}
