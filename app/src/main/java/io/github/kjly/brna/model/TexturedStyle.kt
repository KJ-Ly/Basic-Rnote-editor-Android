package io.github.kjly.brna.model

/**
 * Rnote's `TexturedDotsDistribution`: how the dots of a textured stroke spread across its
 * width. Rnote writes the variant names as they are.
 */
enum class TexturedDistribution(val apiName: String) {
    UNIFORM("Uniform"),
    NORMAL("Normal"),
    EXPONENTIAL("Exponential"),
    REVERSE_EXPONENTIAL("ReverseExponential");

    companion object {
        /** Rnote's `#[default]`. */
        val DEFAULT = NORMAL

        fun fromApiName(name: String): TexturedDistribution = entries.firstOrNull { it.apiName == name } ?: DEFAULT
    }
}

/**
 * A brush stroke in Rnote's Textured style (`TexturedOptions`): no outline, but small
 * ellipses strewn along every segment. Where they fall comes from [seed] alone, so the
 * seed is what a file keeps and what makes the dots here the dots on the laptop.
 */
data class TexturedStyle(
    /**
     * Rnote's `u64` seed, its bits held in a Long; null for a stroke written without one,
     * which Rnote scatters anew each time it draws it.
     */
    val seed: Long?,
    /** Dots per 10 × 10 area. */
    val density: Double = DENSITY_DEFAULT,
    val distribution: TexturedDistribution = TexturedDistribution.DEFAULT
) {
    companion object {
        /** Rnote's `TexturedOptions::default()`: 5 dots per 10 × 10, 6 wide. */
        const val DENSITY_DEFAULT = 5.0
        const val WIDTH_DEFAULT = 6f

        /** `TexturedOptions::DENSITY_MIN` / `_MAX`. */
        const val DENSITY_MIN = 0.1
        const val DENSITY_MAX = 100.0

        /** [seed] as JSON writes a `u64`: unsigned, or null. */
        fun seedJson(seed: Long?): String = seed?.toULong()?.toString() ?: "null"

        /** A `u64` as JSON wrote it; null for anything else. */
        fun seedFromJson(text: String): Long? = text.toULongOrNull()?.toLong()
    }
}
