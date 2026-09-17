package com.noteapp.student.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.noteapp.student.canvas.BoardData
import com.noteapp.student.canvas.CanvasSerializer
import com.noteapp.student.canvas.ConnectorData
import com.noteapp.student.canvas.ConnectorOverlayView
import com.noteapp.student.canvas.DrawingStrokeData
import com.noteapp.student.canvas.NoteBoxView
import com.noteapp.student.settings.ThemeColors
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the canvas to a high-resolution bitmap by re-issuing vector drawing
 * commands (text, shapes, images, curved connectors, freehand ink strokes) at high scale.
 */
object ExportManager {

    private const val RENDER_SCALE = 3f
    private const val MAX_DIMENSION = 6000
    private const val PADDING = 80f

    fun renderBitmap(
        boxes: List<NoteBoxView>,
        connectors: List<ConnectorData>,
        strokes: List<DrawingStrokeData> = emptyList(),
        fgStrokes: List<DrawingStrokeData> = emptyList(),
        themeColors: ThemeColors? = null
    ): Bitmap? {
        if (boxes.isEmpty() && strokes.isEmpty() && fgStrokes.isEmpty()) return null

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

        for (stroke in strokes) {
            for (pt in stroke.points) {
                minX = min(minX, pt.first - stroke.width)
                minY = min(minY, pt.second - stroke.width)
                maxX = max(maxX, pt.first + stroke.width)
                maxY = max(maxY, pt.second + stroke.width)
            }
        }

        for (stroke in fgStrokes) {
            for (pt in stroke.points) {
                minX = min(minX, pt.first - stroke.width)
                minY = min(minY, pt.second - stroke.width)
                maxX = max(maxX, pt.first + stroke.width)
                maxY = max(maxY, pt.second + stroke.width)
            }
        }

        minX -= PADDING; minY -= PADDING; maxX += PADDING; maxY += PADDING

        val contentWidth = maxX - minX
        val contentHeight = maxY - minY

        var scale = RENDER_SCALE
        val naturalW = contentWidth * scale
        val naturalH = contentHeight * scale
        val largestSide = max(naturalW, naturalH)
        if (largestSide > MAX_DIMENSION) {
            scale *= MAX_DIMENSION / largestSide
        }
        scale = max(scale, 1f)

        val bitmapWidth = max(1, (contentWidth * scale).toInt())
        val bitmapHeight = max(1, (contentHeight * scale).toInt())

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgColor = themeColors?.canvasBg ?: Color.WHITE
        canvas.drawColor(bgColor)

        // 1. Draw Connectors
        val boxMap = boxes.associateBy { it.data.id }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 5f * scale
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        for (conn in connectors) {
            val from = boxMap[conn.fromId] ?: continue
            val to = boxMap[conn.toId] ?: continue
            linePaint.color = conn.color
            arrowPaint.color = conn.color

            val (edge1X, edge1Y) = ConnectorOverlayView.getBoxEdgePoint(from.data, to.data.x + to.data.width / 2f, to.data.y + to.data.height / 2f)
            val (edge2X, edge2Y) = ConnectorOverlayView.getBoxEdgePoint(to.data, from.data.x + from.data.width / 2f, from.data.y + from.data.height / 2f)

            val x1 = (edge1X - minX) * scale
            val y1 = (edge1Y - minY) * scale
            val x2 = (edge2X - minX) * scale
            val y2 = (edge2Y - minY) * scale
            drawCurvedArrow(canvas, x1, y1, x2, y2, linePaint, arrowPaint, scale)
        }

        // 2. Draw Freehand Strokes
        val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val highlighterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.BEVEL
        }

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val paint = if (stroke.isHighlighter) {
                highlighterPaint.apply {
                    color = stroke.color
                    alpha = 110
                    strokeWidth = stroke.width * 2.5f * scale
                }
            } else {
                penPaint.apply {
                    color = stroke.color
                    alpha = 255
                    strokeWidth = stroke.width * scale
                }
            }

            if (stroke.points.size == 1) {
                val pt = stroke.points[0]
                val sx = (pt.first - minX) * scale
                val sy = (pt.second - minY) * scale
                canvas.drawCircle(sx, sy, (stroke.width / 2f) * scale, paint)
                continue
            }

            val path = Path()
            val first = stroke.points[0]
            path.moveTo((first.first - minX) * scale, (first.second - minY) * scale)
            for (i in 1 until stroke.points.size) {
                val p0 = stroke.points[i - 1]
                val p1 = stroke.points[i]
                val midX = ((p0.first + p1.first) / 2f - minX) * scale
                val midY = ((p0.second + p1.second) / 2f - minY) * scale
                path.quadTo(
                    (p0.first - minX) * scale, (p0.second - minY) * scale,
                    midX, midY
                )
            }
            val last = stroke.points.last()
            path.lineTo((last.first - minX) * scale, (last.second - minY) * scale)
            canvas.drawPath(path, paint)
        }

        // 3. Draw Note Boxes on top
        for (box in boxes) {
            box.setExportMode(true)
            canvas.save()
            canvas.translate((box.data.x - minX) * scale, (box.data.y - minY) * scale)
            canvas.scale(scale, scale)
            box.draw(canvas)
            canvas.restore()
            box.setExportMode(false)
        }

        // 4. Draw Foreground Freehand Strokes (on top of boxes)
        for (stroke in fgStrokes) {
            if (stroke.points.isEmpty()) continue
            val paint = if (stroke.isHighlighter) {
                highlighterPaint.apply {
                    color = stroke.color
                    alpha = 110
                    strokeWidth = stroke.width * 2.5f * scale
                }
            } else {
                penPaint.apply {
                    color = stroke.color
                    alpha = 255
                    strokeWidth = stroke.width * scale
                }
            }

            if (stroke.points.size == 1) {
                val pt = stroke.points[0]
                val sx = (pt.first - minX) * scale
                val sy = (pt.second - minY) * scale
                canvas.drawCircle(sx, sy, (stroke.width / 2f) * scale, paint)
                continue
            }

            val path = Path()
            val first = stroke.points[0]
            path.moveTo((first.first - minX) * scale, (first.second - minY) * scale)
            for (i in 1 until stroke.points.size) {
                val p0 = stroke.points[i - 1]
                val p1 = stroke.points[i]
                val midX = ((p0.first + p1.first) / 2f - minX) * scale
                val midY = ((p0.second + p1.second) / 2f - minY) * scale
                path.quadTo(
                    (p0.first - minX) * scale, (p0.second - minY) * scale,
                    midX, midY
                )
            }
            val last = stroke.points.last()
            path.lineTo((last.first - minX) * scale, (last.second - minY) * scale)
            canvas.drawPath(path, paint)
        }

        return bitmap
    }

    private fun drawCurvedArrow(
        canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
        linePaint: Paint, arrowPaint: Paint, scale: Float
    ) {
        val dx = x2 - x1
        val path = Path()
        path.moveTo(x1, y1)

        val ctrlX1 = x1 + dx * 0.5f
        val ctrlY1 = y1
        val ctrlX2 = x1 + dx * 0.5f
        val ctrlY2 = y2
        path.cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, x2, y2)
        canvas.drawPath(path, linePaint)

        val angle = atan2((y2 - ctrlY2).toDouble(), (x2 - ctrlX2).toDouble())
        val arrowLength = 24f * scale
        val arrowAngle = Math.toRadians(26.0)

        val arrowPath = Path()
        arrowPath.moveTo(x2, y2)
        arrowPath.lineTo(
            (x2 - arrowLength * cos(angle - arrowAngle)).toFloat(),
            (y2 - arrowLength * sin(angle - arrowAngle)).toFloat()
        )
        arrowPath.lineTo(
            (x2 - arrowLength * cos(angle + arrowAngle)).toFloat(),
            (y2 - arrowLength * sin(angle + arrowAngle)).toFloat()
        )
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
    }

    private fun timestampedName(prefix: String, ext: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return prefix + "_" + stamp + "." + ext
    }

    fun savePng(context: Context, bitmap: Bitmap): Uri? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        dir.mkdirs()
        val file = File(dir, timestampedName("note_board", "png"))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    fun savePdf(context: Context, bitmap: Bitmap): Uri? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        dir.mkdirs()
        val file = File(dir, timestampedName("note_board", "pdf"))
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        document.finishPage(page)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    fun shareUri(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share export"))
    }

    fun downloadPdfToDevice(context: Context, bitmap: Bitmap): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) downloadsDir else context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        targetDir.mkdirs()
        val file = File(targetDir, timestampedName("NoteApp_Export", "pdf"))
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        document.finishPage(page)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    fun downloadPngToDevice(context: Context, bitmap: Bitmap): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) downloadsDir else context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        targetDir.mkdirs()
        val file = File(targetDir, timestampedName("NoteApp_Export", "png"))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    fun exportProjectFile(context: Context, board: BoardData): Pair<File, Uri>? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        dir.mkdirs()
        val safeName = board.meta.name.replace(Regex("[^a-zA-Z0-9_]"), "_").take(24).ifBlank { "board" }
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.noteapp")
        val json = CanvasSerializer.serializeBoard(board)
        file.writeText(json)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Pair(file, uri)
    }

    fun importProjectFile(context: Context, uri: Uri): BoardData? {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return null
            CanvasSerializer.deserializeBoard(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
