package com.rnote.baby.ui.theme

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

    // ── Full palette grid (color dialog) ────────────────────────────────
    // Leads with the same 9 real defaults, then bonus extras beyond what
    // desktop Rnote's own 9-slot picker offers.
    val FullPalette = PenPalette + listOf(
        Color(0xFF62A0EA), Color(0xFF57E389), Color(0xFFF8E45C), Color(0xFFFFBE6F), Color(0xFFED333B),
        Color(0xFF9141AC), Color(0xFFC061CB), Color(0xFF63452C), Color(0xFF77767B), Color(0xFF3D3846),
        Color(0xFFF66151)
    )
}
