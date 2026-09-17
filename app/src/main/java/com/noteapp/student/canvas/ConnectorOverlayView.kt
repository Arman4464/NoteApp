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
import kotlin.math.sin

/**
 * Transparent overlay drawn inside the canvas content layer that renders
 * elegant curved or straight arrows between connected cards.
 */
class ConnectorOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var boxProvider: (() -> List<NoteBoxView>)? = null
    var connectorProvider: (() -> List<ConnectorData>)? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val boxes = boxProvider?.invoke() ?: return
        val connectors = connectorProvider?.invoke() ?: return
        val boxMap = boxes.associateBy { it.data.id }

        for (conn in connectors) {
            val from = boxMap[conn.fromId] ?: continue
            val to = boxMap[conn.toId] ?: continue
            val color = conn.color

            linePaint.color = color
            arrowPaint.color = color

            val (x1, y1) = getBoxEdgePoint(from.data, to.centerX(), to.centerY())
            val (x2, y2) = getBoxEdgePoint(to.data, from.centerX(), from.centerY())

            drawCurvedArrow(
                canvas,
                x1, y1,
                x2, y2,
                conn.style
            )
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

    private fun drawCurvedArrow(
        canvas: Canvas,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        style: String = "arrow"
    ) {
        val dx = x2 - x1
        val path = Path()
        path.moveTo(x1, y1)

        // Smooth cubic bezier curve for a polished diagram look
        val ctrlX1 = x1 + dx * 0.5f
        val ctrlY1 = y1
        val ctrlX2 = x1 + dx * 0.5f
        val ctrlY2 = y2
        path.cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, x2, y2)
        canvas.drawPath(path, linePaint)

        if (style == "plain") return

        // Calculate tangent angle at target (x2, y2) from (ctrlX2, ctrlY2)
        val angle2 = atan2((y2 - ctrlY2).toDouble(), (x2 - ctrlX2).toDouble())
        val arrowLength = 24f
        val arrowAngle = Math.toRadians(26.0)

        when (style) {
            "dot" -> {
                canvas.drawCircle(x2, y2, 10f, arrowPaint)
            }
            "diamond" -> {
                val dSize = 14f
                val dPath = Path().apply {
                    moveTo(x2, y2)
                    lineTo((x2 - dSize * cos(angle2 - 0.5)).toFloat(), (y2 - dSize * sin(angle2 - 0.5)).toFloat())
                    lineTo((x2 - dSize * 1.8f * cos(angle2)).toFloat(), (y2 - dSize * 1.8f * sin(angle2)).toFloat())
                    lineTo((x2 - dSize * cos(angle2 + 0.5)).toFloat(), (y2 - dSize * sin(angle2 + 0.5)).toFloat())
                    close()
                }
                canvas.drawPath(dPath, arrowPaint)
            }
            "double_arrow" -> {
                // End arrow
                val arrowPath2 = Path().apply {
                    moveTo(x2, y2)
                    lineTo(
                        (x2 - arrowLength * cos(angle2 - arrowAngle)).toFloat(),
                        (y2 - arrowLength * sin(angle2 - arrowAngle)).toFloat()
                    )
                    lineTo(
                        (x2 - arrowLength * cos(angle2 + arrowAngle)).toFloat(),
                        (y2 - arrowLength * sin(angle2 + arrowAngle)).toFloat()
                    )
                    close()
                }
                canvas.drawPath(arrowPath2, arrowPaint)

                // Start arrow
                val angle1 = atan2((ctrlY1 - y1).toDouble(), (ctrlX1 - x1).toDouble())
                val arrowPath1 = Path().apply {
                    moveTo(x1, y1)
                    lineTo(
                        (x1 + arrowLength * cos(angle1 - arrowAngle)).toFloat(),
                        (y1 + arrowLength * sin(angle1 - arrowAngle)).toFloat()
                    )
                    lineTo(
                        (x1 + arrowLength * cos(angle1 + arrowAngle)).toFloat(),
                        (y1 + arrowLength * sin(angle1 + arrowAngle)).toFloat()
                    )
                    close()
                }
                canvas.drawPath(arrowPath1, arrowPaint)
            }
            else -> { // Default: "arrow"
                val arrowPath = Path().apply {
                    moveTo(x2, y2)
                    lineTo(
                        (x2 - arrowLength * cos(angle2 - arrowAngle)).toFloat(),
                        (y2 - arrowLength * sin(angle2 - arrowAngle)).toFloat()
                    )
                    lineTo(
                        (x2 - arrowLength * cos(angle2 + arrowAngle)).toFloat(),
                        (y2 - arrowLength * sin(angle2 + arrowAngle)).toFloat()
                    )
                    close()
                }
                canvas.drawPath(arrowPath, arrowPaint)
            }
        }
    }
}
