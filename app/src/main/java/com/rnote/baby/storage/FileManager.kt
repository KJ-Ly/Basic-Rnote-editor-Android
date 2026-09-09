package com.rnote.baby.storage

import android.content.Context
import android.net.Uri
import com.rnote.baby.export.DocumentExporter
import com.rnote.baby.export.ExportPrefs
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.model.PaperPattern
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.PageSize
import com.rnote.baby.model.NativeBrushStroke
import com.rnote.baby.model.RnoteNativeDocument
import com.rnote.baby.model.Stroke
import com.rnote.baby.model.StrokePoint
import java.io.BufferedReader
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
    data class LoadedDocument(val document: NoteDocument, val isNativeRnote: Boolean)

    /**
     * Detects the format by sniffing the first two bytes, then dispatches to the
     * appropriate parser.
     */
    fun loadDocumentFromUri(context: Context, uri: Uri): LoadedDocument? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val buffered = BufferedInputStream(raw, 4)
                buffered.mark(2)
                val b1 = buffered.read()
                val b2 = buffered.read().toByte()
                buffered.reset()

                if (b1 == GZIP_MAGIC_1 && b2 == GZIP_MAGIC_2) {
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
    ): Boolean {
        return if (asRnote) {
            RnoteNativeSerializer.serializeFromNoteDocument(context, uri, document)
        } else {
            try {
                val jsonContent = DocumentSerializer.toJson(document)
                // "wt", not "w": some providers don't truncate on "w", which would
                // leave the tail of a longer previous save behind the new one.
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
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
        prefs: ExportPrefs
    ): DocumentExporter.Result =
        DocumentExporter.exportSingle(context, uri, document, selection, prefs)

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
                    Stroke(
                        points = el.points.map { StrokePoint(it.x, it.y, it.pressure) },
                        color  = color,
                        strokeWidth = el.strokeWidth,
                        isHighlighter = el.isHighlighter,
                        pressureCurve = el.pressureCurve
                    )
                }
                // Non-stroke elements: preserved in nativeElements, not yet editable
                else -> null
            }
        }

        // Use exact page dimensions from the .rnote format
        val formatW = native.pageWidth
        val formatH = native.pageHeight
        val bg = native.background

        val paperStyle = PaperStyle(
            pattern = when (bg.pattern) {
                com.rnote.baby.model.NativePatternType.GRID     -> PaperPattern.GRID
                com.rnote.baby.model.NativePatternType.RULED    -> PaperPattern.LINES
                com.rnote.baby.model.NativePatternType.DOTS     -> PaperPattern.DOTS
                com.rnote.baby.model.NativePatternType.ISO_GRID -> PaperPattern.ISO_GRID
                com.rnote.baby.model.NativePatternType.ISO_DOTS -> PaperPattern.ISO_DOTS
                com.rnote.baby.model.NativePatternType.BLANK    -> PaperPattern.BLANK
            },
            isDarkMode = bg.color.r < 0.5f,
            pageSize = PageSize.CUSTOM,
            // A file with no layout at all used to land on FIXED_SIZE here, which is how
            // an infinite document silently became a single page on reload.
            layoutMode = com.rnote.baby.model.LayoutMode.fromApiName(native.layout),
            customWidthPx = formatW,
            customHeightPx = formatH,
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
