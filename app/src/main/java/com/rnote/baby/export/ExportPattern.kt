package com.rnote.baby.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.rnote.baby.model.PaperPattern
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.render.PatternMetrics
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
        // the case where they differ; the live canvas only uses the x spacing for both.
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

            PaperPattern.ISO_GRID -> drawIsoGrid(canvas, color, area, sx)

            PaperPattern.ISO_DOTS -> drawIsoDots(canvas, color, area, sx)

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

    /** Row pitch of an equilateral-triangle lattice of horizontal spacing [spacing]. */
    private fun isoRowHeight(spacing: Float) = spacing * sqrt(3f) / 2f

    private fun drawIsoGrid(canvas: ExportCanvas, color: Color, area: Rect, spacing: Float) {
        val rowH = isoRowHeight(spacing)
        val w = PatternMetrics.LINE_WIDTH

        forEachStep(area.top, area.bottom, rowH) { y ->
            canvas.drawLine(area.left, y, area.right, y, w, color)
        }

        // The two diagonal families. A descending line through (k*spacing, 0) has moved
        // (y / rowH) * (spacing / 2) to the right by the time it reaches y; the ascending
        // family mirrors it.
        for (dir in intArrayOf(1, -1)) {
            val shiftTop = dir * (area.top / rowH) * (spacing / 2f)
            val shiftBottom = dir * (area.bottom / rowH) * (spacing / 2f)
            val kMin = floor((area.left - maxOf(shiftTop, shiftBottom)) / spacing).toInt()
            val kMax = ceil((area.right - minOf(shiftTop, shiftBottom)) / spacing).toInt()
            if (kMax.toLong() - kMin.toLong() > MAX_ELEMENTS) return
            for (k in kMin..kMax) {
                val base = k * spacing
                canvas.drawLine(base + shiftTop, area.top, base + shiftBottom, area.bottom, w, color)
            }
        }
    }

    private fun drawIsoDots(canvas: ExportCanvas, color: Color, area: Rect, spacing: Float) {
        val rowH = isoRowHeight(spacing)
        val h = PatternMetrics.ISO_DOT_HEIGHT

        var row = ceil(area.top / rowH).toInt()
        var y = row * rowH
        while (y <= area.bottom) {
            // Every other row is offset by half a step — that is what makes it isometric.
            val xOffset = if (row % 2 != 0) spacing / 2f else 0f
            var k = ceil((area.left - xOffset) / spacing).toInt()
            var x = k * spacing + xOffset
            while (x <= area.right) {
                canvas.fillPolygon(hexagon(x, y, h), color)
                k++
                x = k * spacing + xOffset
            }
            row++
            y = row * rowH
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
