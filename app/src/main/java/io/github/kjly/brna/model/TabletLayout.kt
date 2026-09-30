package io.github.kjly.brna.model

/**
 * The tablet layout on a screen narrower than 600 dp. Below that width the pen strip is
 * hidden and Page Settings is a sheet (see MainActivity); some phones have a large screen
 * but report few dp, and on any phone the whole layout is otherwise out of reach. With the
 * switch on, the interface is scaled so that the screen is 600 dp wide — smaller, but all
 * there.
 */
object TabletLayout {
    /** Material's compact/medium boundary, from which the full layout is shown. */
    const val MIN_WIDTH_DP = 600

    /** Whether the screen gets the compact layout: narrow, and the tablet layout not asked for. */
    fun isCompact(screenWidthDp: Int, tabletLayout: Boolean): Boolean =
        screenWidthDp < MIN_WIDTH_DP && !tabletLayout

    /**
     * The factor the interface's density is multiplied by: [screenWidthDp] over 600 on a
     * narrow screen with the switch on, so 600 dp of layout fit in it; 1 otherwise.
     */
    fun scale(screenWidthDp: Int, tabletLayout: Boolean): Float =
        if (tabletLayout && screenWidthDp in 1 until MIN_WIDTH_DP) screenWidthDp.toFloat() / MIN_WIDTH_DP else 1f
}
