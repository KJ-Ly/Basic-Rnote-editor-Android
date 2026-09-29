package io.github.kjly.brna.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kjly.brna.ui.theme.BrnaColors

/** One tab as the bar shows it. */
data class NoteTab(val id: Long, val title: String, val modified: Boolean)

/**
 * Desktop Rnote's tab bar, under the header bar: a tab per open note, the one on screen
 * highlighted, a dot on the ones with unsaved changes, a close button on each and a new
 * tab at the end. Like Rnote's, it only shows once more than one note is open.
 */
@Composable
fun NoteTabBar(
    tabs: List<NoteTab>,
    active: Long,
    background: Color,
    darkTheme: Boolean,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = if (darkTheme) Color.White else Color(0xFF1E1E24)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            for (tab in tabs) {
                val isActive = tab.id == active
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) ink.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable(onClickLabel = "Show") { onSelect(tab.id) }
                        .padding(start = 12.dp, end = 2.dp)
                ) {
                    Text(
                        tab.title,
                        color = if (isActive) ink else ink.copy(alpha = 0.7f),
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                    if (tab.modified) {
                        Text(" •", color = BrnaColors.Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close ${tab.title}",
                            tint = ink.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        IconButton(onClick = onNew) {
            Icon(Icons.Default.Add, contentDescription = "New tab", tint = ink)
        }
    }
}
