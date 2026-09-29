package io.github.kjly.brna.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import io.github.kjly.brna.storage.CustomColors
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
 * Top-center floating bar matching desktop Rnote's colorpicker.ui: the Stroke and
 * Fill color pads, quick-access palette swatches, and a button that opens the full
 * palette. The palette sets the color of whichever pad is active, as in Rnote; the
 * fill goes on shapes, and the palette's transparent swatch is "no fill". There is no
 * width control here — that lives in [PenConfigStrip], matching where Rnote puts it.
 */
@Composable
fun ColorPicker(
    activeColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    fillColor: Color = Color.Transparent,
    /** Whether the palette sets the fill rather than the stroke color. */
    fillPadActive: Boolean = false,
    onPadSelected: (fill: Boolean) -> Unit = {},
    onFillColorSelected: (Color) -> Unit = {}
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
                selected = !fillPadActive,
                enabled = true,
                onClick = { onPadSelected(false) }
            )
            ColorPad(
                icon = GeneratedIcons.FillColorPad,
                label = "Fill Color",
                swatchColor = fillColor,
                selected = fillPadActive,
                enabled = true,
                onClick = { onPadSelected(true) }
            )

            VerticalDivider(modifier = Modifier.height(32.dp), color = BrnaColors.PanelInactive)

            // The bar is wrap-content and sized to hold the palette in one line, so it
            // takes the un-wrapped form; the sheet, which is narrow, takes the other.
            PaletteQuickPicker(
                activeColor = if (fillPadActive) fillColor else activeColor,
                onColorSelected = if (fillPadActive) onFillColorSelected else onColorSelected,
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
    enabled: Boolean,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (selected) BrnaColors.PanelInactive else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
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
 * Below the grid is GTK's "Custom" row: "+" opens its editor on the color
 * chosen now, and the colors made there are kept, newest first. Holding one
 * opens it in the editor again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullPaletteDialog(
    activeColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pendingColor by remember { mutableStateOf(activeColor) }
    var customColors by remember { mutableStateOf(CustomColors.load(context)) }
    // GTK's editor, in place of the grid while a color is being made.
    var editing by remember { mutableStateOf(false) }

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
                        text = if (editing) "Custom Color" else "Pick a Color",
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

                if (editing) {
                    ColorEditor(color = pendingColor, onColorChange = { pendingColor = it })
                } else {
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

                    Spacer(Modifier.height(12.dp))
                    Text("Custom", color = BrnaColors.TextSecondaryOnPanel, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.dp, BrnaColors.TextSecondaryOnPanel, CircleShape)
                                .clickable(onClickLabel = "Custom color") { editing = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Custom color",
                                tint = BrnaColors.TextPrimaryOnPanel,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        for (argb in customColors) {
                            val color = Color(argb)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClickLabel = "Use this color",
                                        onLongClickLabel = "Customize",
                                        onClick = { pendingColor = color },
                                        onLongClick = {
                                            pendingColor = color
                                            editing = true
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CheckerboardPattern(Modifier.matchParentSize())
                                Box(Modifier.matchParentSize().background(color))
                                if (color == pendingColor) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (color.alpha < 0.5f || color.luminance() > 0.5f) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
                        onClick = {
                            if (editing) {
                                // A color made in the editor joins the Custom row, as in GTK.
                                customColors = CustomColors.added(customColors, pendingColor.toArgb())
                                CustomColors.save(context, customColors)
                            }
                            onColorSelected(pendingColor)
                        },
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

/**
 * GTK's color editor, which "+" in Rnote's "Pick a Color" opens: saturation and value in
 * a square, hue and opacity as sliders, and the color as hex to read — or to type, a
 * color copied from the laptop, say.
 */
@Composable
private fun ColorEditor(color: Color, onColorChange: (Color) -> Unit) {
    // Hue, saturation and value are kept here, not worked out from the color every time,
    // so dragging to black or grey doesn't lose the hue.
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(color.alpha) }
    var hexText by remember { mutableStateOf(CustomColors.toHex(color.toArgb())) }

    fun publish() {
        val made = Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
        hexText = CustomColors.toHex(made.toArgb())
        onColorChange(made)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SaturationValuePlane(
            hue, saturation, value,
            onChange = { s, v ->
                saturation = s
                value = v
                publish()
            },
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        HueSlider(
            hue,
            onChange = { h ->
                hue = h
                publish()
            },
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
        AlphaSlider(
            Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
            alpha,
            onChange = { a ->
                alpha = a
                publish()
            },
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape)) {
                CheckerboardPattern(Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(color))
            }
            OutlinedTextField(
                value = hexText,
                onValueChange = { text ->
                    hexText = text
                    CustomColors.parseHex(text)?.let { argb ->
                        android.graphics.Color.colorToHSV(argb, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                        val typed = Color(argb)
                        alpha = typed.alpha
                        onColorChange(typed)
                    }
                },
                singleLine = true,
                label = { Text("Hex") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BrnaColors.TextPrimaryOnPanel,
                    unfocusedTextColor = BrnaColors.TextPrimaryOnPanel
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Follows a finger or the pen across a control, from where it comes down until it lifts. */
private suspend fun PointerInputScope.follow(report: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        report(down.position)
        down.consume()
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            report(change.position)
            change.consume()
        }
    }
}

/** Saturation left to right, value bottom to top, for [hue]; a ring where the color is. */
@Composable
private fun SaturationValuePlane(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latest by rememberUpdatedState(onChange)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                follow { p ->
                    latest(
                        (p.x / size.width).coerceIn(0f, 1f),
                        1f - (p.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val at = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.Black, radius = 9.dp.toPx(), center = at, style = Stroke(width = 1.dp.toPx()))
        drawCircle(Color.White, radius = 7.5.dp.toPx(), center = at, style = Stroke(width = 2.dp.toPx()))
    }
}

/** The hues around the color wheel, red to red, with a mark on [hue]. */
@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(onChange)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(Unit) { follow { p -> latest((p.x / size.width).coerceIn(0f, 1f) * 360f) } }
    ) {
        drawRect(Brush.horizontalGradient(HUES))
        sliderMark(hue / 360f)
    }
}

/** Opacity, from see-through to [color] as it is, over a checkerboard, with a mark on [alpha]. */
@Composable
private fun AlphaSlider(color: Color, alpha: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(onChange)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(Unit) { follow { p -> latest((p.x / size.width).coerceIn(0f, 1f)) } }
    ) {
        val cell = 7.dp.toPx()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var column = 0
            var x = 0f
            while (x < size.width) {
                drawRect(
                    if ((row + column) % 2 == 0) Color.White else Color.LightGray,
                    topLeft = Offset(x, y),
                    size = Size(cell, cell)
                )
                x += cell
                column++
            }
            y += cell
            row++
        }
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 1f))))
        sliderMark(alpha)
    }
}

/** An upright mark across a slider at [fraction] of its width. */
private fun DrawScope.sliderMark(fraction: Float) {
    val x = fraction.coerceIn(0f, 1f) * size.width
    drawRect(Color.Black, topLeft = Offset(x - 2.5.dp.toPx(), 0f), size = Size(5.dp.toPx(), size.height))
    drawRect(Color.White, topLeft = Offset(x - 1.5.dp.toPx(), 0f), size = Size(3.dp.toPx(), size.height))
}

private val HUES = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

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
