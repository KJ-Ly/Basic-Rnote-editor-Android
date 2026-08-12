package com.rnote.baby.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rnote.baby.model.PaperStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RnoteTopBar(
    paperStyle: PaperStyle,
    zoomScale: Float,
    allowFingerDrawing: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isModified: Boolean,
    documentTitle: String,
    currentPage: String?,          // null in infinite mode; "col, row" in paged mode
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: () -> Unit,
    onTitleTap: () -> Unit,
    onToggleFingerDrawing: () -> Unit,
    onSaveDocument: () -> Unit,
    onOpenDocument: () -> Unit,
    onExportSvg: () -> Unit,
    onExportPng: () -> Unit,
    onClearCanvas: () -> Unit,
    onOpenPageSettings: () -> Unit,
    onToggleLandscape: () -> Unit,
    onTogglePaperPattern: () -> Unit,
    onToggleTheme: () -> Unit
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
                            color = Color(0xFF82AAFF),
                            fontSize = 18.sp
                        )
                    }
                }

                // Zoom % — tappable to reset to 100%
                Text(
                    text = "  ${(zoomScale * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = if (zoomScale != 1f) Color(0xFF82AAFF) else Color.Gray,
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
            // Undo
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) iconTint else Color.Gray
                )
            }

            // Redo
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) iconTint else Color.Gray
                )
            }

            // Finger drawing toggle
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

            // Landscape toggle (only in paged mode)
            if (!paperStyle.pageSize.isInfinite) {
                IconButton(onClick = onToggleLandscape) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = if (paperStyle.isLandscape) "Switch to Portrait" else "Switch to Landscape",
                        tint = if (paperStyle.isLandscape) Color(0xFF82AAFF) else iconTint
                    )
                }
            }

            // Paper pattern cycle
            IconButton(onClick = onTogglePaperPattern) {
                Icon(Icons.Default.GridOn, contentDescription = "Change Paper Pattern", tint = iconTint)
            }

            // Dark / light mode
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (paperStyle.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = if (paperStyle.isDarkMode) Color.Yellow else Color(0xFF1E1E24)
                )
            }

            // ⋮ Overflow — Save, Open, Export, Clear
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = iconTint)

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        text = { Text("Open…") },
                        onClick = { showOverflowMenu = false; onOpenDocument() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Article, null) },
                        text = { Text(if (isModified) "Save  •" else "Save") },
                        onClick = { showOverflowMenu = false; onSaveDocument() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.IosShare, null) },
                        text = { Text("Export SVG…") },
                        onClick = { showOverflowMenu = false; onExportSvg() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.IosShare, null) },
                        text = { Text("Export PNG…") },
                        onClick = { showOverflowMenu = false; onExportPng() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFF07178)) },
                        text = { Text("Clear Canvas", color = Color(0xFFF07178)) },
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
