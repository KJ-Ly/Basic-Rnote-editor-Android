package com.rnote.baby.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class StrokePoint(val x: Float, val y: Float, val pressure: Float = PRESSURE_DEFAULT) {
    companion object {
        /**
         * Rnote's `Element::PRESSURE_DEFAULT` — what it records when the input device
         * reports no pressure of its own. Desktop mouse strokes are written entirely at
         * this value, so matching it is what makes a finger-drawn stroke here the same
         * size as a mouse-drawn one there.
         */
        const val PRESSURE_DEFAULT = 0.5f
    }
}

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    /**
     * Ink colour, alpha included — the sole place a stroke's opacity lives, matching
     * .rnote, which carries it in `stroke_color`. There was a separate `alpha` field
     * alongside this; because a Marker's colour already carries its 0.35, every renderer
     * that multiplied the two drew markers at 0.12.
     */
    val color: Color,
    /**
     * The stroke's nominal width, i.e. Rnote's `SmoothOptions::stroke_width`. This is a
     * maximum, not the painted thickness: [pressureCurve] and each point's pressure
     * decide that. Never bake pressure into this value — the .rnote file carries the
     * per-point pressures too, so desktop Rnote would apply it a second time.
     */
    val strokeWidth: Float = 3f,
    val toolType: ToolType = ToolType.BRUSH,
    val isHighlighter: Boolean = false,
    /** Rnote's default for a Solid brush; its Marker brush uses [PressureCurve.CONST]. */
    val pressureCurve: PressureCurve = PressureCurve.DEFAULT
) {
    // Legacy alias so existing DrawingCanvas references to stroke.width still compile
    val width: Float get() = strokeWidth
}
