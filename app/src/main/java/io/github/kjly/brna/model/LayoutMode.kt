package io.github.kjly.brna.model

/**
 * Desktop Rnote's `Layout` (crates/rnote-engine/src/document/mod.rs), serde names
 * included — it is written to `.rnote` as `document.config.layout`.
 */
enum class LayoutMode(val displayName: String, val apiName: String) {
    FIXED_SIZE("Fixed Size", "fixed_size"),
    CONTINUOUS_VERTICAL("Continuous Vertical", "continuous_vertical"),
    /** Unbounded towards positive x/y only; the origin page is the top-left corner. */
    SEMI_INFINITE("Semi Infinite", "semi_infinite"),
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
            // "endless_vertical" is the name older Rnote versions wrote for it.
            "continuous_vertical", "endless_vertical" -> CONTINUOUS_VERTICAL
            // Its own mode, so a desktop note saved here keeps its layout instead of
            // quietly turning into an Infinite one.
            "semi_infinite" -> SEMI_INFINITE
            "infinite" -> INFINITE
            else -> DEFAULT
        }
    }
}
