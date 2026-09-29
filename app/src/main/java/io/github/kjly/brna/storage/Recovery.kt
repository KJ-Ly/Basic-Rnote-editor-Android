package io.github.kjly.brna.storage

import android.content.Context
import android.net.Uri
import io.github.kjly.brna.model.NoteDocument
import java.io.File
import java.util.Properties

/**
 * A private copy of each open note's unsaved state, so work survives Android reclaiming
 * the app in the background — which it does without warning when memory runs short.
 *
 * Written by autosave, cleared by every successful save, and offered back on the next
 * launch if it is still there. It lives in the app's own storage, so writing it is fast
 * and never touches the user's file.
 *
 * Every tab has a slot of its own, named by the tab's id; the ids are taken from the
 * clock, so a slot left by the last run never collides with a tab of this one. Slot
 * [LEGACY_SLOT] is the single copy the app kept before it had tabs, still read.
 */
object Recovery {

    const val LEGACY_SLOT = 0L

    private const val PREFIX = "recovery"
    private const val NOTE_EXT = ".rnote"
    private const val META_EXT = ".properties"

    private const val KEY_TITLE = "title"
    private const val KEY_URI = "uri"
    private const val KEY_AS_RNOTE = "asRnote"
    private const val KEY_SAVED_AT = "savedAt"

    /** A recovered note and where it came from. */
    class Pending(
        /** The tab it was in; restored, it goes on in a tab of that id. */
        val slot: Long,
        val document: NoteDocument,
        /** The file the note belongs to; null for a note that had never been saved. */
        val uri: Uri?,
        val saveAsRnote: Boolean,
        val savedAt: Long
    )

    private fun stem(slot: Long) = if (slot == LEGACY_SLOT) PREFIX else "$PREFIX-$slot"

    /** The slot a recovery file belongs to, or null for any other file. */
    internal fun slotOf(fileName: String): Long? = when {
        !fileName.endsWith(NOTE_EXT) -> null
        fileName == PREFIX + NOTE_EXT -> LEGACY_SLOT
        fileName.startsWith("$PREFIX-") ->
            fileName.removePrefix("$PREFIX-").removeSuffix(NOTE_EXT).toLongOrNull()?.takeIf { it != LEGACY_SLOT }
        else -> null
    }

    /** Blocking; call off the main thread. */
    fun write(context: Context, document: NoteDocument, uri: Uri?, saveAsRnote: Boolean, slot: Long) {
        val dir = context.filesDir
        val noteFile = stem(slot) + NOTE_EXT
        val metaFile = stem(slot) + META_EXT
        // Written aside and renamed into place, so a write cut short by the process being
        // killed leaves the previous copy intact rather than half a file.
        val noteTmp = File(dir, "$noteFile.tmp")
        noteTmp.outputStream().use {
            RnoteNativeSerializer.serialize(it, RnoteNativeSerializer.bridgeToNative(document))
        }
        val meta = Properties().apply {
            setProperty(KEY_TITLE, document.title)
            uri?.let { setProperty(KEY_URI, it.toString()) }
            setProperty(KEY_AS_RNOTE, saveAsRnote.toString())
            setProperty(KEY_SAVED_AT, System.currentTimeMillis().toString())
        }
        val metaTmp = File(dir, "$metaFile.tmp")
        metaTmp.outputStream().use { meta.store(it, null) }
        noteTmp.renameTo(File(dir, noteFile))
        metaTmp.renameTo(File(dir, metaFile))
    }

    /** Every recovered note, oldest tab first; the ones that can't be read are left out. Blocking. */
    fun readAll(context: Context): List<Pending> =
        (context.filesDir.list() ?: emptyArray())
            .mapNotNull { slotOf(it) }
            .sorted()
            .mapNotNull { read(context, it) }

    /** The note recovered from [slot], or null when there is none (or it can't be read). Blocking. */
    private fun read(context: Context, slot: Long): Pending? = try {
        val noteFile = File(context.filesDir, stem(slot) + NOTE_EXT)
        val metaFile = File(context.filesDir, stem(slot) + META_EXT)
        if (!noteFile.exists() || !metaFile.exists()) {
            null
        } else {
            val meta = Properties().apply { metaFile.inputStream().use { load(it) } }
            val native = noteFile.inputStream().use { RnoteNativeParser.parse(it) }
            val document = FileManager.bridgeNativeToNoteDocument(native)
                .copy(title = meta.getProperty(KEY_TITLE) ?: "Recovered Note")
            Pending(
                slot = slot,
                document = document,
                uri = meta.getProperty(KEY_URI)?.let(Uri::parse),
                saveAsRnote = meta.getProperty(KEY_AS_RNOTE)?.toBoolean() ?: true,
                savedAt = meta.getProperty(KEY_SAVED_AT)?.toLongOrNull() ?: noteFile.lastModified()
            )
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }

    /** Forgets the note in [slot]: it was saved, or thrown away on purpose. */
    fun clear(context: Context, slot: Long) {
        File(context.filesDir, stem(slot) + NOTE_EXT).delete()
        File(context.filesDir, stem(slot) + META_EXT).delete()
    }
}
