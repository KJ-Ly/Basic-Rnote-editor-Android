package io.github.kjly.brna.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D affine transforms in the layout the model's `transform` fields use:
 * `[a, b, c, d, tx, ty]`, mapping (x, y) to (a·x + c·y + tx, b·x + d·y + ty).
 * Rnote stores the same matrix column-major as a 3×3 `affine`.
 */
object Affine {
    val IDENTITY: FloatArray get() = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)

    /** Scales by ([sx], [sy]) about the fixed point ([px], [py]). */
    fun scaleAbout(px: Float, py: Float, sx: Float, sy: Float): FloatArray =
        floatArrayOf(sx, 0f, 0f, sy, px - sx * px, py - sy * py)

    /** Rotates by [angle] radians (clockwise on screen, where y points down) about ([cx], [cy]). */
    fun rotateAbout(cx: Float, cy: Float, angle: Float): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        return floatArrayOf(c, s, -s, c, cx - c * cx + s * cy, cy - s * cx - c * cy)
    }

    /** The transform that applies [t] first and then [m]. */
    fun compose(m: FloatArray, t: FloatArray): FloatArray = floatArrayOf(
        m[0] * t[0] + m[2] * t[1],
        m[1] * t[0] + m[3] * t[1],
        m[0] * t[2] + m[2] * t[3],
        m[1] * t[2] + m[3] * t[3],
        m[0] * t[4] + m[2] * t[5] + m[4],
        m[1] * t[4] + m[3] * t[5] + m[5]
    )

    fun mapX(m: FloatArray, x: Float, y: Float) = m[0] * x + m[2] * y + m[4]
    fun mapY(m: FloatArray, x: Float, y: Float) = m[1] * x + m[3] * y + m[5]

    /**
     * How much [m] scales a line width: the geometric mean of its scale factors, which
     * is what Rnote multiplies a stroke's width by when it scales it (`(sx·sy).sqrt()`).
     * A rotation leaves widths alone.
     */
    fun widthFactor(m: FloatArray): Float = sqrt(abs(m[0] * m[3] - m[1] * m[2]))
}
