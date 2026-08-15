package com.rnote.baby.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.rnote.baby.model.Stroke

object SelectionManager {

    /**
     * Determines which strokes are enclosed or intersected by the lasso polygon points.
     */
    fun findStrokesInLasso(lassoPoints: List<Offset>, strokes: List<Stroke>): List<Stroke> {
        if (lassoPoints.size < 3 || strokes.isEmpty()) return emptyList()

        return strokes.filter { stroke ->
            val strokePoints = stroke.points
            if (strokePoints.isEmpty()) false
            else {
                val insideCount = strokePoints.count { pt ->
                    isPointInPolygon(Offset(pt.x, pt.y), lassoPoints)
                }
                (insideCount.toFloat() / strokePoints.size) >= 0.25f
            }
        }
    }

    /**
     * Calculates the tight bounding box surrounding a list of selected strokes.
     */
    fun calculateBoundingBox(selectedStrokes: List<Stroke>): Rect? {
        if (selectedStrokes.isEmpty()) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (stroke in selectedStrokes) {
            for (pt in stroke.points) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }

        if (minX > maxX || minY > maxY) return null
        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * Moves a list of strokes by delta offset.
     */
    fun translateStrokes(strokes: List<Stroke>, delta: Offset): List<Stroke> {
        return strokes.map { stroke ->
            val newPoints = stroke.points.map { pt ->
                pt.copy(x = pt.x + delta.x, y = pt.y + delta.y)
            }
            stroke.copy(points = newPoints)
        }
    }

    /**
     * Scales a list of strokes relative to a center point.
     */
    fun scaleStrokes(strokes: List<Stroke>, center: Offset, scaleFactor: Float): List<Stroke> {
        return strokes.map { stroke ->
            val newPoints = stroke.points.map { pt ->
                val newX = center.x + (pt.x - center.x) * scaleFactor
                val newY = center.y + (pt.y - center.y) * scaleFactor
                pt.copy(x = newX, y = newY)
            }
            stroke.copy(points = newPoints, strokeWidth = stroke.strokeWidth * scaleFactor)
        }
    }

    /**
     * Ray-casting algorithm to determine if point P is inside polygon.
     */
    private fun isPointInPolygon(p: Offset, polygon: List<Offset>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > p.y) != (pj.y > p.y) &&
                (p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
