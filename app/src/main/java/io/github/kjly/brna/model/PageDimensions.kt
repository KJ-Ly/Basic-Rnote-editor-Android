package io.github.kjly.brna.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A page side as the page settings show it and take it typed in (see PageSettingsSheet). */
object PageDimensions {

    /** How far apart a side typed and the side shown may be and still be the same: under a tenth shown. */
    private const val SAME = 0.05f

    /**
     * The number typed: with a decimal point, or with the decimal comma a German keyboard
     * types. Null for anything else, and for 0 or less, which no page is.
     */
    fun parse(text: String): Float? =
        text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }

    /** [value] to a tenth, written as [locale] writes numbers; a whole number without one. */
    fun format(value: Float, locale: Locale = Locale.getDefault()): String {
        val tenths = (value * 10f).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else String.format(locale, "%.1f", tenths / 10f)
    }

    /** Whether [text], as typed, is [value]: then it stays as typed, rather than being written over as it is typed. */
    fun shows(text: String, value: Float): Boolean = parse(text)?.let { abs(it - value) < SAME } == true
}
