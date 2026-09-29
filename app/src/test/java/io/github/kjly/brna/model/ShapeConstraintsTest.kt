package io.github.kjly.brna.model

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeConstraintsTest {

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 1e-3f)
        assertEquals(expected.y, actual.y, 1e-3f)
    }

    @Test
    fun `one to one makes the shorter side as long as the longer, keeping its direction`() {
        val square = ShapeConstraints(enabled = true, ratios = setOf(ConstraintRatio.ONE_TO_ONE))
        assertOffset(Offset(10f, 10f), square.constrain(Offset(10f, 3f)))
        assertOffset(Offset(10f, -10f), square.constrain(Offset(10f, -3f)))
        assertOffset(Offset(-7f, 7f), square.constrain(Offset(-2f, 7f)))
        // Rust's signum is 1 for a zero: a level drag bent to 1:1 goes down, as in Rnote.
        assertOffset(Offset(10f, 10f), square.constrain(Offset(10f, 0f)))
    }

    @Test
    fun `three to two and the golden ratio set the shorter side from the longer`() {
        val threeTwo = ShapeConstraints(enabled = true, ratios = setOf(ConstraintRatio.THREE_TO_TWO))
        assertOffset(Offset(30f, 20f), threeTwo.constrain(Offset(30f, 5f)))
        val golden = ShapeConstraints(enabled = true, ratios = setOf(ConstraintRatio.GOLDEN))
        assertOffset(Offset(16.18f, 10f), golden.constrain(Offset(16.18f, 2f)))
    }

    @Test
    fun `the nearest ratio wins`() {
        val defaults = ShapeConstraints(enabled = true)
        // Nearly level: level.
        assertOffset(Offset(10f, 0f), defaults.constrain(Offset(10f, 1f)))
        // Nearly upright: upright.
        assertOffset(Offset(0f, -10f), defaults.constrain(Offset(1f, -10f)))
        // Nearly diagonal: square.
        assertOffset(Offset(10f, 10f), defaults.constrain(Offset(10f, 9f)))
    }

    @Test
    fun `switched off, a drag is left as it is, and Ctrl switches them for as long as it is held`() {
        val off = ShapeConstraints()
        assertFalse(off.enabled)
        assertOffset(Offset(10f, 9f), off.constrain(Offset(10f, 9f)))
        assertTrue(off.withCtrl(true).enabled)
        assertFalse(off.withCtrl(false).enabled)
        assertFalse(ShapeConstraints(enabled = true).withCtrl(true).enabled)
    }

    @Test
    fun `lines always allow level and upright, as Rnote's line builder does`() {
        val squareOnly = ShapeConstraints(enabled = true, ratios = setOf(ConstraintRatio.ONE_TO_ONE))
        assertOffset(Offset(10f, 0f), squareOnly.withAxes().constrain(Offset(10f, 1f)))
        assertEquals(ShapeConstraints.DEFAULT_RATIOS, ShapeConstraints().ratios)
    }
}
