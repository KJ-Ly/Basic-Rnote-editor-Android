package io.github.kjly.brna.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralized color tokens for BRNA's floating chrome (pen picker, color picker,
 * pen config strip, top bar). Previously these were duplicated as raw hex
 * literals across FloatingToolBar/RnoteTopBar/ColorPickerSheet/PageSettingsSheet
 * with no single source of truth — this is that source.
 *
 * The floating overlay bars deliberately stay dark/semi-opaque regardless of the
 * app theme, matching desktop Rnote's own overlay toolbars.
 */
object BrnaColors {

    // ── Accent / brand ──────────────────────────────────────────────────────
    val Accent = Color(0xFF82AAFF)          // "Rnote Blue" — primary accent, selection highlight
    val AccentOnLight = Color(0xFF1A1A2E)   // text/icon color when placed on an Accent background

    // ── Floating panel chrome ───────────────────────────────────────────────
    val PanelSurface = Color(0xFF282A36)
    const val PanelSurfaceAlpha = 0.92f
    val PanelInactive = Color(0xFF383A4A)
    val PanelDialogSurface = Color(0xFF1E1E24)

    val TextPrimaryOnPanel = Color.White
    val TextSecondaryOnPanel = Color.Gray

    // Desktop Rnote's pen icons are a single neutral color always — only the button
    // background changes for the active pen (GTK's checked-toggle style) — so unlike an
    // earlier version of this file, there is deliberately no per-tool tint here.
    val DisabledPenTint = Color(0xFF55565E)  // Shaper / Typewriter / Tools — not yet implemented

    val DestructiveTint = Color(0xFFFF5370)
    val DestructiveContainer = Color(0x33FF5370)

    // ── Pen picker quick-access swatches ────────────────────────────────────
    // Rnote's actual default 9-slot palette (gschema colorpicker-color-1..9),
    // GNOME/Adwaita-derived: black, white, transparent, then light-blue, blue,
    // green, yellow, orange, dark-red. Color.Transparent renders as a
    // checkerboard swatch in ColorPicker rather than a solid fill.
    val PenPalette = listOf(
        Color.Black,
        Color.White,
        Color.Transparent,
        Color(0xFF99C1F1), // light blue
        Color(0xFF1C71D8), // blue
        Color(0xFF26A269), // green
        Color(0xFFF6D32D), // yellow
        Color(0xFFE66100), // orange
        Color(0xFFA51D2D)  // dark red
    )

    // ── Full palette grid ("Pick a Color" dialog) ──────────────────────────
    // Verbatim GTK4 ColorChooserWidget default palette — the exact grid desktop
    // Rnote shows, since its color dialog is that widget. Column-major: 9 hue
    // columns, each a light→dark 5-shade ramp, rendered as one contiguous strip
    // per column the way GTK draws it.
    val PaletteColumns: List<List<Color>> = listOf(
        listOf(0xFF99C1F1, 0xFF62A0EA, 0xFF3584E4, 0xFF1C71D8, 0xFF1A5FB4), // Blue
        listOf(0xFF8FF0A4, 0xFF57E389, 0xFF33D17A, 0xFF2EC27E, 0xFF26A269), // Green
        listOf(0xFFF9F06B, 0xFFF8E45C, 0xFFF6D32D, 0xFFF5C211, 0xFFE5A50A), // Yellow
        listOf(0xFFFFBE6F, 0xFFFFA348, 0xFFFF7800, 0xFFE66100, 0xFFC64600), // Orange
        listOf(0xFFF66151, 0xFFED333B, 0xFFE01B24, 0xFFC01C28, 0xFFA51D2D), // Red
        listOf(0xFFDC8ADD, 0xFFC061CB, 0xFF9141AC, 0xFF813D9C, 0xFF613583), // Purple
        listOf(0xFFCDAB8F, 0xFFB5835A, 0xFF986A44, 0xFF865E3C, 0xFF63452C), // Brown
        listOf(0xFFFFFFFF, 0xFFF6F5F4, 0xFFDEDDDA, 0xFFC0BFBC, 0xFF9A9996), // Light
        listOf(0xFF77767B, 0xFF5E5C64, 0xFF3D3846, 0xFF241F31, 0xFF000000)  // Dark
    ).map { column -> column.map { Color(it) } }
}
