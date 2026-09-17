package com.noteapp.student.tutorial

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.noteapp.student.settings.AppSettings

class TutorialDialog(
    private val context: Context,
    private val appSettings: AppSettings,
    private val onCompleted: () -> Unit
) {

    private data class TutorialStep(
        val icon: String,
        val title: String,
        val headline: String,
        val description: String
    )

    private val steps = listOf(
        TutorialStep(
            icon = "\uD83C\uDF10",
            title = "Infinite Canvas & Navigation",
            headline = "Glide freely across 24,000px of open workspace",
            description = "• Pinch with 2 fingers to zoom smoothly (0.15x to 4x).\n• Drag with 2 fingers or switch to the Pan tool (hand icon) to explore.\n• Tap the floating Zoom HUD chip ('Fit' or '100%') anytime to re-center instantly."
        ),
        TutorialStep(
            icon = "\uD83D\uDCDD",
            title = "Cards, Notes & Sub-Boards",
            headline = "Structure your thoughts your way",
            description = "• Tap the (+) dock button to create Text Notes, Sticky Notes, interactive Checklists, Images, and Shapes.\n• Add Milanote-style Sub-Board cards to nest workspaces inside workspaces.\n• Tap the 3-dot menu on any card to change colors, fonts, or shapes."
        ),
        TutorialStep(
            icon = "\uD83C\uDFAF",
            title = "Multi-Select & Group Move",
            headline = "Organize multiple ideas simultaneously",
            description = "• In Select mode, drag across empty canvas to lasso multiple cards with the marquee box.\n• Drag any card in the selection to move the whole group together.\n• Use the top bar to batch-change colors, duplicate, align, or delete all selected cards."
        ),
        TutorialStep(
            icon = "\u270F\uFE0F",
            title = "Freehand Inking & Connectors",
            headline = "Sketch diagrams and link relationships",
            description = "• Tap the Pen tool for fluid vector inking; tap again to pick fine/bold strokes, highlighters, and custom ink colors.\n• Use the Eraser tool to remove strokes cleanly.\n• Use the Connector tool to tap two cards and link them with an elegant curved arrow."
        ),
        TutorialStep(
            icon = "\uD83C\uDFA8",
            title = "Themes, Fonts & 3x Export",
            headline = "Personalize your workspace and share your work",
            description = "• Open Settings (gear icon) to switch between 5 unique themes and 6 bundled Google fonts.\n• Tap Export to generate 3x oversampled high-resolution PNG images or documents (PDF) with instant sharing."
        )
    )

    private var currentStep = 0

    fun show() {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 32f
                setStroke(2, Color.parseColor("#334155"))
            }
            setPadding(40, 36, 40, 36)
            elevation = 20f
        }

        // Top Bar: Step indicator + Skip button
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val stepBadge = TextView(context).apply {
            text = "STEP ${currentStep + 1} OF ${steps.size}"
            setTextColor(Color.parseColor("#818CF8"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val skipBtn = TextView(context).apply {
            text = "Skip Tutorial"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                appSettings.tutorialCompleted = true
                dialog.dismiss()
                onCompleted()
            }
        }
        topRow.addView(stepBadge)
        topRow.addView(skipBtn)
        root.addView(topRow)

        // Center Icon & Title
        val iconView = TextView(context).apply {
            text = steps[currentStep].icon
            textSize = 44f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }
        val titleView = TextView(context).apply {
            text = steps[currentStep].title
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val headlineView = TextView(context).apply {
            text = steps[currentStep].headline
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 20)
        }
        val descView = TextView(context).apply {
            text = steps[currentStep].description
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 14f
            setLineSpacing(12f, 1f)
            setPadding(0, 8, 0, 32)
        }

        root.addView(iconView)
        root.addView(titleView)
        root.addView(headlineView)
        root.addView(descView)

        // Bottom Navigation Row: Prev & Next
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val prevBtn = TextView(context).apply {
            text = "← Back"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 15f
            setPadding(24, 16, 24, 16)
            isClickable = true
        }

        val spacer = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        val nextBtn = TextView(context).apply {
            text = "Next →"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4F46E5"))
                cornerRadius = 24f
            }
            setPadding(36, 16, 36, 16)
            gravity = Gravity.CENTER
        }

        fun updateUI() {
            stepBadge.text = "STEP ${currentStep + 1} OF ${steps.size}"
            iconView.text = steps[currentStep].icon
            titleView.text = steps[currentStep].title
            headlineView.text = steps[currentStep].headline
            descView.text = steps[currentStep].description

            prevBtn.visibility = if (currentStep > 0) android.view.View.VISIBLE else android.view.View.INVISIBLE
            nextBtn.text = if (currentStep == steps.size - 1) "Start Exploring! 🚀" else "Next →"
        }

        prevBtn.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                updateUI()
            }
        }

        nextBtn.setOnClickListener {
            if (currentStep < steps.size - 1) {
                currentStep++
                updateUI()
            } else {
                appSettings.tutorialCompleted = true
                dialog.dismiss()
                onCompleted()
            }
        }

        bottomRow.addView(prevBtn)
        bottomRow.addView(spacer)
        bottomRow.addView(nextBtn)
        root.addView(bottomRow)

        dialog.setContentView(root)
        dialog.setCancelable(false)
        updateUI()
        dialog.show()
    }
}
