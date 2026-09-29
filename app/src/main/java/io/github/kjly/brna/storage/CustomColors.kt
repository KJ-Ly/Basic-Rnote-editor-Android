package io.github.kjly.brna.storage

import android.content.Context
import java.util.Locale

/**
 * The colours made in the colour picker's editor — the "Custom" row of GTK's colour
 * chooser, which desktop Rnote's "Pick a Color" dialog is — kept in the app's preferences,
 * newest first. Held as ARGB ints.
 */
object CustomColors {

    /** How many are kept; an older one drops off the end as a new one is made. */
    const val MAX = 8

    private const val PREFS_NAME = "custom_colors"
    private const val KEY_COLORS = "colors"

    fun load(context: Context): List<Int> =
        decode(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_COLORS, null))

    fun save(context: Context, colors: List<Int>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLORS, encode(colors))
            .apply()
    }

    /** [colors] with [argb] first, as GTK puts a colour just made: no repeats, at most [MAX]. */
    fun added(colors: List<Int>, argb: Int): List<Int> = (listOf(argb) + colors.filter { it != argb }).take(MAX)

    internal fun encode(colors: List<Int>): String = colors.joinToString(",")

    /** The reverse of [encode]; anything it can't read is left out rather than failing. */
    internal fun decode(text: String?): List<Int> =
        text?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.take(MAX) ?: emptyList()

    /**
     * `#rrggbb`, or `#rrggbbaa` for a colour that isn't opaque: how GTK's colour editor
     * names a colour, and what can be typed into it — a colour copied from Rnote on the
     * laptop, say.
     */
    fun toHex(argb: Int): String {
        val rgb = String.format(Locale.ROOT, "#%06x", argb and 0xFFFFFF)
        val alpha = (argb ushr 24) and 0xFF
        return if (alpha == 0xFF) rgb else rgb + String.format(Locale.ROOT, "%02x", alpha)
    }

    /** [text] as ARGB: `#rrggbb` or `#rrggbbaa`, the `#` optional, any case; null if it is neither. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        if (hex.any { Character.digit(it, 16) < 0 }) return null
        val rgb = hex.substring(0, 6).toInt(16)
        val alpha = if (hex.length == 8) hex.substring(6, 8).toInt(16) else 0xFF
        return (alpha shl 24) or rgb
    }
}
