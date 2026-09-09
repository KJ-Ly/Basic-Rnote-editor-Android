package com.rnote.baby.model

import androidx.compose.ui.graphics.Color

// ── Color ─────────────────────────────────────────────────────────────────────

data class RnoteNativeColor(val r: Float, val g: Float, val b: Float, val a: Float) {
    fun toComposeColor() = Color(r.coerceIn(0f,1f), g.coerceIn(0f,1f), b.coerceIn(0f,1f), a.coerceIn(0f,1f))
    companion object {
        val BLACK = RnoteNativeColor(0f, 0f, 0f, 1f)
        val WHITE = RnoteNativeColor(1f, 1f, 1f, 1f)
    }
}

// ── Background ────────────────────────────────────────────────────────────────

enum class NativePatternType { BLANK, GRID, RULED, DOTS, ISO_GRID, ISO_DOTS }

data class NativeBackgroundConfig(
    val color: RnoteNativeColor = RnoteNativeColor.WHITE,
    val pattern: NativePatternType = NativePatternType.DOTS,
    val patternWidth: Float = 21f,
    val patternHeight: Float = 21f,
    val patternColor: RnoteNativeColor = RnoteNativeColor(0.8f, 0.9f, 1f, 1f)
)

// ── Canvas element sealed hierarchy ───────────────────────────────────────────

sealed class NativeCanvasElement {
    abstract val minX: Float; abstract val minY: Float
    abstract val maxX: Float; abstract val maxY: Float
}

/** A single sampled point on a brush stroke. */
data class NativeStrokePoint(val x: Float, val y: Float, val pressure: Float)

/** Freehand pen or highlighter stroke. */
data class NativeBrushStroke(
    val points: List<NativeStrokePoint>,
    val strokeWidth: Float,
    val color: RnoteNativeColor,
    val isHighlighter: Boolean,
    override val minX: Float, override val minY: Float,
    override val maxX: Float, override val maxY: Float,
    /** The style's `pressure_curve`; see [PressureCurve] for why it can't be dropped. */
    val pressureCurve: PressureCurve = PressureCurve.DEFAULT
) : NativeCanvasElement()

/** Keyboard-typed text element with an affine transform. */
data class NativeTextElement(
    val text: String,
    val fontFamily: String,
    val fontSize: Float,
    val color: RnoteNativeColor,
    /** Column-major 2D affine transform: [a, b, c, d, tx, ty] */
    val transform: FloatArray = floatArrayOf(1f,0f,0f,1f,0f,0f),
    override val minX: Float, override val minY: Float,
    override val maxX: Float, override val maxY: Float
) : NativeCanvasElement()

/** Embedded bitmap image (PNG/JPEG decoded from Base64). */
data class NativeBitmapElement(
    val pixels: IntArray,   // ARGB pixels, width × height
    val bmpWidth: Int,
    val bmpHeight: Int,
    /** Column-major 2D affine transform: [a, b, c, d, tx, ty] */
    val transform: FloatArray = floatArrayOf(1f,0f,0f,1f,0f,0f),
    override val minX: Float, override val minY: Float,
    override val maxX: Float, override val maxY: Float
) : NativeCanvasElement()

/** Geometric shape — line, rectangle, ellipse, freehand quadratic. */
sealed class NativeShapeKind
data class LineShape(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : NativeShapeKind()
data class RectShape(val x: Float, val y: Float, val w: Float, val h: Float) : NativeShapeKind()
data class EllipseShape(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : NativeShapeKind()
data class FreehandShape(val points: List<NativeStrokePoint>) : NativeShapeKind()

data class NativeShapeElement(
    val shape: NativeShapeKind,
    val color: RnoteNativeColor,
    val strokeWidth: Float,
    override val minX: Float, override val minY: Float,
    override val maxX: Float, override val maxY: Float
) : NativeCanvasElement()

// ── Document ──────────────────────────────────────────────────────────────────

data class RnoteNativeDocument(
    val pageWidth: Float  = 793.7f,   // A4 at 96 dpi
    val pageHeight: Float = 1122.5f,
    val background: NativeBackgroundConfig = NativeBackgroundConfig(),
    /** Elements in chrono (draw) order. */
    val elements: List<NativeCanvasElement> = emptyList(),
    /** Layout mode from the .rnote file: "infinite", "fixed_size", "continuous_vertical", etc. */
    val layout: String = "",

    // Document extent: Rnote's `document.x/y/width/height`, the area the document actually
    // covers. Not the page format -- an infinite-layout document grows to fit its content
    // and routinely starts at negative coordinates.
    val originX: Float = 0f,
    val originY: Float = 0f,
    val totalWidth: Float = 793.7f,
    val totalHeight: Float = 1122.5f,

    // Format decorations: Rnote's `config.format.border_color` / `show_borders` /
    // `show_origin_indicator`.
    val borderColor: RnoteNativeColor = RnoteNativeColor(0.8706f, 0.8667f, 0.851f, 1f),
    val showBorders: Boolean = true,
    val showOriginIndicator: Boolean = true
)
