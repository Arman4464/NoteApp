package com.noteapp.student.tutorial

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.View
import com.noteapp.student.R
import com.noteapp.student.canvas.BoxKind
import com.noteapp.student.settings.AppSettings

enum class QuestId {
    NAVIGATE_CANVAS,
    CREATE_TEXT_NOTE,
    SELECT_AND_MOVE,
    EDIT_NOTE_FORMAT,
    INSERT_ELEMENT,
    DRAW_INK,
    ERASE_INK,
    CONNECT_CARDS,
    MARQUEE_MULTI_SELECT,
    OPEN_MINIMAP,
    SEARCH_BOARD,
    CUSTOMIZE_THEME,
    EXPORT_SHARE
}

data class TutorialQuest(
    val id: QuestId,
    val title: String,
    val icon: String,
    val whatItDoes: String,
    val missionInstruction: String,
    val targetViewId: Int? = null,
    val completionMessage: String
)

class GameTutorialManager(
    private val activity: Activity,
    private val appSettings: AppSettings,
    private val onTutorialFinished: () -> Unit
) {

    private val quests = listOf(
        TutorialQuest(
            id = QuestId.NAVIGATE_CANVAS,
            title = "Navigate Infinite Canvas",
            icon = "🧭",
            whatItDoes = "Your canvas is an endless 24,000px creative workspace with no borders or page boundaries. Zoom from 15% to 400% smoothly.",
            missionInstruction = "Drag with your finger to pan across the board, or pinch with two fingers to zoom.",
            targetViewId = R.id.zoomHud,
            completionMessage = "Canvas Navigation Mastered! Infinite freedom unlocked."
        ),
        TutorialQuest(
            id = QuestId.CREATE_TEXT_NOTE,
            title = "Deploy Your First Note",
            icon = "📝",
            whatItDoes = "Notes are the building blocks for thoughts, quotes, and research on your canvas.",
            missionInstruction = "Tap the 'T' (Text) tool on the bottom dock to place a note.",
            targetViewId = R.id.toolText,
            completionMessage = "First Note Deployed! Ready for your thoughts."
        ),
        TutorialQuest(
            id = QuestId.SELECT_AND_MOVE,
            title = "Select & Move Cards",
            icon = "👆",
            whatItDoes = "Single-tapping any card selects it with a bounding transform frame without summoning the keyboard. Dragging moves it freely.",
            missionInstruction = "Single-tap your note to select it, then drag it across the board.",
            targetViewId = null,
            completionMessage = "Card Positioned! Intuitive single-tap control mastered."
        ),
        TutorialQuest(
            id = QuestId.EDIT_NOTE_FORMAT,
            title = "Double-Tap Edit & Toolbar",
            icon = "✏️",
            whatItDoes = "Double-tapping a card opens full text editing mode and summons the formatting toolbar with fonts, bold, italic, colors, and highlights.",
            missionInstruction = "Double-tap your note to type text and reveal the text formatting toolbar.",
            targetViewId = null,
            completionMessage = "Text Formatting Mastered! Rich typography enabled."
        ),
        TutorialQuest(
            id = QuestId.INSERT_ELEMENT,
            title = "Insert Sticky Notes & Elements",
            icon = "⚡",
            whatItDoes = "NoteApp includes colorful Sticky Notes, interactive Checklists, multi-column Tables, and geometric Shapes for visual mapping.",
            missionInstruction = "Tap the Elements (+) icon on the bottom dock and insert a Sticky Note or Checklist.",
            targetViewId = R.id.toolElements,
            completionMessage = "Element Added! Your board is coming alive."
        ),
        TutorialQuest(
            id = QuestId.DRAW_INK,
            title = "Freehand Vector Inking",
            icon = "🖌️",
            whatItDoes = "Vector drawing overlays allow smooth, lag-free sketching, margin annotations, and diagramming with customizable colors & strokes.",
            missionInstruction = "Tap the Pen icon on the dock, pick a color, and sketch on the canvas.",
            targetViewId = R.id.toolDraw,
            completionMessage = "Ink Stroke Drawn! Fluid sketching unlocked."
        ),
        TutorialQuest(
            id = QuestId.ERASE_INK,
            title = "Precision Vector Eraser",
            icon = "🧹",
            whatItDoes = "The eraser cleans up strokes without touching your text notes, cards, or connectors.",
            missionInstruction = "Tap the Eraser icon on the dock and swipe across an ink stroke to erase it.",
            targetViewId = R.id.toolEraser,
            completionMessage = "Strokes Erased! Clean canvas control."
        ),
        TutorialQuest(
            id = QuestId.CONNECT_CARDS,
            title = "Link Cards with Arrows",
            icon = "🔗",
            whatItDoes = "Connectors dynamically link cards with smart cubic bezier curves and arrowheads that adapt as cards move.",
            missionInstruction = "Tap the Connector icon on the dock, then tap two cards to link them.",
            targetViewId = R.id.toolConnect,
            completionMessage = "Cards Linked! Mind mapping & flowcharts ready."
        ),
        TutorialQuest(
            id = QuestId.MARQUEE_MULTI_SELECT,
            title = "Marquee Lasso Selection",
            icon = "📐",
            whatItDoes = "Lasso-select multiple cards to batch move, recolor, duplicate, or delete them in one tap.",
            missionInstruction = "In Select mode, drag across two or more cards on the empty canvas to lasso them.",
            targetViewId = null,
            completionMessage = "Cards Lassoed! Batch productivity unlocked."
        ),
        TutorialQuest(
            id = QuestId.OPEN_MINIMAP,
            title = "Radar Minimap Navigation",
            icon = "🗺️",
            whatItDoes = "The minimap gives you an instant bird's-eye radar of your entire board. Tapping anywhere teleports you instantly.",
            missionInstruction = "Tap the Minimap icon on the ceiling top bar and tap the radar to teleport.",
            targetViewId = R.id.btnMinimap,
            completionMessage = "Teleported! Never lose your place on an infinite board."
        ),
        TutorialQuest(
            id = QuestId.SEARCH_BOARD,
            title = "Find & Jump Ceiling Search",
            icon = "🔍",
            whatItDoes = "Instantly index and search all card text across your infinite canvas with automatic camera jump & highlight.",
            missionInstruction = "Tap the Search icon on the ceiling top bar and search for any text.",
            targetViewId = R.id.btnCanvasSearch,
            completionMessage = "Found & Jumped! Instant retrieval power."
        ),
        TutorialQuest(
            id = QuestId.CUSTOMIZE_THEME,
            title = "Board Sub-Themes",
            icon = "🎭",
            whatItDoes = "Every board can have its own visual theme with 8 dual-mode palettes (Oceanic, Forest, Neon, Parchment, Obsidian, etc.).",
            missionInstruction = "Tap the 3-dots Menu on the top bar and tap 'Board Theme'.",
            targetViewId = R.id.btnMenu,
            completionMessage = "Sub-Theme Explored! Aesthetic customization at your fingertips."
        ),
        TutorialQuest(
            id = QuestId.EXPORT_SHARE,
            title = "Pro Export & Instant Sharing",
            icon = "🚀",
            whatItDoes = "Export high-resolution 3x oversampled PNGs, crisp PDF documents, or shareable .noteapp project archives.",
            missionInstruction = "Tap the Download or Share button on the ceiling top bar.",
            targetViewId = R.id.btnDownload,
            completionMessage = "Export Ready! You are now a verified NoteApp Master!"
        )
    )

    var isTutorialActive = false
        private set

    var currentQuestIndex = 0
        private set

    private var hudView: GameTutorialHudView? = null
    private var targetPulseAnimator: ObjectAnimator? = null
    private var currentPulsingView: View? = null
    private var isCompletingQuest = false

    fun startTutorial(hud: GameTutorialHudView) {
        hudView = hud
        isTutorialActive = true
        isCompletingQuest = false
        currentQuestIndex = 0

        hud.visibility = View.VISIBLE
        hud.alpha = 0f
        hud.translationY = -30f
        hud.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .start()

        hud.onSkipClicked = {
            exitTutorial()
        }

        loadQuest(currentQuestIndex)
    }

    private fun loadQuest(index: Int) {
        if (index >= quests.size) {
            finishTutorial()
            return
        }

        val quest = quests[index]
        hudView?.bindQuest(quest, index, quests.size)

        // Highlight target button if specified
        stopTargetPulse()
        if (quest.targetViewId != null) {
            val target = activity.findViewById<View>(quest.targetViewId)
            if (target != null) {
                startTargetPulse(target)
            }
        }
    }

    private fun startTargetPulse(view: View) {
        currentPulsingView = view
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.18f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.18f, 1.0f)
        targetPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopTargetPulse() {
        targetPulseAnimator?.cancel()
        targetPulseAnimator = null
        currentPulsingView?.scaleX = 1.0f
        currentPulsingView?.scaleY = 1.0f
        currentPulsingView = null
    }

    fun completeCurrentQuest(targetQuestId: QuestId) {
        if (!isTutorialActive) return
        if (isCompletingQuest) return
        if (currentQuestIndex >= quests.size) return

        val activeQuest = quests[currentQuestIndex]
        if (activeQuest.id != targetQuestId) return

        isCompletingQuest = true

        // Provide tactile confirmation
        try {
            hudView?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } catch (_: Exception) {}

        stopTargetPulse()

        hudView?.showQuestCompleted(activeQuest.completionMessage) {
            currentQuestIndex++
            if (currentQuestIndex < quests.size) {
                loadQuest(currentQuestIndex)
                isCompletingQuest = false
            } else {
                finishTutorial()
            }
        }
    }

    private fun finishTutorial() {
        stopTargetPulse()
        isCompletingQuest = false
        appSettings.tutorialCompleted = true
        hudView?.showGrandVictory {
            exitTutorial()
            onTutorialFinished()
        }
    }

    fun exitTutorial() {
        stopTargetPulse()
        isTutorialActive = false
        isCompletingQuest = false
        hudView?.animate()
            ?.alpha(0f)
            ?.translationY(-30f)
            ?.setDuration(200)
            ?.withEndAction {
                hudView?.visibility = View.GONE
            }
            ?.start()
    }

    // --- Action Notification Hooks ---

    fun notifyCanvasMovedOrZoomed() {
        completeCurrentQuest(QuestId.NAVIGATE_CANVAS)
    }

    fun notifyBoxAdded(kind: BoxKind) {
        if (kind == BoxKind.TEXT) {
            completeCurrentQuest(QuestId.CREATE_TEXT_NOTE)
        } else if (kind == BoxKind.SHAPE || kind == BoxKind.CHECKLIST || kind == BoxKind.TABLE || kind == BoxKind.LINK || kind == BoxKind.BOARD) {
            completeCurrentQuest(QuestId.INSERT_ELEMENT)
        }
    }

    fun notifyBoxSelectedAndMoved() {
        completeCurrentQuest(QuestId.SELECT_AND_MOVE)
    }

    fun notifyTextFocused() {
        completeCurrentQuest(QuestId.EDIT_NOTE_FORMAT)
    }

    fun notifyDrawingFinished() {
        completeCurrentQuest(QuestId.DRAW_INK)
    }

    fun notifyEraserFinished() {
        completeCurrentQuest(QuestId.ERASE_INK)
    }

    fun notifyConnectorCreated() {
        completeCurrentQuest(QuestId.CONNECT_CARDS)
    }

    fun notifyMultiSelect(count: Int) {
        if (count >= 2) {
            completeCurrentQuest(QuestId.MARQUEE_MULTI_SELECT)
        }
    }

    fun notifyMinimapOpened() {
        completeCurrentQuest(QuestId.OPEN_MINIMAP)
    }

    fun notifySearchOpened() {
        completeCurrentQuest(QuestId.SEARCH_BOARD)
    }

    fun notifyThemeOpened() {
        completeCurrentQuest(QuestId.CUSTOMIZE_THEME)
    }

    fun notifyExportOpened() {
        completeCurrentQuest(QuestId.EXPORT_SHARE)
    }
}
