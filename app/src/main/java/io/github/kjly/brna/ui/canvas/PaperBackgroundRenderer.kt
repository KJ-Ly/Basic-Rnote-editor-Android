package io.github.kjly.brna.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.kjly.brna.model.PaperPattern
import io.github.kjly.brna.model.PaperStyle
import kotlin.math.floor
import kotlin.math.sqrt

object PaperBackgroundRenderer {

    /** Gap between pages in canvas pixels (both horizontal and vertical). */
    private const val PAGE_GAP_PX = 0f

    /**
     * Desktop Rnote's `DOTS_WIDTH` (rnote-engine/src/document/background.rs) — a dot is a
     * 1.5-canvas-px rounded square with corner radius width/3, *not* a circle, and it lives
     * in document space so it grows with zoom. This renderer draws in screen space (see
     * [drawPaperBackground]), so the scaling has to be applied by hand; previously the dot
     * was a fixed 1.6px-radius circle, which meant that at 500% zoom the spacing had grown
     * 5x while the dot had not, leaving near-invisible specks on a huge empty grid.
     */
    private const val DOTS_WIDTH_CANVAS = 1.5f

    /**
     * Desktop Rnote's `LINE_WIDTH` (same file) — 0.5 canvas px, shared by the ruled, grid
     * and isometric-grid patterns. Like the dots these lived in screen space here (a flat
     * 1f / 0.8f), so they thinned out relative to their spacing as you zoomed in.
     */
    private const val LINE_WIDTH_CANVAS = 0.5f

    /**
     * Desktop Rnote's `HEXAGON_HEIGHT` (same file). Rnote's isometric *dots* are small
     * hexagons rather than round dots — this is their vertex-to-vertex height in canvas px.
     */
    private const val ISO_DOT_HEIGHT_CANVAS = 2.0f

    /** Floor shared by every pattern, so it fades but never vanishes when zoomed far out. */
    private const val PATTERN_MIN_PX = 1f

    /** Screen-space stroke width for the line-based patterns at the current zoom. */
    private fun patternLineWidth(zoomLevel: Float): Float =
        (LINE_WIDTH_CANVAS * zoomLevel).coerceAtLeast(PATTERN_MIN_PX)

    /**
     * One isometric-lattice dot: a regular pointy-top hexagon of height
     * [ISO_DOT_HEIGHT_CANVAS]. Rnote builds this from `QUARTER_SQRT_THREE`/`HALF_SQRT_THREE`
     * multiples of the height, which are the half-width and full width of exactly this
     * hexagon; the vertex order below is our own, not a transcription of its path data.
     */
    private fun DrawScope.drawIsoDotHexagon(color: Color, center: Offset, zoomLevel: Float) {
        val h = (ISO_DOT_HEIGHT_CANVAS * zoomLevel).coerceAtLeast(PATTERN_MIN_PX)
        val halfH = h / 2f
        val quarterH = h / 4f
        val halfW = (sqrt(3f) / 4f) * h
        val hex = Path().apply {
            moveTo(center.x, center.y - halfH)
            lineTo(center.x + halfW, center.y - quarterH)
            lineTo(center.x + halfW, center.y + quarterH)
            lineTo(center.x, center.y + halfH)
            lineTo(center.x - halfW, center.y + quarterH)
            lineTo(center.x - halfW, center.y - quarterH)
            close()
        }
        drawPath(hex, color)
    }

    private fun DrawScope.drawPatternDot(color: Color, center: Offset, zoomLevel: Float) {
        val side = (DOTS_WIDTH_CANVAS * zoomLevel).coerceAtLeast(PATTERN_MIN_PX)
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
            size = Size(side, side),
            cornerRadius = CornerRadius(side / 3f, side / 3f)
        )
    }

    /**
     * Draws the paper background. Called BEFORE the viewport withTransform in DrawingCanvas,
     * so coordinates here are screen-space. Viewport pan/zoom is applied manually.
     */
    fun drawPaperBackground(
        drawScope: DrawScope,
        paperStyle: PaperStyle,
        zoomLevel: Float = 1.0f,
        panOffset: Offset = Offset.Zero
    ) {
        if (paperStyle.pageSize.isInfinite || !paperStyle.showPageBoundaries) {
            drawInfinitePattern(drawScope, paperStyle, zoomLevel, panOffset)
        } else {
            // ── Paged mode: 2D grid, vertical column, or fixed single page ──────
            val pageCanvasW = paperStyle.effectivePageWidthPx
            val pageCanvasH = paperStyle.effectivePageHeightPx
            val colSpacing  = pageCanvasW + PAGE_GAP_PX   // canvas px per column
            val rowSpacing  = pageCanvasH + PAGE_GAP_PX   // canvas px per row

            val screenW = drawScope.size.width
            val screenH = drawScope.size.height

            // Visible canvas bounds
            val leftCanvas   = -panOffset.x / zoomLevel
            val rightCanvas  = (screenW - panOffset.x) / zoomLevel
            val topCanvas    = -panOffset.y / zoomLevel
            val bottomCanvas = (screenH - panOffset.y) / zoomLevel

            // Determine visible page grid range based on LayoutMode
            val firstCol = floor(leftCanvas  / colSpacing).toInt() - 1
            val lastCol  = floor(rightCanvas / colSpacing).toInt() + 1
            val firstRow = floor(topCanvas   / rowSpacing).toInt() - 1
            val lastRow  = floor(bottomCanvas / rowSpacing).toInt() + 1

            val (colRange, rowRange) = when (paperStyle.layoutMode) {
                io.github.kjly.brna.model.LayoutMode.FIXED_SIZE -> {
                    0..0 to 0..0
                }
                io.github.kjly.brna.model.LayoutMode.CONTINUOUS_VERTICAL -> {
                    val rStart = firstRow.coerceAtLeast(0)
                    val rEnd = lastRow.coerceAtLeast(0)
                    0..0 to (rStart..rEnd)
                }
                io.github.kjly.brna.model.LayoutMode.INFINITE -> {
                    val colCount = (lastCol - firstCol).coerceIn(0, 30)
                    val rowCount = (lastRow - firstRow).coerceIn(0, 30)
                    (firstCol..(firstCol + colCount)) to (firstRow..(firstRow + rowCount))
                }
            }

            for (col in colRange) {
                for (row in rowRange) {
                    // Top-left of this page in canvas coordinates
                    val pageCanvasLeft = col * colSpacing
                    val pageCanvasTop  = row * rowSpacing

                    // Convert to screen coordinates
                    val screenLeft   = pageCanvasLeft * zoomLevel + panOffset.x
                    val screenTop    = pageCanvasTop  * zoomLevel + panOffset.y
                    val screenWidth  = pageCanvasW    * zoomLevel
                    val screenHeight = pageCanvasH    * zoomLevel

                    val pageScreenRect = Rect(
                        left   = screenLeft,
                        top    = screenTop,
                        right  = screenLeft + screenWidth,
                        bottom = screenTop  + screenHeight
                    )

                    // Skip pages that are entirely off-screen
                    if (pageScreenRect.right  < 0f || pageScreenRect.left > screenW) continue
                    if (pageScreenRect.bottom < 0f || pageScreenRect.top  > screenH) continue

                    drawPage(drawScope, paperStyle, pageScreenRect, zoomLevel, panOffset)
                }
            }
        }

        // ── Origin marker: green × centered on canvas (0,0) / page top-left ──
        if (paperStyle.showOriginIndicator) {
            with(drawScope) {
                val cx = panOffset.x
                val cy = panOffset.y
                val arm = 8f
                val markerColor = Color(0xFF4CAF50)
                drawLine(markerColor, Offset(cx - arm, cy - arm), Offset(cx + arm, cy + arm), 2.5f)
                drawLine(markerColor, Offset(cx + arm, cy - arm), Offset(cx - arm, cy + arm), 2.5f)
            }
        }
    }   // end drawPaperBackground

    // ── Out-of-bounds scrim ────────────────────────────────────────────────────

    /**
     * Alpha of the veil laid over canvas that lies outside the document. Two values
     * because a dark note is already dark outside its pages, so the same veil that reads
     * clearly on white paper would barely show there.
     */
    private const val SCRIM_ALPHA_LIGHT = 0.3f
    private const val SCRIM_ALPHA_DARK = 0.45f

    /**
     * Dims everything outside the area the layout actually covers — the single page at the
     * origin in Fixed Size, the column of pages from y=0 down in Continuous Vertical.
     * Nothing clamps drawing to those bounds (Rnote keeps such strokes in the file too),
     * so without this the canvas gives no sign that a long vertical note is being written
     * off the edge of a one-page document.
     *
     * Screen-space like [drawPaperBackground], but called AFTER the strokes so the ink
     * out there is dimmed with the paper rather than sitting brightly on top of it.
     */
    fun drawOutOfBoundsScrim(
        drawScope: DrawScope,
        paperStyle: PaperStyle,
        zoomLevel: Float = 1.0f,
        panOffset: Offset = Offset.Zero
    ) {
        // The pattern-only fallback draws no pages at all, so it has no outside.
        if (paperStyle.pageSize.isInfinite || !paperStyle.showPageBoundaries) return
        if (paperStyle.layoutMode == io.github.kjly.brna.model.LayoutMode.INFINITE) return

        val screenW = drawScope.size.width
        val screenH = drawScope.size.height

        val docLeft = panOffset.x
        val docTop = panOffset.y
        val docRight = paperStyle.effectivePageWidthPx * zoomLevel + panOffset.x
        // Continuous Vertical grows downwards without end, so it has no bottom edge to
        // dim past — clamping to the screen leaves that strip empty.
        val docBottom = if (paperStyle.layoutMode == io.github.kjly.brna.model.LayoutMode.FIXED_SIZE) {
            paperStyle.effectivePageHeightPx * zoomLevel + panOffset.y
        } else {
            screenH
        }

        val scrim = Color.Black.copy(
            alpha = if (paperStyle.isDarkMode) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT
        )

        with(drawScope) {
            fun dim(left: Float, top: Float, right: Float, bottom: Float) {
                val l = left.coerceIn(0f, screenW)
                val t = top.coerceIn(0f, screenH)
                val r = right.coerceIn(0f, screenW)
                val b = bottom.coerceIn(0f, screenH)
                if (r <= l || b <= t) return
                drawRect(color = scrim, topLeft = Offset(l, t), size = Size(r - l, b - t))
            }
            // Full-width bands above and below, then the two side strips between them, so
            // the four rects tile the outside exactly once — no seams, no double-darkening.
            dim(0f, 0f, screenW, docTop)
            dim(0f, docBottom, screenW, screenH)
            dim(0f, docTop, docLeft, docBottom)
            dim(docRight, docTop, screenW, docBottom)
        }
    }

    // ── Per-page rendering ─────────────────────────────────────────────────────

    private fun drawPage(
        drawScope: DrawScope,
        paperStyle: PaperStyle,
        pageRect: Rect,
        zoomLevel: Float,
        panOffset: Offset
    ) {
        with(drawScope) {
            val cornerRadius = CornerRadius(4f, 4f)

            // 1. Drop shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = if (paperStyle.isDarkMode) 0.45f else 0.18f),
                topLeft = Offset(pageRect.left + 6f, pageRect.top + 6f),
                size = pageRect.size,
                cornerRadius = cornerRadius
            )

            // 2. Page background
            val pageBgColor = paperStyle.currentBackgroundColor
            drawRoundRect(
                color = pageBgColor,
                topLeft = pageRect.topLeft,
                size = pageRect.size,
                cornerRadius = cornerRadius
            )

            // 3. Dot / grid pattern clipped to page
            val spacingPx = paperStyle.gridSpacingPx * zoomLevel
            if (spacingPx >= 6f) {
                clipRect(
                    left   = pageRect.left,
                    top    = pageRect.top,
                    right  = pageRect.right,
                    bottom = pageRect.bottom
                ) {
                    drawPattern(
                        drawScope  = this,
                        paperStyle = paperStyle,
                        spacingPx  = spacingPx,
                        panOffset  = panOffset,
                        pageRect   = pageRect,
                        zoomLevel  = zoomLevel
                    )
                }
            }

            // 4. Page format border (respects showFormatBorders toggle)
            if (paperStyle.showFormatBorders) {
                val borderColor = paperStyle.currentBorderColor
                drawRoundRect(
                    color        = borderColor,
                    topLeft      = pageRect.topLeft,
                    size         = pageRect.size,
                    cornerRadius = cornerRadius,
                    style        = Stroke(width = 1.5f)
                )
            }
        }
    }

    /**
     * Draws dots/grid/lines inside [pageRect] using a GLOBALLY aligned phase so that
     * adjacent pages share the same dot grid — no visible seam at page boundaries.
     *
     * Global dot positions satisfy: x = panOffset.x + n * spacingPx for integer n.
     * We find the first such x >= pageRect.left, then iterate across the page.
     */
    private fun drawPattern(
        drawScope: DrawScope,
        paperStyle: PaperStyle,
        spacingPx: Float,
        panOffset: Offset,
        pageRect: Rect,
        zoomLevel: Float = 1f
    ) {
        // Global phase: where the infinite grid origin falls on screen
        val globalPhaseX = ((panOffset.x % spacingPx) + spacingPx) % spacingPx
        val globalPhaseY = ((panOffset.y % spacingPx) + spacingPx) % spacingPx

        // First grid line/dot inside the page (from the left/top edges)
        val firstDotX = run {
            val offset = ((pageRect.left - globalPhaseX) % spacingPx + spacingPx) % spacingPx
            pageRect.left + if (offset < 0.001f) 0f else (spacingPx - offset)
        }
        val firstDotY = run {
            val offset = ((pageRect.top - globalPhaseY) % spacingPx + spacingPx) % spacingPx
            pageRect.top + if (offset < 0.001f) 0f else (spacingPx - offset)
        }

        val gridColor = paperStyle.currentGridColor

        with(drawScope) {
            when (paperStyle.pattern) {
                PaperPattern.DOTS -> {
                    var x = firstDotX
                    while (x <= pageRect.right + 0.5f) {
                        var y = firstDotY
                        while (y <= pageRect.bottom + 0.5f) {
                            drawPatternDot(gridColor, Offset(x, y), zoomLevel)
                            y += spacingPx
                        }
                        x += spacingPx
                    }
                }

                PaperPattern.GRID -> {
                    var x = firstDotX
                    while (x <= pageRect.right + 0.5f) {
                        drawLine(gridColor, Offset(x, pageRect.top), Offset(x, pageRect.bottom), patternLineWidth(zoomLevel))
                        x += spacingPx
                    }
                    var y = firstDotY
                    while (y <= pageRect.bottom + 0.5f) {
                        drawLine(gridColor, Offset(pageRect.left, y), Offset(pageRect.right, y), patternLineWidth(zoomLevel))
                        y += spacingPx
                    }
                }

                PaperPattern.LINES -> {
                    var y = firstDotY
                    while (y <= pageRect.bottom + 0.5f) {
                        drawLine(gridColor, Offset(pageRect.left, y), Offset(pageRect.right, y), patternLineWidth(zoomLevel))
                        y += spacingPx
                    }
                }

                PaperPattern.ISO_GRID -> {
                    drawIsometricGrid(this, gridColor, spacingPx, pageRect, panOffset, zoomLevel)
                }

                PaperPattern.ISO_DOTS -> {
                    drawIsometricDots(this, gridColor, spacingPx, pageRect, panOffset, zoomLevel)
                }

                PaperPattern.BLANK -> { /* page background is enough */ }
            }
        }
    }

    // ── Infinite canvas fallback (PageSize.INFINITE) ───────────────────────────

    private fun drawInfinitePattern(
        drawScope: DrawScope,
        paperStyle: PaperStyle,
        zoomLevel: Float,
        panOffset: Offset
    ) {
        val width     = drawScope.size.width
        val height    = drawScope.size.height
        val gridColor = paperStyle.currentGridColor
        val spacingPx = paperStyle.gridSpacingPx * zoomLevel

        if (spacingPx < 6f) return

        val startX = (panOffset.x % spacingPx + spacingPx) % spacingPx
        val startY = (panOffset.y % spacingPx + spacingPx) % spacingPx
        val screenRect = Rect(0f, 0f, width, height)

        with(drawScope) {
            when (paperStyle.pattern) {
                PaperPattern.DOTS -> {
                    var x = startX
                    while (x < width) {
                        var y = startY
                        while (y < height) {
                            drawPatternDot(gridColor, Offset(x, y), zoomLevel)
                            y += spacingPx
                        }
                        x += spacingPx
                    }
                }
                PaperPattern.GRID -> {
                    var x = startX
                    while (x < width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, height), patternLineWidth(zoomLevel))
                        x += spacingPx
                    }
                    var y = startY
                    while (y < height) {
                        drawLine(gridColor, Offset(0f, y), Offset(width, y), patternLineWidth(zoomLevel))
                        y += spacingPx
                    }
                }
                PaperPattern.LINES -> {
                    var y = startY
                    while (y < height) {
                        drawLine(gridColor, Offset(0f, y), Offset(width, y), patternLineWidth(zoomLevel))
                        y += spacingPx
                    }
                }
                PaperPattern.ISO_GRID -> {
                    drawIsometricGrid(this, gridColor, spacingPx, screenRect, panOffset, zoomLevel)
                }
                PaperPattern.ISO_DOTS -> {
                    drawIsometricDots(this, gridColor, spacingPx, screenRect, panOffset, zoomLevel)
                }
                PaperPattern.BLANK -> { /* solid bg only */ }
            }
        }
    }

    // ── Isometric pattern helpers ──────────────────────────────────────────────

    /**
     * Draws an equilateral triangle grid (isometric). The horizontal spacing is [spacingPx]
     * and the vertical spacing is spacingPx * sqrt(3)/2 for equilateral triangles.
     */
    private fun drawIsometricGrid(
        drawScope: DrawScope,
        color: Color,
        spacingPx: Float,
        rect: Rect,
        panOffset: Offset,
        zoomLevel: Float
    ) {
        val lineWidth = patternLineWidth(zoomLevel)
        val rowH = spacingPx * sqrt(3f) / 2f
        val phaseX = ((panOffset.x % spacingPx) + spacingPx) % spacingPx
        val phaseY = ((panOffset.y % rowH) + rowH) % rowH

        with(drawScope) {
            // Horizontal lines
            var y = rect.top + ((rect.top - phaseY) % rowH + rowH) % rowH
            if (y > rect.top) y -= rowH
            while (y <= rect.bottom + rowH) {
                drawLine(color, Offset(rect.left, y), Offset(rect.right, y), lineWidth)
                y += rowH
            }

            // Diagonal lines: \ direction
            val diagCount = ((rect.width + rect.height) / spacingPx).toInt() + 4
            for (i in -diagCount..diagCount) {
                val baseX = rect.left + phaseX + i * spacingPx
                val x1 = baseX
                val y1 = rect.top
                val x2 = baseX + (rect.height / rowH) * (spacingPx / 2f)
                val y2 = rect.bottom
                drawLine(color, Offset(x1, y1), Offset(x2, y2), lineWidth)
            }

            // Diagonal lines: / direction
            for (i in -diagCount..diagCount) {
                val baseX = rect.left + phaseX + i * spacingPx
                val x1 = baseX
                val y1 = rect.top
                val x2 = baseX - (rect.height / rowH) * (spacingPx / 2f)
                val y2 = rect.bottom
                drawLine(color, Offset(x1, y1), Offset(x2, y2), lineWidth)
            }
        }
    }

    /**
     * Draws dots at equilateral triangle vertices (isometric dot pattern).
     */
    private fun drawIsometricDots(
        drawScope: DrawScope,
        color: Color,
        spacingPx: Float,
        rect: Rect,
        panOffset: Offset,
        zoomLevel: Float
    ) {
        val rowH = spacingPx * sqrt(3f) / 2f
        val phaseX = ((panOffset.x % spacingPx) + spacingPx) % spacingPx
        val phaseY = ((panOffset.y % rowH) + rowH) % rowH

        with(drawScope) {
            var rowIdx = 0
            var y = rect.top + ((rect.top - phaseY) % rowH + rowH) % rowH
            if (y > rect.top) { y -= rowH; rowIdx-- }
            while (y <= rect.bottom + 0.5f) {
                val xOffset = if (rowIdx % 2 != 0) spacingPx / 2f else 0f
                var x = rect.left + ((rect.left - phaseX - xOffset) % spacingPx + spacingPx) % spacingPx + xOffset
                if (x > rect.left + spacingPx) x -= spacingPx
                while (x <= rect.right + 0.5f) {
                    drawIsoDotHexagon(color, Offset(x, y), zoomLevel)
                    x += spacingPx
                }
                y += rowH
                rowIdx++
            }
        }
    }
}
