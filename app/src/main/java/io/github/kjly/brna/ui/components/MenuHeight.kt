package io.github.kjly.brna.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kjly.brna.model.MenuHeight

/**
 * The tallest a dropdown menu may be so that all of it stays on screen and scrolls to its
 * last entry. Pass it as `Modifier.heightIn(max = menuMaxHeight())` to the `DropdownMenu`.
 *
 * Read it inside the composable that shows the menu: the activity handles rotation itself
 * instead of being recreated, so the height has to be worked out again when the menu opens.
 */
@Composable
fun menuMaxHeight(): Dp {
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
