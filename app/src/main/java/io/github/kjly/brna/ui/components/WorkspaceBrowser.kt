package io.github.kjly.brna.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kjly.brna.storage.FolderBrowser
import io.github.kjly.brna.storage.FolderListing
import io.github.kjly.brna.storage.Workspaces
import io.github.kjly.brna.ui.theme.BrnaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Desktop Rnote's workspace browser as a side panel: the workspaces along the top, and
 * the chosen one's folders, notes, PDFs and pictures below. A note opens, a PDF or a
 * picture goes into the open note, and holding an entry offers rename, duplicate and
 * delete, as Rnote's file rows do.
 */
@Composable
fun WorkspaceBrowser(
    workspaces: List<Workspaces.Workspace>,
    /** The uri of the workspace shown. */
    selected: String?,
    /** The notes open in tabs, marked in the list wherever they were opened from. */
    openDocuments: List<Uri>,
    /** Changed by the caller when the folder may have changed: a save, a new note. */
    refreshKey: Int,
    /**
     * The folders walked into below the workspace's own, as (document id, name). Kept by
     * the caller, so the panel opens again where it was left, as Rnote's does.
     */
    path: List<Pair<String, String>>,
    onPathChange: (List<Pair<String, String>>) -> Unit,
    onSelectWorkspace: (String) -> Unit,
    onAddWorkspace: () -> Unit,
    onEditWorkspace: (Workspaces.Workspace, String, Int) -> Unit,
    onRemoveWorkspace: (Workspaces.Workspace) -> Unit,
    /** A note to open, or a PDF or picture to put into the open note. */
    onOpen: (Uri, FolderListing.Kind) -> Unit,
    onNewNote: (tree: Uri, folderId: String, name: String) -> Unit,
    /** A note open in a tab was renamed here: its uri before, and its uri and name now. */
    onOpenRenamed: (Uri, Uri, String) -> Unit,
    /** A note open in a tab had its file deleted here. */
    onOpenDeleted: (Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun isOpen(uri: Uri) = openDocuments.any { FolderBrowser.sameDocument(uri, it) }
    val workspace = workspaces.firstOrNull { it.uri == selected } ?: workspaces.firstOrNull()
    val tree = workspace?.let { Uri.parse(it.uri) }

    // The workspace's own folder; path goes on from there.
    val rootId = remember(tree) { tree?.let { runCatching { FolderBrowser.rootId(it) }.getOrNull() } }
    var entries by remember { mutableStateOf<List<FolderListing.Entry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var localRefresh by remember { mutableIntStateOf(0) }
    val folderId = path.lastOrNull()?.first ?: rootId

    // A different folder starts empty; the same one, refreshed, keeps showing until the new list is in.
    var shownFolder by remember { mutableStateOf<Pair<Uri?, String?>?>(null) }
    LaunchedEffect(tree, folderId, refreshKey, localRefresh) {
        if (tree == null || folderId == null) return@LaunchedEffect
        if (shownFolder != tree to folderId) {
            entries = null
            shownFolder = tree to folderId
        }
        loading = true
        val listed = withContext(Dispatchers.IO) { FolderBrowser.list(context, tree, folderId) }
        if (listed == null && path.isNotEmpty()) {
            // A folder left open last time and gone since — moved, renamed, deleted: back to the top.
            loading = false
            onPathChange(emptyList())
            return@LaunchedEffect
        }
        entries = listed
        failed = listed == null
        loading = false
    }

    // Dialogs: a name to type, a delete to confirm, a workspace to edit.
    var naming by remember { mutableStateOf<Naming?>(null) }
    var deleting by remember { mutableStateOf<FolderListing.Entry?>(null) }
    var editing by remember { mutableStateOf<Workspaces.Workspace?>(null) }

    /** Runs a file action off the main thread, then lists the folder again. */
    fun act(action: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { action() }
            localRefresh++
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        color = BrnaColors.PanelSurface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)
            ) {
                Text(
                    "Workspaces",
                    color = BrnaColors.TextPrimaryOnPanel,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = BrnaColors.TextPrimaryOnPanel)
                }
            }

            WorkspaceChips(
                workspaces = workspaces,
                selected = workspace?.uri,
                onSelect = onSelectWorkspace,
                onEdit = { editing = it },
                onAdd = onAddWorkspace
            )

            if (tree == null) {
                Text(
                    "Add a folder as a workspace — a Google Drive folder works too. " +
                        "Its notes, PDFs and pictures then show here.",
                    color = BrnaColors.TextSecondaryOnPanel,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
                TextButton(onClick = onAddWorkspace, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Add a folder…", color = BrnaColors.Accent)
                }
            } else {
                // Where we are, and what can be made here.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    if (path.isNotEmpty()) {
                        IconButton(onClick = { onPathChange(path.dropLast(1)) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up", tint = BrnaColors.TextPrimaryOnPanel)
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        path.lastOrNull()?.second ?: workspace?.name.orEmpty(),
                        color = BrnaColors.TextPrimaryOnPanel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { naming = Naming.NewNote }, enabled = folderId != null) {
                        Icon(Icons.Default.NoteAdd, "New note here", tint = BrnaColors.TextPrimaryOnPanel)
                    }
                    IconButton(onClick = { naming = Naming.NewFolder }, enabled = folderId != null) {
                        Icon(Icons.Default.CreateNewFolder, "New folder", tint = BrnaColors.TextPrimaryOnPanel)
                    }
                    IconButton(onClick = { localRefresh++ }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = BrnaColors.TextPrimaryOnPanel)
                    }
                }
                HorizontalDivider(color = BrnaColors.PanelInactive)

                val listed = entries
                when {
                    failed -> {
                        Text(
                            "This folder can't be read any more — it may have been moved, or the app's access to it " +
                                "withdrawn. Add it again to keep using it.",
                            color = BrnaColors.TextSecondaryOnPanel,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                        TextButton(onClick = onAddWorkspace, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("Choose the folder again…", color = BrnaColors.Accent)
                        }
                    }
                    listed == null || (loading && listed.isEmpty()) -> Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = BrnaColors.Accent) }
                    listed.isEmpty() -> Text(
                        "Nothing here yet.",
                        color = BrnaColors.TextSecondaryOnPanel,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(listed, key = { it.id }) { entry ->
                            val uri = FolderBrowser.uriOf(tree, entry.id)
                            EntryRow(
                                entry = entry,
                                isOpen = isOpen(uri),
                                onClick = {
                                    if (entry.kind == FolderListing.Kind.FOLDER) {
                                        onPathChange(path + (entry.id to entry.name))
                                    } else {
                                        onOpen(uri, entry.kind)
                                    }
                                },
                                onRename = { naming = Naming.Rename(entry) },
                                onDuplicate = {
                                    val parent = folderId
                                    if (parent != null) {
                                        val siblings = listed.mapTo(mutableSetOf()) { it.name }
                                        act { FolderBrowser.duplicate(context, tree, parent, entry, siblings) }
                                    }
                                },
                                onDelete = { deleting = entry }
                            )
                        }
                    }
                }
            }
        }
    }

    naming?.let { what ->
        NameDialog(
            title = when (what) {
                Naming.NewNote -> "New note"
                Naming.NewFolder -> "New folder"
                is Naming.Rename -> "Rename"
            },
            initial = when (what) {
                Naming.NewNote -> "New Note"
                Naming.NewFolder -> "New Folder"
                // The name without its extension: a note stays a note.
                is Naming.Rename -> io.github.kjly.brna.storage.DocumentUri.titleFrom(what.entry.name)
                    .takeIf { what.entry.kind != FolderListing.Kind.FOLDER } ?: what.entry.name
            },
            onDismiss = { naming = null },
            onConfirm = { name ->
                naming = null
                val parent = folderId ?: return@NameDialog
                when (what) {
                    Naming.NewNote -> onNewNote(tree ?: return@NameDialog, parent, name)
                    Naming.NewFolder -> act { FolderBrowser.createFolder(context, tree ?: return@act, parent, name) }
                    is Naming.Rename -> {
                        val entry = what.entry
                        val newName = if (entry.kind == FolderListing.Kind.FOLDER) name.trim()
                            else FolderListing.renamed(entry.name, name)
                        val oldUri = FolderBrowser.uriOf(tree ?: return@NameDialog, entry.id)
                        val wasOpen = isOpen(oldUri)
                        scope.launch {
                            val renamed = withContext(Dispatchers.IO) { FolderBrowser.rename(context, oldUri, newName) }
                            if (renamed != null && wasOpen) onOpenRenamed(oldUri, renamed, newName)
                            localRefresh++
                        }
                    }
                }
            }
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${entry.name}”?") },
            text = {
                Text(
                    if (entry.kind == FolderListing.Kind.FOLDER) "The folder and everything in it will be deleted. " +
                        "This can't be undone from the app."
                    else "The file will be deleted. This can't be undone from the app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    val uri = FolderBrowser.uriOf(tree ?: return@TextButton, entry.id)
                    val wasOpen = isOpen(uri)
                    scope.launch {
                        val gone = withContext(Dispatchers.IO) { FolderBrowser.delete(context, uri) }
                        if (gone && wasOpen) onOpenDeleted(uri)
                        localRefresh++
                    }
                }) { Text("Delete", color = BrnaColors.DestructiveTint) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    editing?.let { ws ->
        WorkspaceDialog(
            workspace = ws,
            onDismiss = { editing = null },
            onSave = { name, color ->
                editing = null
                onEditWorkspace(ws, name, color)
            },
            onRemove = {
                editing = null
                onRemoveWorkspace(ws)
            }
        )
    }
}

/** What the name dialog is asking for. */
private sealed interface Naming {
    data object NewNote : Naming
    data object NewFolder : Naming
    data class Rename(val entry: FolderListing.Entry) : Naming
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceChips(
    workspaces: List<Workspaces.Workspace>,
    selected: String?,
    onSelect: (String) -> Unit,
    onEdit: (Workspaces.Workspace) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        for (ws in workspaces) {
            val isSelected = ws.uri == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) BrnaColors.PanelInactive else Color.Transparent)
                    .border(1.dp, Color(ws.color).copy(alpha = if (isSelected) 1f else 0.5f), RoundedCornerShape(16.dp))
                    // Held down, it opens the workspace's settings, as right-clicking one does in Rnote.
                    .combinedClickable(
                        onClickLabel = "Show",
                        onLongClickLabel = "Edit",
                        onClick = { onSelect(ws.uri) },
                        onLongClick = { onEdit(ws) }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(ws.color)))
                Spacer(Modifier.width(6.dp))
                Text(ws.name, color = BrnaColors.TextPrimaryOnPanel, fontSize = 13.sp, maxLines = 1)
            }
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, "Add a workspace", tint = BrnaColors.TextPrimaryOnPanel)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: FolderListing.Entry,
    isOpen: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val icon: ImageVector = when (entry.kind) {
        FolderListing.Kind.FOLDER -> Icons.Default.Folder
        FolderListing.Kind.NOTE -> Icons.Default.Description
        FolderListing.Kind.XOPP -> Icons.Default.Article
        FolderListing.Kind.PDF -> Icons.Default.PictureAsPdf
        FolderListing.Kind.IMAGE -> Icons.Default.Image
    }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isOpen) BrnaColors.PanelInactive else Color.Transparent)
                .combinedClickable(
                    onClickLabel = if (entry.kind == FolderListing.Kind.FOLDER) "Open folder" else "Open",
                    onLongClickLabel = "More",
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                icon, null,
                tint = if (isOpen) BrnaColors.Accent else BrnaColors.TextPrimaryOnPanel,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    color = if (isOpen) BrnaColors.Accent else BrnaColors.TextPrimaryOnPanel,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                entry.lastModified?.let { modified ->
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(modified)),
                        color = BrnaColors.TextSecondaryOnPanel,
                        fontSize = 11.sp
                    )
                }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
            if (entry.kind != FolderListing.Kind.FOLDER) {
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { showMenu = false; onDuplicate() })
            }
            DropdownMenuItem(
                text = { Text("Delete", color = BrnaColors.DestructiveTint) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A workspace's name and colour, as Rnote's workspace settings have them, and removing it from the list. */
@Composable
private fun WorkspaceDialog(
    workspace: Workspaces.Workspace,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    onRemove: () -> Unit
) {
    var name by remember { mutableStateOf(workspace.name) }
    var color by remember { mutableIntStateOf(workspace.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workspace") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val ring = MaterialTheme.colorScheme.onSurface
                    for (c in Workspaces.COLORS) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(if (c == color) Modifier.border(3.dp, ring, CircleShape) else Modifier)
                                .clickable { color = c }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Only the entry goes: the folder and its files stay where they are.
                TextButton(onClick = onRemove) {
                    Text("Remove from list", color = BrnaColors.DestructiveTint)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
