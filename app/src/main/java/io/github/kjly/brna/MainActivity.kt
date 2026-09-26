package io.github.kjly.brna

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.provider.DocumentsContract
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.kjly.brna.audio.PenSounds
import io.github.kjly.brna.export.DocumentExporter
import io.github.kjly.brna.export.ExportFormat
import io.github.kjly.brna.export.ExportLayout
import io.github.kjly.brna.export.ExportPrefs
import io.github.kjly.brna.export.ExportScope
import io.github.kjly.brna.export.NotePrintAdapter
import io.github.kjly.brna.export.PageThumbnails
import io.github.kjly.brna.export.ShareTarget
import io.github.kjly.brna.model.BrushStyle
import io.github.kjly.brna.model.FixedPages
import io.github.kjly.brna.model.LayoutMode
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.NoteDocument
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.model.PenFavorite
import io.github.kjly.brna.model.brushFavorite
import io.github.kjly.brna.model.withFavorite
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.TextAlignment
import io.github.kjly.brna.model.TextFormatting
import io.github.kjly.brna.model.TextToggle
import io.github.kjly.brna.model.ToolConfig
import io.github.kjly.brna.model.ToolType
import io.github.kjly.brna.model.PenShortcutState
import io.github.kjly.brna.model.ShortcutKey
import io.github.kjly.brna.model.UndoHistory
import io.github.kjly.brna.model.ViewportState
import io.github.kjly.brna.model.SnapPositions
import io.github.kjly.brna.render.NativeElementRenderer
import io.github.kjly.brna.storage.ContentHash
import io.github.kjly.brna.storage.DocumentUri
import io.github.kjly.brna.storage.FileManager
import io.github.kjly.brna.storage.FolderBrowser
import io.github.kjly.brna.storage.FolderListing
import io.github.kjly.brna.storage.ImageImport
import io.github.kjly.brna.storage.NativeEditing
import io.github.kjly.brna.storage.PdfImporter
import io.github.kjly.brna.storage.PenFavorites
import io.github.kjly.brna.storage.RecentFiles
import io.github.kjly.brna.storage.Recovery
import io.github.kjly.brna.storage.SettingsManager
import io.github.kjly.brna.storage.Workspaces
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.kjly.brna.ui.canvas.DrawingCanvas
import io.github.kjly.brna.ui.KeyboardShortcuts
import io.github.kjly.brna.ui.PenRemote
import io.github.kjly.brna.ui.Shortcut
import io.github.kjly.brna.ui.canvas.SelectionManager
import io.github.kjly.brna.ui.components.ColorPicker
import io.github.kjly.brna.ui.components.ExportSheet
import io.github.kjly.brna.ui.components.PageSettingsSheet
import io.github.kjly.brna.ui.components.PenConfigStrip
import io.github.kjly.brna.ui.components.PenPicker
import io.github.kjly.brna.ui.components.RnoteTopBar
import io.github.kjly.brna.ui.components.PageOverviewDialog
import io.github.kjly.brna.ui.components.RecentFilesDialog
import io.github.kjly.brna.ui.components.InlineTextEditor
import io.github.kjly.brna.ui.components.NoteTab
import io.github.kjly.brna.ui.components.NoteTabBar
import io.github.kjly.brna.ui.components.TextBoxStyle
import io.github.kjly.brna.ui.components.WorkspaceBrowser
import io.github.kjly.brna.ui.theme.BabyRnoteTheme

/**
 * One undo step: the ink and the desktop elements together, so undoing a Clear Canvas
 * or an erased shape brings back everything it took.
 */
private data class DocSnapshot(val strokes: List<Stroke>, val natives: List<NativeCanvasElement>)

/**
 * A note open in a tab other than the one on screen: everything needed to show it again
 * as it was left — its undo history and the part of it in view included. The note on
 * screen lives in the UI's own state instead, as it did before there were tabs.
 */
private data class ParkedTab(
    val title: String,
    val isModified: Boolean,
    val strokes: List<Stroke>,
    val natives: List<NativeCanvasElement>,
    val paperStyle: PaperStyle,
    val undo: List<DocSnapshot>,
    val redo: List<DocSnapshot>,
    val viewport: ViewportState,
    val uri: Uri?,
    val knownLastModified: Long?,
    val saveAsRnote: Boolean,
    val knownContentHash: String? = null
)

/** Copied ink and desktop elements. */
private class Clip(val strokes: List<Stroke>, val natives: List<NativeCanvasElement>) {
    /** [minX, minY, maxX, maxY] around everything copied; null if nothing was. */
    fun bounds(): FloatArray? {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (s in strokes) for (p in s.points) {
            l = minOf(l, p.x); t = minOf(t, p.y); r = maxOf(r, p.x); b = maxOf(b, p.y)
        }
        for (el in natives) {
            l = minOf(l, el.minX); t = minOf(t, el.minY); r = maxOf(r, el.maxX); b = maxOf(b, el.maxY)
        }
        return if (l <= r && t <= b) floatArrayOf(l, t, r, b) else null
    }
}

/**
 * The selector's clipboard. It lives as long as the app does, not the note, so what is
 * copied in one note can be pasted into the next one opened.
 */
private object SelectionClipboard {
    var clip by mutableStateOf<Clip?>(null)
}

/** Width of a page picture in the page overview, in px. */
private const val THUMBNAIL_WIDTH_PX = 320

/** How far a paste lands from the original when both are in view, as Duplicate does. */
private const val PASTE_OFFSET = 20f

/** How far in from the view's corner an inserted image lands, in document units at 100%. */
private const val IMPORT_OFFSET = 32f

/** How long a shared file is kept for the app it went to, in ms. */
private const val SHARED_FILE_LIFETIME_MS = 60 * 60 * 1000L

/** Rnote's `StrokeContent::CLIPBOARD_EXPORT_MARGIN` and `Engine::STROKE_EXPORT_IMAGE_SCALE`. */
private const val CLIPBOARD_IMAGE_MARGIN = 6f
private const val CLIPBOARD_IMAGE_SCALE = 1.8f

/**
 * The text box the Typewriter is typing into: where a new one goes (or where the box
 * stands), the box as it is in the document now — null until the first character, and
 * again once everything is deleted — the last box there was, to type back into, the text
 * field's value, and what the switches set for text typed next.
 */
private data class TextSession(
    /** New for every box tapped, so the text field starts afresh. */
    val id: Int,
    val x: Float,
    val y: Float,
    val element: NativeTextElement?,
    val template: NativeTextElement?,
    val value: TextFieldValue,
    val pending: Map<TextToggle, Boolean> = emptyMap(),
    /** Whether this box's undo step is on the stack yet: one per box, however much is typed. */
    val undoTaken: Boolean = false
)

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
     *
     * State, so the workspace panel can mark the open note as it changes.
     */
    private var currentDocumentUri by mutableStateOf<Uri?>(null)

    /**
     * Where the SAF picker should open. Without `EXTRA_INITIAL_URI` it starts wherever it
     * was last left, which for a first save is rarely anywhere near the note.
     */
    private var pickerStartUri: Uri? = null

    // Called after a save with what was written, so the UI can clear isModified — but only
    // if nothing changed while the write was running.
    private var onSaveSucceeded: ((NoteDocument) -> Unit)? = null

    /**
     * The open note when it has unsaved changes, null when there is nothing to protect.
     * Installed by the UI, read by autosave.
     */
    private var unsavedDocument: (() -> NoteDocument?)? = null

    /**
     * The file's last-modified time as of our own last open or save of it. A different
     * value at save time means somebody else wrote it in between — Toni on the laptop,
     * through a synced Drive folder — and saving over it would silently lose their work.
     */
    private var knownLastModified: Long? = null

    /** A save that found the file changed elsewhere, waiting for the user to decide. */
    private var pendingConflict by mutableStateOf<NoteDocument?>(null)

    /** Notes recovered from the last session, one per tab they were in, waiting to be offered back. */
    private var pendingRecovery by mutableStateOf<List<Recovery.Pending>>(emptyList())

    /** Set when [incomingDocument] is the open note reloaded from its file. */
    private var incomingKeepsView = false

    /** Set when [incomingDocument] is a Xournal++ file made into a note: new, and not yet saved anywhere. */
    private var incomingUnsaved = false

    // ── Tabs ──────────────────────────────────────────────────────────────────

    /**
     * Tab ids come from the clock, so none is ever the id of a tab from an earlier run —
     * whose recovery slot (see [Recovery]) may still be waiting to be offered back.
     */
    private var nextTabId = System.currentTimeMillis()

    private fun newTabId() = nextTabId++

    /** The open tabs, in the tab bar's order. */
    private val tabOrder = mutableStateListOf<Long>()

    /** The tab on screen; its note is the one all of this activity's file handling is about. */
    private var activeTab by mutableLongStateOf(0L)

    /** Every other tab's note. */
    private val parkedTabs = mutableStateMapOf<Long, ParkedTab>()

    /**
     * Installed by the UI. With [uri] already open in a tab, shows that tab and says so;
     * opening it a second time would make two copies that save over each other.
     */
    private var showTabWith: ((Uri) -> Boolean)? = null

    /**
     * Installed by the UI: called as a note being opened arrives. It gets a new tab of its
     * own, unless the tab on screen is an untouched new note it can take the place of.
     */
    private var makeRoomForIncoming: (() -> Unit)? = null

    /** Set when the file's own name becomes the note's title (on open, and on save-as). */
    private var onTitleAdopted: ((String) -> Unit)? = null

    /**
     * Non-null while a file is being read or written off the main thread; shown as a
     * progress card. Reading a large .rnote (imported PDF pages are megabytes of SVG) on
     * the main thread froze the whole UI and could trip "app isn't responding".
     */
    private var busyMessage by mutableStateOf<String?>(null)

    /**
     * A note that has finished loading and waits to be handed to the UI. Going through
     * state rather than calling [onDocumentLoaded] directly matters for "Open with": that
     * load can finish before the first composition has installed the real handler.
     */
    private var incomingDocument by mutableStateOf<NoteDocument?>(null)

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

    /** Where imported PDF pages go: page width, format height, and the top of the first. */
    private class PdfImportTarget(val pageWidth: Float, val formatHeight: Float, val startY: Float)

    /** Installed by the UI, which knows the note's format and where its content ends. */
    private var pdfImportTarget: (() -> PdfImportTarget)? = null

    /** Installed by the UI: adds imported pages to the open note. */
    private var onPdfImported: ((List<NativeVectorImageElement>) -> Unit)? = null

    private val importPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importPdf(it) } }

    /** Rnote's "Import" (Ctrl+Shift+I): a PDF or a picture, each into the open note its own way. */
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importFile(it) } }

    private fun importFile(uri: Uri) {
        val type = contentResolver.getType(uri)
        val name = DocumentUri.displayName(this, uri).orEmpty().lowercase()
        if (type == "application/pdf" || name.endsWith(".pdf")) importPdf(uri) else insertImage(uri)
    }

    /**
     * Rnote's "Print" (Ctrl+P): the note to Android's print dialog, where the printer,
     * the pages and the paper are chosen.
     */
    private fun printDocument(document: NoteDocument) {
        val manager = getSystemService(PrintManager::class.java) ?: return
        try {
            manager.print(document.title.ifBlank { "Note" }, NotePrintAdapter(document), null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Printing isn't available", Toast.LENGTH_LONG).show()
        }
    }

    /** Installed by the UI: carries out a keyboard shortcut; false when there was nothing to do. */
    private var shortcutHandler: ((Shortcut) -> Boolean)? = null

    /** Installed by the UI: Rnote's Ctrl+Space button shortcut went down (true) or came up. */
    private var penShortcutKeyHandler: ((ShortcutKey, Boolean) -> Unit)? = null
    private var ctrlSpaceDown = false

    /** Renders the PDF's pages off the main thread and adds them to the open note. */
    private fun importPdf(uri: Uri) {
        val target = pdfImportTarget?.invoke() ?: return
        if (busyMessage != null) return
        busyMessage = "Importing PDF…"
        lifecycleScope.launch {
            val pages = withContext(Dispatchers.IO) {
                try {
                    PdfImporter.import(
                        this@MainActivity, uri, target.pageWidth, target.formatHeight, target.startY
                    )
                } catch (e: Throwable) {
                    // A password-protected or broken PDF, or one too big to render.
                    e.printStackTrace()
                    null
                }
            }
            busyMessage = null
            if (pages.isNullOrEmpty()) {
                Toast.makeText(this@MainActivity, "Could not import the PDF", Toast.LENGTH_LONG).show()
            } else {
                onPdfImported?.invoke(pages)
                Toast.makeText(
                    this@MainActivity,
                    if (pages.size == 1) "Imported 1 page" else "Imported ${pages.size} pages",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Installed by the UI: where an image of this many pixels goes in the current view. */
    private var imagePlacement: ((Int, Int) -> NativeEditing.ImagePlacement)? = null

    /** Installed by the UI: adds an inserted image to the open note. */
    private var onImageInserted: ((NativeBitmapElement) -> Unit)? = null

    /** The system photo picker, or a plain image picker where there is none. */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { insertImage(it) } }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken -> if (taken) insertImage(cameraPhotoUri(), isCameraPhoto = true) }

    /**
     * The one file the camera app writes into. Always the same, so nothing has to survive
     * the app being reclaimed while the camera is open.
     */
    private fun cameraPhotoFile() = java.io.File(java.io.File(cacheDir, "camera").apply { mkdirs() }, "photo.jpg")

    private fun cameraPhotoUri(): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", cameraPhotoFile())

    private fun takePhoto() {
        try {
            takePhotoLauncher.launch(cameraPhotoUri())
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Reads the picture off the main thread and adds it to the open note where desktop
     * Rnote would put it (see [NativeEditing.placeImage]).
     */
    private fun insertImage(uri: Uri, isCameraPhoto: Boolean = false) {
        val place = imagePlacement ?: return
        if (busyMessage != null) return
        busyMessage = "Inserting image…"
        lifecycleScope.launch {
            val pixels = withContext(Dispatchers.IO) {
                try {
                    ImageImport.read(this@MainActivity, uri)
                } catch (e: Throwable) {
                    // OutOfMemoryError included: an image too big to read is a failed insert.
                    e.printStackTrace()
                    null
                } finally {
                    // The photo is in the note now (or unusable); no need to keep megabytes of it.
                    if (isCameraPhoto) cameraPhotoFile().delete()
                }
            }
            busyMessage = null
            val image = pixels?.let {
                NativeEditing.createImage(it.rgbaBase64, it.width, it.height, place(it.width, it.height))
            }
            if (image == null) {
                Toast.makeText(this@MainActivity, "Could not insert the image", Toast.LENGTH_LONG).show()
            } else {
                onImageInserted?.invoke(image)
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        OpenDocumentNear()
    ) { uri -> uri?.let { openDocument(it) } }

    /**
     * Reads [uri] off the main thread and hands the note to the UI. Shared by the Open
     * picker and by "Open with" from other apps.
     *
     * [reload] is the conflict dialog's "Load theirs": the open note's own file, read again
     * as it is now, with the changes made here thrown away and the view left where it was.
     */
    private fun openDocument(
        uri: Uri,
        fromRecent: Boolean = false,
        reload: Boolean = false,
        /** A reload the app decided on itself, having seen a newer version of the file. */
        automatic: Boolean = false
    ) {
        if (busyMessage != null) return
        // Already open in a tab: that tab is shown instead of a second copy.
        if (!reload && showTabWith?.invoke(uri) == true) return
        busyMessage = when {
            automatic -> "Loading the newer version…"
            reload -> "Loading their version…"
            else -> "Opening…"
        }
        val slot = activeTab
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Taken before reading, so a sync that lands mid-read counts as a change
                    // made elsewhere rather than being taken for the version that was read.
                    val lastModified = DocumentUri.lastModified(this@MainActivity, uri)
                    FileManager.loadDocumentFromUri(this@MainActivity, uri)
                        ?.let {
                            // Discarded on purpose; don't offer them back on the next launch.
                            if (reload) Recovery.clear(this@MainActivity, slot)
                            Triple(it, DocumentUri.displayName(this@MainActivity, uri), lastModified)
                        }
                } catch (e: Throwable) {
                    // OutOfMemoryError included: a file too big to hold is a failed open,
                    // not a crash.
                    e.printStackTrace()
                    null
                }
            }
            busyMessage = null
            if (result != null) {
                val (loaded, fileName, lastModified) = result
                // The file's own name wins over the title inside it: a .rnote carries no
                // title at all, and our JSON's title is only what it was last renamed to.
                val title = fileName?.let(DocumentUri::titleFrom) ?: loaded.document.title
                // A new tab for it, before anything below replaces what the one on screen holds.
                if (!reload) makeRoomForIncoming?.invoke()
                // The bytes decide the format it saves back as. The old test was the
                // "Imported Note" placeholder title, which said nothing about the file.
                saveAsRnote = loaded.isNativeRnote
                if (loaded.imported) {
                    // A Xournal++ file becomes a new note, as in Rnote: saved as an .rnote
                    // beside it, never written back over it.
                    currentDocumentUri = null
                    pickerStartUri = uri
                    knownLastModified = null
                    knownContentHash = null
                    incomingUnsaved = true
                } else {
                    adoptDocumentUri(uri, title)
                    // Set here on the main thread together with incomingDocument, never earlier:
                    // autosave must not see the new file's baseline while the old note is still
                    // open, or it would write that note over the file just read.
                    knownLastModified = lastModified
                    knownContentHash = loaded.contentHash
                }
                incomingKeepsView = reload
                incomingDocument = loaded.document.copy(title = title)
                Toast.makeText(
                    this@MainActivity,
                    when {
                        automatic -> "Newer version of $title loaded"
                        reload -> "Loaded their version of $title"
                        loaded.imported -> "Imported: $title — Save keeps it as an .rnote"
                        else -> "Opened: $title"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } else if (fromRecent) {
                // Moved, deleted, or its access withdrawn: an entry that can only fail goes.
                RecentFiles.remove(this@MainActivity, uri.toString())
                Toast.makeText(
                    this@MainActivity,
                    "Could not open it — it may have been moved or deleted. Try Open…",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this@MainActivity, "Could not open file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** "Open with" from a file manager, Drive, a mail or chat app. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let { openDocument(it) }
    }

    /** This uri is now the note's home: save over it, and start the picker beside it. */
    private fun adoptDocumentUri(uri: Uri, title: String) {
        DocumentUri.takePersistablePermission(this, uri)
        currentDocumentUri = uri
        pickerStartUri = uri
        RecentFiles.add(this, uri, title)
    }

    /**
     * Opens a note from the recent list or a workspace, in a tab of its own. The note on
     * screen is saved first, as leaving for the picker does, so the tab it stays in holds
     * nothing its file doesn't — and whatever can't be saved stays open there, unharmed.
     */
    private fun openRecent(uri: Uri) {
        if (busyMessage != null) return
        lifecycleScope.launch {
            autosaveNow()
            openDocument(uri, fromRecent = true)
        }
    }

    /** Writes back over the file the note came from. False when it has no file yet. */
    private fun saveInPlace(document: NoteDocument, overwriteChanges: Boolean = false): Boolean {
        val target = currentDocumentUri ?: return false
        if (busyMessage != null) return true
        busyMessage = "Saving…"
        val asRnote = saveAsRnote
        val known = knownLastModified
        val knownHash = knownContentHash
        val slot = activeTab
        lifecycleScope.launch {
            savesRunning++
            try {
                if (!overwriteChanges && withContext(Dispatchers.IO) { changedElsewhere(target, known, knownHash) }) {
                    busyMessage = null
                    pendingConflict = document
                    return@launch
                }
                val written = writeLock.withLock { withContext(Dispatchers.IO) { writeDocument(target, document, asRnote) } }
                busyMessage = null
                if (written != null) {
                    afterSave(target, document, slot, written)
                    Toast.makeText(this@MainActivity, "Saved ${document.title}", Toast.LENGTH_SHORT).show()
                } else {
                    // The grant can be gone (file deleted, card pulled, permission revoked, or
                    // a read-only "Open with" grant), so fall back to asking for a destination
                    // rather than losing the edits.
                    currentDocumentUri = null
                    Toast.makeText(
                        this@MainActivity, "Could not save over the file — choose a location", Toast.LENGTH_LONG
                    ).show()
                    launchSavePicker(document)
                }
            } finally {
                savesRunning--
            }
        }
        return true
    }

    /**
     * Whether [uri] was written since [known]: [knownLastModified] as it was when the note
     * being written was captured. Passed in rather than read here, because a reload that
     * finishes in between moves it on to the reloaded file. Blocking; call from
     * [Dispatchers.IO].
     *
     * A new time alone isn't a new version: Drive, for one, can touch a file's time after
     * uploading it, not a byte different from what this app wrote. So when the bytes it
     * held then are known ([knownHash]), the file is read and fingerprinted, and only
     * different bytes count; the same ones just move the known time on, so it isn't read
     * again at every look.
     */
    private fun changedElsewhere(uri: Uri, known: Long?, knownHash: String?): Boolean {
        if (known == null) return false
        val now = DocumentUri.lastModified(this, uri) ?: return false
        if (now == known) return false
        if (knownHash == null) return true
        val hash = try {
            contentResolver.openInputStream(uri)?.use { ContentHash.of(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (hash != knownHash) return true
        lifecycleScope.launch {
            if (uri == currentDocumentUri && known == knownLastModified) knownLastModified = now
        }
        return false
    }

    /**
     * The [ContentHash] of the open note's file as this app last read or wrote it; null
     * when unknown. Kept beside [knownLastModified], see [changedElsewhere].
     */
    private var knownContentHash: String? = null

    /**
     * Bookkeeping after any successful write of [document] to [uri], the note of tab
     * [slot]; [hash] is the fingerprint of the bytes written.
     */
    private suspend fun afterSave(uri: Uri, document: NoteDocument, slot: Long, hash: String) {
        knownLastModified = withContext(Dispatchers.IO) {
            Recovery.clear(this@MainActivity, slot)
            DocumentUri.lastModified(this@MainActivity, uri)
        }
        knownContentHash = hash
        onSaveSucceeded?.invoke(document)
        filesRefresh++
    }

    /**
     * Writes of the open note under way, from the first byte until [afterSave] has taken
     * the file's new last-modified time. Meanwhile the file looks changed although only
     * this app changed it — the save on leaving the app can still be running on return —
     * so [checkForNewerVersion] waits for the next chance.
     */
    private var savesRunning = 0

    /**
     * Whether the open note's file was written elsewhere since this app opened or saved
     * it — Toni saving on the laptop, synced through Drive. With nothing unsaved here the
     * newer version is simply loaded, the view kept where it was; with unsaved changes
     * the conflict dialog asks what to do. Run on coming back to the app and on opening
     * the side panel, and every [NEWER_VERSION_POLL_MS] while the app is open.
     *
     * The regular look ([whileIdle]) only ever loads: with anything unsaved here, or a
     * text box being typed into, it leaves the file alone rather than put a dialog in
     * front of someone in mid-sentence. The next return to the app, or the next save,
     * brings the question up then.
     */
    private fun checkForNewerVersion(whileIdle: Boolean = false) {
        val uri = currentDocumentUri ?: return
        val known = knownLastModified ?: return
        val knownHash = knownContentHash
        if (busyMessage != null || pendingConflict != null || savesRunning > 0) return
        val interrupting = { unsavedDocument?.invoke() != null || reloadWouldInterrupt?.invoke() == true }
        if (whileIdle && interrupting()) return
        lifecycleScope.launch {
            val changed = withContext(Dispatchers.IO) { changedElsewhere(uri, known, knownHash) }
            // Something may have happened meanwhile: another note opened, a save.
            if (!changed || uri != currentDocumentUri || known != knownLastModified ||
                busyMessage != null || pendingConflict != null || savesRunning > 0
            ) {
                return@launch
            }
            if (whileIdle && interrupting()) return@launch
            val unsaved = unsavedDocument?.invoke()
            if (unsaved == null) openDocument(uri, reload = true, automatic = true) else pendingConflict = unsaved
        }
    }

    /** Installed by the UI: whether a reload now would cut into something — a text box being typed into. */
    private var reloadWouldInterrupt: (() -> Boolean)? = null

    // ── Workspaces ────────────────────────────────────────────────────────────

    /** Desktop Rnote's workspaces: the folders the side panel shows. */
    private var workspaces by mutableStateOf<List<Workspaces.Workspace>>(emptyList())

    /** The uri of the workspace the side panel shows. */
    private var selectedWorkspace by mutableStateOf<String?>(null)

    /** Bumped after a save or a new note, so the side panel lists its folder again. */
    private var filesRefresh by mutableIntStateOf(0)

    /** The subfolder of the workspace the side panel was left in (see WorkspaceBrowser). */
    private var filesPath by mutableStateOf<List<Pair<String, String>>>(emptyList())

    private val addWorkspaceLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree -> tree?.let { addWorkspace(it) } }

    /**
     * Keeps the grant to the folder [tree] and adds it as a workspace named after the
     * folder — or, picked again after its grant was lost, brings the one there was back.
     */
    private fun addWorkspace(tree: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "The app can't keep access to that folder", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching { DocumentUri.displayName(this@MainActivity, FolderBrowser.uriOf(tree, FolderBrowser.rootId(tree))) }
                    .getOrNull()
            } ?: "Workspace"
            val key = tree.toString()
            val existing = workspaces.firstOrNull { it.uri == key }
            workspaces = if (existing != null) workspaces else workspaces + Workspaces.Workspace(key, name, Workspaces.nextColor(workspaces))
            Workspaces.save(this@MainActivity, workspaces)
            selectWorkspace(key)
            filesRefresh++
        }
    }

    private fun selectWorkspace(uri: String?) {
        if (uri != selectedWorkspace) filesPath = emptyList()
        selectedWorkspace = uri
        Workspaces.saveSelected(this, uri)
    }

    private fun editWorkspace(workspace: Workspaces.Workspace, name: String, color: Int) {
        workspaces = workspaces.map { if (it.uri == workspace.uri) it.copy(name = name, color = color) else it }
        Workspaces.save(this, workspaces)
    }

    /** Takes [workspace] off the list and lets go of its grant; the folder and its files stay. */
    private fun removeWorkspace(workspace: Workspaces.Workspace) {
        workspaces = workspaces.filter { it.uri != workspace.uri }
        Workspaces.save(this, workspaces)
        if (selectedWorkspace == workspace.uri) selectWorkspace(workspaces.firstOrNull()?.uri)
        try {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(workspace.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Rnote saves on its own every couple of minutes; this does the same. The unsaved note
     * always goes to the private recovery copy first (fast, and it can't fail on a lost
     * file grant), then over its own file if it has one that nobody else has changed. It
     * never asks anything: a conflict or a failure just leaves the recovery copy and the
     * question for the next manual save.
     */
    private fun autosave() {
        lifecycleScope.launch { autosaveNow() }
    }

    /**
     * [autosave], finishing before it returns: for when another note is about to be opened.
     * True when the note's file now holds everything — nothing was unsaved, or it was
     * written — and false when the changes are only in the recovery copy.
     */
    private suspend fun autosaveNow(): Boolean {
        val document = unsavedDocument?.invoke() ?: return true
        val target = currentDocumentUri
        val asRnote = saveAsRnote
        val known = knownLastModified
        val knownHash = knownContentHash
        val slot = activeTab
        savesRunning++
        try {
            val written = withContext(Dispatchers.IO) {
                try {
                    Recovery.write(this@MainActivity, document, target, asRnote, slot)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                if (target != null && busyMessage == null && !changedElsewhere(target, known, knownHash)) {
                    writeLock.withLock { writeDocument(target, document, asRnote) }
                } else {
                    null
                }
            }
            if (written != null && target != null) afterSave(target, document, slot, written)
            return written != null
        } finally {
            savesRunning--
        }
    }

    /** One write to a file at a time: autosave and a manual save can otherwise overlap. */
    private val writeLock = Mutex()

    /**
     * Blocking write; call from [Dispatchers.IO]. The [ContentHash] of what was written, or
     * null on any failure, OOM included.
     */
    private fun writeDocument(uri: Uri, document: NoteDocument, asRnote: Boolean): String? = try {
        FileManager.saveDocumentHashed(this, uri, document, asRnote)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
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
        busyMessage = "Saving…"
        val slot = activeTab
        lifecycleScope.launch {
            savesRunning++
            try {
                val written = writeLock.withLock { withContext(Dispatchers.IO) { writeDocument(uri, document, asRnote) } }
                busyMessage = null
                if (written == null) {
                    Toast.makeText(this@MainActivity, "Save failed", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                saveAsRnote = asRnote
                // The picker lets the name be edited, so the note takes the name it was actually
                // saved under — otherwise the title in the bar and the file on disk disagree.
                val savedTitle = DocumentUri.displayName(this@MainActivity, uri)?.let(DocumentUri::titleFrom)
                adoptDocumentUri(uri, savedTitle ?: document.title)
                savedTitle?.let { onTitleAdopted?.invoke(it) }
                afterSave(uri, document, slot, written)
                Toast.makeText(
                    this@MainActivity,
                    if (asRnote) "Saved as .rnote" else "Saved as .json",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                savesRunning--
            }
        }
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
        val baseName: String,
        val selectedNatives: List<NativeCanvasElement> = emptyList()
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

    private val exportXoppLauncher = registerForActivityResult(
        CreateDocumentNear(ExportFormat.XOPP.mimeType)
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
                ExportFormat.XOPP -> exportXoppLauncher.launch(fileName)
            }
        }
    }

    private fun finishSingleExport(uri: Uri) {
        val request = pendingExport ?: return
        pendingExport = null
        runExport {
            FileManager.exportToUri(
                this, uri, request.document, request.selection, request.prefs, request.selectedNatives
            )
        }
    }

    private fun finishPagesExport(treeUri: Uri) {
        val request = pendingExport ?: return
        pendingExport = null
        runExport {
            FileManager.exportPagesToTree(
                this, treeUri, request.document, request.prefs, request.baseName
            )
        }
    }

    /**
     * Exports off the main thread: a document with imported PDF pages renders each of
     * them in full, which is far too slow to do while the UI waits.
     */
    private fun runExport(export: () -> DocumentExporter.Result) {
        busyMessage = "Exporting…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    export()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    DocumentExporter.Result.Failure("Export failed")
                }
            }
            busyMessage = null
            reportExport(result)
        }
    }

    /**
     * Exports into a private file with [export] and offers it to other apps through
     * Android's share sheet, under [fileName], which is what the receiving app shows.
     */
    private fun shareExport(fileName: String, mimeType: String, export: (Uri) -> DocumentExporter.Result) {
        if (busyMessage != null) return
        busyMessage = "Preparing to share…"
        lifecycleScope.launch {
            val shared = withContext(Dispatchers.IO) {
                try {
                    val dir = java.io.File(cacheDir, "share").apply { mkdirs() }
                    // Earlier shares are left for a while: the app they went to may still be
                    // reading one, as a mail draft does its attachment.
                    val stale = System.currentTimeMillis() - SHARED_FILE_LIFETIME_MS
                    dir.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity, "$packageName.fileprovider", java.io.File(dir, fileName)
                    )
                    uri to export(uri)
                } catch (e: Throwable) {
                    // OutOfMemoryError included, as for any export.
                    e.printStackTrace()
                    null
                }
            }
            busyMessage = null
            val (uri, result) = shared ?: (null to DocumentExporter.Result.Failure("Could not share"))
            if (uri == null || result !is DocumentExporter.Result.Success) {
                reportExport(result)
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                // Carries the read grant through the chooser, and gives it a preview.
                clipData = ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(send, null))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, "No app to share with", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * The selection on Android's clipboard as a picture, as Rnote's selector puts it on the
     * desktop's with its own copy: drawn without background or pattern, 6 px round it, at
     * 1.8 times its size (`StrokeContent::CLIPBOARD_EXPORT_MARGIN`,
     * `Engine::STROKE_EXPORT_IMAGE_SCALE`). Another app pastes it as an image; this one
     * pastes its own copy of the ink, which stays ink.
     */
    private fun copySelectionImage(document: NoteDocument, selection: List<Stroke>, natives: List<NativeCanvasElement>) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                try {
                    val dir = java.io.File(cacheDir, "clipboard").apply { mkdirs() }
                    // Only the newest copy is on the clipboard, so only it is kept; a new name
                    // each time, so nothing that read the last one is shown this one under it.
                    dir.listFiles()?.forEach { it.delete() }
                    val file = java.io.File(dir, "selection-${System.currentTimeMillis()}.png")
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                    val prefs = ExportPrefs(
                        scope = ExportScope.SELECTION,
                        format = ExportFormat.PNG,
                        withBackground = false,
                        withPattern = false,
                        optimizePrinterOutput = false,
                        bitmapScaleFactor = CLIPBOARD_IMAGE_SCALE,
                        marginPx = CLIPBOARD_IMAGE_MARGIN
                    )
                    val result = DocumentExporter.exportSingle(this@MainActivity, uri, document, selection, prefs, natives)
                    uri.takeIf { result is DocumentExporter.Result.Success }
                } catch (e: Throwable) {
                    // OutOfMemoryError included: the copy inside the app has been made anyway.
                    e.printStackTrace()
                    null
                }
            } ?: return@launch
            try {
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newUri(contentResolver, "Selection", uri))
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }
        }
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
        // The first tab, which the note opened or recovered at launch may take over.
        activeTab = newTabId()
        tabOrder.add(activeTab)
        workspaces = Workspaces.load(this)
        selectedWorkspace = Workspaces.loadSelected(this)
        // While the app is in front: look now and then whether Toni saved the open note.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(NEWER_VERSION_POLL_MS)
                    checkForNewerVersion(whileIdle = true)
                }
            }
        }
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
                mutableStateOf(
                    ToolConfig(
                        allowFingerDrawing = SettingsManager.loadAllowFingerDrawing(this),
                        snapPositions = SettingsManager.loadSnapPositions(this),
                        blockPinchZoom = SettingsManager.loadBlockPinchZoom(this),
                        respectBorders = SettingsManager.loadRespectBorders(this)
                    )
                )
            }
            // Rnote's "Pen Sounds": off by default, kept between sessions; the sounds are only
            // loaded while they are on.
            var penSoundsOn by remember { mutableStateOf(SettingsManager.loadPenSounds(this)) }
            // Loaded off the main thread: the pencil's player prepares its recording as it is
            // made, which is no work for the first frame to wait on.
            val penSounds by produceState<PenSounds?>(null, penSoundsOn) {
                if (!penSoundsOn) {
                    value = null
                    return@produceState
                }
                // Not given up halfway: a player made and then dropped would never be released.
                val sounds = withContext(NonCancellable + Dispatchers.IO) { PenSounds(applicationContext) }
                if (!isActive) {
                    sounds.release()
                    return@produceState
                }
                value = sounds
                awaitDispose {
                    value = null
                    sounds.release()
                }
            }
            val togglePenSounds: () -> Unit = {
                penSoundsOn = !penSoundsOn
                SettingsManager.savePenSounds(this@MainActivity, penSoundsOn)
            }
            // Rnote's other canvas menu switches, kept between sessions as Rnote keeps them.
            val toggleBlockPinchZoom: () -> Unit = {
                toolConfig = toolConfig.copy(blockPinchZoom = !toolConfig.blockPinchZoom)
                SettingsManager.saveBlockPinchZoom(this@MainActivity, toolConfig.blockPinchZoom)
            }
            val toggleRespectBorders: () -> Unit = {
                toolConfig = toolConfig.copy(respectBorders = !toolConfig.respectBorders)
                SettingsManager.saveRespectBorders(this@MainActivity, toolConfig.respectBorders)
            }
            /** Rnote's canvas menu toggle, and Ctrl+Shift+P. */
            val toggleSnapPositions: () -> Unit = {
                toolConfig = toolConfig.copy(snapPositions = !toolConfig.snapPositions)
                SettingsManager.saveSnapPositions(this@MainActivity, toolConfig.snapPositions)
            }
            // Rnote's "Button Shortcuts", kept between sessions as Rnote keeps them, and what
            // pressing one has done to the pen (see PenShortcutState).
            var penShortcuts by remember { mutableStateOf(SettingsManager.loadPenShortcuts(this)) }
            val penShortcutState = remember { PenShortcutState() }
            // Rnote's Focus Mode: the pen picker, the colour picker and the pen settings put
            // away, leaving the page and the headerbar. Neither is kept once the app closes,
            // in Rnote as here.
            var focusMode by rememberSaveable { mutableStateOf(false) }
            // Rnote's Fullscreen (F11): here, Android's status and navigation bars put away,
            // a swipe from the edge bringing them back for a moment.
            var fullscreen by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(fullscreen) {
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                if (fullscreen) {
                    bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    bars.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    bars.show(WindowInsetsCompat.Type.systemBars())
                }
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
            // The drawing area's own size, which the pan offset is measured against.
            var canvasSize by remember { mutableStateOf(IntSize.Zero) }
            var showPageSettings by remember { mutableStateOf(false) }
            var showExportSheet by remember { mutableStateOf(false) }
            // Kept across openings so a second export doesn't start from the defaults again.
            var exportPrefs by remember { mutableStateOf(ExportPrefs()) }
            var penFavorites by remember { mutableStateOf(PenFavorites.load(this@MainActivity)) }
            val setFavorite = { slot: Int, favorite: PenFavorite? ->
                penFavorites = penFavorites.toMutableList().also { it[slot] = favorite }
                PenFavorites.save(this@MainActivity, penFavorites)
            }

            // ── Stroke stacks ─────────────────────────────────────────────────────
            val strokes = remember { mutableStateListOf<Stroke>() }
            val undoStack = remember { mutableStateListOf<DocSnapshot>() }
            val redoStack = remember { mutableStateListOf<DocSnapshot>() }
            val selectedStrokes = remember { mutableStateListOf<Stroke>() }
            // Non-stroke elements (text/shapes/images) preserved from an imported native file.
            // Carried through save/export so they aren't silently dropped from opened .rnote files.
            var documentNativeElements by remember { mutableStateOf<List<NativeCanvasElement>>(emptyList()) }
            // Desktop elements (text, shapes, images) the selector holds, beside selectedStrokes.
            val selectedNatives = remember { mutableStateListOf<NativeCanvasElement>() }
            var textSession by remember { mutableStateOf<TextSession?>(null) }
            /** The canvas's top edge in the window, to find how much of it the keyboard covers. */
            var canvasTop by remember { mutableFloatStateOf(0f) }
            var textSessionCount by remember { mutableIntStateOf(0) }
            var showRecent by remember { mutableStateOf(false) }
            var showFiles by remember { mutableStateOf(false) }
            var showPages by remember { mutableStateOf(false) }
            val snapshot = { DocSnapshot(strokes.toList(), documentNativeElements) }
            // Every change that can be undone goes through here, so undo reaches back as far
            // as Rnote's does and no further (see UndoHistory).
            val pushUndo: () -> Unit = {
                undoStack.add(snapshot())
                UndoHistory.trim(undoStack)
            }
            val restore = { state: DocSnapshot ->
                strokes.clear()
                strokes.addAll(state.strokes)
                documentNativeElements = state.natives
                selectedStrokes.clear()
                selectedNatives.clear()
            }

            // ── The pages of a Fixed Size document (see FixedPages) ───────────────
            // Rnote's Resize to Fit Content, which it also does to a Fixed Size document
            // when the format or the layout changes and after an import.
            val fitPagesToContent: () -> Unit = {
                if (paperStyle.layoutMode == LayoutMode.FIXED_SIZE) {
                    val pages = FixedPages.fitting(
                        ExportLayout.contentBounds(strokes, documentNativeElements),
                        paperStyle.effectivePageHeightPx
                    )
                    if (pages != paperStyle.fixedPages) {
                        paperStyle = paperStyle.copy(fixedPageCount = pages)
                        isModified = true
                    }
                }
            }
            val addPage: () -> Unit = {
                if (paperStyle.layoutMode == LayoutMode.FIXED_SIZE) {
                    paperStyle = paperStyle.copy(fixedPageCount = paperStyle.fixedPages + 1)
                    isModified = true
                }
            }
            // As in Rnote, what lies wholly on the page taken away goes with it, and undo
            // brings that back — the page itself stays gone, Rnote's history holding the
            // content and not the document's size.
            val removePage: () -> Unit = {
                if (paperStyle.layoutMode == LayoutMode.FIXED_SIZE && paperStyle.fixedPages > 1) {
                    textSession = null
                    val pages = paperStyle.fixedPages - 1
                    val bottom = pages * paperStyle.effectivePageHeightPx
                    val goneIds = strokes.filter { stroke ->
                        val top = stroke.points.minOfOrNull { it.y } ?: return@filter false
                        FixedPages.goesWithRemovedPage(top - stroke.strokeWidth / 2f, bottom)
                    }.map { it.id }.toSet()
                    val goneNatives = java.util.Collections.newSetFromMap(
                        java.util.IdentityHashMap<NativeCanvasElement, Boolean>()
                    ).apply {
                        addAll(documentNativeElements.filter { FixedPages.goesWithRemovedPage(it.minY, bottom) })
                    }
                    if (goneIds.isNotEmpty() || goneNatives.isNotEmpty()) {
                        pushUndo()
                        redoStack.clear()
                        strokes.removeAll { it.id in goneIds }
                        selectedStrokes.removeAll { it.id in goneIds }
                        documentNativeElements = documentNativeElements.filter { it !in goneNatives }
                        selectedNatives.removeAll { it in goneNatives }
                    }
                    paperStyle = paperStyle.copy(fixedPageCount = pages)
                    isModified = true
                }
            }

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
                textSession = null
                strokes.clear()
                undoStack.clear()
                redoStack.clear()
                selectedStrokes.clear()
                selectedNatives.clear()
                strokes.addAll(doc.strokes)
                // The file's format is the document's, not the user's default — so it is
                // deliberately not persisted; the next new note starts from preferences.
                paperStyle = doc.paperStyle
                documentTitle = doc.title
                documentNativeElements = doc.nativeElements
                viewportState = ViewportState(displayScale = displayScale)
                isModified = false
            }

            // A finished load waits in incomingDocument; hand it over once this handler
            // exists (see incomingDocument for why it isn't called directly).
            val pendingDocument = incomingDocument
            LaunchedEffect(pendingDocument) {
                if (pendingDocument != null) {
                    val view = viewportState
                    onDocumentLoaded(pendingDocument)
                    incomingDocument = null
                    if (incomingUnsaved) {
                        // Imported, not yet anywhere: unsaved, as Rnote marks it.
                        incomingUnsaved = false
                        isModified = true
                    }
                    if (incomingKeepsView) {
                        // Reloaded: stay on the part of the note that was on screen.
                        incomingKeepsView = false
                        viewportState = view
                    }
                }
            }

            // ── New document handler ──────────────────────────────────────────────
            // Empties the tab on screen for a new note: a harder reset than Clear Canvas,
            // the title and the undo history go too. Only ever done to a tab being
            // opened or reused — the note that was there has been put in a tab of its own.
            // The paper style goes back to the stored preference rather than surviving:
            // it belongs to the document now, so whatever an opened file brought with it
            // must not follow the user into their next note.
            val startNewDocument: () -> Unit = {
                textSession = null
                paperStyle = SettingsManager.loadPaperStyle(this)
                strokes.clear()
                undoStack.clear()
                redoStack.clear()
                selectedStrokes.clear()
                selectedNatives.clear()
                documentNativeElements = emptyList()
                documentTitle = "My Note"
                viewportState = ViewportState(displayScale = displayScale)
                isModified = false
                // Fresh notes save as .rnote — see saveAsRnote's declaration.
                saveAsRnote = true
                // No file yet, so the next Save has to ask for one.
                currentDocumentUri = null
                knownLastModified = null
                knownContentHash = null
            }

            // ── Tabs ──────────────────────────────────────────────────────────────
            // The note on screen is the state above; every other open note is parked
            // (see ParkedTab) and swapped in when its tab is chosen.
            val parkActive: () -> ParkedTab = {
                ParkedTab(
                    title = documentTitle,
                    isModified = isModified,
                    strokes = strokes.toList(),
                    natives = documentNativeElements,
                    paperStyle = paperStyle,
                    undo = undoStack.toList(),
                    redo = redoStack.toList(),
                    viewport = viewportState,
                    uri = currentDocumentUri,
                    knownLastModified = knownLastModified,
                    saveAsRnote = saveAsRnote,
                    knownContentHash = knownContentHash
                )
            }
            val showParked: (ParkedTab) -> Unit = { tab ->
                textSession = null
                selectedStrokes.clear()
                selectedNatives.clear()
                strokes.clear()
                strokes.addAll(tab.strokes)
                documentNativeElements = tab.natives
                paperStyle = tab.paperStyle
                documentTitle = tab.title
                undoStack.clear()
                undoStack.addAll(tab.undo)
                redoStack.clear()
                redoStack.addAll(tab.redo)
                viewportState = tab.viewport
                currentDocumentUri = tab.uri
                tab.uri?.let { pickerStartUri = it }
                knownLastModified = tab.knownLastModified
                knownContentHash = tab.knownContentHash
                saveAsRnote = tab.saveAsRnote
                isModified = tab.isModified
            }
            // An untouched new note: what a note being opened may take the place of.
            val activeIsBlank: () -> Boolean = {
                currentDocumentUri == null && !isModified && strokes.isEmpty() && documentNativeElements.isEmpty()
            }
            // Parks the note on screen and shows tab [id] in its place, then and there.
            val activate: (Long) -> Unit = { id ->
                val next = parkedTabs.remove(id)
                if (next != null && id != activeTab) {
                    parkedTabs[activeTab] = parkActive()
                    activeTab = id
                    showParked(next)
                }
            }
            // A tab of its own for a new note, beside the one on screen, which is parked.
            val openFreshTab: () -> Unit = {
                parkedTabs[activeTab] = parkActive()
                val id = newTabId()
                tabOrder.add(tabOrder.indexOf(activeTab) + 1, id)
                activeTab = id
                startNewDocument()
            }
            var settlingTab by remember { mutableStateOf(false) }
            // Readies the note on screen to leave the screen: typing is ended, a save or a
            // load under way is waited out, and it is saved as autosave would — so its tab
            // holds nothing its file doesn't. [then] runs after, told whether that worked;
            // not at all while a conflict waits for an answer about this note.
            val settleActive: ((secured: Boolean) -> Unit) -> Unit = { then ->
                if (!settlingTab && pendingConflict == null) {
                    settlingTab = true
                    lifecycleScope.launch {
                        var secured = false
                        try {
                            textSession = null
                            while (busyMessage != null || savesRunning > 0 || incomingDocument != null) delay(50)
                            secured = autosaveNow()
                        } finally {
                            settlingTab = false
                        }
                        if (pendingConflict == null) then(secured)
                    }
                }
            }
            val switchTab: (Long, () -> Unit) -> Unit = { id, then ->
                if (id != activeTab && id in parkedTabs) {
                    settleActive {
                        activate(id)
                        // Toni may have saved it while it waited in the background.
                        checkForNewerVersion()
                        then()
                    }
                }
            }
            // Rnote's "New tab" — and "New": a new note never takes the place of another.
            val newTab: (() -> Unit) -> Unit = { then ->
                if (activeIsBlank()) {
                    then()
                } else {
                    settleActive {
                        openFreshTab()
                        then()
                    }
                }
            }
            // The tab on screen closed, what was in it gone: a neighbour takes its place,
            // or, for the last tab, a new note.
            val finishClose: () -> Unit = {
                val closed = activeTab
                Recovery.clear(this@MainActivity, closed)
                val index = tabOrder.indexOf(closed)
                tabOrder.remove(closed)
                val neighbour = tabOrder.getOrNull(index) ?: tabOrder.getOrNull(index - 1)
                val next = neighbour?.let { parkedTabs.remove(it) }
                if (neighbour != null && next != null) {
                    activeTab = neighbour
                    showParked(next)
                    checkForNewerVersion()
                } else {
                    val id = newTabId()
                    tabOrder.add(id)
                    activeTab = id
                    startNewDocument()
                }
            }
            // The tab on screen, unsaved, waiting for a yes to close it all the same.
            var confirmClose by remember { mutableStateOf(false) }
            val closeActive: () -> Unit = {
                settleActive { secured ->
                    if (secured && !isModified) {
                        finishClose()
                    } else {
                        confirmClose = true
                    }
                }
            }
            // Rnote's close button: a tab whose note is all in its file just closes; one
            // with unsaved changes is shown first, saved if it can be, and asked about if not.
            val closeTab: (Long) -> Unit = { id ->
                val parked = parkedTabs[id]
                when {
                    id == activeTab -> closeActive()
                    parked == null -> Unit
                    !parked.isModified -> {
                        parkedTabs.remove(id)
                        tabOrder.remove(id)
                        Recovery.clear(this@MainActivity, id)
                    }
                    else -> switchTab(id, closeActive)
                }
            }
            val stepTab: (Int) -> Unit = { step ->
                if (tabOrder.size > 1) {
                    val index = tabOrder.indexOf(activeTab)
                    switchTab(tabOrder[(index + step).mod(tabOrder.size)]) {}
                }
            }
            showTabWith = { uri ->
                if (FolderBrowser.sameDocument(uri, currentDocumentUri)) {
                    true
                } else {
                    val id = parkedTabs.entries.firstOrNull { FolderBrowser.sameDocument(uri, it.value.uri) }?.key
                    if (id != null) switchTab(id) {}
                    id != null
                }
            }
            makeRoomForIncoming = {
                if (!activeIsBlank()) {
                    textSession = null
                    parkedTabs[activeTab] = parkActive()
                    val id = newTabId()
                    tabOrder.add(tabOrder.indexOf(activeTab) + 1, id)
                    activeTab = id
                }
            }
            // Notes the last run didn't get to save, each back in a tab of the id it had —
            // so its recovery slot goes on being its own. Unknown whether their files changed
            // meanwhile, so no conflict baseline is set and the next save asks nothing:
            // recovered work is the user's most recent anyway.
            val restoreRecovered: (List<Recovery.Pending>) -> Unit = restore@{ recovered ->
                if (recovered.isEmpty()) return@restore
                for (r in recovered) {
                    parkedTabs[r.slot] = ParkedTab(
                        title = r.document.title,
                        isModified = true,
                        strokes = r.document.strokes,
                        natives = r.document.nativeElements,
                        paperStyle = r.document.paperStyle,
                        undo = emptyList(),
                        redo = emptyList(),
                        viewport = ViewportState(displayScale = displayScale),
                        uri = r.uri,
                        knownLastModified = null,
                        saveAsRnote = r.saveAsRnote
                    )
                    tabOrder.add(r.slot)
                }
                val last = recovered.last().slot
                if (activeIsBlank()) {
                    val blank = activeTab
                    activate(last)
                    parkedTabs.remove(blank)
                    tabOrder.remove(blank)
                } else {
                    switchTab(last) {}
                }
            }

            // ── Workspace panel ───────────────────────────────────────────────────
            // Desktop Rnote's "New file" in its browser: the file is made with an empty note
            // written into it at once, then opened in a tab of its own like any other — so
            // the note has its home from the start and every save goes straight back there.
            // Written at once, because a file left with nothing in it (a write that never
            // came) is one Rnote on the laptop can't open; one that can't be written goes.
            val newNoteIn: (Uri, String, String) -> Unit = { tree, folderId, name ->
                lifecycleScope.launch {
                    if (busyMessage != null) return@launch
                    val fileName = if (DocumentUri.isRnote(name)) name else "$name.rnote"
                    // Busy while the file is made, so nothing else starts meanwhile and
                    // turns the opening of it below away.
                    busyMessage = "Creating the note…"
                    val created = try {
                        withContext(Dispatchers.IO) {
                            val uri = FolderBrowser.createNote(this@MainActivity, tree, folderId, fileName)
                                ?: return@withContext null
                            // A provider may pick another name when that one is taken.
                            val actualName = DocumentUri.displayName(this@MainActivity, uri) ?: fileName
                            val note = NoteDocument(
                                title = DocumentUri.titleFrom(actualName),
                                paperStyle = SettingsManager.loadPaperStyle(this@MainActivity)
                            )
                            if (FileManager.saveDocumentHashed(this@MainActivity, uri, note, asRnote = true) != null) {
                                uri
                            } else {
                                FolderBrowser.delete(this@MainActivity, uri)
                                null
                            }
                        }
                    } finally {
                        busyMessage = null
                    }
                    if (created == null) {
                        Toast.makeText(this@MainActivity, "Could not create the note there", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    filesRefresh++
                    openDocument(created)
                }
            }
            // Coming to the files is a moment to look whether Toni saved the open note meanwhile.
            LaunchedEffect(showFiles) {
                if (showFiles) checkForNewerVersion()
            }

            // ── Back ──────────────────────────────────────────────────────────────
            // Back closes what is open before it leaves the app, as Escape does in Rnote:
            // the side panel first, then the text box being typed into, then the selection.
            // (The one composed last is asked first.)
            BackHandler(enabled = selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty()) {
                selectedStrokes.clear()
                selectedNatives.clear()
            }
            BackHandler(enabled = textSession != null) { textSession = null }
            BackHandler(enabled = showFiles) { showFiles = false }

            // ── Save succeeded handler ────────────────────────────────────────────
            onSaveSucceeded = { saved ->
                // A save runs in the background; ink added while it ran is not in the file.
                val unchanged = saved.strokes.size == strokes.size &&
                    saved.strokes.indices.all { saved.strokes[it] === strokes[it] } &&
                    saved.nativeElements === documentNativeElements &&
                    saved.paperStyle == paperStyle && saved.title == documentTitle
                if (unchanged) isModified = false
            }
            unsavedDocument = {
                // A loaded note waiting to replace this one already has its file's baseline
                // (see openDocument), so this one must not be written against it.
                if (!isModified || incomingDocument != null) null else NoteDocument(
                    title = documentTitle,
                    paperStyle = paperStyle,
                    strokes = strokes.toList(),
                    nativeElements = documentNativeElements
                )
            }
            // A minute after the first unsaved change, and again after every save.
            LaunchedEffect(isModified) {
                if (isModified) {
                    delay(AUTOSAVE_DELAY_MS)
                    autosave()
                }
            }
            onTitleAdopted = { name -> documentTitle = name }
            // A reload would end the text box being typed into, even before it has changed anything.
            reloadWouldInterrupt = { textSession != null }

            // ── PDF import ─────────────────────────────────────────────────────────
            pdfImportTarget = {
                val pageW = paperStyle.effectivePageWidthPx.takeIf { it > 0f } ?: 793.7f
                val pageH = paperStyle.effectivePageHeightPx.takeIf { it > 0f } ?: 1122.5f
                val others = documentNativeElements.filter { it !is NativeBrushStroke }
                val hasContent = strokes.isNotEmpty() || others.isNotEmpty()
                // Below everything already in the note, starting on the next whole page.
                val bottom = maxOf(
                    strokes.maxOfOrNull { s -> s.points.maxOfOrNull { it.y } ?: 0f } ?: 0f,
                    others.maxOfOrNull { it.maxY } ?: 0f
                )
                val startY = if (hasContent) (floor(bottom / pageH) + 1f) * pageH else 0f
                PdfImportTarget(pageW, pageH, startY)
            }
            onPdfImported = { pages ->
                // Ahead of the rest: the document layer is drawn first, under everything.
                pushUndo()
                redoStack.clear()
                documentNativeElements = pages + documentNativeElements
                isModified = true
                // A Fixed Size document gets the pages the PDF needs, as in Rnote.
                fitPagesToContent()
                // Bring the first imported page into view at the current zoom.
                viewportState = viewportState.copy(
                    panOffset = androidx.compose.ui.geometry.Offset(
                        ViewportState.ORIGIN_MARGIN_PX,
                        ViewportState.ORIGIN_MARGIN_PX - pages.first().minY * viewportState.effectiveScale
                    )
                )
            }

            // ── Inserted images ───────────────────────────────────────────────────
            val hasCamera = remember { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
            imagePlacement = { width, height ->
                val topLeft = viewportState.screenToCanvas(Offset.Zero)
                val bottomRight = viewportState.screenToCanvas(
                    Offset(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                )
                val layout = paperStyle.layoutMode
                val fixedWidth = layout == LayoutMode.FIXED_SIZE || layout == LayoutMode.CONTINUOUS_VERTICAL
                NativeEditing.placeImage(
                    width, height,
                    topLeft.x, topLeft.y, bottomRight.x, bottomRight.y,
                    // Rnote's `Stroke::IMPORT_OFFSET_DEFAULT`: 32 px on its screen, which is
                    // 32 document units at 100%.
                    offset = IMPORT_OFFSET / viewportState.zoomScale,
                    fixedPageWidth = paperStyle.effectivePageWidthPx.takeIf { fixedWidth && it > 0f },
                    clampToOrigin = layout != LayoutMode.INFINITE,
                    borders = if (toolConfig.respectBorders) {
                        NativeEditing.PageBorders(paperStyle.effectivePageWidthPx, paperStyle.effectivePageHeightPx)
                    } else {
                        null
                    }
                )
            }
            onImageInserted = { image ->
                pushUndo()
                redoStack.clear()
                // Last, so on top of the images already there, as a new stroke is in Rnote.
                documentNativeElements = documentNativeElements + image
                // Selected, as desktop Rnote leaves an imported image: ready to move or resize.
                penShortcutState.picked()
                toolConfig = toolConfig.copy(activeTool = ToolType.SELECTOR)
                selectedStrokes.clear()
                selectedNatives.clear()
                selectedNatives.add(image)
                isModified = true
                fitPagesToContent()
            }

            // ── Typewriter ────────────────────────────────────────────────────────
            // Another tool, or a favorite pen picked, ends the typing.
            LaunchedEffect(toolConfig.activeTool) {
                if (toolConfig.activeTool != ToolType.TYPEWRITER) textSession = null
            }
            /** [old] replaced by [new] in the document, in its place; either may be null. */
            val putText = { old: NativeTextElement?, new: NativeTextElement? ->
                documentNativeElements = when {
                    old != null && new != null -> documentNativeElements.map { if (it === old) new else it }
                    new != null -> documentNativeElements + new
                    old != null -> documentNativeElements.filter { it !== old }
                    else -> documentNativeElements
                }
            }
            val onTextChange: (TextFieldValue) -> Unit = change@{ value ->
                val session = textSession ?: return@change
                val old = session.value
                if (value.text != old.text) {
                    // Rnote's typewriter: a key for every character typed or deleted, the
                    // bell and the line feed for a new line.
                    penSounds?.typed(newLine = value.text.count { it == '\n' } > old.text.count { it == '\n' })
                }
                if (value.text == old.text) {
                    // The cursor moved: the switches go back to showing what is there.
                    val moved = value.selection != old.selection
                    textSession = session.copy(value = value, pending = if (moved) emptyMap() else session.pending)
                    return@change
                }
                val base = session.element ?: session.template
                var edited = if (base != null) {
                    // Blank text removes the box, as emptying one does in Rnote.
                    NativeEditing.withText(base, value.text)
                } else {
                    val c = toolConfig.penColor
                    NativeEditing.createText(
                        value.text, session.x, session.y, toolConfig.textSize,
                        RnoteNativeColor(c.red, c.green, c.blue, c.alpha),
                        NativeEditing.typewriterWrapWidth(session.x, paperStyle.effectivePageWidthPx),
                        toolConfig.textAlignment.apiName
                    )
                }
                if (edited == null && session.element == null) {
                    // Only spaces typed into a box that isn't there yet: nothing to keep so far.
                    textSession = session.copy(value = value)
                    return@change
                }
                if (!session.undoTaken) {
                    pushUndo()
                    redoStack.clear()
                }
                if (edited != null && session.pending.isNotEmpty()) {
                    val (from, to) = TextFormatting.changedRange(old.text, value.text)
                    if (to > from) {
                        for ((toggle, on) in session.pending) {
                            edited = NativeEditing.setFormat(edited!!, from, to, toggle, on)
                        }
                    }
                }
                putText(session.element, edited)
                isModified = true
                textSession = session.copy(element = edited, template = edited ?: base, value = value, undoTaken = true)
            }
            /** Bold, italic, underline or strikethrough: on the selection, or else for what is typed next. */
            val onToggleTextFormat: (TextToggle) -> Unit = { toggle ->
                val session = textSession
                if (session != null) {
                    val selection = session.value.selection
                    val element = session.element
                    if (!selection.collapsed && element != null) {
                        if (!session.undoTaken) {
                            pushUndo()
                            redoStack.clear()
                        }
                        val updated = NativeEditing.toggleFormat(element, selection.min, selection.max, toggle)
                        putText(element, updated)
                        isModified = true
                        textSession = session.copy(element = updated, template = updated, undoTaken = true)
                    } else {
                        val shown = session.pending[toggle]
                            ?: (toggle in TextFormatting.togglesAt(element, selection.min, selection.max))
                        textSession = session.copy(pending = session.pending + (toggle to !shown))
                    }
                }
            }
            /**
             * Rnote's alignment buttons: the box being typed into is aligned at once, and
             * new boxes are aligned so from now on.
             */
            val onTextAlignmentSelected: (TextAlignment) -> Unit = { alignment ->
                toolConfig = toolConfig.copy(textAlignment = alignment)
                val session = textSession
                val element = session?.element
                if (session != null && element != null && element.alignment != alignment.apiName) {
                    if (!session.undoTaken) {
                        pushUndo()
                        redoStack.clear()
                    }
                    val updated = NativeEditing.withAlignment(element, alignment.apiName)
                    putText(element, updated)
                    isModified = true
                    textSession = session.copy(element = updated, template = updated, undoTaken = true)
                }
            }
            /** What the alignment button shows: the box's own, or the one new boxes get. */
            val textAlignment: TextAlignment = textSession?.element?.let { TextAlignment.of(it.alignment) }
                ?: toolConfig.textAlignment
            /** What the switches show: set for text typed next, or else what the selection or cursor has. */
            val textFormats: Set<TextToggle> = textSession?.let { session ->
                val selection = session.value.selection
                val there = TextFormatting.togglesAt(session.element, selection.min, selection.max)
                TextToggle.entries.filterTo(mutableSetOf()) { session.pending[it] ?: (it in there) }
            } ?: emptySet()

            // ── Share ─────────────────────────────────────────────────────────────
            val shareNote: (ShareTarget) -> Unit = { target ->
                val document = NoteDocument(
                    title = documentTitle,
                    paperStyle = paperStyle,
                    strokes = strokes.toList(),
                    nativeElements = documentNativeElements
                )
                val format = target.format
                when (target) {
                    ShareTarget.PAGE_PNG, ShareTarget.PAGE_PDF -> {
                        val view = androidx.compose.ui.geometry.Rect(
                            viewportState.screenToCanvas(Offset.Zero),
                            viewportState.screenToCanvas(
                                Offset(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                            )
                        )
                        var prefs = exportPrefs.copy(format = format)
                        val pages = DocumentExporter.pagesFor(document, prefs)
                        val index = ExportLayout.pageInView(pages, view)
                        val detail = if (index != null) {
                            String.format(java.util.Locale.ROOT, "page %02d", index + 1)
                        } else {
                            // No page in view, or none at all: what is on screen, at the
                            // resolution it is on screen.
                            prefs = prefs.copy(bitmapScaleFactor = viewportState.effectiveScale)
                            "view"
                        }
                        val region = index?.let { pages[it] } ?: view
                        val sharePrefs = prefs
                        shareExport(DocumentExporter.sharedFileName(documentTitle, detail, format), format.mimeType) { uri ->
                            DocumentExporter.exportRegion(this@MainActivity, uri, document, region, sharePrefs)
                        }
                    }
                    ShareTarget.SELECTION_PNG -> {
                        val prefs = exportPrefs.copy(scope = ExportScope.SELECTION, format = format)
                        val selection = selectedStrokes.toList()
                        val natives = selectedNatives.toList()
                        shareExport(DocumentExporter.sharedFileName(documentTitle, "selection", format), format.mimeType) { uri ->
                            DocumentExporter.exportSingle(this@MainActivity, uri, document, selection, prefs, natives)
                        }
                    }
                    ShareTarget.NOTE_PDF -> {
                        val prefs = exportPrefs.copy(scope = ExportScope.DOCUMENT, format = format)
                        shareExport(DocumentExporter.sharedFileName(documentTitle, null, format), format.mimeType) { uri ->
                            DocumentExporter.exportSingle(this@MainActivity, uri, document, emptyList(), prefs)
                        }
                    }
                }
            }

            // ── S-Pen Air Action remote shortcuts ─────────────────────────────────
            performUndoAction = {
                // The box being typed into may be what comes undone.
                textSession = null
                // Gated on undoStack, not `strokes` — an empty canvas can still have undo
                // history (e.g. right after Clear Canvas), and that must stay undoable.
                if (undoStack.isNotEmpty()) {
                    redoStack.add(snapshot())
                    restore(undoStack.removeAt(undoStack.lastIndex))
                    isModified = true
                }
            }

            performRedoAction = {
                textSession = null
                if (redoStack.isNotEmpty()) {
                    pushUndo()
                    restore(redoStack.removeAt(redoStack.lastIndex))
                    isModified = true
                }
            }

            // ── What the top bar and the keyboard both do ─────────────────────────
            val currentNote: () -> NoteDocument = {
                NoteDocument(
                    title = documentTitle,
                    paperStyle = paperStyle,
                    strokes = strokes.toList(),
                    nativeElements = documentNativeElements
                )
            }
            val saveDocument: () -> Unit = {
                val currentDoc = currentNote()
                // Straight back over the file it came from; only a note
                // that has never been written asks where to go.
                if (!saveInPlace(currentDoc)) launchSavePicker(currentDoc)
            }
            val saveDocumentAs: () -> Unit = { launchSavePicker(currentNote()) }
            val newDocument: () -> Unit = { newTab {} }
            val clearCanvas: () -> Unit = {
                textSession = null
                if (strokes.isNotEmpty() || documentNativeElements.isNotEmpty()) {
                    pushUndo()
                    redoStack.clear()
                    strokes.clear()
                    selectedStrokes.clear()
                    selectedNatives.clear()
                    documentNativeElements = emptyList()
                    isModified = true
                }
            }
            /** The pen switched, by hand or by a button shortcut; the selection goes with the selector. */
            val switchTool: (ToolType) -> Unit = { newTool ->
                toolConfig = toolConfig.copy(activeTool = newTool)
                if (newTool != ToolType.SELECTOR) {
                    selectedStrokes.clear()
                    selectedNatives.clear()
                }
            }
            val selectTool: (ToolType) -> Unit = { newTool ->
                // A pen picked by hand takes a button's temporary pen off, as in Rnote.
                penShortcutState.picked()
                switchTool(newTool)
            }
            /**
             * Whether a temporary pen from a button still has something on the go, as Rnote's
             * pen reports it has not finished: a selection held, a text box open.
             */
            val shortcutPenBusy: () -> Boolean = {
                when (toolConfig.activeTool) {
                    ToolType.SELECTOR -> selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty()
                    ToolType.TYPEWRITER -> textSession != null
                    else -> false
                }
            }
            /** A button shortcut went down or came up; the pen in use after it. */
            val onShortcutKey: (ShortcutKey, Boolean) -> ToolType = { key, down ->
                val next = if (down) {
                    penShortcutState.press(key, penShortcuts, toolConfig.activeTool)
                } else {
                    penShortcutState.release(key, shortcutPenBusy())
                }
                next?.let(switchTool)
                toolConfig.activeTool
            }
            penShortcutKeyHandler = { key, down -> onShortcutKey(key, down) }
            // The selection let go or the text box left: a temporary pen done with goes.
            val shortcutBusyNow = shortcutPenBusy()
            LaunchedEffect(shortcutBusyNow) {
                penShortcutState.settle(shortcutBusyNow)?.let(switchTool)
            }
            // Which of the colour picker's two pads the palette sets; Rnote's starts on the stroke.
            var fillPadActive by remember { mutableStateOf(false) }
            /** Zoomed by [factor] about the middle of the view, as Rnote's zoom keys do. */
            val zoomBy: (Float) -> Unit = { factor ->
                val middle = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                viewportState = viewportState.zoomedAround(middle, viewportState.zoomScale * factor)
            }
            val zoomFitWidth: () -> Unit = {
                viewportState = viewportState.fittedToWidth(
                    canvasSize.width.toFloat(),
                    canvasSize.height.toFloat(),
                    if (paperStyle.pageSize.isInfinite) 0f else paperStyle.effectivePageWidthPx
                )
            }

            BabyRnoteTheme(darkTheme = paperStyle.isDarkMode) {
                Scaffold(
                    topBar = {
                        Column {
                            RnoteTopBar(
                                paperStyle = paperStyle,
                                zoomScale = viewportState.zoomScale,
                                allowFingerDrawing = toolConfig.allowFingerDrawing,
                                isModified = isModified,
                                documentTitle = documentTitle,
                                currentPage = pageGridLabel,
                                onResetZoom = { viewportState = ViewportState(displayScale = displayScale) },
                                // Unlike the zoom reset next to it, this keeps the zoom and
                                // only moves the view — see ViewportState.returnedToOrigin.
                                onReturnToOrigin = {
                                    viewportState = viewportState.returnedToOrigin(
                                        viewportWidthPx = canvasSize.width.toFloat(),
                                        // Nothing to centre on when the document has no pages.
                                        pageWidthPx = if (paperStyle.pageSize.isInfinite) 0f
                                                      else paperStyle.effectivePageWidthPx
                                    )
                                },
                                onTitleTap = {
                                    renameFieldValue = documentTitle
                                    showRenameDialog = true
                                },
                                onToggleFingerDrawing = {
                                    toolConfig = toolConfig.copy(allowFingerDrawing = !toolConfig.allowFingerDrawing)
                                    SettingsManager.save(
                                        this@MainActivity, paperStyle, toolConfig.allowFingerDrawing
                                    )
                                },
                                onSaveDocument = saveDocument,
                                onSaveDocumentAs = saveDocumentAs,
                                onPrint = { printDocument(currentNote()) },
                                onOpenDocument = {
                                    openDocumentLauncher.launch(arrayOf("*/*", "application/json"))
                                },
                                onImportPdf = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                                onInsertImage = {
                                    pickImageLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                canTakePhoto = hasCamera,
                                onTakePhoto = { takePhoto() },
                                hasPages = ExportLayout.hasPages(paperStyle),
                                hasSelection = selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty(),
                                onShare = shareNote,
                                onShowRecent = { showRecent = true },
                                onShowPages = { showPages = true },
                                onNewDocument = newDocument,
                                onExport = { showExportSheet = true },
                                onClearCanvas = clearCanvas,
                                onOpenPageSettings = { showPageSettings = true },
                                filesOpen = showFiles,
                                onToggleFiles = { showFiles = !showFiles },
                                snapPositions = toolConfig.snapPositions,
                                onToggleSnapPositions = toggleSnapPositions,
                                respectBorders = toolConfig.respectBorders,
                                onToggleRespectBorders = toggleRespectBorders,
                                penSounds = penSoundsOn,
                                onTogglePenSounds = togglePenSounds,
                                blockPinchZoom = toolConfig.blockPinchZoom,
                                onToggleBlockPinchZoom = toggleBlockPinchZoom,
                                onZoomOut = { zoomBy(1f / (1f + ViewportState.ZOOM_STEP)) },
                                onZoomIn = { zoomBy(1f + ViewportState.ZOOM_STEP) },
                                onZoomFitWidth = zoomFitWidth,
                                isFixedSize = paperStyle.layoutMode == LayoutMode.FIXED_SIZE,
                                canRemovePage = paperStyle.fixedPages > 1,
                                onAddPage = addPage,
                                onRemovePage = removePage,
                                onResizeToFitContent = fitPagesToContent,
                                focusMode = focusMode,
                                onToggleFocusMode = { focusMode = !focusMode },
                                fullscreen = fullscreen,
                                onToggleFullscreen = { fullscreen = !fullscreen }
                            )
                            // Desktop Rnote's tab bar: there once more than one note is open.
                            if (tabOrder.size > 1) {
                                NoteTabBar(
                                    tabs = tabOrder.map { id ->
                                        val parked = parkedTabs[id]
                                        if (id == activeTab || parked == null) NoteTab(id, documentTitle, isModified)
                                        else NoteTab(id, parked.title, parked.isModified)
                                    },
                                    active = activeTab,
                                    background = paperStyle.currentBackgroundColor.copy(alpha = 0.95f),
                                    darkTheme = paperStyle.isDarkMode,
                                    onSelect = { id -> switchTab(id) {} },
                                    onClose = closeTab,
                                    onNew = newDocument
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .onSizeChanged { canvasSize = it }
                            .onGloballyPositioned { canvasTop = it.positionInRoot().y }
                    ) {
                        DrawingCanvas(
                            toolConfig = toolConfig,
                            onShortcutKey = onShortcutKey,
                            onPenGestureEnd = {
                                penShortcutState.gestureEnded(shortcutPenBusy())?.let(switchTool)
                            },
                            paperStyle = paperStyle,
                            viewportState = viewportState,
                            strokes = strokes,
                            selectedStrokes = selectedStrokes,
                            onViewportChanged = { newViewport ->
                                viewportState = newViewport
                            },
                            onAddStroke = { newStroke ->
                                pushUndo()
                                redoStack.clear()
                                strokes.add(newStroke)
                                isModified = true
                            },
                            onEraseStart = {
                                // One snapshot for the whole eraser drag. This used to sit
                                // in onEraseStrokes, which fires per motion event, so
                                // rubbing out five strokes cost five undos to put back.
                                pushUndo()
                                redoStack.clear()
                            },
                            onEraseStrokes = { erased ->
                                val erasedIds = erased.map { it.id }.toSet()
                                strokes.removeAll { it.id in erasedIds }
                                isModified = true
                            },
                            onSelectionDragStart = {
                                pushUndo()
                                redoStack.clear()
                            },
                            onStrokesModified = { updatedStrokes ->
                                val updatedById = updatedStrokes.associateBy { it.id }
                                for (i in strokes.indices) {
                                    updatedById[strokes[i].id]?.let { strokes[i] = it }
                                }
                                isModified = true
                            },
                            // The box being typed into is shown by the text field over it instead.
                            nativeElements = textSession?.element
                                ?.let { editing -> documentNativeElements.filter { it !== editing } }
                                ?: documentNativeElements,
                            selectedNatives = selectedNatives,
                            penSounds = penSounds,
                            onAddShapes = { shapes ->
                                // One undo step for all the lines of a grid or a coordinate system.
                                pushUndo()
                                redoStack.clear()
                                documentNativeElements = documentNativeElements + shapes
                                isModified = true
                            },
                            onEraseNatives = { erased ->
                                // The undo snapshot was taken by onEraseStart for the gesture.
                                val gone = java.util.Collections.newSetFromMap(
                                    java.util.IdentityHashMap<NativeCanvasElement, Boolean>()
                                ).apply { addAll(erased) }
                                documentNativeElements = documentNativeElements.filter { it !in gone }
                                isModified = true
                            },
                            onNativesMoved = { moved ->
                                documentNativeElements = documentNativeElements.map { moved[it] ?: it }
                                isModified = true
                            },
                            onSplitStrokes = { split ->
                                // Each cut stroke is replaced where it stood, so the pieces
                                // keep its place in the drawing order.
                                for (i in strokes.indices.reversed()) {
                                    val pieces = split[strokes[i].id] ?: continue
                                    strokes.removeAt(i)
                                    strokes.addAll(i, pieces)
                                }
                                selectedStrokes.removeAll { it.id in split.keys }
                                isModified = true
                            },
                            onTypewriterTap = { x, y ->
                                // Into the box tapped, the cursor where the tap was; else a new box
                                // with its top-left corner there — which only exists once typed into.
                                val slop = 12f / viewportState.effectiveScale
                                val hit = NativeEditing.textAt(documentNativeElements, x, y, slop)
                                textSessionCount++
                                textSession = if (hit != null) {
                                    TextSession(
                                        textSessionCount, hit.transform[4], hit.transform[5], hit, hit,
                                        TextFieldValue(hit.text, TextRange(NativeElementRenderer.charOffsetAt(hit, x, y)))
                                    )
                                } else {
                                    // With Snap Positions on, a new box goes to the pattern, as in Rnote.
                                    val at = if (toolConfig.snapPositions) {
                                        SnapPositions.snap(Offset(x, y), paperStyle)
                                    } else {
                                        Offset(x, y)
                                    }
                                    TextSession(textSessionCount, at.x, at.y, null, null, TextFieldValue(""))
                                }
                            },
                            onVerticalSpace = { dy, strokeIds, natives ->
                                pushUndo()
                                redoStack.clear()
                                // In place, so every stroke keeps its position in the drawing order.
                                val shift = Offset(0f, dy)
                                for (i in strokes.indices) {
                                    if (strokes[i].id in strokeIds) {
                                        strokes[i] = SelectionManager.translateStrokes(listOf(strokes[i]), shift).single()
                                    }
                                }
                                documentNativeElements = documentNativeElements.map {
                                    if (it in natives) NativeEditing.translate(it, 0f, dy) else it
                                }
                                isModified = true
                            },
                        )

                        // The Typewriter's text field, over the box being typed into.
                        textSession?.let { session ->
                            key(session.id) {
                                val box = session.element ?: session.template
                                // Above the keyboard: on Android 15 it covers the window rather
                                // than shrinking it, so its height comes from the insets.
                                val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                                val rootHeight = LocalView.current.height.toFloat()
                                InlineTextEditor(
                                    value = session.value,
                                    onValueChange = onTextChange,
                                    style = box?.let { TextBoxStyle.of(it) } ?: TextBoxStyle(
                                        NativeEditing.TEXT_FONT_FAMILY, toolConfig.textSize, 500, false,
                                        toolConfig.penColor.let { RnoteNativeColor(it.red, it.green, it.blue, it.alpha) },
                                        toolConfig.textAlignment.apiName,
                                        NativeEditing.typewriterWrapWidth(session.x, paperStyle.effectivePageWidthPx)
                                    ),
                                    runs = session.element?.let { TextFormatting.runs(it) } ?: emptyList(),
                                    topLeft = Offset(session.x, session.y),
                                    viewportState = viewportState,
                                    visibleBottom = minOf(canvasSize.height.toFloat(), rootHeight - imeBottom - canvasTop),
                                    onPan = { dy ->
                                        viewportState = viewportState.copy(panOffset = viewportState.panOffset + Offset(0f, dy))
                                    },
                                    onToggle = onToggleTextFormat,
                                    onDone = { textSession = null },
                                    onCursorKey = { penSounds?.cursorKey() }
                                )
                            }
                        }

                        // Rnote's colour picker also recolours the selection while the
                        // selector is out: its lines and text, or its shapes' fill.
                        val recolorSelection: (Color, Boolean) -> Unit = { color, fill ->
                            // Only ink, shapes and text have a colour; only shapes a fill.
                            val affected = if (fill) {
                                selectedNatives.any { it is NativeShapeElement }
                            } else {
                                selectedStrokes.isNotEmpty() ||
                                    selectedNatives.any { it is NativeShapeElement || it is NativeTextElement }
                            }
                            if (toolConfig.activeTool == ToolType.SELECTOR && affected) {
                                pushUndo()
                                redoStack.clear()
                                val native = RnoteNativeColor(color.red, color.green, color.blue, color.alpha)
                                if (!fill) {
                                    val recolored = SelectionManager.recolored(selectedStrokes.toList(), color)
                                    val byId = recolored.associateBy { it.id }
                                    for (i in strokes.indices) {
                                        byId[strokes[i].id]?.let { strokes[i] = it }
                                    }
                                    selectedStrokes.clear()
                                    selectedStrokes.addAll(recolored)
                                }
                                val swapped = java.util.IdentityHashMap<NativeCanvasElement, NativeCanvasElement>()
                                for (el in selectedNatives) {
                                    swapped[el] = if (fill) NativeEditing.withFillColor(el, native)
                                        else NativeEditing.withStrokeColor(el, native)
                                }
                                documentNativeElements = documentNativeElements.map { swapped[it] ?: it }
                                val kept = selectedNatives.map { swapped[it] ?: it }
                                selectedNatives.clear()
                                selectedNatives.addAll(kept)
                                isModified = true
                            }
                        }

                        // Rnote's "Invert Color Brightness of All Selected Strokes": every
                        // colour of the selection, light for dark, in one undo step.
                        val invertSelectionColors: () -> Unit = {
                            val affected = selectedStrokes.isNotEmpty() ||
                                selectedNatives.any { it is NativeShapeElement || it is NativeTextElement }
                            if (affected) {
                                pushUndo()
                                redoStack.clear()
                                val inverted = SelectionManager.inverted(selectedStrokes.toList())
                                val byId = inverted.associateBy { it.id }
                                for (i in strokes.indices) {
                                    byId[strokes[i].id]?.let { strokes[i] = it }
                                }
                                selectedStrokes.clear()
                                selectedStrokes.addAll(inverted)
                                val swapped = java.util.IdentityHashMap<NativeCanvasElement, NativeCanvasElement>()
                                for (el in selectedNatives) swapped[el] = NativeEditing.withInvertedColors(el)
                                documentNativeElements = documentNativeElements.map { swapped[it] ?: it }
                                val kept = selectedNatives.map { swapped[it] ?: it }
                                selectedNatives.clear()
                                selectedNatives.addAll(kept)
                                isModified = true
                            }
                        }

                        // Top-center: stroke and fill color + palette (matches Rnote's colorpicker.ui)
                        if (!focusMode) ColorPicker(
                            activeColor = toolConfig.currentActiveColor,
                            onColorSelected = { newColor ->
                                toolConfig = if (toolConfig.activeTool == ToolType.BRUSH && toolConfig.brushStyle == BrushStyle.MARKER) {
                                    toolConfig.copy(highlighterColor = newColor)
                                } else {
                                    toolConfig.copy(penColor = newColor)
                                }
                                recolorSelection(newColor, false)
                            },
                            fillColor = toolConfig.fillColor,
                            fillPadActive = fillPadActive,
                            onPadSelected = { fill -> fillPadActive = fill },
                            onFillColorSelected = { newColor ->
                                toolConfig = toolConfig.copy(fillColor = newColor)
                                recolorSelection(newColor, true)
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 18.dp)
                        )

                        val deleteSelection: () -> Unit = {
                            if (selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty()) {
                                pushUndo()
                                redoStack.clear()
                                val ids = selectedStrokes.map { it.id }.toSet()
                                strokes.removeAll { it.id in ids }
                                val gone = java.util.Collections.newSetFromMap(
                                    java.util.IdentityHashMap<NativeCanvasElement, Boolean>()
                                ).apply { addAll(selectedNatives) }
                                documentNativeElements = documentNativeElements.filter { it !in gone }
                                selectedStrokes.clear()
                                selectedNatives.clear()
                                isModified = true
                            }
                        }
                        val copySelection: () -> Unit = {
                            if (selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty()) {
                                SelectionClipboard.clip = Clip(selectedStrokes.toList(), selectedNatives.toList())
                                // And for other apps, as a picture of it.
                                copySelectionImage(
                                    NoteDocument(
                                        title = documentTitle,
                                        paperStyle = paperStyle,
                                        strokes = strokes.toList(),
                                        nativeElements = documentNativeElements
                                    ),
                                    selectedStrokes.toList(),
                                    selectedNatives.toList()
                                )
                            }
                        }
                        val pasteClipboard: () -> Unit = paste@{
                            val clip = SelectionClipboard.clip ?: return@paste
                            val box = clip.bounds() ?: return@paste
                            // Where the copy was, nudged so it shows as a copy — unless that is
                            // out of view (another note, or scrolled away): then mid-screen.
                            val viewTopLeft = viewportState.screenToCanvas(Offset.Zero)
                            val viewBottomRight = viewportState.screenToCanvas(
                                Offset(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                            )
                            val inView = box[2] >= viewTopLeft.x && box[0] <= viewBottomRight.x &&
                                box[3] >= viewTopLeft.y && box[1] <= viewBottomRight.y
                            val dx: Float
                            val dy: Float
                            if (inView || canvasSize.width == 0) {
                                dx = PASTE_OFFSET; dy = PASTE_OFFSET
                            } else {
                                dx = (viewTopLeft.x + viewBottomRight.x) / 2f - (box[0] + box[2]) / 2f
                                dy = (viewTopLeft.y + viewBottomRight.y) / 2f - (box[1] + box[3]) / 2f
                            }
                            pushUndo()
                            redoStack.clear()
                            // Moved as the selector moves strokes, curves and all.
                            val newStrokes = SelectionManager.translateStrokes(clip.strokes, Offset(dx, dy))
                                .map { it.copy(id = java.util.UUID.randomUUID().toString()) }
                            // Always new instances: the document tells its elements apart by identity.
                            val newNatives = clip.natives.map { NativeEditing.translate(it, dx, dy) }
                            strokes.addAll(newStrokes)
                            documentNativeElements = documentNativeElements + newNatives
                            selectedStrokes.clear()
                            selectedStrokes.addAll(newStrokes)
                            selectedNatives.clear()
                            selectedNatives.addAll(newNatives)
                            // Pasting again cascades from here, as a second duplicate would.
                            SelectionClipboard.clip = Clip(newStrokes, newNatives)
                            isModified = true
                        }

                        val duplicateSelection: () -> Unit = {
                            if (selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty()) {
                                pushUndo()
                                redoStack.clear()
                                val offset = 20f
                                val duplicates = SelectionManager.translateStrokes(selectedStrokes.toList(), Offset(offset, offset))
                                    .map { it.copy(id = java.util.UUID.randomUUID().toString()) }
                                strokes.addAll(duplicates)
                                selectedStrokes.clear()
                                selectedStrokes.addAll(duplicates)
                                val nativeCopies = selectedNatives.map {
                                    io.github.kjly.brna.storage.NativeEditing.translate(it, offset, offset)
                                }
                                documentNativeElements = documentNativeElements + nativeCopies
                                selectedNatives.clear()
                                selectedNatives.addAll(nativeCopies)
                                isModified = true
                            }
                        }
                        val selectAll: () -> Unit = {
                            selectedStrokes.clear()
                            selectedStrokes.addAll(strokes)
                            selectedNatives.clear()
                            selectedNatives.addAll(documentNativeElements.filter { it !is NativeBrushStroke })
                        }
                        val deselectAll: () -> Unit = {
                            selectedStrokes.clear()
                            selectedNatives.clear()
                        }

                        // ── Keyboard shortcuts (see KeyboardShortcuts) ─────────────────
                        shortcutHandler = handler@{ shortcut ->
                            val hasSelection = selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty()
                            val selecting = toolConfig.activeTool == ToolType.SELECTOR
                            when (shortcut) {
                                Shortcut.OPEN -> openDocumentLauncher.launch(arrayOf("*/*", "application/json"))
                                Shortcut.SAVE -> saveDocument()
                                Shortcut.SAVE_AS -> saveDocumentAs()
                                Shortcut.NEW -> newDocument()
                                Shortcut.CLOSE_TAB -> closeTab(activeTab)
                                Shortcut.NEXT_TAB -> if (tabOrder.size > 1) stepTab(1) else return@handler false
                                Shortcut.PREVIOUS_TAB -> if (tabOrder.size > 1) stepTab(-1) else return@handler false
                                Shortcut.PRINT -> printDocument(currentNote())
                                Shortcut.IMPORT -> importFileLauncher.launch(IMPORTABLE_TYPES)
                                Shortcut.CLEAR -> clearCanvas()
                                Shortcut.PAGE_OVERVIEW -> showPages = true
                                Shortcut.SNAP_POSITIONS -> toggleSnapPositions()
                                Shortcut.ADD_PAGE -> if (paperStyle.layoutMode == LayoutMode.FIXED_SIZE) addPage() else return@handler false
                                Shortcut.REMOVE_PAGE -> if (paperStyle.layoutMode == LayoutMode.FIXED_SIZE) removePage() else return@handler false
                                Shortcut.FULLSCREEN -> fullscreen = !fullscreen
                                Shortcut.UNDO -> performUndoAction?.invoke()
                                Shortcut.REDO -> performRedoAction?.invoke()
                                Shortcut.COPY -> if (hasSelection) copySelection() else return@handler false
                                Shortcut.CUT -> if (hasSelection) {
                                    copySelection()
                                    deleteSelection()
                                } else {
                                    return@handler false
                                }
                                Shortcut.PASTE -> {
                                    if (SelectionClipboard.clip == null) return@handler false
                                    // What is pasted comes in selected, so the selector has to be out.
                                    selectTool(ToolType.SELECTOR)
                                    pasteClipboard()
                                }
                                Shortcut.SELECT_ALL -> {
                                    selectTool(ToolType.SELECTOR)
                                    selectAll()
                                }
                                Shortcut.DUPLICATE -> if (selecting && hasSelection) duplicateSelection() else return@handler false
                                Shortcut.DELETE_SELECTION -> if (selecting && hasSelection) deleteSelection() else return@handler false
                                Shortcut.DESELECT -> if (selecting && hasSelection) deselectAll() else return@handler false
                                Shortcut.ZOOM_IN -> zoomBy(1f + ViewportState.ZOOM_STEP)
                                Shortcut.ZOOM_OUT -> zoomBy(1f / (1f + ViewportState.ZOOM_STEP))
                                Shortcut.ZOOM_RESET -> zoomBy(1f / viewportState.zoomScale)
                                Shortcut.BRUSH -> selectTool(ToolType.BRUSH)
                                Shortcut.SHAPER -> selectTool(ToolType.SHAPER)
                                Shortcut.TYPEWRITER -> selectTool(ToolType.TYPEWRITER)
                                Shortcut.ERASER -> selectTool(ToolType.ERASER)
                                Shortcut.SELECTOR -> selectTool(ToolType.SELECTOR)
                                Shortcut.TOOLS -> selectTool(ToolType.TOOLS)
                            }
                            true
                        }

                        // Left edge, vertically centered: per-pen config (matches RnPensSideBar).
                        // Hidden below the width breakpoint (see isCompactWidth, top of file).
                        if (!isCompactWidth && !focusMode) PenConfigStrip(
                            toolConfig = toolConfig,
                            hasActiveSelection = selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty(),
                            onBrushStyleSelected = { style -> toolConfig = toolConfig.copy(brushStyle = style) },
                            onSizeChanged = { newSize -> toolConfig = toolConfig.updateActiveSize(newSize) },
                            onDeleteSelection = deleteSelection,
                            onDuplicateSelection = duplicateSelection,
                            onSelectAll = selectAll,
                            onDeselectAll = deselectAll,
                            onShapeKindSelected = { kind -> toolConfig = toolConfig.copy(shapeKind = kind) },
                            onEraserModeSelected = { mode -> toolConfig = toolConfig.copy(eraserMode = mode) },
                            canPaste = SelectionClipboard.clip != null,
                            onCopySelection = copySelection,
                            onCutSelection = {
                                copySelection()
                                deleteSelection()
                            },
                            onPaste = pasteClipboard,
                            onLockAspectRatioToggled = {
                                toolConfig = toolConfig.copy(lockAspectRatio = !toolConfig.lockAspectRatio)
                            },
                            onSelectorModeSelected = { mode -> toolConfig = toolConfig.copy(selectorMode = mode) },
                            onToolsModeSelected = { mode -> toolConfig = toolConfig.copy(toolsMode = mode) },
                            onSnapAnglesToggled = {
                                toolConfig = toolConfig.copy(snapAngles = !toolConfig.snapAngles)
                            },
                            onShapeConstraintsChanged = { constraints ->
                                toolConfig = toolConfig.copy(shapeConstraints = constraints)
                            },
                            favorites = penFavorites,
                            onApplyFavorite = { favorite -> toolConfig = toolConfig.withFavorite(favorite) },
                            onStoreFavorite = { slot -> setFavorite(slot, toolConfig.brushFavorite()) },
                            onClearFavorite = { slot -> setFavorite(slot, null) },
                            textFormats = textFormats,
                            textFormatsEnabled = textSession != null,
                            onToggleTextFormat = onToggleTextFormat,
                            textAlignment = textAlignment,
                            onTextAlignmentSelected = onTextAlignmentSelected,
                            onPressureCurveSelected = { curve -> toolConfig = toolConfig.copy(pressureCurve = curve) },
                            onShapeLineChanged = { line -> toolConfig = toolConfig.copy(shapeLine = line) },
                            onShaperStyleSelected = { style -> toolConfig = toolConfig.copy(shaperStyle = style) },
                            onRoughFillSelected = { fill -> toolConfig = toolConfig.copy(roughFill = fill) },
                            onRoughHachureDegreesChanged = { degrees -> toolConfig = toolConfig.copy(roughHachureDegrees = degrees) },
                            onInvertSelectionColors = invertSelectionColors,
                            onTexturedDensityChanged = { density -> toolConfig = toolConfig.copy(texturedDensity = density) },
                            onTexturedDistributionSelected = { distribution ->
                                toolConfig = toolConfig.copy(texturedDistribution = distribution)
                            },
                            onPenPathBuilderSelected = { builder -> toolConfig = toolConfig.copy(penPathBuilder = builder) },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 18.dp)
                        )

                        // Bottom-center: pen switcher + undo/redo (matches Rnote's penpicker.ui)
                        if (!focusMode) PenPicker(
                            toolConfig = toolConfig,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            onToolSelected = selectTool,
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
                                    // Rnote fits a Fixed Size document to its content again
                                    // when the format or the layout changes.
                                    val refit = it.layoutMode == LayoutMode.FIXED_SIZE && (
                                        it.layoutMode != paperStyle.layoutMode ||
                                            it.effectivePageWidthPx != paperStyle.effectivePageWidthPx ||
                                            it.effectivePageHeightPx != paperStyle.effectivePageHeightPx
                                        )
                                    paperStyle = it
                                    if (refit) fitPagesToContent()
                                    // An explicit choice is both an edit to this document
                                    // and the default the next new note should start from.
                                    persistSettings(it)
                                    isModified = true
                                },
                                penShortcuts = penShortcuts,
                                onPenShortcutsChanged = {
                                    penShortcuts = it
                                    SettingsManager.savePenShortcuts(this@MainActivity, it)
                                },
                                onDismiss = { showPageSettings = false },
                                dockedAsSidePanel = !isCompactWidth,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }

                        // ── Workspace browser, from the left as Rnote's sidebar ────────
                        AnimatedVisibility(
                            visible = showFiles,
                            enter = slideInHorizontally { -it },
                            exit = slideOutHorizontally { -it },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            WorkspaceBrowser(
                                workspaces = workspaces,
                                selected = selectedWorkspace,
                                openDocuments = listOfNotNull(currentDocumentUri) + parkedTabs.values.mapNotNull { it.uri },
                                refreshKey = filesRefresh,
                                path = filesPath,
                                onPathChange = { filesPath = it },
                                onSelectWorkspace = { selectWorkspace(it) },
                                onAddWorkspace = { addWorkspaceLauncher.launch(null) },
                                onEditWorkspace = { ws, name, color -> editWorkspace(ws, name, color) },
                                onRemoveWorkspace = { removeWorkspace(it) },
                                onOpen = { uri, kind ->
                                    when (kind) {
                                        // In a tab of its own, or its tab if it is open already.
                                        FolderListing.Kind.NOTE -> {
                                            showFiles = false
                                            if (!FolderBrowser.sameDocument(uri, currentDocumentUri)) openRecent(uri)
                                        }
                                        // Made into a new note, as Rnote opens one.
                                        FolderListing.Kind.XOPP -> {
                                            showFiles = false
                                            openRecent(uri)
                                        }
                                        // Into the open note, as Rnote's browser does with them.
                                        FolderListing.Kind.PDF -> {
                                            showFiles = false
                                            importPdf(uri)
                                        }
                                        FolderListing.Kind.IMAGE -> {
                                            showFiles = false
                                            insertImage(uri)
                                        }
                                        FolderListing.Kind.FOLDER -> Unit
                                    }
                                },
                                onNewNote = { tree, folderId, name ->
                                    showFiles = false
                                    newNoteIn(tree, folderId, name)
                                },
                                onOpenRenamed = { old, uri, name ->
                                    val title = DocumentUri.titleFrom(name)
                                    if (FolderBrowser.sameDocument(old, currentDocumentUri)) {
                                        currentDocumentUri?.let { RecentFiles.remove(this@MainActivity, it.toString()) }
                                        documentTitle = title
                                        adoptDocumentUri(uri, title)
                                        // Some providers count a rename as a change: not one made elsewhere.
                                        lifecycleScope.launch {
                                            knownLastModified = withContext(Dispatchers.IO) {
                                                DocumentUri.lastModified(this@MainActivity, uri)
                                            }
                                        }
                                    } else {
                                        // A note in another tab: its tab follows the file.
                                        parkedTabs.entries.firstOrNull { FolderBrowser.sameDocument(old, it.value.uri) }
                                            ?.let { (id, tab) ->
                                                tab.uri?.let { RecentFiles.remove(this@MainActivity, it.toString()) }
                                                RecentFiles.add(this@MainActivity, uri, title)
                                                parkedTabs[id] = tab.copy(uri = uri, title = title)
                                                lifecycleScope.launch {
                                                    val modified = withContext(Dispatchers.IO) {
                                                        DocumentUri.lastModified(this@MainActivity, uri)
                                                    }
                                                    parkedTabs[id]?.takeIf { it.uri == uri }?.let {
                                                        parkedTabs[id] = it.copy(knownLastModified = modified)
                                                    }
                                                }
                                            }
                                    }
                                },
                                onOpenDeleted = { uri ->
                                    RecentFiles.remove(this@MainActivity, uri.toString())
                                    if (FolderBrowser.sameDocument(uri, currentDocumentUri)) {
                                        currentDocumentUri?.let { RecentFiles.remove(this@MainActivity, it.toString()) }
                                        currentDocumentUri = null
                                        knownLastModified = null
                                        knownContentHash = null
                                        // Still open here, now with nowhere to go: Save asks where.
                                        isModified = true
                                    } else {
                                        parkedTabs.entries.firstOrNull { FolderBrowser.sameDocument(uri, it.value.uri) }
                                            ?.let { (id, tab) ->
                                                tab.uri?.let { RecentFiles.remove(this@MainActivity, it.toString()) }
                                                parkedTabs[id] = tab.copy(
                                                    uri = null, knownLastModified = null, knownContentHash = null, isModified = true
                                                )
                                                // Now it lives only here: kept where recovery finds it.
                                                val note = NoteDocument(
                                                    title = tab.title, paperStyle = tab.paperStyle,
                                                    strokes = tab.strokes, nativeElements = tab.natives
                                                )
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    try {
                                                        Recovery.write(this@MainActivity, note, null, tab.saveAsRnote, id)
                                                    } catch (e: Throwable) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                    }
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Its file was deleted — the note is still open in its tab; save it to keep it",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onClose = { showFiles = false },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .then(if (isCompactWidth) Modifier.fillMaxWidth() else Modifier.width(340.dp))
                            )
                        }

                        // ── Progress card while a file is read or written ─────────────
                        busyMessage?.let { message ->
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(Color(0xE61E1E2E), RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(message, color = Color.White, fontSize = 14.sp)
                            }
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
                            hasSelection = selectedStrokes.isNotEmpty() || selectedNatives.isNotEmpty(),
                            hasImportedPages = DocumentExporter.hasImportedPages(exportDocument),
                            onPrefsChanged = { exportPrefs = it },
                            onDismiss = { showExportSheet = false },
                            onExport = {
                                showExportSheet = false
                                startExport(
                                    PendingExport(
                                        document = exportDocument,
                                        selection = selectedStrokes.toList(),
                                        prefs = exportPrefs,
                                        baseName = documentTitle,
                                        selectedNatives = selectedNatives.toList()
                                    )
                                )
                            }
                        )
                    }

                    // ── Recently opened notes ─────────────────────────────────────
                    if (showRecent) {
                        val entries = remember { RecentFiles.list(this@MainActivity) }
                        RecentFilesDialog(
                            entries = entries,
                            currentUri = currentDocumentUri?.toString(),
                            onOpen = { entry ->
                                showRecent = false
                                openRecent(Uri.parse(entry.uri))
                            },
                            onDismiss = { showRecent = false }
                        )
                    }

                    // ── Page overview ──────────────────────────────────────────────
                    if (showPages) {
                        // The note as it is when the overview opens; its pictures show that.
                        val overviewDocument = remember {
                            NoteDocument(
                                title = documentTitle,
                                paperStyle = paperStyle,
                                strokes = strokes.toList(),
                                nativeElements = documentNativeElements
                            )
                        }
                        val pages = remember { DocumentExporter.pagesFor(overviewDocument, ExportPrefs()) }
                        val current = remember {
                            val centre = viewportState.screenToCanvas(
                                Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                            )
                            pages.indexOfFirst { it.contains(centre) }.takeIf { it >= 0 }
                        }
                        PageOverviewDialog(
                            pages = pages,
                            currentPage = current,
                            renderThumbnail = { page ->
                                PageThumbnails.render(overviewDocument, page, THUMBNAIL_WIDTH_PX)
                            },
                            onPageSelected = { index ->
                                showPages = false
                                val page = pages[index]
                                viewportState = viewportState.showingPage(
                                    page.left, page.top, page.width, canvasSize.width.toFloat()
                                )
                            },
                            onDismiss = { showPages = false }
                        )
                    }

                    // ── Changed elsewhere since it was opened ─────────────────────
                    pendingConflict?.let { conflicted ->
                        AlertDialog(
                            onDismissRequest = { pendingConflict = null },
                            title = { Text("File changed elsewhere") },
                            text = {
                                Text(
                                    "\"${conflicted.title}\" was changed since you opened it — " +
                                        "on another device, for example.\n\n" +
                                        "Save as copy keeps both versions. Overwrite replaces " +
                                        "theirs with yours. Load theirs opens their version and " +
                                        "discards the changes made on this device."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingConflict = null
                                    launchSavePicker(conflicted)
                                }) { Text("Save as copy") }
                            },
                            // Both emitted straight into the dialog's button row, not wrapped
                            // in a Row, so the three buttons can wrap on a narrow screen.
                            dismissButton = {
                                TextButton(onClick = {
                                    pendingConflict = null
                                    currentDocumentUri?.let { openDocument(it, reload = true) }
                                }) { Text("Load theirs") }
                                TextButton(onClick = {
                                    pendingConflict = null
                                    saveInPlace(conflicted, overwriteChanges = true)
                                }) { Text("Overwrite") }
                            }
                        )
                    }

                    // ── Recovered from the last session ───────────────────────────
                    val recovered = pendingRecovery
                    if (recovered.isNotEmpty()) {
                        val titles = recovered.joinToString(", ") { "\"${it.document.title}\"" }
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text(if (recovered.size == 1) "Restore unsaved note?" else "Restore unsaved notes?") },
                            text = {
                                Text(
                                    "$titles had changes that were not saved when the app was last closed." +
                                        if (recovered.size > 1) " Each comes back in a tab of its own." else ""
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingRecovery = emptyList()
                                    restoreRecovered(recovered)
                                }) { Text("Restore") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    pendingRecovery = emptyList()
                                    for (r in recovered) Recovery.clear(this@MainActivity, r.slot)
                                }) { Text("Discard") }
                            }
                        )
                    }

                    // ── Closing a tab whose note isn't all in its file ────────────
                    if (confirmClose) {
                        AlertDialog(
                            onDismissRequest = { confirmClose = false },
                            title = { Text("Close without saving?") },
                            text = {
                                Text(
                                    if (currentDocumentUri == null) {
                                        "\"$documentTitle\" has never been saved. Closing its tab discards it."
                                    } else {
                                        "\"$documentTitle\" has changes that couldn't be saved to its file. " +
                                            "Closing its tab discards them."
                                    }
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmClose = false
                                    finishClose()
                                }) { Text("Discard") }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(onClick = { confirmClose = false }) { Text("Cancel") }
                                    TextButton(onClick = {
                                        confirmClose = false
                                        saveDocument()
                                    }) { Text("Save…") }
                                }
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
        // "Open with" — only for the launch that brought the file, not when the activity
        // is recreated with the same intent after the process was reclaimed.
        if (savedInstanceState == null) {
            if (intent?.action == Intent.ACTION_VIEW) handleViewIntent(intent) else offerRecovery()
        }
    }

    /** Looks for notes the last session didn't get to save, and offers them back. */
    private fun offerRecovery() {
        lifecycleScope.launch {
            pendingRecovery = withContext(Dispatchers.IO) { Recovery.readAll(this@MainActivity) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Toni may have saved the open note on the laptop meanwhile.
        checkForNewerVersion()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the foreground is when Android may reclaim the app without asking, so
        // unsaved work is secured now rather than at the next timer tick. Preferences need
        // nothing here: they are written the moment they are chosen (see persistSettings).
        autosave()
    }

    private companion object {
        /** How long after the first unsaved change autosave runs. Rnote's default is 120 s. */
        const val AUTOSAVE_DELAY_MS = 60_000L

        /**
         * How often the open note's file is looked at while the app is open. Drive syncs a
         * save from the laptop in some seconds; a look every half minute is cheap, and
         * soon enough to be following along.
         */
        const val NEWER_VERSION_POLL_MS = 30_000L

        /** What Rnote's "Import" takes that this app can: PDFs and pictures. */
        val IMPORTABLE_TYPES = arrayOf("application/pdf", "image/png", "image/jpeg")
    }

    /**
     * Desktop Rnote's keyboard shortcuts (see [KeyboardShortcuts]). Only keys nothing else
     * took arrive here: a text box being typed into gets its own first.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // The pen's press is acted on as it is let go (onKeyUp); its going down is the
        // pen's too, and must not start the music as a Play key would.
        if (PenRemote.isPenKey(keyCode) && isPenRemote(event)) return true
        // Rnote's Ctrl+Space button shortcut: held down, it goes down once.
        if (keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
            if (event.repeatCount == 0 && !ctrlSpaceDown) {
                ctrlSpaceDown = true
                penShortcutKeyHandler?.invoke(ShortcutKey.KEYBOARD_CTRL_SPACE, true)
            }
            return true
        }
        // What the key types with no modifier held, on the keyboard's own layout.
        val char = event.getUnicodeChar(0).takeIf { it > 0 }?.toChar()
        val shortcut = KeyboardShortcuts.of(
            char, keyCode, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed || event.isMetaPressed
        )
        if (shortcut != null) {
            // Held down, only undo, redo and zoom repeat; saving or printing once is enough.
            if (event.repeatCount > 0 && !shortcut.repeats) return true
            if (shortcutHandler?.invoke(shortcut) == true) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Whether [event] is a press of the S Pen's remote (see [PenRemote]). */
    private fun isPenRemote(event: KeyEvent?): Boolean {
        if (event == null) return false
        val device = event.device
        return PenRemote.isFromPen(
            device?.name,
            event.deviceId,
            typingKeyboard = device?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        )
    }

    /**
     * Samsung S-Pen Air Action Remote Button Key Event Handler:
     * - Single Press / Page Down: Undo
     * - Page Up: Redo
     * Only for the pen: the same keys from a keyboard or headset do what they do anywhere.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE && ctrlSpaceDown) {
            ctrlSpaceDown = false
            penShortcutKeyHandler?.invoke(ShortcutKey.KEYBOARD_CTRL_SPACE, false)
            return true
        }
        if (!PenRemote.isPenKey(keyCode) || !isPenRemote(event)) return super.onKeyUp(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) performRedoAction?.invoke() else performUndoAction?.invoke()
        return true
    }
}
