package io.github.kjly.brna.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kjly.brna.export.ShareTarget
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.ui.icons.GeneratedIcons
import io.github.kjly.brna.ui.theme.BrnaColors

/**
 * Trimmed to match desktop Rnote's headerbar: title + Page Settings + overflow
 * (New/Open/Save/Export/Clear) only. Undo/redo live in [PenPicker] now (Rnote made
 * the same move in v0.7.0 — bottom-center is thumb-reachable, top-right isn't).
 * Pattern cycling, theme, and landscape are already controllable from Page
 * Settings, so the duplicate quick-toggles that used to live here were removed
 * rather than kept as a second path to the same state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RnoteTopBar(
    paperStyle: PaperStyle,
    zoomScale: Float,
    allowFingerDrawing: Boolean,
    isModified: Boolean,
    documentTitle: String,
    currentPage: String?,          // null in infinite mode; "col, row" in paged mode
    onResetZoom: () -> Unit,
    onReturnToOrigin: () -> Unit,
    onTitleTap: () -> Unit,
    onToggleFingerDrawing: () -> Unit,
    onSaveDocument: () -> Unit,
    onSaveDocumentAs: () -> Unit,
    onOpenDocument: () -> Unit,
    onNewDocument: () -> Unit,
    onExport: () -> Unit,
    onClearCanvas: () -> Unit,
    onOpenPageSettings: () -> Unit,
    /** Desktop Rnote's "Import PDF": the pages go into the open note. */
    onImportPdf: () -> Unit = {},
    /** A picture from the gallery into the open note. */
    onInsertImage: () -> Unit = {},
    /** False on a device without a camera, which hides "Take photo". */
    canTakePhoto: Boolean = false,
    /** A photo taken now into the open note. */
    onTakePhoto: () -> Unit = {},
    /** Whether the paper has pages, which decides whether Share offers "this page" or the view. */
    hasPages: Boolean = true,
    /** Whether the selector holds anything to share. */
    hasSelection: Boolean = false,
    /** Sends a page, the selection or the note to another app. */
    onShare: (ShareTarget) -> Unit = {},
    /** The notes opened or saved last. */
    onShowRecent: () -> Unit = {},
    /** Thumbnails of every page, to jump to one. */
    onShowPages: () -> Unit = {},
    /** The note to Android's print dialog. */
    onPrint: () -> Unit = {},
    /** Whether the workspace side panel is showing. */
    filesOpen: Boolean = false,
    /** Shows or hides the workspace side panel, as the sidebar button in Rnote's headerbar does. */
    onToggleFiles: () -> Unit = {},
    /** Rnote's "Snap Positions", a switch in its canvas menu. */
    snapPositions: Boolean = false,
    onToggleSnapPositions: () -> Unit = {},
    /** Rnote's "Respect Borders When Pasting", "Pen Sounds" and "Block Pinch to Zoom", in its canvas menu. */
    respectBorders: Boolean = false,
    onToggleRespectBorders: () -> Unit = {},
    penSounds: Boolean = false,
    onTogglePenSounds: () -> Unit = {},
    blockPinchZoom: Boolean = false,
    onToggleBlockPinchZoom: () -> Unit = {},
    /** Rnote's canvas menu zoom row: out, in, and to the page's width. */
    onZoomOut: () -> Unit = {},
    onZoomIn: () -> Unit = {},
    onZoomFitWidth: () -> Unit = {},
    /** Rnote's page buttons, which only a Fixed Size document has a use for. */
    isFixedSize: Boolean = false,
    canRemovePage: Boolean = false,
    onAddPage: () -> Unit = {},
    onRemovePage: () -> Unit = {},
    onResizeToFitContent: () -> Unit = {},
    /** Rnote's headerbar Focus Mode: the pens, colours and their settings put away. */
    focusMode: Boolean = false,
    onToggleFocusMode: () -> Unit = {},
    /** Rnote's app menu Fullscreen (F11): Android's status and navigation bars put away. */
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCanvasMenu by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    val iconTint = if (paperStyle.isDarkMode) Color.White else Color(0xFF1E1E24)

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onToggleFiles) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = if (filesOpen) "Hide files" else "Show files",
                    tint = if (filesOpen) BrnaColors.Accent else iconTint
                )
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tappable document title with unsaved dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onTitleTap() }
                ) {
                    Text(
                        text = documentTitle,
                        fontWeight = FontWeight.Bold,
                        color = if (paperStyle.isDarkMode) Color.White else Color(0xFF1E1E24)
                    )
                    if (isModified) {
                        Text(
                            text = " •",
                            fontWeight = FontWeight.Bold,
                            color = BrnaColors.Accent,
                            fontSize = 18.sp
                        )
                    }
                }

                // Zoom % — tappable to reset to 100%
                Text(
                    text = "  ${(zoomScale * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = if (zoomScale != 1f) BrnaColors.Accent else Color.Gray,
                    modifier = Modifier.clickable { onResetZoom() }
                )

                // Page grid indicator (paged mode only)
                if (currentPage != null) {
                    Text(
                        text = "  ·  $currentPage",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        actions = {
            // Finger drawing toggle — an input-mode setting, not a page/document
            // property, so it doesn't have a home in Page Settings the way
            // pattern/theme/orientation do.
            IconButton(onClick = onToggleFingerDrawing) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = if (allowFingerDrawing) "Finger Drawing: ON" else "Finger Drawing: OFF",
                    tint = if (allowFingerDrawing) Color(0xFFC3E88D) else Color.Gray
                )
            }

            // Desktop Rnote keeps this in its canvas menu, but being lost on a big
            // canvas is a one-handed emergency on a tablet — it stays one tap away.
            IconButton(onClick = onReturnToOrigin) {
                Icon(
                    imageVector = Icons.Default.FilterCenterFocus,
                    contentDescription = "Return to Origin",
                    tint = iconTint
                )
            }

            // Page overview: thumbnails of every page, tap one to go there.
            IconButton(onClick = onShowPages) {
                Icon(Icons.Default.GridView, contentDescription = "Pages", tint = iconTint)
            }

            // Page settings
            IconButton(onClick = onOpenPageSettings) {
                Icon(Icons.Default.Article, contentDescription = "Settings", tint = iconTint)
            }

            // Rnote's Focus Mode button, beside its canvas menu in the headerbar.
            IconButton(onClick = onToggleFocusMode) {
                Icon(
                    GeneratedIcons.FocusMode,
                    contentDescription = if (focusMode) "Focus Mode: ON" else "Focus Mode: OFF",
                    tint = if (focusMode) BrnaColors.Accent else iconTint
                )
            }

            // Share: to a chat, a mail or the class's course, through Android's share sheet.
            IconButton(onClick = { showShareMenu = true }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = iconTint)

                DropdownMenu(
                    expanded = showShareMenu,
                    onDismissRequest = { showShareMenu = false }
                ) {
                    val here = if (hasPages) "This page" else "What's on screen"
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Image, null) },
                        text = { Text("$here as PNG") },
                        onClick = { showShareMenu = false; onShare(ShareTarget.PAGE_PNG) }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                        text = { Text("$here as PDF") },
                        onClick = { showShareMenu = false; onShare(ShareTarget.PAGE_PDF) }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.HighlightAlt, null) },
                        text = { Text("Selection as PNG") },
                        enabled = hasSelection,
                        onClick = { showShareMenu = false; onShare(ShareTarget.SELECTION_PNG) }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        text = { Text("Whole note as PDF") },
                        onClick = { showShareMenu = false; onShare(ShareTarget.NOTE_PDF) }
                    )
                }
            }

            // Rnote's canvas menu: zoom, the pages of a Fixed Size document, Snap Positions.
            IconButton(onClick = { showCanvasMenu = true }) {
                Icon(GeneratedIcons.CanvasMenu, contentDescription = "Canvas Menu", tint = iconTint)

                DropdownMenu(
                    expanded = showCanvasMenu,
                    onDismissRequest = { showCanvasMenu = false }
                ) {
                    // Buttons that stay open, as Rnote's do: zoom until it is right.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, "Zoom out") }
                        TextButton(onClick = onResetZoom) { Text("${(zoomScale * 100).toInt()}%") }
                        IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, "Zoom in") }
                        IconButton(onClick = { showCanvasMenu = false; onZoomFitWidth() }) {
                            Icon(GeneratedIcons.ZoomFitWidth, "Zoom to Page Width")
                        }
                    }
                    HorizontalDivider()
                    // Always listed, as in Rnote, and only usable in the layout they are for.
                    DropdownMenuItem(
                        leadingIcon = { Icon(GeneratedIcons.AddPage, null) },
                        text = { Text("Add Page") },
                        trailingIcon = { KeyHint("Ctrl+Shift+A") },
                        enabled = isFixedSize,
                        onClick = onAddPage
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(GeneratedIcons.RemovePage, null) },
                        text = { Text("Remove Page") },
                        trailingIcon = { KeyHint("Ctrl+Shift+R") },
                        enabled = isFixedSize && canRemovePage,
                        onClick = onRemovePage
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(GeneratedIcons.ResizeToFitContent, null) },
                        text = { Text("Resize to Fit Content") },
                        enabled = isFixedSize,
                        onClick = onResizeToFitContent
                    )
                    if (!isFixedSize) {
                        Text(
                            "Page buttons: Fixed Size layout only",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    HorizontalDivider()
                    // A switch, as in Rnote's canvas menu: the menu stays open to show it flip.
                    DropdownMenuItem(
                        leadingIcon = { CheckMark(snapPositions) },
                        text = { Text("Snap Positions") },
                        trailingIcon = { KeyHint("Ctrl+Shift+P") },
                        onClick = onToggleSnapPositions
                    )
                    DropdownMenuItem(
                        leadingIcon = { CheckMark(respectBorders) },
                        text = { Text("Respect Borders When Pasting") },
                        onClick = onToggleRespectBorders
                    )
                    DropdownMenuItem(
                        leadingIcon = { CheckMark(penSounds) },
                        text = { Text("Pen Sounds") },
                        onClick = onTogglePenSounds
                    )
                    DropdownMenuItem(
                        leadingIcon = { CheckMark(blockPinchZoom) },
                        text = { Text("Block Pinch to Zoom") },
                        onClick = onToggleBlockPinchZoom
                    )
                }
            }

            // ⋮ Overflow — New, Open, Save, Export, Clear
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = iconTint)

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
                        text = { Text("New tab") },
                        trailingIcon = { KeyHint("Ctrl+T") },
                        onClick = { showOverflowMenu = false; onNewDocument() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        text = { Text("Open…") },
                        trailingIcon = { KeyHint("Ctrl+O") },
                        onClick = { showOverflowMenu = false; onOpenDocument() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.History, null) },
                        text = { Text("Recent…") },
                        onClick = { showOverflowMenu = false; onShowRecent() }
                    )
                    // Save writes straight back over the note's own file; picking a new
                    // name or folder is what Save As is for.
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Article, null) },
                        text = { Text(if (isModified) "Save  •" else "Save") },
                        trailingIcon = { KeyHint("Ctrl+S") },
                        onClick = { showOverflowMenu = false; onSaveDocument() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.SaveAs, null) },
                        text = { Text("Save As…") },
                        trailingIcon = { KeyHint("Ctrl+Shift+S") },
                        onClick = { showOverflowMenu = false; onSaveDocumentAs() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                        text = { Text("Import PDF…") },
                        onClick = { showOverflowMenu = false; onImportPdf() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Image, null) },
                        text = { Text("Insert image…") },
                        onClick = { showOverflowMenu = false; onInsertImage() }
                    )
                    if (canTakePhoto) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                            text = { Text("Take photo…") },
                            onClick = { showOverflowMenu = false; onTakePhoto() }
                        )
                    }
                    HorizontalDivider()
                    // In Rnote's app menu too; a switch that leaves the menu open to show it.
                    DropdownMenuItem(
                        leadingIcon = { CheckMark(fullscreen) },
                        text = { Text("Fullscreen") },
                        trailingIcon = { KeyHint("F11") },
                        onClick = onToggleFullscreen
                    )
                    HorizontalDivider()
                    // One entry, not one per format: scope and format are both chosen
                    // in the export sheet, the way desktop Rnote's export dialogs do it.
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.IosShare, null) },
                        text = { Text("Export…") },
                        onClick = { showOverflowMenu = false; onExport() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Print, null) },
                        text = { Text("Print…") },
                        trailingIcon = { KeyHint("Ctrl+P") },
                        onClick = { showOverflowMenu = false; onPrint() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = BrnaColors.DestructiveTint) },
                        text = { Text("Clear Canvas", color = BrnaColors.DestructiveTint) },
                        trailingIcon = { KeyHint("Ctrl+L") },
                        onClick = { showOverflowMenu = false; onClearCanvas() }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = paperStyle.currentBackgroundColor.copy(alpha = 0.95f)
        )
    )
}

/** A switch's state in a menu, ticked when it is on. */
@Composable
private fun CheckMark(on: Boolean) {
    Icon(
        if (on) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
        null,
        tint = if (on) BrnaColors.Accent else LocalContentColor.current
    )
}

/** A menu entry's keyboard shortcut, shown small beside it as Rnote's menus show theirs. */
@Composable
private fun KeyHint(keys: String) {
    Text(keys, fontSize = 12.sp, color = Color.Gray)
}
