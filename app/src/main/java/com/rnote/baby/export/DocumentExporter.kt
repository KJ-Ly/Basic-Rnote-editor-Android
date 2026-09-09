package com.rnote.baby.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.geometry.Rect
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.model.Stroke
import java.io.OutputStream
import java.util.Locale

/**
 * Turns an [ExportPrefs] into files. Everything that decides *what* a scope covers — which
 * strokes, which region, which pages — lives here; the format objects only decide how that
 * region is written out.
 */
object DocumentExporter {

    sealed class Result {
        /** [fileCount] is 1 for every scope but [ExportScope.PAGES]. */
        data class Success(val fileCount: Int) : Result()
        data class Failure(val message: String) : Result()
    }

    /** The pages this document has, in the order [prefs] asks for. Empty if it has none. */
    fun pagesFor(document: NoteDocument, prefs: ExportPrefs): List<Rect> =
        ExportLayout.pageRects(document.paperStyle, document.strokes, prefs.pageOrder)

    /** The suggested file name for a single-file export, extension included. */
    fun fileNameFor(baseName: String, prefs: ExportPrefs): String =
        "${sanitize(baseName)}.${prefs.format.extension}"

    /**
     * [ExportScope.DOCUMENT] and [ExportScope.SELECTION] — a single file at [uri].
     * [selection] is only read for the selection scope.
     */
    fun exportSingle(
        context: Context,
        uri: Uri,
        document: NoteDocument,
        selection: List<Stroke>,
        prefs: ExportPrefs
    ): Result {
        val paperStyle = document.paperStyle
        val pages = pagesFor(document, prefs)

        val strokes: List<Stroke>
        val region: Rect
        when (prefs.scope) {
            ExportScope.DOCUMENT -> {
                strokes = document.strokes
                region = ExportLayout.documentBounds(paperStyle, document.strokes)
            }
            ExportScope.SELECTION -> {
                if (selection.isEmpty()) return Result.Failure("Nothing is selected")
                strokes = selection
                region = ExportLayout.selectionBounds(selection, prefs.marginPx)
                    ?: return Result.Failure("The selection has no extent")
            }
            ExportScope.PAGES ->
                return Result.Failure("Page export writes to a folder, not a file")
        }

        val ok = writeTo(context, uri) { out ->
            when (prefs.format) {
                ExportFormat.SVG -> {
                    out.write(
                        SvgExporter.export(paperStyle, strokes, region, prefs, pages)
                            .toByteArray(Charsets.UTF_8)
                    )
                    true
                }
                ExportFormat.PNG, ExportFormat.JPEG ->
                    ImageExporter.exportBitmap(paperStyle, strokes, region, prefs, out, pages)
                ExportFormat.PDF -> {
                    // A document with no page grid is still one PDF page: the whole thing.
                    val pdfPages = pages.ifEmpty { listOf(region) }
                    PdfExporter.export(paperStyle, strokes, pdfPages, prefs, out)
                }
            }
        }
        return if (ok) Result.Success(1) else Result.Failure("Could not write the file")
    }

    /**
     * [ExportScope.PAGES] — one file per page into the folder [treeUri], named the way
     * Rnote names them: "<note> - page 03.svg".
     */
    fun exportPages(
        context: Context,
        treeUri: Uri,
        document: NoteDocument,
        prefs: ExportPrefs,
        baseName: String
    ): Result {
        val pages = pagesFor(document, prefs)
        if (pages.isEmpty()) return Result.Failure("This document has no pages")

        val wanted = PageRange.parse(prefs.pageRange, pages.size)
            ?: return Result.Failure("Could not read the page range")
        if (wanted.isEmpty()) return Result.Failure("That range selects no pages")

        val dirUri = try {
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.Failure("Could not open that folder")
        }

        val stem = sanitize(baseName)
        var written = 0
        for (index in wanted) {
            val page = pages[index]
            val name = String.format(
                Locale.ROOT, "%s - page %02d.%s", stem, index + 1, prefs.format.extension
            )
            val fileUri = try {
                DocumentsContract.createDocument(
                    context.contentResolver, dirUri, prefs.format.mimeType, name
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } ?: return Result.Failure(
                if (written == 0) "Could not create files in that folder"
                else "Stopped after $written page(s)"
            )

            val ok = writeTo(context, fileUri) { out ->
                when (prefs.format) {
                    ExportFormat.SVG -> {
                        out.write(
                            SvgExporter.export(
                                document.paperStyle, document.strokes, page, prefs, listOf(page)
                            ).toByteArray(Charsets.UTF_8)
                        )
                        true
                    }
                    else ->
                        ImageExporter.exportBitmap(
                            document.paperStyle, document.strokes, page, prefs, out, listOf(page)
                        )
                }
            }
            if (!ok) return Result.Failure("Failed on page ${index + 1}")
            written++
        }
        return Result.Success(written)
    }

    private inline fun writeTo(context: Context, uri: Uri, body: (OutputStream) -> Boolean): Boolean =
        try {
            // "wt" so re-exporting over an existing file replaces it rather than
            // overwriting its first N bytes.
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                val ok = body(out)
                out.flush()
                ok
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    /** Keeps a note title usable as a file name on the storage providers SAF talks to. */
    private fun sanitize(name: String): String {
        val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        return cleaned.ifBlank { "MyNote" }
    }
}
