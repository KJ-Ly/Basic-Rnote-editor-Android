package com.rnote.baby.model

import androidx.compose.ui.graphics.Color

enum class ToolType {
    PEN,
    HIGHLIGHTER,
    ERASER,
    SELECT
}

enum class BrushSizePreset(
    val label: String,
    val penPx: Float,
    val highlighterPx: Float,
    val eraserPx: Float
) {
    SMALL("S", 2.0f, 12.0f, 16.0f),
    MEDIUM("M", 6.0f, 24.0f, 32.0f),
    LARGE("L", 12.0f, 48.0f, 64.0f);

    fun sizeForTool(tool: ToolType): Float = when (tool) {
        ToolType.PEN -> penPx
        ToolType.HIGHLIGHTER -> highlighterPx
        ToolType.ERASER -> eraserPx
        ToolType.SELECT -> penPx
    }
}

data class ToolConfig(
    val activeTool: ToolType = ToolType.PEN,
    val penColor: Color = Color(0xFF82AAFF), // Rnote vibrant blue default ink on dark background
    val highlighterColor: Color = Color(0xFFFFCB6B).copy(alpha = 0.35f), // Semi-transparent yellow
    val strokeWidth: Float = 6f,          // Pen stroke width in canvas px
    val highlighterWidth: Float = 24f,    // Highlighter width in canvas px
    val eraserWidth: Float = 32f,         // Eraser radius/width in canvas px
    val isPressureSensitive: Boolean = true,
    /** When false (default), only stylus/S-Pen input can draw. Finger touch is reserved for pan & zoom. */
    val allowFingerDrawing: Boolean = false
) {
    /** Gets active tool's stroke size in px. */
    val currentActiveSize: Float
        get() = when (activeTool) {
            ToolType.PEN -> strokeWidth
            ToolType.HIGHLIGHTER -> highlighterWidth
            ToolType.ERASER -> eraserWidth
            ToolType.SELECT -> strokeWidth
        }

    /** Returns a copy with updated active tool stroke size. */
    fun updateActiveSize(newSize: Float): ToolConfig {
        val clamped = newSize.coerceIn(1f, 128f)
        return when (activeTool) {
            ToolType.PEN -> copy(strokeWidth = clamped)
            ToolType.HIGHLIGHTER -> copy(highlighterWidth = clamped)
            ToolType.ERASER -> copy(eraserWidth = clamped)
            ToolType.SELECT -> copy(strokeWidth = clamped)
        }
    }
}
