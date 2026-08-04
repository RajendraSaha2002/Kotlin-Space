import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.*

/**
 * Canvas-Based Painter & Sketchpad
 * Standard Swing Application written in Kotlin.
 */
class Sketchpad : JFrame("Kotlin Canvas Painter & Sketchpad") {

    // Canvas dimensions
    private val canvasWidth = 1200
    private val canvasHeight = 800

    // Drawing state variables
    private var currentColor: Color = Color.BLACK
    private var brushSize: Float = 5.0f

    // Off-screen buffer image to preserve drawings across repaints
    private val canvasImage: BufferedImage = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
    private val g2d: Graphics2D = canvasImage.createGraphics()

    private lateinit var canvasPanel: JPanel

    // Tracks last mouse position for drawing smooth lines
    private var lastX: Int = 0
    private var lastY: Int = 0

    init {
        title = "Kotlin Canvas Painter & Sketchpad"
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(1000, 700)
        setLocationRelativeTo(null)
        layout = BorderLayout()

        // Enable Anti-Aliasing for smooth strokes
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        // Initialize background canvas to white
        clearCanvasImage()

        // Create GUI Components
        initCanvasPanel()
        val toolbar = createToolbar()

        // Add to main frame
        add(toolbar, BorderLayout.NORTH)
        val scrollPane = JScrollPane(canvasPanel)
        add(scrollPane, BorderLayout.CENTER)
    }

    /**
     * Set up the canvas component and attach mouse event listeners.
     */
    private fun initCanvasPanel() {
        canvasPanel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                // Draw off-screen buffered image onto GUI panel
                g.drawImage(canvasImage, 0, 0, null)
            }
        }
        canvasPanel.preferredSize = Dimension(canvasWidth, canvasHeight)
        canvasPanel.background = Color.LIGHT_GRAY

        // Mouse Event Handler for drawing operations
        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                lastX = e.x
                lastY = e.y
                drawDot(e.x, e.y)
            }

            override fun mouseDragged(e: MouseEvent) {
                drawLine(lastX, lastY, e.x, e.y)
                lastX = e.x
                lastY = e.y
            }
        }

        canvasPanel.addMouseListener(mouseAdapter)
        canvasPanel.addMouseMotionListener(mouseAdapter)
    }

    /**
     * Fills the canvas background with white color.
     */
    private fun clearCanvasImage() {
        val oldColor = g2d.color
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, canvasWidth, canvasHeight)
        g2d.color = oldColor
        if (::canvasPanel.isInitialized) {
            canvasPanel.repaint()
        }
    }

    /**
     * Draws a single dot at the given coordinates (for single clicks).
     */
    private fun drawDot(x: Int, y: Int) {
        g2d.color = currentColor
        val offset = (brushSize / 2).toInt()
        g2d.fillOval(x - offset, y - offset, brushSize.toInt(), brushSize.toInt())
        canvasPanel.repaint()
    }

    /**
     * Draws a continuous line between previous and current mouse points.
     */
    private fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
        g2d.color = currentColor
        g2d.stroke = BasicStroke(
            brushSize,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        )
        g2d.drawLine(x1, y1, x2, y2)
        canvasPanel.repaint()
    }

    /**
     * Builds the GUI Toolbar containing brush sizes, color palette, eraser, and clear screen options.
     */
    private fun createToolbar(): JToolBar {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        toolbar.layout = FlowLayout(FlowLayout.LEFT, 8, 5)

        // Color Preview Box
        val colorPreview = JPanel().apply {
            preferredSize = Dimension(22, 22)
            background = currentColor
            border = BorderFactory.createLineBorder(Color.GRAY)
        }

        // Color Swatches
        val palette = listOf(
            "Black" to Color.BLACK,
            "Red" to Color.RED,
            "Green" to Color(34, 139, 34),
            "Blue" to Color.BLUE,
            "Yellow" to Color.YELLOW,
            "Orange" to Color.ORANGE,
            "Purple" to Color(128, 0, 128)
        )

        toolbar.add(JLabel("Colors: "))
        for ((name, color) in palette) {
            val btn = JButton().apply {
                preferredSize = Dimension(22, 22)
                background = color
                toolTipText = name
                isFocusPainted = false
                addActionListener {
                    currentColor = color
                    colorPreview.background = currentColor
                }
            }
            toolbar.add(btn)
        }

        // Custom Color Picker Button
        val customColorBtn = JButton("More...").apply {
            addActionListener {
                val selected = JColorChooser.showDialog(this@Sketchpad, "Pick Paint Color", currentColor)
                if (selected != null) {
                    currentColor = selected
                    colorPreview.background = currentColor
                }
            }
        }
        toolbar.add(customColorBtn)

        // Eraser Tool
        val eraserBtn = JButton("Eraser").apply {
            addActionListener {
                currentColor = Color.WHITE
                colorPreview.background = currentColor
            }
        }
        toolbar.add(eraserBtn)

        toolbar.add(JLabel(" Selected: "))
        toolbar.add(colorPreview)

        toolbar.addSeparator(Dimension(15, 20))

        // Brush Size Controls
        val sizeLabel = JLabel("Size: 5px")
        val sizeSlider = JSlider(JSlider.HORIZONTAL, 1, 60, 5).apply {
            preferredSize = Dimension(120, 25)
            addChangeListener {
                brushSize = value.toFloat()
                sizeLabel.text = "Size: ${value}px"
            }
        }
        toolbar.add(sizeLabel)
        toolbar.add(sizeSlider)

        toolbar.addSeparator(Dimension(15, 20))

        // Clear Canvas Button
        val clearBtn = JButton("Clear Canvas").apply {
            addActionListener {
                val confirm = JOptionPane.showConfirmDialog(
                    this@Sketchpad,
                    "Clear entire drawing canvas?",
                    "Confirm Clear",
                    JOptionPane.YES_NO_OPTION
                )
                if (confirm == JOptionPane.YES_OPTION) {
                    clearCanvasImage()
                }
            }
        }
        toolbar.add(clearBtn)

        return toolbar
    }
}

fun main() {
    // Launch GUI thread safely
    SwingUtilities.invokeLater {
        val sketchpad = Sketchpad()
        sketchpad.isVisible = true
    }
}