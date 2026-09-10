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
}
