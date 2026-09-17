package com.noteapp.student.undo

interface CanvasCommand {
    fun execute()
    fun undo()
}

class UndoRedoManager(private val maxHistory: Int = 60) {

    private val undoStack = ArrayDeque<CanvasCommand>()
    private val redoStack = ArrayDeque<CanvasCommand>()

    var onStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(command: CanvasCommand) {
        if (undoStack.size >= maxHistory) {
            undoStack.removeFirst()
        }
        undoStack.addLast(command)
        redoStack.clear()
        notifyState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val command = undoStack.removeLast()
        command.undo()
        redoStack.addLast(command)
        notifyState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val command = redoStack.removeLast()
        command.execute()
        undoStack.addLast(command)
        notifyState()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        notifyState()
    }

    private fun notifyState() {
        onStateChanged?.invoke(canUndo, canRedo)
    }
}
