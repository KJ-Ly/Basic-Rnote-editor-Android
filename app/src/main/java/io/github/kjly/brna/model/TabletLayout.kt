package io.github.kjly.brna.model

/**
 * The tablet layout on a screen that is too small for the full one. Below 600 dp wide, or
 * below 480 dp high, the pen strip is hidden and Page Settings is a sheet (see MainActivity):
 * the strip would otherwise overlap most of the drawing area, or, on a phone held sideways,
 * be cut off at the bottom. Some phones have a large screen but report few dp, and on any
 * phone the whole layout is otherwise out of reach. With the switch on, the interface is
 * scaled so that 600 x 480 dp of it fit in the screen — smaller, but all there.
 */
object TabletLayout {
    /** Material's compact/medium width boundary, from which the full layout is shown. */
    const val MIN_WIDTH_DP = 600

    /** Material's compact/medium height boundary: below it the strip and the toolbars do not fit. */
    const val MIN_HEIGHT_DP = 480

    /**
     * Whether the screen is too small for the full layout, however it is held. A height of
     * nothing means the size is not known yet and is not counted.
     */
    fun isNarrow(screenWidthDp: Int, screenHeightDp: Int): Boolean =
        screenWidthDp < MIN_WIDTH_DP || screenHeightDp in 1 until MIN_HEIGHT_DP

    /** Whether the screen gets the compact layout: too small, and the tablet layout not asked for. */
    fun isCompact(screenWidthDp: Int, screenHeightDp: Int, tabletLayout: Boolean): Boolean =
        isNarrow(screenWidthDp, screenHeightDp) && !tabletLayout

    /**
     * The factor the interface's density is multiplied by: the screen over 600 x 480 dp, the
     * tighter of the two, on a small screen with the switch on, so that much of the layout
     * fits in it; 1 otherwise.
     */
    fun scale(screenWidthDp: Int, screenHeightDp: Int, tabletLayout: Boolean): Float {
        if (!tabletLayout || screenWidthDp <= 0 || screenHeightDp <= 0) return 1f
        if (!isNarrow(screenWidthDp, screenHeightDp)) return 1f
        return minOf(screenWidthDp.toFloat() / MIN_WIDTH_DP, screenHeightDp.toFloat() / MIN_HEIGHT_DP)
    }
}
