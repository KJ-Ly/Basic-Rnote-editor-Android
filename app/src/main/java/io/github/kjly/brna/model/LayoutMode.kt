package io.github.kjly.brna.model

/**
 * Desktop Rnote's `Layout` (crates/rnote-engine/src/document/mod.rs), serde names
 * included — it is written to `.rnote` as `document.config.layout`.
 */
enum class LayoutMode(val displayName: String, val apiName: String) {
    FIXED_SIZE("Fixed Size", "fixed_size"),
    CONTINUOUS_VERTICAL("Continuous Vertical", "continuous_vertical"),
    INFINITE("Infinite", "infinite");

    companion object {
        /**
         * Used when a file carries no layout at all — which includes every `.rnote` this
         * app wrote before it started emitting the field. It matches [PaperStyle]'s own
         * default so a document with nothing to say about its layout opens the same way
         * whichever path loaded it.
         */
        val DEFAULT = INFINITE

        fun fromApiName(name: String): LayoutMode = when (name.lowercase().trim()) {
            "fixed_size" -> FIXED_SIZE
            "continuous_vertical" -> CONTINUOUS_VERTICAL
            // Rnote's SemiInfinite grows only towards positive x/y; of the three modes
            // here, unbounded is much closer than a single fixed page.
            "infinite", "semi_infinite" -> INFINITE
            else -> DEFAULT
        }
    }
}
