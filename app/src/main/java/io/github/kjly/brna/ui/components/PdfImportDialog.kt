package io.github.kjly.brna.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kjly.brna.storage.PdfImportPrefs
import io.github.kjly.brna.storage.PdfPageSpacing
import kotlin.math.roundToInt

/**
 * Rnote's "Import Pdf" dialog: what the file is, which of its pages, and its PDF import
 * preferences — Adjust Document, Page Width (%) and Page Spacing, the last two only when
 * the document is not adjusted, as there. [onImport] gets the choices and the pages, from
 * 0, first and last included.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfImportDialog(
    fileName: String,
    pageCount: Int,
    initialPrefs: PdfImportPrefs,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onImport: (PdfImportPrefs, Int, Int) -> Unit
) {
    val containerColor = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF5F5F5)
    val onSurface = if (isDark) Color.White else Color(0xFF1E1E24)
    val onSurfaceDim = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
    val accent = Color(0xFF82AAFF)
    val chipBg = if (isDark) Color(0xFF2E2E3E) else Color(0xFFE8ECF0)

    var prefs by remember { mutableStateOf(initialPrefs) }
    // Rnote's Start Page and End Page, from 1, the whole PDF to begin with.
    var start by remember { mutableIntStateOf(1) }
    var end by remember { mutableIntStateOf(pageCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = { Text("Import Pdf", fontWeight = FontWeight.Bold, color = onSurface) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Label("INFO", onSurfaceDim)
                Spacer(Modifier.height(4.dp))
                Text("File name:  $fileName", fontSize = 13.sp, color = onSurface)
                Text("Pages:  $pageCount", fontSize = 13.sp, color = onSurface)

                Spacer(Modifier.height(16.dp))
                Label("PDF IMPORT PREFERENCES", onSurfaceDim)
                PageRow("Start Page", start, 1..end, onSurface) { start = it }
                PageRow("End Page", end, start..pageCount, onSurface) { end = it }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Adjust Document", fontSize = 14.sp, color = onSurface)
                        Text("Whether the document layout should be adjusted to the Pdf", fontSize = 11.sp, color = onSurfaceDim)
                    }
                    Switch(
                        checked = prefs.adjustDocument,
                        onCheckedChange = { prefs = prefs.copy(adjustDocument = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = if (isDark) Color(0xFF888888) else Color(0xFFBBBBBB),
                            uncheckedTrackColor = if (isDark) Color(0xFF3A3A4A) else Color(0xFFDDE0E5)
                        )
                    )
                }

                // Only for pages that don't set the format themselves, as Rnote greys them out.
                val free = !prefs.adjustDocument
                val freeColor = if (free) onSurface else onSurface.copy(alpha = 0.35f)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Page Width (%)", fontSize = 14.sp, color = freeColor, modifier = Modifier.weight(1f))
                    Text("${prefs.pageWidthPercent} %", fontSize = 13.sp, color = if (free) onSurfaceDim else freeColor)
                }
                Slider(
                    value = prefs.pageWidthPercent.toFloat(),
                    onValueChange = { prefs = prefs.copy(pageWidthPercent = it.roundToInt().coerceIn(1, 100)) },
                    valueRange = 1f..100f,
                    enabled = free,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = onSurface.copy(alpha = 0.2f)
                    )
                )
                Text("Page Spacing", fontSize = 14.sp, color = freeColor)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfPageSpacing.entries.forEach { spacing ->
                        val selected = prefs.spacing == spacing
                        FilterChip(
                            selected = selected,
                            enabled = free,
                            onClick = { prefs = prefs.copy(spacing = spacing) },
                            label = {
                                Text(
                                    spacing.displayName,
                                    fontSize = 12.sp,
                                    color = when {
                                        !free -> onSurface.copy(alpha = 0.35f)
                                        selected -> Color(0xFF1A1A2E)
                                        else -> onSurface
                                    }
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                containerColor = chipBg
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(prefs, start - 1, end - 1) }) {
                Text("Import", color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = onSurfaceDim) }
        }
    )
}

@Composable
private fun Label(text: String, color: Color) {
    Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, letterSpacing = 1.sp)
}

/** A number of pages to step through, as Rnote's spin rows. */
@Composable
private fun PageRow(title: String, value: Int, range: IntRange, onSurface: Color, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(title, fontSize = 14.sp, color = onSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, contentDescription = "Fewer", tint = onSurface)
        }
        Text(
            "$value",
            fontSize = 15.sp,
            color = onSurface,
            modifier = Modifier.width(36.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, contentDescription = "More", tint = onSurface)
        }
    }
}
