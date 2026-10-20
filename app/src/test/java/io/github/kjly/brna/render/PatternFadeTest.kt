package io.github.kjly.brna.render

import io.github.kjly.brna.model.PaperPattern
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

    @Test
    fun `dots and grids fade by the closer of width and height`() {
        assertEquals(8f, PatternMetrics.fadeSpacing(PaperPattern.DOTS, 8f, 32f), 0f)
        assertEquals(8f, PatternMetrics.fadeSpacing(PaperPattern.GRID, 32f, 8f), 0f)
    }

    @Test
    fun `ruled and isometric patterns fade by the height alone`() {
        // A narrow width must not fade lines that are 32 px apart.
        for (pattern in listOf(PaperPattern.LINES, PaperPattern.ISO_GRID, PaperPattern.ISO_DOTS)) {
            assertEquals(32f, PatternMetrics.fadeSpacing(pattern, 4f, 32f), 0f)
        }
    }
}
