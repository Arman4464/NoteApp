package com.noteapp.student.tutorial

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Floating interactive Game Quest HUD for NoteApp's onboarding and training.
 * Provides quest progression, educational lore ("What It Does"), explicit actionable
 * missions ("What You Have To Do"), animated checkmarks, and minimization support.
 */
class GameTutorialHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    // Sub-views
    private val mainCard: LinearLayout
    private val tvQuestBadge: TextView
    private val progressBar: ProgressBar
    private val btnMinimize: TextView
    private val btnSkip: TextView

    private val contentSection: LinearLayout
    private val tvQuestTitle: TextView
    private val tvWhatItDoes: TextView
    private val tvMissionText: TextView

    private val completionBanner: LinearLayout
    private val tvCompletionText: TextView

    // Minimized Pill
    private val minimizedPill: LinearLayout
    private val tvMinimizedText: TextView

    var onSkipClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null

    private var isMinimized = false
    private var currentAccentColor: Int = Color.parseColor("#4F46E5")

    init {
        // Root container padding
        setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())

        // --- 1. FULL QUEST CARD ---
        mainCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.parseColor("#0F172A"), Color.parseColor("#334155"))
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            elevation = 20 * density
        }

        // Row 1: Header (Badge + Progress + Minimize + Skip)
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvQuestBadge = TextView(context).apply {
            text = "🎯 QUEST 1 OF 13"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 13
            progress = 1
            progressTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
            val lp = LinearLayout.LayoutParams((90 * density).toInt(), (6 * density).toInt()).apply {
                marginEnd = (10 * density).toInt()
            }
            layoutParams = lp
        }

        btnMinimize = TextView(context).apply {
            text = " ▾ "
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            isClickable = true
            setOnClickListener { toggleMinimize() }
        }

        btnSkip = TextView(context).apply {
            text = "✕ Exit"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding((8 * density).toInt(), (2 * density).toInt(), (4 * density).toInt(), (2 * density).toInt())
            isClickable = true
            setOnClickListener { onSkipClicked?.invoke() }
        }

        headerRow.addView(tvQuestBadge)
        headerRow.addView(progressBar)
        headerRow.addView(btnMinimize)
        headerRow.addView(btnSkip)
        mainCard.addView(headerRow)

        // Content Section (Title + Lore + Mission)
        contentSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        tvQuestTitle = TextView(context).apply {
            text = "Navigate the Infinite Canvas"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = (8 * density).toInt()
            }
            layoutParams = lp
        }
        contentSection.addView(tvQuestTitle)

        // Lore Box: "What It Does"
        val loreBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 10 * density
            }
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * density).toInt()
            }
            layoutParams = lp
        }

        val tvLoreLabel = TextView(context).apply {
            text = "💡 WHAT IT DOES"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        tvWhatItDoes = TextView(context).apply {
            text = "Infinite canvas gives you 24,000px of open creative space with no borders or page breaks."
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 11.5f
            setLineSpacing(3f, 1f)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (2 * density).toInt()
            }
            layoutParams = lp
        }
        loreBox.addView(tvLoreLabel)
        loreBox.addView(tvWhatItDoes)
        contentSection.addView(loreBox)

        // Mission Box: "What You Have To Do"
        val missionBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#172554")) // Subtle blue-tinted dark container
                cornerRadius = 10 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#38BDF8"))
            }
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
        }

        val tvMissionLabel = TextView(context).apply {
            text = "👉 YOUR MISSION"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
        }
        tvMissionText = TextView(context).apply {
            text = "Drag with your finger to pan across the board, or pinch to zoom."
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (2 * density).toInt()
            }
            layoutParams = lp
        }
        missionBox.addView(tvMissionLabel)
        missionBox.addView(tvMissionText)
        contentSection.addView(missionBox)

        mainCard.addView(contentSection)

        // Completion Banner (Hidden by default, flashes upon objective completion)
        completionBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#065F46"))
                cornerRadius = 10 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#10B981"))
            }
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
            }
            layoutParams = lp
            visibility = View.GONE
        }

        tvCompletionText = TextView(context).apply {
            text = "🎉 Quest Completed! +100 XP"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        completionBanner.addView(tvCompletionText)
        mainCard.addView(completionBanner)

        addView(mainCard)

        // --- 2. MINIMIZED PILL ---
        minimizedPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 20 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#38BDF8"))
            }
            setPadding((14 * density).toInt(), (6 * density).toInt(), (14 * density).toInt(), (6 * density).toInt())
            elevation = 16 * density
            visibility = View.GONE
            setOnClickListener { toggleMinimize() }
        }

        tvMinimizedText = TextView(context).apply {
            text = "🎯 Quest 1/13: Navigate (Tap to expand) ▾"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        minimizedPill.addView(tvMinimizedText)
        addView(minimizedPill)
    }

    private fun createCardBackground(bgColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 18 * density
            setStroke((1.5f * density).toInt(), strokeColor)
        }
    }

    fun applyTheme(accentColor: Int, isDark: Boolean) {
        currentAccentColor = accentColor
        val bg = if (isDark) Color.parseColor("#0F172A") else Color.parseColor("#1E293B")
        mainCard.background = createCardBackground(bg, accentColor)
        progressBar.progressTintList = ColorStateList.valueOf(accentColor)
        tvQuestBadge.setTextColor(accentColor)
        minimizedPill.background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = 20 * density
            setStroke((1.5f * density).toInt(), accentColor)
        }
    }

    private var pendingCompletionRunnable: Runnable? = null

    fun bindQuest(quest: TutorialQuest, questIndex: Int, totalQuests: Int) {
        pendingCompletionRunnable?.let { removeCallbacks(it); pendingCompletionRunnable = null }
        tvQuestBadge.text = "🎯 QUEST ${questIndex + 1} OF $totalQuests"
        progressBar.max = totalQuests
        progressBar.progress = questIndex + 1
        tvQuestTitle.text = "${quest.icon} ${quest.title}"
        tvWhatItDoes.text = quest.whatItDoes
        tvMissionText.text = quest.missionInstruction
        tvMinimizedText.text = "🎯 Quest ${questIndex + 1}/$totalQuests: ${quest.title} ▾"

        completionBanner.visibility = View.GONE
        contentSection.visibility = View.VISIBLE

        // Animate entrance
        mainCard.scaleX = 0.96f
        mainCard.scaleY = 0.96f
        mainCard.alpha = 0.6f
        mainCard.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    fun showQuestCompleted(message: String, onFinished: () -> Unit) {
        pendingCompletionRunnable?.let { removeCallbacks(it) }
        tvCompletionText.text = "🎉 $message"
        contentSection.visibility = View.GONE
        completionBanner.visibility = View.VISIBLE

        // Celebration Bounce
        completionBanner.scaleX = 0.85f
        completionBanner.scaleY = 0.85f
        completionBanner.alpha = 0f
        completionBanner.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .alpha(1f)
            .setDuration(220)
            .withEndAction {
                completionBanner.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
            .start()

        val r = Runnable {
            pendingCompletionRunnable = null
            onFinished()
        }
        pendingCompletionRunnable = r
        postDelayed(r, 1200)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pendingCompletionRunnable?.let { removeCallbacks(it); pendingCompletionRunnable = null }
    }

    fun showGrandVictory(onFinish: () -> Unit) {
        pendingCompletionRunnable?.let { removeCallbacks(it); pendingCompletionRunnable = null }
        tvQuestBadge.text = "🏆 MASTER LEVEL ACHIEVED"
        progressBar.progress = progressBar.max
        tvQuestTitle.text = "👑 Congratulations! You Mastered NoteApp!"
        tvWhatItDoes.text = "You've successfully completed all 13 training quests: Pan & Zoom, Notes, Checklists, Sticky Notes, Tables, Vector Inking, Connectors, Lasso Selection, Minimap, Search, and 3x Pro Export!"
        tvMissionText.text = "Your playground board is saved and ready. Tap below to begin your creative journey!"

        contentSection.visibility = View.VISIBLE
        completionBanner.visibility = View.GONE

        btnSkip.text = "Finish 🚀"
        btnSkip.setTextColor(Color.parseColor("#38BDF8"))
        btnSkip.textSize = 14f
        btnSkip.typeface = Typeface.DEFAULT_BOLD
        btnSkip.setOnClickListener { onFinish() }
    }

    private fun toggleMinimize() {
        isMinimized = !isMinimized
        if (isMinimized) {
            mainCard.visibility = View.GONE
            minimizedPill.visibility = View.VISIBLE
            minimizedPill.alpha = 0f
            minimizedPill.animate().alpha(1f).setDuration(150).start()
        } else {
            minimizedPill.visibility = View.GONE
            mainCard.visibility = View.VISIBLE
            mainCard.alpha = 0f
            mainCard.animate().alpha(1f).setDuration(180).start()
        }
    }
}
