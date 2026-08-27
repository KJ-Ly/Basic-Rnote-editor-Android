package com.rnote.baby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rnote.baby.ui.icons.GeneratedIcons
import com.rnote.baby.ui.theme.BrnaColors

/**
 * Top-center floating bar matching desktop Rnote's colorpicker.ui: a
 * Stroke/Fill color-pad toggle (Fill has a UI slot but no strokes model
 * support yet — see UI_REDESIGN.md), quick-access palette swatches, and a
 * button that opens the full palette. There is no width control here —
 * that lives in [PenConfigStrip], matching where Rnote puts it.
 */
@Composable
fun ColorPicker(
    activeColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFullPalette by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = BrnaColors.PanelSurface.copy(alpha = BrnaColors.PanelSurfaceAlpha),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorPad(
                icon = GeneratedIcons.StrokeColorPad,
                label = "Stroke Color",
                swatchColor = activeColor,
                selected = true,
                enabled = true
            )
            ColorPad(
                icon = GeneratedIcons.FillColorPad,
                label = "Fill Color (coming soon)",
                swatchColor = Color.Transparent,
                selected = false,
                enabled = false
            )

            VerticalDivider(modifier = Modifier.height(32.dp), color = BrnaColors.PanelInactive)

            BrnaColors.PenPalette.forEach { color ->
                ColorSwatch(
                    color = color,
                    selected = activeColor == color,
                    onClick = { onColorSelected(color) }
                )
            }

            IconButton(onClick = { showFullPalette = true }, modifier = Modifier.size(32.dp)) {
                Icon(GeneratedIcons.MoreColors, contentDescription = "More Colors", tint = BrnaColors.TextPrimaryOnPanel)
            }
        }
    }

    if (showFullPalette) {
        FullPaletteDialog(
            activeColor = activeColor,
            onColorSelected = { onColorSelected(it); showFullPalette = false },
            onDismiss = { showFullPalette = false }
        )
    }
}

@Composable
private fun ColorPad(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    swatchColor: Color,
    selected: Boolean,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (selected) BrnaColors.PanelInactive else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (!enabled) BrnaColors.DisabledPenTint else if (swatchColor != Color.Transparent) swatchColor else BrnaColors.TextPrimaryOnPanel,
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(if (color == Color.Transparent) Modifier else Modifier.background(color))
            .then(
                if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        if (color == Color.Transparent) CheckerboardPattern(Modifier.matchParentSize())
    }
}

/** Renders a simple checkerboard to represent a transparent color swatch. */
@Composable
private fun CheckerboardPattern(modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(CircleShape)) {
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.LightGray))
        }
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.LightGray))
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
        }
    }
}

@Composable
private fun FullPaletteDialog(
    activeColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BrnaColors.PanelDialogSurface,
        title = { Text("Color Palette", color = BrnaColors.TextPrimaryOnPanel) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(BrnaColors.FullPalette) { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .then(if (color == Color.Transparent) Modifier else Modifier.background(color))
                            .then(
                                if (color == activeColor) Modifier.border(3.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { onColorSelected(color) }
                    ) {
                        if (color == Color.Transparent) CheckerboardPattern(Modifier.matchParentSize())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = BrnaColors.Accent)
            }
        }
    )
}