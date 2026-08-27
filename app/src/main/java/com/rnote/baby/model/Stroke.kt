package com.rnote.baby.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1f)

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val color: Color,
    val strokeWidth: Float = 3f,
    val toolType: ToolType = ToolType.BRUSH,
    val isHighlighter: Boolean = false,
    val alpha: Float = 1.0f
) {
    // Legacy alias so existing DrawingCanvas references to stroke.width still compile
    val width: Float get() = strokeWidth
}
