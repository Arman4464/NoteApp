package com.noteapp.student.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Overlay inside the infinite canvas content layer for freehand drawing (Pen, Highlighter, Eraser).
 * Draws smooth quadratic bezier vector paths and scales seamlessly with pan and zoom.
 */
class DrawingOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val strokes = mutableListOf<DrawingStrokeData>()
    private var currentStroke: DrawingStrokeData? = null
    private val currentPath = Path()

    var isDrawingEnabled: Boolean = false
    var isEraserMode: Boolean = false
    var currentColor: Int = Color.parseColor("#1E293B")
    var currentStrokeWidth: Float = 6f
    var eraserRadius: Float = 28f
    var isHighlighter: Boolean = false

    var onStrokeFinished: ((DrawingStrokeData) -> Unit)? = null
    var onStrokesErased: ((List<DrawingStrokeData>) -> Unit)? = null

    private val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val highlighterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.BEVEL
        alpha = 110
    }

    private var previousX = 0f
    private var previousY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw completed strokes
        for (stroke in strokes) {
            drawStroke(canvas, stroke)
        }

        // Draw active in-progress stroke
        currentStroke?.let { stroke ->
            drawStroke(canvas, stroke)
        }
    }

    private fun getOrCreateStrokePath(stroke: DrawingStrokeData): Path {
        stroke.cachedPath?.let { return it }
        val path = Path()
        if (stroke.points.isNotEmpty()) {
            val first = stroke.points[0]
            path.moveTo(first.first, first.second)
            for (i in 1 until stroke.points.size) {
                val p0 = stroke.points[i - 1]
                val p1 = stroke.points[i]
                val midX = (p0.first + p1.first) / 2f
                val midY = (p0.second + p1.second) / 2f
                path.quadTo(p0.first, p0.second, midX, midY)
            }
            val last = stroke.points.last()
            path.lineTo(last.first, last.second)
        }
        stroke.cachedPath = path
        return path
    }

    private fun drawStroke(canvas: Canvas, stroke: DrawingStrokeData) {
        if (stroke.points.isEmpty()) return

        val paint = if (stroke.isHighlighter) {
            highlighterPaint.apply {
                color = stroke.color
                alpha = 110
                strokeWidth = stroke.width * 2.5f
            }
        } else {
            penPaint.apply {
                color = stroke.color
                alpha = 255
                strokeWidth = stroke.width
            }
        }

        if (stroke.points.size == 1) {
            val pt = stroke.points[0]
            val sx = pt.first
            val sy = pt.second
            canvas.drawCircle(sx, sy, stroke.width / 2f, paint)
            return
        }

        val path = if (stroke === currentStroke) {
            currentPath.reset()
            val first = stroke.points[0]
            currentPath.moveTo(first.first, first.second)
            for (i in 1 until stroke.points.size) {
                val p0 = stroke.points[i - 1]
                val p1 = stroke.points[i]
                val midX = (p0.first + p1.first) / 2f
                val midY = (p0.second + p1.second) / 2f
                currentPath.quadTo(p0.first, p0.second, midX, midY)
            }
            val last = stroke.points.last()
            currentPath.lineTo(last.first, last.second)
            currentPath
        } else {
            getOrCreateStrokePath(stroke)
        }
        canvas.drawPath(path, paint)
    }

    fun handleDrawingTouchEvent(event: MotionEvent, contentX: Float, contentY: Float): Boolean {
        if (!isDrawingEnabled && !isEraserMode) return false

        if (isEraserMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    eraseAt(contentX, contentY, radius = eraserRadius)
                    return true
                }
            }
            return false
        }

        // Pen / Highlighter mode
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = contentX
                previousY = contentY
                val stroke = DrawingStrokeData(
                    points = mutableListOf(Pair(contentX, contentY)),
                    color = currentColor,
                    width = currentStrokeWidth,
                    isHighlighter = isHighlighter
                )
                currentStroke = stroke
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = currentStroke ?: return false
                val dist = hypot((contentX - previousX).toDouble(), (contentY - previousY).toDouble()).toFloat()
                if (dist > 3f) { // Smooth minimum threshold
                    stroke.points.add(Pair(contentX, contentY))
                    previousX = contentX
                    previousY = contentY
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val stroke = currentStroke
                if (stroke != null && stroke.points.isNotEmpty()) {
                    stroke.cachedPath = null
                    strokes.add(stroke)
                    currentStroke = null
                    invalidate()
                    onStrokeFinished?.invoke(stroke)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                currentStroke = null
                invalidate()
                return true
            }
        }
        return false
    }

    private fun eraseAt(x: Float, y: Float, radius: Float) {
        val erased = mutableListOf<DrawingStrokeData>()
        val radiusSq = radius * radius

        val iterator = strokes.iterator()
        while (iterator.hasNext()) {
            val stroke = iterator.next()
            var hit = false
            for (pt in stroke.points) {
                val dx = pt.first - x
                val dy = pt.second - y
                if (dx * dx + dy * dy <= radiusSq) {
                    hit = true
                    break
                }
            }
            if (hit) {
                erased.add(stroke)
                iterator.remove()
            }
        }

        if (erased.isNotEmpty()) {
            invalidate()
            onStrokesErased?.invoke(erased)
        }
    }

    fun getAllStrokes(): List<DrawingStrokeData> = strokes.toList()

    fun setStrokes(newStrokes: List<DrawingStrokeData>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        invalidate()
    }

    fun addStroke(stroke: DrawingStrokeData) {
        strokes.add(stroke)
        invalidate()
    }

    fun removeStroke(strokeId: String) {
        strokes.removeAll { it.id == strokeId }
        invalidate()
    }

    fun clearStrokes() {
        strokes.clear()
        currentStroke = null
        invalidate()
    }
}
