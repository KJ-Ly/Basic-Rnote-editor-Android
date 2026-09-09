package com.rnote.baby

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rnote.baby.export.DocumentExporter
import com.rnote.baby.export.ExportFormat
import com.rnote.baby.export.ExportPrefs
import com.rnote.baby.export.ExportScope
import com.rnote.baby.model.BrushStyle
import com.rnote.baby.model.NativeCanvasElement
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.Stroke
import com.rnote.baby.model.StrokePoint
import com.rnote.baby.model.ToolConfig
import com.rnote.baby.model.ToolType
import com.rnote.baby.model.ViewportState
import com.rnote.baby.storage.DocumentUri
import com.rnote.baby.storage.FileManager
import com.rnote.baby.storage.SettingsManager
import kotlin.math.floor
import com.rnote.baby.ui.canvas.DrawingCanvas
import com.rnote.baby.ui.components.ColorPicker
import com.rnote.baby.ui.components.ExportSheet
import com.rnote.baby.ui.components.PageSettingsSheet
import com.rnote.baby.ui.components.PenConfigStrip
import com.rnote.baby.ui.components.PenPicker
import com.rnote.baby.ui.components.RnoteTopBar
import com.rnote.baby.ui.theme.BabyRnoteTheme

class MainActivity : ComponentActivity() {

    private var performUndoAction: (() -> Unit)? = null
    private var performRedoAction: (() -> Unit)? = null

    // Storage Activity Launchers
    private var pendingDocumentToSave: NoteDocument? = null
    // Defaults to true since the app's whole purpose is desktop Rnote interop —
    // a brand-new note should save as .rnote, not fall back to our internal .json format.
    private var saveAsRnote: Boolean = true

    /**
     * The file this note came from and saves back to; null until it has been written once.
     *
     * Save used to launch the create-document picker every single time, so a note opened
     * from disk was saved as a *new* file — under whatever name the picker proposed, in
     * whatever folder it happened to open on — instead of back over itself.
     */
    private var currentDocumentUri: Uri? = null

    /**
     * Where the SAF picker should open. Without `EXTRA_INITIAL_URI` it starts wherever it
     * was last left, which for a first save is rarely anywhere near the note.
     */
    private var pickerStartUri: Uri? = null

    // Called after save so we can clear isModified
    private var onSaveSucceeded: (() -> Unit)? = null

    /** Set when the file's own name becomes the note's title (on open, and on save-as). */
    private var onTitleAdopted: ((String) -> Unit)? = null

    /** Opens the create-document picker in the folder the note already lives in. */
    private inner class CreateDocumentNear(mimeType: String) :
        ActivityResultContracts.CreateDocument(mimeType) {
        override fun createIntent(context: Context, input: String): Intent =
            super.createIntent(context, input).apply {
                pickerStartUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
    }

    /** The same for opening, plus the write grant that saving back over the file needs. */
    private inner class OpenDocumentNear : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).apply {
                pickerStartUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
    }

    /** Launcher for saving as our JSON format. */
    private val createDocumentLauncher = registerForActivityResult(
        CreateDocumentNear("application/json")
    ) { uri -> uri?.let { finishSaveAs(it, asRnote = false) } }

    /** Launcher for saving as native .rnote (GZIP+JSON). */
    private val createRnoteLauncher = registerForActivityResult(
        CreateDocumentNear("application/octet-stream")
    ) { uri -> uri?.let { finishSaveAs(it, asRnote = true) } }

    private val openDocumentLauncher = registerForActivityResult(
        OpenDocumentNear()
    ) { uri ->
        uri?.let {
            val loaded = FileManager.loadDocumentFromUri(this, it)
            if (loaded != null) {
                val fileName = DocumentUri.displayName(this, it)
                // The file's own name wins over the title inside it: a .rnote carries no
                // title at all, and our JSON's title is only what it was last renamed to.
                val title = fileName?.let(DocumentUri::titleFrom) ?: loaded.document.title
                // The bytes decide the format it saves back as. The old test was the
                // "Imported Note" placeholder title, which said nothing about the file.
                saveAsRnote = loaded.isNativeRnote
                adoptDocumentUri(it)
                onDocumentLoaded(loaded.document.copy(title = title))
                Toast.makeText(this, "Opened: $title", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** This uri is now the note's home: save over it, and start the picker beside it. */
    private fun adoptDocumentUri(uri: Uri) {
        DocumentUri.takePersistablePermission(this, uri)
        currentDocumentUri = uri
        pickerStartUri = uri
    }

    /** Writes back over the file the note came from. False when it has no file yet. */
    private fun saveInPlace(document: NoteDocument): Boolean {
        val target = currentDocumentUri ?: return false
        val success = FileManager.saveDocumentToUri(this, target, document, asRnote = saveAsRnote)
        if (success) {
            onSaveSucceeded?.invoke()
            Toast.makeText(this, "Saved ${document.title}", Toast.LENGTH_SHORT).show()
        } else {
            // The grant can be gone (file deleted, card pulled, permission revoked), so
            // fall back to asking for a destination rather than losing the edits.
            currentDocumentUri = null
            Toast.makeText(
                this, "Could not save over the file — choose a location", Toast.LENGTH_LONG
            ).show()
            launchSavePicker(document)
        }
        return true
    }

    /** Asks for a destination, then saves there and adopts it. */
    private fun launchSavePicker(document: NoteDocument) {
        pendingDocumentToSave = document
        val safeTitle = document.title.ifBlank { "MyNote" }
        if (saveAsRnote) {
            createRnoteLauncher.launch("$safeTitle.rnote")
        } else {
            createDocumentLauncher.launch("$safeTitle.json")
        }
    }

    private fun finishSaveAs(uri: Uri, asRnote: Boolean) {
        val document = pendingDocumentToSave ?: return
        pendingDocumentToSave = null
        if (!FileManager.saveDocumentToUri(this, uri, document, asRnote)) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            return
        }
        saveAsRnote = asRnote
        adoptDocumentUri(uri)
        // The picker lets the name be edited, so the note takes the name it was actually
        // saved under — otherwise the title in the bar and the file on disk disagree.
        DocumentUri.displayName(this, uri)?.let { onTitleAdopted?.invoke(DocumentUri.titleFrom(it)) }
        onSaveSucceeded?.invoke()
        Toast.makeText(
            this, if (asRnote) "Saved as .rnote" else "Saved as .json", Toast.LENGTH_SHORT
        ).show()
    }


    /**
     * One export request, parked between the sheet's Export button and the SAF result.
     * Rnote's export dialogs collect every answer and then pick a destination; SAF runs
     * the other way round, so the answers wait here until the destination comes back.
     */
    private data class PendingExport(
        val document: NoteDocument,
        val selection: List<Stroke>,
        val prefs: ExportPrefs,
        val baseName: String
    )

    private var pendingExport: PendingExport? = null

    // A CreateDocument contract is bound to one MIME type at registration, so there is a
    // launcher per format rather than one launcher with a variable type. They all start in
    // the note's own folder, like the save picker.
    private val exportSvgLauncher = registerForActivityResult(
        CreateDocumentNear(ExportFormat.SVG.mimeType)
    ) { uri -> uri?.let { finishSingleExport(it) } }

    private val exportPngLauncher = registerForActivityResult(
        CreateDocumentNear(ExportFormat.PNG.mimeType)
    ) { uri -> uri?.let { finishSingleExport(it) } }

    private val exportJpegLauncher = registerForActivityResult(
        CreateDocumentNear(ExportFormat.JPEG.mimeType)
    ) { uri -> uri?.let { finishSingleExport(it) } }

    private val exportPdfLauncher = registerForActivityResult(
        CreateDocumentNear(ExportFormat.PDF.mimeType)
    ) { uri -> uri?.let { finishSingleExport(it) } }

    /** Page export writes a file per page, so it asks for a folder, not a file name. */
    private val exportFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { finishPagesExport(it) } }

    private fun startExport(request: PendingExport) {
        pendingExport = request
        if (request.prefs.scope == ExportScope.PAGES) {
            // OpenDocumentTree takes the folder to start in as its input.
            exportFolderLauncher.launch(pickerStartUri)
        } else {
            val fileName = DocumentExporter.fileNameFor(request.baseName, request.prefs)
            when (request.prefs.format) {
                ExportFormat.SVG -> exportSvgLauncher.launch(fileName)
                ExportFormat.PNG -> exportPngLauncher.launch(fileName)
                ExportFormat.JPEG -> exportJpegLauncher.launch(fileName)
                ExportFormat.PDF -> exportPdfLauncher.launch(fileName)
            }
        }
    }

    private fun finishSingleExport(uri: Uri) {
        val request = pendingExport ?: return
        pendingExport = null
        reportExport(
            FileManager.exportToUri(this, uri, request.document, request.selection, request.prefs)
        )
    }

    private fun finishPagesExport(treeUri: Uri) {
        val request = pendingExport ?: return
        pendingExport = null
        reportExport(
            FileManager.exportPagesToTree(
                this, treeUri, request.document, request.prefs, request.baseName
            )
        )
    }

    private fun reportExport(result: DocumentExporter.Result) {
        val message = when (result) {
            is DocumentExporter.Result.Success ->
                if (result.fileCount == 1) "Exported" else "Exported ${result.fileCount} pages"
            is DocumentExporter.Result.Failure -> result.message
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private var onDocumentLoaded: (NoteDocument) -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Rnote's own breakpoint collapses its sidebar under 1250sp on a desktop window;
            // here, below Material's compact/medium 600dp boundary, the floating PenConfigStrip
            // would overlap most of the drawing area on a phone-width screen, so it's hidden
            // rather than degraded in place, and Page Settings falls back to a modal sheet
            // instead of a docked side panel.
            val isCompactWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

            // ── Persistent settings — loaded once from SharedPreferences ──────────
            var paperStyle by remember {
                mutableStateOf(SettingsManager.loadPaperStyle(this))
            }
            var toolConfig by remember {
                mutableStateOf(ToolConfig(allowFingerDrawing = SettingsManager.loadAllowFingerDrawing(this)))
            }

            // ── Document state ────────────────────────────────────────────────────
            var documentTitle by remember { mutableStateOf("My Note") }
            var isModified by remember { mutableStateOf(false) }
            var showRenameDialog by remember { mutableStateOf(false) }
            var showNewDocumentDialog by remember { mutableStateOf(false) }
            var renameFieldValue by remember { mutableStateOf("") }

            // ── UI sheet state ────────────────────────────────────────────────────
            // One canvas unit must be a constant physical size, so the viewport carries the
            // display's real scale (see ViewportState.displayScale) rather than drawing 1:1.
            val displayMetrics = LocalContext.current.resources.displayMetrics
            val displayScale = remember(displayMetrics) {
                ViewportState.displayScaleFor(
                    displayMetrics.xdpi, displayMetrics.ydpi, displayMetrics.densityDpi
                )
            }
            var viewportState by remember { mutableStateOf(ViewportState(displayScale = displayScale)) }
            var showPageSettings by remember { mutableStateOf(false) }
            var showExportSheet by remember { mutableStateOf(false) }
            // Kept across openings so a second export doesn't start from the defaults again.
            var exportPrefs by remember { mutableStateOf(ExportPrefs()) }

            // ── Stroke stacks ─────────────────────────────────────────────────────
            val strokes = remember { mutableStateListOf<Stroke>() }
            val undoStack = remember { mutableStateListOf<List<Stroke>>() }
            val redoStack = remember { mutableStateListOf<List<Stroke>>() }
            val selectedStrokes = remember { mutableStateListOf<Stroke>() }
            // Non-stroke elements (text/shapes/images) preserved from an imported native file.
            // Carried through save/export so they aren't silently dropped from opened .rnote files.
            var documentNativeElements by remember { mutableStateOf<List<NativeCanvasElement>>(emptyList()) }

            // ── Page indicator (2D grid position) ────────────────────────────────
            val currentPage: Int? = if (paperStyle.pageSize.isInfinite) null else {
                // Just pass a non-null sentinel — actual col/row shown in top bar title
                1
            }
            val pageGridLabel: String? = if (paperStyle.pageSize.isInfinite) null else {
                val pageW = paperStyle.effectivePageWidthPx
                val pageH = paperStyle.effectivePageHeightPx
                val col = floor(-viewportState.panOffset.x / viewportState.effectiveScale / (pageW + 40f)).toInt()
                val row = floor(-viewportState.panOffset.y / viewportState.effectiveScale / (pageH + 40f)).toInt()
                "${col + 1}, ${row + 1}"  // 1-indexed display
            }

            // ── Persist settings ──────────────────────────────────────────────────
            // Only ever from an explicit choice (the Page Settings sheet, the finger-draw
            // toggle). This used to be a SideEffect that mirrored every paperStyle change
            // into SharedPreferences, which meant opening any .rnote silently made that
            // file's format the app-wide default: open a desktop fixed-size note, start a
            // new note, and it was still Fixed Size — that is how a continuous-vertical
            // note got written to disk as `fixed_size`.
            val persistSettings = { style: PaperStyle ->
                SettingsManager.save(this, style, toolConfig.allowFingerDrawing)
            }

            // ── Document load handler ─────────────────────────────────────────────
            onDocumentLoaded = { doc ->
                strokes.clear()
                undoStack.clear()
                redoStack.clear()
                selectedStrokes.clear()
                strokes.addAll(doc.strokes)
                // The file's format is the document's, not the user's default — so it is
                // deliberately not persisted; the next new note starts from preferences.
                paperStyle = doc.paperStyle
                documentTitle = doc.title
                documentNativeElements = doc.nativeElements
                viewportState = ViewportState(displayScale = displayScale)
                isModified = false
            }

            // ── New document handler ──────────────────────────────────────────────
            // A harder reset than Clear Canvas: the title and the undo history go too,
            // so there's no way back — hence the confirmation when edits are unsaved.
            // The paper style goes back to the stored preference rather than surviving:
            // it belongs to the document now, so whatever an opened file brought with it
            // must not follow the user into their next note.
            val startNewDocument = {
                paperStyle = SettingsManager.loadPaperStyle(this)
                strokes.clear()
                undoStack.clear()
                redoStack.clear()
                selectedStrokes.clear()
                documentNativeElements = emptyList()
                documentTitle = "My Note"
                viewportState = ViewportState(displayScale = displayScale)
                isModified = false
                // Fresh notes save as .rnote — see saveAsRnote's declaration.
                saveAsRnote = true
                // No file yet, so the next Save has to ask for one.
                currentDocumentUri = null
            }

            // ── Save succeeded handler ────────────────────────────────────────────
            onSaveSucceeded = { isModified = false }
            onTitleAdopted = { name -> documentTitle = name }

            // ── S-Pen Air Action remote shortcuts ─────────────────────────────────
            performUndoAction = {
                // Gated on undoStack, not `strokes` — an empty canvas can still have undo
                // history (e.g. right after Clear Canvas), and that must stay undoable.
                if (undoStack.isNotEmpty()) {
                    redoStack.add(strokes.toList())
                    val previousState = undoStack.removeAt(undoStack.lastIndex)
                    strokes.clear()
                    strokes.addAll(previousState)
                    isModified = true
                }
            }

            performRedoAction = {
                if (redoStack.isNotEmpty()) {
                    undoStack.add(strokes.toList())
                    val nextState = redoStack.removeAt(redoStack.lastIndex)
                    strokes.clear()
                    strokes.addAll(nextState)
                    isModified = true
                }
            }

            BabyRnoteTheme(darkTheme = paperStyle.isDarkMode) {
                Scaffold(
                    topBar = {
                        RnoteTopBar(
                            paperStyle = paperStyle,
                            zoomScale = viewportState.zoomScale,
                            allowFingerDrawing = toolConfig.allowFingerDrawing,
                            isModified = isModified,
                            documentTitle = documentTitle,
                            currentPage = pageGridLabel,
                            onResetZoom = { viewportState = ViewportState(displayScale = displayScale) },
                            onTitleTap = {
                                renameFieldValue = documentTitle
                                showRenameDialog = true
                            },
                            onToggleFingerDrawing = {
                                toolConfig = toolConfig.copy(allowFingerDrawing = !toolConfig.allowFingerDrawing)
                                SettingsManager.save(
                                    this, paperStyle, toolConfig.allowFingerDrawing
                                )
                            },
                            onSaveDocument = {
                                val currentDoc = NoteDocument(
                                    title = documentTitle,
                                    paperStyle = paperStyle,
                                    strokes = strokes.toList(),
                                    nativeElements = documentNativeElements
                                )
                                // Straight back over the file it came from; only a note
                                // that has never been written asks where to go.
                                if (!saveInPlace(currentDoc)) launchSavePicker(currentDoc)
                            },
                            onSaveDocumentAs = {
                                launchSavePicker(
                                    NoteDocument(
                                        title = documentTitle,
                                        paperStyle = paperStyle,
                                        strokes = strokes.toList(),
                                        nativeElements = documentNativeElements
                                    )
                                )
                            },
                            onOpenDocument = {
                                openDocumentLauncher.launch(arrayOf("*/*", "application/json"))
                            },
                            onNewDocument = {
                                if (isModified) {
                                    showNewDocumentDialog = true
                                } else {
                                    startNewDocument()
                                }
                            },
                            onExport = { showExportSheet = true },
                            onClearCanvas = {
                                if (strokes.isNotEmpty() || documentNativeElements.isNotEmpty()) {
                                    undoStack.add(strokes.toList())
                                    redoStack.clear()
                                    strokes.clear()
                                    selectedStrokes.clear()
                                    documentNativeElements = emptyList()
                                    isModified = true
                                }
                            },
                            onOpenPageSettings = { showPageSettings = true }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        DrawingCanvas(
                            toolConfig = toolConfig,
                            paperStyle = paperStyle,
                            viewportState = viewportState,
                            strokes = strokes,
                            selectedStrokes = selectedStrokes,
                            onViewportChanged = { newViewport ->
                                viewportState = newViewport
                            },
                            onAddStroke = { newStroke ->
                                undoStack.add(strokes.toList())
                                redoStack.clear()
                                strokes.add(newStroke)
                                isModified = true
                            },
                            onEraseStart = {
                                // One snapshot for the whole eraser drag. This used to sit
                                // in onEraseStrokes, which fires per motion event, so
                                // rubbing out five strokes cost five undos to put back.
                                undoStack.add(strokes.toList())
                                redoStack.clear()
                            },
                            onEraseStrokes = { erased ->
                                val erasedIds = erased.map { it.id }.toSet()
                                strokes.removeAll { it.id in erasedIds }
                                isModified = true
                            },
                            onSelectionDragStart = {
                                undoStack.add(strokes.toList())
                                redoStack.clear()
                            },
                            onStrokesModified = { updatedStrokes ->
                                val updatedById = updatedStrokes.associateBy { it.id }
                                for (i in strokes.indices) {
                                    updatedById[strokes[i].id]?.let { strokes[i] = it }
                                }
                                isModified = true
                            },
                        )

                        // Top-center: stroke color + palette (matches Rnote's colorpicker.ui)
                        ColorPicker(
                            activeColor = toolConfig.currentActiveColor,
                            onColorSelected = { newColor ->
                                toolConfig = if (toolConfig.activeTool == ToolType.BRUSH && toolConfig.brushStyle == BrushStyle.MARKER) {
                                    toolConfig.copy(highlighterColor = newColor)
                                } else {
                                    toolConfig.copy(penColor = newColor)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 18.dp)
                        )

                        // Left edge, vertically centered: per-pen config (matches RnPensSideBar).
                        // Hidden below the width breakpoint (see isCompactWidth, top of file).
                        if (!isCompactWidth) PenConfigStrip(
                            toolConfig = toolConfig,
                            hasActiveSelection = selectedStrokes.isNotEmpty(),
                            onBrushStyleSelected = { style -> toolConfig = toolConfig.copy(brushStyle = style) },
                            onSizeChanged = { newSize -> toolConfig = toolConfig.updateActiveSize(newSize) },
                            onDeleteSelection = {
                                if (selectedStrokes.isNotEmpty()) {
                                    undoStack.add(strokes.toList())
                                    redoStack.clear()
                                    val ids = selectedStrokes.map { it.id }.toSet()
                                    strokes.removeAll { it.id in ids }
                                    selectedStrokes.clear()
                                    isModified = true
                                }
                            },
                            onDuplicateSelection = {
                                if (selectedStrokes.isNotEmpty()) {
                                    undoStack.add(strokes.toList())
                                    redoStack.clear()
                                    val offset = 20f
                                    val duplicates = selectedStrokes.map { s ->
                                        s.copy(
                                            id = java.util.UUID.randomUUID().toString(),
                                            points = s.points.map { p -> StrokePoint(p.x + offset, p.y + offset, p.pressure) }
                                        )
                                    }
                                    strokes.addAll(duplicates)
                                    selectedStrokes.clear()
                                    selectedStrokes.addAll(duplicates)
                                    isModified = true
                                }
                            },
                            onSelectAll = {
                                selectedStrokes.clear()
                                selectedStrokes.addAll(strokes)
                            },
                            onDeselectAll = { selectedStrokes.clear() },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 18.dp)
                        )

                        // Bottom-center: pen switcher + undo/redo (matches Rnote's penpicker.ui)
                        PenPicker(
                            toolConfig = toolConfig,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            onToolSelected = { newTool ->
                                toolConfig = toolConfig.copy(activeTool = newTool)
                                if (newTool != ToolType.SELECTOR) selectedStrokes.clear()
                            },
                            onUndo = { performUndoAction?.invoke() },
                            onRedo = { performRedoAction?.invoke() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 28.dp)
                        )

                        // Docked at tablet width (right edge, opposite PenConfigStrip); falls
                        // back to a modal sheet below the breakpoint.
                        if (showPageSettings) {
                            PageSettingsSheet(
                                paperStyle = paperStyle,
                                onPaperStyleChanged = {
                                    paperStyle = it
                                    // An explicit choice is both an edit to this document
                                    // and the default the next new note should start from.
                                    persistSettings(it)
                                    isModified = true
                                },
                                onDismiss = { showPageSettings = false },
                                dockedAsSidePanel = !isCompactWidth,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }

                    // ── Export Sheet ──────────────────────────────────────────────
                    if (showExportSheet) {
                        val exportDocument = NoteDocument(
                            title = documentTitle,
                            paperStyle = paperStyle,
                            strokes = strokes.toList(),
                            nativeElements = documentNativeElements
                        )
                        ExportSheet(
                            paperStyle = paperStyle,
                            prefs = exportPrefs,
                            pageCount = DocumentExporter.pagesFor(exportDocument, exportPrefs).size,
                            hasSelection = selectedStrokes.isNotEmpty(),
                            onPrefsChanged = { exportPrefs = it },
                            onDismiss = { showExportSheet = false },
                            onExport = {
                                showExportSheet = false
                                startExport(
                                    PendingExport(
                                        document = exportDocument,
                                        selection = selectedStrokes.toList(),
                                        prefs = exportPrefs,
                                        baseName = documentTitle
                                    )
                                )
                            }
                        )
                    }

                    // ── New Document Confirmation ─────────────────────────────────
                    if (showNewDocumentDialog) {
                        AlertDialog(
                            onDismissRequest = { showNewDocumentDialog = false },
                            title = { Text("Discard unsaved changes?") },
                            text = { Text("\"$documentTitle\" has unsaved changes. Starting a new note will discard them.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showNewDocumentDialog = false
                                    startNewDocument()
                                }) { Text("Discard") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showNewDocumentDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    // ── Rename Dialog ─────────────────────────────────────────────
                    if (showRenameDialog) {
                        AlertDialog(
                            onDismissRequest = { showRenameDialog = false },
                            title = { Text("Rename Note") },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = renameFieldValue,
                                        onValueChange = { renameFieldValue = it },
                                        label = { Text("Title") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    // Renaming the file itself isn't ours to do: SAF hands
                                    // back a new uri for a renamed document, and a
                                    // single-document grant doesn't extend to it, so the
                                    // note would lose the file it saves to. Save As is the
                                    // way to put the note in a file of the new name.
                                    if (currentDocumentUri != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "The file keeps its name — use Save As to " +
                                                "write this note to a new one.",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val trimmed = renameFieldValue.trim()
                                    if (trimmed.isNotBlank()) {
                                        documentTitle = trimmed
                                        isModified = true
                                    }
                                    showRenameDialog = false
                                }) { Text("Rename") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Nothing to do: preferences are written at the moment they are chosen (see
        // persistSettings), and the open document's own style is not a preference.
    }

    /**
     * Samsung S-Pen Air Action Remote Button Key Event Handler:
     * - Single Press / Page Down: Undo
     * - Page Up: Redo
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_PAGE_DOWN -> {
                performUndoAction?.invoke()
                true
            }
            KeyEvent.KEYCODE_PAGE_UP -> {
                performRedoAction?.invoke()
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
}
