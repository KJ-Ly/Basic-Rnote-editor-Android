package com.rnote.baby.export

import androidx.compose.ui.geometry.Rect
import com.rnote.baby.model.LayoutMode
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.Stroke
import kotlin.math.floor

/**
 * Where the pages are, in document coordinates. The live canvas works this out per frame
 * from the viewport (see PaperBackgroundRenderer); an export has no viewport, so the page
 * grid is derived from the content's own extent instead.
 */
object ExportLayout {

    /**
     * A cap on how many pages one export may produce. An infinite-layout document with a
     * stray stroke a few thousand canvas px off-origin spans a surprising number of pages,
     * and each one is a file.
     */
    const val MAX_PAGES = 400

    /** Padding around the content of a page-less (infinite) document. */
    private const val UNPAGED_MARGIN_PX = 24f

    /** Fallback extent for an empty, page-less document, so the export isn't 0×0. */
    private const val EMPTY_DOC_SIDE_PX = 1024f

    /** False when the document has no page grid at all — an infinite canvas. */
    fun hasPages(paperStyle: PaperStyle): Boolean =
        !paperStyle.pageSize.isInfinite &&
            paperStyle.effectivePageWidthPx > 0f &&
            paperStyle.effectivePageHeightPx > 0f

    /**
     * The bounding box of [strokes], widened by each stroke's nominal half-width so the
     * outline isn't clipped at the edge. Null when there is nothing to bound.
     */
    fun contentBounds(strokes: List<Stroke>): Rect? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var seen = false

        for (stroke in strokes) {
            val half = stroke.strokeWidth / 2f
            for (p in stroke.points) {
                seen = true
                if (p.x - half < minX) minX = p.x - half
                if (p.y - half < minY) minY = p.y - half
                if (p.x + half > maxX) maxX = p.x + half
                if (p.y + half > maxY) maxY = p.y + half
            }
        }
        return if (seen) Rect(minX, minY, maxX, maxY) else null
    }

    /**
     * Every page of the document, in [order]. Empty when the document has no pages.
     *
     * The span always contains the origin page: Rnote's document only ever grows away
     * from (0,0), but a stroke here can sit at negative coordinates (panning left and
     * drawing is not prevented), and dropping those pages would drop that ink.
     */
    fun pageRects(
        paperStyle: PaperStyle,
        strokes: List<Stroke>,
        order: SplitOrder = SplitOrder.ROW_MAJOR
    ): List<Rect> {
        if (!hasPages(paperStyle)) return emptyList()

        val pageW = paperStyle.effectivePageWidthPx
        val pageH = paperStyle.effectivePageHeightPx
        val content = contentBounds(strokes)

        var firstCol = 0; var lastCol = 0
        var firstRow = 0; var lastRow = 0
        if (content != null) {
            firstCol = floor(content.left / pageW).toInt().coerceAtMost(0)
            lastCol = floor(content.right / pageW).toInt().coerceAtLeast(0)
            firstRow = floor(content.top / pageH).toInt().coerceAtMost(0)
            lastRow = floor(content.bottom / pageH).toInt().coerceAtLeast(0)
        }

        val cols: List<Int>
        val rows: List<Int>
        when (paperStyle.layoutMode) {
            LayoutMode.FIXED_SIZE -> { cols = listOf(0); rows = listOf(0) }
            LayoutMode.CONTINUOUS_VERTICAL -> { cols = listOf(0); rows = (firstRow..lastRow).toList() }
            LayoutMode.INFINITE -> { cols = (firstCol..lastCol).toList(); rows = (firstRow..lastRow).toList() }
        }

        val cells = if (order.isRowMajor) {
            rows.flatMap { r -> cols.map { c -> c to r } }
        } else {
            cols.flatMap { c -> rows.map { r -> c to r } }
        }
        val ordered = if (order.isReversed) cells.reversed() else cells

        return ordered.take(MAX_PAGES).map { (col, row) ->
            Rect(col * pageW, row * pageH, col * pageW + pageW, row * pageH + pageH)
        }
    }

    /**
     * The region a whole-document export covers: the union of its pages, or — with no
     * page grid — the content plus a margin.
     */
    fun documentBounds(paperStyle: PaperStyle, strokes: List<Stroke>): Rect {
        val pages = pageRects(paperStyle, strokes)
        if (pages.isNotEmpty()) {
            return pages.reduce { acc, r ->
                Rect(
                    minOf(acc.left, r.left), minOf(acc.top, r.top),
                    maxOf(acc.right, r.right), maxOf(acc.bottom, r.bottom)
                )
            }
        }
        val content = contentBounds(strokes)
            ?: return Rect(0f, 0f, EMPTY_DOC_SIDE_PX, EMPTY_DOC_SIDE_PX)
        return content.inflate(UNPAGED_MARGIN_PX)
    }

    /** The region a selection export covers: its bounds plus Rnote's configurable margin. */
    fun selectionBounds(selection: List<Stroke>, marginPx: Float): Rect? =
        contentBounds(selection)?.inflate(marginPx.coerceAtLeast(0f))

    /** The overlap of two rects, or null when they don't meet. */
    fun intersectOrNull(a: Rect, b: Rect): Rect? {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
    }
}
