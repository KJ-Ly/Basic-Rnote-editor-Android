package io.github.kjly.brna.model

/**
 * How tall a dropdown menu may be. Compose keeps 48 dp free above and below a menu; one that
 * is taller than the window minus both has no position that fits it, so it is parked partly
 * off screen and its last entries can never be scrolled into view (the canvas menu on a
 * tablet held in landscape, or a phone). Capped, the menu scrolls inside what is visible.
 */
object MenuHeight {
    /** The margin Compose keeps free above and below a dropdown menu. */
    const val MARGIN_DP = 48f

    /** Never squeeze a menu below this, however small the window is. */
    const val MIN_DP = 120f

    /**
     * The tallest a menu may be, in dp. The window's height loses the system bars, which the
     * popup may or may not count, so the cap holds either way.
     */
    fun maxDp(rootHeightPx: Int, topInsetPx: Int, bottomInsetPx: Int, density: Float): Float {
        if (density <= 0f) return MIN_DP
        val usableDp = (rootHeightPx - topInsetPx - bottomInsetPx) / density
        return maxOf(usableDp - 2 * MARGIN_DP, MIN_DP)
    }
}
