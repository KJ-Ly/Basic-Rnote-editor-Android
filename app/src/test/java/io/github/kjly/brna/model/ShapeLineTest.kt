package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShapeLineTest {

    @Test
    fun `the shaper starts solid with straight ends, as in Rnote`() {
        assertEquals(ShapeLine(ShapeLineStyle.SOLID, ShapeLineCap.STRAIGHT), ShapeLine())
    }

    @Test
    fun `a dotted line takes a round cap, its dots being round caps`() {
        assertEquals(ShapeLine(ShapeLineStyle.DOTTED, ShapeLineCap.ROUNDED), ShapeLine().withStyle(ShapeLineStyle.DOTTED))
    }

    @Test
    fun `a straight cap turns a dotted line solid again, as Rnote's update_line_cap does`() {
        val dotted = ShapeLine().withStyle(ShapeLineStyle.DOTTED)
        assertEquals(ShapeLine(ShapeLineStyle.SOLID, ShapeLineCap.STRAIGHT), dotted.withCap(ShapeLineCap.STRAIGHT))
    }

    @Test
    fun `dashes keep whichever cap was picked`() {
        val rounded = ShapeLine().withCap(ShapeLineCap.ROUNDED)
        assertEquals(ShapeLineCap.ROUNDED, rounded.withStyle(ShapeLineStyle.DASHED_WIDE).cap)
        assertEquals(ShapeLineCap.STRAIGHT, ShapeLine().withStyle(ShapeLineStyle.DASHED_NARROW).cap)
        assertEquals(ShapeLineStyle.DASHED_NARROW, ShapeLine(ShapeLineStyle.DASHED_NARROW).withCap(ShapeLineCap.STRAIGHT).style)
    }

    @Test
    fun `the names are Rnote's serde names`() {
        assertEquals(
            listOf("solid", "dotted", "dashed_narrow", "dashed_equidistant", "dashed_wide"),
            ShapeLineStyle.entries.map { it.apiName }
        )
        assertEquals(listOf("straight", "rounded"), ShapeLineCap.entries.map { it.apiName })
    }
}
