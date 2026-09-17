package com.noteapp.student

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.noteapp.student.canvas.BoardManager
import com.noteapp.student.canvas.BoardMeta
import com.noteapp.student.canvas.BoxKind
import com.noteapp.student.canvas.CanvasTool
import com.noteapp.student.canvas.InfiniteCanvasView
import com.noteapp.student.canvas.MinimapView
import com.noteapp.student.canvas.NoteBoxView
import com.noteapp.student.canvas.ShapeType
import com.noteapp.student.export.ExportManager
import com.noteapp.student.home.BoardGridAdapter
import com.noteapp.student.settings.AppIconManager
import com.noteapp.student.settings.AppSettings
import com.noteapp.student.settings.GridStyle
import com.noteapp.student.settings.ThemeColors
import com.noteapp.student.settings.ThemeManager
import com.noteapp.student.settings.ThemeType
import com.noteapp.student.tutorial.TutorialDialog
import com.noteapp.student.undo.UndoRedoManager
import com.noteapp.student.util.ColorPicker
import com.noteapp.student.util.ThemedDialog
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var boardManager: BoardManager
    private lateinit var appSettings: AppSettings
    private val undoRedoManager = UndoRedoManager()

    // Screen 1: Homepage
    private lateinit var homeContainer: LinearLayout
    private lateinit var ivHomeAppIcon: ImageView
    private lateinit var tvHomeAppTitle: TextView
    private lateinit var tvHomeAppSubtitle: TextView
    private lateinit var btnHomeImport: ImageButton
    private lateinit var btnHomeSettings: ImageButton
    private lateinit var layoutHomeSearch: LinearLayout
    private lateinit var ivHomeSearchIcon: ImageView
    private lateinit var etHomeSearch: EditText
    private lateinit var rvBoards: RecyclerView
    private lateinit var layoutEmptyHome: LinearLayout
    private lateinit var ivEmptyHomeFolder: ImageView
    private lateinit var tvEmptyHomeTitle: TextView
    private lateinit var tvEmptyHomeSubtitle: TextView
    private lateinit var fabAddBoard: FloatingActionButton
    private lateinit var boardAdapter: BoardGridAdapter

    // Screen 2: Canvas
    private lateinit var canvasContainer: FrameLayout
    private lateinit var canvas: InfiniteCanvasView

    // Canvas Top Navigation Bar
    private lateinit var topBar: LinearLayout
    private lateinit var btnCanvasBack: ImageButton
    private lateinit var btnNavBack: ImageButton
    private lateinit var btnBoardSelector: LinearLayout
    private lateinit var tvCurrentBoardName: TextView
    private lateinit var btnMinimap: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnZoomFit: ImageButton
    private lateinit var btnExport: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnCollapseTopBar: ImageButton
    private lateinit var btnExpandTopBar: ImageButton

    // Minimap HUD
    private lateinit var minimapContainer: FrameLayout
    private lateinit var minimapView: MinimapView
    private lateinit var btnMinimapClose: TextView

    // Multi-Selection Bar
    private lateinit var selectionBar: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectionColor: TextView
    private lateinit var btnSelectionDuplicate: TextView
    private lateinit var btnSelectionAlign: TextView
    private lateinit var btnSelectionDelete: TextView
    private lateinit var btnSelectionClose: TextView

    // Zoom HUD Controller
    private lateinit var zoomHud: LinearLayout
    private lateinit var btnZoomOut: TextView
    private lateinit var tvZoomPercent: TextView
    private lateinit var btnZoomIn: TextView
    private lateinit var btnZoomFitHud: TextView

    // Bottom Tool Dock & Controls
    private lateinit var bottomDock: LinearLayout
    private lateinit var toolText: ImageButton
    private lateinit var toolShapes: ImageButton
    private lateinit var toolElements: ImageButton
    private lateinit var toolDraw: ImageButton
    private lateinit var toolEraser: ImageButton
    private lateinit var toolConnect: ImageButton
    private lateinit var dockDivider: View
    private lateinit var btnToolSettings: ImageButton
    private lateinit var btnCollapseDock: ImageButton
    private lateinit var btnExpandDock: ImageButton

    // Floating Contextual Tool Settings Panel (TLDraw style, Non-blocking)
    private lateinit var toolSettingsPanel: FrameLayout
    private lateinit var tvToolSettingsTitle: TextView
    private lateinit var btnCloseToolSettings: ImageButton

    // Draw Settings
    private lateinit var panelDrawSettings: LinearLayout
    private lateinit var btnDrawSizeS: TextView
    private lateinit var btnDrawSizeM: TextView
    private lateinit var btnDrawSizeL: TextView
    private lateinit var btnDrawSizeXL: TextView
    private lateinit var layoutDrawSwatchesRow1: LinearLayout
    private lateinit var layoutDrawSwatchesRow2: LinearLayout
    private lateinit var btnDrawLayer: Button

    // Eraser Settings
    private lateinit var panelEraserSettings: LinearLayout
    private lateinit var btnEraserSizeS: TextView
    private lateinit var btnEraserSizeM: TextView
    private lateinit var btnEraserSizeL: TextView
    private lateinit var btnClearCanvasInk: Button

    // Connector Settings
    private lateinit var panelConnectSettings: LinearLayout
    private lateinit var btnArrowPlain: TextView
    private lateinit var btnArrowSingle: TextView
    private lateinit var btnArrowDouble: TextView
    private lateinit var btnArrowDot: TextView
    private lateinit var btnArrowDiamond: TextView
    private lateinit var layoutConnectSwatches: LinearLayout
    private var currentConnectorColor: Int = Color.parseColor("#6366F1")

    // Formatting Toolbar
    private lateinit var formatToolbar: LinearLayout
    private var currentTextBox: NoteBoxView? = null

    // State
    private var currentConnectorStyle: String = "arrow"

    private val imagesDir: File by lazy { File(filesDir, "images").apply { if (!exists()) mkdirs() } }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val savedPath = copyImageToInternalStorage(uri)
            if (savedPath != null) {
                canvas.addBox(kind = BoxKind.IMAGE, imagePath = savedPath)
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickProjectFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val board = ExportManager.importProjectFile(this, uri)
            if (board != null) {
                boardManager.saveBoard(board)
                Toast.makeText(this, "Board '${board.meta.name}' imported successfully!", Toast.LENGTH_SHORT).show()
                refreshHomeBoards()
                showCanvasScreen(board.meta.id)
            } else {
                Toast.makeText(this, "Failed to import project file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appSettings = AppSettings(this)
        boardManager = BoardManager(this)

        initViews()
        setupHomeScreen()
        setupCanvas()
        setupToolDock()
        setupToolSettingsPanel()
        setupTopBar()
        setupMinimap()
        setupSelectionBar()
        setupZoomHud()
        setupUndoRedo()

        applySettings()

        setupBackNavigation()

        // Open Homepage initially
        showHomeScreen()

        // Show onboarding tutorial on first launch with skip button
        if (!appSettings.tutorialCompleted) {
            canvas.post {
                TutorialDialog(this, appSettings) {
                    Toast.makeText(this, "Welcome to NoteApp! Enjoy exploring your workspace.", Toast.LENGTH_SHORT).show()
                }.show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (canvasContainer.visibility == View.VISIBLE) {
            saveActiveBoard()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canvasContainer.visibility == View.VISIBLE) {
                    if (boardManager.canNavigateBack()) {
                        saveActiveBoard()
                        val parentBoard = boardManager.navigateBack()
                        if (parentBoard != null) {
                            canvas.loadBoardData(parentBoard)
                            undoRedoManager.clear()
                            updateBoardHeader()
                        }
                    } else {
                        showHomeScreen()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun initViews() {
        // Homepage Views
        homeContainer = findViewById(R.id.homeContainer)
        ivHomeAppIcon = findViewById(R.id.ivHomeAppIcon)
        tvHomeAppTitle = findViewById(R.id.tvHomeAppTitle)
        tvHomeAppSubtitle = findViewById(R.id.tvHomeAppSubtitle)
        btnHomeImport = findViewById(R.id.btnHomeImport)
        btnHomeSettings = findViewById(R.id.btnHomeSettings)
        layoutHomeSearch = findViewById(R.id.layoutHomeSearch)
        ivHomeSearchIcon = findViewById(R.id.ivHomeSearchIcon)
        etHomeSearch = findViewById(R.id.etHomeSearch)
        rvBoards = findViewById(R.id.rvBoards)
        layoutEmptyHome = findViewById(R.id.layoutEmptyHome)
        ivEmptyHomeFolder = findViewById(R.id.ivEmptyHomeFolder)
        tvEmptyHomeTitle = findViewById(R.id.tvEmptyHomeTitle)
        tvEmptyHomeSubtitle = findViewById(R.id.tvEmptyHomeSubtitle)
        fabAddBoard = findViewById(R.id.fabAddBoard)

        // Canvas Views
        canvasContainer = findViewById(R.id.canvasContainer)
        canvas = findViewById(R.id.infiniteCanvas)

        // Top Bar
        topBar = findViewById(R.id.topBar)
        btnCanvasBack = findViewById(R.id.btnCanvasBack)
        btnNavBack = findViewById(R.id.btnNavBack)
        btnBoardSelector = findViewById(R.id.btnBoardSelector)
        tvCurrentBoardName = findViewById(R.id.tvCurrentBoardName)
        btnMinimap = findViewById(R.id.btnMinimap)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnZoomFit = findViewById(R.id.btnZoomFit)
        btnExport = findViewById(R.id.btnExport)
        btnMenu = findViewById(R.id.btnMenu)
        btnCollapseTopBar = findViewById(R.id.btnCollapseTopBar)
        btnExpandTopBar = findViewById(R.id.btnExpandTopBar)

        // Minimap HUD
        minimapContainer = findViewById(R.id.minimapContainer)
        minimapView = findViewById(R.id.minimapView)
        btnMinimapClose = findViewById(R.id.btnMinimapClose)

        // Selection Bar
        selectionBar = findViewById(R.id.selectionBar)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        btnSelectionColor = findViewById(R.id.btnSelectionColor)
        btnSelectionDuplicate = findViewById(R.id.btnSelectionDuplicate)
        btnSelectionAlign = findViewById(R.id.btnSelectionAlign)
        btnSelectionDelete = findViewById(R.id.btnSelectionDelete)
        btnSelectionClose = findViewById(R.id.btnSelectionClose)

        // Zoom HUD
        zoomHud = findViewById(R.id.zoomHud)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        tvZoomPercent = findViewById(R.id.tvZoomPercent)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomFitHud = findViewById(R.id.btnZoomFitHud)

        // Bottom Tool Dock
        bottomDock = findViewById(R.id.bottomDock)
        toolText = findViewById(R.id.toolText)
        toolShapes = findViewById(R.id.toolShapes)
        toolElements = findViewById(R.id.toolElements)
        toolDraw = findViewById(R.id.toolDraw)
        toolEraser = findViewById(R.id.toolEraser)
        toolConnect = findViewById(R.id.toolConnect)
        dockDivider = findViewById(R.id.dockDivider)
        btnToolSettings = findViewById(R.id.btnToolSettings)
        btnCollapseDock = findViewById(R.id.btnCollapseDock)
        btnExpandDock = findViewById(R.id.btnExpandDock)

        // Floating Contextual Tool Settings Panel
        toolSettingsPanel = findViewById(R.id.toolSettingsPanel)
        tvToolSettingsTitle = findViewById(R.id.tvToolSettingsTitle)
        btnCloseToolSettings = findViewById(R.id.btnCloseToolSettings)

        panelDrawSettings = findViewById(R.id.panelDrawSettings)
        btnDrawSizeS = findViewById(R.id.btnDrawSizeS)
        btnDrawSizeM = findViewById(R.id.btnDrawSizeM)
        btnDrawSizeL = findViewById(R.id.btnDrawSizeL)
        btnDrawSizeXL = findViewById(R.id.btnDrawSizeXL)
        layoutDrawSwatchesRow1 = findViewById(R.id.layoutDrawSwatchesRow1)
        layoutDrawSwatchesRow2 = findViewById(R.id.layoutDrawSwatchesRow2)
        btnDrawLayer = findViewById(R.id.btnDrawLayer)

        panelEraserSettings = findViewById(R.id.panelEraserSettings)
        btnEraserSizeS = findViewById(R.id.btnEraserSizeS)
        btnEraserSizeM = findViewById(R.id.btnEraserSizeM)
        btnEraserSizeL = findViewById(R.id.btnEraserSizeL)
        btnClearCanvasInk = findViewById(R.id.btnClearCanvasInk)

        panelConnectSettings = findViewById(R.id.panelConnectSettings)
        btnArrowPlain = findViewById(R.id.btnArrowPlain)
        btnArrowSingle = findViewById(R.id.btnArrowSingle)
        btnArrowDouble = findViewById(R.id.btnArrowDouble)
        btnArrowDot = findViewById(R.id.btnArrowDot)
        btnArrowDiamond = findViewById(R.id.btnArrowDiamond)
        layoutConnectSwatches = findViewById(R.id.layoutConnectSwatches)

        // Text format toolbar
        formatToolbar = buildFormatToolbar()
        val formatParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        canvasContainer.addView(formatToolbar, formatParams)
        formatToolbar.visibility = View.GONE
    }

    // ==================== HOMEPAGE SETUP ====================

    private fun setupHomeScreen() {
        rvBoards.layoutManager = GridLayoutManager(this, 2)
        boardAdapter = BoardGridAdapter(
            boards = emptyList(),
            boardManager = boardManager,
            onBoardClick = { boardId -> showCanvasScreen(boardId) },
            onRenameClick = { board -> showRenameBoardDialog(board.id, board.name) },
            onDuplicateClick = { board ->
                boardManager.duplicateBoard(board.id)
                refreshHomeBoards()
                Toast.makeText(this, "Board duplicated", Toast.LENGTH_SHORT).show()
            },
            onExportClick = { board ->
                val boardData = boardManager.loadBoard(board.id)
                val exported = ExportManager.exportProjectFile(this, boardData)
                if (exported != null) {
                    Toast.makeText(this, "Project exported to Documents", Toast.LENGTH_SHORT).show()
                    ExportManager.shareUri(this, exported.second, "application/json")
                }
            },
            onDeleteClick = { board ->
                if (boardManager.getAllBoards().size <= 1) {
                    Toast.makeText(this, "Cannot delete the only board", Toast.LENGTH_SHORT).show()
                } else {
                    ThemedDialog.Builder(this, ThemeManager.getThemeColors(appSettings.theme))
                        .setTitle("Delete Board")
                        .setMessage("Are you sure you want to delete '${board.name}'? This action cannot be undone.")
                        .setPositiveButton("Delete") {
                            boardManager.deleteBoard(board.id)
                            refreshHomeBoards()
                            Toast.makeText(this, "Board deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel")
                        .show()
                }
            }
        )
        rvBoards.adapter = boardAdapter
        boardAdapter.themeColors = ThemeManager.getThemeColors(appSettings.theme)

        btnHomeImport.setOnClickListener {
            pickProjectFile.launch("*/*")
        }

        // Settings button is strictly accessible from Homepage per Requirement 2.4
        btnHomeSettings.setOnClickListener {
            showSettingsDialog()
        }

        fabAddBoard.setOnClickListener {
            showCreateBoardDialog(asSubBoard = false)
        }

        etHomeSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshHomeBoards(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showHomeScreen() {
        canvas.clearSelection()
        if (canvasContainer.visibility == View.VISIBLE) {
            saveActiveBoard()
        }
        canvasContainer.visibility = View.GONE
        homeContainer.visibility = View.VISIBLE
        val colors = ThemeManager.getThemeColors(appSettings.theme)
        applyHomeTheme(colors)
        refreshHomeBoards(etHomeSearch.text.toString())
    }

    private fun showCanvasScreen(boardId: String) {
        val boardData = boardManager.switchBoard(boardId)
        canvas.loadBoardData(boardData)
        undoRedoManager.clear()
        updateBoardHeader()

        homeContainer.visibility = View.GONE
        canvasContainer.visibility = View.VISIBLE
        val colors = ThemeManager.getThemeColors(appSettings.theme)
        window.statusBarColor = colors.topBarBg
        window.navigationBarColor = colors.canvasBg
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !colors.isDark
        insetsController.isAppearanceLightNavigationBars = !colors.isDark
        minimapView.invalidate()
        updateToolSettingsButtonState()
    }

    private fun refreshHomeBoards(query: String = "") {
        val all = boardManager.getAllBoards()
        val filtered = if (query.isBlank()) {
            all
        } else {
            all.filter { it.name.contains(query, ignoreCase = true) }
        }
        boardAdapter.updateData(filtered)
        layoutEmptyHome.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    // ==================== CANVAS SETUP ====================

    private fun setupCanvas() {
        canvas.undoRedoManager = undoRedoManager
        canvas.onTextFocusEvent = { refreshFormatToolbar() }

        canvas.onSelectionChanged = { count ->
            if (count > 0 && canvas.activeTool == CanvasTool.SELECT) {
                selectionBar.visibility = View.VISIBLE
                tvSelectionCount.text = if (count == 1) "1 card selected" else "$count cards selected"
            } else {
                selectionBar.visibility = View.GONE
            }
            minimapView.invalidate()
        }

        canvas.onOpenSubBoardRequested = { subBoardId ->
            saveActiveBoard()
            val newBoard = boardManager.navigateToSubBoard(subBoardId)
            canvas.loadBoardData(newBoard)
            undoRedoManager.clear()
            updateBoardHeader()
        }

        canvas.onConnectorCreated = {
            Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
            minimapView.invalidate()
        }

        canvas.onBackgroundTapped = {
            closeToolSettingsPanel()
        }

        canvas.onScaleChanged = { scale ->
            tvZoomPercent.text = "${(scale * 100).toInt()}%"
            minimapView.invalidate()
        }
    }

    private fun setupToolDock() {
        fun selectTool(tool: CanvasTool) {
            canvas.activeTool = tool
            val activeColor = canvas.themeColors.accent
            val inactiveColor = Color.parseColor("#94A3B8")

            toolDraw.imageTintList = ColorStateList.valueOf(if (tool == CanvasTool.DRAW) activeColor else inactiveColor)
            toolConnect.imageTintList = ColorStateList.valueOf(if (tool == CanvasTool.CONNECT) activeColor else inactiveColor)
            toolEraser.imageTintList = ColorStateList.valueOf(if (tool == CanvasTool.ERASER) activeColor else inactiveColor)

            if (tool != CanvasTool.SELECT) {
                selectionBar.visibility = View.GONE
            }

            updateToolSettingsButtonState()
        }

        toolText.setOnClickListener {
            closeToolSettingsPanel()
            selectTool(CanvasTool.SELECT)
            canvas.addBox(BoxKind.TEXT)
            Toast.makeText(this, "Text Note created", Toast.LENGTH_SHORT).show()
        }

        toolShapes.setOnClickListener {
            closeToolSettingsPanel()
            showShapesChooser()
        }

        toolElements.setOnClickListener {
            closeToolSettingsPanel()
            showElementsChooser()
        }

        toolDraw.setOnClickListener {
            if (canvas.activeTool == CanvasTool.DRAW) {
                selectTool(CanvasTool.SELECT)
            } else {
                selectTool(CanvasTool.DRAW)
            }
        }

        toolConnect.setOnClickListener {
            if (canvas.activeTool == CanvasTool.CONNECT) {
                selectTool(CanvasTool.SELECT)
            } else {
                selectTool(CanvasTool.CONNECT)
                Toast.makeText(this, "Connect mode: tap two cards to link them", Toast.LENGTH_SHORT).show()
            }
        }

        toolEraser.setOnClickListener {
            if (canvas.activeTool == CanvasTool.ERASER) {
                selectTool(CanvasTool.SELECT)
            } else {
                selectTool(CanvasTool.ERASER)
            }
        }

        btnToolSettings.setOnClickListener {
            toggleToolSettingsPanel()
        }

        // Collapsible Dock
        btnCollapseDock.setOnClickListener {
            closeToolSettingsPanel()
            bottomDock.visibility = View.GONE
            btnExpandDock.visibility = View.VISIBLE
        }
        btnExpandDock.setOnClickListener {
            bottomDock.visibility = View.VISIBLE
            btnExpandDock.visibility = View.GONE
        }

        selectTool(CanvasTool.SELECT)
    }

    private fun setupTopBar() {
        btnCanvasBack.setOnClickListener {
            showHomeScreen()
        }

        btnBoardSelector.setOnClickListener {
            showBoardSwitcherDialog()
        }

        btnNavBack.setOnClickListener {
            if (boardManager.canNavigateBack()) {
                saveActiveBoard()
                val parentBoard = boardManager.navigateBack()
                if (parentBoard != null) {
                    canvas.loadBoardData(parentBoard)
                    undoRedoManager.clear()
                    updateBoardHeader()
                }
            }
        }

        btnMinimap.setOnClickListener {
            toggleMinimap()
        }

        btnZoomFit.setOnClickListener { canvas.zoomToFit() }
        btnExport.setOnClickListener { showExportDialog() }
        btnMenu.setOnClickListener { showCanvasOverflowMenu() }

        // Collapsible Top Bar
        btnCollapseTopBar.setOnClickListener {
            topBar.visibility = View.GONE
            btnExpandTopBar.visibility = View.VISIBLE
        }
        btnExpandTopBar.setOnClickListener {
            topBar.visibility = View.VISIBLE
            btnExpandTopBar.visibility = View.GONE
        }
    }

    private fun setupMinimap() {
        minimapView.boxesProvider = { canvas.allBoxViews() }
        minimapView.viewportProvider = { canvas.getCurrentViewport() }
        minimapView.onTeleport = { x, y -> canvas.teleportTo(x, y) }
        btnMinimapClose.setOnClickListener {
            minimapContainer.visibility = View.GONE
        }
    }

    private fun toggleMinimap() {
        val isVisible = (minimapContainer.visibility == View.VISIBLE)
        minimapContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
        if (!isVisible) {
            minimapView.invalidate()
        }
    }

    private fun setupSelectionBar() {
        btnSelectionColor.setOnClickListener {
            ColorPicker.show(this, "Group Color", canvas.themeColors) { color ->
                canvas.setSelectedBoxesColor(color)
            }
        }

        btnSelectionDuplicate.setOnClickListener {
            canvas.duplicateSelectedBoxes()
            Toast.makeText(this, "Duplicated", Toast.LENGTH_SHORT).show()
        }

        btnSelectionAlign.setOnClickListener {
            showAlignmentMenu()
        }

        btnSelectionDelete.setOnClickListener {
            canvas.deleteSelectedBoxes()
        }

        btnSelectionClose.setOnClickListener {
            canvas.clearSelection()
        }
    }

    private fun setupZoomHud() {
        btnZoomOut.setOnClickListener { canvas.zoomOut() }
        btnZoomIn.setOnClickListener { canvas.zoomIn() }
        btnZoomFitHud.setOnClickListener { canvas.zoomToFit() }

        tvZoomPercent.setOnClickListener {
            val zoomOptions = arrayOf("25%", "50%", "75%", "100%", "150%", "200%", "Fit to Content")
            ThemedDialog.Builder(this, canvas.themeColors)
                .setTitle("Zoom Level")
                .setItems(zoomOptions) { which ->
                    when (which) {
                        0 -> canvas.setZoomLevel(0.25f)
                        1 -> canvas.setZoomLevel(0.5f)
                        2 -> canvas.setZoomLevel(0.75f)
                        3 -> canvas.resetZoom()
                        4 -> canvas.setZoomLevel(1.5f)
                        5 -> canvas.setZoomLevel(2.0f)
                        6 -> canvas.zoomToFit()
                    }
                }
                .setNegativeButton("Close")
                .show()
        }
    }

    private fun setupUndoRedo() {
        undoRedoManager.onStateChanged = { canUndo, canRedo ->
            val themeColors = ThemeManager.getThemeColors(appSettings.theme)
            val activeColor = themeColors.topBarText
            val disabledColor = if (themeColors.isDark) Color.parseColor("#475569") else Color.parseColor("#CBD5E1")
            btnUndo.isEnabled = canUndo
            btnUndo.imageTintList = ColorStateList.valueOf(if (canUndo) activeColor else disabledColor)
            btnRedo.isEnabled = canRedo
            btnRedo.imageTintList = ColorStateList.valueOf(if (canRedo) activeColor else disabledColor)
        }
        btnUndo.setOnClickListener { undoRedoManager.undo() }
        btnRedo.setOnClickListener { undoRedoManager.redo() }
    }

    // ==================== SETTINGS & THEMES ====================

    private fun applySettings() {
        val colors = ThemeManager.getThemeColors(appSettings.theme)
        canvas.applyTheme(colors)
        canvas.gridStyle = appSettings.gridStyle
        canvas.gridSnap = appSettings.gridSnap

        // Ceiling Top Bar
        topBar.backgroundTintList = ColorStateList.valueOf(colors.topBarBg)
        tvCurrentBoardName.setTextColor(colors.topBarText)
        btnCanvasBack.imageTintList = ColorStateList.valueOf(colors.topBarText)
        btnNavBack.imageTintList = ColorStateList.valueOf(colors.accent)
        btnMinimap.imageTintList = ColorStateList.valueOf(colors.accent)
        btnZoomFit.imageTintList = ColorStateList.valueOf(colors.topBarText)
        btnExport.imageTintList = ColorStateList.valueOf(colors.accent)
        btnMenu.imageTintList = ColorStateList.valueOf(colors.topBarText)
        val mutedIconColor = if (colors.isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        btnCollapseTopBar.imageTintList = ColorStateList.valueOf(mutedIconColor)

        // Ceiling Expand Pill
        btnExpandTopBar.backgroundTintList = ColorStateList.valueOf(colors.topBarBg)
        btnExpandTopBar.imageTintList = ColorStateList.valueOf(colors.accent)

        // Ground Dock & Tool Settings
        bottomDock.backgroundTintList = ColorStateList.valueOf(colors.dockBg)
        btnCollapseDock.imageTintList = ColorStateList.valueOf(mutedIconColor)
        toolText.imageTintList = ColorStateList.valueOf(colors.accent)
        toolShapes.imageTintList = ColorStateList.valueOf(colors.accent)
        toolElements.imageTintList = ColorStateList.valueOf(colors.accent)
        dockDivider.setBackgroundColor(colors.cardBorder)
        btnToolSettings.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        btnToolSettings.imageTintList = ColorStateList.valueOf(colors.accent)

        // Ground Expand Pill
        btnExpandDock.backgroundTintList = ColorStateList.valueOf(colors.dockBg)
        btnExpandDock.imageTintList = ColorStateList.valueOf(colors.accent)

        // Refresh Undo/Redo button tints
        undoRedoManager.onStateChanged?.invoke(undoRedoManager.canUndo, undoRedoManager.canRedo)

        applyHomeTheme(colors)
        AppIconManager.applyAppIcon(this, appSettings.theme)
        updateToolSettingsButtonState()
    }

    private fun applyHomeTheme(colors: ThemeColors) {
        // 1. Root Container & Window Chrome
        homeContainer.setBackgroundColor(colors.canvasBg)
        window.statusBarColor = colors.canvasBg
        window.navigationBarColor = colors.canvasBg
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !colors.isDark
        insetsController.isAppearanceLightNavigationBars = !colors.isDark

        // 2. Header Brand & Action Buttons
        ivHomeAppIcon.imageTintList = ColorStateList.valueOf(colors.accent)
        tvHomeAppTitle.setTextColor(colors.topBarText)
        val subtitleColor = if (colors.isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        tvHomeAppSubtitle.setTextColor(subtitleColor)
        btnHomeImport.imageTintList = ColorStateList.valueOf(colors.accent)
        btnHomeSettings.imageTintList = ColorStateList.valueOf(colors.accent)

        // 3. Search Bar
        layoutHomeSearch.background = GradientDrawable().apply {
            setColor(colors.cardDefaultBg)
            setStroke(3, colors.cardBorder)
            cornerRadius = 28f
        }
        ivHomeSearchIcon.imageTintList = ColorStateList.valueOf(if (colors.isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
        etHomeSearch.setTextColor(colors.topBarText)
        etHomeSearch.setHintTextColor(if (colors.isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))

        // 4. Empty State
        ivEmptyHomeFolder.imageTintList = ColorStateList.valueOf(if (colors.isDark) Color.parseColor("#475569") else Color.parseColor("#CBD5E1"))
        tvEmptyHomeTitle.setTextColor(colors.topBarText)
        tvEmptyHomeSubtitle.setTextColor(subtitleColor)

        // 5. Floating Action Button (+)
        fabAddBoard.backgroundTintList = ColorStateList.valueOf(colors.accent)
        fabAddBoard.imageTintList = ColorStateList.valueOf(Color.WHITE)

        // 6. RecyclerView Board Cards
        if (::boardAdapter.isInitialized) {
            boardAdapter.themeColors = colors
        }
    }

    private fun showSettingsDialog() {
        val themeColors = ThemeManager.getThemeColors(appSettings.theme)
        ThemedDialog.Builder(this, themeColors)
            .setTitle("NoteApp Settings")
            .addItem("Color Theme", subtitle = appSettings.theme.displayName) {
                showThemePickerDialog()
            }
            .addItem("Canvas Grid", subtitle = appSettings.gridStyle.displayName) {
                showGridStylePickerDialog()
            }
            .addItem("Grid Snapping", subtitle = if (appSettings.gridSnap) "ON (24px snap)" else "OFF (Free placement)") {
                appSettings.gridSnap = !appSettings.gridSnap
                canvas.gridSnap = appSettings.gridSnap
                Toast.makeText(this, "Grid Snap is now ${if (appSettings.gridSnap) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }
            .addItem("Default Font", subtitle = appSettings.defaultFont.replaceFirstChar { it.uppercase() }) {
                showDefaultFontDialog()
            }
            .addItem("Re-run Guided Tutorial", subtitle = "Interactive walkthrough of canvas tools") {
                TutorialDialog(this, appSettings) {
                    Toast.makeText(this, "Tutorial finished!", Toast.LENGTH_SHORT).show()
                }.show()
            }
            .addItem("About NoteApp", subtitle = "Flagship Personal Infinite Canvas") {
                showAboutDialog()
            }
            .setPositiveButton("Done")
            .show()
    }

    private fun showThemePickerDialog() {
        val themes = ThemeType.values()
        val builder = ThemedDialog.Builder(this, ThemeManager.getThemeColors(appSettings.theme))
            .setTitle("Choose Theme")
        themes.forEach { theme ->
            builder.addItem(theme.displayName, isSelected = (theme == appSettings.theme)) {
                appSettings.theme = theme
                applySettings()
                Toast.makeText(this, "Applied ${theme.displayName} (Theme & App Icon)", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel").show()
    }

    private fun showGridStylePickerDialog() {
        val styles = GridStyle.values()
        val builder = ThemedDialog.Builder(this, canvas.themeColors)
            .setTitle("Canvas Grid Style")
        styles.forEach { style ->
            builder.addItem(style.displayName, isSelected = (style == appSettings.gridStyle)) {
                appSettings.gridStyle = style
                canvas.gridStyle = style
            }
        }
        builder.setNegativeButton("Cancel").show()
    }

    private fun showDefaultFontDialog() {
        val fonts = arrayOf("Outfit (Modern Sans)", "Caveat (Handwritten)", "Kalam (Calligraphic)", "Lora (Literary Serif)", "Playfair (Display Serif)", "Space Mono (Tech Mono)")
        val keys = arrayOf("outfit", "caveat", "kalam", "lora", "playfair", "spacemono")
        val builder = ThemedDialog.Builder(this, ThemeManager.getThemeColors(appSettings.theme))
            .setTitle("Default Font")
        fonts.forEachIndexed { idx, fontName ->
            builder.addItem(fontName, isSelected = (keys[idx] == appSettings.defaultFont)) {
                appSettings.defaultFont = keys[idx]
                Toast.makeText(this, "Default font set to $fontName", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel").show()
    }

    private fun showAboutDialog() {
        ThemedDialog.Builder(this, ThemeManager.getThemeColors(appSettings.theme))
            .setTitle("NoteApp (Personal Edition)")
            .setMessage("A flagship infinite visual workspace combining Milanote, Canvio, and tldraw.\n\n• Infinite 24,000px Vector Canvas\n• Multi-Select & Group Moving\n• Nested Sub-Boards\n• Freehand Smooth Vector Inking\n• 6 Bundled Typography Fonts\n• 5 Pre-Built Aesthetic Themes\n• Interactive Birds-Eye Minimap\n• High-Res Vector PDF & PNG Export\n\nBuilt for focused personal thinking and visual organization.")
            .setPositiveButton("Awesome")
            .show()
    }

    // ==================== TOOL SETTINGS DRAWER ====================

    // ==================== CONTEXTUAL TOOL SETTINGS (TLDraw Style) ====================

    private fun setupToolSettingsPanel() {
        btnCloseToolSettings.setOnClickListener {
            closeToolSettingsPanel()
        }

        // --- DRAW PANEL SETUP ---
        fun applyDrawSize(size: Float, isHighlighter: Boolean) {
            canvas.bgDrawingOverlay.currentStrokeWidth = size
            canvas.fgDrawingOverlay.currentStrokeWidth = size
            canvas.bgDrawingOverlay.isHighlighter = isHighlighter
            canvas.fgDrawingOverlay.isHighlighter = isHighlighter
            updateDrawSizeUI()
        }

        btnDrawSizeS.setOnClickListener { applyDrawSize(3f, false) }
        btnDrawSizeM.setOnClickListener { applyDrawSize(6f, false) }
        btnDrawSizeL.setOnClickListener { applyDrawSize(12f, false) }
        btnDrawSizeXL.setOnClickListener { applyDrawSize(18f, true) }

        // Color swatches (TLDraw style palette)
        val drawColorsRow1 = listOf(
            Color.parseColor("#1E293B"), // Slate 900
            Color.parseColor("#64748B"), // Slate 500
            Color.parseColor("#9333EA"), // Purple
            Color.parseColor("#3B82F6"), // Blue
            Color.parseColor("#0EA5E9")  // Sky
        )
        val drawColorsRow2 = listOf(
            Color.parseColor("#22C55E"), // Green
            Color.parseColor("#F59E0B"), // Amber
            Color.parseColor("#F43F5E"), // Rose
            Color.parseColor("#EF4444")  // Red
        )

        fun createSwatchView(color: Int, onSelected: (Int) -> Unit): View {
            val swatch = View(this)
            val sizePx = (24 * resources.displayMetrics.density).toInt()
            val marginPx = (4 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                setMargins(marginPx, 0, marginPx, 0)
            }
            swatch.layoutParams = lp
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke((1.5f * resources.displayMetrics.density).toInt(), Color.parseColor("#CBD5E1"))
            }
            swatch.background = gd
            swatch.setOnClickListener {
                onSelected(color)
            }
            return swatch
        }

        layoutDrawSwatchesRow1.removeAllViews()
        for (color in drawColorsRow1) {
            layoutDrawSwatchesRow1.addView(createSwatchView(color) { c ->
                canvas.bgDrawingOverlay.currentColor = c
                canvas.fgDrawingOverlay.currentColor = c
                Toast.makeText(this, "Ink color changed", Toast.LENGTH_SHORT).show()
            })
        }

        layoutDrawSwatchesRow2.removeAllViews()
        for (color in drawColorsRow2) {
            layoutDrawSwatchesRow2.addView(createSwatchView(color) { c ->
                canvas.bgDrawingOverlay.currentColor = c
                canvas.fgDrawingOverlay.currentColor = c
                Toast.makeText(this, "Ink color changed", Toast.LENGTH_SHORT).show()
            })
        }

        val pickerBtn = TextView(this).apply {
            text = "🎨"
            textSize = 13f
            gravity = Gravity.CENTER
            val sizePx = (24 * resources.displayMetrics.density).toInt()
            val marginPx = (4 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                setMargins(marginPx, 0, marginPx, 0)
            }
            layoutParams = lp
            setOnClickListener {
                ColorPicker.show(this@MainActivity, "Custom Ink Color", canvas.themeColors) { c ->
                    canvas.bgDrawingOverlay.currentColor = c
                    canvas.fgDrawingOverlay.currentColor = c
                }
            }
        }
        layoutDrawSwatchesRow2.addView(pickerBtn)

        btnDrawLayer.setOnClickListener {
            canvas.isDrawingOnForeground = !canvas.isDrawingOnForeground
            updateDrawLayerButton()
            Toast.makeText(
                this,
                if (canvas.isDrawingOnForeground) "Drawing over cards (Foreground)" else "Drawing behind cards (Background)",
                Toast.LENGTH_SHORT
            ).show()
        }

        // --- ERASER PANEL SETUP ---
        fun applyEraserSize(radius: Float) {
            canvas.bgDrawingOverlay.eraserRadius = radius
            canvas.fgDrawingOverlay.eraserRadius = radius
            updateEraserSizeUI()
        }

        btnEraserSizeS.setOnClickListener { applyEraserSize(16f) }
        btnEraserSizeM.setOnClickListener { applyEraserSize(28f) }
        btnEraserSizeL.setOnClickListener { applyEraserSize(56f) }

        btnClearCanvasInk.setOnClickListener {
            ThemedDialog.Builder(this, canvas.themeColors)
                .setTitle("Clear Canvas Drawings")
                .setMessage("Are you sure you want to clear all ink strokes on this board?")
                .setPositiveButton("Clear All") {
                    canvas.bgDrawingOverlay.clearStrokes()
                    canvas.fgDrawingOverlay.clearStrokes()
                    Toast.makeText(this, "All ink drawings cleared", Toast.LENGTH_SHORT).show()
                    closeToolSettingsPanel()
                }
                .setNegativeButton("Cancel")
                .show()
        }

        // --- CONNECTOR / ARROW PANEL SETUP ---
        fun applyConnectorStyle(style: String) {
            currentConnectorStyle = style
            canvas.defaultConnectorStyle = style
            updateConnectorStyleUI()
            Toast.makeText(this, "Connector: $style", Toast.LENGTH_SHORT).show()
        }

        btnArrowPlain.setOnClickListener { applyConnectorStyle("plain") }
        btnArrowSingle.setOnClickListener { applyConnectorStyle("arrow") }
        btnArrowDouble.setOnClickListener { applyConnectorStyle("double_arrow") }
        btnArrowDot.setOnClickListener { applyConnectorStyle("dot") }
        btnArrowDiamond.setOnClickListener { applyConnectorStyle("diamond") }

        val connColors = listOf(
            Color.parseColor("#6366F1"), // Indigo (default)
            Color.parseColor("#1E293B"), // Slate
            Color.parseColor("#3B82F6"), // Blue
            Color.parseColor("#10B981"), // Emerald
            Color.parseColor("#EF4444"), // Red
            Color.parseColor("#F59E0B")  // Amber
        )
        layoutConnectSwatches.removeAllViews()
        for (color in connColors) {
            layoutConnectSwatches.addView(createSwatchView(color) { c ->
                currentConnectorColor = c
                canvas.defaultConnectorColor = c
                Toast.makeText(this, "Connector color changed", Toast.LENGTH_SHORT).show()
            })
        }
    }

    private fun updateToolSettingsButtonState() {
        if (!::btnToolSettings.isInitialized) return
        val tool = canvas.activeTool
        val hasSettings = (tool == CanvasTool.DRAW || tool == CanvasTool.ERASER || tool == CanvasTool.CONNECT)

        btnToolSettings.isEnabled = hasSettings
        btnToolSettings.isClickable = hasSettings

        if (hasSettings) {
            btnToolSettings.alpha = 1.0f
            btnToolSettings.imageTintList = ColorStateList.valueOf(canvas.themeColors.accent)
            if (::toolSettingsPanel.isInitialized && toolSettingsPanel.visibility == View.VISIBLE) {
                showToolSettingsForActiveTool()
            }
        } else {
            btnToolSettings.alpha = 0.35f
            btnToolSettings.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
            closeToolSettingsPanel()
        }
    }

    private fun closeToolSettingsPanel() {
        if (::toolSettingsPanel.isInitialized && toolSettingsPanel.visibility == View.VISIBLE) {
            toolSettingsPanel.visibility = View.GONE
        }
    }

    private fun toggleToolSettingsPanel() {
        val tool = canvas.activeTool
        val hasSettings = (tool == CanvasTool.DRAW || tool == CanvasTool.ERASER || tool == CanvasTool.CONNECT)
        if (!hasSettings) {
            closeToolSettingsPanel()
            return
        }

        if (toolSettingsPanel.visibility == View.VISIBLE) {
            toolSettingsPanel.visibility = View.GONE
        } else {
            showToolSettingsForActiveTool()
            toolSettingsPanel.visibility = View.VISIBLE
        }
    }

    private fun showToolSettingsForActiveTool() {
        val tool = canvas.activeTool
        panelDrawSettings.visibility = View.GONE
        panelEraserSettings.visibility = View.GONE
        panelConnectSettings.visibility = View.GONE

        when (tool) {
            CanvasTool.DRAW -> {
                tvToolSettingsTitle.text = "Pen & Drawing"
                panelDrawSettings.visibility = View.VISIBLE
                updateDrawSizeUI()
                updateDrawLayerButton()
            }
            CanvasTool.ERASER -> {
                tvToolSettingsTitle.text = "Eraser Settings"
                panelEraserSettings.visibility = View.VISIBLE
                updateEraserSizeUI()
            }
            CanvasTool.CONNECT -> {
                tvToolSettingsTitle.text = "Arrow & Line"
                panelConnectSettings.visibility = View.VISIBLE
                updateConnectorStyleUI()
            }
            else -> {
                closeToolSettingsPanel()
            }
        }
    }

    private fun updateDrawSizeUI() {
        val currentWidth = canvas.bgDrawingOverlay.currentStrokeWidth
        val isHighlighter = canvas.bgDrawingOverlay.isHighlighter

        fun setPillSelected(tv: TextView, selected: Boolean) {
            if (selected) {
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * resources.displayMetrics.density
                    setColor(canvas.themeColors.accent)
                }
                tv.background = gd
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(R.drawable.bg_tool_pill)
                tv.setTextColor(Color.parseColor("#1E293B"))
            }
        }

        setPillSelected(btnDrawSizeS, !isHighlighter && currentWidth <= 4f)
        setPillSelected(btnDrawSizeM, !isHighlighter && currentWidth > 4f && currentWidth <= 8f)
        setPillSelected(btnDrawSizeL, !isHighlighter && currentWidth > 8f && currentWidth <= 14f)
        setPillSelected(btnDrawSizeXL, isHighlighter)
    }

    private fun updateDrawLayerButton() {
        btnDrawLayer.text = if (canvas.isDrawingOnForeground) {
            "Drawing: Over Cards (Foreground)"
        } else {
            "Drawing: Behind Cards (Background)"
        }
    }

    private fun updateEraserSizeUI() {
        val radius = canvas.bgDrawingOverlay.eraserRadius

        fun setPillSelected(tv: TextView, selected: Boolean) {
            if (selected) {
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * resources.displayMetrics.density
                    setColor(canvas.themeColors.accent)
                }
                tv.background = gd
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(R.drawable.bg_tool_pill)
                tv.setTextColor(Color.parseColor("#1E293B"))
            }
        }

        setPillSelected(btnEraserSizeS, radius <= 20f)
        setPillSelected(btnEraserSizeM, radius > 20f && radius <= 35f)
        setPillSelected(btnEraserSizeL, radius > 35f)
    }

    private fun updateConnectorStyleUI() {
        fun setPillSelected(tv: TextView, selected: Boolean) {
            if (selected) {
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * resources.displayMetrics.density
                    setColor(canvas.themeColors.accent)
                }
                tv.background = gd
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(R.drawable.bg_tool_pill)
                tv.setTextColor(Color.parseColor("#1E293B"))
            }
        }

        setPillSelected(btnArrowPlain, currentConnectorStyle == "plain")
        setPillSelected(btnArrowSingle, currentConnectorStyle == "arrow")
        setPillSelected(btnArrowDouble, currentConnectorStyle == "double_arrow")
        setPillSelected(btnArrowDot, currentConnectorStyle == "dot")
        setPillSelected(btnArrowDiamond, currentConnectorStyle == "diamond")
    }

    // ==================== ALIGNMENT TOOLS ====================

    private fun showAlignmentMenu() {
        ThemedDialog.Builder(this, canvas.themeColors)
            .setTitle("Align Selected Cards")
            .addItem("Align Left", subtitle = "Align cards to leftmost boundary") {
                canvas.alignSelectedLeft()
            }
            .addItem("Align Top", subtitle = "Align cards to topmost boundary") {
                canvas.alignSelectedTop()
            }
            .addItem("Distribute Horizontally", subtitle = "Evenly space cards along horizontal axis") {
                canvas.distributeSelectedHorizontally()
            }
            .setNegativeButton("Cancel")
            .show()
    }

    // ==================== BOARD MANAGEMENT ====================

    private fun saveActiveBoard() {
        val currentMeta = boardManager.getActiveMeta()
        val boardData = canvas.exportBoardData(currentMeta)
        boardManager.saveBoard(boardData)
    }

    private fun updateBoardHeader() {
        val meta = boardManager.getActiveMeta()
        tvCurrentBoardName.text = meta.name
        btnNavBack.visibility = if (boardManager.canNavigateBack()) View.VISIBLE else View.GONE
    }

    private fun showBoardSwitcherDialog() {
        saveActiveBoard()
        val boards = boardManager.getAllBoards()
        val activeId = boardManager.getActiveBoardId()
        val builder = ThemedDialog.Builder(this, canvas.themeColors)
            .setTitle("Switch Board")

        boards.forEach { b ->
            val isCurrent = (b.id == activeId)
            builder.addItem(
                label = b.name,
                subtitle = if (isCurrent) "Current Active Board" else null,
                isSelected = isCurrent
            ) {
                if (!isCurrent) {
                    showCanvasScreen(b.id)
                }
            }
        }
        builder.addItem("+ Create New Board", subtitle = "Start a fresh visual canvas") {
            showCreateBoardDialog(asSubBoard = false)
        }
        builder.setNegativeButton("Close")
        builder.show()
    }

    private fun showCreateBoardDialog(asSubBoard: Boolean = false) {
        val colors = if (canvasContainer.visibility == View.VISIBLE) canvas.themeColors else ThemeManager.getThemeColors(appSettings.theme)
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            hint = if (asSubBoard) "Sub-board name (e.g. Wireframes)" else "Board name (e.g. Brainstorming)"
            setTextColor(colors.topBarText)
            setHintTextColor(if (colors.isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(if (colors.isDark) Color.parseColor("#0F172A") else Color.parseColor("#F8FAFC"))
                setStroke((1 * density).toInt(), colors.cardBorder)
            }
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
        }

        ThemedDialog.Builder(this, colors)
            .setTitle(if (asSubBoard) "New Sub-Board" else "Create New Board")
            .setCustomView(input)
            .setPositiveButton("Create") {
                val name = input.text.toString().trim().ifBlank { "Untitled Board" }
                val parentId = if (asSubBoard) boardManager.getActiveBoardId() else null
                val newBoard = boardManager.createBoard(name, parentId)

                if (asSubBoard) {
                    canvas.addBox(
                        kind = BoxKind.BOARD,
                        targetBoardId = newBoard.meta.id,
                        targetBoardName = newBoard.meta.name
                    )
                } else {
                    refreshHomeBoards()
                    showCanvasScreen(newBoard.meta.id)
                }
            }
            .setNegativeButton("Cancel")
            .show()
    }

    private fun showRenameBoardDialog(boardId: String, currentName: String) {
        val colors = if (canvasContainer.visibility == View.VISIBLE) canvas.themeColors else ThemeManager.getThemeColors(appSettings.theme)
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
            setTextColor(colors.topBarText)
            setHintTextColor(if (colors.isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(if (colors.isDark) Color.parseColor("#0F172A") else Color.parseColor("#F8FAFC"))
                setStroke((1 * density).toInt(), colors.cardBorder)
            }
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
        }

        ThemedDialog.Builder(this, colors)
            .setTitle("Rename Board")
            .setCustomView(input)
            .setPositiveButton("Save") {
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    boardManager.renameBoard(boardId, newName)
                    refreshHomeBoards()
                    updateBoardHeader()
                }
            }
            .setNegativeButton("Cancel")
            .show()
    }

    // ==================== DOCK CHOOSERS: SHAPES & ELEMENTS ====================

    private fun showShapesChooser() {
        ThemedDialog.Builder(this, canvas.themeColors)
            .setTitle("Insert Shape")
            .addItem("Rounded Rectangle", subtitle = "Standard visual container", iconRes = R.drawable.ic_tool_shapes) {
                canvas.addBox(BoxKind.SHAPE, ShapeType.ROUNDED_RECT)
            }
            .addItem("Circle", subtitle = "Circular bubble / milestone") {
                canvas.addBox(BoxKind.SHAPE, ShapeType.CIRCLE)
            }
            .addItem("Sticky Note", subtitle = "Square brainstorming memo") {
                canvas.addBox(BoxKind.SHAPE, ShapeType.STICKY_NOTE)
            }
            .addItem("Diamond", subtitle = "Decision / process node") {
                canvas.addBox(BoxKind.SHAPE, ShapeType.DIAMOND)
            }
            .addItem("Star", subtitle = "Highlight / accent star") {
                canvas.addBox(BoxKind.SHAPE, ShapeType.STAR)
            }
            .addItem("Cloud", subtitle = "Thought bubble / cloud shape") {
                canvas.addBox(BoxKind.SHAPE, ShapeType.CLOUD)
            }
            .addItem("Triangle", subtitle = "Directional / warning triangle") {
                canvas.addBox(BoxKind.SHAPE, ShapeType.TRIANGLE)
            }
            .setNegativeButton("Cancel")
            .show()
    }

    private fun showElementsChooser() {
        ThemedDialog.Builder(this, canvas.themeColors)
            .setTitle("Insert Element")
            .addItem("Checklist / To-Do", subtitle = "Interactive checklist items", iconRes = R.drawable.ic_tool_elements) {
                canvas.addBox(BoxKind.CHECKLIST)
            }
            .addItem("Sticky Note", subtitle = "Square brainstorming memo") {
                canvas.addBox(BoxKind.SHAPE, ShapeType.STICKY_NOTE)
            }
            .addItem("Photo / Image", subtitle = "Import picture from gallery") {
                pickImage.launch("image/*")
            }
            .addItem("Nested Sub-Board", subtitle = "Drill-down board within this canvas") {
                showCreateBoardDialog(asSubBoard = true)
            }
            .addItem("Web Bookmark Link", subtitle = "Interactive web link card") {
                canvas.addBox(BoxKind.LINK)
            }
            .setNegativeButton("Cancel")
            .show()
    }

    private fun showAddChooser() {
        showElementsChooser()
    }

    // ==================== FORMATTING TOOLBAR ====================

    private fun buildFormatToolbar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                cornerRadius = 24f
            }
            setPadding(16, 8, 16, 8)
            gravity = Gravity.CENTER_VERTICAL
            elevation = 16f
        }

        fun button(label: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(16, 10, 16, 10)
                setOnClickListener { onClick() }
                bar.addView(this)
            }
        }

        button("A-") { currentTextBox?.let { it.setFontSize(it.data.fontSizeSp - 2f) } }
        button("A+") { currentTextBox?.let { it.setFontSize(it.data.fontSizeSp + 2f) } }
        button("Font ▾") { currentTextBox?.let { showFontChooserDialog(it) } }
        button("B") { currentTextBox?.toggleBold() }
        button("I") { currentTextBox?.toggleItalic() }
        button("Color") {
            ColorPicker.show(this, "Text Color", canvas.themeColors) { color -> currentTextBox?.setTextColor(color) }
        }
        button("Highlight") {
            ColorPicker.show(this, "Highlight Color", canvas.themeColors) { color -> currentTextBox?.setTextBgColor(color) }
        }
        button("Done") {
            currentFocus?.clearFocus()
            hideFormatToolbar()
        }

        return bar
    }

    private fun showFontChooserDialog(box: NoteBoxView) {
        val fontLabels = arrayOf(
            "Outfit (Modern Sans)",
            "Caveat (Handwritten)",
            "Kalam (Calligraphic Pen)",
            "Lora (Literary Serif)",
            "Playfair (Display Serif)",
            "Space Mono (Tech Mono)",
            "System Sans",
            "System Serif",
            "System Monospace"
        )
        val fontKeys = arrayOf(
            "outfit",
            "caveat",
            "kalam",
            "lora",
            "playfair",
            "spacemono",
            "sans-serif",
            "serif",
            "monospace"
        )

        val currentKey = box.data.fontFamily.lowercase()
        val builder = ThemedDialog.Builder(this, canvas.themeColors)
            .setTitle("Card Font")
        fontLabels.forEachIndexed { idx, label ->
            builder.addItem(label, isSelected = (fontKeys[idx] == currentKey)) {
                box.setFontFamily(fontKeys[idx])
            }
        }
        builder.setNegativeButton("Cancel")
        builder.show()
    }

    private fun refreshFormatToolbar() {
        val focused = currentFocus
        if (focused is EditText && (focused.parent is NoteBoxView || focused.parent?.parent is NoteBoxView)) {
            val box = if (focused.parent is NoteBoxView) focused.parent as NoteBoxView else focused.parent.parent as NoteBoxView
            if (box.data.kind == BoxKind.TEXT || box.data.kind == BoxKind.SHAPE) {
                currentTextBox = box
                showFormatToolbar()
                return
            }
        }
        hideFormatToolbar()
    }

    private fun showFormatToolbar() {
        formatToolbar.visibility = View.VISIBLE
        bottomDock.visibility = View.GONE
        zoomHud.visibility = View.GONE
    }

    private fun hideFormatToolbar() {
        formatToolbar.visibility = View.GONE
        bottomDock.visibility = View.VISIBLE
        zoomHud.visibility = View.VISIBLE
        currentTextBox = null
    }

    // ==================== CANVAS OVERFLOW MENU ====================

    private fun showCanvasOverflowMenu() {
        ThemedDialog.Builder(this, canvas.themeColors)
            .setTitle("Board Options")
            .addItem("Select All Cards", subtitle = "Select all notes and shapes on canvas") {
                canvas.selectAll()
            }
            .addItem("Reset Zoom (100%)", subtitle = "Return to standard 1.0x view scale") {
                canvas.resetZoom()
            }
            .addItem("Canvas Grid", subtitle = appSettings.gridStyle.displayName) {
                showGridStylePickerDialog()
            }
            .addItem("Switch Board", subtitle = "Browse or jump to another board") {
                showBoardSwitcherDialog()
            }
            .addItem("Clear Current Board", subtitle = "Erase all cards, connectors, and drawings") {
                ThemedDialog.Builder(this, canvas.themeColors)
                    .setTitle("Clear Board")
                    .setMessage("Are you sure you want to clear all cards, connectors, and drawings on this board?")
                    .setPositiveButton("Clear All") {
                        canvas.clearAll()
                        undoRedoManager.clear()
                        saveActiveBoard()
                        minimapView.invalidate()
                    }
                    .setNegativeButton("Cancel")
                    .show()
            }
            .setNegativeButton("Close")
            .show()
    }

    // ==================== EXPORT & DOWNLOAD ====================

    private fun showExportDialog() {
        if (canvas.boxCount() == 0 && canvas.bgDrawingOverlay.getAllStrokes().isEmpty() && canvas.fgDrawingOverlay.getAllStrokes().isEmpty()) {
            Toast.makeText(this, "Add notes or sketches before exporting", Toast.LENGTH_SHORT).show()
            return
        }

        ThemedDialog.Builder(this, canvas.themeColors)
            .setTitle("Export Board (Vector Quality)")
            .addItem("Download PDF to Device", subtitle = "Direct save vector PDF to Documents") {
                currentFocus?.clearFocus()
                canvas.post { performDirectDownload(asPdf = true) }
            }
            .addItem("Share PDF", subtitle = "Share vector document via system sheet") {
                currentFocus?.clearFocus()
                canvas.post { performShare(asPdf = true) }
            }
            .addItem("Download High-Res PNG to Device", subtitle = "Save lossless raster image") {
                currentFocus?.clearFocus()
                canvas.post { performDirectDownload(asPdf = false) }
            }
            .addItem("Share High-Res PNG", subtitle = "Share image via system sheet") {
                currentFocus?.clearFocus()
                canvas.post { performShare(asPdf = false) }
            }
            .addItem("Export Project Archive (.noteapp)", subtitle = "Portable project file with full canvas state") {
                currentFocus?.clearFocus()
                canvas.post { performProjectExport() }
            }
            .setNegativeButton("Cancel")
            .show()
    }

    private fun performDirectDownload(asPdf: Boolean) {
        val bitmap = getRenderedBitmap() ?: return
        val file = if (asPdf) {
            ExportManager.downloadPdfToDevice(this, bitmap)
        } else {
            ExportManager.downloadPngToDevice(this, bitmap)
        }
        if (file != null) {
            Toast.makeText(this, "Saved to: ${file.name}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performShare(asPdf: Boolean) {
        val bitmap = getRenderedBitmap() ?: return
        val uri = if (asPdf) ExportManager.savePdf(this, bitmap) else ExportManager.savePng(this, bitmap)
        if (uri == null) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
            return
        }
        ExportManager.shareUri(this, uri, if (asPdf) "application/pdf" else "image/png")
    }

    private fun performProjectExport() {
        val currentMeta = boardManager.getActiveMeta()
        val boardData = canvas.exportBoardData(currentMeta)
        val exported = ExportManager.exportProjectFile(this, boardData)
        if (exported != null) {
            Toast.makeText(this, "Project archive saved: ${exported.first.name}", Toast.LENGTH_SHORT).show()
            ExportManager.shareUri(this, exported.second, "application/json")
        } else {
            Toast.makeText(this, "Failed to export project", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRenderedBitmap(): Bitmap? {
        val boxes = canvas.allBoxViews()
        val connectors = canvas.allConnectors()
        val bgStrokes = canvas.bgDrawingOverlay.getAllStrokes()
        val fgStrokes = canvas.fgDrawingOverlay.getAllStrokes()

        val bitmap: Bitmap? = ExportManager.renderBitmap(boxes, connectors, bgStrokes, fgStrokes, canvas.themeColors)
        if (bitmap == null) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show()
        }
        return bitmap
    }

    private fun copyImageToInternalStorage(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val outFile = File(imagesDir, "img_${System.currentTimeMillis()}.jpg")
            input.use { streamIn ->
                FileOutputStream(outFile).use { streamOut ->
                    streamIn.copyTo(streamOut)
                }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
