package io.github.kjly.brna.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.DigestOutputStream

/**
 * What a note's file held before this app wrote over it, kept in the app's own storage:
 * the newest [KEEP_PER_FILE] versions of each file, each for [KEEP_MS] — a week.
 *
 * Not every save makes one. A file that holds what this app wrote there last is written
 * over without a copy, or every autosave would push the versions worth having out; a file
 * that holds anything else — as it was opened, or as the laptop saved it since — is copied
 * first, once. So a save here can never take a version made elsewhere with it, and "Restore
 * Previous Version" brings any of them back.
 *
 * One folder per file, named by a fingerprint of its uri; in it each version as
 * `<time taken>-<fingerprint>.bak`, and [WRITTEN]: the fingerprint of what this app wrote
 * there last.
 */
object Backups {

    const val KEEP_PER_FILE = 5
    const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    private const val DIR = "backups"
    private const val WRITTEN = "written"
    private const val EXT = ".bak"
    private const val INCOMING = "incoming.tmp"

    /** One version kept of a file: when it was taken, the fingerprint of its bytes, and how many there are. */
    data class Version(val file: File, val takenAt: Long, val hash: String, val size: Long = file.length())

    private fun dirFor(context: Context, uri: Uri): File =
        File(File(context.filesDir, DIR), keyOf(uri.toString()))

    /** The folder name for a file's versions: its uri, fingerprinted, so any uri makes a name. */
    internal fun keyOf(uri: String): String = ContentHash.of(uri.toByteArray(Charsets.UTF_8)).take(32)

    /**
     * Blocking; before [uri] is written over. Copies what it holds, unless that is what this
     * app wrote there last — which [knownHash], the fingerprint of what it held when last
     * read or written here, says without reading it again, unless [changedSince]: a file
     * someone else wrote after that, about to be written over anyway. A copy that fails
     * never stops the save.
     */
    fun beforeOverwrite(
        context: Context,
        uri: Uri,
        knownHash: String?,
        changedSince: Boolean = false,
        now: Long = System.currentTimeMillis()
    ) {
        try {
            val dir = dirFor(context, uri)
            val written = readWritten(dir)
            if (!mayDiffer(knownHash, written, changedSince)) return
            keep(dir, now, skipHash = written) { context.contentResolver.openInputStream(uri) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Blocking; a copy of what [uri] holds now, whoever wrote it — before a version is restored over it. */
    fun keepNow(context: Context, uri: Uri, now: Long = System.currentTimeMillis()) {
        try {
            keep(dirFor(context, uri), now, skipHash = null) { context.contentResolver.openInputStream(uri) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Blocking; this app just wrote the bytes fingerprinted [hash] to [uri]. */
    fun written(context: Context, uri: Uri, hash: String) {
        try {
            val dir = dirFor(context, uri)
            dir.mkdirs()
            File(dir, WRITTEN).writeText(hash)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Blocking; the versions kept of [uri], newest first. */
    fun versions(context: Context, uri: Uri, now: Long = System.currentTimeMillis()): List<Version> =
        list(dirFor(context, uri), now)

    /**
     * Blocking; run now and then. Versions past their week go, and the folder of a file with
     * none left and nothing written to it this week goes too.
     */
    fun sweep(context: Context, now: Long = System.currentTimeMillis()) {
        val dirs = File(context.filesDir, DIR).listFiles() ?: return
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            prune(dir, now)
            val written = File(dir, WRITTEN)
            if (list(dir, now).isEmpty() && (!written.exists() || now - written.lastModified() > KEEP_MS)) {
                dir.deleteRecursively()
            }
        }
    }

    /**
     * Whether a file may hold anything but what this app wrote there last, fingerprinted
     * [written] — and so has to be read to know. Not when what it held when last read or
     * written here ([knownHash]) is that, and nobody wrote it since.
     */
    internal fun mayDiffer(knownHash: String?, written: String?, changedSince: Boolean): Boolean =
        changedSince || knownHash == null || knownHash != written

    // ── On a folder of versions ───────────────────────────────────────────────

    private fun readWritten(dir: File): String? =
        File(dir, WRITTEN).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    /**
     * Copies what [open] reads into [dir] as a version taken at [now]; nothing when it is
     * empty, the same as the newest version already kept, or fingerprinted [skipHash].
     * Then keeps only what [kept] keeps. The version made, or null.
     */
    internal fun keep(dir: File, now: Long, skipHash: String?, open: () -> InputStream?): Version? {
        dir.mkdirs()
        val incoming = File(dir, INCOMING)
        val digest = ContentHash.newDigest()
        val input = open() ?: return null
        try {
            input.use { from -> DigestOutputStream(incoming.outputStream(), digest).use { from.copyTo(it) } }
        } catch (e: Exception) {
            // A full disk, say: no half a copy left behind taking up room.
            incoming.delete()
            throw e
        }
        val hash = ContentHash.hex(digest)
        if (incoming.length() == 0L || hash == skipHash || hash == list(dir, now).firstOrNull()?.hash) {
            incoming.delete()
            return null
        }
        // Each its own time, even two in the same millisecond, so the newest is the last one taken.
        val times = list(dir, now).map { it.takenAt }.toSet()
        var takenAt = now
        while (takenAt in times) takenAt++
        val file = File(dir, "$takenAt-$hash$EXT")
        if (!incoming.renameTo(file)) {
            incoming.delete()
            return null
        }
        prune(dir, now)
        return Version(file, takenAt, hash)
    }

    /** The versions in [dir], newest first; any past their week are left out. */
    internal fun list(dir: File, now: Long): List<Version> {
        val all = (dir.listFiles() ?: return emptyList()).mapNotNull { file ->
            val name = file.name.takeIf { it.endsWith(EXT) }?.removeSuffix(EXT) ?: return@mapNotNull null
            val takenAt = name.substringBefore('-').toLongOrNull() ?: return@mapNotNull null
            Version(file, takenAt, name.substringAfter('-'))
        }
        val keep = kept(all.map { it.takenAt }, now).toSet()
        return all.filter { it.takenAt in keep }.sortedByDescending { it.takenAt }
    }

    private fun prune(dir: File, now: Long) {
        val keep = list(dir, now).map { it.file }.toSet()
        dir.listFiles()?.filter { it.name.endsWith(EXT) && it !in keep }?.forEach { it.delete() }
    }

    /** Of versions taken at [times], which stay: the newest [KEEP_PER_FILE] of those not past their week. */
    internal fun kept(times: List<Long>, now: Long): List<Long> =
        times.filter { now - it <= KEEP_MS }.sortedDescending().take(KEEP_PER_FILE)
}
