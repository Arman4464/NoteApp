package com.noteapp.student.canvas

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the in-memory board (boxes + connectors + strokes + viewport) into a JSON string and
 * back, so full boards can be saved to disk and restored cleanly.
 */
object CanvasSerializer {

    fun serializeBoard(board: BoardData): String {
        val root = JSONObject()

        // Board metadata
        val metaObj = JSONObject().apply {
            put("id", board.meta.id)
            put("name", board.meta.name)
            put("createdAt", board.meta.createdAt)
            put("updatedAt", board.meta.updatedAt)
            put("parentId", board.meta.parentId)
        }
        root.put("meta", metaObj)

        // Viewport
        root.put("panX", board.panX)
        root.put("panY", board.panY)
        root.put("scale", board.scale)

        // Boxes
        val boxArray = JSONArray()
        for (b in board.boxes) {
            val o = JSONObject()
            o.put("id", b.id)
            o.put("x", b.x)
            o.put("y", b.y)
            o.put("width", b.width)
            o.put("height", b.height)
            o.put("kind", b.kind.name)
            o.put("shapeType", b.shapeType.name)
            o.put("targetBoardId", b.targetBoardId)
            o.put("targetBoardName", b.targetBoardName)
            o.put("text", b.text)
            o.put("textColor", b.textColor)
            o.put("textBgColor", b.textBgColor)
            o.put("boxColor", b.boxColor)
            o.put("strokeColor", b.strokeColor)
            o.put("strokeWidth", b.strokeWidth)
            o.put("fontSizeSp", b.fontSizeSp)
            o.put("fontFamily", b.fontFamily)
            o.put("bold", b.bold)
            o.put("italic", b.italic)
            o.put("imagePath", b.imagePath)
            o.put("zIndex", b.zIndex)

            val itemsArray = JSONArray()
            for (item in b.checklist) {
                val io = JSONObject()
                io.put("id", item.id)
                io.put("text", item.text)
                io.put("checked", item.checked)
                itemsArray.put(io)
            }
            o.put("checklist", itemsArray)
            boxArray.put(o)
        }
        root.put("boxes", boxArray)

        // Connectors
        val connArray = JSONArray()
        for (c in board.connectors) {
            val o = JSONObject()
            o.put("id", c.id)
            o.put("fromId", c.fromId)
            o.put("toId", c.toId)
            o.put("color", c.color)
            o.put("style", c.style)
            connArray.put(o)
        }
        root.put("connectors", connArray)

        // Freehand drawing strokes
        val strokeArray = JSONArray()
        for (s in board.strokes) {
            val so = JSONObject()
            so.put("id", s.id)
            so.put("color", s.color)
            so.put("width", s.width)
            so.put("isHighlighter", s.isHighlighter)
            val ptsArray = JSONArray()
            for (pt in s.points) {
                val ptArr = JSONArray()
                ptArr.put(pt.first.toDouble())
                ptArr.put(pt.second.toDouble())
                ptsArray.put(ptArr)
            }
            so.put("points", ptsArray)
            strokeArray.put(so)
        }
        root.put("strokes", strokeArray)

        // Foreground freehand drawing strokes
        val fgStrokeArray = JSONArray()
        for (s in board.fgStrokes) {
            val so = JSONObject()
            so.put("id", s.id)
            so.put("color", s.color)
            so.put("width", s.width)
            so.put("isHighlighter", s.isHighlighter)
            val ptsArray = JSONArray()
            for (pt in s.points) {
                val ptArr = JSONArray()
                ptArr.put(pt.first.toDouble())
                ptArr.put(pt.second.toDouble())
                ptsArray.put(ptArr)
            }
            so.put("points", ptsArray)
            fgStrokeArray.put(so)
        }
        root.put("fgStrokes", fgStrokeArray)

        return root.toString()
    }

    fun deserializeBoard(json: String, defaultBoardId: String = "main", defaultBoardName: String = "Main Board"): BoardData {
        val root = JSONObject(json)

        val meta = if (root.has("meta")) {
            val mo = root.getJSONObject("meta")
            BoardMeta(
                id = mo.optString("id", defaultBoardId),
                name = mo.optString("name", defaultBoardName),
                createdAt = mo.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = mo.optLong("updatedAt", System.currentTimeMillis()),
                parentId = if (mo.has("parentId") && !mo.isNull("parentId")) mo.getString("parentId") else null
            )
        } else {
            BoardMeta(id = defaultBoardId, name = defaultBoardName)
        }

        val panX = root.optDouble("panX", 0.0).toFloat()
        val panY = root.optDouble("panY", 0.0).toFloat()
        val scale = root.optDouble("scale", 1.0).toFloat()

        val boxes = mutableListOf<NoteBoxData>()
        val boxArray = root.optJSONArray("boxes") ?: JSONArray()
        for (i in 0 until boxArray.length()) {
            val o = boxArray.getJSONObject(i)
            val checklist = mutableListOf<ChecklistItem>()
            val itemsArray = o.optJSONArray("checklist")
            if (itemsArray != null) {
                for (j in 0 until itemsArray.length()) {
                    val io = itemsArray.getJSONObject(j)
                    checklist.add(
                        ChecklistItem(
                            id = io.getString("id"),
                            text = io.getString("text"),
                            checked = io.getBoolean("checked")
                        )
                    )
                }
            }
            val imagePath = if (o.has("imagePath") && !o.isNull("imagePath")) {
                o.getString("imagePath")
            } else null

            val shapeType = try {
                ShapeType.valueOf(o.optString("shapeType", "ROUNDED_RECT"))
            } catch (e: Exception) {
                ShapeType.ROUNDED_RECT
            }

            val kind = try {
                BoxKind.valueOf(o.optString("kind", "TEXT"))
            } catch (e: Exception) {
                BoxKind.TEXT
            }

            boxes.add(
                NoteBoxData(
                    id = o.getString("id"),
                    x = o.getDouble("x").toFloat(),
                    y = o.getDouble("y").toFloat(),
                    width = o.getDouble("width").toFloat(),
                    height = o.getDouble("height").toFloat(),
                    kind = kind,
                    shapeType = shapeType,
                    targetBoardId = if (o.has("targetBoardId") && !o.isNull("targetBoardId")) o.getString("targetBoardId") else null,
                    targetBoardName = if (o.has("targetBoardName") && !o.isNull("targetBoardName")) o.getString("targetBoardName") else null,
                    text = o.optString("text", ""),
                    textColor = o.optInt("textColor", Color.parseColor("#111827")),
                    textBgColor = o.optInt("textBgColor", Color.TRANSPARENT),
                    boxColor = o.optInt("boxColor", Color.WHITE),
                    strokeColor = o.optInt("strokeColor", Color.parseColor("#C7C9F2")),
                    strokeWidth = o.optDouble("strokeWidth", 3.0).toFloat(),
                    fontSizeSp = o.optDouble("fontSizeSp", 15.0).toFloat(),
                    fontFamily = o.optString("fontFamily", "sans-serif"),
                    bold = o.optBoolean("bold", false),
                    italic = o.optBoolean("italic", false),
                    imagePath = imagePath,
                    checklist = checklist,
                    zIndex = o.optInt("zIndex", 0)
                )
            )
        }

        val connectors = mutableListOf<ConnectorData>()
        val connArray = root.optJSONArray("connectors") ?: JSONArray()
        for (i in 0 until connArray.length()) {
            val o = connArray.getJSONObject(i)
            connectors.add(
                ConnectorData(
                    id = o.getString("id"),
                    fromId = o.getString("fromId"),
                    toId = o.getString("toId"),
                    color = o.optInt("color", Color.parseColor("#6366F1")),
                    style = o.optString("style", "arrow")
                )
            )
        }

        val strokes = mutableListOf<DrawingStrokeData>()
        val strokeArray = root.optJSONArray("strokes") ?: JSONArray()
        for (i in 0 until strokeArray.length()) {
            val so = strokeArray.getJSONObject(i)
            val strokeId = so.optString("id", java.util.UUID.randomUUID().toString())
            val color = so.optInt("color", Color.parseColor("#1E293B"))
            val width = so.optDouble("width", 6.0).toFloat()
            val isHighlighter = so.optBoolean("isHighlighter", false)
            val pts = mutableListOf<Pair<Float, Float>>()
            val ptsArray = so.optJSONArray("points")
            if (ptsArray != null) {
                for (j in 0 until ptsArray.length()) {
                    val ptArr = ptsArray.getJSONArray(j)
                    pts.add(Pair(ptArr.getDouble(0).toFloat(), ptArr.getDouble(1).toFloat()))
                }
            }
            strokes.add(DrawingStrokeData(id = strokeId, points = pts, color = color, width = width, isHighlighter = isHighlighter))
        }

        val fgStrokes = mutableListOf<DrawingStrokeData>()
        val fgStrokeArray = root.optJSONArray("fgStrokes")
        if (fgStrokeArray != null) {
            for (i in 0 until fgStrokeArray.length()) {
                val so = fgStrokeArray.getJSONObject(i)
                val strokeId = so.optString("id", java.util.UUID.randomUUID().toString())
                val color = so.optInt("color", Color.parseColor("#1E293B"))
                val width = so.optDouble("width", 6.0).toFloat()
                val isHighlighter = so.optBoolean("isHighlighter", false)
                val pts = mutableListOf<Pair<Float, Float>>()
                val ptsArray = so.optJSONArray("points")
                if (ptsArray != null) {
                    for (j in 0 until ptsArray.length()) {
                        val ptArr = ptsArray.getJSONArray(j)
                        pts.add(Pair(ptArr.getDouble(0).toFloat(), ptArr.getDouble(1).toFloat()))
                    }
                }
                fgStrokes.add(DrawingStrokeData(id = strokeId, points = pts, color = color, width = width, isHighlighter = isHighlighter))
            }
        }

        return BoardData(
            meta = meta,
            boxes = boxes,
            connectors = connectors,
            strokes = strokes,
            fgStrokes = fgStrokes,
            panX = panX,
            panY = panY,
            scale = scale
        )
    }

    // Backward-compatibility wrappers
    fun serialize(boxes: List<NoteBoxData>, connectors: List<ConnectorData>): String {
        val board = BoardData(
            meta = BoardMeta(id = "default", name = "Main Board"),
            boxes = boxes.toMutableList(),
            connectors = connectors.toMutableList()
        )
        return serializeBoard(board)
    }

    fun deserialize(json: String): Pair<List<NoteBoxData>, List<ConnectorData>> {
        val board = deserializeBoard(json)
        return Pair(board.boxes, board.connectors)
    }
}
