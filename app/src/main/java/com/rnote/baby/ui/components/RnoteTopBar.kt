package com.rnote.baby.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.ui.theme.BrnaColors

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
    onTitleTap: () -> Unit,
    onToggleFingerDrawing: () -> Unit,
    onSaveDocument: () -> Unit,
    onSaveDocumentAs: () -> Unit,
    onOpenDocument: () -> Unit,
    onNewDocument: () -> Unit,
    onExport: () -> Unit,
    onClearCanvas: () -> Unit,
    onOpenPageSettings: () -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val iconTint = if (paperStyle.isDarkMode) Color.White else Color(0xFF1E1E24)

    TopAppBar(
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

            // Page settings
            IconButton(onClick = onOpenPageSettings) {
                Icon(Icons.Default.Article, contentDescription = "Page Settings", tint = iconTint)
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
                        text = { Text("New") },
                        onClick = { showOverflowMenu = false; onNewDocument() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        text = { Text("Open…") },
                        onClick = { showOverflowMenu = false; onOpenDocument() }
                    )
                    // Save writes straight back over the note's own file; picking a new
                    // name or folder is what Save As is for.
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Article, null) },
                        text = { Text(if (isModified) "Save  •" else "Save") },
                        onClick = { showOverflowMenu = false; onSaveDocument() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.SaveAs, null) },
                        text = { Text("Save As…") },
                        onClick = { showOverflowMenu = false; onSaveDocumentAs() }
                    )
                    HorizontalDivider()
                    // One entry, not one per format: scope and format are both chosen
                    // in the export sheet, the way desktop Rnote's export dialogs do it.
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.IosShare, null) },
                        text = { Text("Export…") },
                        onClick = { showOverflowMenu = false; onExport() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = BrnaColors.DestructiveTint) },
                        text = { Text("Clear Canvas", color = BrnaColors.DestructiveTint) },
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
