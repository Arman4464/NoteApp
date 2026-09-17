package com.noteapp.student.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Transparent overlay drawn inside the canvas content layer that renders
 * precision directional connectors between cards and free-floating canvas arrows.
 */
class ConnectorOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var boxProvider: (() -> List<NoteBoxView>)? = null
    var connectorProvider: (() -> List<ConnectorData>)? = null
    var previewArrow: ConnectorData? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val boxes = boxProvider?.invoke() ?: emptyList()
        val connectors = connectorProvider?.invoke() ?: emptyList()
        val boxMap = boxes.associateBy { it.data.id }

        for (conn in connectors) {
            renderConnector(canvas, conn, boxMap)
        }

        // Draw active in-progress drag preview if present
        previewArrow?.let { preview ->
            renderConnector(canvas, preview, boxMap)
        }
    }

    private fun renderConnector(canvas: Canvas, conn: ConnectorData, boxMap: Map<String, NoteBoxView>) {
        val color = conn.color
        val strokeW = conn.strokeWidth.coerceAtLeast(2f)

        linePaint.color = color
        linePaint.strokeWidth = strokeW
        arrowFillPaint.color = color
        arrowStrokePaint.color = color
        arrowStrokePaint.strokeWidth = strokeW

        val x1: Float
        val y1: Float
        val x2: Float
        val y2: Float
        val isCurved: Boolean

        if (conn.isFreeArrow) {
            // Free-floating canvas arrow
            x1 = conn.startX
            y1 = conn.startY
            x2 = conn.endX
            y2 = conn.endY
            isCurved = false
        } else {
            val from = boxMap[conn.fromId] ?: return
            val to = boxMap[conn.toId] ?: return
            val startPt = getBoxEdgePoint(from.data, to.centerX(), to.centerY())
            val endPt = getBoxEdgePoint(to.data, from.centerX(), from.centerY())
            x1 = startPt.first
            y1 = startPt.second
            x2 = endPt.first
            y2 = endPt.second
            isCurved = true
        }

        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist < 4f) return

        // Resolve Head and Tail styles with legacy fallback
        val headStyle: String
        val tailStyle: String
        if (conn.headStyle != "triangle" || conn.tailStyle != "none") {
            headStyle = conn.headStyle
            tailStyle = conn.tailStyle
        } else {
            when (conn.style) {
                "double_arrow" -> { headStyle = "triangle"; tailStyle = "triangle" }
                "dot" -> { headStyle = "dot"; tailStyle = "none" }
                "diamond" -> { headStyle = "diamond"; tailStyle = "none" }
                "plain" -> { headStyle = "none"; tailStyle = "none" }
                else -> { headStyle = "triangle"; tailStyle = "none" }
            }
        }

        val path = Path()
        path.moveTo(x1, y1)

        val angleEnd: Double
        val angleStart: Double

        if (isCurved) {
            // Subtle, elegant quadratic arc
            val curvature = 0.10f
            val mx = (x1 + x2) / 2f
            val my = (y1 + y2) / 2f
            val ctrlX = mx - dy * curvature
            val ctrlY = my + dx * curvature

            path.quadTo(ctrlX, ctrlY, x2, y2)
            canvas.drawPath(path, linePaint)

            // True Bezier curve tangent derivative:
            // At endpoint t = 1.0: B'(1) = 2 * (P2 - C)
            angleEnd = atan2((y2 - ctrlY).toDouble(), (x2 - ctrlX).toDouble())
            // At startpoint t = 0.0: B'(0) = 2 * (C - P1) pointing outwards
            angleStart = atan2((y1 - ctrlY).toDouble(), (x1 - ctrlX).toDouble())
        } else {
            // Crisp straight directional line
            path.lineTo(x2, y2)
            canvas.drawPath(path, linePaint)

            angleEnd = atan2(dy.toDouble(), dx.toDouble())
            angleStart = atan2((-dy).toDouble(), (-dx).toDouble())
        }

        // Render Tail Decoration at (x1, y1)
        drawEndpointShape(canvas, x1, y1, angleStart, tailStyle, strokeW)

        // Render Head Decoration at (x2, y2)
        drawEndpointShape(canvas, x2, y2, angleEnd, headStyle, strokeW)
    }

    private fun drawEndpointShape(
        canvas: Canvas,
        x: Float,
        y: Float,
        angle: Double,
        style: String,
        strokeW: Float
    ) {
        val arrowLen = (20f + strokeW * 1.2f).coerceIn(16f, 36f)
        val arrowAngle = Math.toRadians(24.0)

        when (style.lowercase()) {
            "triangle", "arrow" -> {
                // Closed solid wedge arrowhead
                val p = Path().apply {
                    moveTo(x, y)
                    val p1X = (x - arrowLen * cos(angle - arrowAngle)).toFloat()
                    val p1Y = (y - arrowLen * sin(angle - arrowAngle)).toFloat()
                    val p2X = (x - arrowLen * cos(angle + arrowAngle)).toFloat()
                    val p2Y = (y - arrowLen * sin(angle + arrowAngle)).toFloat()
                    lineTo(p1X, p1Y)
                    // Slightly indented base for a sleek stealth look
                    val baseMidX = (x - arrowLen * 0.75f * cos(angle)).toFloat()
                    val baseMidY = (y - arrowLen * 0.75f * sin(angle)).toFloat()
                    lineTo(baseMidX, baseMidY)
                    lineTo(p2X, p2Y)
                    close()
                }
                canvas.drawPath(p, arrowFillPaint)
            }
            "open" -> {
                // Open V-barb
                val p = Path().apply {
                    val p1X = (x - arrowLen * cos(angle - arrowAngle)).toFloat()
                    val p1Y = (y - arrowLen * sin(angle - arrowAngle)).toFloat()
                    val p2X = (x - arrowLen * cos(angle + arrowAngle)).toFloat()
                    val p2Y = (y - arrowLen * sin(angle + arrowAngle)).toFloat()
                    moveTo(p1X, p1Y)
                    lineTo(x, y)
                    lineTo(p2X, p2Y)
                }
                canvas.drawPath(p, arrowStrokePaint)
            }
            "dot" -> {
                val radius = (7f + strokeW * 0.7f).coerceIn(6f, 14f)
                val cx = (x - radius * cos(angle)).toFloat()
                val cy = (y - radius * sin(angle)).toFloat()
                canvas.drawCircle(cx, cy, radius, arrowFillPaint)
            }
            "diamond" -> {
                val dSize = (11f + strokeW * 0.8f).coerceIn(10f, 20f)
                val p = Path().apply {
                    moveTo(x, y)
                    lineTo((x - dSize * cos(angle - 0.5)).toFloat(), (y - dSize * sin(angle - 0.5)).toFloat())
                    lineTo((x - dSize * 1.8f * cos(angle)).toFloat(), (y - dSize * 1.8f * sin(angle)).toFloat())
                    lineTo((x - dSize * cos(angle + 0.5)).toFloat(), (y - dSize * sin(angle + 0.5)).toFloat())
                    close()
                }
                canvas.drawPath(p, arrowFillPaint)
            }
            "bar" -> {
                val barHalf = (10f + strokeW).coerceIn(8f, 18f)
                val perpAngle = angle + Math.PI / 2.0
                val b1X = (x + barHalf * cos(perpAngle)).toFloat()
                val b1Y = (y + barHalf * sin(perpAngle)).toFloat()
                val b2X = (x - barHalf * cos(perpAngle)).toFloat()
                val b2Y = (y - barHalf * sin(perpAngle)).toFloat()
                canvas.drawLine(b1X, b1Y, b2X, b2Y, arrowStrokePaint)
            }
            "none", "plain" -> {
                // No decoration
            }
        }
    }

    companion object {
        fun getBoxEdgePoint(boxData: NoteBoxData, targetX: Float, targetY: Float): Pair<Float, Float> {
            val cx = boxData.x + boxData.width / 2f
            val cy = boxData.y + boxData.height / 2f
            val dx = targetX - cx
            val dy = targetY - cy
            if (dx == 0f && dy == 0f) return Pair(cx, cy)

            val hw = boxData.width / 2f
            val hh = boxData.height / 2f

            val scaleX = if (dx != 0f) hw / Math.abs(dx) else Float.MAX_VALUE
            val scaleY = if (dy != 0f) hh / Math.abs(dy) else Float.MAX_VALUE
            val scale = Math.min(scaleX, scaleY)

            return Pair(cx + dx * scale, cy + dy * scale)
        }
    }
}
