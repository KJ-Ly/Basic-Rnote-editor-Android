package io.github.kjly.brna.ui.canvas

import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.Stroke
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs

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
     * Ids of the strokes that move for a drag started at [y]: every stroke whose outline
     * reaches [y] or further down. Rnote's `keys_between` takes whatever its bounds touch,
     * so a stroke crossing the line moves whole rather than being left behind or cut.
     */
    fun strokesBelow(strokes: List<Stroke>, y: Float): Set<String> =
        strokes.filter { s -> s.points.any { it.y + s.strokeWidth / 2f >= y } }.mapTo(HashSet()) { it.id }

    /**
     * The desktop elements — text, shapes, images, PDF pages — that move by the same rule,
     * told apart by identity as the document does. Brush strokes are left to [strokesBelow].
     */
    fun nativesBelow(elements: List<NativeCanvasElement>, y: Float): Set<NativeCanvasElement> {
        val below = Collections.newSetFromMap(IdentityHashMap<NativeCanvasElement, Boolean>())
        elements.filterTo(below) { it !is NativeBrushStroke && it.maxY >= y }
        return below
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
