package io.github.kjly.brna.model

import io.github.kjly.brna.render.RnoteRandom

/** Rnote's `ShaperStyle`: shapes drawn with clean outlines, or as if sketched by hand. */
enum class ShaperStyle { SMOOTH, ROUGH }

/**
 * Rnote's rough `FillStyle`, in the order of its shaper settings and with the names its
 * files use.
 */
enum class RoughFillStyle(val apiName: String) {
    SOLID("solid"),
    HACHURE("hachure"),
    ZIG_ZAG("zig_zag"),
    ZIG_ZAG_LINE("zig_zag_line"),
    CROSSHATCH("crosshatch"),
    DOTS("dots"),
    DASHED("dashed");

    companion object {
        /** Rnote's `#[default]`. */
        val DEFAULT = HACHURE

        /**
         * Rnote before 0.5.9 wrote "Hachure", capitalised, for every fill and drew it
         * solid; it still reads that name as solid, and so does this.
         */
        fun fromApiName(name: String): RoughFillStyle =
            if (name == "Hachure") SOLID else entries.firstOrNull { it.apiName == name } ?: DEFAULT
    }
}

/**
 * A shape in Rnote's rough style (`RoughOptions`): drawn by roughr, every line wobbling a
 * little and drawn twice, the fill sketched in. The wobble comes from [seed] alone, so the
 * seed is what a file keeps, and what makes the shape here the shape on the laptop.
 */
data class RoughStyle(
    val fillStyle: RoughFillStyle = RoughFillStyle.DEFAULT,
    /** The angle of hachure lines, in radians. */
    val hachureAngle: Double = HACHURE_ANGLE_DEFAULT,
    /** Rnote's `u64` seed, its bits held in a Long; null for none, which roughr seeds as 345. */
    val seed: Long? = null
) {
    /**
     * Rnote's `advance_seed`: the style for the next of several shapes drawn with one
     * stroke of the pen — the lines of a coordinate system — each wobbling its own way.
     */
    fun advanced(): RoughStyle = copy(seed = seed?.let { RnoteRandom.seedAdvance(it) })

    companion object {
        /** Rnote's `RoughOptions::default()` line width. */
        const val STROKE_WIDTH_DEFAULT = 2.4f

        /** Rnote's default: −41°, in radians. */
        const val HACHURE_ANGLE_DEFAULT = -0.715585

        /** Rnote's shaper settings take the angle in whole degrees, −180 to 180. */
        const val HACHURE_DEGREES_DEFAULT = -41
        const val HACHURE_DEGREES_MIN = -180
        const val HACHURE_DEGREES_MAX = 180

        /** The margin Rnote puts round a rough shape's bounds: `ROUGH_BOUNDS_MARGIN`. */
        const val BOUNDS_MARGIN = 20f

        /** The angle Rnote keeps for [degrees] picked in its settings: rounded, in radians, within ±π. */
        fun hachureAngleOf(degrees: Int): Double =
            Math.toRadians(degrees.toDouble()).coerceIn(-Math.PI, Math.PI)
    }
}
