package com.noteapp.student.canvas

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.noteapp.student.R
import com.noteapp.student.settings.ThemeColors
import com.noteapp.student.util.ColorPicker
import com.noteapp.student.util.ThemedDialog
import kotlin.math.hypot
import kotlin.math.max

/**
 * A draggable / resizable card on the canvas representing:
 * - TEXT: Note card with rich typography
 * - IMAGE: Photo or imported picture
 * - CHECKLIST: Interactive task list with strikethroughs
 * - TABLE: Interactive table grid with customizable indexing
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
    private lateinit var tableContainer: LinearLayout
    private var tableControlsLayout: View? = null

    private var isSelectedState = false
    private var savedHint: CharSequence? = null

    // Text editing state (double tap to edit, single tap to drag/select)
    private var isTextEditingMode = false
    private var activeEditText: EditText? = null

    var onColorChanged: ((box: NoteBoxView, oldColor: Int, newColor: Int) -> Unit)? = null
    var onContentChanged: (() -> Unit)? = null

    // Geometric vector shape rendering
    private val shapeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shapeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shapePath = Path()

    // Touch drag state when card is selected
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDraggingSelf = false

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
            } else if (data.kind == BoxKind.TABLE) {
                rebuildTable()
            }
        }

    var onBringToFrontRequested: ((NoteBoxView) -> Unit)? = null
    var onSendToBackRequested: ((NoteBoxView) -> Unit)? = null

    private var lastTapTime = 0L

    init {
        setWillNotDraw(false)
        updateBackgroundShape()
        clipToPadding = false
        elevation = 0f

        // Content Container fills 100% of card surface with no solid header taking space
        contentContainer = when (data.kind) {
            BoxKind.TEXT -> buildTextContent()
            BoxKind.IMAGE -> buildImageContent()
            BoxKind.CHECKLIST -> buildChecklistContent()
            BoxKind.TABLE -> buildTableContent()
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

    fun containsPoint(wx: Float, wy: Float): Boolean {
        return wx >= data.x && wx <= data.x + data.width &&
               wy >= data.y && wy <= data.y + data.height
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isSelectedState) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = ev.rawX
                    touchStartY = ev.rawY
                    isDraggingSelf = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(ev.rawX - touchStartX)
                    val dy = Math.abs(ev.rawY - touchStartY)
                    val density = resources.displayMetrics.density
                    if (hypot(dx.toDouble(), dy.toDouble()) > 8 * density) {
                        onBoxTapped(this)
                        if (!data.isLocked) {
                            isDraggingSelf = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    return true
                }
            }
            return true
        }

        // When selected, for text cards only intercept if not currently editing text
        if (data.kind == BoxKind.TEXT || (data.kind == BoxKind.SHAPE && data.shapeType == ShapeType.STICKY_NOTE)) {
            if (!isTextEditingMode) {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = ev.rawX
                        touchStartY = ev.rawY
                        isDraggingSelf = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(ev.rawX - touchStartX)
                        val dy = Math.abs(ev.rawY - touchStartY)
                        val density = resources.displayMetrics.density
                        if (hypot(dx.toDouble(), dy.toDouble()) > 8 * density) {
                            if (!data.isLocked) {
                                isDraggingSelf = true
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        return true
                    }
                }
                return true
            }
        }

        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isTextEditingMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDraggingSelf = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - touchStartX)
                    val dy = Math.abs(event.rawY - touchStartY)
                    val density = resources.displayMetrics.density
                    if (!isDraggingSelf && hypot(dx.toDouble(), dy.toDouble()) > 8 * density) {
                        if (!isSelectedState) {
                            onBoxTapped(this)
                        }
                        if (!data.isLocked) {
                            isDraggingSelf = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    if (isDraggingSelf && !data.isLocked) {
                        val moveDx = (event.rawX - touchStartX) / getScale()
                        val moveDy = (event.rawY - touchStartY) / getScale()
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        onMoved(moveDx, moveDy)
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingSelf) {
                        isDraggingSelf = false
                        onMoveFinished()
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return true
                    } else {
                        val now = android.os.SystemClock.uptimeMillis()
                        val isDoubleTap = (now - lastTapTime < 350)
                        lastTapTime = now

                        if (isDoubleTap) {
                            when (data.kind) {
                                BoxKind.TEXT -> enterTextEditing()
                                BoxKind.SHAPE -> {
                                    if (data.shapeType == ShapeType.STICKY_NOTE) enterTextEditing()
                                    else showBoxMenu()
                                }
                                BoxKind.BOARD -> data.targetBoardId?.let { onOpenSubBoard(it) }
                                BoxKind.TABLE -> onBoxTapped(this)
                                BoxKind.IMAGE -> showBoxMenu()
                                else -> showBoxMenu()
                            }
                        } else {
                            onBoxTapped(this)
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingSelf) {
                        isDraggingSelf = false
                        onMoveFinished()
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun enterTextEditing() {
        val et = activeEditText ?: return
        isTextEditingMode = true
        et.isFocusable = true
        et.isFocusableInTouchMode = true
        et.isCursorVisible = true
        et.requestFocus()
        et.setSelection(et.text.length)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        et.postDelayed({
            et.requestFocus()
            imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 80)
    }

    fun exitTextEditing() {
        if (!isTextEditingMode) return
        isTextEditingMode = false
        val et = activeEditText ?: return
        et.isFocusable = false
        et.isFocusableInTouchMode = false
        et.isCursorVisible = false
        et.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun resolveTextColor(): Int {
        if (data.textColor != Color.parseColor("#111827") && data.textColor != 0) {
            return data.textColor
        }
        return themeColors?.defaultTextColor ?: if (themeColors?.isDark == true) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
    }

    private fun getHeaderColor(): Int {
        return when (data.kind) {
            BoxKind.TEXT -> Color.parseColor("#4F46E5")
            BoxKind.IMAGE -> Color.parseColor("#059669")
            BoxKind.CHECKLIST -> Color.parseColor("#0284C7")
            BoxKind.TABLE -> Color.parseColor("#3B82F6")
            BoxKind.SHAPE -> Color.parseColor("#D97706")
            BoxKind.BOARD -> Color.parseColor("#7C3AED")
            BoxKind.LINK -> Color.parseColor("#2563EB")
        }
    }

    fun updateBackgroundShape() {
        if (data.kind == BoxKind.SHAPE) {
            background = null
            invalidate()
            return
        }

        val isGlass = (data.kind == BoxKind.TEXT || data.kind == BoxKind.CHECKLIST || data.kind == BoxKind.TABLE)
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
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.kind == BoxKind.SHAPE) {
            drawVectorShape(canvas)
        }
    }

    private fun drawVectorShape(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val strokeCol = if (isSelectedState) {
            themeColors?.accent ?: Color.parseColor("#4F46E5")
        } else {
            data.strokeColor
        }
        val strokeW = if (isSelectedState) 3f * density else data.strokeWidth.coerceAtLeast(2f)

        shapeFillPaint.color = data.boxColor
        shapeStrokePaint.color = strokeCol
        shapeStrokePaint.strokeWidth = strokeW

        val w = width.toFloat()
        val h = height.toFloat()
        val halfW = strokeW / 2f

        shapePath.reset()
        when (data.shapeType) {
            ShapeType.CIRCLE -> {
                canvas.drawOval(halfW, halfW, w - halfW, h - halfW, shapeFillPaint)
                canvas.drawOval(halfW, halfW, w - halfW, h - halfW, shapeStrokePaint)
                return
            }
            ShapeType.STICKY_NOTE -> {
                val foldSize = 20f * density
                shapePath.moveTo(halfW + 4f * density, halfW)
                shapePath.lineTo(w - halfW - 4f * density, halfW)
                shapePath.quadTo(w - halfW, halfW, w - halfW, halfW + 4f * density)
                shapePath.lineTo(w - halfW, h - halfW - foldSize)
                shapePath.lineTo(w - halfW - foldSize, h - halfW)
                shapePath.lineTo(halfW + 4f * density, h - halfW)
                shapePath.quadTo(halfW, h - halfW, halfW, h - halfW - 4f * density)
                shapePath.lineTo(halfW, halfW + 4f * density)
                shapePath.quadTo(halfW, halfW, halfW + 4f * density, halfW)
                shapePath.close()

                canvas.drawPath(shapePath, shapeFillPaint)
                canvas.drawPath(shapePath, shapeStrokePaint)

                // Fold flap in bottom right
                val foldPath = Path().apply {
                    moveTo(w - halfW - foldSize, h - halfW)
                    lineTo(w - halfW - foldSize, h - halfW - foldSize)
                    lineTo(w - halfW, h - halfW - foldSize)
                    close()
                }
                val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(45, 0, 0, 0)
                }
                canvas.drawPath(foldPath, foldPaint)
                canvas.drawPath(foldPath, shapeStrokePaint)
                return
            }
            ShapeType.ROUNDED_RECT -> {
                val rad = 16f * density
                canvas.drawRoundRect(halfW, halfW, w - halfW, h - halfW, rad, rad, shapeFillPaint)
                canvas.drawRoundRect(halfW, halfW, w - halfW, h - halfW, rad, rad, shapeStrokePaint)
                return
            }
            ShapeType.RECTANGLE -> {
                canvas.drawRect(halfW, halfW, w - halfW, h - halfW, shapeFillPaint)
                canvas.drawRect(halfW, halfW, w - halfW, h - halfW, shapeStrokePaint)
                return
            }
            ShapeType.DIAMOND -> {
                shapePath.moveTo(w / 2f, halfW)
                shapePath.lineTo(w - halfW, h / 2f)
                shapePath.lineTo(w / 2f, h - halfW)
                shapePath.lineTo(halfW, h / 2f)
                shapePath.close()
            }
            ShapeType.TRIANGLE -> {
                shapePath.moveTo(w / 2f, halfW)
                shapePath.lineTo(w - halfW, h - halfW)
                shapePath.lineTo(halfW, h - halfW)
                shapePath.close()
            }
            ShapeType.STAR -> {
                val cx = w / 2f
                val cy = h / 2f
                val outerR = kotlin.math.min(w, h) / 2f - halfW
                val innerR = outerR * 0.45f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outerR else innerR
                    val angle = Math.toRadians((i * 36.0 - 90.0))
                    val px = cx + (r * Math.cos(angle)).toFloat()
                    val py = cy + (r * Math.sin(angle)).toFloat()
                    if (i == 0) shapePath.moveTo(px, py) else shapePath.lineTo(px, py)
                }
                shapePath.close()
            }
            ShapeType.CLOUD -> {
                val left = halfW + 6f * density
                val right = w - halfW - 6f * density
                val top = halfW + 10f * density
                val bottom = h - halfW - 10f * density
                val cw = right - left
                val ch = bottom - top

                shapePath.moveTo(left + cw * 0.2f, bottom)
                shapePath.lineTo(left + cw * 0.8f, bottom)
                shapePath.cubicTo(right, bottom, right + 4f * density, top + ch * 0.6f, left + cw * 0.82f, top + ch * 0.45f)
                shapePath.cubicTo(right, top - 4f * density, left + cw * 0.55f, top - 6f * density, left + cw * 0.5f, top + ch * 0.15f)
                shapePath.cubicTo(left + cw * 0.4f, top - 6f * density, left + cw * 0.15f, top, left + cw * 0.2f, top + ch * 0.4f)
                shapePath.cubicTo(left - 4f * density, top + ch * 0.55f, left - 2f * density, bottom, left + cw * 0.2f, bottom)
                shapePath.close()
            }
        }
        canvas.drawPath(shapePath, shapeFillPaint)
        canvas.drawPath(shapePath, shapeStrokePaint)
    }

    // ---------- Content builders ----------

    private fun buildTextContent(): EditText {
        val density = resources.displayMetrics.density
        val isDark = themeColors?.isDark ?: false
        val et = EditText(context).apply {
            hint = "Type your note..."
            setHintTextColor(if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.TOP or Gravity.START
            setTextColor(resolveTextColor())
            textSize = data.fontSizeSp
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setText(data.text)
            movementMethod = ScrollingMovementMethod.getInstance()
            typeface = resolveTypeface()
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
        }
        activeEditText = et
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                data.text = s?.toString() ?: ""
                onContentChanged?.invoke()
            }
        })
        et.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                isTextEditingMode = false
                et.isFocusable = false
                et.isFocusableInTouchMode = false
                et.isCursorVisible = false
            }
            onTextFocusChanged()
        }

        return et
    }

    private fun buildImageContent(): View {
        val path = data.imagePath
        val bmp = if (path != null) BitmapFactory.decodeFile(path) else null
        if (bmp != null) {
            return ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setImageBitmap(bmp)
            }
        }
        val density = resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(Color.argb(30, 148, 163, 184))
                setStroke((1.5f * density).toInt(), Color.parseColor("#94A3B8"), 12f, 8f)
            }
            val iv = ImageView(context).apply {
                setImageResource(R.drawable.ic_image)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
            }
            val tv = TextView(context).apply {
                text = "Missing Image\n(Tap to delete)"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, (6 * density).toInt(), 0, 0)
            }
            addView(iv)
            addView(tv)
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

    private fun buildTableContent(): View {
        val density = resources.displayMetrics.density
        val vScroll = ScrollView(context).apply {
            clipToPadding = false
            isFillViewport = true
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        val hScroll = HorizontalScrollView(context).apply {
            clipToPadding = false
            isFillViewport = true
            setPadding(0, 0, 0, 0)
        }
        tableContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())
        }
        hScroll.addView(tableContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        vScroll.addView(hScroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        rebuildTable()
        return vScroll
    }

    fun rebuildTable() {
        if (!::tableContainer.isInitialized) return
        tableContainer.removeAllViews()

        val table = data.tableData ?: TableData().also { data.tableData = it }
        val density = resources.displayMetrics.density
        val isDark = themeColors?.isDark ?: false
        val textColor = resolveTextColor()
        val accentCol = themeColors?.accent ?: Color.parseColor("#4F46E5")
        val borderColor = if (isDark) Color.parseColor("#334155") else Color.parseColor("#CBD5E1")
        val headerBg = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9")
        val headerTextColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")

        // Ensure cell grid matches rows x cols
        while (table.cells.size < table.rows) {
            table.cells.add(MutableList(table.cols) { "" })
        }
        while (table.cells.size > table.rows) {
            table.cells.removeAt(table.cells.size - 1)
        }
        for (row in table.cells) {
            while (row.size < table.cols) row.add("")
            while (row.size > table.cols) row.removeAt(row.size - 1)
        }

        val showColHeaders = (table.colHeaders != TableIndexStyle.NONE)
        val showRowHeaders = (table.rowHeaders != TableIndexStyle.NONE)

        val minColWidth = (84 * density).toInt()
        val minRowHeight = (40 * density).toInt()

        val headerColWidth = if (showRowHeaders) (44 * density).toInt() else 0
        val headerRowHeight = if (showColHeaders) (36 * density).toInt() else 0
        val controlsH = if (isSelectedState) (44 * density).toInt() else 0

        val padH = (32 * density).toInt()
        val padV = (42 * density).toInt()

        val reqMinW = padH + headerColWidth + (table.cols * minColWidth)
        val reqMinH = padV + headerRowHeight + (table.rows * minRowHeight) + controlsH

        if (data.width < reqMinW) {
            data.width = reqMinW.toFloat()
            val lp = layoutParams
            if (lp != null) {
                lp.width = data.width.toInt()
                layoutParams = lp
            }
        }
        if (data.height < reqMinH) {
            data.height = reqMinH.toFloat()
            val lp = layoutParams
            if (lp != null) {
                lp.height = data.height.toInt()
                layoutParams = lp
            }
        }

        val availableW = (data.width - padH - headerColWidth).toInt()
        val availableH = (data.height - padV - headerRowHeight - controlsH).toInt()

        val dynamicColWidth = if (table.cols > 0) max(minColWidth, availableW / table.cols) else minColWidth
        val dynamicRowHeight = if (table.rows > 0) max(minRowHeight, availableH / table.rows) else minRowHeight

        // 1. Column Header Row (if enabled)
        if (showColHeaders) {
            val colHeaderRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (showRowHeaders) {
                val corner = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(headerColWidth, headerRowHeight)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(headerBg)
                        setStroke((1 * density).toInt(), borderColor)
                    }
                }
                colHeaderRow.addView(corner)
            }
            for (c in 0 until table.cols) {
                val colLabel = if (c < table.customColLabels.size && table.customColLabels[c].isNotEmpty()) {
                    table.customColLabels[c]
                } else ""
                val defaultColLabel = getColHeaderLabel(c, table.colHeaders)

                val th = EditText(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dynamicColWidth, headerRowHeight)
                    setText(colLabel)
                    hint = defaultColLabel
                    textSize = 12f
                    includeFontPadding = false
                    setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(headerTextColor)
                    setHintTextColor(if (isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
                    gravity = Gravity.CENTER
                    isSingleLine = false
                    maxLines = 2
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(headerBg)
                        setStroke((1 * density).toInt(), borderColor)
                    }
                }
                th.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        while (table.customColLabels.size <= c) table.customColLabels.add("")
                        table.customColLabels[c] = s?.toString() ?: ""
                        onContentChanged?.invoke()
                    }
                })
                th.setOnFocusChangeListener { _, _ -> onTextFocusChanged() }
                colHeaderRow.addView(th)
            }
            tableContainer.addView(colHeaderRow)
        }

        // 2. Data Rows
        for (r in 0 until table.rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dynamicRowHeight
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            if (showRowHeaders) {
                val rowLabel = if (r < table.customRowLabels.size && table.customRowLabels[r].isNotEmpty()) {
                    table.customRowLabels[r]
                } else ""
                val defaultRowLabel = getRowHeaderLabel(r, table.rowHeaders)

                val rh = EditText(context).apply {
                    layoutParams = LinearLayout.LayoutParams(headerColWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                    minimumHeight = dynamicRowHeight
                    setText(rowLabel)
                    hint = defaultRowLabel
                    textSize = 12f
                    includeFontPadding = false
                    setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(headerTextColor)
                    setHintTextColor(if (isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
                    gravity = Gravity.CENTER
                    isSingleLine = false
                    maxLines = 3
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(headerBg)
                        setStroke((1 * density).toInt(), borderColor)
                    }
                }
                rh.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        while (table.customRowLabels.size <= r) table.customRowLabels.add("")
                        table.customRowLabels[r] = s?.toString() ?: ""
                        onContentChanged?.invoke()
                    }
                })
                rh.setOnFocusChangeListener { _, _ -> onTextFocusChanged() }
                rowLayout.addView(rh)
            }

            for (c in 0 until table.cols) {
                val cellEdit = EditText(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dynamicColWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                    minimumHeight = dynamicRowHeight
                    setText(table.cells[r][c])
                    textSize = data.fontSizeSp.coerceIn(11f, 18f)
                    includeFontPadding = false
                    setTextColor(textColor)
                    setHintTextColor(if (isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
                    hint = "..."
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                    isSingleLine = false
                    maxLines = 10
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    setHorizontallyScrolling(false)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.TRANSPARENT)
                        setStroke((1 * density).toInt(), borderColor)
                    }
                }
                cellEdit.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (r < table.cells.size && c < table.cells[r].size) {
                            table.cells[r][c] = s?.toString() ?: ""
                            onContentChanged?.invoke()
                        }
                    }
                })
                cellEdit.setOnFocusChangeListener { _, _ -> onTextFocusChanged() }
                rowLayout.addView(cellEdit)
            }
            tableContainer.addView(rowLayout)
        }

        // 3. Quick Table Row/Col Modification Controls (Visible only when table card is selected)
        val controlsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
            visibility = if (isSelectedState) View.VISIBLE else View.GONE
        }
        tableControlsLayout = controlsLayout
        val btnAddRow = TextView(context).apply {
            text = "+ Row"
            setTextColor(accentCol)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(if (isDark) Color.argb(30, 255, 255, 255) else Color.argb(20, 79, 70, 229))
            }
            setOnClickListener {
                table.rows++
                table.cells.add(MutableList(table.cols) { "" })
                val checkMinH = padV + headerRowHeight + (table.rows * minRowHeight) + controlsH
                if (data.height < checkMinH) {
                    data.height = checkMinH.toFloat()
                    val lp = layoutParams
                    if (lp != null) {
                        lp.height = data.height.toInt()
                        layoutParams = lp
                    }
                    onResized()
                }
                rebuildTable()
                onContentChanged?.invoke()
            }
        }
        val btnAddCol = TextView(context).apply {
            text = "+ Column"
            setTextColor(accentCol)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            layoutParams = lp
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(if (isDark) Color.argb(30, 255, 255, 255) else Color.argb(20, 79, 70, 229))
            }
            setOnClickListener {
                table.cols++
                for (row in table.cells) {
                    row.add("")
                }
                val checkMinW = padH + headerColWidth + (table.cols * minColWidth)
                if (data.width < checkMinW) {
                    data.width = checkMinW.toFloat()
                    val currentLp = layoutParams
                    if (currentLp != null) {
                        currentLp.width = data.width.toInt()
                        layoutParams = currentLp
                    }
                    onResized()
                }
                rebuildTable()
                onContentChanged?.invoke()
            }
        }
        controlsLayout.addView(btnAddRow)
        controlsLayout.addView(btnAddCol)

        if (table.rows > 1) {
            val btnRemoveRow = TextView(context).apply {
                text = "- Row"
                setTextColor(if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (8 * density).toInt()
                }
                layoutParams = lp
                setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(if (isDark) Color.argb(20, 255, 255, 255) else Color.argb(12, 0, 0, 0))
                }
                setOnClickListener {
                    if (table.rows > 1) {
                        table.rows--
                        if (table.cells.isNotEmpty()) table.cells.removeAt(table.cells.size - 1)
                        if (table.customRowLabels.size > table.rows) table.customRowLabels.removeAt(table.rows)
                        rebuildTable()
                        onContentChanged?.invoke()
                    }
                }
            }
            controlsLayout.addView(btnRemoveRow)
        }

        if (table.cols > 1) {
            val btnRemoveCol = TextView(context).apply {
                text = "- Col"
                setTextColor(if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (8 * density).toInt()
                }
                layoutParams = lp
                setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(if (isDark) Color.argb(20, 255, 255, 255) else Color.argb(12, 0, 0, 0))
                }
                setOnClickListener {
                    if (table.cols > 1) {
                        table.cols--
                        for (row in table.cells) {
                            if (row.isNotEmpty()) row.removeAt(row.size - 1)
                        }
                        if (table.customColLabels.size > table.cols) table.customColLabels.removeAt(table.cols)
                        rebuildTable()
                        onContentChanged?.invoke()
                    }
                }
            }
            controlsLayout.addView(btnRemoveCol)
        }

        tableContainer.addView(controlsLayout)
    }

    fun updateTableDimensions() {
        if (!::tableContainer.isInitialized || tableContainer.childCount == 0) {
            rebuildTable()
            return
        }
        val table = data.tableData ?: return
        val density = resources.displayMetrics.density
        val minColWidth = (84 * density).toInt()
        val minRowHeight = (40 * density).toInt()

        val showColHeaders = (table.colHeaders != TableIndexStyle.NONE)
        val showRowHeaders = (table.rowHeaders != TableIndexStyle.NONE)

        val headerColWidth = if (showRowHeaders) (44 * density).toInt() else 0
        val headerRowHeight = if (showColHeaders) (36 * density).toInt() else 0
        val controlsH = if (isSelectedState) (44 * density).toInt() else 0

        val padH = (32 * density).toInt()
        val padV = (42 * density).toInt()

        val availableW = (data.width - padH - headerColWidth).toInt()
        val availableH = (data.height - padV - headerRowHeight - controlsH).toInt()

        val dynamicColWidth = if (table.cols > 0) max(minColWidth, availableW / table.cols) else minColWidth
        val dynamicRowHeight = if (table.rows > 0) max(minRowHeight, availableH / table.rows) else minRowHeight

        var childIdx = 0
        if (showColHeaders && childIdx < tableContainer.childCount) {
            val colHeaderRow = tableContainer.getChildAt(childIdx) as? LinearLayout
            if (colHeaderRow != null) {
                val startCol = if (showRowHeaders) 1 else 0
                for (c in startCol until colHeaderRow.childCount) {
                    val th = colHeaderRow.getChildAt(c)
                    val lp = th.layoutParams
                    if (lp.width != dynamicColWidth) {
                        lp.width = dynamicColWidth
                        th.layoutParams = lp
                    }
                }
            }
            childIdx++
        }

        for (r in 0 until table.rows) {
            if (childIdx >= tableContainer.childCount) break
            val rowLayout = tableContainer.getChildAt(childIdx) as? LinearLayout
            if (rowLayout != null) {
                if (rowLayout.minimumHeight != dynamicRowHeight) {
                    rowLayout.minimumHeight = dynamicRowHeight
                }
                val startCol = if (showRowHeaders) 1 else 0
                if (showRowHeaders && rowLayout.childCount > 0) {
                    val rh = rowLayout.getChildAt(0)
                    if (rh.minimumHeight != dynamicRowHeight) {
                        rh.minimumHeight = dynamicRowHeight
                    }
                }
                for (c in startCol until rowLayout.childCount) {
                    val cell = rowLayout.getChildAt(c)
                    val lp = cell.layoutParams
                    var changed = false
                    if (lp.width != dynamicColWidth) {
                        lp.width = dynamicColWidth
                        changed = true
                    }
                    if (cell.minimumHeight != dynamicRowHeight) {
                        cell.minimumHeight = dynamicRowHeight
                        changed = true
                    }
                    if (changed) {
                        cell.layoutParams = lp
                    }
                }
            }
            childIdx++
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (data.kind == BoxKind.TABLE && (w != oldw || h != oldh) && oldw > 0 && oldh > 0) {
            updateTableDimensions()
        }
    }

    private fun toLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun toRoman(num: Int): String {
        val vals = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val syms = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var n = num.coerceAtLeast(1)
        val sb = StringBuilder()
        for (i in vals.indices) {
            while (n >= vals[i]) {
                n -= vals[i]
                sb.append(syms[i])
            }
        }
        return if (sb.isEmpty()) "I" else sb.toString()
    }

    private fun getColHeaderLabel(col: Int, style: TableIndexStyle): String {
        return when (style) {
            TableIndexStyle.NUMBERS -> (col + 1).toString()
            TableIndexStyle.LETTERS -> toLetter(col)
            TableIndexStyle.ROMAN -> toRoman(col + 1)
            TableIndexStyle.NONE -> ""
        }
    }

    private fun getRowHeaderLabel(row: Int, style: TableIndexStyle): String {
        return when (style) {
            TableIndexStyle.NUMBERS -> (row + 1).toString()
            TableIndexStyle.LETTERS -> toLetter(row)
            TableIndexStyle.ROMAN -> toRoman(row + 1)
            TableIndexStyle.NONE -> ""
        }
    }

    private fun buildShapeContent(): View {
        return buildTextContent().apply {
            hint = "Add shape note..."
            gravity = Gravity.CENTER
            val density = resources.displayMetrics.density
            when (data.shapeType) {
                ShapeType.DIAMOND -> setPadding((36 * density).toInt(), (36 * density).toInt(), (36 * density).toInt(), (36 * density).toInt())
                ShapeType.TRIANGLE -> setPadding((32 * density).toInt(), (44 * density).toInt(), (32 * density).toInt(), (20 * density).toInt())
                ShapeType.STAR -> setPadding((36 * density).toInt(), (36 * density).toInt(), (36 * density).toInt(), (36 * density).toInt())
                ShapeType.CLOUD -> setPadding((28 * density).toInt(), (28 * density).toInt(), (28 * density).toInt(), (28 * density).toInt())
                else -> setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            }
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
                onContentChanged?.invoke()
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
                    onContentChanged?.invoke()
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
                onContentChanged?.invoke()
            }
            val removeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(if (isDark) Color.parseColor("#64748B") else Color.parseColor("#9CA3AF"))
                setPadding(16, 8, 16, 8)
                setOnClickListener {
                    data.checklist.remove(item)
                    rebuildChecklist()
                    onContentChanged?.invoke()
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
                onContentChanged?.invoke()
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
 
    fun showBoxMenu() {
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
            .addItem(if (data.isLocked) "Unlock Position" else "Lock Position", subtitle = if (data.isLocked) "Allow moving and resizing" else "Fix position and prevent accidental movement") {
                data.isLocked = !data.isLocked
                onContentChanged?.invoke()
                onBoxTapped(this)
            }
            .addItem("Bring to Front", subtitle = "Move card above other elements") {
                onBringToFrontRequested?.invoke(this)
            }
            .addItem("Send to Back", subtitle = "Move card behind other elements") {
                onSendToBackRequested?.invoke(this)
            }

        if (data.kind == BoxKind.SHAPE) {
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

        if (data.kind == BoxKind.TABLE) {
            val table = data.tableData ?: TableData().also { data.tableData = it }
            val density = resources.displayMetrics.density
            val minColWidth = (84 * density).toInt()
            val minRowHeight = (40 * density).toInt()
            val showColH = (table.colHeaders != TableIndexStyle.NONE)
            val showRowH = (table.rowHeaders != TableIndexStyle.NONE)
            val headerColW = if (showRowH) (44 * density).toInt() else 0
            val headerRowH = if (showColH) (36 * density).toInt() else 0
            val padH = (32 * density).toInt()
            val padV = (42 * density).toInt()
            val controlsH = (44 * density).toInt()

            builder.addItem("Add Row", subtitle = "Insert new row at bottom") {
                table.rows++
                table.cells.add(MutableList(table.cols) { "" })
                val checkMinH = padV + headerRowH + (table.rows * minRowHeight) + controlsH
                if (data.height < checkMinH) {
                    data.height = checkMinH.toFloat()
                    val lp = layoutParams
                    if (lp != null) {
                        lp.height = data.height.toInt()
                        layoutParams = lp
                    }
                    onResized()
                }
                rebuildTable()
                onContentChanged?.invoke()
            }
            builder.addItem("Add Column", subtitle = "Insert new column at right") {
                table.cols++
                for (r in table.cells) r.add("")
                val checkMinW = padH + headerColW + (table.cols * minColWidth)
                if (data.width < checkMinW) {
                    data.width = checkMinW.toFloat()
                    val lp = layoutParams
                    if (lp != null) {
                        lp.width = data.width.toInt()
                        layoutParams = lp
                    }
                    onResized()
                }
                rebuildTable()
                onContentChanged?.invoke()
            }
            if (table.rows > 1) {
                builder.addItem("Delete Last Row", subtitle = "Remove bottom row") {
                    table.rows--
                    if (table.cells.isNotEmpty()) table.cells.removeAt(table.cells.size - 1)
                    if (table.customRowLabels.size > table.rows) table.customRowLabels.removeAt(table.rows)
                    rebuildTable()
                    onContentChanged?.invoke()
                }
            }
            if (table.cols > 1) {
                builder.addItem("Delete Last Column", subtitle = "Remove rightmost column") {
                    table.cols--
                    for (r in table.cells) if (r.isNotEmpty()) r.removeAt(r.size - 1)
                    if (table.customColLabels.size > table.cols) table.customColLabels.removeAt(table.cols)
                    rebuildTable()
                    onContentChanged?.invoke()
                }
            }
        }

        builder.addItem("Delete Card", subtitle = "Remove card from canvas") {
            onDeleteRequested(this)
        }

        builder.setNegativeButton("Cancel")
        builder.show()
    }

    // ---------- Styling ----------

    fun setBoxColor(color: Int, recordUndo: Boolean = true) {
        val old = data.boxColor
        data.boxColor = color
        updateBackgroundShape()
        invalidate()
        if (recordUndo && old != color) {
            onColorChanged?.invoke(this, old, color)
        }
        onContentChanged?.invoke()
    }

    fun setTextColor(color: Int) {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.textColor = color
        (contentContainer as? EditText)?.setTextColor(color)
        onContentChanged?.invoke()
    }

    fun setTextBgColor(color: Int) {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.textBgColor = color
        contentContainer.setBackgroundColor(color)
        onContentChanged?.invoke()
    }

    fun setFontSize(sp: Float) {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        val clamped = sp.coerceIn(10f, 48f)
        data.fontSizeSp = clamped
        (contentContainer as? EditText)?.textSize = clamped
        onContentChanged?.invoke()
    }

    fun setFontFamily(family: String) {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.fontFamily = family
        (contentContainer as? EditText)?.typeface = resolveTypeface()
        onContentChanged?.invoke()
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
        onContentChanged?.invoke()
    }

    fun toggleBold() {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.bold = !data.bold
        (contentContainer as? EditText)?.typeface = resolveTypeface()
        onContentChanged?.invoke()
    }

    fun toggleItalic() {
        if (data.kind != BoxKind.TEXT && data.kind != BoxKind.SHAPE) return
        data.italic = !data.italic
        (contentContainer as? EditText)?.typeface = resolveTypeface()
    }

    // ---------- Selection & Highlight ----------

    fun setSelectedState(selected: Boolean) {
        isSelectedState = selected
        if (!selected) {
            exitTextEditing()
        }
        updateBackgroundShape()
        if (data.kind == BoxKind.TABLE) {
            tableControlsLayout?.visibility = if (selected) View.VISIBLE else View.GONE
            updateTableDimensions()
        }
        invalidate()
    }

    fun focusTextInput() {
        enterTextEditing()
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
