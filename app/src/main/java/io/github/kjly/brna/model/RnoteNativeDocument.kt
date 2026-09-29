package io.github.kjly.brna.model

import androidx.compose.ui.graphics.Color

// ── Color ─────────────────────────────────────────────────────────────────────

data class RnoteNativeColor(val r: Float, val g: Float, val b: Float, val a: Float) {
    fun toComposeColor() = Color(r.coerceIn(0f,1f), g.coerceIn(0f,1f), b.coerceIn(0f,1f), a.coerceIn(0f,1f))
    companion object {
        val BLACK = RnoteNativeColor(0f, 0f, 0f, 1f)
        val WHITE = RnoteNativeColor(1f, 1f, 1f, 1f)
        /** Rnote's "no fill" — the value its own writer puts on an unfilled shape. */
        val TRANSPARENT = RnoteNativeColor(0f, 0f, 0f, 0f)
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
data class NativeStrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    /** The curve of the segment ending here; see [SegmentCurve]. */
    val curve: SegmentCurve? = null
)

/** Freehand pen or highlighter stroke. */
data class NativeBrushStroke(
    val points: List<NativeStrokePoint>,
    val strokeWidth: Float,
    val color: RnoteNativeColor,
    val isHighlighter: Boolean,
    override val minX: Float, override val minY: Float,
    override val maxX: Float, override val maxY: Float,
    /** The style's `pressure_curve`; see [PressureCurve] for why it can't be dropped. */
    val pressureCurve: PressureCurve = PressureCurve.DEFAULT,
    /** The seed and dots of a stroke in Rnote's `textured` style; null for `smooth`. */
    val textured: TexturedStyle? = null,
    /** The value of its `brushstroke` exactly as the file had it, written back while the stroke is unchanged. */
    val raw: String? = null
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
    override val maxX: Float, override val maxY: Float,
    /** Wrap width in document units; null means the text only breaks at newlines. */
    val maxWidth: Float? = null,
    /** CSS-style weight, 100-900; Rnote's default is 500. */
    val fontWeight: Int = 500,
    val italic: Boolean = false,
    /** Rnote's `TextAlignment`: "start", "center", "end" or "fill". */
    val alignment: String = "start",
    /** Bold, italic, underlined … stretches over the box's own style: Rnote's `ranged_text_attributes`. */
    val ranges: List<RangedTextAttr> = emptyList(),
    /**
     * The element exactly as the file had it. A save writes this back, and editing the
     * text changes it in place (NativeEditing.withText) — so bold ranges, underlines and
     * anything else the model here has no field for survive the round trip.
     */
    val raw: com.google.gson.JsonElement? = null
) : NativeCanvasElement()

/**
 * Embedded bitmap image.
 *
 * Two shapes of it exist. Older files carry an encoded PNG/JPEG, decoded into [pixels].
 * Rnote 0.14 writes raw premultiplied RGBA ([rgbaBase64]) placed by a transformed
 * rectangle ([rect]), like a vector image; that data is only decoded when drawn, since a
 * photo or a bitmap-imported PDF page is tens of megabytes of pixels.
 */
data class NativeBitmapElement(
    val pixels: IntArray,   // ARGB pixels, width × height; empty for the 0.14 form
    val bmpWidth: Int,
    val bmpHeight: Int,
    /** Column-major 2D affine transform: [a, b, c, d, tx, ty] */
    val transform: FloatArray = floatArrayOf(1f,0f,0f,1f,0f,0f),
    override val minX: Float, override val minY: Float,
    override val maxX: Float, override val maxY: Float,
    /** Rnote 0.14: base64 of bmpWidth × bmpHeight × 4 bytes of premultiplied RGBA. */
    val rgbaBase64: String? = null,
    /** Rnote 0.14: where the image sits, as half-extents about a transformed centre. */
    val rect: RectShape? = null,
    /** The chrono layer it came from; Rnote puts images on "image". */
    val layer: String = "image",
    /** The element exactly as the file had it, written back untouched on save. */
    val raw: com.google.gson.JsonElement? = null
) : NativeCanvasElement()

/**
 * Rnote's `VectorImage` — what desktop Rnote makes of an imported PDF page or SVG. The
 * SVG is kept verbatim so a save writes it back untouched; it is stretched over the
 * rectangle [-halfExtentX, halfExtentX] x [-halfExtentY, halfExtentY], which [transform]
 * then places in the document (Rnote's `VectorImage::gen_svg`).
 */
class NativeVectorImageElement(
    val svgData: String,
    val intrinsicWidth: Float,
    val intrinsicHeight: Float,
    val halfExtentX: Float,
    val halfExtentY: Float,
    /** Column-major 2D affine, as [NativeTextElement.transform]. Centres the rectangle. */
    val transform: FloatArray,
    /** The chrono layer it came from: "document" for PDF pages, "image" otherwise. */
    val layer: String,
    override val minX: Float, override val minY: Float,
    override val maxX: Float, override val maxY: Float
) : NativeCanvasElement()

/**
 * Geometric shape, held the way Rnote holds it (`rnote-compose/src/shapes`) rather than
 * as an axis-aligned box of our own: a rect is half-extents about a *transformed* centre,
 * an ellipse is radii about one. Storing them as a plain x/y/w/h — which is what this
 * app used to do — cannot hold a shape the desktop rotated, and re-saving would have
 * silently squared it up.
 *
 * There is no freehand kind: older files have one, and it is read as the brush stroke it
 * effectively is, so that everything held here is a shape this app can also write back.
 */
sealed class NativeShapeKind

/** A line carries its endpoints outright, with no transform of its own. */
data class LineShape(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : NativeShapeKind()

data class RectShape(
    val halfExtentX: Float,
    val halfExtentY: Float,
    /** Column-major 2D affine, as [NativeTextElement.transform]. Centres the rect. */
    val transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
) : NativeShapeKind()

/**
 * One step of a [PathShape] outline, in document coordinates. Covers the shapes the
 * model has no dedicated class for — arrows, curves, polylines, polygons — which only
 * ever need drawing, never editing.
 */
sealed class PathOp {
    data class MoveTo(val x: Float, val y: Float) : PathOp()
    data class LineTo(val x: Float, val y: Float) : PathOp()
    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : PathOp()
    data class CubicTo(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float
    ) : PathOp()
    object Close : PathOp()
}

/** Rnote's arrow, curve, polyline and polygon shapes, kept as their outline. */
data class PathShape(val ops: List<PathOp>) : NativeShapeKind()

data class EllipseShape(
    val radiusX: Float,
    val radiusY: Float,
    /** Column-major 2D affine, as [NativeTextElement.transform]. Centres the ellipse. */
    val transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
) : NativeShapeKind()

data class NativeShapeElement(
    val shape: NativeShapeKind,
    val color: RnoteNativeColor,
    val strokeWidth: Float,
    override val minX: Float, override val minY: Float,
    override val maxX: Float, override val maxY: Float,
    /** A shape can be filled; a brush stroke can't. Dropping this emptied filled shapes. */
    val fillColor: RnoteNativeColor = RnoteNativeColor.TRANSPARENT,
    /** Rnote's `line_style`: "solid", "dotted", "dashed_narrow", "dashed_equidistant", "dashed_wide". */
    val lineStyle: String = "solid",
    /** Rnote's `line_cap`: true for "rounded". */
    val roundCap: Boolean = false,
    /** Rnote's rough style, when the shape has it rather than the smooth one. */
    val rough: RoughStyle? = null,
    /** The element exactly as the file had it, written back untouched on save. */
    val raw: com.google.gson.JsonElement? = null
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
