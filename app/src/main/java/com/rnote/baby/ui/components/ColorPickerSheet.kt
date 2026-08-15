package com.rnote.baby.ui.components

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rnote.baby.model.ToolConfig
import com.rnote.baby.model.ToolType

val FullPalette = listOf(
    Color(0xFF82AAFF), Color(0xFFC3E88D), Color(0xFFFFCB6B), Color(0xFFF07178), Color(0xFFC792EA),
    Color(0xFF89DDFF), Color(0xFFF78C6C), Color(0xFFFF5370), Color(0xFFA6ACCD), Color(0xFF00C9A7),
    Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF475569), Color(0xFF0F172A),
    Color(0xFFFFB4A2), Color(0xFFE5989B), Color(0xFFB5838D), Color(0xFF6D6875), Color(0xFF355070)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    toolConfig: ToolConfig,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    // Determine active tool's current size and appropriate range
    val currentSize = toolConfig.currentActiveSize
    val maxSize = if (toolConfig.activeTool == ToolType.PEN) 48f else 128f
    val toolLabel = when (toolConfig.activeTool) {
        ToolType.PEN -> "Pen"
        ToolType.HIGHLIGHTER -> "Highlighter"
        ToolType.ERASER -> "Eraser"
        ToolType.SELECT -> "Pen"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF21232D)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Tool Options & Color Palette",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stroke Size Slider — uses active tool's width
            Text(
                text = "$toolLabel Width: ${currentSize.toInt()} px",
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Slider(
                value = currentSize,
                onValueChange = onStrokeWidthChanged,
                valueRange = 2f..maxSize,
                colors = SliderDefaults.colors(
                    thumbColor = toolConfig.penColor,
                    activeTrackColor = toolConfig.penColor
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Color Palette",
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(160.dp)
            ) {
                items(FullPalette) { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (toolConfig.penColor == color) {
                                    Modifier.border(3.dp, Color.White, CircleShape)
                                } else Modifier
                            )
                            .clickable {
                                onColorSelected(color)
                                onDismiss()
                            }
                    )
                }
            }
        }
    }
}
