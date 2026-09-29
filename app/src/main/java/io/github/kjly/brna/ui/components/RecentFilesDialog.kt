package io.github.kjly.brna.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kjly.brna.storage.RecentFiles

/**
 * The notes opened or saved last. Tapping one saves what is open now (as leaving for the
 * file picker does) and opens it in a tab of its own — or shows its tab, if it has one.
 */
@Composable
fun RecentFilesDialog(
    entries: List<RecentFiles.Entry>,
    /** The note open now, marked in the list. */
    currentUri: String?,
    onOpen: (RecentFiles.Entry) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent") },
        text = {
            Column {
                if (entries.isEmpty()) {
                    Text(
                        "Notes you open with Open… or save show up here. " +
                            "Files opened from another app are listed only until the app closes."
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(entries, key = { it.uri }) { entry ->
                            val isOpen = entry.uri == currentUri
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(entry) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        entry.title,
                                        fontWeight = if (isOpen) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (isOpen) "Open now" else DateUtils.getRelativeTimeSpanString(entry.openedAt).toString(),
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
