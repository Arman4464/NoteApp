package com.noteapp.student.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.noteapp.student.R
import com.noteapp.student.canvas.BoardManager
import com.noteapp.student.canvas.BoardMeta
import com.noteapp.student.canvas.BoxKind
import com.noteapp.student.settings.BoardSubTheme
import com.noteapp.student.settings.ThemeColors
import com.noteapp.student.util.ThemedDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BoardGridAdapter(
    private var boards: List<BoardMeta>,
    private val boardManager: BoardManager,
    private val onBoardClick: (boardId: String) -> Unit,
    private val onRenameClick: (boardMeta: BoardMeta) -> Unit,
    private val onDuplicateClick: (boardMeta: BoardMeta) -> Unit,
    private val onExportClick: (boardMeta: BoardMeta) -> Unit,
    private val onDeleteClick: (boardMeta: BoardMeta) -> Unit
) : RecyclerView.Adapter<BoardGridAdapter.BoardViewHolder>() {

    var themeColors: ThemeColors? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun updateData(newBoards: List<BoardMeta>) {
        boards = newBoards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_board_card, parent, false)
        return BoardViewHolder(view)
    }

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        val board = boards[position]
        holder.bind(board)
    }

    override fun getItemCount(): Int = boards.size

    inner class BoardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardBoardRoot: MaterialCardView = itemView.findViewById(R.id.cardBoardRoot)
        private val minimapThumbnail: BoardThumbnailMinimapView = itemView.findViewById(R.id.boardMinimapThumbnail)
        private val tvCardCountBadge: TextView = itemView.findViewById(R.id.tvCardCountBadge)
        private val tvBoardTitle: TextView = itemView.findViewById(R.id.tvBoardTitle)
        private val tvBoardCreated: TextView = itemView.findViewById(R.id.tvBoardCreated)
        private val tvBoardUpdated: TextView = itemView.findViewById(R.id.tvBoardUpdated)
        private val tvBoardDetails: TextView = itemView.findViewById(R.id.tvBoardDetails)
        private val btnBoardCardMenu: ImageButton = itemView.findViewById(R.id.btnBoardCardMenu)

        fun bind(board: BoardMeta) {
            tvBoardTitle.text = board.name

            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            tvBoardCreated.text = "Created: " + dateFormat.format(Date(board.createdAt))
            tvBoardUpdated.text = "Updated: " + dateFormat.format(Date(board.updatedAt))

            // Load board data to feed living minimap and breakdown stats
            val boardData = try { boardManager.loadBoard(board.id) } catch (e: Exception) { null }

            // Resolve board-specific subtheme (with global fallback)
            val fallback = themeColors ?: ThemeColors.modernClean()
            val boardColors = BoardSubTheme.resolveThemeColors(board.subThemeId, board.subThemeIsDark, fallback = fallback)

            minimapThumbnail.boardData = boardData
            minimapThumbnail.themeColors = boardColors

            // Apply Theme to Card
            cardBoardRoot.setCardBackgroundColor(boardColors.cardDefaultBg)
            cardBoardRoot.strokeColor = boardColors.cardBorder
            cardBoardRoot.strokeWidth = 3 // ~1.5dp

            tvBoardTitle.setTextColor(boardColors.topBarText)
            val subColor = if (boardColors.isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
            tvBoardCreated.setTextColor(subColor)
            tvBoardUpdated.setTextColor(subColor)
            tvBoardDetails.setTextColor(boardColors.accent)
            btnBoardCardMenu.imageTintList = ColorStateList.valueOf(subColor)

            tvCardCountBadge.background = GradientDrawable().apply {
                setColor(boardColors.accent)
                cornerRadius = 24f * itemView.resources.displayMetrics.density
            }
            tvCardCountBadge.setTextColor(Color.WHITE)

            if (boardData != null) {
                val boxes = boardData.boxes
                val totalCards = boxes.size
                val textCount = boxes.count { it.kind == BoxKind.TEXT }
                val taskCount = boxes.count { it.kind == BoxKind.CHECKLIST }
                val imgCount = boxes.count { it.kind == BoxKind.IMAGE }
                val shapeCount = boxes.count { it.kind == BoxKind.SHAPE }
                val strokeCount = boardData.strokes.size + boardData.fgStrokes.size

                val totalElements = totalCards + strokeCount
                tvCardCountBadge.text = if (totalElements == 1) "1 item" else "$totalElements items"

                val parts = mutableListOf<String>()
                if (textCount > 0) parts.add("$textCount note${if (textCount > 1) "s" else ""}")
                if (taskCount > 0) parts.add("$taskCount to-do${if (taskCount > 1) "s" else ""}")
                if (imgCount > 0) parts.add("$imgCount image${if (imgCount > 1) "s" else ""}")
                if (shapeCount > 0) parts.add("$shapeCount shape${if (shapeCount > 1) "s" else ""}")
                if (strokeCount > 0) parts.add("$strokeCount ink")

                tvBoardDetails.text = if (parts.isNotEmpty()) parts.joinToString(" • ") else "Blank canvas ready"
            } else {
                tvCardCountBadge.text = "0 items"
                tvBoardDetails.text = "Blank canvas ready"
            }

            itemView.setOnClickListener {
                onBoardClick(board.id)
            }

            btnBoardCardMenu.setOnClickListener {
                ThemedDialog.Builder(itemView.context, boardColors)
                    .setTitle(board.name)
                    .addItem("Rename", subtitle = "Change board title") { onRenameClick(board) }
                    .addItem("Duplicate", subtitle = "Make a duplicate copy of this board") { onDuplicateClick(board) }
                    .addItem("Export Project (.noteapp)", subtitle = "Export archive to Documents") { onExportClick(board) }
                    .addItem("Delete", subtitle = "Permanently remove this board") { onDeleteClick(board) }
                    .setNegativeButton("Cancel")
                    .show()
            }
        }
    }
}
