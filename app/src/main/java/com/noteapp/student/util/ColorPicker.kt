package com.noteapp.student.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.GridLayout
import android.widget.TextView
import com.noteapp.student.settings.ThemeColors

/** A small grid-of-swatches color picker — theme adaptive */
object ColorPicker {

    private val palette = intArrayOf(
        Color.WHITE,
        Color.parseColor("#F3F4F6"),
        Color.parseColor("#FDE68A"),
        Color.parseColor("#FCA5A5"),
        Color.parseColor("#FCD34D"),
        Color.parseColor("#86EFAC"),
        Color.parseColor("#93C5FD"),
        Color.parseColor("#C4B5FD"),
        Color.parseColor("#F9A8D4"),
        Color.parseColor("#4B5563"),
        Color.BLACK,
        Color.TRANSPARENT
    )

    fun show(context: Context, title: String, themeColors: ThemeColors? = null, onPicked: (Int) -> Unit) {
        val colors = themeColors ?: ThemeColors.modernClean()
        val density = context.resources.displayMetrics.density
        val pad = (8 * density).toInt()

        val grid = GridLayout(context).apply {
            columnCount = 4
            setPadding(pad, pad, pad, pad)
        }
        val swatchSize = (44 * density).toInt()
        val swatchViews = mutableListOf<TextView>()
        for (color in palette) {
            val swatch = TextView(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (color == Color.TRANSPARENT) Color.TRANSPARENT else color)
                    setStroke((2 * density).toInt(), if (color == Color.TRANSPARENT) colors.cardBorder else Color.parseColor("#94A3B8"))
                }
                if (color == Color.TRANSPARENT) {
                    text = "None"
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(if (colors.isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
                }
            }
            val margin = (6 * density).toInt()
            val params = GridLayout.LayoutParams().apply {
                width = swatchSize
                height = swatchSize
                setMargins(margin, margin, margin, margin)
            }
            swatch.layoutParams = params
            grid.addView(swatch)
            swatchViews.add(swatch)
        }

        val dialog = ThemedDialog.Builder(context, colors)
            .setTitle(title)
            .setCustomView(grid)
            .setNegativeButton("Cancel")
            .show()

        for (i in swatchViews.indices) {
            swatchViews[i].setOnClickListener {
                onPicked(palette[i])
                dialog.dismiss()
            }
        }
    }
}
