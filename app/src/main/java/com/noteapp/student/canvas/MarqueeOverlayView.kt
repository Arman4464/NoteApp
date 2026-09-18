package com.noteapp.student.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Transparent overlay for rendering the selection marquee rectangle (lasso/box select).
 */
class MarqueeOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val marqueeRect = RectF()
    private var isVisible = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334F46E5") // Semi-transparent Indigo
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F46E5")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    fun setMarquee(rect: RectF?) {
        if (rect == null) {
            isVisible = false
        } else {
            marqueeRect.set(rect)
            isVisible = true
        }
        invalidate()
    }

    fun applyTheme(accentColor: Int) {
        borderPaint.color = accentColor
        fillPaint.color = Color.argb(45, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isVisible && !marqueeRect.isEmpty) {
            canvas.drawRect(marqueeRect, fillPaint)
            canvas.drawRect(marqueeRect, borderPaint)
        }
    }
}
