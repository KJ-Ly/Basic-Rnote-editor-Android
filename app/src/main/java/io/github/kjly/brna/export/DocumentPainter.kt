package io.github.kjly.brna.export

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.model.Stroke

/**
 * Paints one rectangular region of a document — paper, pattern, ink — onto any
 * [ExportCanvas]. Every export format goes through here, so SVG, PNG, JPEG and PDF can't
 * drift apart in what they include.
 */
object DocumentPainter {

    /**
     * How far [ExportPrefs.optimizePrinterOutput] pulls the pattern colour towards black.
     * Rnote's switch swaps the paper for white and darkens the pattern so it survives a
     * printer's dynamic range; this is that idea, not a transcription of its numbers.
     */
    private const val PRINTER_PATTERN_DARKEN = 0.45f

    fun paint(
        canvas: ExportCanvas,
        paperStyle: PaperStyle,
        strokes: List<Stroke>,
        region: Rect,
        prefs: ExportPrefs,
        pages: List<Rect> = emptyList()
    ) {
        val paperColor =
            if (prefs.optimizePrinterOutput) Color.White else paperStyle.currentBackgroundColor
        val patternColor =
            if (prefs.optimizePrinterOutput) darken(paperStyle.currentGridColor)
            else paperStyle.currentGridColor

        if (prefs.withBackground) {
            canvas.fillRect(region, paperColor)
        }

        if (prefs.withPattern) {
            if (pages.isEmpty()) {
                // No page grid — the pattern just fills the exported region.
                canvas.clipped(region) {
                    ExportPattern.paint(canvas, paperStyle, patternColor, region)
                }
            } else {
                // Clipped per page, so the pattern never bleeds past a page edge even when
                // the spacing doesn't divide the page size evenly.
                for (page in pages) {
                    val visible = ExportLayout.intersectOrNull(page, region) ?: continue
                    canvas.clipped(visible) {
                        ExportPattern.paint(canvas, paperStyle, patternColor, page)
                    }
                }
            }
        }

        // Clipped to the region so a stroke crossing a page boundary is cut at the edge
        // rather than spilling into the neighbouring page's file.
        canvas.clipped(region) {
            for (stroke in strokes) canvas.fillStroke(stroke)
        }
    }

    private fun darken(color: Color) = Color(
        red = color.red * PRINTER_PATTERN_DARKEN,
        green = color.green * PRINTER_PATTERN_DARKEN,
        blue = color.blue * PRINTER_PATTERN_DARKEN,
        alpha = color.alpha
    )
}
