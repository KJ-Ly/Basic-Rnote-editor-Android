package io.github.kjly.brna.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.withSign

/**
 * Desktop Rnote's constraint ratios (rnote-compose/src/constraints.rs): what the Shaper
 * can bend a drag to — level, upright, square, 3:2 or the golden ratio.
 */
enum class ConstraintRatio {
    HORIZONTAL, VERTICAL, ONE_TO_ONE, THREE_TO_TWO, GOLDEN;

    /** The drag [v] bent to this ratio: Rnote's `ConstraintRatio::constrain`. */
    fun constrain(v: Offset): Offset = when (this) {
        HORIZONTAL -> Offset(v.x, 0f)
        VERTICAL -> Offset(0f, v.y)
        ONE_TO_ONE -> ratioOf(v, 1f)
        THREE_TO_TWO -> ratioOf(v, 1.5f)
        GOLDEN -> ratioOf(v, GOLDEN_RATIO)
    }

    companion object {
        /** Rnote's `ConstraintRatio::GOLDEN_RATIO`. */
        const val GOLDEN_RATIO = 1.618f
    }
}

/**
 * The longer side of [v] kept, the shorter one set from it. Rust's `signum` is 1 for a
 * zero, so a level drag bent to 1:1 goes down, as it does in Rnote.
 */
private fun ratioOf(v: Offset, ratio: Float): Offset =
    if (abs(v.x) > abs(v.y)) {
        Offset(v.x, abs(v.x / ratio).withSign(v.y))
    } else {
        Offset(abs(v.y / ratio).withSign(v.x), v.y)
    }

/**
 * Rnote's `Constraints`: whether the Shaper bends drags, and the ratios it may bend them
 * to. Rnote starts with them off and with 1:1, level and upright to choose from; holding
 * Ctrl turns them on, or off, for as long as it is held.
 */
data class ShapeConstraints(
    val enabled: Boolean = false,
    val ratios: Set<ConstraintRatio> = DEFAULT_RATIOS
) {
    /** [v] bent to whichever ratio is nearest it, or as it is when they are off. */
    fun constrain(v: Offset): Offset {
        if (!enabled) return v
        var best = v
        var bestDistance = Float.MAX_VALUE
        for (ratio in ratios) {
            val bent = ratio.constrain(v)
            val distance = (bent - v).getDistance()
            if (distance <= bestDistance) {
                best = bent
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * With level and upright added, as Rnote's line, arrow, polyline, polygon and curve
     * builders always add them.
     */
    fun withAxes(): ShapeConstraints = copy(ratios = ratios + ConstraintRatio.HORIZONTAL + ConstraintRatio.VERTICAL)

    /** Switched on or off for as long as Ctrl is held ([ctrl]), as in Rnote. */
    fun withCtrl(ctrl: Boolean): ShapeConstraints = if (ctrl) copy(enabled = !enabled) else this

    companion object {
        /** Rnote's `ShaperConfig::default` ratios. */
        val DEFAULT_RATIOS = setOf(ConstraintRatio.ONE_TO_ONE, ConstraintRatio.HORIZONTAL, ConstraintRatio.VERTICAL)
    }
}
