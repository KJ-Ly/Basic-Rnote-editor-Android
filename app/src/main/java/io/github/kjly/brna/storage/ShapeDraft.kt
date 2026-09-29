package io.github.kjly.brna.storage

import androidx.compose.ui.geometry.Offset
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RoughStyle
import io.github.kjly.brna.model.ShapeConstraints
import io.github.kjly.brna.model.ShapeKind
import io.github.kjly.brna.model.ShapeLine

/**
 * Desktop Rnote's shape builders that take more than one stroke of the pen
 * (rnote-compose/src/builders), kept by the canvas between strokes:
 *
 * - polyline and polygon: every stroke ends on a new corner; putting the pen down on the
 *   last corner again and lifting it finishes the shape (`PolylineBuilder`, `PolygonBuilder`);
 * - quadratic curve: a stroke from the start to the control point, a second to the end
 *   (`QuadBezBuilder`); the cubic one has a stroke per control point (`CubBezBuilder`);
 * - foci ellipse: where the first stroke ends is one focus, the second stroke the other,
 *   the third a point the ellipse runs through (`FociEllipseBuilder`).
 *
 * The pen's position comes in already snapped, if Snap Positions is on; the constraints
 * are applied here, the way each Rnote builder applies them.
 */
data class ShapeDraft(
    val kind: ShapeKind,
    /** The points settled by earlier strokes, in the order the shape takes them. */
    val points: List<Offset>,
    /** Where the point being placed is; null while the pen is up between strokes. */
    val current: Offset? = null,
    /** A polyline or polygon: the pen came down on its last corner, so lifting it finishes. */
    val finishing: Boolean = false
) {
    /** The point the next one is measured from, as Rnote's builders constrain it. */
    val anchor: Offset? get() = points.lastOrNull()

    /** The pen came down again, at [pos], to place the next point. */
    fun down(pos: Offset, constraints: ShapeConstraints, finishDistance: Float): ShapeDraft = when (kind) {
        ShapeKind.POLYLINE, ShapeKind.POLYGON -> {
            val last = points.last()
            copy(
                current = constrainedFrom(last, pos, constraints.withAxes()),
                finishing = (pos - last).getDistance() < finishDistance
            )
        }
        ShapeKind.FOCI_ELLIPSE ->
            if (points.isEmpty()) copy(current = pos)
            else copy(current = constrainedFrom(points.last(), pos, constraints.withAxes()))
        else -> copy(current = constrainedFrom(points.last(), pos, constraints.withAxes()))
    }

    /** The pen moved to [pos] while down. */
    fun move(pos: Offset, constraints: ShapeConstraints): ShapeDraft = when {
        // The first focus, and the point on the ellipse once it is being dragged, follow
        // the pen as it is: Rnote only constrains the second focus.
        kind == ShapeKind.FOCI_ELLIPSE && points.size != 1 -> copy(current = pos)
        else -> copy(current = constrainedFrom(points.last(), pos, constraints.withAxes()))
    }

    /**
     * The pen lifted: the draft to carry on with, or null once the shape is done — and
     * then its points, in the order [toShape] takes them.
     */
    fun up(): Lifted {
        val placed = current ?: return Lifted(copy(finishing = false), null)
        return when (kind) {
            ShapeKind.POLYLINE, ShapeKind.POLYGON -> when {
                finishing -> Lifted(null, points)
                // A tap on the corner just placed adds nothing, rather than a line of no length.
                (placed - points.last()).getDistance() < SAME_POINT -> Lifted(copy(current = null), null)
                else -> Lifted(copy(points = points + placed, current = null), null)
            }
            else -> {
                val all = points + placed
                if (all.size == pointsNeeded(kind)) Lifted(null, all) else Lifted(copy(points = all, current = null), null)
            }
        }
    }

    /** What lifting the pen made of a draft. */
    data class Lifted(val draft: ShapeDraft?, val finished: List<Offset>?)

    companion object {

        /** Rnote's `FINISH_THRESHOLD_DIST` for polylines and polygons, in document units. */
        const val FINISH_DISTANCE = 8f

        /** Closer than this, two points are the same point. */
        private const val SAME_POINT = 0.5f

        fun isMultiStroke(kind: ShapeKind) = kind == ShapeKind.POLYLINE || kind == ShapeKind.POLYGON ||
            kind == ShapeKind.QUADBEZ || kind == ShapeKind.CUBBEZ || kind == ShapeKind.FOCI_ELLIPSE

        /**
         * A draft started by the pen coming down at [pos]: there the polyline, polygon or
         * curve starts; the foci ellipse's first focus is wherever that stroke ends.
         */
        fun start(kind: ShapeKind, pos: Offset): ShapeDraft =
            if (kind == ShapeKind.FOCI_ELLIPSE) ShapeDraft(kind, emptyList(), current = pos)
            else ShapeDraft(kind, listOf(pos), current = pos)

        /**
         * The finished shape through [points], as desktop Rnote writes it; null when there
         * is nothing to draw — too few corners, or every point in the same spot.
         */
        fun toShape(
            kind: ShapeKind,
            points: List<Offset>,
            color: RnoteNativeColor,
            strokeWidth: Float,
            fill: RnoteNativeColor,
            line: ShapeLine = ShapeLine(),
            rough: RoughStyle? = null
        ): NativeShapeElement? {
            if (points.isEmpty()) return null
            val spread = maxOf(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
            if (spread < 1f) return null
            val pairs = points.map { it.x to it.y }
            return when (kind) {
                ShapeKind.POLYLINE -> NativeEditing.createPolyShape(false, pairs, color, strokeWidth, fill, line, rough)
                ShapeKind.POLYGON -> NativeEditing.createPolyShape(true, pairs, color, strokeWidth, fill, line, rough)
                ShapeKind.QUADBEZ, ShapeKind.CUBBEZ -> NativeEditing.createCurve(pairs, color, strokeWidth, fill, line, rough)
                ShapeKind.FOCI_ELLIPSE -> if (points.size == 3) {
                    // Foci on top of each other make a circle; Rnote allows that, and so does this.
                    NativeEditing.createFociEllipse(
                        points[0].x, points[0].y, points[1].x, points[1].y, points[2].x, points[2].y,
                        color, strokeWidth, fill, line, rough
                    )
                } else null
                else -> null
            }
        }

        /** How many points [kind] is made of: start, control point(s) and end, or two foci and a point. */
        private fun pointsNeeded(kind: ShapeKind): Int = when (kind) {
            ShapeKind.QUADBEZ, ShapeKind.FOCI_ELLIPSE -> 3
            ShapeKind.CUBBEZ -> 4
            else -> Int.MAX_VALUE
        }

        private fun constrainedFrom(from: Offset, pos: Offset, constraints: ShapeConstraints): Offset =
            from + constraints.constrain(pos - from)
    }
}
