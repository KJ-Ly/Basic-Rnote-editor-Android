package io.github.kjly.brna.export

import androidx.compose.ui.geometry.Rect
import io.github.kjly.brna.model.LayoutMode
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.model.Stroke
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
    fun contentBounds(
        strokes: List<Stroke>,
        /** Desktop elements (PDF pages, images, text, shapes) that count as content too. */
        nativeElements: List<NativeCanvasElement> = emptyList()
    ): Rect? {
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
        for (el in nativeElements) {
            // Brush strokes are already in [strokes], edited or erased since they were read.
            if (el is NativeBrushStroke) continue
            seen = true
            if (el.minX < minX) minX = el.minX
            if (el.minY < minY) minY = el.minY
            if (el.maxX > maxX) maxX = el.maxX
            if (el.maxY > maxY) maxY = el.maxY
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
        order: SplitOrder = SplitOrder.ROW_MAJOR,
        nativeElements: List<NativeCanvasElement> = emptyList(),
        /** Cut along imported PDF pages instead of the format grid; see [importedPageRects]. */
        followImportedPages: Boolean = false
    ): List<Rect> {
        if (followImportedPages) {
            importedPageRects(strokes, nativeElements, order)?.let { return it }
        }
        if (!hasPages(paperStyle)) return emptyList()

        val pageW = paperStyle.effectivePageWidthPx
        val pageH = paperStyle.effectivePageHeightPx
        val content = contentBounds(strokes, nativeElements)

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
            // Its pages, however many it has and whatever lies outside them, as Rnote exports it.
            LayoutMode.FIXED_SIZE -> { cols = listOf(0); rows = (0 until paperStyle.fixedPages).toList() }
            LayoutMode.CONTINUOUS_VERTICAL -> { cols = listOf(0); rows = (firstRow..lastRow).toList() }
            // Semi Infinite keeps the origin-inclusive span too: ink drawn left of or above
            // the origin is still in the file, so its pages are still exported.
            LayoutMode.SEMI_INFINITE, LayoutMode.INFINITE -> { cols = (firstCol..lastCol).toList(); rows = (firstRow..lastRow).toList() }
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

    /** Room left around ink that widens an imported page, so it isn't cut at the edge. */
    private const val IMPORTED_PAGE_MARGIN_PX = 24f

    /**
     * One page per imported PDF page, or null when the document has none.
     *
     * A PDF imported larger than the document format straddles several format pages, so
     * cutting along the format grid slices every worksheet into pieces. Here each imported
     * page is a page of its own, widened to take in the notes written beside it: anything
     * whose vertical centre lies level with the page belongs to it, and anything level with
     * no page goes to the nearest one.
     */
    fun importedPageRects(
        strokes: List<Stroke>,
        nativeElements: List<NativeCanvasElement>,
        order: SplitOrder = SplitOrder.ROW_MAJOR
    ): List<Rect>? {
        val imported = nativeElements.filterIsInstance<NativeVectorImageElement>()
        if (imported.isEmpty()) return null

        // Reading order: top to bottom, then left to right.
        val bases = imported
            .map { Rect(it.minX, it.minY, it.maxX, it.maxY) }
            .sortedWith(compareBy<Rect>({ it.top }, { it.left }))
        val lefts = bases.map { it.left }.toFloatArray()
        val tops = bases.map { it.top }.toFloatArray()
        val rights = bases.map { it.right }.toFloatArray()
        val bottoms = bases.map { it.bottom }.toFloatArray()

        fun include(minX: Float, minY: Float, maxX: Float, maxY: Float) {
            val cy = (minY + maxY) / 2f
            var best = -1
            var bestDistance = Float.MAX_VALUE
            for (i in bases.indices) {
                val b = bases[i]
                val distance = when {
                    cy < b.top -> b.top - cy
                    cy > b.bottom -> cy - b.bottom
                    else -> 0f
                }
                if (distance < bestDistance) { bestDistance = distance; best = i }
            }
            if (best < 0) return
            val m = IMPORTED_PAGE_MARGIN_PX
            if (minX - m < lefts[best]) lefts[best] = minX - m
            if (minY - m < tops[best]) tops[best] = minY - m
            if (maxX + m > rights[best]) rights[best] = maxX + m
            if (maxY + m > bottoms[best]) bottoms[best] = maxY + m
        }

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val half = stroke.strokeWidth / 2f
            include(
                stroke.points.minOf { it.x } - half, stroke.points.minOf { it.y } - half,
                stroke.points.maxOf { it.x } + half, stroke.points.maxOf { it.y } + half
            )
        }
        for (el in nativeElements) {
            if (el is NativeBrushStroke || el is NativeVectorImageElement) continue
            include(el.minX, el.minY, el.maxX, el.maxY)
        }

        val pages = bases.indices.map { Rect(lefts[it], tops[it], rights[it], bottoms[it]) }
        return if (order.isReversed) pages.reversed() else pages
    }

    /**
     * The region a whole-document export covers: the union of its pages, or — with no
     * page grid — the content plus a margin.
     */
    fun documentBounds(
        paperStyle: PaperStyle,
        strokes: List<Stroke>,
        nativeElements: List<NativeCanvasElement> = emptyList()
    ): Rect {
        val pages = pageRects(paperStyle, strokes, nativeElements = nativeElements)
        if (pages.isNotEmpty()) {
            return pages.reduce { acc, r ->
                Rect(
                    minOf(acc.left, r.left), minOf(acc.top, r.top),
                    maxOf(acc.right, r.right), maxOf(acc.bottom, r.bottom)
                )
            }
        }
        val content = contentBounds(strokes, nativeElements)
            ?: return Rect(0f, 0f, EMPTY_DOC_SIDE_PX, EMPTY_DOC_SIDE_PX)
        return content.inflate(UNPAGED_MARGIN_PX)
    }

    /**
     * The region a selection export covers: its bounds plus Rnote's configurable margin.
     * [natives] are the selected desktop elements — text, shapes, images — which the
     * selector holds alongside the ink.
     */
    fun selectionBounds(
        selection: List<Stroke>,
        marginPx: Float,
        natives: List<NativeCanvasElement> = emptyList()
    ): Rect? = contentBounds(selection, natives)?.inflate(marginPx.coerceAtLeast(0f))

    /**
     * Which of [pages] is "this page" for a [view] of the document: the one showing the
     * most of itself. Null when none of them is in view at all.
     */
    fun pageInView(pages: List<Rect>, view: Rect): Int? {
        var best: Int? = null
        var bestArea = 0f
        pages.forEachIndexed { i, page ->
            val overlap = intersectOrNull(page, view) ?: return@forEachIndexed
            val area = overlap.width * overlap.height
            if (area > bestArea) {
                best = i
                bestArea = area
            }
        }
        return best
    }

    /** The overlap of two rects, or null when they don't meet. */
    fun intersectOrNull(a: Rect, b: Rect): Rect? {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
    }
}
