package com.rnote.baby.model

/**
 * Desktop Rnote's `PressureCurve` (crates/rnote-compose/src/style/mod.rs), serde names
 * included.
 *
 * This is the piece that decides how wide a stroke is actually painted. Rnote never
 * draws a stroke at its nominal `stroke_width`: it applies the curve to that width and
 * the point's pressure at *every* point along the path, so `stroke_width` is a maximum,
 * not a measurement. A renderer that ignores the curve draws every pressure-varying
 * stroke too thick — a desktop mouse stroke, which records Rnote's fallback pressure of
 * 0.5 throughout, comes out at exactly double.
 */
enum class PressureCurve(val apiName: String) {
    CONST("const"),
    LINEAR("linear"),
    SQRT("sqrt"),
    CBRT("cbrt"),
    POW2("pow2"),
    POW3("pow3");

    /** Rnote's `PressureCurve::apply`, verbatim. Pressure is expected in [0.0, 1.0]. */
    fun apply(width: Float, pressure: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        return when (this) {
            CONST  -> width
            LINEAR -> width * p
            SQRT   -> width * kotlin.math.sqrt(p)
            CBRT   -> width * Math.cbrt(p.toDouble()).toFloat()
            POW2   -> width * p * p
            POW3   -> width * p * p * p
        }
    }

    companion object {
        /** Rnote's `#[default]` variant, used when the field is absent or unrecognised. */
        val DEFAULT = LINEAR

        fun fromApiName(name: String): PressureCurve =
            entries.firstOrNull { it.apiName.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
