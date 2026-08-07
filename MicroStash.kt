import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.*
import javax.swing.border.* // Includes CompoundBorder, EmptyBorder, MatteBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.plaf.basic.BasicScrollBarUI
import javax.swing.plaf.basic.BasicSplitPaneDivider
import javax.swing.plaf.basic.BasicSplitPaneUI

// --- Data Models ---
data class ClipItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val previewText: String
        get() = text.trim().replace("\r", "").replace("\n", " ").let {
            if (it.length > 90) it.substring(0, 90) + "..." else it
        }
}

// --- Filterable List Model ---
class FilterableClipListModel : AbstractListModel<ClipItem>() {
    private val allClips = mutableListOf<ClipItem>()
    private var filteredClips = mutableListOf<ClipItem>()

    var filterQuery: String = ""
        set(value) {
            field = value.trim()
            applyFilter()
        }

    fun addClip(clip: ClipItem) {
        // Avoid duplicate top item
        if (allClips.isNotEmpty() && allClips.first().text == clip.text) return

        // Remove identical prior instance if present
        allClips.removeAll { it.text == clip.text }
        allClips.add(0, clip)

        // Limit maximum history stash depth
        if (allClips.size > 100) {
            allClips.removeAt(allClips.size - 1)
        }
        applyFilter()
    }

    fun clear() {
        val sizeBefore = filteredClips.size
        allClips.clear()
        filteredClips.clear()
        if (sizeBefore > 0) {
            fireIntervalRemoved(this, 0, sizeBefore - 1)
        }
    }

    private fun applyFilter() {
        val oldSize = filteredClips.size
        filteredClips = if (filterQuery.isBlank()) {
            allClips.toMutableList()
        } else {
            allClips.filter { it.text.contains(filterQuery, ignoreCase = true) }.toMutableList()
        }
        fireContentsChanged(this, 0, maxOf(oldSize, filteredClips.size))
    }

    override fun getSize(): Int = filteredClips.size
    override fun getElementAt(index: Int): ClipItem = filteredClips[index]
    fun getTotalCount(): Int = allClips.size
}

// --- Custom Theme Palette ---
object Theme {
    val BG_DARK = Color(0x1E, 0x1E, 0x2E)
    val PANEL_BG = Color(0x18, 0x18, 0x25)
    val CARD_BG = Color(0x11, 0x11, 0x1B)
    val HOVER_BG = Color(0x31, 0x32, 0x44)
    val ACCENT_BLUE = Color(0x89, 0xB4, 0xFA)
    val ACCENT_GREEN = Color(0xA6, 0xE3, 0xA1)
    val ACCENT_RED = Color(0xF3, 0x8B, 0xBA)
    val TEXT_PRIMARY = Color(0xCD, 0xD6, 0xF4)
    val TEXT_MUTED = Color(0xA6, 0xAD, 0xC8)
    val BORDER_COLOR = Color(0x31, 0x32, 0x44)
    val FONT_MAIN = Font("Segoe UI", Font.PLAIN, 12)
    val FONT_BOLD = Font("Segoe UI", Font.BOLD, 12)
    val FONT_MONO = Font("Consolas", Font.PLAIN, 13)
}

// --- Custom ScrollBar UI ---
class DarkScrollBarUI : BasicScrollBarUI() {
    override fun configureScrollBarColors() {
        thumbColor = Color(0x45, 0x47, 0x5A)
        trackColor = Theme.BG_DARK
    }

    override fun createDecreaseButton(orientation: Int): JButton = createZeroButton()
    override fun createIncreaseButton(orientation: Int): JButton = createZeroButton()

    private fun createZeroButton(): JButton {
        val btn = JButton("")
        btn.preferredSize = Dimension(0, 0)
        return btn
    }

    override fun paintThumb(g: Graphics, c: JComponent, bounds: Rectangle) {
        if (bounds.isEmpty || !scrollbar.isEnabled) return
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = thumbColor
        g2.fillRoundRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4, 6, 6)
        g2.dispose()
    }

    override fun paintTrack(g: Graphics, c: JComponent, bounds: Rectangle) {
        val g2 = g.create() as Graphics2D
        g2.color = trackColor
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
        g2.dispose()
    }
}

// --- Clip Item List Renderer ---
class ClipItemRenderer : ListCellRenderer<ClipItem> {
    private val panel = JPanel(BorderLayout(6, 2))
    private val previewLabel = JLabel("")
    private val metaLabel = JLabel("")

    init {
        panel.border = EmptyBorder(8, 10, 8, 10)
        panel.isOpaque = true

        previewLabel.font = Theme.FONT_MAIN
        metaLabel.font = Font("Segoe UI", Font.PLAIN, 10)

        val box = Box.createVerticalBox()
        box.add(previewLabel)
        box.add(Box.createVerticalStrut(3))
        box.add(metaLabel)

        panel.add(box, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out ClipItem>,
        value: ClipItem?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        if (value != null) {
            previewLabel.text = value.previewText
            val timeStr = SimpleDateFormat("HH:mm:ss").format(Date(value.timestamp))
            metaLabel.text = "$timeStr • ${value.text.length} chars"
        }

        if (isSelected) {
            panel.background = Theme.HOVER_BG
            previewLabel.foreground = Theme.ACCENT_BLUE
            metaLabel.foreground = Theme.TEXT_MUTED
        } else {
            panel.background = Theme.CARD_BG
            previewLabel.foreground = Theme.TEXT_PRIMARY
            metaLabel.foreground = Color(0x6C, 0x70, 0x86)
        }

        return panel
    }
}

// --- Main Application Window ---
class MicroStashApp : JFrame() {
    private val clipListModel = FilterableClipListModel()
    private val clipList = JList(clipListModel)
    private val scratchpadArea = JTextArea()
    private val statusLabel = JLabel(" Clipboard monitoring active")
    private val countLabel = JLabel("0 chars | 0 words")
    private val historyHeaderLabel = JLabel("CLIPBOARD HISTORY (0)")

    private var lastClipboardContent: String = ""
    private var isPinned = false
    private var initialClick: Point? = null

    init {
        title = "Micro Scratchpad & Stash"
        size = Dimension(360, 640)
        minimumSize = Dimension(300, 450)
        isUndecorated = true
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)

        val rootPanel = JPanel(BorderLayout())
        rootPanel.background = Theme.BG_DARK
        rootPanel.border = BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1)

        // Title Bar & Controls
        rootPanel.add(buildTitleBar(), BorderLayout.NORTH)

        // Main Split Content View
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, buildScratchpadPanel(), buildHistoryPanel())
        splitPane.isOneTouchExpandable = false
        splitPane.dividerSize = 4
        splitPane.resizeWeight = 0.45
        splitPane.background = Theme.BG_DARK
        splitPane.setUI(object : BasicSplitPaneUI() {
            override fun createDefaultDivider(): BasicSplitPaneDivider {
                return object : BasicSplitPaneDivider(this) {
                    override fun paint(g: Graphics) {
                        g.color = Theme.BORDER_COLOR
                        g.fillRect(0, 0, width, height)
                    }
                }
            }
        })

        rootPanel.add(splitPane, BorderLayout.CENTER)
        rootPanel.add(buildStatusBar(), BorderLayout.SOUTH)

        contentPane = rootPanel

        // Initialize Clipboard Timer Poller (Runs every 800ms)
        startClipboardPoller()
    }

    private fun buildTitleBar(): JPanel {
        val titleBar = JPanel(BorderLayout())
        titleBar.background = Theme.PANEL_BG
        titleBar.border = MatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR)

        // Draggable window action
        val dragAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                initialClick = e.point as Point?
            }
            override fun mouseDragged(e: MouseEvent) {
                val curr = location
                val xMoved = e.xOnScreen - (curr.x + initialClick!!.x)
                val yMoved = e.yOnScreen - (curr.y + initialClick!!.y)
                setLocation(curr.x + xMoved, curr.y + yMoved)
            }
        }
        titleBar.addMouseListener(dragAdapter)
        titleBar.addMouseMotionListener(dragAdapter)

        val titleLabel = JLabel("  MICRO STASH", JLabel.LEFT)
        titleLabel.font = Theme.FONT_BOLD
        titleLabel.foreground = Theme.ACCENT_BLUE

        val controls = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2))
        controls.isOpaque = false

        val pinBtn = createIconButton("📌", "Pin Always-on-Top") {
            isPinned = !isPinned
            isAlwaysOnTop = isPinned
            (it.source as JButton).foreground = if (isPinned) Theme.ACCENT_GREEN else Theme.TEXT_PRIMARY
            showStatus(if (isPinned) "Window pinned on top" else "Window unpinned")
        }

        val minBtn = createIconButton("—", "Minimize") {
            extendedState = ICONIFIED
        }

        val closeBtn = createIconButton("✕", "Close") {
            System.exit(0)
        }
        closeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { closeBtn.foreground = Theme.ACCENT_RED }
            override fun mouseExited(e: MouseEvent) { closeBtn.foreground = Theme.TEXT_PRIMARY }
        })

        controls.add(pinBtn)
        controls.add(minBtn)
        controls.add(closeBtn)

        titleBar.add(titleLabel, BorderLayout.WEST)
        titleBar.add(controls, BorderLayout.EAST)
        return titleBar
    }

    private fun buildScratchpadPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = Theme.BG_DARK

        // Header Section
        val header = JPanel(BorderLayout())
        header.background = Theme.BG_DARK
        header.border = EmptyBorder(6, 10, 4, 10)

        val label = JLabel("SCRATCHPAD")
        label.font = Theme.FONT_BOLD
        label.foreground = Theme.TEXT_MUTED

        countLabel.font = Font("Segoe UI", Font.PLAIN, 10)
        countLabel.foreground = Theme.TEXT_MUTED

        header.add(label, BorderLayout.WEST)
        header.add(countLabel, BorderLayout.EAST)

        // Text Area Setup
        scratchpadArea.font = Theme.FONT_MONO
        scratchpadArea.background = Theme.BG_DARK
        scratchpadArea.foreground = Theme.TEXT_PRIMARY
        scratchpadArea.caretColor = Theme.ACCENT_BLUE
        scratchpadArea.lineWrap = true
        scratchpadArea.wrapStyleWord = true
        scratchpadArea.border = EmptyBorder(8, 10, 8, 10)

        // Live text statistics update
        scratchpadArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateCounts()
            override fun removeUpdate(e: DocumentEvent) = updateCounts()
            override fun changedUpdate(e: DocumentEvent) = updateCounts()

            private fun updateCounts() {
                val text = scratchpadArea.text
                val charCount = text.length
                val wordCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
                countLabel.text = "$charCount chars | $wordCount words"
            }
        })

        val scrollPane = JScrollPane(scratchpadArea)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.verticalScrollBar.setUI(DarkScrollBarUI())
        scrollPane.horizontalScrollBar.setUI(DarkScrollBarUI())

        panel.add(header, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    private fun buildHistoryPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = Theme.PANEL_BG

        // Header Controls
        val topContainer = JPanel(BorderLayout(4, 4))
        topContainer.background = Theme.PANEL_BG
        topContainer.border = EmptyBorder(6, 10, 6, 10)

        historyHeaderLabel.font = Theme.FONT_BOLD
        historyHeaderLabel.foreground = Theme.TEXT_MUTED

        val clearBtn = createIconButton("🗑", "Clear Clipboard History") {
            clipListModel.clear()
            updateHistoryHeader()
            showStatus("History cleared")
        }

        val headerTop = JPanel(BorderLayout())
        headerTop.isOpaque = false
        headerTop.add(historyHeaderLabel, BorderLayout.WEST)
        headerTop.add(clearBtn, BorderLayout.EAST)

        // Search Bar
        val searchField = JTextField()
        searchField.font = Theme.FONT_MAIN
        searchField.background = Theme.CARD_BG
        searchField.foreground = Theme.TEXT_PRIMARY
        searchField.caretColor = Theme.ACCENT_BLUE
        searchField.border = CompoundBorder(
            MatteBorder(1, 1, 1, 1, Theme.BORDER_COLOR),
            EmptyBorder(4, 6, 4, 6)
        )

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { clipListModel.filterQuery = searchField.text }
            override fun removeUpdate(e: DocumentEvent) { clipListModel.filterQuery = searchField.text }
            override fun changedUpdate(e: DocumentEvent) { clipListModel.filterQuery = searchField.text }
        })

        topContainer.add(headerTop, BorderLayout.NORTH)
        topContainer.add(searchField, BorderLayout.SOUTH)

        // Clipboard History List
        clipList.cellRenderer = ClipItemRenderer()
        clipList.background = Theme.PANEL_BG
        clipList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        clipList.fixedCellHeight = 46

        // Re-copy on Click Action
        clipList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = clipList.locationToIndex(e.point)
                if (index >= 0) {
                    val selectedItem = clipListModel.getElementAt(index)
                    copyToSystemClipboard(selectedItem.text)
                }
            }
        })

        val scrollPane = JScrollPane(clipList)
        scrollPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR)
        scrollPane.verticalScrollBar.setUI(DarkScrollBarUI())

        panel.add(topContainer, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    private fun buildStatusBar(): JPanel {
        val statusPanel = JPanel(BorderLayout())
        statusPanel.background = Theme.PANEL_BG
        statusPanel.border = CompoundBorder(
            MatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR),
            EmptyBorder(4, 8, 4, 8)
        )

        statusLabel.font = Font("Segoe UI", Font.PLAIN, 11)
        statusLabel.foreground = Theme.TEXT_MUTED

        statusPanel.add(statusLabel, BorderLayout.WEST)
        return statusPanel
    }

    private fun createIconButton(iconText: String, tooltip: String, onClick: (ActionEvent) -> Unit): JButton {
        val btn = JButton(iconText)
        btn.toolTipText = tooltip
        btn.font = Font("Segoe UI Symbol", Font.PLAIN, 12)
        btn.foreground = Theme.TEXT_PRIMARY
        btn.isFocusPainted = false
        btn.isBorderPainted = false
        btn.isContentAreaFilled = false
        btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        btn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                btn.isContentAreaFilled = true
                btn.background = Theme.HOVER_BG
            }
            override fun mouseExited(e: MouseEvent) {
                btn.isContentAreaFilled = false
            }
        })
        btn.addActionListener(onClick)
        return btn
    }

    private fun startClipboardPoller() {
        val timer = Timer(800) {
            try {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    val currentText = clipboard.getData(DataFlavor.stringFlavor) as? String
                    if (!currentText.isNullOrBlank() && currentText != lastClipboardContent) {
                        lastClipboardContent = currentText
                        clipListModel.addClip(ClipItem(currentText))
                        updateHistoryHeader()
                        showStatus("Captured copy (${currentText.length} chars)")
                    }
                }
            } catch (_: Exception) {
                // Ignore transient clipboard locks
            }
        }
        timer.start()
    }

    private fun copyToSystemClipboard(text: String) {
        try {
            val selection = StringSelection(text)
            lastClipboardContent = text // Avoid duplicate re-capture loops
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            showStatus("Re-copied to clipboard!")
        } catch (e: Exception) {
            showStatus("Failed to copy text")
        }
    }

    private fun updateHistoryHeader() {
        historyHeaderLabel.text = "CLIPBOARD HISTORY (${clipListModel.getTotalCount()})"
    }

    private fun showStatus(msg: String) {
        statusLabel.text = " $msg"
        statusLabel.foreground = Theme.ACCENT_GREEN

        val resetTimer = Timer(2500) {
            statusLabel.text = " Clipboard monitoring active"
            statusLabel.foreground = Theme.TEXT_MUTED
        }
        resetTimer.isRepeats = false
        resetTimer.start()
    }
}

// --- Application Entry Point ---
fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {}

        val app = MicroStashApp()
        app.isVisible = true
    }
}