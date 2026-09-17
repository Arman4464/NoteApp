package com.noteapp.student.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max

/**
 * MS Paint-style Selection and Transformation Overlay:
 * - Crisp blue rectangular border outline around the selected element
 * - 8 resize handles (4 corners + 4 midpoints) to expand/contract in all directions
 * - Clean typography heading floating ABOVE (outside) the element with no solid background
 * - Dragging the heading moves the element smoothly
 * - 3-dots options menu button next to the heading
 */
class SelectionTransformOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    companion object {
        const val HANDLE_TOP_LEFT = 0
        const val HANDLE_TOP_CENTER = 1
        const val HANDLE_TOP_RIGHT = 2
        const val HANDLE_RIGHT_CENTER = 3
        const val HANDLE_BOTTOM_RIGHT = 4
        const val HANDLE_BOTTOM_CENTER = 5
        const val HANDLE_BOTTOM_LEFT = 6
        const val HANDLE_LEFT_CENTER = 7
        const val HANDLE_HEADING = 8
        const val HANDLE_NONE = -1

        const val MIN_WIDTH = 140f
        const val MIN_HEIGHT = 100f
    }

    var targetBox: NoteBoxView? = null
        set(value) {
            field = value
            invalidate()
        }

    var onBoxMoved: ((dx: Float, dy: Float) -> Unit)? = null
    var onBoxMoveFinished: (() -> Unit)? = null
    var onBoxResized: (() -> Unit)? = null
    var onBoxResizeFinished: ((box: NoteBoxView, oldX: Float, oldY: Float, oldW: Float, oldH: Float) -> Unit)? = null
    var onMenuRequested: ((box: NoteBoxView) -> Unit)? = null

    // Visual paints
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB") // Classic vibrant MS Paint selection blue
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        textSize = 34f // In canvas units, crisp readable size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 0f, 2f, Color.parseColor("#40000000"))
    }

    // Touch interaction state
    private var activeHandle = HANDLE_NONE
    private var isInteracting = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialWidth = 0f
    private var initialHeight = 0f
    private var initialX = 0f
    private var initialY = 0f

    // Bounds cache
    private val headingRect = RectF()
    private val handleCenters = Array(8) { FloatArray(2) }

    fun updateTheme(textColor: Int) {
        headingPaint.color = textColor
        invalidate()
    }

    private fun updateGeometry(box: NoteBoxView) {
        val l = box.data.x
        val t = box.data.y
        val r = box.data.x + box.data.width
        val b = box.data.y + box.data.height
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f

        // 8 handles
        handleCenters[HANDLE_TOP_LEFT][0] = l
        handleCenters[HANDLE_TOP_LEFT][1] = t

        handleCenters[HANDLE_TOP_CENTER][0] = cx
        handleCenters[HANDLE_TOP_CENTER][1] = t

        handleCenters[HANDLE_TOP_RIGHT][0] = r
        handleCenters[HANDLE_TOP_RIGHT][1] = t

        handleCenters[HANDLE_RIGHT_CENTER][0] = r
        handleCenters[HANDLE_RIGHT_CENTER][1] = cy

        handleCenters[HANDLE_BOTTOM_RIGHT][0] = r
        handleCenters[HANDLE_BOTTOM_RIGHT][1] = b

        handleCenters[HANDLE_BOTTOM_CENTER][0] = cx
        handleCenters[HANDLE_BOTTOM_CENTER][1] = b

        handleCenters[HANDLE_BOTTOM_LEFT][0] = l
        handleCenters[HANDLE_BOTTOM_LEFT][1] = b

        handleCenters[HANDLE_LEFT_CENTER][0] = l
        handleCenters[HANDLE_LEFT_CENTER][1] = cy

        // Heading title text above the box (pure typography, NO solid background)
        val headingText = getHeadingTitle(box)
        val textWidth = headingPaint.measureText(headingText)

        val headingWidth = max(box.data.width, textWidth + 32f)
        val headingHeight = 44f
        val headingBottom = t - 8f
        val headingTop = headingBottom - headingHeight

        headingRect.set(l, headingTop, l + headingWidth, headingBottom)
    }

    private fun getHeadingTitle(box: NoteBoxView): String {
        return when (box.data.kind) {
            BoxKind.TEXT -> "Note"
            BoxKind.IMAGE -> "Image"
            BoxKind.CHECKLIST -> "Checklist"
            BoxKind.TABLE -> "Table"
            BoxKind.SHAPE -> when (box.data.shapeType) {
                ShapeType.STICKY_NOTE -> "Sticky Note"
                ShapeType.DIAMOND -> "Diamond"
                ShapeType.STAR -> "Star"
                ShapeType.CLOUD -> "Cloud"
                ShapeType.TRIANGLE -> "Triangle"
                ShapeType.CIRCLE -> "Circle"
                else -> "Shape"
            }
            BoxKind.BOARD -> "\uD83D\uDCC1 ${box.data.targetBoardName ?: "Board"}"
            BoxKind.LINK -> "\uD83D\uDD17 Link"
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val box = targetBox ?: return
        if (!box.isBoxSelected()) return

        updateGeometry(box)

        val l = box.data.x
        val t = box.data.y
        val r = box.data.x + box.data.width
        val b = box.data.y + box.data.height

        // 1. MS Paint Blue Border
        canvas.drawRect(l, t, r, b, borderPaint)

        // 2. 8 MS Paint Handles (14px square, white fill + blue stroke)
        val handleHalfSize = 7f
        for (i in 0..7) {
            val hx = handleCenters[i][0]
            val hy = handleCenters[i][1]
            canvas.drawRect(hx - handleHalfSize, hy - handleHalfSize, hx + handleHalfSize, hy + handleHalfSize, handleFillPaint)
            canvas.drawRect(hx - handleHalfSize, hy - handleHalfSize, hx + handleHalfSize, hy + handleHalfSize, handleStrokePaint)
        }

        // 3. Heading Text floating ABOVE (outside) the box (NO solid background color, pure clean text)
        val headingText = getHeadingTitle(box)
        canvas.drawText(headingText, headingRect.left + 4f, headingRect.bottom - 8f, headingPaint)
    }

    private fun hitTest(x: Float, y: Float): Int {
        val box = targetBox ?: return HANDLE_NONE
        if (!box.isBoxSelected()) return HANDLE_NONE
        updateGeometry(box)

        // 1. 8 handles hit test (touch target radius 28px)
        val touchRadius = 28f
        for (i in 0..7) {
            val hx = handleCenters[i][0]
            val hy = handleCenters[i][1]
            if (hypot(x - hx, y - hy) <= touchRadius) {
                return i
            }
        }

        // 2. Heading drag hit test (generous touch padding above the box spanning full width)
        val headingHit = RectF(
            headingRect.left - 24f,
            headingRect.top - 24f,
            headingRect.right + 24f,
            box.data.y + 12f
        )
        if (headingHit.contains(x, y)) {
            return HANDLE_HEADING
        }

        return HANDLE_NONE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val box = targetBox ?: return false
        if (!box.isBoxSelected()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val handle = hitTest(event.x, event.y)
                if (handle == HANDLE_NONE) {
                    val l = box.data.x
                    val t = box.data.y
                    val r = box.data.x + box.data.width
                    val b = box.data.y + box.data.height

                    // If touch is inside the box:
                    if (event.x in l..r && event.y in t..b) {
                        // For non-text content (Images, Boards, Links), direct touch drags to move the box!
                        if (box.data.kind == BoxKind.IMAGE || box.data.kind == BoxKind.BOARD || box.data.kind == BoxKind.LINK) {
                            activeHandle = HANDLE_HEADING
                            isInteracting = true
                            lastTouchX = event.x
                            lastTouchY = event.y
                            initialX = box.data.x
                            initialY = box.data.y
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        return false
                    }
                    return false
                }

                activeHandle = handle
                isInteracting = true
                lastTouchX = event.x
                lastTouchY = event.y
                initialWidth = box.data.width
                initialHeight = box.data.height
                initialX = box.data.x
                initialY = box.data.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isInteracting || activeHandle == HANDLE_NONE) return false

                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

                when (activeHandle) {
                    HANDLE_HEADING -> {
                        // Drag heading moves the element!
                        onBoxMoved?.invoke(dx, dy)
                    }
                    HANDLE_TOP_LEFT -> {
                        if (box.data.kind == BoxKind.IMAGE) {
                            val aspect = initialWidth / initialHeight.coerceAtLeast(0.01f)
                            val newW = max(box.data.width - dx, MIN_HEIGHT * aspect)
                            val newH = newW / aspect
                            box.data.x += (box.data.width - newW)
                            box.data.y += (box.data.height - newH)
                            box.data.width = newW
                            box.data.height = newH
                            applyBoxBounds(box)
                        } else {
                            val newW = box.data.width - dx
                            val newH = box.data.height - dy
                            if (newW >= MIN_WIDTH) {
                                box.data.x += dx
                                box.data.width = newW
                            }
                            if (newH >= MIN_HEIGHT) {
                                box.data.y += dy
                                box.data.height = newH
                            }
                            applyBoxBounds(box)
                        }
                    }
                    HANDLE_TOP_CENTER -> {
                        if (box.data.kind == BoxKind.IMAGE) {
                            val aspect = initialWidth / initialHeight.coerceAtLeast(0.01f)
                            val newH = max(box.data.height - dy, MIN_HEIGHT)
                            val newW = newH * aspect
                            box.data.y += (box.data.height - newH)
                            box.data.width = newW
                            box.data.height = newH
                            applyBoxBounds(box)
                        } else {
                            val newH = box.data.height - dy
                            if (newH >= MIN_HEIGHT) {
                                box.data.y += dy
                                box.data.height = newH
                            }
                            applyBoxBounds(box)
                        }
                    }
                    HANDLE_TOP_RIGHT -> {
                        if (box.data.kind == BoxKind.IMAGE) {
                            val aspect = initialWidth / initialHeight.coerceAtLeast(0.01f)
                            val newW = max(box.data.width + dx, MIN_HEIGHT * aspect)
                            val newH = newW / aspect
                            box.data.y += (box.data.height - newH)
                            box.data.width = newW
                            box.data.height = newH
                            applyBoxBounds(box)
                        } else {
                            val newW = box.data.width + dx
                            val newH = box.data.height - dy
                            if (newW >= MIN_WIDTH) {
                                box.data.width = newW
                            }
                            if (newH >= MIN_HEIGHT) {
                                box.data.y += dy
                                box.data.height = newH
                            }
                            applyBoxBounds(box)
                        }
                    }
                    HANDLE_RIGHT_CENTER -> {
                        if (box.data.kind == BoxKind.IMAGE) {
                            val aspect = initialWidth / initialHeight.coerceAtLeast(0.01f)
                            val newW = max(box.data.width + dx, MIN_WIDTH)
                            val newH = newW / aspect
                            box.data.width = newW
                            box.data.height = newH
                            applyBoxBounds(box)
                        } else {
                            val newW = box.data.width + dx
                            if (newW >= MIN_WIDTH) {
                                box.data.width = newW
                            }
                            applyBoxBounds(box)
                        }
                    }
                    HANDLE_BOTTOM_RIGHT -> {
                        if (box.data.kind == BoxKind.IMAGE) {
                            val aspect = initialWidth / initialHeight.coerceAtLeast(0.01f)
                            val newW = max(box.data.width + dx, MIN_HEIGHT * aspect)
                            val newH = newW / aspect
                            box.data.width = newW
                            box.data.height = newH
                            applyBoxBounds(box)
                        } else {
                            val newW = box.data.width + dx
                            val newH = box.data.height + dy
                            if (newW >= MIN_WIDTH) {
                                box.data.width = newW
                            }
                            if (newH >= MIN_HEIGHT) {
                                box.data.height = newH
                            }
                            applyBoxBounds(box)
                        }
                    }
                    HANDLE_BOTTOM_CENTER -> {
                        if (box.data.kind == BoxKind.IMAGE) {
                            val aspect = initialWidth / initialHeight.coerceAtLeast(0.01f)
                            val newH = max(box.data.height + dy, MIN_HEIGHT)
                            val newW = newH * aspect
                            box.data.width = newW
                            box.data.height = newH
                            applyBoxBounds(box)
                        } else {
                            val newH = box.data.height + dy
                            if (newH >= MIN_HEIGHT) {
                                box.data.height = newH
                            }
                            applyBoxBounds(box)
                        }
                    }
                    HANDLE_BOTTOM_LEFT -> {
                        if (box.data.kind == BoxKind.IMAGE) {
                            val aspect = initialWidth / initialHeight.coerceAtLeast(0.01f)
                            val newW = max(box.data.width - dx, MIN_HEIGHT * aspect)
                            val newH = newW / aspect
                            box.data.x += (box.data.width - newW)
                            box.data.width = newW
                            box.data.height = newH
                            applyBoxBounds(box)
                        } else {
                            val newW = box.data.width - dx
                            val newH = box.data.height + dy
                            if (newW >= MIN_WIDTH) {
                                box.data.width = newW
                            }
                            if (newH >= MIN_HEIGHT) {
                                box.data.height = newH
                            }
                            applyBoxBounds(box)
                        }
                    }
                    HANDLE_LEFT_CENTER -> {
                        if (box.data.kind == BoxKind.IMAGE) {
                            val aspect = initialWidth / initialHeight.coerceAtLeast(0.01f)
                            val newW = max(box.data.width - dx, MIN_WIDTH)
                            val newH = newW / aspect
                            box.data.x += (box.data.width - newW)
                            box.data.width = newW
                            box.data.height = newH
                            applyBoxBounds(box)
                        } else {
                            val newW = box.data.width - dx
                            if (newW >= MIN_WIDTH) {
                                box.data.x += dx
                                box.data.width = newW
                            }
                            applyBoxBounds(box)
                        }
                    }
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isInteracting) {
                    if (activeHandle == HANDLE_HEADING) {
                        onBoxMoveFinished?.invoke()
                    } else if (activeHandle in 0..7) {
                        onBoxResized?.invoke()
                        onBoxResizeFinished?.invoke(box, initialX, initialY, initialWidth, initialHeight)
                    }
                    isInteracting = false
                    activeHandle = HANDLE_NONE
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun applyBoxBounds(box: NoteBoxView) {
        box.x = box.data.x
        box.y = box.data.y
        val lp = box.layoutParams
        lp.width = box.data.width.toInt()
        lp.height = box.data.height.toInt()
        box.layoutParams = lp
        onBoxResized?.invoke()
    }
}
