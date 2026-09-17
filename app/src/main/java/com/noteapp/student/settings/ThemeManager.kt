package com.noteapp.student.settings

import android.graphics.Color

data class ThemeColors(
    val canvasBg: Int,
    val gridDot: Int,
    val topBarBg: Int,
    val topBarText: Int,
    val accent: Int,
    val cardDefaultBg: Int,
    val cardBorder: Int,
    val dockBg: Int,
    val isDark: Boolean,
    val defaultTextColor: Int = Color.parseColor("#0F172A")
) {
    companion object {
        fun modernClean(): ThemeColors = ThemeManager.getThemeColors(ThemeType.MODERN_CLEAN)
    }
}

object ThemeManager {

    fun getThemeColors(type: ThemeType): ThemeColors {
        return when (type) {
            ThemeType.MODERN_CLEAN -> ThemeColors(
                canvasBg = Color.parseColor("#F8FAFC"),
                gridDot = Color.parseColor("#94A3B8"),
                topBarBg = Color.parseColor("#FFFFFF"),
                topBarText = Color.parseColor("#0F172A"),
                accent = Color.parseColor("#4F46E5"),
                cardDefaultBg = Color.WHITE,
                cardBorder = Color.parseColor("#C7C9F2"),
                dockBg = Color.parseColor("#0F172A"),
                isDark = false,
                defaultTextColor = Color.parseColor("#0F172A")
            )
            ThemeType.MILANOTE_DARK -> ThemeColors(
                canvasBg = Color.parseColor("#0F172A"),
                gridDot = Color.parseColor("#334155"),
                topBarBg = Color.parseColor("#1E293B"),
                topBarText = Color.parseColor("#F8FAFC"),
                accent = Color.parseColor("#8B5CF6"),
                cardDefaultBg = Color.parseColor("#1E293B"),
                cardBorder = Color.parseColor("#475569"),
                dockBg = Color.parseColor("#1E293B"),
                isDark = true,
                defaultTextColor = Color.parseColor("#F8FAFC")
            )
            ThemeType.WARM_PARCHMENT -> ThemeColors(
                canvasBg = Color.parseColor("#FDF6E2"),
                gridDot = Color.parseColor("#D5C8A8"),
                topBarBg = Color.parseColor("#FFFBEB"),
                topBarText = Color.parseColor("#78350F"),
                accent = Color.parseColor("#D97706"),
                cardDefaultBg = Color.parseColor("#FEF3C7"),
                cardBorder = Color.parseColor("#E5D5B8"),
                dockBg = Color.parseColor("#78350F"),
                isDark = false,
                defaultTextColor = Color.parseColor("#451A03")
            )
            ThemeType.CYBERPUNK_NEON -> ThemeColors(
                canvasBg = Color.parseColor("#090D16"),
                gridDot = Color.parseColor("#164E63"),
                topBarBg = Color.parseColor("#111827"),
                topBarText = Color.parseColor("#22D3EE"),
                accent = Color.parseColor("#A855F7"),
                cardDefaultBg = Color.parseColor("#111827"),
                cardBorder = Color.parseColor("#06B6D4"),
                dockBg = Color.parseColor("#0F172A"),
                isDark = true,
                defaultTextColor = Color.parseColor("#F1F5F9")
            )
            ThemeType.SOLARIZED_MINT -> ThemeColors(
                canvasBg = Color.parseColor("#E8F5E9"),
                gridDot = Color.parseColor("#81C784"),
                topBarBg = Color.parseColor("#F1F8F5"),
                topBarText = Color.parseColor("#064E3B"),
                accent = Color.parseColor("#059669"),
                cardDefaultBg = Color.WHITE,
                cardBorder = Color.parseColor("#A5D6A7"),
                dockBg = Color.parseColor("#064E3B"),
                isDark = false,
                defaultTextColor = Color.parseColor("#064E3B")
            )
        }
    }
}
