package io.github.kjly.brna.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kjly.brna.storage.CustomFonts

/**
 * Fonts loaded from files, standing in for a family the tablet's own faces don't
 * cover — the closest an unrooted device gets to desktop Rnote's "pick any font
 * installed on this system" (Page Settings > Text). A font registered here under a
 * family name draws in place of that family wherever a text box asks for it, in this
 * note and any other.
 */
@Composable
fun FontManagerDialog(
    fonts: List<CustomFonts.Entry>,
    /**
     * Family names the open note's text boxes use that nothing here covers yet — a
     * nudge on what to add, not a requirement; left empty when there's no open note
     * or nothing is missing.
     */
    missingFamilies: List<String> = emptyList(),
    onPickFile: () -> Unit,
    onRemove: (CustomFonts.Entry) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fonts") },
        text = {
            Column {
                Text(
                    "Load a .ttf or .otf font file from this device and register it under " +
                        "the family name a note uses, so its text boxes draw in that font " +
                        "instead of falling back to the default face.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                if (missingFamilies.isNotEmpty()) {
                    Text(
                        "This note also asks for: ${missingFamilies.joinToString(", ")}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (fonts.isEmpty()) {
                    Text(
                        "No fonts loaded yet.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp).padding(top = 8.dp)) {
                        items(fonts, key = { it.fileName }) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FontDownload, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.family, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        entry.originalName,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onRemove(entry) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove ${entry.family}")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPickFile) { Text("Add font…") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * Asks which family a just-picked font file should stand in for, prefilled with its
 * file name (without extension) — the family a document names it by is what has to be
 * typed here, since Android can't be asked to read a font's own name table.
 */
@Composable
fun NameFontDialog(
    suggestedFamily: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var family by remember { mutableStateOf(suggestedFamily) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this font") },
        text = {
            Column {
                Text(
                    "Enter the font family exactly as the note uses it (e.g. the name shown " +
                        "in desktop Rnote's font picker). Any text box asking for this family " +
                        "will be drawn with the file just picked.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = family,
                    onValueChange = { family = it },
                    label = { Text("Family name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(family) }, enabled = family.isNotBlank()) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
