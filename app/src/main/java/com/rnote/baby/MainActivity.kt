package com.rnote.baby

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rnote.baby.model.BrushStyle
import com.rnote.baby.model.NativeCanvasElement
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.model.Stroke
import com.rnote.baby.model.StrokePoint
import com.rnote.baby.model.ToolConfig
import com.rnote.baby.model.ToolType
import com.rnote.baby.model.ViewportState
import com.rnote.baby.storage.FileManager
import com.rnote.baby.storage.SettingsManager
import kotlin.math.floor
import com.rnote.baby.ui.canvas.DrawingCanvas
import com.rnote.baby.ui.components.ColorPicker
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
    private var pendingDocumentToExport: NoteDocument? = null
    // Defaults to true since the app's whole purpose is desktop Rnote interop —
    // a brand-new note should save as .rnote, not fall back to our internal .json format.
    private var saveAsRnote: Boolean = true

    // Called after save so we can clear isModified
    private var onSaveSucceeded: (() -> Unit)? = null

    /** Launcher for saving as our JSON format. */
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            pendingDocumentToSave?.let { doc ->
                val success = FileManager.saveDocumentToUri(this, it, doc, asRnote = false)
                if (success) { onSaveSucceeded?.invoke(); Toast.makeText(this, "Saved as .json", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Launcher for saving as native .rnote (GZIP+JSON). */
    private val createRnoteLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            pendingDocumentToSave?.let { doc ->
                val success = FileManager.saveDocumentToUri(this, it, doc, asRnote = true)
                if (success) { onSaveSucceeded?.invoke(); Toast.makeText(this, "Saved as .rnote", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val loadedDoc = FileManager.loadDocumentFromUri(this, it)
            if (loadedDoc != null) {
                onDocumentLoaded(loadedDoc)
                Toast.makeText(this, "Opened: ${loadedDoc.title}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportSvgLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        uri?.let {
            pendingDocumentToExport?.let { doc ->
                val success = FileManager.exportSvgToUri(this, it, doc)
                if (success) {
                    Toast.makeText(this, "Exported SVG!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "SVG export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val exportPngLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let {
            pendingDocumentToExport?.let { doc ->
                val success = FileManager.exportPngToUri(this, it, doc)
                if (success) {
                    Toast.makeText(this, "Exported PNG!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PNG export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

            // ── Persist settings whenever they change ─────────────────────────────
            SideEffect {
                SettingsManager.save(this, paperStyle, toolConfig.allowFingerDrawing)
            }

            // ── Document load handler ─────────────────────────────────────────────
            onDocumentLoaded = { doc ->
                strokes.clear()
                undoStack.clear()
                redoStack.clear()
                selectedStrokes.clear()
                strokes.addAll(doc.strokes)
                paperStyle = doc.paperStyle
                documentTitle = doc.title
                documentNativeElements = doc.nativeElements
                viewportState = ViewportState(displayScale = displayScale)
                isModified = false
                // Flag that this was opened from a .rnote file so Save goes back to .rnote
                saveAsRnote = doc.nativeElements.isNotEmpty() || doc.title == "Imported Note"
            }

            // ── Save succeeded handler ────────────────────────────────────────────
            onSaveSucceeded = { isModified = false }

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
                            },
                            onSaveDocument = {
                                val safeTitle = documentTitle.ifBlank { "MyNote" }
                                val currentDoc = NoteDocument(
                                    title = documentTitle,
                                    paperStyle = paperStyle,
                                    strokes = strokes.toList(),
                                    nativeElements = documentNativeElements
                                )
                                pendingDocumentToSave = currentDoc
                                if (saveAsRnote) {
                                    createRnoteLauncher.launch("$safeTitle.rnote")
                                } else {
                                    createDocumentLauncher.launch("$safeTitle.json")
                                }
                            },
                            onOpenDocument = {
                                openDocumentLauncher.launch(arrayOf("*/*", "application/json"))
                            },
                            onExportSvg = {
                                val safeTitle = documentTitle.ifBlank { "MyNote" }
                                val currentDoc = NoteDocument(
                                    title = documentTitle,
                                    paperStyle = paperStyle,
                                    strokes = strokes.toList(),
                                    nativeElements = documentNativeElements
                                )
                                pendingDocumentToExport = currentDoc
                                exportSvgLauncher.launch("$safeTitle.svg")
                            },
                            onExportPng = {
                                val safeTitle = documentTitle.ifBlank { "MyNote" }
                                val currentDoc = NoteDocument(
                                    title = documentTitle,
                                    paperStyle = paperStyle,
                                    strokes = strokes.toList(),
                                    nativeElements = documentNativeElements
                                )
                                pendingDocumentToExport = currentDoc
                                exportPngLauncher.launch("$safeTitle.png")
                            },
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
                            onEraseStrokes = { erased ->
                                undoStack.add(strokes.toList())
                                redoStack.clear()
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
                            onUndoRequested = { performUndoAction?.invoke() }
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
                                onPaperStyleChanged = { paperStyle = it },
                                onDismiss = { showPageSettings = false },
                                dockedAsSidePanel = !isCompactWidth,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }

                    // ── Rename Dialog ─────────────────────────────────────────────
                    if (showRenameDialog) {
                        AlertDialog(
                            onDismissRequest = { showRenameDialog = false },
                            title = { Text("Rename Note") },
                            text = {
                                OutlinedTextField(
                                    value = renameFieldValue,
                                    onValueChange = { renameFieldValue = it },
                                    label = { Text("Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
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
        // Settings are also saved via SideEffect on every change, but we save
        // on stop as a belt-and-suspenders guarantee before the process is killed.
        // We don't have access to Compose state here, so SideEffect handles it.
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
