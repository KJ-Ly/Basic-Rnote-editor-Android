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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kjly.brna.export.ExportFormat
import io.github.kjly.brna.export.ExportPrefs
import io.github.kjly.brna.export.ExportScope
import io.github.kjly.brna.export.ImageExporter
import io.github.kjly.brna.export.PageRange
import io.github.kjly.brna.export.SplitOrder
import io.github.kjly.brna.model.PaperStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One dialog in place of desktop Rnote's three export dialogs: the scope row at the top
 * picks which of them you are in, and the options below are that scope's prefs (see
 * [ExportPrefs], which mirrors Rnote's three prefs structs).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(
    paperStyle: PaperStyle,
    prefs: ExportPrefs,
    pageCount: Int,
    hasSelection: Boolean,
    onPrefsChanged: (ExportPrefs) -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    val isDark = paperStyle.isDarkMode
    val containerColor = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF5F5F5)
    val onSurface = if (isDark) Color.White else Color(0xFF1E1E24)
    val onSurfaceDim = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
    val accent = Color(0xFF82AAFF)
    val chipBg = if (isDark) Color(0xFF2E2E3E) else Color(0xFFE8ECF0)
    val fieldBg = if (isDark) Color(0xFF2A2A3A) else Color(0xFFFFFFFF)

    val pagedRange = PageRange.parse(prefs.pageRange, pageCount)
    val rangeIsValid = pagedRange != null && (prefs.scope != ExportScope.PAGES || pagedRange.isNotEmpty())
    val canExport = when (prefs.scope) {
        ExportScope.DOCUMENT -> true
        ExportScope.PAGES -> pageCount > 0 && rangeIsValid
        ExportScope.SELECTION -> hasSelection
    }

    // Page order only changes anything once a document is cut into pages, which for a
    // single-file export means PDF and nothing else.
    val showPageOrder = pageCount > 1 &&
        (prefs.scope == ExportScope.PAGES || prefs.format == ExportFormat.PDF)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = { Text("Export", fontWeight = FontWeight.Bold, color = onSurface) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Scope ─────────────────────────────────────────────────────
                ExportLabel("WHAT TO EXPORT", onSurfaceDim)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportScope.entries.forEach { scope ->
                        val enabled = when (scope) {
                            ExportScope.DOCUMENT -> true
                            ExportScope.PAGES -> pageCount > 0
                            ExportScope.SELECTION -> hasSelection
                        }
                        ExportChip(
                            label = scope.displayName,
                            selected = prefs.scope == scope,
                            enabled = enabled,
                            onSurface = onSurface,
                            chipBg = chipBg,
                            accent = accent,
                            onClick = { onPrefsChanged(prefs.withScope(scope)) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (prefs.scope) {
                        ExportScope.DOCUMENT ->
                            if (pageCount > 1) "Everything, as one file ($pageCount pages)"
                            else "Everything, as one file"
                        ExportScope.PAGES -> "One file per page, into a folder you pick"
                        ExportScope.SELECTION -> "Only what the selector tool holds"
                    },
                    fontSize = 12.sp,
                    color = onSurfaceDim
                )

                Spacer(Modifier.height(16.dp))

                // ── Format ────────────────────────────────────────────────────
                ExportLabel("FORMAT", onSurfaceDim)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportPrefs.formatsFor(prefs.scope).forEach { format ->
                        ExportChip(
                            label = format.displayName,
                            selected = prefs.format == format,
                            enabled = true,
                            onSurface = onSurface,
                            chipBg = chipBg,
                            accent = accent,
                            onClick = { onPrefsChanged(prefs.copy(format = format)) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Rnote's three shared switches ─────────────────────────────
                ExportLabel("OPTIONS", onSurfaceDim)
                ExportSwitchRow(
                    label = "Background",
                    checked = prefs.withBackground,
                    onCheckedChange = { onPrefsChanged(prefs.copy(withBackground = it)) },
                    onSurface = onSurface, accent = accent, isDark = isDark
                )
                ExportSwitchRow(
                    label = "Pattern",
                    checked = prefs.withPattern,
                    onCheckedChange = { onPrefsChanged(prefs.copy(withPattern = it)) },
                    onSurface = onSurface, accent = accent, isDark = isDark
                )
                ExportSwitchRow(
                    label = "Optimize printer output",
                    checked = prefs.optimizePrinterOutput,
                    onCheckedChange = { onPrefsChanged(prefs.copy(optimizePrinterOutput = it)) },
                    onSurface = onSurface, accent = accent, isDark = isDark
                )

                // ── Page order ────────────────────────────────────────────────
                if (showPageOrder) {
                    Spacer(Modifier.height(12.dp))
                    ExportLabel("PAGE ORDER", onSurfaceDim)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SplitOrder.entries.forEach { order ->
                            ExportChip(
                                label = order.displayName,
                                selected = prefs.pageOrder == order,
                                enabled = true,
                                onSurface = onSurface,
                                chipBg = chipBg,
                                accent = accent,
                                onClick = { onPrefsChanged(prefs.copy(pageOrder = order)) }
                            )
                        }
                    }
                }

                // ── Which pages ───────────────────────────────────────────────
                if (prefs.scope == ExportScope.PAGES) {
                    Spacer(Modifier.height(12.dp))
                    ExportLabel("PAGES", onSurfaceDim)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = prefs.pageRange,
                        onValueChange = { onPrefsChanged(prefs.copy(pageRange = it)) },
                        singleLine = true,
                        isError = !rangeIsValid,
                        placeholder = {
                            Text("All $pageCount pages", fontSize = 13.sp, color = onSurfaceDim)
                        },
                        supportingText = {
                            Text(
                                text = if (!rangeIsValid) "Use numbers and ranges, e.g. 1-3, 5"
                                       else "Blank for every page, or e.g. 1-3, 5",
                                fontSize = 11.sp,
                                color = if (rangeIsValid) onSurfaceDim else Color(0xFFFF5370)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = onSurface.copy(alpha = 0.2f),
                            focusedTextColor = onSurface,
                            unfocusedTextColor = onSurface,
                            focusedContainerColor = fieldBg,
                            unfocusedContainerColor = fieldBg
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Bitmap options ────────────────────────────────────────────
                if (prefs.format.isBitmap) {
                    Spacer(Modifier.height(12.dp))
                    ExportSliderRow(
                        label = "Bitmap scale",
                        value = String.format(Locale.ROOT, "%.1f×", prefs.bitmapScaleFactor),
                        sliderValue = prefs.bitmapScaleFactor,
                        valueRange = ImageExporter.MIN_SCALE..ImageExporter.MAX_SCALE,
                        onValueChange = { onPrefsChanged(prefs.copy(bitmapScaleFactor = it)) },
                        onSurface = onSurface, onSurfaceDim = onSurfaceDim, accent = accent
                    )
                }
                if (prefs.format == ExportFormat.JPEG) {
                    ExportSliderRow(
                        label = "JPEG quality",
                        value = "${prefs.jpegQuality}",
                        sliderValue = prefs.jpegQuality.toFloat(),
                        valueRange = 1f..100f,
                        onValueChange = { onPrefsChanged(prefs.copy(jpegQuality = it.roundToInt())) },
                        onSurface = onSurface, onSurfaceDim = onSurfaceDim, accent = accent
                    )
                }

                // ── Selection margin ──────────────────────────────────────────
                if (prefs.scope == ExportScope.SELECTION) {
                    Spacer(Modifier.height(12.dp))
                    ExportSliderRow(
                        label = "Margin",
                        value = "${prefs.marginPx.roundToInt()} px",
                        sliderValue = prefs.marginPx,
                        valueRange = 0f..200f,
                        onValueChange = { onPrefsChanged(prefs.copy(marginPx = it)) },
                        onSurface = onSurface, onSurfaceDim = onSurfaceDim, accent = accent
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport, enabled = canExport) {
                Text(
                    text = if (prefs.scope == ExportScope.PAGES) "Choose folder…" else "Export",
                    color = if (canExport) accent else onSurfaceDim
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = onSurfaceDim) }
        }
    )
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
private fun ExportLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ExportChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSurface: Color,
    chipBg: Color,
    accent: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                color = when {
                    !enabled -> onSurface.copy(alpha = 0.35f)
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

@Composable
private fun ExportSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onSurface: Color,
    accent: Color,
    isDark: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Text(text = label, fontSize = 14.sp, color = onSurface, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = if (isDark) Color(0xFF888888) else Color(0xFFBBBBBB),
                uncheckedTrackColor = if (isDark) Color(0xFF3A3A4A) else Color(0xFFDDE0E5)
            )
        )
    }
}

@Composable
private fun ExportSliderRow(
    label: String,
    value: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onSurface: Color,
    onSurfaceDim: Color,
    accent: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 14.sp, color = onSurface, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 13.sp, color = onSurfaceDim)
    }
    Slider(
        value = sliderValue.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = onSurface.copy(alpha = 0.2f)
        )
    )
}
