package io.github.kjly.brna.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.kjly.brna.model.BrushStyle
import io.github.kjly.brna.model.ConstraintRatio
import io.github.kjly.brna.model.PenPathBuilder
import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.RoughFillStyle
import io.github.kjly.brna.model.RoughStyle
import io.github.kjly.brna.model.ShaperStyle
import io.github.kjly.brna.model.TexturedDistribution
import io.github.kjly.brna.model.TexturedStyle
import io.github.kjly.brna.model.ShapeLine
import io.github.kjly.brna.model.ShapeLineCap
import io.github.kjly.brna.model.ShapeLineStyle
import io.github.kjly.brna.model.ShapeConstraints
import io.github.kjly.brna.model.TextAlignment
import io.github.kjly.brna.model.BrushSizePreset
import io.github.kjly.brna.model.EraserMode
import io.github.kjly.brna.model.PenFavorite
import io.github.kjly.brna.model.SelectorMode
import io.github.kjly.brna.model.TextToggle
import io.github.kjly.brna.model.brushFavorite
import io.github.kjly.brna.model.ToolConfig
import io.github.kjly.brna.model.ToolType
import io.github.kjly.brna.model.ToolsMode
import io.github.kjly.brna.ui.icons.GeneratedIcons
import io.github.kjly.brna.ui.theme.BrnaColors
import kotlinx.coroutines.launch

/**
 * Side floating strip matching desktop Rnote's RnPensSideBar: content swaps
 * by active pen (brushpage.ui / eraserpage.ui / selectorpage.ui / …). Unlike
 * the bottom/top bars this is a vertical column, since the strip docks to a
 * screen edge and is vertically centered.
 */
@Composable
fun PenConfigStrip(
    toolConfig: ToolConfig,
    hasActiveSelection: Boolean,
    onBrushStyleSelected: (BrushStyle) -> Unit,
    onSizeChanged: (Float) -> Unit,
    onDeleteSelection: () -> Unit,
    onDuplicateSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier,
    onShapeKindSelected: (io.github.kjly.brna.model.ShapeKind) -> Unit = {},
    onEraserModeSelected: (EraserMode) -> Unit = {},
    canPaste: Boolean = false,
    onCopySelection: () -> Unit = {},
    onCutSelection: () -> Unit = {},
    onPaste: () -> Unit = {},
    onLockAspectRatioToggled: () -> Unit = {},
    onSelectorModeSelected: (SelectorMode) -> Unit = {},
    onToolsModeSelected: (ToolsMode) -> Unit = {},
    onSnapAnglesToggled: () -> Unit = {},
    /** The Shaper's constraints changed: switched on or off, or a ratio added or taken away. */
    onShapeConstraintsChanged: (ShapeConstraints) -> Unit = {},
    /** The saved pens, [io.github.kjly.brna.storage.PenFavorites.SLOTS] of them; null for an empty slot. */
    favorites: List<PenFavorite?> = emptyList(),
    onApplyFavorite: (PenFavorite) -> Unit = {},
    /** Keep the brush as it is set now in this slot. */
    onStoreFavorite: (Int) -> Unit = {},
    onClearFavorite: (Int) -> Unit = {},
    /** The Typewriter's formatting switches that are on, and whether a text box is being typed into. */
    textFormats: Set<TextToggle> = emptySet(),
    textFormatsEnabled: Boolean = false,
    onToggleTextFormat: (TextToggle) -> Unit = {},
    /** The alignment of the text box being typed into, or else of the next one. */
    textAlignment: TextAlignment = TextAlignment.START,
    onTextAlignmentSelected: (TextAlignment) -> Unit = {},
    /** The Solid brush's pressure curve picked, as in Rnote's brush settings. */
    onPressureCurveSelected: (PressureCurve) -> Unit = {},
    /** The Shaper's line style or line cap picked, as in Rnote's shaper settings. */
    onShapeLineChanged: (ShapeLine) -> Unit = {},
    /** The Shaper's style picked — smooth or rough — and the rough style's fill and hachure angle. */
    onShaperStyleSelected: (ShaperStyle) -> Unit = {},
    onRoughFillSelected: (RoughFillStyle) -> Unit = {},
    onRoughHachureDegreesChanged: (Int) -> Unit = {},
    /** Rnote's "Invert Color Brightness of All Selected Strokes". */
    onInvertSelectionColors: () -> Unit = {},
    /** The Textured brush's dots: how many, and how they spread. */
    onTexturedDensityChanged: (Double) -> Unit = {},
    onTexturedDistributionSelected: (TexturedDistribution) -> Unit = {},
    /** Rnote's "Path Modelling" for the brush picked: simple or modeled. */
    onPenPathBuilderSelected: (PenPathBuilder) -> Unit = {}
) {
    Surface(
        modifier = modifier.width(60.dp),
        shape = RoundedCornerShape(24.dp),
        color = BrnaColors.PanelSurface.copy(alpha = BrnaColors.PanelSurfaceAlpha),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (toolConfig.activeTool) {
                ToolType.BRUSH -> BrushConfigPage(
                    toolConfig, onBrushStyleSelected, onPressureCurveSelected,
                    onTexturedDensityChanged, onTexturedDistributionSelected, onPenPathBuilderSelected, onSizeChanged,
                    favorites, onApplyFavorite, onStoreFavorite, onClearFavorite
                )
                ToolType.ERASER -> EraserConfigPage(toolConfig, onEraserModeSelected, onSizeChanged)
                ToolType.SELECTOR -> SelectorConfigPage(
                    toolConfig.selectorMode, onSelectorModeSelected,
                    hasActiveSelection, onDeleteSelection, onDuplicateSelection, onSelectAll, onDeselectAll,
                    onInvertSelectionColors, canPaste, onCopySelection, onCutSelection, onPaste,
                    toolConfig.lockAspectRatio, onLockAspectRatioToggled
                )
                ToolType.SHAPER -> ShaperConfigPage(
                    toolConfig, onShapeKindSelected, onSnapAnglesToggled, onShapeConstraintsChanged, onShapeLineChanged,
                    onShaperStyleSelected, onRoughFillSelected, onRoughHachureDegreesChanged, onSizeChanged
                )
                ToolType.TYPEWRITER -> TypewriterConfigPage(
                    toolConfig, onSizeChanged, textFormats, textFormatsEnabled, onToggleTextFormat,
                    textAlignment, onTextAlignmentSelected
                )
                ToolType.TOOLS -> ToolsConfigPage(toolConfig.toolsMode, onToolsModeSelected)
            }
        }
    }
}

// ── Brush ────────────────────────────────────────────────────────────────

@Composable
private fun BrushConfigPage(
    toolConfig: ToolConfig,
    onBrushStyleSelected: (BrushStyle) -> Unit,
    onPressureCurveSelected: (PressureCurve) -> Unit,
    onTexturedDensityChanged: (Double) -> Unit,
    onTexturedDistributionSelected: (TexturedDistribution) -> Unit,
    onPenPathBuilderSelected: (PenPathBuilder) -> Unit,
    onSizeChanged: (Float) -> Unit,
    favorites: List<PenFavorite?>,
    onApplyFavorite: (PenFavorite) -> Unit,
    onStoreFavorite: (Int) -> Unit,
    onClearFavorite: (Int) -> Unit
) {
    StripIconToggle(GeneratedIcons.BrushStyleSolid, "Solid", toolConfig.brushStyle == BrushStyle.SOLID, true) {
        onBrushStyleSelected(BrushStyle.SOLID)
    }
    StripIconToggle(Icons.Default.Highlight, "Marker", toolConfig.brushStyle == BrushStyle.MARKER, true) {
        onBrushStyleSelected(BrushStyle.MARKER)
    }
    StripIconToggle(GeneratedIcons.BrushStyleTextured, "Textured", toolConfig.brushStyle == BrushStyle.TEXTURED, true) {
        onBrushStyleSelected(BrushStyle.TEXTURED)
    }
    // Rnote offers the curve with the Solid style only; its marker keeps a constant width.
    if (toolConfig.brushStyle == BrushStyle.SOLID) {
        PressureCurveMenu(toolConfig.pressureCurve, onPressureCurveSelected)
    }
    if (toolConfig.brushStyle == BrushStyle.TEXTURED) {
        TexturedMenu(
            toolConfig.texturedDensity, toolConfig.texturedDistribution,
            onTexturedDensityChanged, onTexturedDistributionSelected
        )
    }
    PathModellingMenu(toolConfig.penPathBuilder, onPenPathBuilderSelected)
    StripDivider()
    val presets = BrushSizePreset.entries.map { it to it.sizeForTool(ToolType.BRUSH, toolConfig.brushStyle) }
    StrokeWidthPicker(toolConfig.currentActiveSize, presets, maxRange = if (toolConfig.brushStyle == BrushStyle.MARKER) 128f else 64f, onSizeChanged)
    if (favorites.isNotEmpty()) {
        StripDivider()
        FavoriteSlots(toolConfig.brushFavorite(), favorites, onApplyFavorite, onStoreFavorite, onClearFavorite)
    }
}

/**
 * The saved pens, each a dot in its colour and roughly its width. An empty slot saves the
 * brush as it is set now; a full one switches to it, or, held down, offers to replace
 * or remove it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteSlots(
    current: PenFavorite,
    favorites: List<PenFavorite?>,
    onApply: (PenFavorite) -> Unit,
    onStore: (Int) -> Unit,
    onClear: (Int) -> Unit
) {
    favorites.forEachIndexed { slot, favorite ->
        var showMenu by remember { mutableStateOf(false) }
        Box {
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (favorite != null && favorite.matches(current)) BrnaColors.PanelInactive else Color.Transparent
                    )
                    .combinedClickable(
                        onClickLabel = if (favorite == null) "Save the current pen here" else "Use this pen",
                        onLongClickLabel = "Replace or remove",
                        onClick = { if (favorite == null) onStore(slot) else onApply(favorite) },
                        onLongClick = { if (favorite != null) showMenu = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (favorite == null) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Empty favorite",
                        tint = BrnaColors.TextSecondaryOnPanel,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    // Ringed, so a black pen still shows on the dark panel.
                    val diameter = (6f + favorite.width.coerceAtMost(32f) / 32f * 16f).dp
                    Box(
                        modifier = Modifier
                            .size(diameter)
                            .clip(CircleShape)
                            .background(Color(favorite.argb))
                            .border(1.dp, BrnaColors.TextSecondaryOnPanel, CircleShape)
                    )
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Replace with current pen") },
                    onClick = {
                        showMenu = false
                        onStore(slot)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = {
                        showMenu = false
                        onClear(slot)
                    }
                )
            }
        }
    }
}

/** Rnote's path builders for the brush, under the names and with the explanations of its brush settings. */
private val PATH_BUILDERS = listOf(
    Triple(PenPathBuilder.SIMPLE, "Simple", "Produces line segments from the raw input."),
    Triple(PenPathBuilder.CURVED, "Curved", "Produces smooth, curved segments."),
    Triple(PenPathBuilder.MODELED, "Modeled", "Produces a modeled path with physics based algorithms. Results in the best looking handwriting.")
)

private fun pathBuilderIcon(builder: PenPathBuilder): ImageVector = when (builder) {
    PenPathBuilder.SIMPLE -> Icons.Default.Timeline
    PenPathBuilder.CURVED -> Icons.Default.Waves
    PenPathBuilder.MODELED -> Icons.Default.Gesture
}

/** Rnote's "Path Modelling": one button showing how strokes are built, a menu to pick. */
@Composable
private fun PathModellingMenu(builder: PenPathBuilder, onSelected: (PenPathBuilder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = PATH_BUILDERS.first { it.first == builder }.second
    Box {
        StripIconToggle(
            pathBuilderIcon(builder), "Path Modelling: $label",
            selected = builder != PenPathBuilder.MODELED, implemented = true
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading("Path Modelling")
            for ((each, eachLabel, explanation) in PATH_BUILDERS) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            pathBuilderIcon(each), null,
                            tint = if (each == builder) BrnaColors.Accent else LocalContentColor.current
                        )
                    },
                    text = {
                        Column(modifier = Modifier.width(260.dp)) {
                            Text(eachLabel)
                            Text(explanation, fontSize = 12.sp, color = Color.Gray)
                        }
                    },
                    onClick = {
                        open = false
                        onSelected(each)
                    }
                )
            }
        }
    }
}

/** Rnote's dot distributions, in the order and under the names of its brush settings. */
private val TEXTURED_DISTRIBUTIONS = listOf(
    TexturedDistribution.UNIFORM to "Uniform",
    TexturedDistribution.NORMAL to "Normal",
    TexturedDistribution.EXPONENTIAL to "Exponential",
    TexturedDistribution.REVERSE_EXPONENTIAL to "Reverse Exponential"
)

/**
 * Rnote's Textured Style settings: the density — dots per 10 × 10 — stepped up and down,
 * and how the dots spread across the stroke. The menu stays open while they change.
 */
@Composable
private fun TexturedMenu(
    density: Double,
    distribution: TexturedDistribution,
    onDensityChanged: (Double) -> Unit,
    onDistributionSelected: (TexturedDistribution) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        StripIconToggle(Icons.Default.Grain, "Texture", selected = true, implemented = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Density (dots per 10×10)",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { onDensityChanged(steppedDensity(density, -DENSITY_STEP)) }) { Text("−") }
                Text(String.format(java.util.Locale.ROOT, "%.1f", density), modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = { onDensityChanged(steppedDensity(density, DENSITY_STEP)) }) { Text("+") }
            }
            HorizontalDivider()
            Text(
                "Stroke Dots Position Distribution",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            for ((each, label) in TEXTURED_DISTRIBUTIONS) {
                DropdownMenuItem(
                    leadingIcon = { CheckMark(each == distribution) },
                    text = { Text(label) },
                    onClick = { onDistributionSelected(each) }
                )
            }
        }
    }
}

/** How far one press moves the density. */
private const val DENSITY_STEP = 0.5

/** [density] moved by [step], kept to Rnote's range and to one decimal, as its spin row shows it. */
private fun steppedDensity(density: Double, step: Double): Double =
    (Math.round((density + step) * 10.0) / 10.0).coerceIn(TexturedStyle.DENSITY_MIN, TexturedStyle.DENSITY_MAX)

/** Rnote's pressure curves, in the order and under the names of its brush settings. */
private val PRESSURE_CURVES = listOf(
    PressureCurve.CONST to "Constant",
    PressureCurve.LINEAR to "Linear",
    PressureCurve.SQRT to "Square root",
    PressureCurve.CBRT to "Cubic root",
    PressureCurve.POW2 to "Quadratic Parabola",
    PressureCurve.POW3 to "Cubic Parabola"
)

/**
 * Each curve drawn as its graph — the width a stroke gets, rising with the pressure of
 * the pen — so they tell apart at a glance; Rnote lists them by name alone.
 */
private val PRESSURE_CURVE_ICONS: Map<PressureCurve, ImageVector> by lazy {
    PressureCurve.entries.associateWith { curve ->
        ImageVector.Builder(
            name = "PressureCurve${curve.name}",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeAlpha = 0.45f, strokeLineWidth = 1f) {
                moveTo(1.5f, 1.5f)
                lineTo(1.5f, 14.5f)
                lineTo(14.5f, 14.5f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                for (i in 0..24) {
                    val pressure = i / 24f
                    val x = 3f + 11f * pressure
                    val y = 13f - 11f * curve.apply(1f, pressure)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
        }.build()
    }
}

/** The Solid brush's pressure curve: one button showing its graph, the six in its menu. */
@Composable
private fun PressureCurveMenu(curve: PressureCurve, onSelected: (PressureCurve) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = PRESSURE_CURVES.first { it.first == curve }.second
    Box {
        StripIconToggle(
            PRESSURE_CURVE_ICONS.getValue(curve), "Pressure Curve: $label",
            selected = curve != PressureCurve.LINEAR, implemented = true
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((each, eachLabel) in PRESSURE_CURVES) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            PRESSURE_CURVE_ICONS.getValue(each), null,
                            tint = if (each == curve) BrnaColors.Accent else LocalContentColor.current
                        )
                    },
                    text = { Text(eachLabel) },
                    onClick = {
                        open = false
                        onSelected(each)
                    }
                )
            }
        }
    }
}

// ── Shaper ───────────────────────────────────────────────────────────────

@Composable
private fun ShaperConfigPage(
    toolConfig: ToolConfig,
    onShapeKindSelected: (io.github.kjly.brna.model.ShapeKind) -> Unit,
    onSnapAnglesToggled: () -> Unit,
    onConstraintsChanged: (ShapeConstraints) -> Unit,
    onShapeLineChanged: (ShapeLine) -> Unit,
    onShaperStyleSelected: (ShaperStyle) -> Unit,
    onRoughFillSelected: (RoughFillStyle) -> Unit,
    onRoughHachureDegreesChanged: (Int) -> Unit,
    onSizeChanged: (Float) -> Unit
) {
    val kind = toolConfig.shapeKind
    StripIconToggle(Icons.Default.HorizontalRule, "Line", kind == io.github.kjly.brna.model.ShapeKind.LINE, true) {
        onShapeKindSelected(io.github.kjly.brna.model.ShapeKind.LINE)
    }
    StripIconToggle(Icons.Default.NorthEast, "Arrow", kind == io.github.kjly.brna.model.ShapeKind.ARROW, true) {
        onShapeKindSelected(io.github.kjly.brna.model.ShapeKind.ARROW)
    }
    StripIconToggle(Icons.Default.CropSquare, "Rectangle", kind == io.github.kjly.brna.model.ShapeKind.RECTANGLE, true) {
        onShapeKindSelected(io.github.kjly.brna.model.ShapeKind.RECTANGLE)
    }
    StripIconToggle(Icons.Default.RadioButtonUnchecked, "Ellipse", kind == io.github.kjly.brna.model.ShapeKind.ELLIPSE, true) {
        onShapeKindSelected(io.github.kjly.brna.model.ShapeKind.ELLIPSE)
    }
    LineShapesMenu(kind, onShapeKindSelected)
    CurveShapesMenu(kind, onShapeKindSelected)
    StripDivider()
    ConstraintsMenu(toolConfig.shapeConstraints, onConstraintsChanged)
    StripIconToggle(
        Icons.Default.Architecture, "Snap Lines to 15°", toolConfig.snapAngles, true, onClick = onSnapAnglesToggled
    )
    ShapeStyleMenu(toolConfig, onShaperStyleSelected, onShapeLineChanged, onRoughFillSelected, onRoughHachureDegreesChanged)
    StripDivider()
    // Rnote's shaper shares the brush's 2 / 6 / 12 width presets.
    val presets = BrushSizePreset.entries.map { it to it.brushSolidPx }
    StrokeWidthPicker(toolConfig.currentActiveSize, presets, maxRange = 64f, onSizeChanged)
}

/** The shapes Rnote builds from lines, in the order and under the names its shape menu uses. */
private val LINE_SHAPES = listOf(
    Triple(io.github.kjly.brna.model.ShapeKind.COORD_SYSTEM_2D, GeneratedIcons.ShapeCoordSystem2D, "Coordinate System"),
    Triple(io.github.kjly.brna.model.ShapeKind.COORD_SYSTEM_3D, GeneratedIcons.ShapeCoordSystem3D, "3D Coordinate System"),
    Triple(io.github.kjly.brna.model.ShapeKind.QUADRANT, GeneratedIcons.ShapeQuadrant, "Single Quadrant Coordinate System"),
    Triple(io.github.kjly.brna.model.ShapeKind.GRID, GeneratedIcons.ShapeGrid, "Grid")
)

/**
 * One button for the shapes built from lines, showing whichever of them is chosen, and a
 * menu to pick one: four more toggles would not fit the strip on a tablet held sideways.
 */
@Composable
private fun LineShapesMenu(
    kind: io.github.kjly.brna.model.ShapeKind,
    onShapeKindSelected: (io.github.kjly.brna.model.ShapeKind) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val chosen = LINE_SHAPES.firstOrNull { it.first == kind }
    Box {
        StripIconToggle(
            chosen?.second ?: GeneratedIcons.ShapeCoordSystem2D,
            chosen?.third ?: "Coordinate Systems and Grid",
            selected = chosen != null,
            implemented = true
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((shape, icon, label) in LINE_SHAPES) {
                DropdownMenuItem(
                    leadingIcon = { Icon(icon, null) },
                    text = { Text(if (shape == io.github.kjly.brna.model.ShapeKind.GRID) "$label (draw a cell, then drag it out)" else label) },
                    onClick = {
                        open = false
                        onShapeKindSelected(shape)
                    }
                )
            }
        }
    }
}

/**
 * The shapes Rnote builds from several strokes of the pen, with its icons and names, and
 * how each is drawn here: a polyline or polygon a corner per stroke, finished by putting
 * the pen down on the last corner again.
 */
private val CURVE_SHAPES = listOf(
    Triple(io.github.kjly.brna.model.ShapeKind.POLYLINE, GeneratedIcons.ShapePolyline, "Polyline (a corner per stroke; tap the last corner again to finish)"),
    Triple(io.github.kjly.brna.model.ShapeKind.POLYGON, GeneratedIcons.ShapePolygon, "Polygon (a corner per stroke; tap the last corner again to finish)"),
    Triple(io.github.kjly.brna.model.ShapeKind.QUADBEZ, GeneratedIcons.ShapeQuadBez, "Quadratic Curve (drag to the control point, then to the end)"),
    Triple(io.github.kjly.brna.model.ShapeKind.CUBBEZ, GeneratedIcons.ShapeCubBez, "Cubic Curve (drag to each control point, then to the end)"),
    Triple(io.github.kjly.brna.model.ShapeKind.FOCI_ELLIPSE, GeneratedIcons.ShapeFociEllipse, "Ellipse From Foci (both foci, then a point on it)")
)

/** One button for the shapes drawn in several strokes, as [LineShapesMenu] is for those built from lines. */
@Composable
private fun CurveShapesMenu(
    kind: io.github.kjly.brna.model.ShapeKind,
    onShapeKindSelected: (io.github.kjly.brna.model.ShapeKind) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val chosen = CURVE_SHAPES.firstOrNull { it.first == kind }
    Box {
        StripIconToggle(
            chosen?.second ?: GeneratedIcons.ShapePolygon,
            chosen?.third ?: "Polylines, Polygons and Curves",
            selected = chosen != null,
            implemented = true
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((shape, icon, label) in CURVE_SHAPES) {
                DropdownMenuItem(
                    leadingIcon = { Icon(icon, null, tint = if (shape == kind) BrnaColors.Accent else LocalContentColor.current) },
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onShapeKindSelected(shape)
                    }
                )
            }
        }
    }
}

/** Rnote's line styles and line caps, in the order and under the names of its shaper settings. */
private val LINE_STYLES = listOf(
    Triple(ShapeLineStyle.SOLID, GeneratedIcons.LineStyleSolid, "Solid"),
    Triple(ShapeLineStyle.DOTTED, GeneratedIcons.LineStyleDotted, "Dotted"),
    Triple(ShapeLineStyle.DASHED_NARROW, GeneratedIcons.LineStyleDashedNarrow, "Dashed (narrow)"),
    Triple(ShapeLineStyle.DASHED_EQUIDISTANT, GeneratedIcons.LineStyleDashedEquidistant, "Dashed (equidistant)"),
    Triple(ShapeLineStyle.DASHED_WIDE, GeneratedIcons.LineStyleDashedWide, "Dashed (wide)")
)
private val LINE_CAPS = listOf(
    Triple(ShapeLineCap.STRAIGHT, GeneratedIcons.LineCapStraight, "Straight"),
    Triple(ShapeLineCap.ROUNDED, GeneratedIcons.LineCapRounded, "Round")
)

/** Rnote's two shaper styles, under the names of its shaper settings. */
private val SHAPER_STYLES = listOf(
    Triple(ShaperStyle.SMOOTH, GeneratedIcons.ShaperStyleSmooth, "Smooth"),
    Triple(ShaperStyle.ROUGH, GeneratedIcons.ShaperStyleRough, "Rough")
)

/** Rnote's rough fill styles, in the order and under the names of its shaper settings. */
private val ROUGH_FILLS = listOf(
    RoughFillStyle.SOLID to "Solid",
    RoughFillStyle.HACHURE to "Hachure",
    RoughFillStyle.ZIG_ZAG to "Zig-Zag",
    RoughFillStyle.ZIG_ZAG_LINE to "Zig-Zag Line",
    RoughFillStyle.CROSSHATCH to "Crosshatch",
    RoughFillStyle.DOTS to "Dots",
    RoughFillStyle.DASHED to "Dashed"
)

/** How far one press turns the hachure angle: the step of Rnote's spin row. */
private const val HACHURE_STEP = 2

/**
 * Rnote's shaper settings, as one button and a menu: the style, then what that style
 * has — for smooth, line style and line cap (picking dotted rounds the cap, and a
 * straight cap makes a dotted line solid again, as in Rnote; see [ShapeLine]); for rough,
 * the fill style and the angle of its hatching. The fill itself shows only with a fill
 * colour picked, as in Rnote.
 */
@Composable
private fun ShapeStyleMenu(
    toolConfig: ToolConfig,
    onStyleSelected: (ShaperStyle) -> Unit,
    onLineChanged: (ShapeLine) -> Unit,
    onRoughFillSelected: (RoughFillStyle) -> Unit,
    onHachureDegreesChanged: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val line = toolConfig.shapeLine
    val rough = toolConfig.shaperStyle == ShaperStyle.ROUGH
    val (_, lineIcon, lineLabel) = LINE_STYLES.first { it.first == line.style }
    Box {
        if (rough) {
            StripIconToggle(GeneratedIcons.ShaperStyleRough, "Shaper Style: Rough", selected = true, implemented = true) { open = true }
        } else {
            StripIconToggle(lineIcon, "Line Style: $lineLabel", selected = line != ShapeLine(), implemented = true) { open = true }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading("Style")
            for ((style, styleIcon, styleLabel) in SHAPER_STYLES) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(styleIcon, null, tint = if (style == toolConfig.shaperStyle) BrnaColors.Accent else LocalContentColor.current)
                    },
                    text = { Text(styleLabel) },
                    // Stays open, so the style's own settings show below.
                    onClick = { onStyleSelected(style) }
                )
            }
            HorizontalDivider()
            if (rough) {
                MenuHeading("Fill Style")
                for ((fill, fillLabel) in ROUGH_FILLS) {
                    DropdownMenuItem(
                        leadingIcon = { CheckMark(fill == toolConfig.roughFill) },
                        text = { Text(fillLabel) },
                        onClick = { onRoughFillSelected(fill) }
                    )
                }
                HorizontalDivider()
                MenuHeading("Hachure Angle")
                val degrees = toolConfig.roughHachureDegrees
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { onHachureDegreesChanged(steppedHachure(degrees, -HACHURE_STEP)) }) { Text("−") }
                    Text("$degrees°", modifier = Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { onHachureDegreesChanged(steppedHachure(degrees, HACHURE_STEP)) }) { Text("+") }
                }
            } else {
                MenuHeading("Line Style")
                for ((style, styleIcon, styleLabel) in LINE_STYLES) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(styleIcon, null, tint = if (style == line.style) BrnaColors.Accent else LocalContentColor.current)
                        },
                        text = { Text(styleLabel) },
                        // Stays open, so the cap it may bring along shows below.
                        onClick = { onLineChanged(line.withStyle(style)) }
                    )
                }
                HorizontalDivider()
                MenuHeading("Line Cap")
                for ((cap, capIcon, capLabel) in LINE_CAPS) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(capIcon, null, tint = if (cap == line.cap) BrnaColors.Accent else LocalContentColor.current)
                        },
                        text = { Text(capLabel) },
                        onClick = { onLineChanged(line.withCap(cap)) }
                    )
                }
            }
        }
    }
}

/** [degrees] turned by [step], kept to Rnote's −180° to 180°. */
private fun steppedHachure(degrees: Int, step: Int): Int =
    (degrees + step).coerceIn(RoughStyle.HACHURE_DEGREES_MIN, RoughStyle.HACHURE_DEGREES_MAX)

@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Color.Gray,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/**
 * Rnote's shaper constraints: a switch — Ctrl held on a keyboard flips it for as long as
 * it is held — and the ratios to bend a drag to. Level and upright are always among them,
 * as Rnote keeps them; 1:1, 3:2 and the golden ratio can be picked, as in its menu.
 */
@Composable
private fun ConstraintsMenu(constraints: ShapeConstraints, onChanged: (ShapeConstraints) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        StripIconToggle(
            Icons.Default.AspectRatio, "Constraints", selected = constraints.enabled, implemented = true
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                leadingIcon = { CheckMark(constraints.enabled) },
                text = { Text("Enabled (hold Ctrl to switch while drawing)") },
                onClick = { onChanged(constraints.copy(enabled = !constraints.enabled)) }
            )
            HorizontalDivider()
            for ((ratio, label) in CONSTRAINT_RATIOS) {
                val on = ratio in constraints.ratios
                DropdownMenuItem(
                    leadingIcon = { CheckMark(on) },
                    text = { Text(label) },
                    onClick = {
                        onChanged(constraints.copy(ratios = if (on) constraints.ratios - ratio else constraints.ratios + ratio))
                    }
                )
            }
        }
    }
}

/** The ratios Rnote's constraint menu offers, under its names. */
private val CONSTRAINT_RATIOS = listOf(
    ConstraintRatio.ONE_TO_ONE to "1:1",
    ConstraintRatio.THREE_TO_TWO to "3:2",
    ConstraintRatio.GOLDEN to "Golden Ratio (1:1.618)"
)

@Composable
private fun CheckMark(on: Boolean) {
    Icon(
        if (on) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
        null,
        tint = if (on) BrnaColors.Accent else LocalContentColor.current
    )
}

// ── Eraser ───────────────────────────────────────────────────────────────

@Composable
private fun EraserConfigPage(
    toolConfig: ToolConfig,
    onEraserModeSelected: (EraserMode) -> Unit,
    onSizeChanged: (Float) -> Unit
) {
    StripIconToggle(GeneratedIcons.EraserTrash, "Trash Strokes", toolConfig.eraserMode == EraserMode.TRASH, true) {
        onEraserModeSelected(EraserMode.TRASH)
    }
    StripIconToggle(GeneratedIcons.EraserSplit, "Split Strokes", toolConfig.eraserMode == EraserMode.SPLIT, true) {
        onEraserModeSelected(EraserMode.SPLIT)
    }
    StripDivider()
    val presets = BrushSizePreset.entries.map { it to it.eraserPx }
    StrokeWidthPicker(
        toolConfig.currentActiveSize, presets, maxRange = 128f, onSizeChanged,
        previewStyle = StrokeWidthPreviewStyle.ROUNDED_RECT
    )
}

// ── Typewriter ───────────────────────────────────────────────────────────

@Composable
private fun TypewriterConfigPage(
    toolConfig: ToolConfig,
    onSizeChanged: (Float) -> Unit,
    formats: Set<TextToggle>,
    formatsEnabled: Boolean,
    onToggleFormat: (TextToggle) -> Unit,
    alignment: TextAlignment,
    onAlignmentSelected: (TextAlignment) -> Unit
) {
    Text(
        text = "Tap to\ntype",
        color = BrnaColors.TextSecondaryOnPanel,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    StripDivider()
    // Rnote's typewriter page: on the selection, or for what is typed next.
    for ((toggle, icon, label) in TEXT_FORMATS) {
        StripIconToggle(icon, label, toggle in formats, formatsEnabled) { onToggleFormat(toggle) }
    }
    AlignmentMenu(alignment, onAlignmentSelected)
    StripDivider()
    // Font size, not a stroke width: small, Rnote's default 32, and large.
    val presets = listOf(
        BrushSizePreset.SMALL to 20f,
        BrushSizePreset.MEDIUM to 32f,
        BrushSizePreset.LARGE to 48f
    )
    StrokeWidthPicker(toolConfig.currentActiveSize, presets, maxRange = 128f, onSizeChanged, title = "Font Size")
}

/** The typewriter's formatting switches, with their keyboard shortcuts where Rnote has one. */
private val TEXT_FORMATS = listOf(
    Triple(TextToggle.BOLD, Icons.Default.FormatBold, "Bold (Ctrl+B)"),
    Triple(TextToggle.ITALIC, Icons.Default.FormatItalic, "Italic (Ctrl+I)"),
    Triple(TextToggle.UNDERLINE, Icons.Default.FormatUnderlined, "Underline (Ctrl+U)"),
    Triple(TextToggle.STRIKETHROUGH, Icons.Default.FormatStrikethrough, "Strikethrough")
)

/** Rnote's typewriter alignment buttons, with its icons and tooltips. */
private val TEXT_ALIGNMENTS = listOf(
    Triple(TextAlignment.START, GeneratedIcons.TextAlignStart, "Align Left"),
    Triple(TextAlignment.CENTER, GeneratedIcons.TextAlignCenter, "Align Center"),
    Triple(TextAlignment.END, GeneratedIcons.TextAlignEnd, "Align Right"),
    Triple(TextAlignment.FILL, GeneratedIcons.TextAlignFill, "Fill")
)

/**
 * The alignment as one button showing the one in use, the four in its menu — Rnote shows
 * them side by side, which the strip has no room for. It aligns the box being typed into,
 * or the next one.
 */
@Composable
private fun AlignmentMenu(alignment: TextAlignment, onSelected: (TextAlignment) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val (_, icon, label) = TEXT_ALIGNMENTS.first { it.first == alignment }
    Box {
        StripIconToggle(icon, label, selected = alignment != TextAlignment.START, implemented = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((each, eachIcon, eachLabel) in TEXT_ALIGNMENTS) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(eachIcon, null, tint = if (each == alignment) BrnaColors.Accent else LocalContentColor.current)
                    },
                    text = { Text(eachLabel) },
                    onClick = {
                        open = false
                        onSelected(each)
                    }
                )
            }
        }
    }
}

// ── Selector ─────────────────────────────────────────────────────────────

@Composable
private fun SelectorConfigPage(
    mode: SelectorMode,
    onModeSelected: (SelectorMode) -> Unit,
    hasActiveSelection: Boolean,
    onDeleteSelection: () -> Unit,
    onDuplicateSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onInvertColors: () -> Unit,
    canPaste: Boolean,
    onCopySelection: () -> Unit,
    onCutSelection: () -> Unit,
    onPaste: () -> Unit,
    lockAspectRatio: Boolean,
    onLockAspectRatioToggled: () -> Unit
) {
    SelectorModeMenu(mode, onModeSelected)
    StripDivider()
    StripActionButton(GeneratedIcons.SelectionSelectAll, "Select All Strokes", enabled = true, onClick = onSelectAll)
    StripActionButton(GeneratedIcons.SelectionDeselectAll, "Deselect All Strokes", enabled = hasActiveSelection, onClick = onDeselectAll)
    StripActionButton(
        GeneratedIcons.SelectionInvertColor, "Invert Color Brightness of All Selected Strokes",
        enabled = hasActiveSelection, onClick = onInvertColors
    )
    StripActionButton(GeneratedIcons.SelectionDuplicate, "Duplicate Selection", enabled = hasActiveSelection, onClick = onDuplicateSelection)
    StripActionButton(GeneratedIcons.SelectionDelete, "Delete Selection", enabled = hasActiveSelection, tint = BrnaColors.DestructiveTint, onClick = onDeleteSelection)
    StripDivider()
    StripActionButton(Icons.Default.ContentCopy, "Copy", enabled = hasActiveSelection, onClick = onCopySelection)
    StripActionButton(Icons.Default.ContentCut, "Cut", enabled = hasActiveSelection, onClick = onCutSelection)
    StripActionButton(Icons.Default.ContentPaste, "Paste", enabled = canPaste, onClick = onPaste)
    StripDivider()
    StripIconToggle(GeneratedIcons.SelectionLockAspectRatio, "Lock Aspect Ratio", selected = lockAspectRatio, implemented = true, onClick = onLockAspectRatioToggled)
}

/** Rnote's four selector styles, with its icons and tooltips. */
private val SELECTOR_MODES = listOf(
    Triple(SelectorMode.POLYGON, GeneratedIcons.SelectorPolygon, "Select With a Polygon"),
    Triple(SelectorMode.RECTANGLE, GeneratedIcons.SelectorRectangle, "Select With a Rectangle"),
    Triple(SelectorMode.SINGLE, GeneratedIcons.SelectorSingle, "Select Single Strokes"),
    Triple(SelectorMode.INTERSECTING_PATH, GeneratedIcons.SelectorIntersectingPath, "Select Intersecting Path")
)

/**
 * The selector style as one button showing the style in use, the four to choose from
 * in its menu — Rnote shows them side by side, which the strip has no room for.
 */
@Composable
private fun SelectorModeMenu(mode: SelectorMode, onModeSelected: (SelectorMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val (_, icon, label) = SELECTOR_MODES.first { it.first == mode }
    Box {
        StripIconToggle(icon, label, selected = true, implemented = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((each, eachIcon, eachLabel) in SELECTOR_MODES) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(eachIcon, null, tint = if (each == mode) BrnaColors.Accent else LocalContentColor.current)
                    },
                    text = { Text(eachLabel) },
                    onClick = {
                        open = false
                        onModeSelected(each)
                    }
                )
            }
        }
    }
}

// ── Tools ────────────────────────────────────────────────────────────────

/**
 * Rnote's Tools pen, its four styles in its order: Vertical Space, the default, Offset
 * Camera (drag the page with the pen), Zoom (drag up to zoom in, down to zoom out) and
 * the Laser.
 */
@Composable
private fun ToolsConfigPage(mode: ToolsMode, onModeSelected: (ToolsMode) -> Unit) {
    StripIconToggle(Icons.Default.Height, "Vertical Space", selected = mode == ToolsMode.VERTICAL_SPACE, implemented = true) {
        onModeSelected(ToolsMode.VERTICAL_SPACE)
    }
    StripIconToggle(GeneratedIcons.ToolsOffsetCamera, "Offset Camera", selected = mode == ToolsMode.OFFSET_CAMERA, implemented = true) {
        onModeSelected(ToolsMode.OFFSET_CAMERA)
    }
    StripIconToggle(GeneratedIcons.ToolsZoom, "Zoom", selected = mode == ToolsMode.ZOOM, implemented = true) {
        onModeSelected(ToolsMode.ZOOM)
    }
    StripIconToggle(GeneratedIcons.ToolsLaser, "Laser", selected = mode == ToolsMode.LASER, implemented = true) {
        onModeSelected(ToolsMode.LASER)
    }
    StripDivider()
    Text(
        text = when (mode) {
            ToolsMode.VERTICAL_SPACE -> "Drag\ndown to\nmake\nroom"
            ToolsMode.OFFSET_CAMERA -> "Drag\nthe page"
            ToolsMode.ZOOM -> "Drag up\nto zoom\nin, down\nto zoom\nout"
            ToolsMode.LASER -> "Point;\nit fades\naway"
        },
        color = BrnaColors.TextSecondaryOnPanel,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

// ── Shared building blocks ──────────────────────────────────────────────

@Composable
private fun StripDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = BrnaColors.PanelInactive
    )
}

@Composable
private fun StripIconToggle(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    implemented: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = { if (implemented) onClick() },
        enabled = implemented,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected && implemented) BrnaColors.PanelInactive else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                !implemented -> BrnaColors.DisabledPenTint
                selected -> BrnaColors.Accent
                else -> BrnaColors.TextPrimaryOnPanel
            }
        )
    }
}

@Composable
private fun StripActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = BrnaColors.TextPrimaryOnPanel,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else BrnaColors.TextSecondaryOnPanel
        )
    }
}

/**
 * Desktop Rnote's `StrokeWidthPreviewStyle`. The brush pages preview a width as a dot;
 * `eraserpage.ui` sets `preview-style: rounded-rect`, because Rnote's eraser really is
 * an axis-aligned square (`EraserConfig::eraser_bounds` is an Aabb), so a round preview
 * would advertise the wrong shape.
 */
private enum class StrokeWidthPreviewStyle {
    CIRCLE,
    ROUNDED_RECT;

    /**
     * Rnote rounds its rect preview by a flat 3.0 against a widget whose largest square
     * is 32 across, so the radius is ~9% of the side. A flat radius here instead turned
     * the smallest preset back into a circle, since 2.dp of rounding on a 5.dp box leaves
     * almost no straight edge.
     */
    fun shapeFor(diameter: Dp): Shape = when (this) {
        CIRCLE -> CircleShape
        ROUNDED_RECT -> RoundedCornerShape(diameter * (3f / 32f))
    }
}

/**
 * Numeric chip (tap opens a scroll-wheel picker popup) then three size-preset
 * dots below. Earlier iterations tried an always-inline editor — a text field
 * (fiddly to type into on a narrow strip) and drag-to-scrub (worked, but the
 * user asked for a scrollable popup instead) — this replaces both.
 */
@Composable
private fun StrokeWidthPicker(
    currentSize: Float,
    presets: List<Pair<BrushSizePreset, Float>>,
    maxRange: Float,
    onSizeChanged: (Float) -> Unit,
    previewStyle: StrokeWidthPreviewStyle = StrokeWidthPreviewStyle.CIRCLE,
    title: String = "Stroke Size"
) {
    var showPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(bottom = 4.dp)
            .size(width = 36.dp, height = 24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BrnaColors.PanelInactive)
            .clickable { showPicker = true },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatSize(currentSize),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = BrnaColors.Accent,
            textAlign = TextAlign.Center
        )
    }

    presets.forEach { (preset, size) ->
        val isSelected = kotlin.math.abs(currentSize - size) < 0.5f
        // Each preset renders as a dot whose size previews the actual stroke width,
        // not a lettered chip — S/M/L are tap-target labels only.
        val dotDiameter = when (preset) {
            BrushSizePreset.SMALL -> 5.dp
            BrushSizePreset.MEDIUM -> 9.dp
            BrushSizePreset.LARGE -> 13.dp
        }
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isSelected) BrnaColors.PanelInactive else Color.Transparent)
                .clickable { onSizeChanged(size) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(dotDiameter)
                    .clip(previewStyle.shapeFor(dotDiameter))
                    .background(if (isSelected) BrnaColors.Accent else BrnaColors.TextPrimaryOnPanel)
            )
        }
    }

    if (showPicker) {
        WheelPickerDialog(
            title = title,
            currentValue = currentSize,
            maxRange = maxRange,
            onValueChange = onSizeChanged,
            onDismiss = { showPicker = false }
        )
    }
}

private fun formatSize(size: Float) = String.format("%.1f", size)

/**
 * Step size shrinks where fine control matters most (small strokes) and grows for large
 * ones, where a flat fine step would take forever to scroll through: 0.1 up to 12,
 * 0.5 up to 50, 1 up to 100, 2 up to 128 (each tier capped at [maxRange] if it's smaller).
 */
private fun tieredStrokeSizeValues(maxRange: Float): List<Float> {
    val tiers = listOf(12f to 0.1f, 50f to 0.5f, 100f to 1f, 128f to 2f)
    val values = mutableListOf<Float>()
    var segmentStart = 0f
    for ((tierUpTo, step) in tiers) {
        val segmentEnd = minOf(tierUpTo, maxRange)
        if (segmentEnd > segmentStart) {
            val steps = kotlin.math.round((segmentEnd - segmentStart) / step).toInt()
            for (i in 1..steps) {
                values.add(kotlin.math.round((segmentStart + i * step) * 100f) / 100f)
            }
        }
        segmentStart = segmentEnd
        if (segmentStart >= maxRange) break
    }
    return values
}

@Composable
private fun WheelPickerDialog(
    title: String,
    currentValue: Float,
    maxRange: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BrnaColors.PanelDialogSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = BrnaColors.TextPrimaryOnPanel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                WheelNumberPicker(
                    initialValue = currentValue,
                    maxRange = maxRange,
                    onValueChange = onValueChange
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("Done", color = BrnaColors.Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * A scroll wheel: drag/fling through a vertical list of values, the one that
 * settles behind the center highlight band is the selection. Reports the
 * centered value live as you scroll (not only once you stop), and animates
 * the list to rest exactly centered on a value once scrolling ends, rather
 * than relying on a snap-fling API that may not be available in this Compose
 * version.
 */
@Composable
private fun WheelNumberPicker(
    initialValue: Float,
    maxRange: Float,
    onValueChange: (Float) -> Unit
) {
    val values = remember(maxRange) { tieredStrokeSizeValues(maxRange) }
    val initialIndex = remember(initialValue, values) {
        values.indices.minByOrNull { kotlin.math.abs(values[it] - initialValue) } ?: 0
    }
    val itemHeight = 36.dp
    val visibleCount = 5
    val listState = rememberLazyListState(initialIndex)
    val coroutineScope = rememberCoroutineScope()

    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull {
                kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
            }?.index ?: initialIndex
        }
    }

    LaunchedEffect(centeredIndex) {
        values.getOrNull(centeredIndex)?.let(onValueChange)
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            listState.animateScrollToItem(centeredIndex)
        }
    }

    Box(
        modifier = Modifier
            .height(itemHeight * visibleCount)
            .width(100.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(BrnaColors.PanelInactive, RoundedCornerShape(8.dp))
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values) { index, v ->
                val isCentered = index == centeredIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            onValueChange(v)
                            coroutineScope.launch { listState.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatSize(v),
                        fontSize = if (isCentered) 18.sp else 13.sp,
                        fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCentered) BrnaColors.Accent else BrnaColors.TextSecondaryOnPanel
                    )
                }
            }
        }
    }
}
