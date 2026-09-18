package com.noteapp.student.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.noteapp.student.canvas.BoardData
import com.noteapp.student.canvas.BoxKind
import com.noteapp.student.settings.ThemeColors
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a miniature visual preview (minimap) of an entire board's contents
 * for use in Homepage board cards.
 */
class BoardThumbnailMinimapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var boardData: BoardData? = null
        set(value) {
            field = value
            invalidate()
        }

    var themeColors: ThemeColors? = null
        set(value) {
            field = value
            if (value != null) {
                bgPaint.color = value.canvasBg
                gridPaint.color = value.gridDot
                borderPaint.color = value.cardBorder
                placeholderPaint.color = if (value.isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8")
            }
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A") // Modern deep slate
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val contentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val boundsRect = RectF()
    private val tempCardRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Rounded background & border
        boundsRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(boundsRect, 16f, 16f, bgPaint)
        canvas.drawRoundRect(boundsRect, 16f, 16f, borderPaint)

        // Subtle geometric grid
        val step = 24f
        var gx = step
        while (gx < w) {
            canvas.drawLine(gx, 0f, gx, h, gridPaint)
            gx += step
        }
        var gy = step
        while (gy < h) {
            canvas.drawLine(0f, gy, w, gy, gridPaint)
            gy += step
        }

        val data = boardData
        if (data == null || (data.boxes.isEmpty() && data.strokes.isEmpty() && data.fgStrokes.isEmpty() && data.connectors.isEmpty())) {
            canvas.drawText("Empty Board", w / 2f, h / 2f + 10f, placeholderPaint)
            return
        }

        // Compute bounding box
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        val boxMap = data.boxes.associateBy { it.id }

        for (b in data.boxes) {
            minX = min(minX, b.x)
            minY = min(minY, b.y)
            maxX = max(maxX, b.x + b.width)
            maxY = max(maxY, b.y + b.height)
        }

        val allStrokes = data.strokes + data.fgStrokes
        for (s in allStrokes) {
            for (pt in s.points) {
                minX = min(minX, pt.first)
                minY = min(minY, pt.second)
                maxX = max(maxX, pt.first)
                maxY = max(maxY, pt.second)
            }
        }

        for (c in data.connectors) {
            if (c.isFreeArrow) {
                minX = min(minX, min(c.startX, c.endX))
                minY = min(minY, min(c.startY, c.endY))
                maxX = max(maxX, max(c.startX, c.endX))
                maxY = max(maxY, max(c.startY, c.endY))
            } else {
                val from = boxMap[c.fromId]
                val to = boxMap[c.toId]
                if (from != null && to != null) {
                    val x1 = from.x + from.width / 2f
                    val y1 = from.y + from.height / 2f
                    val x2 = to.x + to.width / 2f
                    val y2 = to.y + to.height / 2f
                    minX = min(minX, min(x1, x2))
                    minY = min(minY, min(y1, y2))
                    maxX = max(maxX, max(x1, x2))
                    maxY = max(maxY, max(y1, y2))
                }
            }
        }

        if (minX.isInfinite()) {
            canvas.drawText("Empty Board", w / 2f, h / 2f + 10f, placeholderPaint)
            return
        }

        val margin = 80f
        minX -= margin
        minY -= margin
        maxX += margin
        maxY += margin

        val worldW = max(100f, maxX - minX)
        val worldH = max(100f, maxY - minY)

        val pad = 14f
        val innerW = w - pad * 2
        val innerH = h - pad * 2

        val scale = min(innerW / worldW, innerH / worldH)
        val offsetX = pad + (innerW - worldW * scale) / 2f
        val offsetY = pad + (innerH - worldH * scale) / 2f

        // 1. Draw Connectors (including free-floating arrows)
        val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        for (c in data.connectors) {
            val x1: Float
            val y1: Float
            val x2: Float
            val y2: Float
            if (c.isFreeArrow) {
                x1 = offsetX + (c.startX - minX) * scale
                y1 = offsetY + (c.startY - minY) * scale
                x2 = offsetX + (c.endX - minX) * scale
                y2 = offsetY + (c.endY - minY) * scale
            } else {
                val from = boxMap[c.fromId] ?: continue
                val to = boxMap[c.toId] ?: continue
                x1 = offsetX + (from.x + from.width / 2f - minX) * scale
                y1 = offsetY + (from.y + from.height / 2f - minY) * scale
                x2 = offsetX + (to.x + to.width / 2f - minX) * scale
                y2 = offsetY + (to.y + to.height / 2f - minY) * scale
            }

            linePaint.color = c.color
            linePaint.strokeWidth = max(2f, c.strokeWidth * scale)
            canvas.drawLine(x1, y1, x2, y2, linePaint)

            // Draw miniature directional arrow head
            val dx = x2 - x1
            val dy = y2 - y1
            val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len > 8f) {
                val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
                val arrowSize = (6f + c.strokeWidth * scale).coerceIn(5f, 14f)
                val arrowAngle = Math.toRadians(28.0)
                val a1X = (x2 - arrowSize * kotlin.math.cos(angle - arrowAngle)).toFloat()
                val a1Y = (y2 - arrowSize * kotlin.math.sin(angle - arrowAngle)).toFloat()
                val a2X = (x2 - arrowSize * kotlin.math.cos(angle + arrowAngle)).toFloat()
                val a2Y = (y2 - arrowSize * kotlin.math.sin(angle + arrowAngle)).toFloat()
                val arrowPath = Path().apply {
                    moveTo(x2, y2)
                    lineTo(a1X, a1Y)
                    lineTo(a2X, a2Y)
                    close()
                }
                arrowFillPaint.color = c.color
                canvas.drawPath(arrowPath, arrowFillPaint)
            }
        }

        // 2. Draw Background Strokes
        for (s in data.strokes) {
            drawStroke(canvas, s.points, s.color, s.width * scale, minX, minY, offsetX, offsetY, scale)
        }

        // 3. Draw Cards
        for (b in data.boxes) {
            val bx = offsetX + (b.x - minX) * scale
            val by = offsetY + (b.y - minY) * scale
            val bw = max(6f, b.width * scale)
            val bh = max(6f, b.height * scale)

            tempCardRect.set(bx, by, bx + bw, by + bh)

            val baseColor = when (b.kind) {
                BoxKind.TEXT -> Color.parseColor("#4F46E5")
                BoxKind.IMAGE -> Color.parseColor("#10B981")
                BoxKind.CHECKLIST -> Color.parseColor("#06B6D4")
                BoxKind.TABLE -> Color.parseColor("#3B82F6")
                BoxKind.SHAPE -> Color.parseColor("#F59E0B")
                BoxKind.BOARD -> Color.parseColor("#8B5CF6")
                BoxKind.LINK -> Color.parseColor("#3B82F6")
            }

            cardPaint.color = baseColor
            cardStrokePaint.color = Color.WHITE
            canvas.drawRoundRect(tempCardRect, 4f, 4f, cardPaint)

            // Draw miniature content line indicators if large enough
            if (bw > 24f && bh > 16f) {
                val lx1 = bx + 4f
                val lx2 = bx + bw - 4f
                val ly1 = by + 6f
                val ly2 = by + 12f
                if (ly1 < by + bh - 4f) canvas.drawLine(lx1, ly1, lx2, ly1, contentLinePaint)
                if (ly2 < by + bh - 4f) canvas.drawLine(lx1, ly2, bx + bw * 0.6f, ly2, contentLinePaint)
            }
        }

        // 4. Draw Foreground Strokes
        for (s in data.fgStrokes) {
            drawStroke(canvas, s.points, s.color, s.width * scale, minX, minY, offsetX, offsetY, scale)
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        points: List<Pair<Float, Float>>,
        color: Int,
        width: Float,
        minX: Float, minY: Float,
        offsetX: Float, offsetY: Float,
        scale: Float
    ) {
        if (points.size < 2) return
        linePaint.color = color
        linePaint.strokeWidth = max(1.5f, width)

        val path = Path()
        val first = points[0]
        path.moveTo(offsetX + (first.first - minX) * scale, offsetY + (first.second - minY) * scale)
        for (i in 1 until points.size) {
            val pt = points[i]
            path.lineTo(offsetX + (pt.first - minX) * scale, offsetY + (pt.second - minY) * scale)
        }
        canvas.drawPath(path, linePaint)
    }
}
