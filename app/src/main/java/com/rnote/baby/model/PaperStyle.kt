package com.rnote.baby.model

import androidx.compose.ui.graphics.Color

enum class PaperPattern(val displayName: String) {
    BLANK("None"),
    DOTS("Dots"),
    GRID("Grid"),
    LINES("Lines"),
    ISO_GRID("Isometric Grid"),
    ISO_DOTS("Isometric Dots")
}

/** Measurement unit for displaying width/height values. */
enum class MeasureUnit(val label: String) {
    PX("px"),
    MM("mm"),
    IN("in");

    fun fromPx(px: Float, dpi: Float): Float = when (this) {
        PX -> px
        MM -> px / dpi * 25.4f
        IN -> px / dpi
    }

    fun toPx(value: Float, dpi: Float): Float = when (this) {
        PX -> value
        MM -> value / 25.4f * dpi
        IN -> value * dpi
    }
}

data class PaperStyle(
    val pattern: PaperPattern = PaperPattern.DOTS,
    val isDarkMode: Boolean = true,
    val backgroundColorDark: Color = Color(0xFF1E1E24),
    val backgroundColorLight: Color = Color(0xFFF7F9FC),
    val gridColorDark: Color = Color(0xFF383842),
    val gridColorLight: Color = Color(0xFFE2E8F0),
    /** Page size. Use CUSTOM for imported sizes with custom dimensions. */
    val pageSize: PageSize = PageSize.A3,
    /** Layout mode — replaces the old pageSize==INFINITE convention. */
    val layoutMode: LayoutMode = LayoutMode.INFINITE,
    /** Document DPI. Affects how mm/inch units map to canvas pixels. */
    val dpi: Float = 96f,
    /**
     * Dot/grid density in dots-per-inch on the canvas.
     * 5 DPI ≈ 5 mm spacing (standard dot paper). Range: 1–20.
     */
    val dotDensityDpi: Int = 5,
    val showPageBoundaries: Boolean = true,
    /** When true, page width and height are swapped (landscape orientation). */
    val isLandscape: Boolean = false,
    /** Custom page width in canvas pixels — only used when pageSize == CUSTOM. */
    val customWidthPx: Float = 0f,
    /** Custom page height in canvas pixels — only used when pageSize == CUSTOM. */
    val customHeightPx: Float = 0f,
    /** Custom grid/dot spacing in canvas pixels — overrides gridSpacingPx when > 0. */
    val customGridSpacingPx: Float = 0f,
    /** Custom background color from .rnote — overrides isDarkMode when set. */
    val customBackgroundColor: Color? = null,
    /** Custom grid/dot color from .rnote. */
    val customGridColor: Color? = null,
    /** Whether to show format border lines around pages. */
    val showFormatBorders: Boolean = true,
    /** Color of format border lines. */
    val formatBorderColor: Color = Color(0xFFDEDDD9),
    /** Whether to show the green × origin indicator. */
    val showOriginIndicator: Boolean = true,
    /** Pattern spacing height (independent from width for non-square patterns). */
    val customPatternHeightPx: Float = 0f
) {
    val currentBackgroundColor: Color
        get() = customBackgroundColor ?: if (isDarkMode) backgroundColorDark else backgroundColorLight

    val currentGridColor: Color
        get() = customGridColor ?: if (isDarkMode) gridColorDark else gridColorLight

    val currentBorderColor: Color
        get() = if (formatBorderColor != Color(0xFFDEDDD9)) formatBorderColor
                else if (isDarkMode) Color(0xFF45455A) else Color(0xFFCDD5E0)

    /** Dot/grid spacing in canvas pixels at CANVAS_DPI reference resolution. */
    val gridSpacingPx: Float
        get() = if (customGridSpacingPx > 0f) customGridSpacingPx
                else CANVAS_DPI / dotDensityDpi.coerceIn(1, 20)

    /** Pattern height spacing — defaults to gridSpacingPx if not explicitly set. */
    val patternHeightPx: Float
        get() = if (customPatternHeightPx > 0f) customPatternHeightPx else gridSpacingPx

    /** Page width in canvas pixels, accounting for landscape and custom sizes. */
    val effectivePageWidthPx: Float
        get() = when {
            pageSize == PageSize.CUSTOM -> if (isLandscape) customHeightPx else customWidthPx
            isLandscape -> pageSize.heightPx
            else -> pageSize.widthPx
        }

    /** Page height in canvas pixels, accounting for landscape and custom sizes. */
    val effectivePageHeightPx: Float
        get() = when {
            pageSize == PageSize.CUSTOM -> if (isLandscape) customWidthPx else customHeightPx
            isLandscape -> pageSize.widthPx
            else -> pageSize.heightPx
        }
}
