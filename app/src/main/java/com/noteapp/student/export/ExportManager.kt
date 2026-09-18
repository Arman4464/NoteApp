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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
        if (boxes.isEmpty() && strokes.isEmpty() && fgStrokes.isEmpty() && connectors.isEmpty()) return null

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        val boxMap = boxes.associateBy { it.data.id }

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

        for (conn in connectors) {
            if (conn.isFreeArrow) {
                val pad = conn.strokeWidth + 30f
                minX = min(minX, min(conn.startX, conn.endX) - pad)
                minY = min(minY, min(conn.startY, conn.endY) - pad)
                maxX = max(maxX, max(conn.startX, conn.endX) + pad)
                maxY = max(maxY, max(conn.startY, conn.endY) + pad)
            } else {
                val from = boxMap[conn.fromId]?.data
                val to = boxMap[conn.toId]?.data
                if (from != null && to != null) {
                    val toCenterX = to.x + to.width / 2f
                    val toCenterY = to.y + to.height / 2f
                    val fromCenterX = from.x + from.width / 2f
                    val fromCenterY = from.y + from.height / 2f
                    val (x1, y1) = ConnectorOverlayView.getBoxEdgePoint(from, toCenterX, toCenterY)
                    val (x2, y2) = ConnectorOverlayView.getBoxEdgePoint(to, fromCenterX, fromCenterY)
                    val (ctrlX, ctrlY) = ConnectorOverlayView.getConnectorControlPoint(x1, y1, x2, y2)
                    val pad = conn.strokeWidth + 30f
                    minX = min(minX, minOf(x1, x2, ctrlX) - pad)
                    minY = min(minY, minOf(y1, y2, ctrlY) - pad)
                    maxX = max(maxX, maxOf(x1, x2, ctrlX) + pad)
                    maxY = max(maxY, maxOf(y1, y2, ctrlY) + pad)
                }
            }
        }

        minX -= PADDING; minY -= PADDING; maxX += PADDING; maxY += PADDING

        val contentWidth = maxX - minX
        val contentHeight = maxY - minY
        if (contentWidth <= 0f || contentHeight <= 0f) return null

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
        canvas.drawFilter = android.graphics.PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val bgColor = themeColors?.canvasBg ?: Color.WHITE
        canvas.drawColor(bgColor)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

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

        fun drawStrokesList(strokeList: List<DrawingStrokeData>) {
            for (stroke in strokeList) {
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
        }

        fun drawConnectorsList(connList: List<ConnectorData>) {
            canvas.save()
            canvas.translate(-minX * scale, -minY * scale)
            for (conn in connList) {
                val from = if (conn.fromId.isNotBlank()) boxMap[conn.fromId]?.data else null
                val to = if (conn.toId.isNotBlank()) boxMap[conn.toId]?.data else null
                ConnectorOverlayView.renderConnectorDirect(
                    canvas,
                    conn,
                    from,
                    to,
                    scale = scale,
                    linePaint,
                    arrowFillPaint,
                    arrowStrokePaint
                )
            }
            canvas.restore()
        }

        // LAYER 1: Background Connectors (behind cards)
        drawConnectorsList(connectors.filter { !it.isForeground })

        // LAYER 2: Background Freehand Strokes (behind cards)
        drawStrokesList(strokes)

        // LAYER 3: Note Boxes (cards, text, checklists, shapes, images)
        for (box in boxes) {
            box.setExportMode(true)
            canvas.save()
            canvas.translate((box.data.x - minX) * scale, (box.data.y - minY) * scale)
            canvas.scale(scale, scale)
            box.draw(canvas)
            canvas.restore()
            box.setExportMode(false)
        }

        // LAYER 4: Foreground Connectors (over cards and images)
        drawConnectorsList(connectors.filter { it.isForeground })

        // LAYER 5: Foreground Freehand Strokes (over cards and images)
        drawStrokesList(fgStrokes)

        return bitmap
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
            clipData = android.content.ClipData.newRawUri("NoteApp Export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share export").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
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
        val success = FileOutputStream(file).use { out ->
            writeProjectToStream(board, out)
        }
        if (!success) return null
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Pair(file, uri)
    }

    fun writePdfToStream(bitmap: Bitmap, outputStream: java.io.OutputStream): Boolean {
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)
            outputStream.use { out -> document.writeTo(out) }
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun writePngToStream(bitmap: Bitmap, outputStream: java.io.OutputStream): Boolean {
        return try {
            outputStream.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun writeProjectToStream(board: BoardData, outputStream: java.io.OutputStream): Boolean {
        return try {
            val zipOut = ZipOutputStream(outputStream)
            val imageMap = mutableMapOf<String, String>() // localPath -> zipEntryName
            var imgIndex = 1

            for (b in board.boxes) {
                val path = b.imagePath
                if (!path.isNullOrBlank() && !imageMap.containsKey(path)) {
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        val ext = file.extension.ifBlank { "png" }
                        val entryName = "images/img_${imgIndex++}.$ext"
                        imageMap[path] = entryName
                        zipOut.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { input -> input.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            }

            // Remap box image paths to zip-relative paths
            val mappedBoxes = if (imageMap.isNotEmpty()) {
                board.boxes.map { b ->
                    val relativePath = imageMap[b.imagePath]
                    if (relativePath != null) b.copy(imagePath = relativePath) else b
                }.toMutableList()
            } else {
                board.boxes
            }
            val mappedBoard = board.copy(boxes = mappedBoxes)
            val json = CanvasSerializer.serializeBoard(mappedBoard)

            zipOut.putNextEntry(ZipEntry("board.json"))
            zipOut.write(json.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.finish()
            zipOut.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importProjectFile(context: Context, uri: Uri): BoardData? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return null

            // Check if zip archive (magic bytes 0x50, 0x4B)
            val isZip = bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

            if (isZip) {
                var boardJson: String? = null
                val extractedImages = mutableMapOf<String, String>() // zipEntryName -> localFilePath
                val imagesDir = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

                val zipIn = ZipInputStream(ByteArrayInputStream(bytes))
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "board.json" || name.endsWith("/board.json")) {
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(4096)
                        var len: Int
                        while (zipIn.read(buf).also { len = it } > 0) {
                            out.write(buf, 0, len)
                        }
                        boardJson = out.toString("UTF-8")
                    } else if (name.startsWith("images/") && !entry.isDirectory) {
                        val ext = name.substringAfterLast(".", "png")
                        val uniqueName = "imported_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.$ext"
                        val destFile = File(imagesDir, uniqueName)
                        FileOutputStream(destFile).use { out ->
                            zipIn.copyTo(out)
                        }
                        extractedImages[name] = destFile.absolutePath
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                zipIn.close()

                if (boardJson == null) return null
                val deserialized = CanvasSerializer.deserializeBoard(boardJson)
                if (extractedImages.isEmpty()) {
                    deserialized
                } else {
                    val remappedBoxes = deserialized.boxes.map { b ->
                        val local = extractedImages[b.imagePath]
                        if (local != null) b.copy(imagePath = local) else b
                    }.toMutableList()
                    deserialized.copy(boxes = remappedBoxes)
                }
            } else {
                // Plain JSON legacy .noteapp file
                val json = String(bytes, Charsets.UTF_8)
                CanvasSerializer.deserializeBoard(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
