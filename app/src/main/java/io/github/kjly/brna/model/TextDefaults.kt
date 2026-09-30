package io.github.kjly.brna.model

/**
 * The size and family the Typewriter gives new text, as they are kept between sessions:
 * the last ones chosen, so a document that uses one size and one font throughout does not
 * have them set again for every text box.
 */
object TextDefaults {
    /** Rnote's `TextStyle::FONT_SIZE_DEFAULT`. */
    const val SIZE = 32f

    /** Rnote's `TextStyle::FONT_SIZE_MIN` and `_MAX`, the limits [ToolConfig.updateActiveSize] keeps to. */
    const val MIN_SIZE = 1f
    const val MAX_SIZE = 512f

    /** Rnote's own default family; matches NativeEditing.TEXT_FONT_FAMILY. */
    const val FAMILY = "serif"

    /** [saved] as a size for new text: within Rnote's limits, or the default for nothing usable. */
    fun size(saved: Float): Float =
        if (saved.isFinite() && saved > 0f) saved.coerceIn(MIN_SIZE, MAX_SIZE) else SIZE

    /**
     * [saved] as a family for new text. Kept if it is the default or [isAvailable] says a
     * font is loaded for it; a font that has been removed since is no longer offered, so
     * the default stands in.
     */
    fun family(saved: String?, isAvailable: (String) -> Boolean): String {
        val name = saved?.trim().orEmpty()
        return if (name.isEmpty() || (name != FAMILY && !isAvailable(name))) FAMILY else name
    }
}
