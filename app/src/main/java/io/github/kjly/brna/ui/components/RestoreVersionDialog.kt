package io.github.kjly.brna.ui.components

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kjly.brna.storage.Backups

/**
 * "Restore Previous Version": the versions [Backups] kept of the open note's file, newest
 * first. Tapping one puts it on screen in place of the note, unsaved, until the next save
 * writes it back.
 */
@Composable
fun RestoreVersionDialog(
    /** Null while they are still being looked up. */
    versions: List<Backups.Version>?,
    onRestore: (Backups.Version) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Previous Version") },
        text = {
            Column {
                when {
                    versions == null -> Text("Looking…")
                    versions.isEmpty() -> Text(
                        "No earlier version of this file is kept yet. Before a save writes over " +
                            "a version made elsewhere — on the laptop, say — it is kept here " +
                            "for ${Backups.KEEP_MS / DateUtils.DAY_IN_MILLIS} days, the last " +
                            "${Backups.KEEP_PER_FILE} of them."
                    )
                    else -> {
                        Text(
                            "What the file held before it was saved over. The one picked opens unsaved, " +
                                "and the next save puts it back in the file.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                            items(versions, key = { it.file.name }) { version ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRestore(version) }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            DateUtils.formatDateTime(
                                                context,
                                                version.takenAt,
                                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                                                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
                                            )
                                        )
                                        Text(
                                            DateUtils.getRelativeTimeSpanString(version.takenAt).toString() +
                                                " · " + Formatter.formatShortFileSize(context, version.size),
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
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
