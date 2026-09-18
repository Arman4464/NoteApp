package com.noteapp.student.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import com.noteapp.student.settings.GridStyle
import com.noteapp.student.settings.ThemeColors
import com.noteapp.student.settings.ThemeManager
import com.noteapp.student.settings.ThemeType
import com.noteapp.student.undo.CanvasCommand
import com.noteapp.student.undo.UndoRedoManager
import kotlin.math.max
import kotlin.math.min

/**
 * Flagship infinite canvas surface supporting:
 * - Multi-selection (marquee/lasso & multi-box tap)
 * - Group moving & group styling
 * - Full Undo/Redo command history
 * - Freehand inking & eraser
 * - Directional connector links
 * - Scaled dynamic dot grid background
 */
enum class EraserMode {
    INK,
    ARROWS,
    ALL
}

class InfiniteCanvasView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    companion object {
        const val WORLD_SIZE = 24000f
        const val MIN_SCALE = 0.15f
        const val MAX_SCALE = 4.0f
    }

    val contentLayer: FrameLayout = FrameLayout(context)
    val connectorOverlay: ConnectorOverlayView = ConnectorOverlayView(context).apply {
        renderForegroundOnly = false
    }
    val fgConnectorOverlay: ConnectorOverlayView = ConnectorOverlayView(context).apply {
        renderForegroundOnly = true
    }
    val bgDrawingOverlay: DrawingOverlayView = DrawingOverlayView(context)
    val fgDrawingOverlay: DrawingOverlayView = DrawingOverlayView(context)

    var eraserMode: EraserMode = EraserMode.INK
    var eraserRadius: Float = 28f
    private val activeStrokeErasedConnectors = mutableListOf<ConnectorData>()

    var isDrawingOnForeground: Boolean = false
        set(value) {
            field = value
            updateToolState()
            if (value) {
                maintainLayerOrder()
            }
        }
    val drawingOverlay: DrawingOverlayView get() = if (isDrawingOnForeground) fgDrawingOverlay else bgDrawingOverlay
    val marqueeOverlay: MarqueeOverlayView = MarqueeOverlayView(context)
    val selectionOverlay: SelectionTransformOverlayView = SelectionTransformOverlayView(context)

    fun maintainLayerOrder() {
        fgConnectorOverlay.bringToFront()
        fgDrawingOverlay.bringToFront()
        selectionOverlay.bringToFront()
        marqueeOverlay.bringToFront()
    }

    fun invalidateConnectors() {
        connectorOverlay.invalidate()
        fgConnectorOverlay.invalidate()
    }

    private val boxes = mutableListOf<NoteBoxView>()
    private val connectors = mutableListOf<ConnectorData>()
    val selectedBoxes = mutableSetOf<NoteBoxView>()

    var activeTool: CanvasTool = CanvasTool.SELECT
        set(value) {
            field = value
            updateToolState()
        }

    var undoRedoManager: UndoRedoManager? = null

    var scale = 1f
        private set

    private var firstSelectedForConnect: NoteBoxView? = null
    var onConnectorCreated: (() -> Unit)? = null
    var onTextFocusEvent: (() -> Unit)? = null
    var onSelectionChanged: ((selectedCount: Int) -> Unit)? = null
    var onOpenSubBoardRequested: ((boardId: String) -> Unit)? = null
    var onBackgroundTapped: (() -> Unit)? = null
    var onContentChanged: (() -> Unit)? = null
    var onBoxAddedListener: ((BoxKind) -> Unit)? = null
    var onBoxMovedListener: (() -> Unit)? = null
    var onCanvasPanZoomListener: (() -> Unit)? = null
    var onStrokeFinishedListener: (() -> Unit)? = null
    var onEraserFinishedListener: (() -> Unit)? = null
    var defaultConnectorStyle: String = "arrow"
    var defaultConnectorColor: Int = Color.parseColor("#6366F1")
    var isFreeArrowMode: Boolean = false
    var selectedTailStyle: String = "none"
    var selectedHeadStyle: String = "triangle"
    var isArrowForeground: Boolean = false
    private var freeArrowStartX = 0f
    private var freeArrowStartY = 0f
    private var isDrawingFreeArrow = false

    // Panning & Gestures
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isPanning = false

    // Marquee Box Selection
    private var isMarqueeDragging = false
    private var marqueeStartX = 0f
    private var marqueeStartY = 0f
    private val marqueeRect = RectF()

    // Move command tracking
    private val moveStartPositions = mutableMapOf<String, Pair<Float, Float>>()

    // Dotted Canvas Grid Paint
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        style = Paint.Style.FILL
    }
    private val lineGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    var gridStyle: GridStyle = GridStyle.DOTS
        set(value) {
            field = value
            invalidate()
        }
    var gridSnap: Boolean = false
    var onScaleChanged: ((scale: Float) -> Unit)? = null
    var themeColors: ThemeColors = ThemeManager.getThemeColors(ThemeType.MODERN_CLEAN)
        private set

    private val autoColors = listOf(
        Color.parseColor("#FFFFFF"),
        Color.parseColor("#FEF3C7"),
        Color.parseColor("#DBEAFE"),
        Color.parseColor("#DCFCE7"),
        Color.parseColor("#FCE7F3"),
        Color.parseColor("#EDE9FE")
    )
    private var colorCursor = 0

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scale
                var newScale = scale * detector.scaleFactor
                newScale = max(MIN_SCALE, min(MAX_SCALE, newScale))
                val focusX = detector.focusX
                val focusY = detector.focusY
                val contentX = (focusX - contentLayer.translationX) / oldScale
                val contentY = (focusY - contentLayer.translationY) / oldScale
                contentLayer.translationX = focusX - contentX * newScale
                contentLayer.translationY = focusY - contentY * newScale
                scale = newScale
                contentLayer.scaleX = scale
                contentLayer.scaleY = scale
                onScaleChanged?.invoke(scale)
                onCanvasPanZoomListener?.invoke()
                invalidate() // Redraw grid with new scale
                return true
            }
        }
    )

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false

        addView(contentLayer, LayoutParams(WORLD_SIZE.toInt(), WORLD_SIZE.toInt()))
        contentLayer.clipChildren = false
        contentLayer.pivotX = 0f
        contentLayer.pivotY = 0f

        // Layer 1: Connectors (under cards)
        contentLayer.addView(
            connectorOverlay,
            LayoutParams(WORLD_SIZE.toInt(), WORLD_SIZE.toInt())
        )
        connectorOverlay.boxProvider = { boxes }
        connectorOverlay.connectorProvider = { connectors }
        connectorOverlay.renderForegroundOnly = false

        // Layer 2: Freehand drawing strokes (Background layer - under cards)
        contentLayer.addView(
            bgDrawingOverlay,
            LayoutParams(WORLD_SIZE.toInt(), WORLD_SIZE.toInt())
        )
        bgDrawingOverlay.onStrokeFinished = { stroke ->
            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() { bgDrawingOverlay.addStroke(stroke) }
                override fun undo() { bgDrawingOverlay.removeStroke(stroke.id) }
            })
            onStrokeFinishedListener?.invoke()
        }
        bgDrawingOverlay.onStrokesErased = { erased ->
            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() {
                    for (s in erased) bgDrawingOverlay.removeStroke(s.id)
                }
                override fun undo() {
                    for (s in erased) bgDrawingOverlay.addStroke(s)
                }
            })
            onEraserFinishedListener?.invoke()
        }

        // Layer 3: Connectors (Foreground layer - over cards)
        contentLayer.addView(
            fgConnectorOverlay,
            LayoutParams(WORLD_SIZE.toInt(), WORLD_SIZE.toInt())
        )
        fgConnectorOverlay.boxProvider = { boxes }
        fgConnectorOverlay.connectorProvider = { connectors }
        fgConnectorOverlay.renderForegroundOnly = true

        // Layer 4: Freehand drawing strokes (Foreground layer - over cards)
        contentLayer.addView(
            fgDrawingOverlay,
            LayoutParams(WORLD_SIZE.toInt(), WORLD_SIZE.toInt())
        )
        fgDrawingOverlay.onStrokeFinished = { stroke ->
            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() { fgDrawingOverlay.addStroke(stroke) }
                override fun undo() { fgDrawingOverlay.removeStroke(stroke.id) }
            })
            onStrokeFinishedListener?.invoke()
        }
        fgDrawingOverlay.onStrokesErased = { erased ->
            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() {
                    for (s in erased) fgDrawingOverlay.removeStroke(s.id)
                }
                override fun undo() {
                    for (s in erased) fgDrawingOverlay.addStroke(s)
                }
            })
            onEraserFinishedListener?.invoke()
        }

        // Layer 4: MS Paint 8-handle Selection and Transform Overlay
        contentLayer.addView(
            selectionOverlay,
            LayoutParams(WORLD_SIZE.toInt(), WORLD_SIZE.toInt())
        )
        selectionOverlay.onBoxMoved = { dx, dy -> handleBoxMoved(dx, dy) }
        selectionOverlay.onBoxMoveFinished = { handleBoxMoveFinished() }
        selectionOverlay.onBoxResized = {
            invalidateConnectors()
            handleBoxResized()
        }
        selectionOverlay.onBoxResizeFinished = { box, oldX, oldY, oldW, oldH ->
            val finalX = box.data.x
            val finalY = box.data.y
            val finalW = box.data.width
            val finalH = box.data.height
            if (oldX != finalX || oldY != finalY || oldW != finalW || oldH != finalH) {
                undoRedoManager?.record(object : CanvasCommand {
                    override fun execute() {
                        box.data.x = finalX
                        box.data.y = finalY
                        box.data.width = finalW
                        box.data.height = finalH
                        box.x = finalX
                        box.y = finalY
                        val lp = box.layoutParams
                        lp.width = finalW.toInt()
                        lp.height = finalH.toInt()
                        box.layoutParams = lp
                        selectionOverlay.invalidate()
                        invalidateConnectors()
                    }
                    override fun undo() {
                        box.data.x = oldX
                        box.data.y = oldY
                        box.data.width = oldW
                        box.data.height = oldH
                        box.x = oldX
                        box.y = oldY
                        val lp = box.layoutParams
                        lp.width = oldW.toInt()
                        lp.height = oldH.toInt()
                        box.layoutParams = lp
                        selectionOverlay.invalidate()
                        invalidateConnectors()
                    }
                })
            }
        }
        selectionOverlay.onMenuRequested = { box ->
            box.showBoxMenu()
        }

        // Layer 5: Selection marquee overlay
        contentLayer.addView(
            marqueeOverlay,
            LayoutParams(WORLD_SIZE.toInt(), WORLD_SIZE.toInt())
        )

        post {
            contentLayer.translationX = width / 2f - WORLD_SIZE / 2f
            contentLayer.translationY = height / 2f - WORLD_SIZE / 2f
            invalidate()
        }
    }

    fun applyTheme(colors: ThemeColors) {
        themeColors = colors
        boxes.forEach { it.themeColors = colors }
        setBackgroundColor(colors.canvasBg)
        dotPaint.color = colors.gridDot
        lineGridPaint.color = colors.gridDot
        selectionOverlay.applyTheme(colors)
        marqueeOverlay.applyTheme(colors.accent)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gridStyle == GridStyle.NONE) return

        val density = resources.displayMetrics.density
        val baseStep = 32f * density
        val step = baseStep * scale
        if (step < 12f) return // Avoid rendering too fine a grid when zoomed way out

        val startX = (contentLayer.translationX % step + step) % step
        val startY = (contentLayer.translationY % step + step) % step

        if (gridStyle == GridStyle.DOTS) {
            val dotRadius = (1.5f * density).coerceAtLeast(1.0f)
            dotPaint.alpha = if (scale < 0.5f) 70 else 120

            var x = startX
            while (x < width) {
                var y = startY
                while (y < height) {
                    canvas.drawCircle(x, y, dotRadius, dotPaint)
                    y += step
                }
                x += step
            }
        } else if (gridStyle == GridStyle.LINES) {
            lineGridPaint.alpha = if (scale < 0.5f) 30 else 55

            var x = startX
            while (x < width) {
                canvas.drawLine(x, 0f, x, height.toFloat(), lineGridPaint)
                x += step
            }
            var y = startY
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, lineGridPaint)
                y += step
            }
        }
    }

    private fun updateToolState() {
        val isDrawing = (activeTool == CanvasTool.DRAW)
        val isEraser = (activeTool == CanvasTool.ERASER)
        bgDrawingOverlay.isDrawingEnabled = isDrawing && !isDrawingOnForeground
        bgDrawingOverlay.isEraserMode = isEraser
        fgDrawingOverlay.isDrawingEnabled = isDrawing && isDrawingOnForeground
        fgDrawingOverlay.isEraserMode = isEraser

        val isConnect = (activeTool == CanvasTool.CONNECT)
        firstSelectedForConnect?.setHighlighted(false)
        firstSelectedForConnect = null
        boxes.forEach { it.setConnectMode(isConnect && !isFreeArrowMode) }

        if (activeTool != CanvasTool.SELECT) {
            clearSelection()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // When drawing or erasing, route directly to onTouchEvent so that child cards and edit texts
        // cannot intercept or block freehand ink strokes!
        if (activeTool == CanvasTool.DRAW || activeTool == CanvasTool.ERASER) {
            return onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Multi-touch gestures (pinch to zoom) are always intercepted by canvas
        if (ev.pointerCount >= 2) return true
        if (activeTool == CanvasTool.PAN) return true
        if (activeTool == CanvasTool.DRAW || activeTool == CanvasTool.ERASER) return true
        return false
    }

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasPannedSignificant = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true

        val contentX = (event.x - contentLayer.translationX) / scale
        val contentY = (event.y - contentLayer.translationY) / scale

        // Freehand drawing / erasing
        if (activeTool == CanvasTool.DRAW || activeTool == CanvasTool.ERASER) {
            if (activeTool == CanvasTool.DRAW) {
                val activeOverlay = if (isDrawingOnForeground) fgDrawingOverlay else bgDrawingOverlay
                activeOverlay.handleDrawingTouchEvent(event, contentX, contentY)
                return true
            }

            // CanvasTool.ERASER
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                activeStrokeErasedConnectors.clear()
            }

            if (eraserMode == EraserMode.INK || eraserMode == EraserMode.ALL) {
                bgDrawingOverlay.handleDrawingTouchEvent(event, contentX, contentY)
                fgDrawingOverlay.handleDrawingTouchEvent(event, contentX, contentY)
            }

            if (eraserMode == EraserMode.ARROWS || eraserMode == EraserMode.ALL) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val hit = eraseConnectorsAt(contentX, contentY, radius = eraserRadius)
                    if (hit.isNotEmpty()) {
                        activeStrokeErasedConnectors.addAll(hit)
                    }
                }
            }

            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (activeStrokeErasedConnectors.isNotEmpty()) {
                    val toUndo = activeStrokeErasedConnectors.toList()
                    activeStrokeErasedConnectors.clear()
                    undoRedoManager?.record(object : CanvasCommand {
                        override fun execute() {
                            connectors.removeAll(toUndo)
                            invalidateConnectors()
                            onContentChanged?.invoke()
                        }
                        override fun undo() {
                            connectors.addAll(toUndo)
                            invalidateConnectors()
                            onContentChanged?.invoke()
                        }
                    })
                    onEraserFinishedListener?.invoke()
                    onContentChanged?.invoke()
                }
            }
            return true
        }

        // Free-floating Arrow Drawing on Canvas
        if (activeTool == CanvasTool.CONNECT && isFreeArrowMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    freeArrowStartX = contentX
                    freeArrowStartY = contentY
                    isDrawingFreeArrow = true
                    val preview = ConnectorData(
                        startX = freeArrowStartX,
                        startY = freeArrowStartY,
                        endX = contentX,
                        endY = contentY,
                        headStyle = selectedHeadStyle,
                        tailStyle = selectedTailStyle,
                        color = defaultConnectorColor,
                        strokeWidth = 5f,
                        isForeground = isArrowForeground
                    )
                    connectorOverlay.previewArrow = preview
                    fgConnectorOverlay.previewArrow = preview
                    invalidateConnectors()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDrawingFreeArrow) {
                        connectorOverlay.previewArrow?.let {
                            it.endX = contentX
                            it.endY = contentY
                        }
                        fgConnectorOverlay.previewArrow?.let {
                            it.endX = contentX
                            it.endY = contentY
                        }
                        invalidateConnectors()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDrawingFreeArrow) {
                        isDrawingFreeArrow = false
                        connectorOverlay.previewArrow = null
                        fgConnectorOverlay.previewArrow = null
                        invalidateConnectors()
                        val dist = kotlin.math.hypot(
                            (contentX - freeArrowStartX).toDouble(),
                            (contentY - freeArrowStartY).toDouble()
                        ).toFloat()
                        if (dist > 15f) {
                            val newArrow = ConnectorData(
                                startX = freeArrowStartX,
                                startY = freeArrowStartY,
                                endX = contentX,
                                endY = contentY,
                                headStyle = selectedHeadStyle,
                                tailStyle = selectedTailStyle,
                                color = defaultConnectorColor,
                                strokeWidth = 5f,
                                isForeground = isArrowForeground
                            )
                            connectors.add(newArrow)
                            invalidateConnectors()
                            undoRedoManager?.record(object : CanvasCommand {
                                override fun execute() {
                                    if (!connectors.contains(newArrow)) connectors.add(newArrow)
                                    invalidateConnectors()
                                }
                                override fun undo() {
                                    connectors.remove(newArrow)
                                    invalidateConnectors()
                                }
                            })
                            onConnectorCreated?.invoke()
                            onContentChanged?.invoke()
                        }
                        return true
                    }
                }
            }
            return true
        }

        // Natural Navigation & 1-finger canvas panning:
        // 1-finger drag on background pans canvas. Tap on background deselects all cards.
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                isPanning = true
                lastPanX = event.x
                lastPanY = event.y
                initialTouchX = event.x
                initialTouchY = event.y
                hasPannedSignificant = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPanning) {
                    val dx = event.x - lastPanX
                    val dy = event.y - lastPanY
                    if (Math.hypot((event.x - initialTouchX).toDouble(), (event.y - initialTouchY).toDouble()) > 10.0) {
                        hasPannedSignificant = true
                    }
                    contentLayer.translationX += dx
                    contentLayer.translationY += dy
                    lastPanX = event.x
                    lastPanY = event.y
                    invalidate()
                    onScaleChanged?.invoke(scale)
                    if (hasPannedSignificant) {
                        onCanvasPanZoomListener?.invoke()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                if (isPanning && !hasPannedSignificant && event.actionMasked == MotionEvent.ACTION_UP) {
                    // Tap on empty background -> deselect all cards
                    clearSelection()
                    onBackgroundTapped?.invoke()
                }
                isPanning = false
            }
        }
        return true
    }

    // ---------- Box Factory & Operations ----------

    fun addBox(
        kind: BoxKind = BoxKind.TEXT,
        shapeType: ShapeType = ShapeType.ROUNDED_RECT,
        imagePath: String? = null,
        targetBoardId: String? = null,
        targetBoardName: String? = null,
        tableData: TableData? = null,
        customWidth: Float? = null,
        customHeight: Float? = null
    ): NoteBoxView {
        val density = resources.displayMetrics.density
        val topInsetPx = 64f * density
        val bottomInsetPx = 72f * density
        val visibleCenterYPx = (topInsetPx + (height - bottomInsetPx)) / 2f
        val viewportCenterContentX = (width / 2f - contentLayer.translationX) / scale
        val viewportCenterContentY = (visibleCenterYPx - contentLayer.translationY) / scale

        val defaultW = customWidth ?: when (kind) {
            BoxKind.IMAGE -> 240f
            BoxKind.BOARD -> 200f
            BoxKind.TABLE -> 320f
            BoxKind.SHAPE -> if (shapeType == ShapeType.CIRCLE) 200f else 220f
            else -> 260f
        }
        val defaultH = customHeight ?: when (kind) {
            BoxKind.IMAGE -> 240f
            BoxKind.BOARD -> 150f
            BoxKind.TABLE -> 200f
            BoxKind.SHAPE -> if (shapeType == ShapeType.CIRCLE) 200f else 180f
            else -> 170f
        }

        val color = if (kind == BoxKind.SHAPE && shapeType == ShapeType.STICKY_NOTE) {
            Color.parseColor("#FEF08A") // Warm sticky note yellow
        } else {
            themeColors.cardDefaultBg
        }

        val data = NoteBoxData(
            x = viewportCenterContentX - defaultW / 2f,
            y = viewportCenterContentY - defaultH / 2f,
            width = defaultW,
            height = defaultH,
            kind = kind,
            shapeType = shapeType,
            targetBoardId = targetBoardId,
            targetBoardName = targetBoardName,
            imagePath = imagePath,
            boxColor = color,
            textColor = themeColors.defaultTextColor,
            tableData = tableData
        )

        val box = addBoxFromData(data)
        clearSelection()
        selectedBoxes.add(box)
        box.setSelectedState(true)
        selectionOverlay.targetBox = box
        selectionOverlay.bringToFront()
        marqueeOverlay.bringToFront()
        selectionOverlay.invalidate()
        onSelectionChanged?.invoke(selectedBoxes.size)
        onBoxAddedListener?.invoke(kind)

        if (kind == BoxKind.TEXT || kind == BoxKind.SHAPE) {
            box.post { box.focusTextInput() }
        }

        // Record Undo
        undoRedoManager?.record(object : CanvasCommand {
            override fun execute() {
                if (!boxes.contains(box)) {
                    boxes.add(box)
                    contentLayer.addView(box)
                    maintainLayerOrder()
                    invalidateConnectors()
                }
            }
            override fun undo() {
                removeBoxInternal(box)
            }
        })

        return box
    }

    fun addBoxFromData(data: NoteBoxData): NoteBoxView {
        val box = NoteBoxView(
            context = context,
            data = data,
            onMoved = { dx, dy -> handleBoxMoved(dx, dy) },
            onMoveFinished = { handleBoxMoveFinished() },
            onResized = { handleBoxResized() },
            onSelectedForConnect = { handleConnectSelection(it) },
            onDeleteRequested = { deleteSingleBox(it) },
            onDuplicateRequested = { duplicateSingleBox(it) },
            onOpenSubBoard = { boardId -> onOpenSubBoardRequested?.invoke(boardId) },
            onBoxTapped = { handleBoxTapped(it) },
            onTextFocusChanged = { onTextFocusEvent?.invoke() },
            getScale = { scale }
        )
        box.onColorChanged = { b, oldColor, newColor ->
            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() {
                    b.setBoxColor(newColor, recordUndo = false)
                }
                override fun undo() {
                    b.setBoxColor(oldColor, recordUndo = false)
                }
            })
        }
        box.onContentChanged = { onContentChanged?.invoke() }
        boxes.add(box)
        contentLayer.addView(box)
        maintainLayerOrder()
        box.themeColors = themeColors
        box.setConnectMode(activeTool == CanvasTool.CONNECT)
        return box
    }

    private fun handleBoxTapped(box: NoteBoxView) {
        if (activeTool == CanvasTool.CONNECT) {
            handleConnectSelection(box)
            return
        }
        // Bring tapped box to front in contentLayer and end of boxes list
        boxes.remove(box)
        boxes.add(box)
        box.bringToFront()
        maintainLayerOrder()

        if (!selectedBoxes.contains(box)) {
            clearSelection()
            selectedBoxes.add(box)
            box.setSelectedState(true)
        }
        selectionOverlay.targetBox = box
        selectionOverlay.invalidate()
        onSelectionChanged?.invoke(selectedBoxes.size)
    }

    // ---------- Group Moving ----------

    private fun handleBoxMoved(dx: Float, dy: Float) {
        if (moveStartPositions.isEmpty()) {
            for (b in selectedBoxes) {
                moveStartPositions[b.data.id] = Pair(b.data.x, b.data.y)
            }
        }
        // Move all selected boxes simultaneously
        for (selected in selectedBoxes) {
            selected.applyMoveDelta(dx, dy)
        }
        selectionOverlay.invalidate()
        invalidateConnectors()
    }

    private fun handleBoxMoveFinished() {
        if (moveStartPositions.isNotEmpty()) {
            val initial = moveStartPositions.toMap()

            // Optional grid snap (matching 32dp canvas grid)
            if (gridSnap) {
                val snap = 32f * resources.displayMetrics.density
                for (box in selectedBoxes) {
                    val sx = Math.round(box.data.x / snap) * snap
                    val sy = Math.round(box.data.y / snap) * snap
                    box.data.x = sx
                    box.data.y = sy
                    box.x = sx
                    box.y = sy
                }
            }
            selectionOverlay.invalidate()
            invalidateConnectors()

            val final = selectedBoxes.associate { it.data.id to Pair(it.data.x, it.data.y) }
            val moved = initial.any { (id, pos) ->
                final[id]?.let { f -> f.first != pos.first || f.second != pos.second } ?: false
            }
            if (moved) {
                onBoxMovedListener?.invoke()
            }
            moveStartPositions.clear()

            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() {
                    for (box in boxes) {
                        final[box.data.id]?.let { (fx, fy) ->
                            box.data.x = fx
                            box.data.y = fy
                            box.x = fx
                            box.y = fy
                        }
                    }
                    selectionOverlay.invalidate()
                    invalidateConnectors()
                }
                override fun undo() {
                    for (box in boxes) {
                        initial[box.data.id]?.let { (ix, iy) ->
                            box.data.x = ix
                            box.data.y = iy
                            box.x = ix
                            box.y = iy
                        }
                    }
                    selectionOverlay.invalidate()
                    invalidateConnectors()
                }
            })
        }
    }

    private fun handleBoxResized() {
        selectionOverlay.invalidate()
        invalidateConnectors()
    }

    // ---------- Multi-Select Operations ----------

    fun selectAll() {
        selectedBoxes.clear()
        selectedBoxes.addAll(boxes)
        boxes.forEach { it.setSelectedState(true) }
        selectionOverlay.targetBox = boxes.lastOrNull()
        selectionOverlay.bringToFront()
        marqueeOverlay.bringToFront()
        selectionOverlay.invalidate()
        onSelectionChanged?.invoke(selectedBoxes.size)
    }

    fun dismissKeyboardAndClearFocus() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        val currentFocusView = findFocus() ?: this
        imm?.hideSoftInputFromWindow(windowToken, 0)
        currentFocusView.clearFocus()
    }

    fun clearSelection() {
        dismissKeyboardAndClearFocus()
        selectedBoxes.forEach { it.setSelectedState(false) }
        selectedBoxes.clear()
        selectionOverlay.targetBox = null
        selectionOverlay.invalidate()
        onSelectionChanged?.invoke(0)
    }

    fun deleteSelectedBoxes() {
        if (selectedBoxes.isEmpty()) return
        val toDelete = selectedBoxes.toList()
        val deletedData = toDelete.map { it.data.copyDeep() }
        val affectedConnectors = connectors.filter { conn ->
            toDelete.any { it.data.id == conn.fromId || it.data.id == conn.toId }
        }

        for (box in toDelete) {
            removeBoxInternal(box)
        }
        clearSelection()

        undoRedoManager?.record(object : CanvasCommand {
            override fun execute() {
                for (d in deletedData) {
                    boxes.firstOrNull { it.data.id == d.id }?.let { removeBoxInternal(it) }
                }
            }
            override fun undo() {
                for (d in deletedData) {
                    addBoxFromData(d.copyDeep())
                }
                connectors.addAll(affectedConnectors)
                invalidateConnectors()
            }
        })
    }

    fun setSelectedBoxesColor(color: Int) {
        if (selectedBoxes.isEmpty()) return
        val prevColors = selectedBoxes.associate { it.data.id to it.data.boxColor }
        for (b in selectedBoxes) {
            b.setBoxColor(color)
        }

        undoRedoManager?.record(object : CanvasCommand {
            override fun execute() {
                for (b in boxes) {
                    if (prevColors.containsKey(b.data.id)) b.setBoxColor(color)
                }
            }
            override fun undo() {
                for (b in boxes) {
                    prevColors[b.data.id]?.let { b.setBoxColor(it) }
                }
            }
        })
    }

    fun duplicateSelectedBoxes() {
        if (selectedBoxes.isEmpty()) return
        val duplicatedViews = mutableListOf<NoteBoxView>()
        for (b in selectedBoxes) {
            val copyData = b.data.copyDeep().apply {
                x += 40f
                y += 40f
            }
            val newView = addBoxFromData(copyData)
            duplicatedViews.add(newView)
        }
        clearSelection()
        for (v in duplicatedViews) {
            selectedBoxes.add(v)
            v.setSelectedState(true)
        }
        onSelectionChanged?.invoke(selectedBoxes.size)

        undoRedoManager?.record(object : CanvasCommand {
            override fun execute() {
                for (v in duplicatedViews) {
                    if (!boxes.contains(v)) {
                        boxes.add(v)
                        contentLayer.addView(v)
                    }
                }
                maintainLayerOrder()
                invalidateConnectors()
            }
            override fun undo() {
                for (v in duplicatedViews) {
                    removeBoxInternal(v)
                }
            }
        })
    }

    private fun deleteSingleBox(box: NoteBoxView) {
        selectedBoxes.clear()
        selectedBoxes.add(box)
        deleteSelectedBoxes()
    }

    private fun duplicateSingleBox(box: NoteBoxView) {
        selectedBoxes.clear()
        selectedBoxes.add(box)
        duplicateSelectedBoxes()
    }

    private fun removeBoxInternal(box: NoteBoxView) {
        boxes.remove(box)
        selectedBoxes.remove(box)
        connectors.removeAll { it.fromId == box.data.id || it.toId == box.data.id }
        contentLayer.removeView(box)
        if (firstSelectedForConnect === box) firstSelectedForConnect = null
        invalidateConnectors()
    }

    // ---------- Connectors ----------

    fun eraseConnectorsAt(cx: Float, cy: Float, radius: Float): List<ConnectorData> {
        val boxMap = boxes.associateBy { it.data.id }
        val hitList = mutableListOf<ConnectorData>()
        val iter = connectors.iterator()
        while (iter.hasNext()) {
            val conn = iter.next()
            val from = if (conn.fromId.isNotBlank()) boxMap[conn.fromId]?.data else null
            val to = if (conn.toId.isNotBlank()) boxMap[conn.toId]?.data else null
            val dist = ConnectorOverlayView.distToConnector(cx, cy, conn, from, to)
            if (dist <= radius) {
                hitList.add(conn)
                iter.remove()
            }
        }
        if (hitList.isNotEmpty()) {
            invalidateConnectors()
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
        return hitList
    }

    fun clearAllArrows(recordUndo: Boolean = true) {
        if (connectors.isEmpty()) return
        val saved = connectors.toList()
        connectors.clear()
        invalidateConnectors()
        onContentChanged?.invoke()
        if (recordUndo) {
            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() {
                    connectors.removeAll(saved)
                    invalidateConnectors()
                    onContentChanged?.invoke()
                }
                override fun undo() {
                    connectors.addAll(saved)
                    invalidateConnectors()
                    onContentChanged?.invoke()
                }
            })
        }
    }

    private fun handleConnectSelection(box: NoteBoxView) {
        val current = firstSelectedForConnect
        if (current == null) {
            firstSelectedForConnect = box
            box.setHighlighted(true)
        } else if (current !== box) {
            val newConn = ConnectorData(
                fromId = current.data.id,
                toId = box.data.id,
                style = defaultConnectorStyle,
                color = defaultConnectorColor,
                headStyle = selectedHeadStyle,
                tailStyle = selectedTailStyle,
                isForeground = isArrowForeground
            )
            connectors.add(newConn)
            current.setHighlighted(false)
            firstSelectedForConnect = null
            invalidateConnectors()
            onConnectorCreated?.invoke()

            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() {
                    if (!connectors.contains(newConn)) connectors.add(newConn)
                    invalidateConnectors()
                }
                override fun undo() {
                    connectors.remove(newConn)
                    invalidateConnectors()
                }
            })
        }
    }

    // ---------- Alignment Tools ----------

    fun alignSelectedLeft() {
        if (selectedBoxes.size < 2) return
        val minX = selectedBoxes.minOf { it.data.x }
        val prevPositions = selectedBoxes.associate { it.data.id to Pair(it.data.x, it.data.y) }
        for (b in selectedBoxes) {
            b.data.x = minX
            b.x = minX
        }
        invalidateConnectors()

        undoRedoManager?.record(object : CanvasCommand {
            override fun execute() {
                for (b in selectedBoxes) { b.data.x = minX; b.x = minX }
                invalidateConnectors()
            }
            override fun undo() {
                for (b in selectedBoxes) {
                    prevPositions[b.data.id]?.let { (px, _) ->
                        b.data.x = px; b.x = px
                    }
                }
                invalidateConnectors()
            }
        })
    }

    fun alignSelectedTop() {
        if (selectedBoxes.size < 2) return
        val minY = selectedBoxes.minOf { it.data.y }
        val prevPositions = selectedBoxes.associate { it.data.id to Pair(it.data.x, it.data.y) }
        for (b in selectedBoxes) {
            b.data.y = minY
            b.y = minY
        }
        invalidateConnectors()

        undoRedoManager?.record(object : CanvasCommand {
            override fun execute() {
                for (b in selectedBoxes) { b.data.y = minY; b.y = minY }
                invalidateConnectors()
            }
            override fun undo() {
                for (b in selectedBoxes) {
                    prevPositions[b.data.id]?.let { (_, py) ->
                        b.data.y = py; b.y = py
                    }
                }
                invalidateConnectors()
            }
        })
    }

    fun distributeSelectedHorizontally() {
        if (selectedBoxes.size < 3) return
        val sorted = selectedBoxes.sortedBy { it.data.x }
        val firstX = sorted.first().data.x
        val lastX = sorted.last().data.x
        val totalSpan = lastX - firstX
        val spacing = totalSpan / (sorted.size - 1)
        val prevPositions = selectedBoxes.associate { it.data.id to Pair(it.data.x, it.data.y) }

        for (i in sorted.indices) {
            val targetX = firstX + spacing * i
            sorted[i].data.x = targetX
            sorted[i].x = targetX
        }
        invalidateConnectors()

        undoRedoManager?.record(object : CanvasCommand {
            override fun execute() {
                for (i in sorted.indices) {
                    val targetX = firstX + spacing * i
                    sorted[i].data.x = targetX
                    sorted[i].x = targetX
                }
                invalidateConnectors()
            }
            override fun undo() {
                for (b in selectedBoxes) {
                    prevPositions[b.data.id]?.let { (px, _) ->
                        b.data.x = px; b.x = px
                    }
                }
                invalidateConnectors()
            }
        })
    }

    // ---------- Camera & Zoom Controls ----------

    fun zoomToFit() {
        if (boxes.isEmpty()) {
            resetZoom()
            return
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (box in boxes) {
            minX = min(minX, box.data.x)
            minY = min(minY, box.data.y)
            maxX = max(maxX, box.data.x + box.data.width)
            maxY = max(maxY, box.data.y + box.data.height)
        }
        val padding = 80f
        val contentW = (maxX - minX) + padding * 2
        val contentH = (maxY - minY) + padding * 2

        val fitScaleX = width / contentW
        val fitScaleY = height / contentH
        val targetScale = min(fitScaleX, fitScaleY).coerceIn(MIN_SCALE, 1.5f)

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        scale = targetScale
        contentLayer.scaleX = scale
        contentLayer.scaleY = scale
        contentLayer.translationX = width / 2f - centerX * scale
        contentLayer.translationY = height / 2f - centerY * scale
        onScaleChanged?.invoke(scale)
        invalidate()
    }

    fun resetZoom() {
        setZoomLevel(1.0f)
    }

    fun zoomIn() {
        setZoomLevel((scale * 1.25f).coerceIn(MIN_SCALE, MAX_SCALE))
    }

    fun zoomOut() {
        setZoomLevel((scale * 0.8f).coerceIn(MIN_SCALE, MAX_SCALE))
    }

    fun setZoomLevel(targetScale: Float) {
        val oldScale = scale
        val newScale = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val focusX = width / 2f
        val focusY = height / 2f
        val contentX = (focusX - contentLayer.translationX) / oldScale
        val contentY = (focusY - contentLayer.translationY) / oldScale
        contentLayer.translationX = focusX - contentX * newScale
        contentLayer.translationY = focusY - contentY * newScale
        scale = newScale
        contentLayer.scaleX = scale
        contentLayer.scaleY = scale
        onScaleChanged?.invoke(scale)
        invalidate()
    }

    fun boxCount() = boxes.size
    fun allBoxViews(): List<NoteBoxView> = boxes.toList()
    fun allConnectors(): List<ConnectorData> = connectors.toList()

    private var searchMatches = mutableListOf<NoteBoxView>()
    private var currentSearchIndex = -1

    private fun clearAllInternal() {
        boxes.forEach { contentLayer.removeView(it) }
        boxes.clear()
        selectedBoxes.clear()
        connectors.clear()
        bgDrawingOverlay.clearStrokes()
        fgDrawingOverlay.clearStrokes()
        firstSelectedForConnect = null
        invalidateConnectors()
        onSelectionChanged?.invoke(0)
    }

    fun clearAll(recordUndo: Boolean = true) {
        if (recordUndo && (boxes.isNotEmpty() || connectors.isNotEmpty() || bgDrawingOverlay.getAllStrokes().isNotEmpty() || fgDrawingOverlay.getAllStrokes().isNotEmpty())) {
            val savedBoxes = boxes.map { it.data.copyDeep() }
            val savedConnectors = connectors.map { it.copy() }
            val savedBgStrokes = bgDrawingOverlay.getAllStrokes().map { it.copyDeep() }
            val savedFgStrokes = fgDrawingOverlay.getAllStrokes().map { it.copyDeep() }

            clearAllInternal()

            undoRedoManager?.record(object : CanvasCommand {
                override fun execute() {
                    clearAllInternal()
                }
                override fun undo() {
                    clearAllInternal()
                    for (b in savedBoxes) {
                        addBoxFromData(b.copyDeep())
                    }
                    connectors.addAll(savedConnectors.map { it.copy() })
                    bgDrawingOverlay.setStrokes(savedBgStrokes.map { it.copyDeep() })
                    fgDrawingOverlay.setStrokes(savedFgStrokes.map { it.copyDeep() })
                    maintainLayerOrder()
                    invalidateConnectors()
                }
            })
        } else {
            clearAllInternal()
        }
    }

    fun findAndJump(query: String, forward: Boolean = true): Pair<Int, Int>? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchMatches.clear()
            currentSearchIndex = -1
            return null
        }

        val newMatches = boxes.filter { box ->
            when (box.data.kind) {
                BoxKind.TEXT, BoxKind.SHAPE -> box.data.text.contains(trimmed, ignoreCase = true)
                BoxKind.CHECKLIST -> box.data.checklist.any { it.text.contains(trimmed, ignoreCase = true) }
                BoxKind.TABLE -> box.data.tableData?.let { td ->
                    td.cells.any { row -> row.any { cell -> cell.contains(trimmed, ignoreCase = true) } } ||
                    td.customColLabels.any { it.contains(trimmed, ignoreCase = true) } ||
                    td.customRowLabels.any { it.contains(trimmed, ignoreCase = true) }
                } ?: false
                BoxKind.BOARD -> (box.data.targetBoardName ?: box.data.text).contains(trimmed, ignoreCase = true)
                BoxKind.LINK -> box.data.text.contains(trimmed, ignoreCase = true)
                BoxKind.IMAGE -> false
            }
        }

        if (newMatches.isEmpty()) {
            searchMatches.clear()
            currentSearchIndex = -1
            return Pair(0, 0)
        }

        searchMatches.clear()
        searchMatches.addAll(newMatches)

        if (forward) {
            currentSearchIndex = (currentSearchIndex + 1) % searchMatches.size
        } else {
            currentSearchIndex = if (currentSearchIndex <= 0) searchMatches.size - 1 else currentSearchIndex - 1
        }

        val target = searchMatches[currentSearchIndex]

        clearSelection()
        selectedBoxes.add(target)
        target.setSelectedState(true)
        selectionOverlay.targetBox = target
        selectionOverlay.bringToFront()
        selectionOverlay.invalidate()
        onSelectionChanged?.invoke(1)

        teleportTo(target.centerX(), target.centerY())

        return Pair(currentSearchIndex + 1, searchMatches.size)
    }

    fun clearSearch() {
        searchMatches.clear()
        currentSearchIndex = -1
    }

    fun loadBoardData(board: BoardData) {
        clearAll(recordUndo = false)
        for (bd in board.boxes) {
            addBoxFromData(bd)
        }
        connectors.addAll(board.connectors)
        maintainLayerOrder()
        invalidateConnectors()
        bgDrawingOverlay.setStrokes(board.strokes)
        fgDrawingOverlay.setStrokes(board.fgStrokes)

        if (board.scale in MIN_SCALE..MAX_SCALE && board.panX != 0f && board.panY != 0f) {
            scale = board.scale
            contentLayer.scaleX = scale
            contentLayer.scaleY = scale
            contentLayer.translationX = board.panX
            contentLayer.translationY = board.panY
        } else {
            zoomToFit()
        }
        invalidate()
    }

    fun exportBoardData(meta: BoardMeta): BoardData {
        return BoardData(
            meta = meta,
            boxes = boxes.map { it.data }.toMutableList(),
            connectors = connectors.toMutableList(),
            strokes = bgDrawingOverlay.getAllStrokes().toMutableList(),
            fgStrokes = fgDrawingOverlay.getAllStrokes().toMutableList(),
            panX = contentLayer.translationX,
            panY = contentLayer.translationY,
            scale = scale
        )
    }

    fun teleportTo(worldX: Float, worldY: Float) {
        contentLayer.translationX = width / 2f - worldX * scale
        contentLayer.translationY = height / 2f - worldY * scale
        invalidate()
        onScaleChanged?.invoke(scale)
    }

    fun getCurrentViewport(): RectF {
        val left = -contentLayer.translationX / scale
        val top = -contentLayer.translationY / scale
        val right = left + width / scale
        val bottom = top + height / scale
        return RectF(left, top, right, bottom)
    }

    fun getViewportPanX(): Float = contentLayer.translationX
    fun getViewportPanY(): Float = contentLayer.translationY
}
