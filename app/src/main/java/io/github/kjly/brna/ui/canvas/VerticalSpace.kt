package io.github.kjly.brna.ui.canvas

import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.Stroke
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * Desktop Rnote's vertical space tool, the Tools pen's default style
 * (rnote-engine/src/pens/tools/verticalspace.rs): put the pen down at a height and drag,
 * and everything reaching down past that height moves with it — down to make room for
 * something forgotten, up to close a gap again.
 */
object VerticalSpace {

    /** Rnote's `SNAP_START_POS_DIST`: within this of where it started, nothing moves. */
    const val SNAP_DISTANCE = 10f

    /** Rnote's `Y_OFFSET_THRESHOLD`: a move smaller than this is no move. */
    const val MIN_OFFSET = 0.1f

    /**
     * What a drag can take along: the box Rnote's `keys_between` looks in. Everything
     * whose bounds touch it moves — from the height the pen went down at to the bottom of
     * the document, unless one of the two limits of the Tools pen (`VerticalSpaceToolConfig`)
     * narrows it. Unbounded sides are infinite.
     */
    class Region(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
        /** Whether bounds [left], [top], [right], [bottom] touch the box, as Rnote's `intersects` counts touching. */
        fun touches(left: Float, top: Float, right: Float, bottom: Float): Boolean =
            right >= minX && left <= maxX && bottom >= minY && top <= maxY
    }

    /**
     * The box for a drag started at ([x], [y]) on pages [pageWidth] by [pageHeight]. With
     * [limitVerticalBorders] it is only the page column the pen went down in — what lies
     * left or right of that page stays. With [limitHorizontalBorders] it stops at the next
     * page border below the pen — the pages after that stay. A page with no size (an
     * infinite one) has no borders to limit to, so that limit does nothing there.
     */
    fun region(
        x: Float,
        y: Float,
        pageWidth: Float,
        pageHeight: Float,
        limitVerticalBorders: Boolean,
        limitHorizontalBorders: Boolean
    ): Region {
        var minX = Float.NEGATIVE_INFINITY
        var maxX = Float.POSITIVE_INFINITY
        var maxY = Float.POSITIVE_INFINITY
        if (limitVerticalBorders && pageWidth > 0f) {
            val column = floor(x / pageWidth)
            minX = column * pageWidth
            maxX = (column + 1f) * pageWidth
        }
        if (limitHorizontalBorders && pageHeight > 0f) {
            maxY = (floor(y / pageHeight) + 1f) * pageHeight
        }
        return Region(minX, maxX, y, maxY)
    }

    /** Everything reaching [y] or further down, with no limit: the tool as Rnote has it by default. */
    fun below(y: Float): Region = Region(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, y, Float.POSITIVE_INFINITY)

    /**
     * Ids of the strokes that move for a drag started at [y]: every stroke whose outline
     * reaches [y] or further down. Rnote's `keys_between` takes whatever its bounds touch,
     * so a stroke crossing the line moves whole rather than being left behind or cut.
     */
    fun strokesBelow(strokes: List<Stroke>, y: Float): Set<String> = strokesIn(strokes, below(y))

    /** The strokes whose outline touches [region]; a stroke half in it moves whole. */
    fun strokesIn(strokes: List<Stroke>, region: Region): Set<String> =
        strokes.filter { s ->
            if (s.points.isEmpty()) return@filter false
            val half = s.strokeWidth / 2f
            region.touches(
                s.points.minOf { it.x } - half, s.points.minOf { it.y } - half,
                s.points.maxOf { it.x } + half, s.points.maxOf { it.y } + half
            )
        }.mapTo(HashSet()) { it.id }

    /**
     * The desktop elements — text, shapes, images, PDF pages — that move by the same rule,
     * told apart by identity as the document does. Brush strokes are left to [strokesBelow].
     */
    fun nativesBelow(elements: List<NativeCanvasElement>, y: Float): Set<NativeCanvasElement> =
        nativesIn(elements, below(y))

    /** The desktop elements whose bounds touch [region]. */
    fun nativesIn(elements: List<NativeCanvasElement>, region: Region): Set<NativeCanvasElement> {
        val inside = Collections.newSetFromMap(IdentityHashMap<NativeCanvasElement, Boolean>())
        elements.filterTo(inside) {
            it !is NativeBrushStroke && region.touches(it.minX, it.minY, it.maxX, it.maxY)
        }
        return inside
    }

    /**
     * How far everything has moved with the pen at [pointerY], for a drag started at
     * [startY]. With Snap Positions on, [snap] takes the distance to the pattern's step,
     * as Rnote snaps the offset rather than the pen: ruled lines stay on their lines.
     */
    fun offset(startY: Float, pointerY: Float, snap: ((Float) -> Float)? = null): Float {
        if (abs(pointerY - startY) < SNAP_DISTANCE) return 0f
        val raw = pointerY - startY
        return snap?.invoke(raw) ?: raw
    }
}
