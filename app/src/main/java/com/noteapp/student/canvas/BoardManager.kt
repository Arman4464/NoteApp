package com.noteapp.student.canvas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages multiple independent boards / pages and nested sub-boards,
 * providing persistence and navigation across boards.
 */
class BoardManager(private val context: Context) {

    private val boardsDir: File by lazy {
        File(context.filesDir, "boards").apply { if (!exists()) mkdirs() }
    }
    private val manifestFile: File by lazy { File(boardsDir, "manifest.json") }
    private val legacyStateFile: File by lazy { File(context.filesDir, "canvas_state.json") }

    private val boardList = mutableListOf<BoardMeta>()
    private var activeBoardId: String = "main"

    // Breadcrumb navigation stack: for drilling down into nested sub-boards and going back
    val navigationStack = mutableListOf<String>()

    init {
        loadManifest()
    }

    private fun loadManifest() {
        boardList.clear()
        if (manifestFile.exists()) {
            try {
                val json = manifestFile.readText()
                val root = JSONObject(json)
                activeBoardId = root.optString("activeBoardId", "main")
                val array = root.optJSONArray("boards") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    boardList.add(
                        BoardMeta(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                            parentId = if (o.has("parentId") && !o.isNull("parentId")) o.getString("parentId") else null,
                            subThemeId = if (o.has("subThemeId") && !o.isNull("subThemeId")) o.getString("subThemeId") else null,
                            subThemeIsDark = if (o.has("subThemeIsDark") && !o.isNull("subThemeIsDark")) o.getBoolean("subThemeIsDark") else null
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore and reinitialize
            }
        }

        // If no boards exist, check for legacy canvas_state.json or seed interactive playground board
        if (boardList.isEmpty()) {
            if (legacyStateFile.exists()) {
                val mainMeta = BoardMeta(id = "main", name = "Main Board")
                boardList.add(mainMeta)
                activeBoardId = "main"
                try {
                    val legacyJson = legacyStateFile.readText()
                    val (boxes, connectors) = CanvasSerializer.deserialize(legacyJson)
                    val legacyBoard = BoardData(
                        meta = mainMeta,
                        boxes = boxes.toMutableList(),
                        connectors = connectors.toMutableList()
                    )
                    saveBoard(legacyBoard)
                } catch (e: Exception) {
                    saveBoard(BoardData(meta = mainMeta))
                }
            } else {
                val playgroundBoard = createDefaultPlaygroundBoard()
                boardList.add(playgroundBoard.meta)
                activeBoardId = playgroundBoard.meta.id
                saveBoard(playgroundBoard)

                // Also seed the nested child board
                val subBoard = createDefaultSubBoard(playgroundBoard.meta.id)
                boardList.add(subBoard.meta)
                saveBoard(subBoard)
            }
            saveManifest()
        }

        if (boardList.none { it.id == activeBoardId }) {
            activeBoardId = boardList.first().id
        }
    }

    fun saveManifest() {
        try {
            val root = JSONObject()
            root.put("activeBoardId", activeBoardId)
            val array = JSONArray()
            for (m in boardList) {
                val o = JSONObject()
                o.put("id", m.id)
                o.put("name", m.name)
                o.put("createdAt", m.createdAt)
                o.put("updatedAt", m.updatedAt)
                o.put("parentId", m.parentId)
                o.put("subThemeId", m.subThemeId)
                o.put("subThemeIsDark", m.subThemeIsDark)
                array.put(o)
            }
            root.put("boards", array)
            manifestFile.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBreadcrumbs(boardId: String): List<BoardMeta> {
        val chain = mutableListOf<BoardMeta>()
        var cur = boardList.firstOrNull { it.id == boardId }
        val visited = mutableSetOf<String>()
        while (cur != null && visited.add(cur.id)) {
            chain.add(0, cur)
            cur = cur.parentId?.let { pId -> boardList.firstOrNull { it.id == pId } }
        }
        return chain
    }

    fun getAllBoards(): List<BoardMeta> = boardList.toList()

    fun getActiveBoardId(): String = activeBoardId

    fun getActiveMeta(): BoardMeta {
        return boardList.firstOrNull { it.id == activeBoardId }
            ?: boardList.first().also { activeBoardId = it.id }
    }

    fun loadCurrentBoard(): BoardData {
        return loadBoard(activeBoardId)
    }

    fun loadBoard(boardId: String): BoardData {
        val file = File(boardsDir, "board_$boardId.json")
        val meta = boardList.firstOrNull { it.id == boardId } ?: BoardMeta(id = boardId, name = "Board")
        if (file.exists()) {
            return try {
                CanvasSerializer.deserializeBoard(file.readText(), meta.id, meta.name)
            } catch (e: Exception) {
                BoardData(meta = meta)
            }
        }
        return BoardData(meta = meta)
    }

    fun saveBoard(board: BoardData) {
        try {
            val meta = board.meta
            meta.updatedAt = System.currentTimeMillis()
            val existing = boardList.indexOfFirst { it.id == meta.id }
            if (existing >= 0) {
                boardList[existing] = meta
            } else {
                boardList.add(meta)
            }
            saveManifest()

            val file = File(boardsDir, "board_${meta.id}.json")
            val json = CanvasSerializer.serializeBoard(board)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createBoard(name: String, parentId: String? = null): BoardData {
        val meta = BoardMeta(name = name.ifBlank { "Untitled Board" }, parentId = parentId)
        val newBoard = BoardData(meta = meta)
        boardList.add(meta)
        activeBoardId = meta.id
        saveBoard(newBoard)
        return newBoard
    }

    fun switchBoard(targetBoardId: String): BoardData {
        activeBoardId = targetBoardId
        saveManifest()
        return loadCurrentBoard()
    }

    fun navigateToSubBoard(subBoardId: String): BoardData {
        if (!navigationStack.contains(activeBoardId)) {
            navigationStack.add(activeBoardId)
        }
        return switchBoard(subBoardId)
    }

    fun canNavigateBack(): Boolean = navigationStack.isNotEmpty()

    fun navigateBack(): BoardData? {
        if (navigationStack.isEmpty()) return null
        val prevBoardId = navigationStack.removeAt(navigationStack.size - 1)
        return switchBoard(prevBoardId)
    }

    fun renameBoard(boardId: String, newName: String) {
        val meta = boardList.firstOrNull { it.id == boardId } ?: return
        meta.name = newName.ifBlank { "Untitled Board" }
        meta.updatedAt = System.currentTimeMillis()
        saveManifest()

        // Also update the board file if exists
        val board = loadBoard(boardId)
        board.meta.name = meta.name
        saveBoard(board)
    }

    fun duplicateBoard(boardId: String): BoardData {
        val original = loadBoard(boardId)
        val newMeta = BoardMeta(
            name = "${original.meta.name} (Copy)",
            parentId = original.meta.parentId
        )
        val newBoxes = original.boxes.map { it.copyDeep() }.toMutableList()
        val idMapping = original.boxes.indices.associate {
            original.boxes[it].id to newBoxes[it].id
        }
        val newConnectors = original.connectors.mapNotNull { conn ->
            val from = idMapping[conn.fromId] ?: return@mapNotNull null
            val to = idMapping[conn.toId] ?: return@mapNotNull null
            conn.copy(id = java.util.UUID.randomUUID().toString(), fromId = from, toId = to)
        }.toMutableList()
        val newStrokes = original.strokes.map { it.copyDeep() }.toMutableList()

        val copyBoard = BoardData(
            meta = newMeta,
            boxes = newBoxes,
            connectors = newConnectors,
            strokes = newStrokes,
            panX = original.panX,
            panY = original.panY,
            scale = original.scale
        )
        boardList.add(newMeta)
        saveBoard(copyBoard)
        return copyBoard
    }

    fun deleteBoard(boardId: String): Boolean {
        if (boardList.size <= 1) return false // Prevent deleting the last board

        val file = File(boardsDir, "board_$boardId.json")
        if (file.exists()) file.delete()

        boardList.removeAll { it.id == boardId }
        navigationStack.removeAll { it == boardId }

        if (activeBoardId == boardId) {
            activeBoardId = boardList.first().id
        }
        saveManifest()
        return true
    }

    fun getOrCreatePlaygroundBoard(): BoardData {
        val existing = boardList.find { it.id == "playground" }
        return if (existing != null) {
            loadBoard(existing.id)
        } else {
            val pb = createDefaultPlaygroundBoard()
            boardList.add(0, pb.meta)
            saveBoard(pb)
            saveManifest()
            pb
        }
    }

    fun createDefaultPlaygroundBoard(): BoardData {
        val meta = BoardMeta(id = "playground", name = "Tutorial & Playground")
        val cx = 12000f
        val cy = 12000f

        val box1 = NoteBoxData(
            id = "tutorial_welcome",
            x = cx - 440f,
            y = cy - 280f,
            width = 330f,
            height = 230f,
            kind = BoxKind.TEXT,
            text = "👋 Welcome to NoteApp!\n\nThis is your personal infinite workspace. You can pan anywhere, zoom smoothly, sketch with vector ink, and organize thoughts on an endless board.\n\nCheck out the playground below!",
            fontFamily = "outfit",
            boxColor = android.graphics.Color.WHITE
        )

        val box2 = NoteBoxData(
            id = "tutorial_checklist",
            x = cx - 80f,
            y = cy - 280f,
            width = 320f,
            height = 270f,
            kind = BoxKind.CHECKLIST,
            text = "Playground Checklist",
            boxColor = android.graphics.Color.parseColor("#F0FDF4"),
            checklist = mutableListOf(
                ChecklistItem(text = "Pinch with 2 fingers to zoom", checked = true),
                ChecklistItem(text = "Drag empty canvas to lasso multiple cards", checked = false),
                ChecklistItem(text = "Drag any selected card to group-move", checked = false),
                ChecklistItem(text = "Try sketching with the Pen tool", checked = false),
                ChecklistItem(text = "Tap the Sub-board below to open it", checked = false)
            )
        )

        val box3 = NoteBoxData(
            id = "tutorial_sticky",
            x = cx + 270f,
            y = cy - 280f,
            width = 250f,
            height = 210f,
            kind = BoxKind.SHAPE,
            shapeType = ShapeType.STICKY_NOTE,
            text = "✨ Sticky Note\n\nI'm written in the bundled Caveat font! Try dragging me, or tap my 3-dot menu to change my color.",
            fontFamily = "caveat",
            fontSizeSp = 18f,
            boxColor = android.graphics.Color.parseColor("#FEF08A")
        )

        val cardA = NoteBoxData(
            id = "tutorial_card_a",
            x = cx - 440f,
            y = cy + 30f,
            width = 240f,
            height = 140f,
            kind = BoxKind.TEXT,
            text = "💡 Concept Phase\n\nMap out your architectural vision and feature goals.",
            fontFamily = "lora",
            boxColor = android.graphics.Color.parseColor("#EFF6FF")
        )

        val cardB = NoteBoxData(
            id = "tutorial_card_b",
            x = cx - 120f,
            y = cy + 30f,
            width = 240f,
            height = 140f,
            kind = BoxKind.TEXT,
            text = "🚀 Launch Phase\n\nExport your board to crisp 3x PNG or PDF.",
            fontFamily = "outfit",
            boxColor = android.graphics.Color.parseColor("#FAF5FF")
        )

        val connector = ConnectorData(
            id = "tutorial_conn",
            fromId = cardA.id,
            toId = cardB.id,
            color = android.graphics.Color.parseColor("#6366F1")
        )

        val subBoardCard = NoteBoxData(
            id = "tutorial_sub_board_card",
            x = cx + 200f,
            y = cy + 30f,
            width = 260f,
            height = 170f,
            kind = BoxKind.BOARD,
            targetBoardId = "sub_project_alpha",
            targetBoardName = "Project Alpha (Sub-Board)",
            text = "Project Alpha",
            boxColor = android.graphics.Color.parseColor("#F5F3FF")
        )

        // Sample decorative ink stroke underlining the welcome card
        val inkStroke = DrawingStrokeData(
            id = "tutorial_stroke",
            points = mutableListOf(
                Pair(cx - 430f, cy - 40f),
                Pair(cx - 360f, cy - 36f),
                Pair(cx - 280f, cy - 42f),
                Pair(cx - 190f, cy - 38f),
                Pair(cx - 120f, cy - 44f)
            ),
            color = android.graphics.Color.parseColor("#F59E0B"),
            width = 8f,
            isHighlighter = true
        )

        return BoardData(
            meta = meta,
            boxes = mutableListOf(box1, box2, box3, cardA, cardB, subBoardCard),
            connectors = mutableListOf(connector),
            strokes = mutableListOf(inkStroke)
        )
    }

    fun createDefaultSubBoard(parentId: String): BoardData {
        val meta = BoardMeta(
            id = "sub_project_alpha",
            name = "Project Alpha (Sub-Board)",
            parentId = parentId
        )
        val cx = 12000f
        val cy = 12000f

        val note = NoteBoxData(
            id = "sub_note_1",
            x = cx - 140f,
            y = cy - 80f,
            width = 280f,
            height = 160f,
            kind = BoxKind.TEXT,
            text = "📁 Project Alpha Workspace\n\nThis is an independent nested sub-board! Tap the '← Back' button in the top bar to return to the Playground.",
            fontFamily = "outfit",
            boxColor = android.graphics.Color.parseColor("#EDE9FE")
        )

        return BoardData(
            meta = meta,
            boxes = mutableListOf(note)
        )
    }
}
