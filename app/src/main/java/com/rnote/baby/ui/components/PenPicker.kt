package com.rnote.baby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rnote.baby.model.ToolConfig
import com.rnote.baby.model.ToolType
import com.rnote.baby.ui.icons.CustomIcons
import com.rnote.baby.ui.icons.GeneratedIcons
import com.rnote.baby.ui.theme.BrnaColors

/**
 * Bottom-center floating bar matching desktop Rnote's penpicker.ui: a single
 * toggle group of the six pens (Brush, Shaper, Typewriter, Eraser, Selector,
 * Tools), a separator, then undo/redo. Deliberately carries no sizes or
 * colors — those live in [PenConfigStrip] and [ColorPicker] respectively,
 * matching where Rnote actually puts them.
 */
@Composable
fun PenPicker(
    toolConfig: ToolConfig,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolSelected: (ToolType) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PenToggleButton(ToolType.BRUSH, Icons.Default.Brush, "Brush", toolConfig, onToolSelected)
            PenToggleButton(ToolType.SHAPER, CustomIcons.Shaper, "Shaper", toolConfig, onToolSelected)
            PenToggleButton(ToolType.TYPEWRITER, CustomIcons.Typewriter, "Typewriter", toolConfig, onToolSelected)
            PenToggleButton(ToolType.ERASER, CustomIcons.Eraser, "Eraser", toolConfig, onToolSelected)
            PenToggleButton(ToolType.SELECTOR, CustomIcons.Selector, "Selector", toolConfig, onToolSelected)
            PenToggleButton(ToolType.TOOLS, CustomIcons.Tools, "Tools", toolConfig, onToolSelected)

            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .height(32.dp),
                color = BrnaColors.PanelInactive
            )

            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = GeneratedIcons.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) BrnaColors.TextPrimaryOnPanel else BrnaColors.TextSecondaryOnPanel
                )
            }
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = GeneratedIcons.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) BrnaColors.TextPrimaryOnPanel else BrnaColors.TextSecondaryOnPanel
                )
            }
        }
    }
}

/**
 * Desktop Rnote's pen icons stay a single neutral color always — only the
 * button's background changes for the active pen, via GTK's checked-toggle
 * style. Matching that here rather than giving each tool its own tint color,
 * which read as a very un-Rnote "rainbow toolbar" against the real app.
 */
@Composable
private fun PenToggleButton(
    tool: ToolType,
    icon: ImageVector,
    label: String,
    toolConfig: ToolConfig,
    onToolSelected: (ToolType) -> Unit
) {
    val isSelected = toolConfig.activeTool == tool
    val implemented = tool.isImplemented
    IconButton(
        onClick = { if (implemented) onToolSelected(tool) },
        enabled = implemented,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isSelected) BrnaColors.PanelInactive else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (implemented) label else "$label (coming soon)",
            tint = when {
                !implemented -> BrnaColors.DisabledPenTint
                isSelected -> BrnaColors.Accent
                else -> BrnaColors.TextPrimaryOnPanel
            }
        )
    }
}
