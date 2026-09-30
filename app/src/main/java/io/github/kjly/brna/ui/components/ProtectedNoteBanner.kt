package io.github.kjly.brna.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Under the header bar while the note on screen came from an Rnote newer than this app
 * knows the format of: that its file is never saved over, and a way to save a copy.
 */
@Composable
fun ProtectedNoteBanner(
    version: String,
    darkTheme: Boolean,
    onSaveCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (darkTheme) Color(0xFF4A3B12) else Color(0xFFFFF1C2)
    val ink = if (darkTheme) Color(0xFFFFE9A8) else Color(0xFF5C4300)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Protected — made with Rnote $version, newer than this app. Its file is never saved over; changes go to a copy.",
            color = ink,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSaveCopy) {
            Text("Save a copy", color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
