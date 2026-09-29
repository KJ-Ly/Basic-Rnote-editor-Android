package io.github.kjly.brna.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * The storage side of the workspace browser: listing a folder of a picked document tree
 * and the file actions desktop Rnote's browser offers — new note, new folder, rename,
 * duplicate, delete. All of it through the document tree's grant, so it works the same
 * on the tablet's storage and in a Drive folder. Everything here blocks: call it off the
 * main thread.
 */
object FolderBrowser {

    /** The document id of the tree's own folder: where browsing a workspace starts. */
    fun rootId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    /** The uri of the document [id] inside [tree], for opening, renaming and so on. */
    fun uriOf(tree: Uri, id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    /** The folder [id] in [tree], sorted and filtered as Rnote's browser shows it; null if it can't be read. */
    fun list(context: Context, tree: Uri, id: String): List<FolderListing.Entry>? {
        return try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            val columns = arrayOf(
                Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED
            )
            val entries = mutableListOf<FolderListing.Entry>()
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val kind = FolderListing.kindOf(name, c.getString(2)) ?: continue
                    val modified = if (c.isNull(3)) null else c.getLong(3).takeIf { it > 0L }
                    entries += FolderListing.Entry(docId, name, kind, modified)
                }
            } ?: return null
            FolderListing.sorted(entries)
        } catch (e: Exception) {
            // The grant withdrawn, the folder gone, the provider unreachable.
            e.printStackTrace()
            null
        }
    }

    /**
     * An empty `.rnote` named [name] in the folder [parentId]; the caller writes the note
     * into it. Null when it can't be made.
     */
    fun createNote(context: Context, tree: Uri, parentId: String, name: String): Uri? =
        create(context, tree, parentId, "application/octet-stream", name)

    fun createFolder(context: Context, tree: Uri, parentId: String, name: String): Uri? =
        create(context, tree, parentId, FolderListing.FOLDER_MIME, name)

    private fun create(context: Context, tree: Uri, parentId: String, mime: String, name: String): Uri? = try {
        DocumentsContract.createDocument(context.contentResolver, uriOf(tree, parentId), mime, name)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /** [uri] renamed to [newName]; the uri it has now (a provider may hand out a new one), or null on failure. */
    fun rename(context: Context, uri: Uri, newName: String): Uri? = try {
        DocumentsContract.renameDocument(context.contentResolver, uri, newName) ?: uri
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    fun delete(context: Context, uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /**
     * A copy of the file [entry] beside it, named as Rnote names copies ("Notes - 1.rnote").
     * Copied byte for byte rather than with the provider's own copy, which not every
     * provider has. Null on failure; a half-written copy is removed again.
     */
    fun duplicate(context: Context, tree: Uri, parentId: String, entry: FolderListing.Entry, siblings: Set<String>): Uri? {
        if (entry.kind == FolderListing.Kind.FOLDER) return null
        val target = create(
            context, tree, parentId, "application/octet-stream", FolderListing.duplicateName(entry.name, siblings)
        ) ?: return null
        return try {
            val resolver = context.contentResolver
            resolver.openInputStream(uriOf(tree, entry.id)).use { input ->
                resolver.openOutputStream(target, "wt").use { output ->
                    requireNotNull(input).copyTo(requireNotNull(output))
                }
            }
            target
        } catch (e: Exception) {
            e.printStackTrace()
            delete(context, target)
            null
        }
    }

    /**
     * Whether [a] and [b] are the same file, however each was reached: a note opened with
     * the file picker and the same note in a workspace have different uris but one
     * document id.
     */
    fun sameDocument(a: Uri?, b: Uri?): Boolean {
        if (a == null || b == null) return false
        if (a == b) return true
        if (a.authority != b.authority) return false
        return try {
            DocumentsContract.getDocumentId(a) == DocumentsContract.getDocumentId(b)
        } catch (e: Exception) {
            false
        }
    }
}
