package com.rnote.baby.model

/** Physical page sizes expressed in inches. Canvas pixels = inches * CANVAS_DPI. */
const val CANVAS_DPI = 96f  // Reference canvas resolution: 96 px per inch

enum class PageSize(
    val displayName: String,
    val widthInches: Float,
    val heightInches: Float
) {
    INFINITE(
        displayName = "Infinite (No Pages)",
        widthInches = 0f,
        heightInches = 0f
    ),
    A6(
        displayName = "A6  105 × 148 mm",
        widthInches = 4.134f,   // 105 mm
        heightInches = 5.827f   // 148 mm
    ),
    A5(
        displayName = "A5  148 × 210 mm",
        widthInches = 5.827f,   // 148 mm
        heightInches = 8.268f   // 210 mm
    ),
    A4(
        displayName = "A4  210 × 297 mm",
        widthInches = 8.268f,   // 210 mm
        heightInches = 11.693f  // 297 mm
    ),
    A3(
        displayName = "A3  297 × 420 mm",
        widthInches = 11.693f,  // 297 mm
        heightInches = 16.535f  // 420 mm
    ),
    A2(
        displayName = "A2  420 × 594 mm",
        widthInches = 16.535f,  // 420 mm
        heightInches = 23.386f  // 594 mm
    ),
    LETTER(
        displayName = "US Letter  8.5\" × 11\"",
        widthInches = 8.5f,
        heightInches = 11f
    ),
    LEGAL(
        displayName = "US Legal  8.5\" × 14\"",
        widthInches = 8.5f,
        heightInches = 14f
    ),
    CUSTOM(
        displayName = "Custom",
        widthInches = 0f,
        heightInches = 0f
    );

    /** Page width in canvas pixels at the reference DPI. */
    val widthPx: Float get() = widthInches * CANVAS_DPI

    /** Page height in canvas pixels at the reference DPI. */
    val heightPx: Float get() = heightInches * CANVAS_DPI

    /** True when this entry represents unbounded infinite canvas. */
    val isInfinite: Boolean get() = this == INFINITE
}
