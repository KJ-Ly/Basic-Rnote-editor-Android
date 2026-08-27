package com.rnote.baby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rnote.baby.model.BrushStyle
import com.rnote.baby.model.BrushSizePreset
import com.rnote.baby.model.ToolConfig
import com.rnote.baby.model.ToolType
import com.rnote.baby.ui.icons.GeneratedIcons
import com.rnote.baby.ui.theme.BrnaColors
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
    modifier: Modifier = Modifier
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
                ToolType.BRUSH -> BrushConfigPage(toolConfig, onBrushStyleSelected, onSizeChanged)
                ToolType.ERASER -> EraserConfigPage(toolConfig, onSizeChanged)
                ToolType.SELECTOR -> SelectorConfigPage(
                    hasActiveSelection, onDeleteSelection, onDuplicateSelection, onSelectAll, onDeselectAll
                )
                ToolType.SHAPER -> StubConfigPage("Shaper")
                ToolType.TYPEWRITER -> StubConfigPage("Typewriter")
                ToolType.TOOLS -> StubConfigPage("Tools")
            }
        }
    }
}

// ── Brush ────────────────────────────────────────────────────────────────

@Composable
private fun BrushConfigPage(
    toolConfig: ToolConfig,
    onBrushStyleSelected: (BrushStyle) -> Unit,
    onSizeChanged: (Float) -> Unit
) {
    StripIconToggle(GeneratedIcons.BrushStyleSolid, "Solid", toolConfig.brushStyle == BrushStyle.SOLID, true) {
        onBrushStyleSelected(BrushStyle.SOLID)
    }
    StripIconToggle(Icons.Default.Highlight, "Marker", toolConfig.brushStyle == BrushStyle.MARKER, true) {
        onBrushStyleSelected(BrushStyle.MARKER)
    }
    StripIconToggle(GeneratedIcons.BrushStyleTextured, "Textured (coming soon)", toolConfig.brushStyle == BrushStyle.TEXTURED, false) {
        onBrushStyleSelected(BrushStyle.TEXTURED)
    }
    StripDivider()
    val presets = BrushSizePreset.entries.map { it to it.sizeForTool(ToolType.BRUSH, toolConfig.brushStyle) }
    StrokeWidthPicker(toolConfig.currentActiveSize, presets, maxRange = if (toolConfig.brushStyle == BrushStyle.MARKER) 128f else 64f, onSizeChanged)
}

// ── Eraser ───────────────────────────────────────────────────────────────

@Composable
private fun EraserConfigPage(toolConfig: ToolConfig, onSizeChanged: (Float) -> Unit) {
    StripIconToggle(GeneratedIcons.EraserTrash, "Trash Strokes", selected = true, implemented = true) {}
    StripIconToggle(GeneratedIcons.EraserSplit, "Split Strokes (coming soon)", selected = false, implemented = false) {}
    StripDivider()
    val presets = BrushSizePreset.entries.map { it to it.eraserPx }
    StrokeWidthPicker(toolConfig.currentActiveSize, presets, maxRange = 128f, onSizeChanged)
}

// ── Selector ─────────────────────────────────────────────────────────────

@Composable
private fun SelectorConfigPage(
    hasActiveSelection: Boolean,
    onDeleteSelection: () -> Unit,
    onDuplicateSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    StripIconToggle(GeneratedIcons.SelectorPolygon, "Select With a Polygon", selected = true, implemented = true) {}
    StripDivider()
    StripActionButton(GeneratedIcons.SelectionSelectAll, "Select All Strokes", enabled = true, onClick = onSelectAll)
    StripActionButton(GeneratedIcons.SelectionDeselectAll, "Deselect All Strokes", enabled = hasActiveSelection, onClick = onDeselectAll)
    StripActionButton(GeneratedIcons.SelectionDuplicate, "Duplicate Selection", enabled = hasActiveSelection, onClick = onDuplicateSelection)
    StripActionButton(GeneratedIcons.SelectionDelete, "Delete Selection", enabled = hasActiveSelection, tint = BrnaColors.DestructiveTint, onClick = onDeleteSelection)
    StripDivider()
    StripIconToggle(GeneratedIcons.SelectionInvertColor, "Invert Color Brightness (coming soon)", selected = false, implemented = false) {}
    StripIconToggle(GeneratedIcons.SelectionLockAspectRatio, "Lock Aspect Ratio (coming soon)", selected = false, implemented = false) {}
}

// ── Stub pages (Shaper / Typewriter / Tools) ────────────────────────────

@Composable
private fun StubConfigPage(name: String) {
    Text(
        text = "$name\ncoming\nsoon",
        color = BrnaColors.TextSecondaryOnPanel,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        modifier = Modifier.padding(vertical = 8.dp)
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
    onSizeChanged: (Float) -> Unit
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
                    .clip(CircleShape)
                    .background(if (isSelected) BrnaColors.Accent else BrnaColors.TextPrimaryOnPanel)
            )
        }
    }

    if (showPicker) {
        WheelPickerDialog(
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
                    text = "Stroke Size",
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
