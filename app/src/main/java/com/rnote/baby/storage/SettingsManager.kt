package com.rnote.baby.storage

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.rnote.baby.model.LayoutMode
import com.rnote.baby.model.PageSize
import com.rnote.baby.model.PaperPattern
import com.rnote.baby.model.PaperStyle

/**
 * Persists user preferences (paper style, tool settings) across app sessions
 * using SharedPreferences. All keys are versioned under "baby_rnote_settings".
 */
object SettingsManager {

    private const val PREFS_NAME             = "baby_rnote_settings"
    private const val KEY_DARK_MODE          = "isDarkMode"
    private const val KEY_PAGE_SIZE          = "pageSize"
    private const val KEY_DOT_DENSITY        = "dotDensityDpi"
    private const val KEY_PAPER_PATTERN      = "paperPattern"
    private const val KEY_IS_LANDSCAPE       = "isLandscape"
    private const val KEY_ALLOW_FINGER_DRAW  = "allowFingerDrawing"
    private const val KEY_LAYOUT_MODE        = "layoutMode"
    private const val KEY_DPI                = "dpi"
    private const val KEY_SHOW_BORDERS       = "showFormatBorders"
    private const val KEY_SHOW_ORIGIN        = "showOriginIndicator"
    private const val KEY_CUSTOM_WIDTH       = "customWidthPx"
    private const val KEY_CUSTOM_HEIGHT      = "customHeightPx"
    private const val KEY_GRID_SPACING       = "customGridSpacingPx"
    private const val KEY_PATTERN_HEIGHT     = "customPatternHeightPx"
    private const val KEY_CUSTOM_BG_COLOR    = "customBgColor"
    private const val KEY_CUSTOM_GRID_COLOR  = "customGridColor"
    private const val KEY_BORDER_COLOR       = "formatBorderColor"

    fun save(
        context: Context,
        paperStyle: PaperStyle,
        allowFingerDrawing: Boolean
    ) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.putBoolean(KEY_DARK_MODE, paperStyle.isDarkMode)
        editor.putString(KEY_PAGE_SIZE, paperStyle.pageSize.name)
        editor.putInt(KEY_DOT_DENSITY, paperStyle.dotDensityDpi)
        editor.putString(KEY_PAPER_PATTERN, paperStyle.pattern.name)
        editor.putBoolean(KEY_IS_LANDSCAPE, paperStyle.isLandscape)
        editor.putBoolean(KEY_ALLOW_FINGER_DRAW, allowFingerDrawing)
        editor.putString(KEY_LAYOUT_MODE, paperStyle.layoutMode.name)
        editor.putFloat(KEY_DPI, paperStyle.dpi)
        editor.putBoolean(KEY_SHOW_BORDERS, paperStyle.showFormatBorders)
        editor.putBoolean(KEY_SHOW_ORIGIN, paperStyle.showOriginIndicator)
        editor.putFloat(KEY_CUSTOM_WIDTH, paperStyle.customWidthPx)
        editor.putFloat(KEY_CUSTOM_HEIGHT, paperStyle.customHeightPx)
        editor.putFloat(KEY_GRID_SPACING, paperStyle.customGridSpacingPx)
        editor.putFloat(KEY_PATTERN_HEIGHT, paperStyle.customPatternHeightPx)
        editor.putInt(KEY_BORDER_COLOR, paperStyle.formatBorderColor.toArgb())
        paperStyle.customBackgroundColor?.let { editor.putInt(KEY_CUSTOM_BG_COLOR, it.toArgb()) }
            ?: editor.remove(KEY_CUSTOM_BG_COLOR)
        paperStyle.customGridColor?.let { editor.putInt(KEY_CUSTOM_GRID_COLOR, it.toArgb()) }
            ?: editor.remove(KEY_CUSTOM_GRID_COLOR)
        editor.apply()
    }

    fun loadPaperStyle(context: Context): PaperStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pageSize = try {
            PageSize.valueOf(prefs.getString(KEY_PAGE_SIZE, PageSize.A3.name)!!)
        } catch (e: Exception) { PageSize.A3 }
        val pattern = try {
            PaperPattern.valueOf(prefs.getString(KEY_PAPER_PATTERN, PaperPattern.DOTS.name)!!)
        } catch (e: Exception) { PaperPattern.DOTS }
        val layoutMode = try {
            LayoutMode.valueOf(prefs.getString(KEY_LAYOUT_MODE, LayoutMode.INFINITE.name)!!)
        } catch (e: Exception) { LayoutMode.INFINITE }

        val customBgColor = if (prefs.contains(KEY_CUSTOM_BG_COLOR))
            Color(prefs.getInt(KEY_CUSTOM_BG_COLOR, 0)) else null
        val customGridColor = if (prefs.contains(KEY_CUSTOM_GRID_COLOR))
            Color(prefs.getInt(KEY_CUSTOM_GRID_COLOR, 0)) else null

        return PaperStyle(
            isDarkMode           = prefs.getBoolean(KEY_DARK_MODE, true),
            pageSize             = pageSize,
            dotDensityDpi        = prefs.getInt(KEY_DOT_DENSITY, 5),
            pattern              = pattern,
            isLandscape          = prefs.getBoolean(KEY_IS_LANDSCAPE, false),
            layoutMode           = layoutMode,
            dpi                  = prefs.getFloat(KEY_DPI, 96f),
            showFormatBorders    = prefs.getBoolean(KEY_SHOW_BORDERS, true),
            showOriginIndicator  = prefs.getBoolean(KEY_SHOW_ORIGIN, true),
            customWidthPx        = prefs.getFloat(KEY_CUSTOM_WIDTH, 0f),
            customHeightPx       = prefs.getFloat(KEY_CUSTOM_HEIGHT, 0f),
            customGridSpacingPx  = prefs.getFloat(KEY_GRID_SPACING, 0f),
            customPatternHeightPx = prefs.getFloat(KEY_PATTERN_HEIGHT, 0f),
            formatBorderColor    = Color(prefs.getInt(KEY_BORDER_COLOR, Color(0xFFDEDDD9).toArgb())),
            customBackgroundColor = customBgColor,
            customGridColor      = customGridColor
        )
    }

    fun loadAllowFingerDrawing(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_FINGER_DRAW, false)
}
