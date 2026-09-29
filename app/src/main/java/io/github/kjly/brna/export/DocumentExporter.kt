package io.github.kjly.brna.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.geometry.Rect
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NoteDocument
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.storage.RnoteNativeSerializer
import io.github.kjly.brna.storage.XoppConvert
import io.github.kjly.brna.storage.XoppFile
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
        ExportLayout.pageRects(
            document.paperStyle, document.strokes, prefs.pageOrder, document.nativeElements,
            followImportedPages = prefs.pagesFromImportedPdf
        )

    /** Whether the document has imported PDF pages, which [ExportPrefs.pagesFromImportedPdf] follows. */
    fun hasImportedPages(document: NoteDocument): Boolean =
        document.nativeElements.any { it is io.github.kjly.brna.model.NativeVectorImageElement }

    /** The suggested file name for a single-file export, extension included. */
    fun fileNameFor(baseName: String, prefs: ExportPrefs): String =
        "${sanitize(baseName)}.${prefs.format.extension}"

    /**
     * [ExportScope.DOCUMENT] and [ExportScope.SELECTION] — a single file at [uri].
     * [selection] and [selectedNatives] are only read for the selection scope.
     */
    fun exportSingle(
        context: Context,
        uri: Uri,
        document: NoteDocument,
        selection: List<Stroke>,
        prefs: ExportPrefs,
        selectedNatives: List<NativeCanvasElement> = emptyList()
    ): Result {
        val paperStyle = document.paperStyle
        val pages = pagesFor(document, prefs)

        val strokes: List<Stroke>
        val region: Rect
        val natives: List<NativeCanvasElement>
        when (prefs.scope) {
            ExportScope.DOCUMENT -> {
                strokes = document.strokes
                natives = document.nativeElements
                region = ExportLayout.documentBounds(paperStyle, document.strokes, natives)
            }
            ExportScope.SELECTION -> {
                // The selector holds text, shapes and images as well as ink.
                if (selection.isEmpty() && selectedNatives.isEmpty()) return Result.Failure("Nothing is selected")
                strokes = selection
                natives = selectedNatives
                region = ExportLayout.selectionBounds(selection, prefs.marginPx, selectedNatives)
                    ?: return Result.Failure("The selection has no extent")
            }
            ExportScope.PAGES ->
                return Result.Failure("Page export writes to a folder, not a file")
        }

        val ok = writeTo(context, uri) { out ->
            when (prefs.format) {
                ExportFormat.SVG -> {
                    out.write(
                        SvgExporter.export(paperStyle, strokes, region, prefs, pages, natives)
                            .toByteArray(Charsets.UTF_8)
                    )
                    true
                }
                ExportFormat.PNG, ExportFormat.JPEG ->
                    ImageExporter.exportBitmap(paperStyle, strokes, region, prefs, out, pages, natives)
                ExportFormat.PDF -> {
                    // A document with no page grid is still one PDF page: the whole thing.
                    val pdfPages = pages.ifEmpty { listOf(region) }
                    PdfExporter.export(paperStyle, strokes, pdfPages, prefs, out, natives)
                }
                ExportFormat.XOPP -> {
                    out.write(exportXopp(document, prefs, region))
                    true
                }
            }
        }
        return if (ok) Result.Success(1) else Result.Failure("Could not write the file")
    }

    /**
     * The document as a Xournal++ file, as Rnote writes one (see [XoppConvert.fromNative]):
     * the pages of the format grid with something on them — not the imported PDF's, which
     * Rnote doesn't follow here — or, without pages, one round [region].
     */
    private fun exportXopp(document: NoteDocument, prefs: ExportPrefs, region: Rect): ByteArray {
        val native = RnoteNativeSerializer.bridgeToNative(document)
        val pages = pagesFor(document, prefs.copy(pagesFromImportedPdf = false)).ifEmpty { listOf(region) }
            .map { XoppConvert.PageRect(it.left.toDouble(), it.top.toDouble(), it.right.toDouble(), it.bottom.toDouble()) }
        // Rnote's `export_to_bitmap_image_bytes`: the element alone, no background, at 1.8.
        val imagePrefs = ExportPrefs(
            format = ExportFormat.PNG,
            withBackground = false,
            withPattern = false,
            optimizePrinterOutput = false,
            bitmapScaleFactor = XoppConvert.IMAGE_SCALE.toFloat()
        )
        val root = XoppConvert.fromNative(native, pages) { el ->
            val bounds = Rect(el.minX, el.minY, el.maxX, el.maxY)
            if (bounds.width <= 0f || bounds.height <= 0f) return@fromNative null
            val png = java.io.ByteArrayOutputStream()
            val drawn = ImageExporter.exportBitmap(
                document.paperStyle, emptyList(), bounds, imagePrefs, png, emptyList(), listOf(el)
            )
            if (drawn) png.toByteArray() else null
        }
        return XoppFile.save(root)
    }

    /**
     * One [region] of the document — a page, or what is on screen of a canvas without
     * pages — as a single file at [uri], in [ExportPrefs.format]. What sharing a page sends.
     */
    fun exportRegion(
        context: Context,
        uri: Uri,
        document: NoteDocument,
        region: Rect,
        prefs: ExportPrefs
    ): Result {
        val paperStyle = document.paperStyle
        // The pages it touches, so the pattern stops at their edges as in any export.
        val pages = pagesFor(document, prefs).filter { ExportLayout.intersectOrNull(it, region) != null }
        val ok = writeTo(context, uri) { out ->
            when (prefs.format) {
                ExportFormat.SVG -> {
                    out.write(
                        SvgExporter.export(paperStyle, document.strokes, region, prefs, pages, document.nativeElements)
                            .toByteArray(Charsets.UTF_8)
                    )
                    true
                }
                ExportFormat.PNG, ExportFormat.JPEG ->
                    ImageExporter.exportBitmap(
                        paperStyle, document.strokes, region, prefs, out, pages, document.nativeElements
                    )
                ExportFormat.PDF ->
                    PdfExporter.export(paperStyle, document.strokes, listOf(region), prefs, out, document.nativeElements)
                // Only ever offered for the whole document.
                ExportFormat.XOPP -> false
            }
        }
        return if (ok) Result.Success(1) else Result.Failure("Could not write the file")
    }

    /**
     * The pages printing sends, as Rnote's "Print" sends them: the document's pages, or
     * a canvas without any as a single page of everything on it.
     */
    fun printPages(document: NoteDocument, prefs: ExportPrefs): List<Rect> =
        pagesFor(document, prefs).ifEmpty {
            listOf(ExportLayout.documentBounds(document.paperStyle, document.strokes, document.nativeElements))
        }

    /** [pages] of [document] as a PDF into [out], which is left open. False on failure. */
    fun writePdf(document: NoteDocument, pages: List<Rect>, prefs: ExportPrefs, out: OutputStream): Boolean =
        PdfExporter.export(document.paperStyle, document.strokes, pages, prefs, out, document.nativeElements)

    /**
     * The name a shared file goes out under, which is what the receiving app shows:
     * "<note> - page 03.png", "<note> - selection.png", or "<note>.pdf" without [detail].
     */
    fun sharedFileName(baseName: String, detail: String?, format: ExportFormat): String {
        val stem = sanitize(baseName)
        return if (detail == null) "$stem.${format.extension}" else "$stem - $detail.${format.extension}"
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
                                document.paperStyle, document.strokes, page, prefs, listOf(page),
                                document.nativeElements
                            ).toByteArray(Charsets.UTF_8)
                        )
                        true
                    }
                    else ->
                        ImageExporter.exportBitmap(
                            document.paperStyle, document.strokes, page, prefs, out, listOf(page),
                            document.nativeElements
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
