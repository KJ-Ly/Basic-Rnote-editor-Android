package io.github.kjly.brna.storage

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Fonts the user has loaded from files on the device, standing in for a family a
 * document asks for that isn't one of Android's own faces. Desktop Rnote lets its Text
 * tool pick any font installed on the system (Page Settings > Text); an unrooted
 * Android device can't install a system font, so this is the closest equivalent —
 * a font file is imported once and registered under the family name a `.rnote` file
 * names it by, kept between sessions. [NativeElementRenderer.typeface] and the
 * Typewriter's own field both check here, case-insensitively, before falling back
 * to Android's built-in family resolution.
 */
object CustomFonts {

    /** One loaded font: [family] is what a document's `font_family` must equal (any case)
     *  for this font to be used; [fileName] is where its bytes sit under [fontsDir];
     *  [originalName] is the file name it was imported from, shown in the font list. */
    data class Entry(val family: String, val fileName: String, val originalName: String)

    private const val PREFS_NAME = "custom_fonts"
    private const val KEY_ENTRIES = "entries"

    /** family (trimmed, lower-cased) -> loaded face, filled by [restore]. */
    private val cache = ConcurrentHashMap<String, Typeface>()

    private fun key(family: String) = family.trim().lowercase(Locale.ROOT)

    private fun fontsDir(context: Context): File =
        File(context.filesDir, "custom_fonts").apply { mkdirs() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The registered fonts, newest first. */
    fun load(context: Context): List<Entry> = decode(prefs(context).getString(KEY_ENTRIES, null))

    private fun persist(context: Context, entries: List<Entry>) {
        prefs(context).edit().putString(KEY_ENTRIES, encode(entries)).apply()
    }

    /**
     * Loads every registered font's [Typeface] into memory. Call once when the app
     * starts (see MainActivity.onCreate) — [get] only ever reads this in-memory cache,
     * so a font imported in an earlier session wouldn't otherwise be found until this
     * has run.
     */
    fun restore(context: Context) {
        cache.clear()
        val dir = fontsDir(context)
        for (entry in load(context)) {
            val file = File(dir, entry.fileName)
            if (!file.exists()) continue
            runCatching { Typeface.createFromFile(file) }.getOrNull()?.let { cache[key(entry.family)] = it }
        }
    }

    /** The loaded typeface registered for [family] (matched case-insensitively), or null. */
    fun get(family: String): Typeface? = cache[key(family)]

    /**
     * Copies [uri]'s bytes into the app's own storage and registers them under
     * [family] (replacing whatever font [family] was already registered to). Returns
     * the new entry, or null if [uri]'s content isn't a font file Android can load.
     */
    fun import(context: Context, uri: Uri, family: String, originalName: String): Entry? {
        val trimmedFamily = family.trim()
        if (trimmedFamily.isEmpty()) return null
        val dir = fontsDir(context)
        val fileName = "font_${System.currentTimeMillis()}_${System.nanoTime()}${extensionOf(originalName)}"
        val target = File(dir, fileName)
        val copied = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            false
        }
        if (!copied) {
            target.delete()
            return null
        }
        val typeface = runCatching { Typeface.createFromFile(target) }.getOrNull()
        if (typeface == null) {
            target.delete()
            return null
        }
        // Replace whatever this family name used to point to, if anything.
        val existing = load(context)
        existing.firstOrNull { key(it.family) == key(trimmedFamily) }?.let { File(dir, it.fileName).delete() }
        val entry = Entry(trimmedFamily, fileName, originalName.ifBlank { fileName })
        val updated = listOf(entry) + existing.filterNot { key(it.family) == key(trimmedFamily) }
        persist(context, updated)
        cache[key(trimmedFamily)] = typeface
        return entry
    }

    /** Un-registers [entry]'s family and deletes its file. */
    fun remove(context: Context, entry: Entry) {
        File(fontsDir(context), entry.fileName).delete()
        persist(context, load(context).filterNot { it.fileName == entry.fileName })
        cache.remove(key(entry.family))
    }

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot in 0 until name.length - 1) name.substring(dot) else ""
    }

    // One line per entry, tab-separated; family/file names can't contain tabs or
    // newlines, so this is safe without a JSON dependency (matching PdfImportPrefs'
    // and PenShortcuts' own hand-rolled encoding elsewhere in this package).
    private fun encode(entries: List<Entry>): String =
        entries.joinToString("\n") { "${sanitize(it.family)}\t${sanitize(it.fileName)}\t${sanitize(it.originalName)}" }

    private fun sanitize(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    private fun decode(text: String?): List<Entry> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split("\n").mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split("\t")
            val family = parts.getOrNull(0) ?: return@mapNotNull null
            val fileName = parts.getOrNull(1) ?: return@mapNotNull null
            Entry(family, fileName, parts.getOrElse(2) { fileName })
        }
    }
}
