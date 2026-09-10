package io.github.kjly.brna.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/**
 * The file identity behind a SAF document uri: what it is called, and how to keep calling
 * it that. A note used to lose this the moment it was opened — a `.rnote` carries no title
 * of its own, so every opened file became "Imported Note" and saved itself out under that
 * name, wherever the picker happened to be pointing.
 */
object DocumentUri {

    /** The file name as the storage provider reports it, extension included. */
    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /** "Lecture 3.rnote" → "Lecture 3". The note title and the file stem are one thing. */
    fun titleFrom(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        return stem.trim().ifBlank { fileName }
    }

    fun isRnote(fileName: String): Boolean = fileName.endsWith(".rnote", ignoreCase = true)

    /**
     * Holds on to the grant so the uri still works after a restart. Providers are allowed
     * to refuse — a uri that was never flagged persistable throws — and a refusal only
     * costs us the uri on the next launch, so it isn't worth failing a save over.
     */
    fun takePersistablePermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
