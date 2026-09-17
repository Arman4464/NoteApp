package com.noteapp.student.canvas

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.noteapp.student.R
import com.noteapp.student.settings.ThemeColors
import com.noteapp.student.util.ColorPicker
import com.noteapp.student.util.ThemedDialog
import kotlin.math.max

/**
 * A draggable / resizable card on the canvas representing:
 * - TEXT: Note card with rich typography
 * - IMAGE: Photo or imported picture
 * - CHECKLIST: Interactive task list with strikethroughs
 * - SHAPE: Sticky note, rounded container, or circle
 * - BOARD: Milanote-style sub-board container card
 * - LINK: Web bookmark card
 */
class NoteBoxView(
    context: Context,
    val data: NoteBoxData,
    private val onMoved: (dx: Float, dy: Float) -> Unit,
    private val onMoveFinished: () -> Unit,
    private val onResized: () -> Unit,
    private val onSelectedForConnect: (NoteBoxView) -> Unit,
    private val onDeleteRequested: (NoteBoxView) -> Unit,
    private val onDuplicateRequested: (NoteBoxView) -> Unit,
    private val onOpenSubBoard: (String) -> Unit,
    private val onBoxTapped: (NoteBoxView) -> Unit,
    private val onTextFocusChanged: () -> Unit,
    private val getScale: () -> Float
) : FrameLayout(context) {

    companion object {
        const val MIN_WIDTH = 140f
        const val MIN_HEIGHT = 100f
    }

    private val contentContainer: View
    private lateinit var checklistContainer: LinearLayout

    private var isSelectedState = false
    private var savedHint: CharSequence? = null
    var themeColors: ThemeColors? = null
        set(value) {
            field = value
            updateBackgroundShape()
            if (data.kind == BoxKind.TEXT || data.kind == BoxKind.SHAPE) {
                (contentContainer as? EditText)?.let { et ->
                    et.setTextColor(resolveTextColor())
                    et.setHintTextColor(if (value?.isDark == true) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
                }
            } else if (data.kind == BoxKind.CHECKLIST) {
                rebuildChecklist()
            }
        }

    init {
        updateBackgroundShape()
        clipToPadding = false
        elevation = 8f

        // Content Container fills 100% of card surface with no solid header taking space
        contentContainer = when (data.kind) {
            BoxKind.TEXT -> buildTextContent()
            BoxKind.IMAGE -> buildImageContent()
            BoxKind.CHECKLIST -> buildChecklistContent()
            BoxKind.SHAPE -> buildShapeContent()
            BoxKind.BOARD -> buildBoardContent()
            BoxKind.LINK -> buildLinkContent()
        }
        val contentParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(contentContainer, contentParams)

        layoutParams = LayoutParams(data.width.toInt(), data.height.toInt())
        x = data.x
        y = data.y

        setOnClickListener {
            onBoxTapped(this)
        }
    }

    fun resolveTextColor(): Int {
        if (data.textColor != Color.parseColor("#111827")) {
            return data.textColor
        }
        return if (themeColors?.isDark == true) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
    }

    private fun getHeaderColor(): Int {
        return when (data.kind) {
            BoxKind.TEXT -> Color.parseColor("#4F46E5")
            BoxKind.IMAGE -> Color.parseColor("#059669")
            BoxKind.CHECKLIST -> Color.parseColor("#0284C7")
            BoxKind.SHAPE -> Color.parseColor("#D97706")
            BoxKind.BOARD -> Color.parseColor("#7C3AED")
            BoxKind.LINK -> Color.parseColor("#2563EB")
        }
    }

    fun updateBackgroundShape() {
        val isGlass = (data.kind == BoxKind.TEXT || data.kind == BoxKind.CHECKLIST)
        val density = context.resources.displayMetrics.density
        val isDark = themeColors?.isDark ?: false

        background = GradientDrawable().apply {
            if (isGlass) {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                orientation = GradientDrawable.Orientation.TOP_BOTTOM

                val isDefaultColor = (data.boxColor == Color.WHITE || data.boxColor == Color.TRANSPARENT)
                if (isDark) {
                    if (isDefaultColor) {
                        // Translucent frosted glass with subtle top specular reflection
                        colors = intArrayOf(
                            Color.argb(45, 255, 255, 255),
                            Color.argb(65, 15, 23, 42)
                        )
                    } else {
                        val r = Color.red(data.boxColor)
                        val g = Color.green(data.boxColor)
                        val b = Color.blue(data.boxColor)
                        colors = intArrayOf(
                            Color.argb(70, r, g, b),
                            Color.argb(35, r, g, b)
                        )
                    }
                } else {
                    if (isDefaultColor) {
                        // Luminous see-through acrylic with specular highlight
                        colors = intArrayOf(
                            Color.argb(135, 255, 255, 255),
                            Color.argb(70, 255, 255, 255)
                        )
                    } else {
                        val r = Color.red(data.boxColor)
                        val g = Color.green(data.boxColor)
                        val b = Color.blue(data.boxColor)
                        colors = intArrayOf(
                            Color.argb(110, r, g, b),
                            Color.argb(55, r, g, b)
                        )
                    }
                }

                // Refraction specular border
                val strokeCol = if (isSelectedState) {
                    themeColors?.accent ?: Color.parseColor("#4F46E5")
                } else {
                    if (isDark) Color.argb(110, 255, 255, 255) else Color.argb(190, 255, 255, 255)
                }
                val strokeW = if (isSelectedState) (3 * density).toInt() else (1.5f * density).toInt()
                setStroke(strokeW, strokeCol)
            } else {
                setColor(data.boxColor)
                when (data.shapeType) {
                    ShapeType.CIRCLE -> {
                        shape = GradientDrawable.OVAL
                    }
                    ShapeType.STICKY_NOTE -> {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 4f * density
                    }
                    ShapeType.ROUNDED_RECT -> {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 16f * density
                    }
                    ShapeType.RECTANGLE -> {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 0f
                    }
                    ShapeType.DIAMOND, ShapeType.STAR, ShapeType.CLOUD, ShapeType.TRIANGLE -> {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 16f * density
                    }
                }
                val strokeCol = if (isSelectedState) {
                    themeColors?.accent ?: Color.parseColor("#4F46E5")
                } else {
                    data.strokeColor
                }
                val strokeW = if (isSelectedState) (3 * density).toInt() else data.strokeWidth.toInt().coerceAtLeast(2)
                setStroke(strokeW, strokeCol)
            }
        }
    }

    // ---------- Content builders ----------

    private fun buildTextContent(): EditText {
        val isDark = themeColors?.isDark ?: false
        val et = EditText(context).apply {
            hint = "Type your note..."
            setHintTextColor(if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.TOP or Gravity.START
            setTextColor(resolveTextColor())
            textSize = data.fontSizeSp
            setPadding(20, 20, 20, 20)
            setText(data.text)
            movementMethod = ScrollingMovementMethod.getInstance()
            typeface = resolveTypeface()
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                data.text = s?.toString() ?: ""
            }
        })
        et.setOnFocusChangeListener { _, _ -> onTextFocusChanged() }
        et.setOnTouchListener { _, event ->
            if (!isSelectedState && event.actionMasked == MotionEvent.ACTION_UP) {
                onBoxTapped(this@NoteBoxView)
            }
            false
        }
        return et
    }

    private fun buildImageContent(): ImageView {
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val path = data.imagePath
            if (path != null) {
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) setImageBitmap(bmp)
            }
        }
    }

    private fun buildChecklistContent(): View {
        val scroll = ScrollView(context).apply {
            clipToPadding = false
            setPadding(0, 0, 0, 16)
        }
        checklistContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 32)
        }
        scroll.addView(checklistContainer)
        rebuildChecklist()
        return scroll
    }

    private fun buildShapeContent(): View {
        return buildTextContent().apply {
            hint = "Add shape note..."
        }
    }

    private fun buildBoardContent(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }
        val icon = TextView(context).apply {
            text = "\uD83D\uDCC2"
            textSize = 36f
            gravity = Gravity.CENTER
        }
        val nameView = TextView(context).apply {
            text = data.targetBoardName ?: data.text.ifBlank { "Sub-Board" }
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1F2937"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }
        val tapHint = TextView(context).apply {
            text = "Tap to open board \u2192"
            textSize = 12f
            setTextColor(Color.parseColor("#7C3AED"))
            gravity = Gravity.CENTER
        }
        layout.addView(icon)
        layout.addView(nameView)
        layout.addView(tapHint)

        layout.setOnClickListener {
            data.targetBoardId?.let { boardId ->
                onOpenSubBoard(boardId)
            }
        }
        return layout
    }

    private fun buildLinkContent(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val linkEdit = EditText(context).apply {
            hint = "https://example.com"
            setText(data.text)
            textSize = 14f
            setTextColor(Color.parseColor("#2563EB"))
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(true)
        }
        linkEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                data.text = s?.toString() ?: ""
            }
        })
        layout.addView(linkEdit)
        return layout
    }

    private fun rebuildChecklist() {
        checklistContainer.removeAllViews()
        val accentCol = themeColors?.accent ?: Color.parseColor("#4F46E5")
        val isDark = themeColors?.isDark ?: false
        val textColor = resolveTextColor()

        for (item in data.checklist) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            val checkBox = CheckBox(context).apply {
                isChecked = item.checked
                buttonTintList = ColorStateList.valueOf(accentCol)
            }
            val itemEdit = EditText(context).apply {
                setText(item.text)
                isSingleLine = true
                setBackgroundColor(Color.TRANSPARENT)
                textSize = data.fontSizeSp
                setTextColor(textColor)
                setHintTextColor(if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
                hint = "Task item..."
                if (item.checked) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            itemEdit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    item.text = s?.toString() ?: ""
                }
            })
            itemEdit.setOnFocusChangeListener { _, _ -> onTextFocusChanged() }
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.checked = isChecked
                itemEdit.paintFlags = if (isChecked) {
                    itemEdit.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    itemEdit.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            }
            val removeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(if (isDark) Color.parseColor("#64748B") else Color.parseColor("#9CA3AF"))
                setPadding(16, 8, 16, 8)
                setOnClickListener {
                    data.checklist.remove(item)
                    rebuildChecklist()
                }
            }
            row.addView(checkBox)
            row.addView(itemEdit)
            row.addView(removeBtn)
            checklistContainer.addView(row)
        }
        val addRow = TextView(context).apply {
            text = "+ Add item"
            setTextColor(accentCol)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            val density = resources.displayMetrics.density
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(if (isDark) Color.argb(30, 255, 255, 255) else Color.argb(20, 79, 70, 229))
                setStroke((1 * density).toInt(), if (isDark) Color.argb(50, 255, 255, 255) else Color.argb(35, 79, 70, 229))
            }
            setOnClickListener {
                data.checklist.add(ChecklistItem())
                rebuildChecklist()
            }
        }
        checklistContainer.addView(addRow)
    }

    /**
     * Resolves typeface using bundled fonts:
     * - Caveat (Handwritten)
     * - Kalam (Calligraphic Ink)
     * - Lora (Literary Serif)
     * - Playfair (Display Serif)
     * - Space Mono (Tech Mono)
     * - Outfit (Modern Geometric Sans)
     */
    fun resolveTypeface(): Typeface {
        val base: Typeface = when (data.fontFamily.lowercase()) {
            "caveat", "cursive", "handwritten" -> {
                try {
                    ResourcesCompat.getFont(context, R.font.caveat) ?: Typeface.create("cursive", Typeface.NORMAL)
                } catch (e: Exception) {
                    Typeface.create("cursive", Typeface.NORMAL)
                }
            }
            "kalam" -> {
                try {
                    ResourcesCompat.getFont(context, R.font.kalam) ?: Typeface.create("cursive", Typeface.NORMAL)
                } catch (e: Exception) {
                    Typeface.create("cursive", Typeface.NORMAL)
                }
            }
            "lora" -> {
                try {
                    ResourcesCompat.getFont(context, R.font.lora) ?: Typeface.SERIF
                } catch (e: Exception) {
                    Typeface.SERIF
                }
            }
            "playfair", "playfair display" -> {
                try {
                    ResourcesCompat.getFont(context, R.font.playfair) ?: Typeface.SERIF
                } catch (e: Exception) {
                    Typeface.SERIF
                }
            }
            "spacemono", "space mono", "mono" -> {
                try {
                    ResourcesCompat.getFont(context, R.font.spacemono) ?: Typeface.MONOSPACE
                } catch (e: Exception) {
                    Typeface.MONOSPACE
                }
            }
            "outfit" -> {
                try {
                    ResourcesCompat.getFont(context, R.font.outfit) ?: Typeface.SANS_SERIF
                } catch (e: Exception) {
                    Typeface.SANS_SERIF
                }
            }
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        val style = when {
            data.bold && data.italic -> Typeface.BOLD_ITALIC
            data.bold -> Typeface.BOLD
            data.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    fun applyMoveDelta(dx: Float, dy: Float) {
        data.x += dx
        data.y += dy
        this.x = data.x
        this.y = data.y
    }

    // ---------- Menu ----------
 
    fun showBoxMenu(anchorView: View? = null) {
        val tc = themeColors ?: ThemeColors.modernClean()
        val title = if (data.text.isNotBlank()) data.text.take(24) else "Card Options"
        val builder = ThemedDialog.Builder(context, tc)
            .setTitle(title)
            .addItem("Change Color", subtitle = "Pick custom card surface color") {
                ColorPicker.show(context, "Card Color", tc) { color -> setBoxColor(color) }
            }
            .addItem("Duplicate", subtitle = "Duplicate this card on canvas") {
                onDuplicateRequested(this)
            }

        if (data.kind == BoxKind.SHAPE) {
            builder.addItem("Sticky Note Shape", subtitle = "Square memo shape", isSelected = (data.shapeType == ShapeType.STICKY_NOTE)) {
                data.shapeType = ShapeType.STICKY_NOTE
                updateBackgroundShape()
            }
            builder.addItem("Rounded Rectangle", subtitle = "Modern rounded card", isSelected = (data.shapeType == ShapeType.ROUNDED_RECT)) {
                data.shapeType = ShapeType.ROUNDED_RECT
                updateBackgroundShape()
            }
            builder.addItem("Circle Shape", subtitle = "Circular bubble node", isSelected = (data.shapeType == ShapeType.CIRCLE)) {
                data.shapeType = ShapeType.CIRCLE
                updateBackgroundShape()
            }
            builder.addItem("Diamond Shape", subtitle = "Decision node", isSelected = (data.shapeType == ShapeType.DIAMOND)) {
                data.shapeType = ShapeType.DIAMOND
                updateBackgroundShape()
            }
            builder.addItem("Star Shape", subtitle = "Highlight star", isSelected = (data.shapeType == ShapeType.STAR)) {
                data.shapeType = ShapeType.STAR
                updateBackgroundShape()
            }
            builder.addItem("Cloud Shape", subtitle = "Thought bubble", isSelected = (data.shapeType == ShapeType.CLOUD)) {
                data.shapeType = ShapeType.CLOUD
                updateBackgroundShape()
            }
            builder.addItem("Triangle Shape", subtitle = "Warning triangle", isSelected = (data.shapeType == ShapeType.TRIANGLE)) {
                data.shapeType = ShapeType.TRIANGLE
                updateBackgroundShape()
            }
        }

        if (data.kind == BoxKind.BOARD) {
            builder.addItem("Open Board", subtitle = "Enter nested workspace") {
                data.targetBoardId?.let { onOpenSubBoard(it) }
            }
        }

        builder.addItem("Delete Card", subtitle = "Remove card from canvas") {
            onDeleteRequested(this)
        }

        builder.setNegativeButton("Cancel")
        builder.show()
    }

    // ---------- Styling ----------

    fun setBoxColor(color: Int) {
        data.boxColor = color
        updateBackgroundShape()
    }

    fun setTextColor(color: Int) {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.textColor = color
        (contentContainer as? EditText)?.setTextColor(color)
    }

    fun setTextBgColor(color: Int) {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.textBgColor = color
        contentContainer.setBackgroundColor(color)
    }

    fun setFontSize(sp: Float) {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        val clamped = sp.coerceIn(10f, 48f)
        data.fontSizeSp = clamped
        (contentContainer as? EditText)?.textSize = clamped
    }

    fun setFontFamily(family: String) {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.fontFamily = family
        (contentContainer as? EditText)?.typeface = resolveTypeface()
    }

    fun cycleFontFamily() {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.fontFamily = when (data.fontFamily.lowercase()) {
            "outfit" -> "caveat"
            "caveat", "cursive", "handwritten" -> "kalam"
            "kalam" -> "lora"
            "lora" -> "playfair"
            "playfair" -> "spacemono"
            "spacemono", "mono" -> "serif"
            "serif" -> "sans-serif"
            else -> "outfit"
        }
        (contentContainer as? EditText)?.typeface = resolveTypeface()
    }

    fun toggleBold() {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.bold = !data.bold
        (contentContainer as? EditText)?.typeface = resolveTypeface()
    }

    fun toggleItalic() {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.italic = !data.italic
        (contentContainer as? EditText)?.typeface = resolveTypeface()
    }

    // ---------- Selection & Highlight ----------

    fun setSelectedState(selected: Boolean) {
        isSelectedState = selected
        updateBackgroundShape()
    }

    fun focusTextInput() {
        (contentContainer as? EditText)?.let { et ->
            et.requestFocus()
            et.setSelection(et.text.length)
            et.post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun isBoxSelected(): Boolean = isSelectedState

    fun setConnectMode(enabled: Boolean) {
        isClickable = enabled
        if (enabled) {
            setOnClickListener { onSelectedForConnect(this) }
        } else {
            setOnClickListener { onBoxTapped(this) }
        }
    }

    fun setHighlighted(highlighted: Boolean) {
        val bg = background as? GradientDrawable ?: return
        bg.setStroke(
            if (highlighted) 8 else data.strokeWidth.toInt().coerceAtLeast(2),
            if (highlighted) Color.parseColor("#F59E0B") else data.strokeColor
        )
    }

    // ---------- Export ----------

    fun setExportMode(enabled: Boolean) {
        if (data.kind == BoxKind.TEXT || data.kind == BoxKind.SHAPE) {
            val et = contentContainer as? EditText
            if (enabled) {
                savedHint = et?.hint
                et?.hint = null
            } else {
                et?.hint = savedHint
            }
        }
    }

    fun centerX() = data.x + data.width / 2f
    fun centerY() = data.y + data.height / 2f
}
