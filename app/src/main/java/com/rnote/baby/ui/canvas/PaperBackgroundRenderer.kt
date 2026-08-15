package com.rnote.baby.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rnote.baby.model.PaperPattern
import com.rnote.baby.model.PaperStyle
import kotlin.math.floor
import kotlin.math.sqrt

object PaperBackgroundRenderer {

    /** Gap between pages in canvas pixels (both horizontal and vertical). */
    private const val PAGE_GAP_PX = 0f

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
                com.rnote.baby.model.LayoutMode.FIXED_SIZE -> {
                    0..0 to 0..0
                }
                com.rnote.baby.model.LayoutMode.CONTINUOUS_VERTICAL -> {
                    val rStart = firstRow.coerceAtLeast(0)
                    val rEnd = lastRow.coerceAtLeast(0)
                    0..0 to (rStart..rEnd)
                }
                com.rnote.baby.model.LayoutMode.INFINITE -> {
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
                            drawCircle(color = gridColor, radius = 1.6f, center = Offset(x, y))
                            y += spacingPx
                        }
                        x += spacingPx
                    }
                }

                PaperPattern.GRID -> {
                    var x = firstDotX
                    while (x <= pageRect.right + 0.5f) {
                        drawLine(gridColor, Offset(x, pageRect.top), Offset(x, pageRect.bottom), 1f)
                        x += spacingPx
                    }
                    var y = firstDotY
                    while (y <= pageRect.bottom + 0.5f) {
                        drawLine(gridColor, Offset(pageRect.left, y), Offset(pageRect.right, y), 1f)
                        y += spacingPx
                    }
                }

                PaperPattern.LINES -> {
                    var y = firstDotY
                    while (y <= pageRect.bottom + 0.5f) {
                        drawLine(gridColor, Offset(pageRect.left, y), Offset(pageRect.right, y), 1f)
                        y += spacingPx
                    }
                }

                PaperPattern.ISO_GRID -> {
                    drawIsometricGrid(this, gridColor, spacingPx, pageRect, panOffset)
                }

                PaperPattern.ISO_DOTS -> {
                    drawIsometricDots(this, gridColor, spacingPx, pageRect, panOffset)
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
                            drawCircle(color = gridColor, radius = 1.6f, center = Offset(x, y))
                            y += spacingPx
                        }
                        x += spacingPx
                    }
                }
                PaperPattern.GRID -> {
                    var x = startX
                    while (x < width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, height), 1f)
                        x += spacingPx
                    }
                    var y = startY
                    while (y < height) {
                        drawLine(gridColor, Offset(0f, y), Offset(width, y), 1f)
                        y += spacingPx
                    }
                }
                PaperPattern.LINES -> {
                    var y = startY
                    while (y < height) {
                        drawLine(gridColor, Offset(0f, y), Offset(width, y), 1f)
                        y += spacingPx
                    }
                }
                PaperPattern.ISO_GRID -> {
                    drawIsometricGrid(this, gridColor, spacingPx, screenRect, panOffset)
                }
                PaperPattern.ISO_DOTS -> {
                    drawIsometricDots(this, gridColor, spacingPx, screenRect, panOffset)
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
        panOffset: Offset
    ) {
        val rowH = spacingPx * sqrt(3f) / 2f
        val phaseX = ((panOffset.x % spacingPx) + spacingPx) % spacingPx
        val phaseY = ((panOffset.y % rowH) + rowH) % rowH

        with(drawScope) {
            // Horizontal lines
            var y = rect.top + ((rect.top - phaseY) % rowH + rowH) % rowH
            if (y > rect.top) y -= rowH
            while (y <= rect.bottom + rowH) {
                drawLine(color, Offset(rect.left, y), Offset(rect.right, y), 0.8f)
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
                drawLine(color, Offset(x1, y1), Offset(x2, y2), 0.8f)
            }

            // Diagonal lines: / direction
            for (i in -diagCount..diagCount) {
                val baseX = rect.left + phaseX + i * spacingPx
                val x1 = baseX
                val y1 = rect.top
                val x2 = baseX - (rect.height / rowH) * (spacingPx / 2f)
                val y2 = rect.bottom
                drawLine(color, Offset(x1, y1), Offset(x2, y2), 0.8f)
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
        panOffset: Offset
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
                    drawCircle(color = color, radius = 1.6f, center = Offset(x, y))
                    x += spacingPx
                }
                y += rowH
                rowIdx++
            }
        }
    }
}
