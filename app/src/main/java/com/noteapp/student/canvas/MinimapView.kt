package com.noteapp.student.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.noteapp.student.settings.ThemeColors
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive Birds-Eye Minimap HUD:
 * - Visualizes the positions of all cards with distinct color coding per element kind
 * - Highlights the active screen viewport rectangle
 * - Touching or dragging anywhere on the minimap teleports the camera directly to that area
 * - 100% theme adaptive
 */
class MinimapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var boxesProvider: (() -> List<NoteBoxView>)? = null
    var viewportProvider: (() -> RectF)? = null
    var onTeleport: ((worldX: Float, worldY: Float) -> Unit)? = null
    var themeColors: ThemeColors? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E60F172A") // Translucent slate glass
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val viewportFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4038BDF8") // Subtle sky blue fill
        style = Paint.Style.FILL
    }
    private val viewportStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Vibrant cyan viewfinder
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun applyTheme(colors: ThemeColors) {
        themeColors = colors
        bgPaint.color = Color.argb(
            230,
            Color.red(colors.topBarBg),
            Color.green(colors.topBarBg),
            Color.blue(colors.topBarBg)
        )
        borderPaint.color = colors.cardBorder
        val r = Color.red(colors.accent)
        val g = Color.green(colors.accent)
        val b = Color.blue(colors.accent)
        viewportFillPaint.color = Color.argb(45, r, g, b)
        viewportStrokePaint.color = colors.accent
        invalidate()
    }

    private val boundsRect = RectF()
    private val tempCardRect = RectF()
    private val tempViewportRect = RectF()

    private var worldMinX = 0f
    private var worldMinY = 0f
    private var mapScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Draw rounded background HUD
        boundsRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(boundsRect, 24f, 24f, bgPaint)
        canvas.drawRoundRect(boundsRect, 24f, 24f, borderPaint)

        val boxes = boxesProvider?.invoke() ?: emptyList()
        val viewport = viewportProvider?.invoke() ?: RectF(0f, 0f, 1000f, 1000f)

        // 2. Compute bounding area of all cards and active viewport
        var minX = viewport.left
        var minY = viewport.top
        var maxX = viewport.right
        var maxY = viewport.bottom

        for (box in boxes) {
            val d = box.data
            minX = min(minX, d.x)
            minY = min(minY, d.y)
            maxX = max(maxX, d.x + d.width)
            maxY = max(maxY, d.y + d.height)
        }

        // Add 20% margin around content
        val marginX = max(400f, (maxX - minX) * 0.15f)
        val marginY = max(400f, (maxY - minY) * 0.15f)
        minX -= marginX
        minY -= marginY
        maxX += marginX
        maxY += marginY

        val worldW = max(100f, maxX - minX)
        val worldH = max(100f, maxY - minY)

        val pad = 16f
        val innerW = w - pad * 2
        val innerH = h - pad * 2

        mapScale = min(innerW / worldW, innerH / worldH)
        worldMinX = minX
        worldMinY = minY

        // Center map inside HUD
        offsetX = pad + (innerW - worldW * mapScale) / 2f
        offsetY = pad + (innerH - worldH * mapScale) / 2f

        // 3. Draw cards
        for (box in boxes) {
            val d = box.data
            val cx = offsetX + (d.x - worldMinX) * mapScale
            val cy = offsetY + (d.y - worldMinY) * mapScale
            val cw = max(4f, d.width * mapScale)
            val ch = max(4f, d.height * mapScale)

            cardPaint.color = when (d.kind) {
                BoxKind.TEXT -> themeColors?.accent ?: Color.parseColor("#6366F1")
                BoxKind.IMAGE -> Color.parseColor("#10B981") // Green
                BoxKind.CHECKLIST -> Color.parseColor("#06B6D4") // Cyan
                BoxKind.TABLE -> Color.parseColor("#3B82F6") // Blue Table
                BoxKind.SHAPE -> Color.parseColor("#F59E0B") // Amber
                BoxKind.BOARD -> Color.parseColor("#A855F7") // Purple
                BoxKind.LINK -> Color.parseColor("#2563EB") // Blue
            }

            tempCardRect.set(cx, cy, cx + cw, cy + ch)
            canvas.drawRoundRect(tempCardRect, 4f, 4f, cardPaint)
        }

        // 4. Draw current camera viewport rectangle
        val vx = offsetX + (viewport.left - worldMinX) * mapScale
        val vy = offsetY + (viewport.top - worldMinY) * mapScale
        val vw = viewport.width() * mapScale
        val vh = viewport.height() * mapScale

        tempViewportRect.set(vx, vy, vx + vw, vy + vh)
        canvas.drawRoundRect(tempViewportRect, 6f, 6f, viewportFillPaint)
        canvas.drawRoundRect(tempViewportRect, 6f, 6f, viewportStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (mapScale > 0) {
                    val worldX = worldMinX + (event.x - offsetX) / mapScale
                    val worldY = worldMinY + (event.y - offsetY) / mapScale
                    onTeleport?.invoke(worldX, worldY)
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
