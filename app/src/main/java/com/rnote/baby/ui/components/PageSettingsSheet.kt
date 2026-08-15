package com.rnote.baby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rnote.baby.model.LayoutMode
import com.rnote.baby.model.MeasureUnit
import com.rnote.baby.model.PageSize
import com.rnote.baby.model.PaperPattern
import com.rnote.baby.model.PaperStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PageSettingsSheet(
    paperStyle: PaperStyle,
    onPaperStyleChanged: (PaperStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isDark = paperStyle.isDarkMode
    val containerColor = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF5F5F5)
    val onSurface = if (isDark) Color.White else Color(0xFF1E1E24)
    val onSurfaceDim = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
    val accent = Color(0xFF82AAFF)
    val chipBg = if (isDark) Color(0xFF2E2E3E) else Color(0xFFE8ECF0)
    val fieldBg = if (isDark) Color(0xFF2A2A3A) else Color(0xFFFFFFFF)

    var measureUnit by remember { mutableStateOf(MeasureUnit.PX) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // ═══════════════════════════════════════════════════════════════════
            //  SECTION 1: PAGE FORMAT
            // ═══════════════════════════════════════════════════════════════════

            SectionHeader("PAGE FORMAT", onSurface)

            Spacer(modifier = Modifier.height(12.dp))

            // ── Predefined Format ─────────────────────────────────────────────
            SubLabel("PREDEFINED SIZE", onSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageSize.entries.filter { it != PageSize.INFINITE }.forEach { size ->
                    val isSelected = paperStyle.pageSize == size
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onPaperStyleChanged(paperStyle.copy(pageSize = size))
                        },
                        label = {
                            Text(
                                text = size.displayName,
                                fontSize = 12.sp,
                                color = if (isSelected) Color(0xFF1A1A2E) else onSurface
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            containerColor = chipBg
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Orientation ───────────────────────────────────────────────────
            SubLabel("ORIENTATION", onSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val portraitSelected = !paperStyle.isLandscape
                IconButton(
                    onClick = { onPaperStyleChanged(paperStyle.copy(isLandscape = false)) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (portraitSelected) accent else chipBg,
                        contentColor = if (portraitSelected) Color(0xFF1A1A2E) else onSurface
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.CropPortrait, contentDescription = "Portrait")
                }
                IconButton(
                    onClick = { onPaperStyleChanged(paperStyle.copy(isLandscape = true)) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (!portraitSelected) accent else chipBg,
                        contentColor = if (!portraitSelected) Color(0xFF1A1A2E) else onSurface
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.CropLandscape, contentDescription = "Landscape")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Dimensions (Width × Height) ───────────────────────────────────
            SubLabel("DIMENSIONS", onSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))

            // Unit toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                MeasureUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = measureUnit == unit,
                        onClick = { measureUnit = unit },
                        label = { Text(unit.label, fontSize = 12.sp, color = if (measureUnit == unit) Color(0xFF1A1A2E) else onSurface) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            containerColor = chipBg
                        )
                    )
                }
            }

            val currentW = paperStyle.effectivePageWidthPx
            val currentH = paperStyle.effectivePageHeightPx
            val displayW = measureUnit.fromPx(currentW, paperStyle.dpi)
            val displayH = measureUnit.fromPx(currentH, paperStyle.dpi)
            val isCustom = paperStyle.pageSize == PageSize.CUSTOM

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DimensionField(
                    label = "Width",
                    value = displayW,
                    unit = measureUnit.label,
                    enabled = isCustom,
                    onSurface = onSurface,
                    fieldBg = fieldBg,
                    accent = accent,
                    onValueChange = { newVal ->
                        val px = measureUnit.toPx(newVal, paperStyle.dpi)
                        onPaperStyleChanged(paperStyle.copy(customWidthPx = px))
                    },
                    modifier = Modifier.weight(1f)
                )
                DimensionField(
                    label = "Height",
                    value = displayH,
                    unit = measureUnit.label,
                    enabled = isCustom,
                    onSurface = onSurface,
                    fieldBg = fieldBg,
                    accent = accent,
                    onValueChange = { newVal ->
                        val px = measureUnit.toPx(newVal, paperStyle.dpi)
                        onPaperStyleChanged(paperStyle.copy(customHeightPx = px))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── DPI ───────────────────────────────────────────────────────────
            SubLabel("DPI", onSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = paperStyle.dpi,
                    onValueChange = { onPaperStyleChanged(paperStyle.copy(dpi = it.roundToInt().toFloat())) },
                    valueRange = 24f..300f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = if (isDark) Color(0xFF3A3A4A) else Color(0xFFCDD5E0)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${paperStyle.dpi.roundToInt()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = if (isDark) Color(0xFF3A3A4A) else Color(0xFFDDE0E5))
            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════════════════════════════════
            //  SECTION 2: DOCUMENT
            // ═══════════════════════════════════════════════════════════════════

            SectionHeader("DOCUMENT", onSurface)
            Spacer(modifier = Modifier.height(12.dp))

            // ── Layout Mode ──────────────────────────────────────────────────
            SubLabel("LAYOUT", onSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LayoutMode.entries.forEach { mode ->
                    val isSelected = paperStyle.layoutMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPaperStyleChanged(paperStyle.copy(layoutMode = mode)) },
                        label = {
                            Text(
                                text = mode.displayName,
                                fontSize = 12.sp,
                                color = if (isSelected) Color(0xFF1A1A2E) else onSurface
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            containerColor = chipBg
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Background Color ──────────────────────────────────────────────
            SubLabel("BACKGROUND COLOR", onSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))

            val bgColor = paperStyle.currentBackgroundColor
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick-pick common bg colors
                val presetBgColors = listOf(
                    Color.White,
                    Color(0xFFF7F9FC),
                    Color(0xFFFFF8E1),  // warm cream
                    Color(0xFF1E1E24),  // dark
                    Color(0xFF2D2D3F),  // darker blue
                    Color(0xFF1A1A2E)   // deep navy
                )
                presetBgColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (bgColor == color) 2.5.dp else 1.dp,
                                color = if (bgColor == color) accent else onSurfaceDim.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable {
                                onPaperStyleChanged(
                                    paperStyle.copy(
                                        customBackgroundColor = color,
                                        isDarkMode = color.red < 0.5f && color.green < 0.5f && color.blue < 0.5f
                                    )
                                )
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Pattern Style ─────────────────────────────────────────────────
            SubLabel("PATTERN", onSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaperPattern.entries.forEach { pat ->
                    val isSelected = paperStyle.pattern == pat
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPaperStyleChanged(paperStyle.copy(pattern = pat)) },
                        label = {
                            Text(
                                text = pat.displayName,
                                fontSize = 12.sp,
                                color = if (isSelected) Color(0xFF1A1A2E) else onSurface
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            containerColor = chipBg
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Pattern Color ─────────────────────────────────────────────────
            if (paperStyle.pattern != PaperPattern.BLANK) {
                SubLabel("PATTERN COLOR", onSurfaceDim)
                Spacer(modifier = Modifier.height(8.dp))

                val gridColor = paperStyle.currentGridColor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val presetGridColors = listOf(
                        Color(0xFF383842),  // dark grid
                        Color(0xFFE2E8F0),  // light grid
                        Color(0xFFCCDAFF),  // soft blue
                        Color(0xFF82AAFF),  // accent blue
                        Color(0xFFDBA7A7),  // soft red
                        Color(0xFFA7DBB8)   // soft green
                    )
                    presetGridColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (gridColor == color) 2.5.dp else 1.dp,
                                    color = if (gridColor == color) accent else onSurfaceDim.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    onPaperStyleChanged(paperStyle.copy(customGridColor = color))
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Pattern Spacing ───────────────────────────────────────────
                SubLabel("PATTERN SPACING", onSurfaceDim)
                Spacer(modifier = Modifier.height(8.dp))

                val spacingW = paperStyle.gridSpacingPx
                val spacingH = paperStyle.patternHeightPx

                Text(
                    text = "Width: ${spacingW.roundToInt()} px",
                    fontSize = 12.sp,
                    color = accent
                )
                Slider(
                    value = spacingW,
                    onValueChange = { onPaperStyleChanged(paperStyle.copy(customGridSpacingPx = it)) },
                    valueRange = 8f..128f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = if (isDark) Color(0xFF3A3A4A) else Color(0xFFCDD5E0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Height: ${spacingH.roundToInt()} px",
                    fontSize = 12.sp,
                    color = accent
                )
                Slider(
                    value = spacingH,
                    onValueChange = { onPaperStyleChanged(paperStyle.copy(customPatternHeightPx = it)) },
                    valueRange = 8f..128f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = if (isDark) Color(0xFF3A3A4A) else Color(0xFFCDD5E0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = if (isDark) Color(0xFF3A3A4A) else Color(0xFFDDE0E5))
            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════════════════════
            //  SECTION 3: DISPLAY
            // ═══════════════════════════════════════════════════════════════════

            SectionHeader("DISPLAY", onSurface)
            Spacer(modifier = Modifier.height(12.dp))

            // ── Show Format Borders ──────────────────────────────────────────
            SettingsSwitchRow(
                label = "Show format borders",
                checked = paperStyle.showFormatBorders,
                onCheckedChange = { onPaperStyleChanged(paperStyle.copy(showFormatBorders = it)) },
                onSurface = onSurface,
                accent = accent,
                isDark = isDark
            )

            // ── Show Origin Indicator ────────────────────────────────────────
            SettingsSwitchRow(
                label = "Show origin indicator",
                checked = paperStyle.showOriginIndicator,
                onCheckedChange = { onPaperStyleChanged(paperStyle.copy(showOriginIndicator = it)) },
                onSurface = onSurface,
                accent = accent,
                isDark = isDark
            )

            // ── Dark Mode ────────────────────────────────────────────────────
            SettingsSwitchRow(
                label = "Dark mode",
                checked = paperStyle.isDarkMode,
                onCheckedChange = {
                    onPaperStyleChanged(
                        paperStyle.copy(
                            isDarkMode = it,
                            customBackgroundColor = null,
                            customGridColor = null
                        )
                    )
                },
                onSurface = onSurface,
                accent = accent,
                isDark = isDark
            )
        }
    }
}

// ── Helper Composables ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun SubLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        letterSpacing = 1.sp
    )
}

@Composable
private fun DimensionField(
    label: String,
    value: Float,
    unit: String,
    enabled: Boolean,
    onSurface: Color,
    fieldBg: Color,
    accent: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) {
        mutableStateOf(
            if (value == value.roundToInt().toFloat()) value.roundToInt().toString()
            else String.format("%.1f", value)
        )
    }

    Column(modifier = modifier) {
        Text(text = label, fontSize = 12.sp, color = onSurface.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = textValue,
            onValueChange = { newText ->
                textValue = newText
                newText.toFloatOrNull()?.let { onValueChange(it) }
            },
            enabled = enabled,
            singleLine = true,
            suffix = { Text(unit, fontSize = 12.sp, color = onSurface.copy(alpha = 0.5f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = onSurface.copy(alpha = 0.2f),
                disabledBorderColor = onSurface.copy(alpha = 0.1f),
                focusedTextColor = onSurface,
                unfocusedTextColor = onSurface,
                disabledTextColor = onSurface.copy(alpha = 0.5f),
                focusedContainerColor = fieldBg,
                unfocusedContainerColor = fieldBg,
                disabledContainerColor = fieldBg.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsSwitchRow(
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
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = if (isDark) Color(0xFF888888) else Color(0xFFBBBBBB),
                uncheckedTrackColor = if (isDark) Color(0xFF3A3A4A) else Color(0xFFDDE0E5)
            )
        )
    }
}
