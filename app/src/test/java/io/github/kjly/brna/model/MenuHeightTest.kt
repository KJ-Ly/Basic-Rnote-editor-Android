package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuHeightTest {
    @Test fun `a menu keeps clear of the bars and of the margin above and below`() {
        // 1000 px at 2 px per dp is 500 dp; the bars take 40 dp and 20 dp, the margins 96 dp.
        assertEquals(344f, MenuHeight.maxDp(1000, 80, 40, 2f), 1e-3f)
    }

    @Test fun `a short landscape window leaves room for the menu to scroll`() {
        // The screen the canvas menu was cut off on: 923 px at 2.5 px per dp, under a tall status bar.
        val max = MenuHeight.maxDp(923, 85, 20, 2.5f)
        assertEquals((923f - 85f - 20f) / 2.5f - 96f, max, 1e-3f)
        assertTrue(max + 2 * MenuHeight.MARGIN_DP <= 923f / 2.5f)
    }

    @Test fun `a menu is never squeezed below the smallest height`() {
        assertEquals(MenuHeight.MIN_DP, MenuHeight.maxDp(300, 60, 60, 3f), 0f)
        assertEquals(MenuHeight.MIN_DP, MenuHeight.maxDp(0, 0, 0, 2f), 0f)
    }

    @Test fun `a density of nothing falls back to the smallest height`() {
        assertEquals(MenuHeight.MIN_DP, MenuHeight.maxDp(1000, 0, 0, 0f), 0f)
    }
}
