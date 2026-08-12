package com.rnote.baby.model

import androidx.compose.ui.geometry.Offset

data class ViewportState(
    val panOffset: Offset = Offset.Zero,
    val zoomScale: Float = 1.0f
) {
    /**
     * Converts a screen pixel offset into document canvas space coordinates.
     */
    fun screenToCanvas(screenOffset: Offset): Offset {
        return (screenOffset - panOffset) / zoomScale
    }

    /**
     * Converts document canvas space coordinates into screen pixel offset.
     */
    fun canvasToScreen(canvasOffset: Offset): Offset {
        return (canvasOffset * zoomScale) + panOffset
    }

    /**
     * Clamps and returns a new ViewportState with updated zoom and pan.
     */
    fun update(newPan: Offset, newZoom: Float): ViewportState {
        val clampedZoom = newZoom.coerceIn(0.25f, 5.0f)
        return ViewportState(panOffset = newPan, zoomScale = clampedZoom)
    }
}
