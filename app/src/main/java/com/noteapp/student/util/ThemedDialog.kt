package com.noteapp.student.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.noteapp.student.settings.ThemeColors

/**
 * Modern, theme-adaptive dialog system.
 * Renders beautiful card dialogs respecting all 5 app themes.
 */
class ThemedDialog private constructor(
    private val context: Context,
    private val colors: ThemeColors
) {
    private var titleText: String? = null
    private var messageText: String? = null
    private var customContentView: View? = null
    private val items = mutableListOf<ItemEntry>()
    private var positiveButtonText: String? = null
    private var positiveButtonAction: (() -> Unit)? = null
    private var negativeButtonText: String? = null
    private var negativeButtonAction: (() -> Unit)? = null
    private var dismissOnItemClick: Boolean = true

    data class ItemEntry(
        val label: String,
        val iconRes: Int? = null,
        val subtitle: String? = null,
        val isSelected: Boolean = false,
        val onClick: () -> Unit
    )

    class Builder(private val context: Context, private val colors: ThemeColors) {
        private val dialog = ThemedDialog(context, colors)

        fun setTitle(title: String) = apply { dialog.titleText = title }
        fun setMessage(message: String) = apply { dialog.messageText = message }
        fun setCustomView(view: View) = apply { dialog.customContentView = view }
        fun setDismissOnItemClick(dismiss: Boolean) = apply { dialog.dismissOnItemClick = dismiss }

        fun addItem(
            label: String,
            iconRes: Int? = null,
            subtitle: String? = null,
            isSelected: Boolean = false,
            onClick: () -> Unit
        ) = apply {
            dialog.items.add(ItemEntry(label, iconRes, subtitle, isSelected, onClick))
        }

        fun setItems(
            items: Array<String>,
            selectedIndex: Int = -1,
            onSelect: (which: Int) -> Unit
        ) = apply {
            items.forEachIndexed { index, label ->
                dialog.items.add(ItemEntry(
                    label = label,
                    isSelected = (index == selectedIndex),
                    onClick = { onSelect(index) }
                ))
            }
        }

        fun setPositiveButton(text: String, action: (() -> Unit)? = null) = apply {
            dialog.positiveButtonText = text
            dialog.positiveButtonAction = action
        }

        fun setNegativeButton(text: String = "Cancel", action: (() -> Unit)? = null) = apply {
            dialog.negativeButtonText = text
            dialog.negativeButtonAction = action
        }

        fun show(): Dialog {
            return dialog.buildAndShow()
        }
    }

    private fun buildAndShow(): Dialog {
        val dlg = Dialog(context)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val theme = colors
        val density = context.resources.displayMetrics.density
        val pad16 = (16 * density).toInt()
        val pad12 = (12 * density).toInt()
        val pad8 = (8 * density).toInt()

        // Root container (rounded card)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * density
                setColor(theme.cardDefaultBg)
                setStroke((1.5f * density).toInt(), theme.cardBorder)
            }
            elevation = 20 * density
            setPadding(pad16, pad16, pad16, pad16)
        }

        // 1. Header (Title + Close button)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, pad12)
        }

        val tvTitle = TextView(context).apply {
            text = titleText ?: ""
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(theme.topBarText)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(tvTitle)

        val btnClose = TextView(context).apply {
            text = "✕"
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(pad8, pad8, pad8, pad8)
            setOnClickListener { dlg.dismiss() }
        }
        headerLayout.addView(btnClose)
        root.addView(headerLayout)

        // Subtle divider under header
        val headerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply {
                bottomMargin = pad12
            }
            setBackgroundColor(theme.cardBorder)
        }
        root.addView(headerDivider)

        // 2. Optional message
        messageText?.let { msg ->
            val tvMsg = TextView(context).apply {
                text = msg
                textSize = 14f
                setTextColor(if (theme.isDark) Color.parseColor("#CBD5E1") else Color.parseColor("#475569"))
                setPadding(0, 0, 0, pad12)
                setLineSpacing(0f, 1.25f)
            }
            root.addView(tvMsg)
        }

        // 3. Custom content view if provided
        customContentView?.let { cv ->
            (cv.parent as? ViewGroup)?.removeView(cv)
            root.addView(cv)
        }

        // 4. Items List (if any)
        if (items.isNotEmpty()) {
            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            val itemsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            for (item in items) {
                val itemRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val rowPad = (10 * density).toInt()
                    setPadding(rowPad, rowPad, rowPad, rowPad)

                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 10 * density
                        if (item.isSelected) {
                            setColor(if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EEF2FF"))
                            setStroke((1.5f * density).toInt(), theme.accent)
                        } else {
                            setColor(Color.TRANSPARENT)
                        }
                    }
                    background = bg

                    setOnClickListener {
                        item.onClick()
                        if (dismissOnItemClick) dlg.dismiss()
                    }
                }

                // Optional Icon
                item.iconRes?.let { icon ->
                    val iv = ImageView(context).apply {
                        setImageResource(icon)
                        val iconSize = (22 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            rightMargin = pad12
                        }
                        setColorFilter(if (item.isSelected) theme.accent else theme.topBarText)
                    }
                    itemRow.addView(iv)
                }

                // Text column
                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvLabel = TextView(context).apply {
                    text = item.label
                    textSize = 14f
                    typeface = if (item.isSelected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                    setTextColor(if (item.isSelected) theme.accent else theme.topBarText)
                }
                textCol.addView(tvLabel)

                item.subtitle?.let { sub ->
                    val tvSub = TextView(context).apply {
                        text = sub
                        textSize = 11f
                        setTextColor(if (theme.isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
                    }
                    textCol.addView(tvSub)
                }
                itemRow.addView(textCol)

                if (item.isSelected) {
                    val tvCheck = TextView(context).apply {
                        text = "●"
                        textSize = 14f
                        setTextColor(theme.accent)
                    }
                    itemRow.addView(tvCheck)
                }

                itemsLayout.addView(itemRow)
            }
            scrollView.addView(itemsLayout)
            root.addView(scrollView)
        }

        // 5. Actions / Buttons
        if (positiveButtonText != null || negativeButtonText != null) {
            val buttonsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, pad16, 0, 0)
            }

            negativeButtonText?.let { negText ->
                val btnNeg = Button(context).apply {
                    text = negText
                    textSize = 13f
                    isAllCaps = false
                    setTextColor(if (theme.isDark) Color.parseColor("#CBD5E1") else Color.parseColor("#475569"))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 10 * density
                        setColor(Color.TRANSPARENT)
                        setStroke((1 * density).toInt(), theme.cardBorder)
                    }
                    val btnHeight = (38 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, btnHeight).apply {
                        rightMargin = pad8
                    }
                    setOnClickListener {
                        negativeButtonAction?.invoke()
                        dlg.dismiss()
                    }
                }
                buttonsLayout.addView(btnNeg)
            }

            positiveButtonText?.let { posText ->
                val btnPos = Button(context).apply {
                    text = posText
                    textSize = 13f
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 10 * density
                        setColor(theme.accent)
                    }
                    val btnHeight = (38 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, btnHeight)
                    setOnClickListener {
                        positiveButtonAction?.invoke()
                        dlg.dismiss()
                    }
                }
                buttonsLayout.addView(btnPos)
            }

            root.addView(buttonsLayout)
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val dialogWidth = (screenWidth * 0.90f).toInt().coerceAtMost((420 * density).toInt())
        val params = ViewGroup.LayoutParams(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        dlg.setContentView(root, params)
        dlg.show()
        return dlg
    }
}
