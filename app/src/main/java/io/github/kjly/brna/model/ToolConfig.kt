package io.github.kjly.brna.model

import androidx.compose.ui.graphics.Color

/**
 * Matches desktop Rnote's six "pens" (Brush, Shaper, Typewriter, Eraser,
 * Selector, Tools) — see penpicker.ui in the flxzt/rnote source. SHAPER,
 * TYPEWRITER, and TOOLS have a UI slot in [io.github.kjly.brna.ui.components.PenPicker]
 * but no implementation yet; selecting them is a no-op. See HANDOFF.md roadmap.
 */
enum class ToolType(val isImplemented: Boolean = true) {
    BRUSH,
    SHAPER(isImplemented = false),
    TYPEWRITER(isImplemented = false),
    ERASER,
    SELECTOR,
    TOOLS(isImplemented = false)
}

/**
 * Desktop Rnote's brush styles. MARKER reproduces what BRNA used to call the
 * "Highlighter" tool — translucent, wide, layered under other strokes — but
 * as a Brush style rather than a separate top-level tool, matching how
 * desktop Rnote (and BRNA's own .rnote writer, via the stroke's chrono
 * "layer") actually represent it. SOLID is the old plain "Pen". TEXTURED has
 * a UI slot but no rendering implementation yet.
 */
enum class BrushStyle {
    MARKER,
    SOLID,
    TEXTURED
}

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
    // Rnote's EraserConfig::WIDTH_DEFAULT is 12.0, which is deliberately not one of the
    // 4/9/24 palette presets — a fresh eraser starts between Small and Medium.
    val eraserWidth: Float = 12f,          // Eraser square side in canvas units
    val isPressureSensitive: Boolean = true,
    /** When false (default), only stylus/S-Pen input can draw. Finger touch is reserved for pan & zoom. */
    val allowFingerDrawing: Boolean = false
) {
    private val isMarker: Boolean get() = activeTool == ToolType.BRUSH && brushStyle == BrushStyle.MARKER

    /** Gets active tool's stroke size in px. */
    val currentActiveSize: Float
        get() = when {
            activeTool == ToolType.ERASER -> eraserWidth
            isMarker -> highlighterWidth
            else -> strokeWidth
        }

    /** The ink color for the active tool/style. */
    val currentActiveColor: Color
        get() = if (isMarker) highlighterColor else penColor

    /** Returns a copy with updated active tool stroke size. */
    fun updateActiveSize(newSize: Float): ToolConfig {
        // Desktop Rnote's per-tool limits: BrushConfig::STROKE_WIDTH_MIN / _MAX are
        // 0.1 / 500, EraserConfig::WIDTH_MIN / _MAX are 1 / 500. The old flat 1f floor
        // sat above the range the spin button steps through for a brush (0.1 below
        // width 12), so the finest widths desktop can express were unreachable here.
        val minWidth = if (activeTool == ToolType.ERASER) 1f else 0.1f
        val clamped = newSize.coerceIn(minWidth, 500f)
        return when {
            activeTool == ToolType.ERASER -> copy(eraserWidth = clamped)
            isMarker -> copy(highlighterWidth = clamped)
            else -> copy(strokeWidth = clamped)
        }
    }
}
