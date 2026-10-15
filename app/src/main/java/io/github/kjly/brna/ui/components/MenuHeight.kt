package io.github.kjly.brna.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kjly.brna.model.MenuHeight

/**
 * The app's `DropdownMenu`: no taller than the screen, so all of it stays on screen and
 * scrolls to its last entry (#28). Every menu goes through here, so a new one can't miss it.
 */
@Composable
fun ScreenDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.heightIn(max = menuMaxHeight()),
        content = content
    )
}

/**
 * The tallest a dropdown menu may be. Read inside the composable that shows the menu: the
 * activity handles rotation itself instead of being recreated, so the height has to be
 * worked out again when the menu opens.
 */
@Composable
private fun menuMaxHeight(): Dp {
    // Read so that a rotation recomposes the caller with the new window size.
    LocalConfiguration.current
    val density = LocalDensity.current
    val bars = WindowInsets.systemBars
    return MenuHeight.maxDp(
        rootHeightPx = LocalView.current.height,
        topInsetPx = bars.getTop(density),
        bottomInsetPx = bars.getBottom(density),
        density = density.density
    ).dp
}
