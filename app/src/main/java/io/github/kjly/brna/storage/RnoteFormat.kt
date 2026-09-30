package io.github.kjly.brna.storage

/**
 * Rnote's limits on a document's format (`Format` in format.rs). Rnote holds to them
 * whenever the format is set, but not when a file is read: a page 0 wide, or 0 high,
 * goes straight into its sums — a Fixed Size document's height becomes 0/0 — so a file
 * written here never names one.
 */
object RnoteFormat {

    /** `Format::WIDTH_MIN`/`WIDTH_MAX`, the same for the height, in document units. */
    const val SIDE_MIN = 1f
    const val SIDE_MAX = 30000f

    /** `Format::WIDTH_DEFAULT`/`HEIGHT_DEFAULT`: an A3 page, a new Rnote document's. */
    const val WIDTH_DEFAULT = 1123f
    const val HEIGHT_DEFAULT = 1587f

    /** `Format::DPI_MIN`/`DPI_MAX`/`DPI_DEFAULT`. */
    const val DPI_MIN = 1f
    const val DPI_MAX = 5000f
    const val DPI_DEFAULT = 96f

    /**
     * A page side as Rnote may be given it: up to [SIDE_MAX], as its `set_width` clamps,
     * and its [default] for a page with none at all — this app's "no pages" is 0 wide.
     */
    fun side(px: Float, default: Float): Float = when {
        px.isNaN() || px < SIDE_MIN -> default
        else -> px.coerceAtMost(SIDE_MAX)
    }

    /** A dpi as Rnote may be given it, and its default for none that makes sense. */
    fun dpi(dpi: Float): Float = when {
        dpi.isNaN() || dpi < DPI_MIN -> DPI_DEFAULT
        else -> dpi.coerceAtMost(DPI_MAX)
    }
}
