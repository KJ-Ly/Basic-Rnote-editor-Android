package com.rnote.baby.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.rnote.baby.model.InkPoint

object InkSmoother {
    /**
     * Converts a list of raw ink points into a smooth vector Path using
     * quadratic Bézier curve interpolation between point midpoints.
     */
    fun createSmoothPath(points: List<InkPoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            path.moveTo(points[0].x, points[0].y)
            path.addOval(
                androidx.compose.ui.geometry.Rect(
                    center = Offset(points[0].x, points[0].y),
                    radius = 1f
                )
            )
            return path
        }

        if (points.size == 2) {
            path.moveTo(points[0].x, points[0].y)
            path.lineTo(points[1].x, points[1].y)
            return path
        }

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size - 1) {
            val current = points[i]
            val next = points[i + 1]
            val midX = (current.x + next.x) / 2f
            val midY = (current.y + next.y) / 2f

            path.quadraticTo(current.x, current.y, midX, midY)
        }

        val last = points.last()
        path.lineTo(last.x, last.y)

        return path
    }
}
