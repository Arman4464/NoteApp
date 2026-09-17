package com.noteapp.student.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Manages dynamically switching the Android launcher app icon based on the active theme
 * using activity-alias toggling via PackageManager.
 */
object AppIconManager {

    val THEME_ALIASES = mapOf(
        ThemeType.MODERN_CLEAN to "com.noteapp.student.MainActivityClean",
        ThemeType.MILANOTE_DARK to "com.noteapp.student.MainActivityDark",
        ThemeType.WARM_PARCHMENT to "com.noteapp.student.MainActivityParchment",
        ThemeType.CYBERPUNK_NEON to "com.noteapp.student.MainActivityCyberpunk",
        ThemeType.SOLARIZED_MINT to "com.noteapp.student.MainActivityMint"
    )

    private var pendingTheme: ThemeType? = null

    fun applyAppIcon(context: Context, theme: ThemeType) {
        val pm = context.packageManager
        val targetAlias = THEME_ALIASES[theme] ?: return
        pendingTheme = theme

        try {
            // Enable the desired theme's launcher icon alias immediately
            pm.setComponentEnabledSetting(
                ComponentName(context, targetAlias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun flushDisabledAliases(context: Context) {
        val currentTheme = pendingTheme ?: AppSettings(context).theme
        val pm = context.packageManager

        try {
            for ((t, alias) in THEME_ALIASES) {
                if (t != currentTheme) {
                    val comp = ComponentName(context, alias)
                    val state = pm.getComponentEnabledSetting(comp)
                    if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
