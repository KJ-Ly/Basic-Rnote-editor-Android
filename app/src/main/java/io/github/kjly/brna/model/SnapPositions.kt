package io.github.kjly.brna.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.floor

/**
 * Desktop Rnote's "Snap Positions" (`Document::snap_position`, rnote-engine's
 * document/mod.rs): a point goes to the nearest point of the page's pattern — a dot, a
 * grid crossing, a ruled line, an isometric corner — and, near a page edge, onto the edge.
 * The Shaper, moving a selection, a new text box and vertical space all go through it.
 */
object SnapPositions {

    /** Rnote's `DOCUMENT_SNAP_DIST`: this close to a page edge, the edge wins. */
    const val DOCUMENT_SNAP_DIST = 10f

    /**
     * Rnote's `TRANSLATE_OFFSET_THRESHOLD`, in screen px: a snapped selection moves once
     * the pen has taken it at least this far.
     */
    const val TRANSLATE_THRESHOLD_PX = 1.414f

    private const val SQRT_THREE = 1.7320508075688772

    fun snap(pos: Offset, paper: PaperStyle): Offset {
        val onDocument = toGrid(pos, paper.effectivePageWidthPx, paper.effectivePageHeightPx)
        val spacingX = paper.gridSpacingPx
        val spacingY = paper.patternHeightPx
        val onPattern = when (paper.pattern) {
            PaperPattern.BLANK -> pos
            PaperPattern.LINES -> Offset(pos.x, toStep(pos.y, spacingY))
            PaperPattern.GRID, PaperPattern.DOTS -> toGrid(pos, spacingX, spacingY)
            PaperPattern.ISO_GRID, PaperPattern.ISO_DOTS -> toIsometric(pos, spacingY)
        }
        return Offset(
            if (abs(onDocument.x - pos.x) < DOCUMENT_SNAP_DIST) onDocument.x else onPattern.x,
            if (abs(onDocument.y - pos.y) < DOCUMENT_SNAP_DIST) onDocument.y else onPattern.y
        )
    }

    /**
     * The corner of [box] nearest [pos]: the one a moved selection snaps by, Rnote's
     * `SnapCorner::determine_from_bounds`.
     */
    fun nearestCorner(box: Rect, pos: Offset): Offset = Offset(
        if (abs(pos.x - box.left) < abs(pos.x - box.right)) box.left else box.right,
        if (abs(pos.y - box.top) < abs(pos.y - box.bottom)) box.top else box.bottom
    )

    private fun toGrid(pos: Offset, w: Float, h: Float) = Offset(toStep(pos.x, w), toStep(pos.y, h))

    /** [v] to the nearest multiple of [step]; as it is when there is no step. */
    private fun toStep(v: Float, step: Float): Float =
        if (step > 0f) (rustRound(v.toDouble() / step) * step).toFloat() else v

    /**
     * Rnote's `snap_to_isometric_pattern`: the nearest corner of the triangles, worked out
     * in cube coordinates as for a hexagon grid (redblobgames.com/grids/hexagons/#rounding).
     */
    private fun toIsometric(pos: Offset, spacing: Float): Offset {
        if (spacing <= 0f) return pos
        val columnWidth = spacing * SQRT_THREE
        val rowHeight = spacing * 0.5
        val q = pos.x / columnWidth + pos.y / spacing
        val r = pos.x / columnWidth - pos.y / spacing
        val s = -q - r
        var roundedQ = rustRound(q)
        var roundedR = rustRound(r)
        val roundedS = rustRound(s)
        val qDiff = abs(roundedQ - q)
        val rDiff = abs(roundedR - r)
        val sDiff = abs(roundedS - s)
        if (qDiff > rDiff && qDiff > sDiff) {
            roundedQ = -roundedR - roundedS
        } else if (rDiff > sDiff) {
            roundedR = -roundedQ - roundedS
        }
        return Offset(
            ((roundedQ + roundedR) * columnWidth * 0.5).toFloat(),
            ((roundedQ - roundedR) * rowHeight).toFloat()
        )
    }

    /** Rust's `f64::round`: halves away from zero, where Kotlin's `round` goes to even. */
    private fun rustRound(v: Double): Double = if (v < 0.0) -floor(-v + 0.5) else floor(v + 0.5)
}
