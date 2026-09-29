package io.github.kjly.brna.storage

import android.content.Context
import android.net.Uri
import io.github.kjly.brna.export.DocumentExporter
import io.github.kjly.brna.export.ExportPrefs
import io.github.kjly.brna.model.FixedPages
import io.github.kjly.brna.model.LayoutMode
import io.github.kjly.brna.model.NoteDocument
import io.github.kjly.brna.model.PaperPattern
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.model.PageSize
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.RnoteNativeDocument
import io.github.kjly.brna.model.RnoteStrokeSource
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import java.io.BufferedReader
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.io.BufferedInputStream
import java.io.InputStreamReader

object FileManager {

    // GZIP magic bytes: 0x1F 0x8B
    private const val GZIP_MAGIC_1 = 0x1F
    private const val GZIP_MAGIC_2 = 0x8B.toByte()

    /**
     * A loaded document together with the format its bytes were actually in.
     *
     * Save writes back over the same file now, so the caller has to know which format to
     * write — and it can't ask the file name, since a `.rnote` that was renamed is still
     * a `.rnote` and writing our JSON over it would destroy it.
     */
    data class LoadedDocument(
        val document: NoteDocument,
        val isNativeRnote: Boolean,
        /** The file's bytes' [ContentHash], as they were read. */
        val contentHash: String? = null,
        /**
         * A Xournal++ file made into a note, as Rnote opens one: a new, unsaved note, never
         * written back over the `.xopp` it came from.
         */
        val imported: Boolean = false
    )

    /**
     * Detects the format by sniffing the first two bytes, then dispatches to the
     * appropriate parser.
     */
    fun loadDocumentFromUri(context: Context, uri: Uri): LoadedDocument? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                // Fingerprinted on the way through, so the file is read only once.
                val digest = ContentHash.newDigest()
                val buffered = BufferedInputStream(DigestInputStream(raw, digest), SNIFF_LIMIT)
                buffered.mark(SNIFF_LIMIT)
                val b1 = buffered.read()
                val b2 = buffered.read().toByte()
                buffered.reset()

                val gzip = b1 == GZIP_MAGIC_1 && b2 == GZIP_MAGIC_2
                val loaded = if (gzip && isXopp(buffered)) {
                    // Xournal++, gzipped XML where Rnote has JSON: made into a note as Rnote does.
                    val native = XoppConvert.toNative(XoppFile.load(buffered.readBytes()), ImageImport::decodeForXopp)
                    LoadedDocument(bridgeNativeToNoteDocument(native), isNativeRnote = true, imported = true)
                } else if (gzip) {
                    // Native .rnote — parse then bridge to our editable model
                    val native = RnoteNativeParser.parse(buffered)
                    LoadedDocument(bridgeNativeToNoteDocument(native), isNativeRnote = true)
                } else {
                    // Our JSON format
                    val jsonContent = BufferedReader(InputStreamReader(buffered)).readText()
                    LoadedDocument(
                        DocumentSerializer.parseJson(jsonContent), isNativeRnote = false
                    )
                }
                // A parser may stop short of the end (GZIP's trailer); the fingerprint may not.
                ContentHash.drain(buffered)
                loaded.copy(contentHash = ContentHash.hex(digest))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** How far into a file its format is looked for: the gzip header and the start of what it holds. */
    private const val SNIFF_LIMIT = 64 * 1024

    /**
     * Whether the gzipped file at the start of [buffered] holds XML, as a Xournal++ file
     * does, rather than an `.rnote`'s JSON. Leaves the stream where it was.
     */
    private fun isXopp(buffered: BufferedInputStream): Boolean {
        buffered.mark(SNIFF_LIMIT)
        return try {
            // Not closed: that would close the file under it.
            val head = ByteArray(64)
            val n = java.util.zip.GZIPInputStream(buffered, 512).read(head)
            n > 0 && XoppFile.looksLikeXml(head.copyOf(n))
        } catch (e: java.io.IOException) {
            false
        } finally {
            buffered.reset()
        }
    }

    /**
     * Loads a native .rnote and returns the raw [RnoteNativeDocument] for
     * full-fidelity rendering (text, shapes, images).
     */
    fun loadNativeDocumentFromUri(context: Context, uri: Uri): RnoteNativeDocument? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                RnoteNativeParser.parse(raw)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a [NoteDocument].
     * If [asRnote] is true, writes the desktop .rnote GZIP+JSON format.
     * Otherwise writes our flat JSON format.
     */
    fun saveDocumentToUri(
        context: Context,
        uri: Uri,
        document: NoteDocument,
        asRnote: Boolean = false
    ): Boolean = saveDocumentHashed(context, uri, document, asRnote) != null

    /**
     * [saveDocumentToUri], giving back the [ContentHash] of the bytes written — what the
     * file holds now, as long as nobody else writes it — or null when the save failed.
     */
    fun saveDocumentHashed(
        context: Context,
        uri: Uri,
        document: NoteDocument,
        asRnote: Boolean
    ): String? = try {
        val digest = ContentHash.newDigest()
        // "wt", not "w": some providers don't truncate on "w", which would leave the tail
        // of a longer previous save behind the new one — for GZIP, a file that no longer parses.
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("No stream for $uri")
        out.use { raw ->
            val hashing = DigestOutputStream(raw, digest)
            if (asRnote) {
                RnoteNativeSerializer.serialize(hashing, RnoteNativeSerializer.bridgeToNative(document))
            } else {
                hashing.write(DocumentSerializer.toJson(document).toByteArray(Charsets.UTF_8))
                hashing.flush()
            }
        }
        ContentHash.hex(digest)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /**
     * Saves a native [RnoteNativeDocument] directly back to .rnote format
     * (e.g. after opening and editing a native file).
     */
    fun saveNativeDocumentToUri(
        context: Context,
        uri: Uri,
        document: RnoteNativeDocument
    ): Boolean = RnoteNativeSerializer.serialize(context, uri, document)

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * A single-file export — the whole document, or the current selection. What each
     * scope covers is [DocumentExporter]'s business; this is the file-I/O entry point
     * the rest of the app calls.
     */
    fun exportToUri(
        context: Context,
        uri: Uri,
        document: NoteDocument,
        selection: List<Stroke>,
        prefs: ExportPrefs,
        selectedNatives: List<NativeCanvasElement> = emptyList()
    ): DocumentExporter.Result =
        DocumentExporter.exportSingle(context, uri, document, selection, prefs, selectedNatives)

    /** A page-per-file export into the folder the user picked. */
    fun exportPagesToTree(
        context: Context,
        treeUri: Uri,
        document: NoteDocument,
        prefs: ExportPrefs,
        baseName: String
    ): DocumentExporter.Result =
        DocumentExporter.exportPages(context, treeUri, document, prefs, baseName)

    /**
     * Converts a parsed native document to our editable [NoteDocument].
     * Brush strokes are fully editable. Text, bitmaps, and shapes are stored
     * as read-only pass-through elements for now.
     */
    fun bridgeNativeToNoteDocument(native: RnoteNativeDocument): NoteDocument {
        val strokes = native.elements.mapNotNull { el ->
            when (el) {
                is NativeBrushStroke -> {
                    val color = el.color.toComposeColor()
                    val stroke = Stroke(
                        points = el.points.map { StrokePoint(it.x, it.y, it.pressure, it.curve) },
                        color  = color,
                        strokeWidth = el.strokeWidth,
                        isHighlighter = el.isHighlighter,
                        pressureCurve = el.pressureCurve,
                        textured = el.textured
                    )
                    // Kept alongside, to be written back byte for byte while it is unchanged.
                    el.raw?.let { stroke.copy(source = RnoteStrokeSource(it, stroke)) } ?: stroke
                }
                // Non-stroke elements: preserved in nativeElements, not yet editable
                else -> null
            }
        }

        // Use exact page dimensions from the .rnote format
        val formatW = native.pageWidth
        val formatH = native.pageHeight
        val bg = native.background
        val layout = LayoutMode.fromApiName(native.layout)

        val paperStyle = PaperStyle(
            pattern = when (bg.pattern) {
                io.github.kjly.brna.model.NativePatternType.GRID     -> PaperPattern.GRID
                io.github.kjly.brna.model.NativePatternType.RULED    -> PaperPattern.LINES
                io.github.kjly.brna.model.NativePatternType.DOTS     -> PaperPattern.DOTS
                io.github.kjly.brna.model.NativePatternType.ISO_GRID -> PaperPattern.ISO_GRID
                io.github.kjly.brna.model.NativePatternType.ISO_DOTS -> PaperPattern.ISO_DOTS
                io.github.kjly.brna.model.NativePatternType.BLANK    -> PaperPattern.BLANK
            },
            isDarkMode = bg.color.r < 0.5f,
            pageSize = PageSize.CUSTOM,
            // Taken from the format itself rather than the file's `orientation` field:
            // the dimensions are what the document is, the field only what it calls
            // itself, and files this app wrote before it emitted a real one say
            // "portrait" over a landscape page. The custom size goes in portrait-order so
            // that effectivePage*Px, which swaps for landscape, lands back on the format.
            isLandscape = formatW > formatH,
            // A file with no layout at all used to land on FIXED_SIZE here, which is how
            // an infinite document silently became a single page on reload.
            layoutMode = layout,
            // A Fixed Size document is as many pages tall as it says; Rnote keeps that
            // height, so a note with pages added on the laptop keeps them here.
            fixedPageCount = if (layout == LayoutMode.FIXED_SIZE) FixedPages.countFor(native.totalHeight, formatH) else 1,
            customWidthPx = if (formatW > formatH) formatH else formatW,
            customHeightPx = if (formatW > formatH) formatW else formatH,
            customGridSpacingPx = bg.patternWidth,
            customPatternHeightPx = bg.patternHeight,
            customBackgroundColor = bg.color.toComposeColor(),
            customGridColor = bg.patternColor.toComposeColor(),
            showFormatBorders = native.showBorders,
            showOriginIndicator = native.showOriginIndicator,
            formatBorderColor = native.borderColor.toComposeColor()
        )

        return NoteDocument(
            title      = "Imported Note",
            paperStyle = paperStyle,
            strokes    = strokes,
            nativeElements = native.elements
        )
    }

}
