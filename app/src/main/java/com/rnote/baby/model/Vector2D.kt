package com.rnote.baby.model

import androidx.compose.ui.geometry.Offset

data class Vector2D(
    val x: Float,
    val y: Float
) {
    fun toOffset(): Offset = Offset(x, y)

    operator fun plus(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D): Vector2D = Vector2D(x - other.x, y - other.y)
    operator fun times(factor: Float): Vector2D = Vector2D(x * factor, y * factor)
    operator fun div(factor: Float): Vector2D = Vector2D(x / factor, y / factor)

    companion object {
        val Zero = Vector2D(0f, 0f)
        fun fromOffset(offset: Offset): Vector2D = Vector2D(offset.x, offset.y)
    }
}
