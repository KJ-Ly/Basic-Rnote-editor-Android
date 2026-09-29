package io.github.kjly.brna.storage

import android.content.Context
import io.github.kjly.brna.model.BrushStyle
import io.github.kjly.brna.model.PenFavorite

/**
 * The favorite pens, kept in the app's preferences. Always [SLOTS] long; an empty slot
 * is null.
 */
object PenFavorites {

    const val SLOTS = 4

    private const val PREFS_NAME = "pen_favorites"
    private const val KEY_SLOTS = "slots"

    fun load(context: Context): List<PenFavorite?> =
        decode(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SLOTS, null))

    fun save(context: Context, favorites: List<PenFavorite?>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SLOTS, encode(favorites))
            .apply()
    }

    /** One line per slot, "STYLE,argb,width", an empty line for an empty slot. */
    internal fun encode(favorites: List<PenFavorite?>): String =
        (0 until SLOTS).joinToString("\n") { i ->
            favorites.getOrNull(i)?.let { "${it.style.name},${it.argb},${it.width}" } ?: ""
        }

    /** The reverse of [encode]; a slot it can't read comes back empty rather than failing. */
    internal fun decode(text: String?): List<PenFavorite?> {
        val lines = text?.split('\n') ?: emptyList()
        return (0 until SLOTS).map { i -> lines.getOrNull(i)?.let(::decodeSlot) }
    }

    private fun decodeSlot(line: String): PenFavorite? {
        val parts = line.split(',')
        if (parts.size != 3) return null
        val style = BrushStyle.entries.firstOrNull { it.name == parts[0] } ?: return null
        val argb = parts[1].toIntOrNull() ?: return null
        val width = parts[2].toFloatOrNull()?.takeIf { it > 0f } ?: return null
        return PenFavorite(style, argb, width)
    }
}
