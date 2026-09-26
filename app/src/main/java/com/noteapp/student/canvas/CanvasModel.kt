package com.noteapp.student.canvas

import android.graphics.Color
import android.graphics.Path
import java.util.UUID

enum class BoxKind { TEXT, IMAGE, CHECKLIST, SHAPE, BOARD, LINK, TABLE }

enum class ShapeType { RECTANGLE, ROUNDED_RECT, CIRCLE, STICKY_NOTE, DIAMOND, STAR, CLOUD, TRIANGLE }

enum class TableIndexStyle {
    NUMBERS,   // 1, 2, 3...
    LETTERS,   // A, B, C...
    ROMAN,     // I, II, III...
    NONE
}

data class TableData(
    var rows: Int = 3,
    var cols: Int = 3,
    var rowHeaders: TableIndexStyle = TableIndexStyle.NUMBERS,
    var colHeaders: TableIndexStyle = TableIndexStyle.LETTERS,
    var cells: MutableList<MutableList<String>> = mutableListOf(),
    var customColLabels: MutableList<String> = mutableListOf(),
    var customRowLabels: MutableList<String> = mutableListOf()
) {
    fun copyDeep(): TableData {
        return TableData(
            rows = rows,
            cols = cols,
            rowHeaders = rowHeaders,
            colHeaders = colHeaders,
            cells = cells.map { it.toMutableList() }.toMutableList(),
            customColLabels = customColLabels.toMutableList(),
            customRowLabels = customRowLabels.toMutableList()
        )
    }
}

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
    var tableData: TableData? = null,
    var zIndex: Int = 0,
    var isLocked: Boolean = false
) {
    fun copyDeep(): NoteBoxData {
        return copy(
            checklist = checklist.map { it.copy() }.toMutableList(),
            tableData = tableData?.copyDeep(),
            isLocked = isLocked
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
    @Transient
    var cachedPath: Path? = null
    @Transient
    var cachedBounds: android.graphics.RectF? = null

    fun copyDeep(): DrawingStrokeData {
        return copy(
            points = points.toMutableList()
        ).also { 
            it.cachedPath = this.cachedPath 
            it.cachedBounds = this.cachedBounds
        }
    }
}

/**
 * A directional link between two boxes, or a free-floating directional arrow drawn anywhere on canvas.
 */
data class ConnectorData(
    val id: String = UUID.randomUUID().toString(),
    val fromId: String = "",
    val toId: String = "",
    var startX: Float = 0f,
    var startY: Float = 0f,
    var endX: Float = 0f,
    var endY: Float = 0f,
    var color: Int = Color.parseColor("#6366F1"),
    var strokeWidth: Float = 5f,
    var style: String = "arrow", // legacy support: "arrow", "double_arrow", "dot", "diamond", "plain"
    var headStyle: String = "triangle", // "triangle", "open", "dot", "diamond", "none"
    var tailStyle: String = "none",     // "none", "triangle", "open", "dot", "diamond", "bar"
    var isForeground: Boolean = false
) {
    val isFreeArrow: Boolean
        get() = fromId.isBlank() || toId.isBlank()
}

/**
 * Metadata for a board in a multi-page workspace.
 */
data class BoardMeta(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Main Board",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var parentId: String? = null,
    var subThemeId: String? = null,
    var subThemeIsDark: Boolean? = null
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
