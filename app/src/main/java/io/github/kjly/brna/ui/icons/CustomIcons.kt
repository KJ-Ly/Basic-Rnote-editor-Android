package io.github.kjly.brna.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The six pen icons (Brush, Shaper, Typewriter, Eraser, Selector, Tools),
 * vendored directly from desktop Rnote's own symbolic icon set rather than
 * approximated with Material Icons, for an exact visual match in [PenPicker].
 *
 * Source: https://github.com/flxzt/rnote,
 * crates/rnote-ui/data/icons/scalable/actions/pen-*-symbolic.svg (GPL-3.0).
 * Path data transcribed as-is from the original SVGs' `d` attributes via
 * [addPathNodes]. See THIRD_PARTY_NOTICES.md and LICENSE at the repo root —
 * this repository is GPL-3.0 in its entirety so that including this material
 * is fully compliant.
 *
 * All source SVGs share a 16x16 viewBox, hence viewportWidth/Height = 16f below.
 */
object CustomIcons {

    private fun icon(name: String, vararg pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            pathData.forEach { d ->
                addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
            }
        }.build()

    val Brush: ImageVector by lazy {
        icon(
            "PenBrush",
            "m 9 11 c 0 2.210938 -1.789062 4.011719 -4 4 h -4 v -4 c 0 -2.210938 1.789062 -4 4 -4 s 4 1.789062 4 4 z m 0 0",
            "m 14.40625 0.0507812 c -0.386719 0.0078126 -0.757812 0.1718748 -1.03125 0.4492188 l -5.800781 5.773438 c 0.90625 0.476562 1.644531 1.214843 2.121093 2.121093 l 5.800782 -5.769531 c 0.980468 -0.957031 0.277344 -2.6171875 -1.089844 -2.5742188 z m 0 0"
        )
    }

    val Shaper: ImageVector by lazy {
        icon(
            "PenShaper",
            "m 5.191406 1.296875 c -0.390625 -0.390625 -1.023437 -0.390625 -1.414062 0 l -2.5 2.5 c -0.390625 0.390625 -0.390625 1.023437 0 1.414063 l 2.5 2.5 c 0.390625 0.390624 1.023437 0.390624 1.414062 0 l 2.496094 -2.5 c 0.390625 -0.390626 0.390625 -1.023438 0 -1.414063 z m 0 0",
            "m 9.984375 12.003906 c 0 1.65625 -1.34375 3 -3 3 c -1.660156 0 -3 -1.34375 -3 -3 s 1.339844 -3 3 -3 c 1.65625 0 3 1.34375 3 3 z m 0 0",
            "m 11.929688 2.007812 c -0.339844 0.015626 -0.644532 0.203126 -0.8125 0.496094 l -2.320313 4 c -0.386719 0.664063 0.09375 1.5 0.863281 1.5 h 4.644532 c 0.769531 0 1.25 -0.835937 0.863281 -1.5 l -2.320313 -4 c -0.1875 -0.328125 -0.542968 -0.519531 -0.917968 -0.496094 z m 0 0"
        )
    }

    val Typewriter: ImageVector by lazy {
        icon(
            "PenTypewriter",
            "M 4.21875 2 L 1 14 L 3 14 L 3.835938 11 L 8.164062 11 L 9 14 L 11 14 L 7.78125 2 Z M 5.78125 4 L 6.21875 4 L 7.609375 9 L 4.390625 9 Z M 5.78125 4 ",
            "M 11 0 L 11 1 L 12 2 L 12 14 L 11 15 L 11 16 L 15 16 L 15 15 L 14 14 L 14 2 L 15 1 L 15 0 Z M 11 0 "
        )
    }

    /** A rotated eraser block — pen-eraser-symbolic.svg's silhouette. */
    val Eraser: ImageVector by lazy {
        icon(
            "PenEraser",
            "m 13.949219 8.359375 s 0 0.019531 0.003906 0.011719 l -5.003906 4.914062 l 0.699219 -0.285156 h -4.621094 l 0.707031 0.292969 l -3.683594 -3.683594 v -0.019531 l 6.554688 -6.554688 h 0.019531 l -0.292969 -0.707031 c 0 0.265625 0.105469 0.519531 0.292969 0.707031 z m -3.617188 -6.03125 c 0 -0.265625 -0.105469 -0.519531 -0.292969 -0.707031 c -0.789062 -0.789063 -2.058593 -0.789063 -2.847656 0 l -6.554687 6.554687 c -0.789063 0.789063 -0.789063 2.058594 0 2.847657 l 3.683593 3.683593 c 0.1875 0.1875 0.441407 0.292969 0.707032 0.292969 h 4.621094 c 0.261718 0 0.511718 -0.101562 0.699218 -0.285156 l 5.015625 -4.921875 c 0.789063 -0.789063 0.789063 -2.058594 0 -2.847657 l -5.324219 -5.324218 z m 0 0",
            "m 8.539062 1.832031 l -5.292968 4.121094 l 7.800781 7.800781 l 4.117187 -5.296875 z m 0 0"
        )
    }

    val Selector: ImageVector by lazy {
        icon(
            "PenSelector",
            "m 3.015625 2.996094 c -1.644531 0 -3 1.355468 -3 3 h 2 c 0 -0.570313 0.429687 -1 1 -1 z m 0 0",
            "m 5.015625 -0.00390625 v 2.00000025 h 8 c 0.574219 0 1 0.429687 1 1 v 8 h 2 v -8 c 0 -1.644532 -1.351563 -3.00000025 -3 -3.00000025 z m 0 0",
            "m 3.019531 1.996094 c 0 -1.101563 0.894531 -1.996094 1.996094 -1.996094 v 1.996094 z m 0 0",
            "m 16.015625 10.996094 c 0 1.101562 -0.894531 1.996094 -1.996094 1.996094 v -1.996094 z m 0 0",
            "m 4.015625 2.996094 h 2 v 2 h -2 z m 0 0",
            "m 7.015625 2.996094 h 2 v 2 h -2 z m 0 0",
            "m 4.015625 13.996094 h 2 v 2 h -2 z m 0 0",
            "m 7.015625 13.996094 h 2 v 2 h -2 z m 0 0",
            "m 0.015625 6.996094 h 2 v 2 h -2 z m 0 0",
            "m 0.015625 9.996094 h 2 v 2 h -2 z m 0 0",
            "m 11.015625 6.996094 h 2 v 2 h -2 z m 0 0",
            "m 11.015625 9.996094 h 2 v 2 h -2 z m 0 0",
            "m 10.015625 2.996094 c 1.648437 0 3 1.355468 3 3 h -2 c 0 -0.570313 -0.425781 -1 -1 -1 z m 0 0",
            "m 3.015625 15.996094 c -1.644531 0 -3 -1.355469 -3 -3 h 2 c 0 0.570312 0.429687 1 1 1 z m 0 0",
            "m 10.015625 15.996094 c 1.648437 0 3 -1.355469 3 -3 h -2 c 0 0.570312 -0.425781 1 -1 1 z m 0 0"
        )
    }

    val Tools: ImageVector by lazy {
        icon(
            "PenTools",
            "m 11.5 0 c -2.484375 0 -4.5 2.015625 -4.5 4.5 s 2.015625 4.5 4.5 4.5 s 4.5 -2.015625 4.5 -4.5 c 0 -0.574219 -0.109375 -1.144531 -0.324219 -1.675781 l -2.175781 2.175781 h -2.5 v -2.5 l 2.175781 -2.175781 c -0.53125 -0.214844 -1.101562 -0.324219 -1.675781 -0.324219 z m 0 0",
            "m 7.730469 4.734375 l -7 7 c -0.46875 0.46875 -0.730469 1.101563 -0.730469 1.765625 s 0.261719 1.300781 0.730469 1.769531 c 0.976562 0.972657 2.5625 0.972657 3.539062 0 l 7 -7 z m -5.230469 7.765625 c 0.550781 0 1 0.449219 1 1 s -0.449219 1 -1 1 s -1 -0.449219 -1 -1 s 0.449219 -1 1 -1 z m 0 0"
        )
    }
}
