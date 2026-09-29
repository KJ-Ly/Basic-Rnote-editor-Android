package io.github.kjly.brna.storage

import io.github.kjly.brna.model.ShapeKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Shaper's shapes that desktop Rnote builds out of several lines
 * (rnote-compose/src/builders): the coordinate systems and the grid. Rnote inserts each
 * line as a shape stroke of its own, so these only work out where the lines go, and
 * [NativeEditing.createShape] turns every one into an ordinary line.
 */
object ShapeBuilders {

    /** A straight line from ([x1], [y1]) to ([x2], [y2]). */
    data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    /** Rnote's `GridBuilder::FIRST_CELL_DIMENSIONS_MIN`: a smaller first cell cancels the grid. */
    const val GRID_CELL_MIN = 2f

    /** Rnote's `GridBuilder::CELL_GRID_DIMENSIONS_MAX`, per direction. */
    const val GRID_CELLS_MAX = 100

    /** The step [snapAngle] turns lines to. */
    const val SNAP_DEGREES = 15.0

    /** Whether [kind] is made of several lines by this object rather than one shape. */
    fun isMultiLine(kind: ShapeKind) = kind == ShapeKind.COORD_SYSTEM_2D ||
        kind == ShapeKind.COORD_SYSTEM_3D || kind == ShapeKind.QUADRANT || kind == ShapeKind.GRID

    /**
     * The axes of a coordinate system dragged from ([startX], [startY]) — the tip of the
     * vertical axis, where the pen went down — to ([endX], [endY]), the tip of the
     * horizontal one. The origin is below the first and level with the second, as in
     * Rnote's `CoordSystem2DBuilder`, `CoordSystem3DBuilder` and
     * `QuadrantCoordSystem2DBuilder`. Empty for any other [kind].
     */
    fun axes(kind: ShapeKind, startX: Float, startY: Float, endX: Float, endY: Float): List<Segment> {
        val cx = startX
        val cy = endY
        return when (kind) {
            ShapeKind.QUADRANT -> listOf(
                Segment(cx, cy, startX, startY),
                Segment(cx, cy, endX, endY)
            )
            ShapeKind.COORD_SYSTEM_2D -> listOf(
                Segment(cx, cy, startX, startY),
                Segment(cx, cy, cx, 2f * endY - startY),
                Segment(cx, cy, endX, endY),
                Segment(cx, cy, 2f * startX - endX, cy)
            )
            ShapeKind.COORD_SYSTEM_3D -> {
                // The third axis runs diagonally, a quarter of the other two's length.
                val d = (hypot(cx - startX, cy - startY) + hypot(cx - endX, cy - endY)) / 4f
                listOf(
                    Segment(cx, cy, startX, startY),
                    Segment(cx, cy, cx, 2f * endY - startY),
                    Segment(cx, cy, endX, endY),
                    Segment(cx, cy, 2f * startX - endX, cy),
                    Segment(cx, cy, cx - d, cy + d),
                    Segment(cx, cy, cx + d, cy - d)
                )
            }
            else -> emptyList()
        }
    }

    /**
     * Rnote's `GridBuilder` once its first cell is drawn: cells of [cellW] × [cellH] from
     * ([startX], [startY]), as many whole ones as fit up to ([endX], [endY]), at most
     * [GRID_CELLS_MAX] each way. Every cell edge is a line of its own, in Rnote's order:
     * the top row's upper edges, the left column's left edges, then each cell's right and
     * bottom edge. Empty when the pen went the other way from the first cell.
     */
    fun grid(
        startX: Float, startY: Float,
        cellW: Float, cellH: Float,
        endX: Float, endY: Float
    ): List<Segment> {
        if (cellW == 0f || cellH == 0f) return emptyList()
        val colsF = (endX - startX) / cellW
        val rowsF = (endY - startY) / cellH
        if (colsF < 0f || rowsF < 0f) return emptyList()
        val cols = floor(colsF).toInt().coerceAtMost(GRID_CELLS_MAX)
        val rows = floor(rowsF).toInt().coerceAtMost(GRID_CELLS_MAX)
        val lines = ArrayList<Segment>(cols + rows + 2 * cols * rows)
        for (col in 0 until cols) {
            lines += Segment(startX + cellW * col, startY, startX + cellW * (col + 1), startY)
        }
        for (row in 0 until rows) {
            lines += Segment(startX, startY + cellH * row, startX, startY + cellH * (row + 1))
        }
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = startX + cellW * col
                val y = startY + cellH * row
                lines += Segment(x + cellW, y, x + cellW, y + cellH)
                lines += Segment(x, y + cellH, x + cellW, y + cellH)
            }
        }
        return lines
    }

    /** The outline of the grid's first cell while it is being drawn, as Rnote shows it. */
    fun cellOutline(x1: Float, y1: Float, x2: Float, y2: Float): List<Segment> = listOf(
        Segment(x1, y1, x2, y1),
        Segment(x2, y1, x2, y2),
        Segment(x2, y2, x1, y2),
        Segment(x1, y2, x1, y1)
    )

    /**
     * ([endX], [endY]) turned about ([startX], [startY]) to the nearest multiple of
     * [SNAP_DEGREES], its distance kept: straight, level and the usual set-square angles.
     */
    fun snapAngle(startX: Float, startY: Float, endX: Float, endY: Float): Pair<Float, Float> {
        val dx = (endX - startX).toDouble()
        val dy = (endY - startY).toDouble()
        val length = hypot(dx, dy)
        if (length == 0.0) return endX to endY
        val step = Math.toRadians(SNAP_DEGREES)
        val angle = (atan2(dy, dx) / step).roundToInt() * step
        return (startX + length * cos(angle)).toFloat() to (startY + length * sin(angle)).toFloat()
    }
}
