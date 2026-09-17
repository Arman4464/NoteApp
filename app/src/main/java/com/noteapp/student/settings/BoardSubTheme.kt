package com.noteapp.student.settings

import android.graphics.Color

data class BoardSubTheme(
    val id: String,
    val name: String,
    val lightMode: ThemeColors,
    val darkMode: ThemeColors
) {
    companion object {
        val ALL: List<BoardSubTheme> = listOf(
            BoardSubTheme(
                id = "oceanic_abyss",
                name = "Oceanic Abyss",
                lightMode = ThemeColors(
                    canvasBg = Color.parseColor("#E0F2FE"),
                    gridDot = Color.parseColor("#7DD3FC"),
                    topBarBg = Color.parseColor("#F0F9FF"),
                    topBarText = Color.parseColor("#0369A1"),
                    accent = Color.parseColor("#0284C7"),
                    cardDefaultBg = Color.WHITE,
                    cardBorder = Color.parseColor("#BAE6FD"),
                    dockBg = Color.parseColor("#0C4A6E"),
                    isDark = false,
                    defaultTextColor = Color.parseColor("#0C4A6E")
                ),
                darkMode = ThemeColors(
                    canvasBg = Color.parseColor("#082F49"),
                    gridDot = Color.parseColor("#0369A1"),
                    topBarBg = Color.parseColor("#0C4A6E"),
                    topBarText = Color.parseColor("#E0F2FE"),
                    accent = Color.parseColor("#38BDF8"),
                    cardDefaultBg = Color.parseColor("#0C4A6E"),
                    cardBorder = Color.parseColor("#0284C7"),
                    dockBg = Color.parseColor("#032030"),
                    isDark = true,
                    defaultTextColor = Color.parseColor("#F0F9FF")
                )
            ),
            BoardSubTheme(
                id = "lavender_amethyst",
                name = "Lavender Amethyst",
                lightMode = ThemeColors(
                    canvasBg = Color.parseColor("#F5F3FF"),
                    gridDot = Color.parseColor("#C4B5FD"),
                    topBarBg = Color.parseColor("#FAF5FF"),
                    topBarText = Color.parseColor("#6D28D9"),
                    accent = Color.parseColor("#7C3AED"),
                    cardDefaultBg = Color.WHITE,
                    cardBorder = Color.parseColor("#DDD6FE"),
                    dockBg = Color.parseColor("#4C1D95"),
                    isDark = false,
                    defaultTextColor = Color.parseColor("#4C1D95")
                ),
                darkMode = ThemeColors(
                    canvasBg = Color.parseColor("#1E1035"),
                    gridDot = Color.parseColor("#5B21B6"),
                    topBarBg = Color.parseColor("#2E1065"),
                    topBarText = Color.parseColor("#F5F3FF"),
                    accent = Color.parseColor("#A78BFA"),
                    cardDefaultBg = Color.parseColor("#2E1065"),
                    cardBorder = Color.parseColor("#6D28D9"),
                    dockBg = Color.parseColor("#140826"),
                    isDark = true,
                    defaultTextColor = Color.parseColor("#FAF5FF")
                )
            ),
            BoardSubTheme(
                id = "forest_pine",
                name = "Forest Pine",
                lightMode = ThemeColors(
                    canvasBg = Color.parseColor("#ECFDF5"),
                    gridDot = Color.parseColor("#6EE7B7"),
                    topBarBg = Color.parseColor("#F0FDF4"),
                    topBarText = Color.parseColor("#166534"),
                    accent = Color.parseColor("#15803D"),
                    cardDefaultBg = Color.WHITE,
                    cardBorder = Color.parseColor("#BBF7D0"),
                    dockBg = Color.parseColor("#14532D"),
                    isDark = false,
                    defaultTextColor = Color.parseColor("#14532D")
                ),
                darkMode = ThemeColors(
                    canvasBg = Color.parseColor("#052E16"),
                    gridDot = Color.parseColor("#166534"),
                    topBarBg = Color.parseColor("#14532D"),
                    topBarText = Color.parseColor("#ECFDF5"),
                    accent = Color.parseColor("#4ADE80"),
                    cardDefaultBg = Color.parseColor("#14532D"),
                    cardBorder = Color.parseColor("#15803D"),
                    dockBg = Color.parseColor("#021C0D"),
                    isDark = true,
                    defaultTextColor = Color.parseColor("#F0FDF4")
                )
            ),
            BoardSubTheme(
                id = "sunset_twilight",
                name = "Sunset Twilight",
                lightMode = ThemeColors(
                    canvasBg = Color.parseColor("#FFF7ED"),
                    gridDot = Color.parseColor("#FDBA74"),
                    topBarBg = Color.parseColor("#FFEDD5"),
                    topBarText = Color.parseColor("#9A3412"),
                    accent = Color.parseColor("#EA580C"),
                    cardDefaultBg = Color.WHITE,
                    cardBorder = Color.parseColor("#FED7AA"),
                    dockBg = Color.parseColor("#7C2D12"),
                    isDark = false,
                    defaultTextColor = Color.parseColor("#7C2D12")
                ),
                darkMode = ThemeColors(
                    canvasBg = Color.parseColor("#271406"),
                    gridDot = Color.parseColor("#7C2D12"),
                    topBarBg = Color.parseColor("#431407"),
                    topBarText = Color.parseColor("#FED7AA"),
                    accent = Color.parseColor("#FB923C"),
                    cardDefaultBg = Color.parseColor("#431407"),
                    cardBorder = Color.parseColor("#9A3412"),
                    dockBg = Color.parseColor("#1A0C04"),
                    isDark = true,
                    defaultTextColor = Color.parseColor("#FFEDD5")
                )
            ),
            BoardSubTheme(
                id = "rose_crimson",
                name = "Rose Crimson",
                lightMode = ThemeColors(
                    canvasBg = Color.parseColor("#FFF1F2"),
                    gridDot = Color.parseColor("#FDA4AF"),
                    topBarBg = Color.parseColor("#FFE4E6"),
                    topBarText = Color.parseColor("#9F1239"),
                    accent = Color.parseColor("#E11D48"),
                    cardDefaultBg = Color.WHITE,
                    cardBorder = Color.parseColor("#FECDD3"),
                    dockBg = Color.parseColor("#881337"),
                    isDark = false,
                    defaultTextColor = Color.parseColor("#881337")
                ),
                darkMode = ThemeColors(
                    canvasBg = Color.parseColor("#260711"),
                    gridDot = Color.parseColor("#881337"),
                    topBarBg = Color.parseColor("#4C0519"),
                    topBarText = Color.parseColor("#FFE4E6"),
                    accent = Color.parseColor("#FB7185"),
                    cardDefaultBg = Color.parseColor("#4C0519"),
                    cardBorder = Color.parseColor("#9F1239"),
                    dockBg = Color.parseColor("#19040A"),
                    isDark = true,
                    defaultTextColor = Color.parseColor("#FFE4E6")
                )
            ),
            BoardSubTheme(
                id = "nordic_arctic",
                name = "Nordic Arctic",
                lightMode = ThemeColors(
                    canvasBg = Color.parseColor("#F1F5F9"),
                    gridDot = Color.parseColor("#94A3B8"),
                    topBarBg = Color.parseColor("#F8FAFC"),
                    topBarText = Color.parseColor("#334155"),
                    accent = Color.parseColor("#475569"),
                    cardDefaultBg = Color.WHITE,
                    cardBorder = Color.parseColor("#CBD5E1"),
                    dockBg = Color.parseColor("#1E293B"),
                    isDark = false,
                    defaultTextColor = Color.parseColor("#1E293B")
                ),
                darkMode = ThemeColors(
                    canvasBg = Color.parseColor("#0B0F19"),
                    gridDot = Color.parseColor("#334155"),
                    topBarBg = Color.parseColor("#111827"),
                    topBarText = Color.parseColor("#E2E8F0"),
                    accent = Color.parseColor("#94A3B8"),
                    cardDefaultBg = Color.parseColor("#111827"),
                    cardBorder = Color.parseColor("#334155"),
                    dockBg = Color.parseColor("#030712"),
                    isDark = true,
                    defaultTextColor = Color.parseColor("#F1F5F9")
                )
            ),
            BoardSubTheme(
                id = "golden_obsidian",
                name = "Golden Obsidian",
                lightMode = ThemeColors(
                    canvasBg = Color.parseColor("#FEFCE8"),
                    gridDot = Color.parseColor("#FDE047"),
                    topBarBg = Color.parseColor("#FEF9C3"),
                    topBarText = Color.parseColor("#854D0E"),
                    accent = Color.parseColor("#CA8A04"),
                    cardDefaultBg = Color.WHITE,
                    cardBorder = Color.parseColor("#FEF08A"),
                    dockBg = Color.parseColor("#713F12"),
                    isDark = false,
                    defaultTextColor = Color.parseColor("#713F12")
                ),
                darkMode = ThemeColors(
                    canvasBg = Color.parseColor("#181503"),
                    gridDot = Color.parseColor("#713F12"),
                    topBarBg = Color.parseColor("#2E2305"),
                    topBarText = Color.parseColor("#FEF08A"),
                    accent = Color.parseColor("#FACC15"),
                    cardDefaultBg = Color.parseColor("#2E2305"),
                    cardBorder = Color.parseColor("#854D0E"),
                    dockBg = Color.parseColor("#100D02"),
                    isDark = true,
                    defaultTextColor = Color.parseColor("#FEF9C3")
                )
            ),
            BoardSubTheme(
                id = "tokyo_synth",
                name = "Tokyo Synth",
                lightMode = ThemeColors(
                    canvasBg = Color.parseColor("#FDF2F8"),
                    gridDot = Color.parseColor("#F472B6"),
                    topBarBg = Color.parseColor("#FCE7F3"),
                    topBarText = Color.parseColor("#831843"),
                    accent = Color.parseColor("#DB2777"),
                    cardDefaultBg = Color.WHITE,
                    cardBorder = Color.parseColor("#FBCFE8"),
                    dockBg = Color.parseColor("#500724"),
                    isDark = false,
                    defaultTextColor = Color.parseColor("#500724")
                ),
                darkMode = ThemeColors(
                    canvasBg = Color.parseColor("#18091E"),
                    gridDot = Color.parseColor("#701A75"),
                    topBarBg = Color.parseColor("#2B0938"),
                    topBarText = Color.parseColor("#FCE7F3"),
                    accent = Color.parseColor("#F472B6"),
                    cardDefaultBg = Color.parseColor("#2B0938"),
                    cardBorder = Color.parseColor("#86198F"),
                    dockBg = Color.parseColor("#110515"),
                    isDark = true,
                    defaultTextColor = Color.parseColor("#FDF2F8")
                )
            )
        )

        fun findById(id: String?): BoardSubTheme? {
            if (id == null) return null
            return ALL.find { it.id.equals(id, ignoreCase = true) }
        }

        fun resolveThemeColors(subThemeId: String?, isDark: Boolean?, fallback: ThemeColors): ThemeColors {
            val subTheme = findById(subThemeId) ?: return fallback
            val dark = isDark ?: false
            return if (dark) subTheme.darkMode else subTheme.lightMode
        }
    }
}
