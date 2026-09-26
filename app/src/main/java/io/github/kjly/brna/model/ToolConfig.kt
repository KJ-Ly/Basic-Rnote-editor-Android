package io.github.kjly.brna.model

import androidx.compose.ui.graphics.Color

/**
 * Matches desktop Rnote's six "pens" (Brush, Shaper, Typewriter, Eraser,
 * Selector, Tools) — see penpicker.ui in the flxzt/rnote source. TOOLS is Rnote's
 * vertical space tool (see [io.github.kjly.brna.ui.canvas.VerticalSpace]).
 */
enum class ToolType(val isImplemented: Boolean = true) {
    BRUSH,
    SHAPER,
    TYPEWRITER,
    ERASER,
    SELECTOR,
    TOOLS;

    /** Rnote's name for the pen, as its `PenStyle` is written in its settings. */
    val apiName: String get() = name.lowercase()

    /** The pen's name in Rnote's menus: "Brush", "Shaper", … */
    val displayName: String get() = name.lowercase().replaceFirstChar { it.uppercaseChar() }

    companion object {
        fun fromApiName(name: String): ToolType? = entries.firstOrNull { it.apiName == name }
    }
}

/**
 * Desktop Rnote's two eraser styles: TRASH removes every stroke the eraser touches,
 * SPLIT cuts out only the part of a brush stroke under it and keeps the rest.
 */
enum class EraserMode { TRASH, SPLIT }

/**
 * The shapes the Shaper draws, from desktop Rnote's shape picker. COORD_SYSTEM_2D to GRID
 * are built from several lines, as Rnote builds them (see storage.ShapeBuilders); GRID
 * takes two drags there too, the first for one cell and the second for how far to repeat
 * it. The last five take several strokes of the pen each, as Rnote's own builders do
 * (see storage.ShapeDraft): a polyline and a polygon a stroke per corner, the curves a
 * stroke per control point, the foci ellipse one per focus and one for a point on it.
 */
enum class ShapeKind {
    LINE, ARROW, RECTANGLE, ELLIPSE, COORD_SYSTEM_2D, COORD_SYSTEM_3D, QUADRANT, GRID,
    POLYLINE, POLYGON, QUADBEZ, CUBBEZ, FOCI_ELLIPSE
}

/** Rnote's `TextAlignment`, with the name its files use. */
enum class TextAlignment(val apiName: String) {
    START("start"), CENTER("center"), END("end"), FILL("fill");

    companion object {
        fun of(apiName: String): TextAlignment = entries.firstOrNull { it.apiName == apiName } ?: START
    }
}

/**
 * Desktop Rnote's selector styles (`SelectorStyle`): draw round what to take, drag a
 * rectangle over it, tap one thing, or draw a line through everything to take.
 */
enum class SelectorMode { POLYGON, RECTANGLE, SINGLE, INTERSECTING_PATH }

/**
 * Desktop Rnote's Tools pen styles (`ToolStyle`) that this app has: Vertical Space, and
 * the Laser — a red trail to point with that fades away and is never saved. Rnote's
 * other two, Offset Camera and Zoom, are what pan and pinch already do on a tablet.
 */
/** Rnote's `ToolStyle`s for the Tools pen, in its order. */
enum class ToolsMode { VERTICAL_SPACE, OFFSET_CAMERA, ZOOM, LASER }

/**
 * Desktop Rnote's brush styles. MARKER reproduces what BRNA used to call the
 * "Highlighter" tool — translucent, wide, layered under other strokes — but
 * as a Brush style rather than a separate top-level tool, matching how
 * desktop Rnote (and BRNA's own .rnote writer, via the stroke's chrono
 * "layer") actually represent it. SOLID is the old plain "Pen". TEXTURED draws
 * Rnote's dots, the same dots Rnote draws from the stroke's seed (see TexturedDots).
 */
enum class BrushStyle {
    MARKER,
    SOLID,
    TEXTURED
}

/**
 * Rnote's `PenPathBuilderType` for the brush ("Path Modelling" in its settings), in its
 * order: SIMPLE draws through the pen's samples as they come; CURVED through cubic curves
 * between them (see render.CurvedPathBuilder); MODELED — Rnote's default — through what
 * its stroke modeler makes of them (see render.ModeledPathBuilder).
 */
enum class PenPathBuilder { SIMPLE, CURVED, MODELED }

/**
 * Matches desktop Rnote's RnStrokeWidthPicker gschema defaults exactly:
 * brush/shaper 2.0/6.0/12.0, eraser 4.0/9.0/24.0. Real Rnote's width picker
 * doesn't have a separate scale for the Marker brush style — Solid and
 * Marker share the same three presets.
 */
enum class BrushSizePreset(
    val label: String,
    val brushSolidPx: Float,
    val brushMarkerPx: Float,
    val eraserPx: Float
) {
    SMALL("S", 2.0f, 2.0f, 4.0f),
    MEDIUM("M", 6.0f, 6.0f, 9.0f),
    LARGE("L", 12.0f, 12.0f, 24.0f);

    fun sizeForTool(tool: ToolType, brushStyle: BrushStyle): Float = when (tool) {
        ToolType.BRUSH -> if (brushStyle == BrushStyle.MARKER) brushMarkerPx else brushSolidPx
        ToolType.ERASER -> eraserPx
        else -> brushSolidPx
    }
}

data class ToolConfig(
    val activeTool: ToolType = ToolType.BRUSH,
    val brushStyle: BrushStyle = BrushStyle.SOLID,
    val penColor: Color = Color.Black, // matches Rnote's actual default (gschema active-stroke-color: black)
    val highlighterColor: Color = Color(0xFFF6D32D).copy(alpha = 0.35f), // Semi-transparent yellow, used by Brush/Marker
    val strokeWidth: Float = 2f,           // Brush (Solid) stroke width in canvas px — matches Rnote's default (SmoothOptions stroke_width: 2.0)
    val highlighterWidth: Float = 12f,     // Brush (Marker) width in canvas px — matches Rnote's MarkerOptions fixed default
    /** The Textured brush's own width, density and distribution: Rnote's `TexturedOptions`. */
    val texturedWidth: Float = TexturedStyle.WIDTH_DEFAULT,
    val texturedDensity: Double = TexturedStyle.DENSITY_DEFAULT,
    val texturedDistribution: TexturedDistribution = TexturedDistribution.DEFAULT,
    // Rnote's EraserConfig::WIDTH_DEFAULT is 12.0, which is deliberately not one of the
    // 4/9/24 palette presets — a fresh eraser starts between Small and Medium.
    val eraserWidth: Float = 12f,          // Eraser square side in canvas units
    /** How the brush makes a stroke of the pen's samples; Rnote's default is modeled. */
    val penPathBuilder: PenPathBuilder = PenPathBuilder.MODELED,
    /**
     * The Solid brush's pressure curve: Rnote's `SolidOptions::pressure_curve`, which its
     * brush settings let you pick and which starts linear.
     */
    val pressureCurve: PressureCurve = PressureCurve.LINEAR,
    /** The Shaper's current shape and width; Rnote's shaper defaults to a 2.0 line. */
    val shapeKind: ShapeKind = ShapeKind.LINE,
    val shaperWidth: Float = 2f,
    /** Turns lines and arrows to the nearest 15°: level, upright and the set-square angles. */
    val snapAngles: Boolean = false,
    val eraserMode: EraserMode = EraserMode.TRASH,
    /** The selector's "Lock Aspect Ratio": scale the selection uniformly. Off in Rnote by default. */
    val lockAspectRatio: Boolean = false,
    val selectorMode: SelectorMode = SelectorMode.POLYGON,
    val toolsMode: ToolsMode = ToolsMode.VERTICAL_SPACE,
    /**
     * The colour picker's second pad: what new shapes are filled with. Transparent — no
     * fill — until one is picked, as in Rnote, whose pens start without a fill colour.
     */
    val fillColor: Color = Color.Transparent,
    /** Typewriter font size; Rnote's `TextStyle::FONT_SIZE_DEFAULT` is 32. */
    val textSize: Float = 32f,
    /** How new text is aligned; Rnote's typewriter starts at the start. */
    val textAlignment: TextAlignment = TextAlignment.START,
    /** The Shaper's constraints (1:1, level, upright …), off by default as in Rnote. */
    val shapeConstraints: ShapeConstraints = ShapeConstraints(),
    /** The Shaper's line style and line cap, as Rnote's shaper settings pick them. */
    val shapeLine: ShapeLine = ShapeLine(),
    /** The Shaper's style: Rnote's smooth outlines, or its rough, sketched look. */
    val shaperStyle: ShaperStyle = ShaperStyle.SMOOTH,
    /** The rough style's fill, and the angle of its hatching in whole degrees, as Rnote's settings take it. */
    val roughFill: RoughFillStyle = RoughFillStyle.DEFAULT,
    val roughHachureDegrees: Int = RoughStyle.HACHURE_DEGREES_DEFAULT,
    /**
     * Rnote's "Snap Positions": shapes, moved selections, new text and vertical space go
     * to the page's pattern. Off by default, as in Rnote; kept in the settings.
     */
    val snapPositions: Boolean = false,
    /**
     * Rnote's "Block Pinch to Zoom": two fingers still move the page, but no longer zoom
     * it — so a hand resting on the screen can't. Off by default, as in Rnote.
     */
    val blockPinchZoom: Boolean = false,
    /**
     * Rnote's "Respect Borders When Pasting": an inserted image is shrunk until it stays
     * clear of the next page border to the right and below. Off by default, as in Rnote.
     */
    val respectBorders: Boolean = false,
    /** When false (default), only stylus/S-Pen input can draw. Finger touch is reserved for pan & zoom. */
    val allowFingerDrawing: Boolean = false
) {
    private val isMarker: Boolean get() = activeTool == ToolType.BRUSH && brushStyle == BrushStyle.MARKER
    private val isTextured: Boolean get() = activeTool == ToolType.BRUSH && brushStyle == BrushStyle.TEXTURED

    /** Gets active tool's stroke size in px. */
    val currentActiveSize: Float
        get() = when {
            activeTool == ToolType.ERASER -> eraserWidth
            activeTool == ToolType.SHAPER -> shaperWidth
            activeTool == ToolType.TYPEWRITER -> textSize
            isMarker -> highlighterWidth
            isTextured -> texturedWidth
            else -> strokeWidth
        }

    /**
     * The curve a new brush stroke carries, as Rnote's `pensconfig/brushconfig.rs` sets
     * it: the marker's is pinned to Const — a constant-width nib — the textured brush
     * keeps its options' default, linear, and the solid brush's is [pressureCurve].
     */
    val strokePressureCurve: PressureCurve
        get() = when (brushStyle) {
            BrushStyle.MARKER -> PressureCurve.CONST
            BrushStyle.TEXTURED -> PressureCurve.LINEAR
            BrushStyle.SOLID -> pressureCurve
        }

    /**
     * The textured style a new brush stroke takes with [seed] — Rnote gives every stroke a
     * seed of its own — or null when the brush is not textured.
     */
    fun strokeTextured(seed: Long): TexturedStyle? =
        if (brushStyle == BrushStyle.TEXTURED) TexturedStyle(seed, texturedDensity, texturedDistribution) else null

    /**
     * The rough style a new shape takes with [seed] — Rnote picks one each time the pen
     * goes down — or null when the Shaper draws smooth shapes.
     */
    fun shapeRough(seed: Long): RoughStyle? =
        if (shaperStyle == ShaperStyle.ROUGH) RoughStyle(roughFill, RoughStyle.hachureAngleOf(roughHachureDegrees), seed) else null

    /** The ink color for the active tool/style. */
    val currentActiveColor: Color
        get() = if (isMarker) highlighterColor else penColor

    /** Returns a copy with updated active tool stroke size. */
    fun updateActiveSize(newSize: Float): ToolConfig {
        // Desktop Rnote's per-tool limits: BrushConfig::STROKE_WIDTH_MIN / _MAX are
        // 0.1 / 500, EraserConfig::WIDTH_MIN / _MAX are 1 / 500. The old flat 1f floor
        // sat above the range the spin button steps through for a brush (0.1 below
        // width 12), so the finest widths desktop can express were unreachable here.
        if (activeTool == ToolType.TYPEWRITER) {
            // Rnote's TextStyle::FONT_SIZE_MIN / _MAX.
            return copy(textSize = newSize.coerceIn(1f, 512f))
        }
        val minWidth = if (activeTool == ToolType.ERASER) 1f else 0.1f
        val clamped = newSize.coerceIn(minWidth, 500f)
        return when {
            activeTool == ToolType.ERASER -> copy(eraserWidth = clamped)
            activeTool == ToolType.SHAPER -> copy(shaperWidth = clamped)
            isMarker -> copy(highlighterWidth = clamped)
            isTextured -> copy(texturedWidth = clamped)
            else -> copy(strokeWidth = clamped)
        }
    }
}
