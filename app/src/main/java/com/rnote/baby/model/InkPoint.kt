package com.rnote.baby.model

/**
 * Legacy alias — new code should use [StrokePoint] directly.
 * Kept so existing DrawingCanvas, SelectionManager, etc. compile unchanged.
 */
typealias InkPoint = StrokePoint

/** Extension to keep old call-sites that reference timestamp (always returns 0). */
val StrokePoint.timestamp: Long get() = 0L
fun StrokePoint.toVector2D() = Vector2D(x, y)
