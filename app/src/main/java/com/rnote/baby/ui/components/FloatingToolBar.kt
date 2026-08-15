package com.rnote.baby.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rnote.baby.model.BrushSizePreset
import com.rnote.baby.model.ToolConfig
import com.rnote.baby.model.ToolType
import kotlin.math.abs

// Rnote Palette Swatches
val RnotePalette = listOf(
    Color(0xFF82AAFF), // Rnote Blue
    Color(0xFFC3E88D), // Pastel Green
    Color(0xFFFFCB6B), // Warm Yellow
    Color(0xFFF07178), // Coral Red
    Color(0xFFC792EA), // Lavender Purple
    Color(0xFFFFFFFF), // Pure White
    Color(0xFF292D3E)  // Charcoal Black
)

@Composable
fun FloatingToolBar(
    toolConfig: ToolConfig,
    onToolSelected: (ToolType) -> Unit,
    onColorSelected: (Color) -> Unit,
    onOpenColorPicker: () -> Unit,
    onSizeChanged: (Float) -> Unit = {},
    hasActiveSelection: Boolean = false,
    onDeleteSelection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSizeDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF282A36).copy(alpha = 0.92f), // Glassmorphism Dark Floating Bar
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Delete Selection — shown only when lasso has captured strokes
            if (hasActiveSelection) {
                IconButton(
                    onClick = onDeleteSelection,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0x33FF5370),
                        contentColor = Color(0xFFFF5370)
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Selection")
                }
            }

            // Pen Button
            ToolIconButton(
                icon = Icons.Default.Create,
                contentDescription = "Pen Tool",
                isSelected = toolConfig.activeTool == ToolType.PEN,
                selectedColor = toolConfig.penColor,
                onClick = { onToolSelected(ToolType.PEN) }
            )

            // Highlighter Button
            ToolIconButton(
                icon = Icons.Default.Highlight,
                contentDescription = "Highlighter",
                isSelected = toolConfig.activeTool == ToolType.HIGHLIGHTER,
                selectedColor = toolConfig.highlighterColor,
                onClick = { onToolSelected(ToolType.HIGHLIGHTER) }
            )

            // Eraser Button
            ToolIconButton(
                icon = Icons.Default.AutoFixHigh,
                contentDescription = "Eraser",
                isSelected = toolConfig.activeTool == ToolType.ERASER,
                selectedColor = Color.LightGray,
                onClick = { onToolSelected(ToolType.ERASER) }
            )

            // Lasso Selection Tool
            ToolIconButton(
                icon = Icons.Default.Gesture,
                contentDescription = "Lasso Selection Tool",
                isSelected = toolConfig.activeTool == ToolType.SELECT,
                selectedColor = Color(0xFFC792EA),
                onClick = { onToolSelected(ToolType.SELECT) }
            )

            // ── Brush Size Controls (Shown for Pen, Highlighter, and Eraser) ─────
            AnimatedVisibility(visible = toolConfig.activeTool != ToolType.SELECT) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    val currentSize = toolConfig.currentActiveSize

                    // 3 Default Presets (S / M / L)
                    BrushSizePreset.entries.forEach { preset ->
                        val presetSize = preset.sizeForTool(toolConfig.activeTool)
                        val isSelected = abs(currentSize - presetSize) < 0.5f

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF82AAFF) else Color(0xFF383A4A))
                                .clickable { onSizeChanged(presetSize) }
                        ) {
                            Text(
                                text = preset.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF1A1A2E) else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Numerical Stroke Size Indicator Chip (Click to open numeric adjuster)
                    Surface(
                        onClick = { showSizeDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF383A4A),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LineWeight,
                                contentDescription = "Adjust stroke width",
                                tint = Color(0xFF82AAFF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${String.format("%.1f", currentSize)} px",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ── Color Swatches (Shown when Pen or Highlighter is active) ────────
            AnimatedVisibility(visible = toolConfig.activeTool == ToolType.PEN || toolConfig.activeTool == ToolType.HIGHLIGHTER) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    RnotePalette.take(5).forEach { color ->
                        ColorSwatch(
                            color = color,
                            isSelected = toolConfig.penColor == color,
                            onClick = { onColorSelected(color) }
                        )
                    }

                    // Open Full Picker
                    IconButton(
                        onClick = onOpenColorPicker,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatColorFill,
                            contentDescription = "Color Picker",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // ── Numerical Brush Size Adjuster Dialog ──────────────────────────────────
    if (showSizeDialog) {
        NumericalBrushSizeDialog(
            toolConfig = toolConfig,
            onSizeChanged = onSizeChanged,
            onDismiss = { showSizeDialog = false }
        )
    }
}

@Composable
private fun NumericalBrushSizeDialog(
    toolConfig: ToolConfig,
    onSizeChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var sizeInput by remember { mutableStateOf(String.format("%.1f", toolConfig.currentActiveSize)) }
    val maxRange = if (toolConfig.activeTool == ToolType.PEN) 64f else 128f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E24),
        title = {
            Text(
                text = "${toolConfig.activeTool.name.lowercase().replaceFirstChar { it.uppercase() }} Size Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = "STROKE SIZE PRESETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrushSizePreset.entries.forEach { preset ->
                        val pSize = preset.sizeForTool(toolConfig.activeTool)
                        val isSel = abs(toolConfig.currentActiveSize - pSize) < 0.5f
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                onSizeChanged(pSize)
                                sizeInput = String.format("%.1f", pSize)
                            },
                            label = { Text("${preset.label} (${pSize.toInt()} px)", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF82AAFF),
                                selectedLabelColor = Color(0xFF1A1A2E),
                                containerColor = Color(0xFF282A36),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "NUMERICAL ADJUSTMENT (PX)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newS = (toolConfig.currentActiveSize - 1.0f).coerceAtLeast(1.0f)
                            onSizeChanged(newS)
                            sizeInput = String.format("%.1f", newS)
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF282A36))
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                    }

                    OutlinedTextField(
                        value = sizeInput,
                        onValueChange = { input ->
                            sizeInput = input
                            input.toFloatOrNull()?.let { v ->
                                onSizeChanged(v.coerceIn(1f, 128f))
                            }
                        },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF82AAFF),
                            unfocusedBorderColor = Color(0xFF383A4A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    IconButton(
                        onClick = {
                            val newS = (toolConfig.currentActiveSize + 1.0f).coerceAtMost(maxRange)
                            onSizeChanged(newS)
                            sizeInput = String.format("%.1f", newS)
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF282A36))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = toolConfig.currentActiveSize,
                    onValueChange = {
                        onSizeChanged(it)
                        sizeInput = String.format("%.1f", it)
                    },
                    valueRange = 1f..maxRange,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF82AAFF),
                        activeTrackColor = Color(0xFF82AAFF),
                        inactiveTrackColor = Color(0xFF383A4A)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF82AAFF), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ToolIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF383A4A) else Color.Transparent
    val tint = if (isSelected) selectedColor else Color.Gray

    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = backgroundColor),
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color.White, CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick)
    )
}
