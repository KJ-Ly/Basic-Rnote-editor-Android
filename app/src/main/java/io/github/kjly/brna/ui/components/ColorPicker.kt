package io.github.kjly.brna.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kjly.brna.ui.icons.GeneratedIcons
import io.github.kjly.brna.ui.theme.BrnaColors

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

            // The bar is wrap-content and sized to hold the palette in one line, so it
            // takes the un-wrapped form; the sheet, which is narrow, takes the other.
            PaletteQuickPicker(
                activeColor = activeColor,
                onColorSelected = onColorSelected,
                wrap = false
            )
        }
    }
}

/**
 * The palette every color in this app is picked from: the quick swatches, plus the
 * button that opens the full GTK grid the stroke picker uses. Page Settings picks its
 * background and pattern colors from the same one — desktop Rnote hands all three to
 * the same GTK color chooser, and a page whose background can only be one of six greys
 * chosen here would be a house palette that exists nowhere else in the app.
 *
 * [wrap] lays the swatches out in a [FlowRow] for a container too narrow to hold them
 * in one line; the floating color bar is built around a single row and passes false.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteQuickPicker(
    activeColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = true,
    swatchSize: Dp = 24.dp,
    selectionRing: Color = Color.White,
    moreColorsTint: Color = BrnaColors.TextPrimaryOnPanel
) {
    var showFullPalette by remember { mutableStateOf(false) }

    val swatches: @Composable () -> Unit = {
        BrnaColors.PenPalette.forEach { color ->
            ColorSwatch(
                color = color,
                selected = activeColor == color,
                size = swatchSize,
                ringColor = selectionRing,
                onClick = { onColorSelected(color) }
            )
        }
        IconButton(
            onClick = { showFullPalette = true },
            modifier = Modifier.size(swatchSize + 8.dp)
        ) {
            Icon(
                GeneratedIcons.MoreColors,
                contentDescription = "More Colors",
                tint = moreColorsTint
            )
        }
    }

    if (wrap) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { swatches() }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { swatches() }
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
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    size: Dp = 24.dp,
    ringColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (color == Color.Transparent) Modifier else Modifier.background(color))
            .then(
                if (selected) Modifier.border(2.dp, ringColor, CircleShape)
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

/**
 * Mirrors desktop Rnote's "Pick a Color" dialog, which is GTK's
 * ColorChooserWidget: a 9x5 grid of the GNOME palette laid out as nine
 * contiguous hue strips shading light to dark, a check mark on the current
 * color, and Cancel/Select buttons — so nothing is applied until Select.
 * (GTK's "Custom" row is deliberately not implemented yet.)
 */
@Composable
private fun FullPaletteDialog(
    activeColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingColor by remember { mutableStateOf(activeColor) }

    // The platform default dialog width is far too narrow for nine columns.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(16.dp),
            color = BrnaColors.PanelDialogSurface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(40.dp))
                    Text(
                        text = "Pick a Color",
                        color = BrnaColors.TextPrimaryOnPanel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BrnaColors.TextSecondaryOnPanel)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BrnaColors.PaletteColumns.forEach { shades ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            shades.forEach { color ->
                                PaletteSwatch(
                                    color = color,
                                    selected = color == pendingColor,
                                    onClick = { pendingColor = color }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = BrnaColors.TextPrimaryOnPanel)
                    }
                    Button(
                        onClick = { onColorSelected(pendingColor) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrnaColors.Accent,
                            contentColor = BrnaColors.AccentOnLight
                        )
                    ) {
                        Text("Select", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                // GTK picks the check's color for contrast against the swatch; so do we.
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
