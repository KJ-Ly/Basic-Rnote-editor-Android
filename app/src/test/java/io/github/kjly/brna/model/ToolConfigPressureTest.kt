package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolConfigPressureTest {

    @Test
    fun `the solid brush starts linear, as Rnote's SolidOptions do`() {
        assertEquals(PressureCurve.LINEAR, ToolConfig().strokePressureCurve)
    }

    @Test
    fun `the solid brush draws with the curve picked`() {
        for (curve in PressureCurve.entries) {
            assertEquals(curve, ToolConfig(pressureCurve = curve).strokePressureCurve)
        }
    }

    @Test
    fun `the marker keeps a constant width whatever curve was picked`() {
        val marker = ToolConfig(brushStyle = BrushStyle.MARKER, pressureCurve = PressureCurve.POW3)
        assertEquals(PressureCurve.CONST, marker.strokePressureCurve)
    }

    @Test
    fun `the textured brush draws linear, as Rnote's TexturedOptions do`() {
        val textured = ToolConfig(brushStyle = BrushStyle.TEXTURED, pressureCurve = PressureCurve.POW3)
        assertEquals(PressureCurve.LINEAR, textured.strokePressureCurve)
    }

    @Test
    fun `only the textured brush gives a stroke a texture, with the seed it is given`() {
        val textured = ToolConfig(
            brushStyle = BrushStyle.TEXTURED,
            texturedDensity = 8.5,
            texturedDistribution = TexturedDistribution.EXPONENTIAL
        )
        assertEquals(TexturedStyle(99L, 8.5, TexturedDistribution.EXPONENTIAL), textured.strokeTextured(99L))
        assertEquals(null, ToolConfig().strokeTextured(99L))
    }

    @Test
    fun `the textured brush has a width of its own, Rnote's 6 to begin with`() {
        val textured = ToolConfig(brushStyle = BrushStyle.TEXTURED)
        assertEquals(6f, textured.currentActiveSize, 0f)
        assertEquals(9f, textured.updateActiveSize(9f).texturedWidth, 0f)
        assertEquals(2f, textured.updateActiveSize(9f).strokeWidth, 0f)
    }

    @Test
    fun `the brush models its paths by default, as Rnote's does`() {
        assertEquals(PenPathBuilder.MODELED, ToolConfig().penPathBuilder)
    }
}
