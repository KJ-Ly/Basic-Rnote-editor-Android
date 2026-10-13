package io.github.kjly.brna.render

/**
 * Desktop Rnote's background pattern metrics (rnote-engine/src/document/background.rs),
 * in canvas px. Two renderers share them: the live canvas (PaperBackgroundRenderer draws
 * in screen space, so it multiplies each by the zoom level) and export (ExportPattern
 * draws in document space, so it uses them as they are).
 */
object PatternMetrics {

    /** `DOTS_WIDTH` — a dot is a rounded square of this side, not a circle. */
    const val DOTS_WIDTH = 1.5f

    /** `LINE_WIDTH` — shared by the ruled, grid and isometric-grid patterns. */
    const val LINE_WIDTH = 0.5f

    /** `HEXAGON_HEIGHT` — isometric *dots* are small hexagons; this is their height. */
    const val ISO_DOT_HEIGHT = 2.0f

    /** Below this many screen px between lines or dots the live canvas draws no pattern. */
    const val HIDE_BELOW_PX = 6f

    /** From this many screen px down the pattern fades towards [HIDE_BELOW_PX]. */
    const val FADE_BELOW_PX = 10f

    /**
     * How strongly the live canvas draws a pattern whose cells are [spacingPx] screen px
     * apart: fully from [FADE_BELOW_PX] up, not at all at [HIDE_BELOW_PX], linear between.
     * The pattern used to switch off hard at 6 px, so just above it the lines sat 6-7 px
     * apart on a pixel raster they did not divide — the uneven cells read as a moiré.
     */
    fun fadeAlpha(spacingPx: Float): Float =
        ((spacingPx - HIDE_BELOW_PX) / (FADE_BELOW_PX - HIDE_BELOW_PX)).coerceIn(0f, 1f)
}
