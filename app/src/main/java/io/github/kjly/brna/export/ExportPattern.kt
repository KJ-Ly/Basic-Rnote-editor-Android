package io.github.kjly.brna.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.PaperPattern
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.render.PatternMetrics
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The paper pattern, drawn in document space at 1:1 for export. PaperBackgroundRenderer
 * does the same job for the screen, but everything there is in screen coordinates and
 * phased against the viewport pan, so none of it is reusable here.
 *
 * The lattice is anchored at the document origin, which is what makes adjacent pages
 * share one continuous grid — the same "globally aligned" rule the live canvas follows.
 */
object ExportPattern {

    /** Refuse to emit a pattern denser than this; it would be a hang, not a picture. */
    private const val MAX_ELEMENTS = 200_000

    fun paint(canvas: ExportCanvas, paperStyle: PaperStyle, color: Color, area: Rect) {
        if (paperStyle.pattern == PaperPattern.BLANK) return

        // Rnote's background carries independent x/y pattern spacing, and ruled paper is
        // the case where they differ; rows, ruled lines and isometric patterns go by y.
        val sx = paperStyle.gridSpacingPx
        val sy = paperStyle.patternHeightPx
        if (sx < 0.5f || sy < 0.5f) return
        if (estimatedCount(area, sx, sy) > MAX_ELEMENTS) return

        when (paperStyle.pattern) {
            PaperPattern.DOTS -> {
                val side = PatternMetrics.DOTS_WIDTH
                forEachLattice(area, sx, sy) { x, y ->
                    canvas.fillRoundRect(
                        Rect(x - side / 2f, y - side / 2f, x + side / 2f, y + side / 2f),
                        side / 3f,
                        color
                    )
                }
            }

            PaperPattern.GRID -> {
                forEachStep(area.left, area.right, sx) { x ->
                    canvas.drawLine(x, area.top, x, area.bottom, PatternMetrics.LINE_WIDTH, color)
                }
                forEachStep(area.top, area.bottom, sy) { y ->
                    canvas.drawLine(area.left, y, area.right, y, PatternMetrics.LINE_WIDTH, color)
                }
            }

            PaperPattern.LINES -> {
                forEachStep(area.top, area.bottom, sy) { y ->
                    canvas.drawLine(area.left, y, area.right, y, PatternMetrics.LINE_WIDTH, color)
                }
            }

            PaperPattern.ISO_GRID -> drawIsoGrid(canvas, color, area, sy)

            PaperPattern.ISO_DOTS -> drawIsoDots(canvas, color, area, sy)

            PaperPattern.BLANK -> Unit
        }
    }

    // ── Lattice helpers ───────────────────────────────────────────────────────

    private fun estimatedCount(area: Rect, sx: Float, sy: Float): Long =
        ((area.width / sx).toLong() + 1) * ((area.height / sy).toLong() + 1)

    /** Every multiple of [step] that falls inside [from]..[to]. */
    private inline fun forEachStep(from: Float, to: Float, step: Float, body: (Float) -> Unit) {
        var i = ceil(from / step).toInt()
        var v = i * step
        while (v <= to) {
            body(v)
            i++
            v = i * step
        }
    }

    private inline fun forEachLattice(area: Rect, sx: Float, sy: Float, body: (Float, Float) -> Unit) {
        forEachStep(area.left, area.right, sx) { x ->
            forEachStep(area.top, area.bottom, sy) { y -> body(x, y) }
        }
    }

    // ── Isometric patterns ────────────────────────────────────────────────────

    /** Column pitch of Rnote's isometric lattice: triangles of side [spacing] on an upright edge. */
    private fun isoColumnWidth(spacing: Float) = spacing * sqrt(3f) / 2f

    /**
     * Rnote's `gen_iso_grid_pattern`: upright lines a column apart, crossed by the two
     * families y = ±x/√3 + n·spacing.
     */
    private fun drawIsoGrid(canvas: ExportCanvas, color: Color, area: Rect, spacing: Float) {
        val column = isoColumnWidth(spacing)
        val w = PatternMetrics.LINE_WIDTH

        forEachStep(area.left, area.right, column) { x ->
            canvas.drawLine(x, area.top, x, area.bottom, w, color)
        }

        val slope = 1f / sqrt(3f)
        for (dir in intArrayOf(1, -1)) {
            val atLeft = dir * slope * area.left
            val atRight = dir * slope * area.right
            val nMin = floor((area.top - maxOf(atLeft, atRight)) / spacing).toInt()
            val nMax = ceil((area.bottom - minOf(atLeft, atRight)) / spacing).toInt()
            if (nMax.toLong() - nMin.toLong() > MAX_ELEMENTS) return
            for (n in nMin..nMax) {
                canvas.drawLine(area.left, atLeft + n * spacing, area.right, atRight + n * spacing, w, color)
            }
        }
    }

    /** Rnote's `gen_iso_dots_pattern`: a hexagon on every corner, every other column half a step down. */
    private fun drawIsoDots(canvas: ExportCanvas, color: Color, area: Rect, spacing: Float) {
        val column = isoColumnWidth(spacing)
        val h = PatternMetrics.ISO_DOT_HEIGHT

        var k = ceil(area.left / column).toInt()
        var x = k * column
        while (x <= area.right) {
            val shift = if (k % 2 != 0) spacing / 2f else 0f
            var n = ceil((area.top - shift) / spacing).toInt()
            var y = n * spacing + shift
            while (y <= area.bottom) {
                canvas.fillPolygon(hexagon(x, y, h), color)
                n++
                y = n * spacing + shift
            }
            k++
            x = k * column
        }
    }

    /** A regular pointy-top hexagon of vertex-to-vertex height [height]. */
    private fun hexagon(cx: Float, cy: Float, height: Float): List<Offset> {
        val halfH = height / 2f
        val quarterH = height / 4f
        val halfW = (sqrt(3f) / 4f) * height
        return listOf(
            Offset(cx, cy - halfH),
            Offset(cx + halfW, cy - quarterH),
            Offset(cx + halfW, cy + quarterH),
            Offset(cx, cy + halfH),
            Offset(cx - halfW, cy + quarterH),
            Offset(cx - halfW, cy - quarterH)
        )
    }
}
