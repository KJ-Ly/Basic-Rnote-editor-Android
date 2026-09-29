package io.github.kjly.brna.storage

import android.content.Context
import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * The notes opened or saved last, newest first, for reopening in two taps instead of
 * digging through the file picker (or Drive) every time.
 *
 * A uri is only worth listing while the app may still read it. The file picker hands out
 * a lasting grant (taken in [DocumentUri.takePersistablePermission]); "Open with" from
 * another app — Drive's own app, for one — only grants access until this app closes. Such
 * a note is listed for the rest of the session and then dropped rather than offered as an
 * entry that can only fail.
 */
object RecentFiles {

    data class Entry(val uri: String, val title: String, val openedAt: Long)

    const val MAX_ENTRIES = 10

    private const val PREFS = "recent_files"
    private const val KEY = "entries"

    /** Opened in this run of the app, so readable even without a lasting grant. */
    private val openedThisSession = mutableSetOf<String>()

    /** Records [uri] as just opened or saved, under [title]. */
    fun add(context: Context, uri: Uri, title: String) {
        val key = uri.toString()
        synchronized(openedThisSession) { openedThisSession += key }
        val entries = withEntry(load(context), Entry(key, title, System.currentTimeMillis()))
        store(context, entries)
    }

    /** Forgets [uri], e.g. once it turned out not to open any more. */
    fun remove(context: Context, uri: String) {
        store(context, load(context).filter { it.uri != uri })
    }

    /** The entries that can still be opened, newest first. */
    fun list(context: Context): List<Entry> {
        val granted = try {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val session = synchronized(openedThisSession) { openedThisSession.toSet() }
        return load(context).filter { it.uri in session || isGranted(it.uri, granted) }
    }

    /**
     * Whether [uri] is readable under [granted]: granted itself, or — for a note opened in a
     * workspace — a document inside a granted folder tree, whose grant covers it.
     */
    internal fun isGranted(uri: String, granted: Set<String>): Boolean =
        uri in granted || granted.any { uri.startsWith("$it/document/") }

    /** [entries] with [entry] put first, any older entry for the same file dropped. */
    internal fun withEntry(entries: List<Entry>, entry: Entry, max: Int = MAX_ENTRIES): List<Entry> =
        (listOf(entry) + entries.filter { it.uri != entry.uri }).take(max)

    internal fun encode(entries: List<Entry>): String = JsonArray().apply {
        entries.forEach { e ->
            add(JsonObject().apply {
                addProperty("uri", e.uri)
                addProperty("title", e.title)
                addProperty("openedAt", e.openedAt)
            })
        }
    }.toString()

    /** Never throws: a list that can't be read is an empty list, not a crash at startup. */
    internal fun decode(json: String?): List<Entry> = try {
        if (json.isNullOrBlank()) emptyList()
        else JsonParser.parseString(json).asJsonArray.mapNotNull { item ->
            val o = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val uri = o.get("uri")?.asString ?: return@mapNotNull null
            Entry(uri, o.get("title")?.asString ?: uri, o.get("openedAt")?.asLong ?: 0L)
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun load(context: Context): List<Entry> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    private fun store(context: Context, entries: List<Entry>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(entries)).apply()
    }
}
