package com.noteapp.student.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

enum class ThemeType(val displayName: String) {
    MODERN_CLEAN("Modern Clean (Light)"),
    MILANOTE_DARK("Milanote Slate (Dark)"),
    WARM_PARCHMENT("Warm Parchment (Sepia)"),
    CYBERPUNK_NEON("Cyberpunk Neon Studio"),
    SOLARIZED_MINT("Solarized Sage (Mint)")
}

enum class GridStyle(val displayName: String) {
    DOTS("Dotted Grid"),
    LINES("Line Grid"),
    NONE("Clean / Blank")
}

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("noteapp_settings", Context.MODE_PRIVATE)

    var theme: ThemeType
        get() {
            val name = prefs.getString("theme", ThemeType.MODERN_CLEAN.name)
            return try {
                ThemeType.valueOf(name ?: ThemeType.MODERN_CLEAN.name)
            } catch (e: Exception) {
                ThemeType.MODERN_CLEAN
            }
        }
        set(value) = prefs.edit().putString("theme", value.name).apply()

    var gridStyle: GridStyle
        get() {
            val name = prefs.getString("grid_style", GridStyle.DOTS.name)
            return try {
                GridStyle.valueOf(name ?: GridStyle.DOTS.name)
            } catch (e: Exception) {
                GridStyle.DOTS
            }
        }
        set(value) = prefs.edit().putString("grid_style", value.name).apply()

    var gridSnap: Boolean
        get() = prefs.getBoolean("grid_snap", false)
        set(value) = prefs.edit().putBoolean("grid_snap", value).apply()

    var defaultFont: String
        get() = prefs.getString("default_font", "outfit") ?: "outfit"
        set(value) = prefs.edit().putString("default_font", value).apply()

    var defaultCardColor: Int
        get() = prefs.getInt("default_card_color", Color.WHITE)
        set(value) = prefs.edit().putInt("default_card_color", value).apply()

    var tutorialCompleted: Boolean
        get() = prefs.getBoolean("tutorial_completed", false)
        set(value) = prefs.edit().putBoolean("tutorial_completed", value).apply()

    var exportAsPdf: Boolean
        get() = prefs.getBoolean("export_as_pdf", false)
        set(value) = prefs.edit().putBoolean("export_as_pdf", value).apply()
}
