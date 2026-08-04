import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.*

/**
 * Desktop Screen-Recording Video Frame Splitter
 * Native Robot desktop capture streaming live 30 FPS video with real-time RGB pixel metrics.
 */
class ScreenFrameSplitter : JFrame("Desktop Screen Video Frame Splitter") {

    // Native Screen Capture Robot
    private val robot = Robot()

    // Bounding region of desktop to capture (X, Y, Width, Height)
    @Volatile private var captureRect = Rectangle(100, 100, 640, 480)

    // Streaming Thread Controls
    @Volatile private var isStreaming = false
    @Volatile private var isPaused = false
    private var streamThread: Thread? = null
    private var prevPixelData: IntArray? = null

    // --- GUI Components ---
    private val videoCanvas = VideoCanvas()
    private val fpsLabel = JLabel("FPS: 0")

    // Input fields for region coordinates
    private val xInput = JTextField("${captureRect.x}", 4)
    private val yInput = JTextField("${captureRect.y}", 4)
    private val wInput = JTextField("${captureRect.width}", 4)
    private val hInput = JTextField("${captureRect.height}", 4)

    // RGB Metrics Controls
    private val redBar = JProgressBar(0, 255)
    private val greenBar = JProgressBar(0, 255)
    private val blueBar = JProgressBar(0, 255)
    private val motionBar = JProgressBar(0, 100)
    private val colorPreviewPanel = JPanel()
    private val metricsLabel = JLabel("<html>Avg RGB: (0, 0, 0)<br>Luminance: 0 / 255</html>")

    init {
        title = "Desktop Screen Video Frame Splitter"
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(980, 620)
        layout = BorderLayout()

        // Assemble Panels
        val controlBar = createTopControlBar()
        val metricsSidebar = createMetricsSidebar()

        add(controlBar, BorderLayout.NORTH)
        add(videoCanvas, BorderLayout.CENTER)
        add(metricsSidebar, BorderLayout.EAST)

        pack()
        setLocationRelativeTo(null)

        startStreamingEngine()
    }

    /**
     * Top Toolbar: Region Coordinates, Interactive Screen Selector, and Pause Toggle.
     */
    private fun createTopControlBar(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 8)).apply {
            background = Color(35, 35, 40)
        }

        val labelColor = Color.WHITE

        val applyBtn = JButton("Apply Region").apply {
            isFocusPainted = false
            addActionListener { applyInputCoordinates() }
        }

        val selectRegionBtn = JButton("Drag-Select Region").apply {
            font = Font("SansSerif", Font.BOLD, 12)
            background = Color(33, 150, 243)
            foreground = Color.WHITE
            isFocusPainted = false
            addActionListener { openInteractiveRegionSelector() }
        }

        val pauseBtn = JButton("Pause Stream").apply {
            isFocusPainted = false
            addActionListener {
                isPaused = !isPaused
                text = if (isPaused) "Resume Stream" else "Pause Stream"
            }
        }

        panel.add(JLabel("X:").apply { foreground = labelColor })
        panel.add(xInput)
        panel.add(JLabel("Y:").apply { foreground = labelColor })
        panel.add(yInput)
        panel.add(JLabel("W:").apply { foreground = labelColor })
        panel.add(wInput)
        panel.add(JLabel("H:").apply { foreground = labelColor })
        panel.add(hInput)
        panel.add(applyBtn)
        panel.add(Box.createHorizontalStrut(10))
        panel.add(selectRegionBtn)
        panel.add(Box.createHorizontalStrut(10))
        panel.add(pauseBtn)

        return panel
    }

    /**
     * Right Sidebar: Live Frame RGB Color Breakdown, Luminance, and Motion Detection.
     */
    private fun createMetricsSidebar(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            preferredSize = Dimension(260, 0)
            background = Color(25, 25, 30)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Color(50, 50, 55)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
            )
        }

        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 0, 6, 0)
            fill = GridBagConstraints.HORIZONTAL
            gridx = 0
        }

        val headerLabel = JLabel("LIVE PIXEL METRICS").apply {
            font = Font("SansSerif", Font.BOLD, 13)
            foreground = Color(0, 230, 118)
        }
        gbc.gridy = 0
        panel.add(headerLabel, gbc)

        // FPS Readout
        fpsLabel.font = Font("Monospaced", Font.BOLD, 14)
        fpsLabel.foreground = Color.WHITE
        gbc.gridy = 1
        panel.add(fpsLabel, gbc)

        // Color Preview Box
        colorPreviewPanel.apply {
            preferredSize = Dimension(200, 40)
            background = Color.BLACK
            border = BorderFactory.createLineBorder(Color.GRAY)
        }
        gbc.gridy = 2
        panel.add(JLabel("Dominant Average Color:").apply { foreground = Color.GRAY }, gbc)
        gbc.gridy = 3
        panel.add(colorPreviewPanel, gbc)

        // Color Progress Bars
        redBar.foreground = Color(244, 67, 54)
        greenBar.foreground = Color(76, 175, 80)
        blueBar.foreground = Color(33, 150, 243)
        motionBar.foreground = Color(255, 214, 0)

        gbc.gridy = 4; panel.add(JLabel("Red Level:").apply { foreground = Color.WHITE }, gbc)
        gbc.gridy = 5; panel.add(redBar, gbc)

        gbc.gridy = 6; panel.add(JLabel("Green Level:").apply { foreground = Color.WHITE }, gbc)
        gbc.gridy = 7; panel.add(greenBar, gbc)

        gbc.gridy = 8; panel.add(JLabel("Blue Level:").apply { foreground = Color.WHITE }, gbc)
        gbc.gridy = 9; panel.add(blueBar, gbc)

        gbc.gridy = 10; panel.add(JLabel("Motion Activity Index:").apply { foreground = Color.WHITE }, gbc)
        gbc.gridy = 11; panel.add(motionBar, gbc)

        // Text Metrics
        metricsLabel.foreground = Color.LIGHT_GRAY
        gbc.gridy = 12
        panel.add(metricsLabel, gbc)

        return panel
    }

    /**
     * Starts the 30 FPS Desktop Capture loop thread using java.awt.Robot.
     */
    private fun startStreamingEngine() {
        if (isStreaming) return
        isStreaming = true

        streamThread = Thread {
            var frameCount = 0
            var lastFpsCheck = System.currentTimeMillis()

            while (isStreaming) {
                val frameStart = System.currentTimeMillis()

                if (!isPaused && captureRect.width > 0 && captureRect.height > 0) {
                    try {
                        // 1. Capture screen region via Robot
                        val img = robot.createScreenCapture(captureRect)

                        // 2. Extract structural RGB pixel metrics & motion
                        analyzePixelMetrics(img)

                        // 3. Render frame to video canvas
                        videoCanvas.updateFrame(img)

                        frameCount++
                    } catch (e: Exception) {
                        // Bounds safety fallback
                    }
                }

                // FPS Counter Update
                val now = System.currentTimeMillis()
                if (now - lastFpsCheck >= 1000) {
                    val currentFps = frameCount
                    frameCount = 0
                    lastFpsCheck = now

                    SwingUtilities.invokeLater {
                        fpsLabel.text = "Stream FPS: $currentFps"
                    }
                }

                // Maintain ~30 FPS rate (~33ms per frame)
                val elapsed = System.currentTimeMillis() - frameStart
                val sleepTime = 33 - elapsed
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Analyzes raw pixel buffer to extract structural RGB metrics & motion delta.
     */
    private fun analyzePixelMetrics(img: BufferedImage) {
        val pixels = (img.raster.dataBuffer as DataBufferInt).data
        if (pixels.isEmpty()) return

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var deltaSum = 0L

        // Subsample every 2nd pixel for high-performance throughput
        val step = 2
        var sampledCount = 0

        for (i in pixels.indices step step) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            sumR += r
            sumG += g
            sumB += b
            sampledCount++

            // Calculate Motion Delta comparing current frame to previous
            prevPixelData?.let { prev ->
                if (i < prev.size) {
                    val prevP = prev[i]
                    val pr = (prevP shr 16) and 0xFF
                    val pg = (prevP shr 8) and 0xFF
                    val pb = prevP and 0xFF

                    deltaSum += kotlin.math.abs(r - pr) + kotlin.math.abs(g - pg) + kotlin.math.abs(b - pb)
                }
            }
        }

        prevPixelData = pixels.clone()

        val avgR = (sumR / sampledCount).toInt()
        val avgG = (sumG / sampledCount).toInt()
        val avgB = (sumB / sampledCount).toInt()
        val luminance = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB)
        val motionScore = ((deltaSum.toDouble() / (sampledCount * 3 * 255)) * 1200).coerceIn(0.0, 100.0)

        // Update UI Dashboard
        SwingUtilities.invokeLater {
            redBar.value = avgR
            greenBar.value = avgG
            blueBar.value = avgB
            motionBar.value = motionScore.toInt()
            colorPreviewPanel.background = Color(avgR, avgG, avgB)
            metricsLabel.text = "<html>Avg RGB: ($avgR, $avgG, $avgB)<br>Luminance: ${luminance.toInt()} / 255</html>"
        }
    }

    private fun applyInputCoordinates() {
        try {
            val x = xInput.text.trim().toInt()
            val y = yInput.text.trim().toInt()
            val w = wInput.text.trim().toInt()
            val h = hInput.text.trim().toInt()

            val screenSize = Toolkit.getDefaultToolkit().screenSize
            val validW = w.coerceIn(50, screenSize.width)
            val validH = h.coerceIn(50, screenSize.height)
            val validX = x.coerceIn(0, screenSize.width - validW)
            val validY = y.coerceIn(0, screenSize.height - validH)

            captureRect = Rectangle(validX, validY, validW, validH)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Invalid Coordinates Entered!", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    /**
     * Launches a full-screen transparent overlay allowing user to click and drag to select capture area.
     */
    private fun openInteractiveRegionSelector() {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val selectorDialog = JDialog(this, "Drag Region Selector", true).apply {
            isUndecorated = true
            bounds = Rectangle(0, 0, screenSize.width, screenSize.height)
            background = Color(0, 0, 0, 1)
        }

        var startPt: Point? = null
        var selectionRect = Rectangle()

        val drawPanel = object : JPanel() {
            init { isOpaque = false }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g as Graphics2D
                g2.color = Color(0, 0, 0, 110)
                g2.fillRect(0, 0, width, height)

                if (selectionRect.width > 0 && selectionRect.height > 0) {
                    // Clear selected bounding region
                    g2.composite = AlphaComposite.Clear
                    g2.fillRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height)

                    // Draw selection border
                    g2.composite = AlphaComposite.SrcOver
                    g2.color = Color(0, 230, 118)
                    g2.stroke = BasicStroke(2.0f)
                    g2.drawRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height)

                    g2.color = Color.WHITE
                    g2.drawString("Selected: ${selectionRect.width} x ${selectionRect.height}", selectionRect.x + 5, selectionRect.y + 20)
                }
            }
        }

        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                startPt = e.point as Point?
                selectionRect = Rectangle(e.x, e.y, 0, 0)
                drawPanel.repaint()
            }

            override fun mouseDragged(e: MouseEvent) {
                startPt?.let { start ->
                    val x = minOf(start.x, e.x)
                    val y = minOf(start.y, e.y)
                    val w = kotlin.math.abs(e.x - start.x)
                    val h = kotlin.math.abs(e.y - start.y)
                    selectionRect = Rectangle(x, y, w, h)
                    drawPanel.repaint()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (selectionRect.width > 20 && selectionRect.height > 20) {
                    captureRect = selectionRect
                    xInput.text = "${selectionRect.x}"
                    yInput.text = "${selectionRect.y}"
                    wInput.text = "${selectionRect.width}"
                    hInput.text = "${selectionRect.height}"
                    selectorDialog.dispose()
                }
            }
        }

        drawPanel.addMouseListener(mouseAdapter)
        drawPanel.addMouseMotionListener(mouseAdapter)
        selectorDialog.add(drawPanel)
        selectorDialog.isVisible = true
    }

    /**
     * Inner Component: Smooth Video Canvas with aspect-ratio rendering.
     */
    private inner class VideoCanvas : JPanel() {
        private var currentFrame: BufferedImage? = null

        init {
            background = Color(15, 15, 20)
        }

        fun updateFrame(img: BufferedImage) {
            currentFrame = img
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            currentFrame?.let { img ->
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

                val panelW = width
                val panelH = height
                val imgW = img.width
                val imgH = img.height

                // Maintain aspect ratio scaling
                val scale = minOf(panelW.toDouble() / imgW, panelH.toDouble() / imgH)
                val drawW = (imgW * scale).toInt()
                val drawH = (imgH * scale).toInt()
                val drawX = (panelW - drawW) / 2
                val drawY = (panelH - drawH) / 2

                g2.drawImage(img, drawX, drawY, drawW, drawH, null)
            }
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val splitter = ScreenFrameSplitter()
        splitter.isVisible = true
    }
}