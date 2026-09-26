package com.noteapp.student.canvas

import android.content.Context
import android.graphics.Canvas
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

    /**
     * When null: renders all connectors.
     * When false: renders only background connectors.
     * When true: renders only foreground connectors.
     */
    var renderForegroundOnly: Boolean? = null

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
        val connectors = connectorProvider?.invoke() ?: emptyList()
        val preview = previewArrow
        if (connectors.isEmpty() && preview == null) return

        val filter = renderForegroundOnly
        val hasMatching = connectors.any { filter == null || it.isForeground == filter } ||
                (preview != null && (filter == null || preview.isForeground == filter))
        if (!hasMatching) return

        val boxes = boxProvider?.invoke() ?: emptyList()
        val boxMap = boxes.associateBy { it.data.id }

        for (conn in connectors) {
            if (filter != null && conn.isForeground != filter) continue
            val from = if (conn.fromId.isNotBlank()) boxMap[conn.fromId]?.data else null
            val to = if (conn.toId.isNotBlank()) boxMap[conn.toId]?.data else null
            renderConnectorDirect(canvas, conn, from, to, scale = 1f, linePaint, arrowFillPaint, arrowStrokePaint)
        }

        // Draw active in-progress drag preview if present
        preview?.let { previewItem ->
            if (filter == null || previewItem.isForeground == filter) {
                val from = if (previewItem.fromId.isNotBlank()) boxMap[previewItem.fromId]?.data else null
                val to = if (previewItem.toId.isNotBlank()) boxMap[previewItem.toId]?.data else null
                renderConnectorDirect(canvas, previewItem, from, to, scale = 1f, linePaint, arrowFillPaint, arrowStrokePaint)
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

        /**
         * Resolves the quadratic Bezier control point for card-to-card connectors.
         */
        fun getConnectorControlPoint(x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float> {
            val curvature = 0.10f
            val mx = (x1 + x2) / 2f
            val my = (y1 + y2) / 2f
            val dx = x2 - x1
            val dy = y2 - y1
            val ctrlX = mx - dy * curvature
            val ctrlY = my + dx * curvature
            return Pair(ctrlX, ctrlY)
        }

        /**
         * Reusable vector renderer for connectors and free-floating canvas arrows.
         * Used identically by ConnectorOverlayView (on-screen) and ExportManager (high-res export).
         */
        fun renderConnectorDirect(
            canvas: Canvas,
            conn: ConnectorData,
            fromBox: NoteBoxData?,
            toBox: NoteBoxData?,
            scale: Float,
            linePaint: Paint,
            arrowFillPaint: Paint,
            arrowStrokePaint: Paint
        ) {
            val color = conn.color
            val strokeW = conn.strokeWidth.coerceAtLeast(2f) * scale

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
                if (fromBox == null || toBox == null) return
                val toCenterX = toBox.x + toBox.width / 2f
                val toCenterY = toBox.y + toBox.height / 2f
                val fromCenterX = fromBox.x + fromBox.width / 2f
                val fromCenterY = fromBox.y + fromBox.height / 2f

                val startPt = getBoxEdgePoint(fromBox, toCenterX, toCenterY)
                val endPt = getBoxEdgePoint(toBox, fromCenterX, fromCenterY)
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

            // Symmetrical, deterministic head & tail style resolution
            val headStyle = resolveHeadStyle(conn)
            val tailStyle = resolveTailStyle(conn)

            val path = Path()
            path.moveTo(x1, y1)

            val angleEnd: Double
            val angleStart: Double

            if (isCurved) {
                val (ctrlX, ctrlY) = getConnectorControlPoint(x1, y1, x2, y2)
                path.quadTo(ctrlX, ctrlY, x2, y2)
                canvas.drawPath(path, linePaint)

                // Bezier tangents
                angleEnd = atan2((y2 - ctrlY).toDouble(), (x2 - ctrlX).toDouble())
                angleStart = atan2((y1 - ctrlY).toDouble(), (x1 - ctrlX).toDouble())
            } else {
                path.lineTo(x2, y2)
                canvas.drawPath(path, linePaint)

                angleEnd = atan2(dy.toDouble(), dx.toDouble())
                angleStart = atan2((-dy).toDouble(), (-dx).toDouble())
            }

            // Render Tail Decoration at (x1, y1)
            drawEndpointShape(canvas, x1, y1, angleStart, tailStyle, strokeW, arrowFillPaint, arrowStrokePaint)

            // Render Head Decoration at (x2, y2)
            drawEndpointShape(canvas, x2, y2, angleEnd, headStyle, strokeW, arrowFillPaint, arrowStrokePaint)
        }

        fun resolveHeadStyle(conn: ConnectorData): String {
            val h = conn.headStyle.lowercase().trim()
            if (h.isNotBlank()) return h
            return when (conn.style) {
                "double_arrow" -> "triangle"
                "dot" -> "dot"
                "diamond" -> "diamond"
                "plain" -> "none"
                else -> "triangle"
            }
        }

        fun resolveTailStyle(conn: ConnectorData): String {
            val t = conn.tailStyle.lowercase().trim()
            if (t.isNotBlank()) return t
            return when (conn.style) {
                "double_arrow" -> "triangle"
                else -> "none"
            }
        }

        fun drawEndpointShape(
            canvas: Canvas,
            x: Float,
            y: Float,
            angle: Double,
            style: String,
            strokeW: Float,
            arrowFillPaint: Paint,
            arrowStrokePaint: Paint
        ) {
            val arrowLen = (20f + strokeW * 1.2f).coerceIn(16f, 38f)
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
                "dot", "circle" -> {
                    val radius = (7f + strokeW * 0.7f).coerceIn(6f, 16f)
                    val cx = (x - radius * cos(angle)).toFloat()
                    val cy = (y - radius * sin(angle)).toFloat()
                    canvas.drawCircle(cx, cy, radius, arrowFillPaint)
                }
                "diamond" -> {
                    val dSize = (11f + strokeW * 0.8f).coerceIn(10f, 22f)
                    val p = Path().apply {
                        moveTo(x, y)
                        lineTo((x - dSize * cos(angle - 0.5)).toFloat(), (y - dSize * sin(angle - 0.5)).toFloat())
                        lineTo((x - dSize * 1.8f * cos(angle)).toFloat(), (y - dSize * 1.8f * sin(angle)).toFloat())
                        lineTo((x - dSize * 1.8f * cos(angle)).toFloat(), (y - dSize * 1.8f * sin(angle)).toFloat())
                        lineTo((x - dSize * cos(angle + 0.5)).toFloat(), (y - dSize * sin(angle + 0.5)).toFloat())
                        close()
                    }
                    canvas.drawPath(p, arrowFillPaint)
                }
                "bar" -> {
                    val barHalf = (10f + strokeW * 1.1f).coerceIn(8f, 20f)
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

        /**
         * Computes the shortest distance from a test point (px, py) to the connector line/arc.
         * Used for precise arrow erasing hit-testing.
         */
        fun distToConnector(
            px: Float,
            py: Float,
            conn: ConnectorData,
            fromBox: NoteBoxData?,
            toBox: NoteBoxData?
        ): Float {
            val x1: Float
            val y1: Float
            val x2: Float
            val y2: Float
            val isCurved: Boolean

            if (conn.isFreeArrow) {
                x1 = conn.startX
                y1 = conn.startY
                x2 = conn.endX
                y2 = conn.endY
                isCurved = false
            } else {
                if (fromBox == null || toBox == null) return Float.MAX_VALUE
                val toCenterX = toBox.x + toBox.width / 2f
                val toCenterY = toBox.y + toBox.height / 2f
                val fromCenterX = fromBox.x + fromBox.width / 2f
                val fromCenterY = fromBox.y + fromBox.height / 2f

                val startPt = getBoxEdgePoint(fromBox, toCenterX, toCenterY)
                val endPt = getBoxEdgePoint(toBox, fromCenterX, fromCenterY)
                x1 = startPt.first
                y1 = startPt.second
                x2 = endPt.first
                y2 = endPt.second
                isCurved = true
            }

            if (!isCurved) {
                return distToSegment(px, py, x1, y1, x2, y2)
            } else {
                val (ctrlX, ctrlY) = getConnectorControlPoint(x1, y1, x2, y2)
                var minDist = Float.MAX_VALUE
                var prevX = x1
                var prevY = y1
                val steps = 10
                for (i in 1..steps) {
                    val t = i.toFloat() / steps.toFloat()
                    val omt = 1f - t
                    val curX = omt * omt * x1 + 2f * omt * t * ctrlX + t * t * x2
                    val curY = omt * omt * y1 + 2f * omt * t * ctrlY + t * t * y2
                    val d = distToSegment(px, py, prevX, prevY, curX, curY)
                    if (d < minDist) minDist = d
                    prevX = curX
                    prevY = curY
                }
                return minDist
            }
        }

        private fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            val l2 = dx * dx + dy * dy
            if (l2 == 0f) return hypot((px - x1).toDouble(), (py - y1).toDouble()).toFloat()
            val t = ((px - x1) * dx + (py - y1) * dy) / l2
            val clampedT = t.coerceIn(0f, 1f)
            val projX = x1 + clampedT * dx
            val projY = y1 + clampedT * dy
            return hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
        }
    }
}
