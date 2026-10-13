package io.github.kjly.brna.render

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternFadeTest {

    @Test
    fun `a pattern with room to spare is drawn fully`() {
        assertEquals(1f, PatternMetrics.fadeAlpha(10f), 0f)
        assertEquals(1f, PatternMetrics.fadeAlpha(40f), 0f)
    }

    @Test
    fun `a pattern at or below 6 px is not drawn`() {
        assertEquals(0f, PatternMetrics.fadeAlpha(6f), 0f)
        assertEquals(0f, PatternMetrics.fadeAlpha(2f), 0f)
    }

    @Test
    fun `between 6 and 10 px it fades evenly`() {
        assertEquals(0.25f, PatternMetrics.fadeAlpha(7f), 1e-6f)
        assertEquals(0.5f, PatternMetrics.fadeAlpha(8f), 1e-6f)
    }
}
