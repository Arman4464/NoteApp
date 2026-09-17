package com.noteapp.student.canvas

import android.graphics.Color
import java.util.UUID

enum class BoxKind { TEXT, IMAGE, CHECKLIST, SHAPE, BOARD, LINK }

enum class ShapeType { RECTANGLE, ROUNDED_RECT, CIRCLE, STICKY_NOTE, DIAMOND, STAR, CLOUD, TRIANGLE }

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var checked: Boolean = false
)

/**
 * Position/size/content of a single box, in "content space" — i.e. world
 * coordinates on the infinite canvas, independent of current zoom/pan.
 */
data class NoteBoxData(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var width: Float = 260f,
    var height: Float = 170f,
    var kind: BoxKind = BoxKind.TEXT,
    var shapeType: ShapeType = ShapeType.ROUNDED_RECT,
    var targetBoardId: String? = null,
    var targetBoardName: String? = null,
    var text: String = "",
    var textColor: Int = Color.parseColor("#111827"),
    var textBgColor: Int = Color.TRANSPARENT,
    var boxColor: Int = Color.WHITE,
    var strokeColor: Int = Color.parseColor("#C7C9F2"),
    var strokeWidth: Float = 3f,
    var fontSizeSp: Float = 15f,
    var fontFamily: String = "sans-serif",
    var bold: Boolean = false,
    var italic: Boolean = false,
    var imagePath: String? = null,
    var checklist: MutableList<ChecklistItem> = mutableListOf(),
    var zIndex: Int = 0
) {
    fun copyDeep(): NoteBoxData {
        return copy(
            checklist = checklist.map { it.copy() }.toMutableList()
        )
    }
}

/**
 * A freehand drawing stroke in content space coordinates.
 */
data class DrawingStrokeData(
    val id: String = UUID.randomUUID().toString(),
    val points: MutableList<Pair<Float, Float>> = mutableListOf(),
    var color: Int = Color.parseColor("#1E293B"),
    var width: Float = 6f,
    var isHighlighter: Boolean = false
) {
    fun copyDeep(): DrawingStrokeData {
        return copy(
            points = points.toMutableList()
        )
    }
}

/**
 * A directional link between two boxes, referenced by id.
 */
data class ConnectorData(
    val id: String = UUID.randomUUID().toString(),
    val fromId: String,
    val toId: String,
    var color: Int = Color.parseColor("#6366F1"),
    var style: String = "arrow" // "arrow", "double_arrow", "dot", "diamond", "plain"
)

/**
 * Metadata for a board in a multi-page workspace.
 */
data class BoardMeta(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Main Board",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var parentId: String? = null
)

/**
 * Full state of an individual board page.
 */
data class BoardData(
    val meta: BoardMeta,
    val boxes: MutableList<NoteBoxData> = mutableListOf(),
    val connectors: MutableList<ConnectorData> = mutableListOf(),
    val strokes: MutableList<DrawingStrokeData> = mutableListOf(), // Background ink layer
    val fgStrokes: MutableList<DrawingStrokeData> = mutableListOf(), // Foreground ink layer
    var panX: Float = 0f,
    var panY: Float = 0f,
    var scale: Float = 1f
)
