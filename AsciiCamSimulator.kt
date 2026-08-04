import java.awt.*
import javax.swing.*
import kotlin.math.*

/**
 * Live Video ASCII Filter Cam Simulator
 * Converts dynamic algorithmic video feeds into real-time ASCII typography frames.
 */
class AsciiCamSimulator : JFrame("Live Video ASCII Filter Cam Simulator") {

    // --- Video Matrix Resolution ---
    private var cols = 90
    private var rows = 42

    // --- State Variables ---
    @Volatile private var feedMode = 0      // 0 = 3D Geometry, 1 = Plasma Fluid, 2 = Radar Wave, 3 = Quantum Tunnel
    @Volatile private var targetFps = 30
    @Volatile private var isInverted = false
    private var time = 0.0

    // ASCII Density Ramp (Darkest to Brightest)
    private val densityRamp = charArrayOf(' ', '.', ':', '-', '=', '+', '*', '#', '%', '@')

    // --- GUI Components ---
    private val textArea = JTextArea()
    private val fpsLabel = JLabel("FPS: 30")
    private var frameTimer: Timer? = null

    // Pre-allocated StringBuilder for fast frame rendering
    private val frameBuffer = StringBuilder()

    init {
        title = "Live Video ASCII Filter Cam Simulator"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false
        layout = BorderLayout()

        // Create UI Panels
        val topToolbar = createToolbar()
        val displayPanel = createDisplayPanel()

        add(topToolbar, BorderLayout.NORTH)
        add(displayPanel, BorderLayout.CENTER)

        pack()
        setLocationRelativeTo(null)

        startVideoPipeline()
    }

    /**
     * Creates top control toolbar with Feed selector, FPS slider, Theme picker, and Invert checkbox.
     */
    private fun createToolbar(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.CENTER, 12, 8)).apply {
            background = Color(30, 30, 35)
        }

        val labelColor = Color.WHITE

        // 1. Camera Feed Selector
        val feedCombo = JComboBox(arrayOf("3D Geometry Cam", "Plasma Fluid Cam", "Optical Radar", "Quantum Tunnel")).apply {
            isFocusable = false
            addActionListener {
                feedMode = selectedIndex
            }
        }

        // 2. Color Theme Selector
        val themeCombo = JComboBox(arrayOf("Matrix Green", "Amber CRT", "Monochrome White", "Cyberpunk Pink")).apply {
            isFocusable = false
            addActionListener {
                when (selectedIndex) {
                    0 -> applyTheme(Color.BLACK, Color(0, 255, 100))  // Matrix Green
                    1 -> applyTheme(Color(20, 15, 0), Color(255, 170, 0)) // Amber CRT
                    2 -> applyTheme(Color.BLACK, Color.WHITE)         // White
                    3 -> applyTheme(Color(20, 0, 20), Color(255, 0, 180)) // Cyberpunk Pink
                }
            }
        }

        // 3. Invert Ramp Checkbox
        val invertCheckBox = JCheckBox("Invert Ramp").apply {
            foreground = labelColor
            background = Color(30, 30, 35)
            isFocusable = false
            addActionListener {
                isInverted = isSelected
            }
        }

        // 4. FPS / Speed Slider
        fpsLabel.foreground = labelColor
        val fpsSlider = JSlider(JSlider.HORIZONTAL, 10, 60, targetFps).apply {
            preferredSize = Dimension(110, 25)
            background = Color(30, 30, 35)
            addChangeListener {
                targetFps = value
                fpsLabel.text = "FPS: $targetFps"
                restartTimer()
            }
        }

        panel.add(JLabel("Video Feed:").apply { foreground = labelColor })
        panel.add(feedCombo)
        panel.add(Box.createHorizontalStrut(8))
        panel.add(JLabel("Theme:").apply { foreground = labelColor })
        panel.add(themeCombo)
        panel.add(Box.createHorizontalStrut(8))
        panel.add(invertCheckBox)
        panel.add(Box.createHorizontalStrut(8))
        panel.add(fpsLabel)
        panel.add(fpsSlider)

        return panel
    }

    /**
     * Center Display: Monospace Text Area representing the ASCII Video Matrix.
     */
    private fun createDisplayPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        textArea.apply {
            font = Font("Monospaced", Font.BOLD, 12)
            background = Color.BLACK
            foreground = Color(0, 255, 100) // Default Matrix Green
            isEditable = false
            isFocusable = false
            margin = Insets(5, 5, 5, 5)
        }

        panel.add(textArea, BorderLayout.CENTER)
        return panel
    }

    private fun applyTheme(bg: Color, fg: Color) {
        textArea.background = bg
        textArea.foreground = fg
    }

    /**
     * Starts the video frame refresh loop timer.
     */
    private fun startVideoPipeline() {
        val delayMs = 1000 / targetFps
        frameTimer = Timer(delayMs) {
            renderNextAsciiFrame()
            time += 0.05
        }
        frameTimer?.start()
    }

    private fun restartTimer() {
        frameTimer?.stop()
        startVideoPipeline()
    }

    /**
     * Processes live video feed frame and maps pixel brightness to ASCII typography.
     */
    private fun renderNextAsciiFrame() {
        frameBuffer.setLength(0)

        val aspectCorrection = 0.55 // Compensates for tall monospace character aspect ratios

        for (y in 0 until rows) {
            // Map grid coordinates to normalized space [-1.0, 1.0]
            val ny = ((y.toDouble() / rows) * 2.0 - 1.0)

            for (x in 0 until cols) {
                val nx = ((x.toDouble() / cols) * 2.0 - 1.0) * (cols.toDouble() / rows) * aspectCorrection

                // Calculate brightness value [0.0 to 1.0] from selected synthetic video feed
                val brightness = when (feedMode) {
                    0 -> sample3DGeometryFeed(nx, ny, time)
                    1 -> samplePlasmaFluidFeed(nx, ny, time)
                    2 -> sampleRadarWaveFeed(nx, ny, time)
                    else -> sampleQuantumTunnelFeed(nx, ny, time)
                }.coerceIn(0.0, 1.0)

                // Map brightness value to ASCII character density index
                var rampIndex = (brightness * (densityRamp.size - 1)).toInt()
                if (isInverted) {
                    rampIndex = (densityRamp.size - 1) - rampIndex
                }

                frameBuffer.append(densityRamp[rampIndex])
            }
            frameBuffer.append("\n")
        }

        // Update Matrix Text Display
        textArea.text = frameBuffer.toString()
    }

    // --- SYNTHETIC VIDEO FEED GENERATORS ---

    /**
     * Feed 1: 3D Rotating Cube Geometry with Dynamic Lighting.
     */
    private fun sample3DGeometryFeed(x: Double, y: Double, t: Double): Double {
        val cosT = cos(t * 0.8)
        val sinT = sin(t * 0.8)

        // Rotate 2D point space
        val rx = x * cosT - y * sinT
        val ry = x * sinT + y * cosT

        val cubeSize = 0.55
        val edgeThickness = 0.08

        // Distance to square edges
        val dx = abs(rx) - cubeSize
        val dy = abs(ry) - cubeSize

        val outerBox = max(dx, dy)
        val innerBox = max(dx + edgeThickness, dy + edgeThickness)

        // Render wireframe outline with dynamic ambient pulse
        val isEdge = outerBox < 0.0 && innerBox > 0.0
        val fillCenter = abs(rx) < cubeSize && abs(ry) < cubeSize

        return if (isEdge) {
            0.95
        } else if (fillCenter) {
            0.25 + 0.2 * sin(x * 5.0 + t * 2.0)
        } else {
            0.05
        }
    }

    /**
     * Feed 2: Organic Plasma Fluid Dynamics Video.
     */
    private fun samplePlasmaFluidFeed(x: Double, y: Double, t: Double): Double {
        val v1 = sin(x * 3.0 + t)
        val v2 = sin(y * 3.0 + t * 1.2)
        val v3 = sin((x + y) * 2.5 + t * 0.8)
        val v4 = sin(sqrt(x * x + y * y) * 4.0 - t * 1.5)

        val total = (v1 + v2 + v3 + v4) / 4.0 // Range [-1.0, 1.0]
        return (total + 1.0) / 2.0            // Map to [0.0, 1.0]
    }

    /**
     * Feed 3: Concentric Optical Radar Sweep.
     */
    private fun sampleRadarWaveFeed(x: Double, y: Double, t: Double): Double {
        val dist = sqrt(x * x + y * y)
        val angle = atan2(y, x)

        val wave = sin(dist * 12.0 - t * 4.0)
        val sweep = cos(angle - t * 2.0)

        val intensity = (wave * sweep + 1.0) / 2.0
        val ring = if (abs(dist - 0.7) < 0.03) 0.8 else 0.0

        return max(intensity * 0.8, ring)
    }

    /**
     * Feed 4: Quantum Hyperspace Tunnel.
     */
    private fun sampleQuantumTunnelFeed(x: Double, y: Double, t: Double): Double {
        val dist = sqrt(x * x + y * y).coerceAtLeast(0.01)
        val angle = atan2(y, x)

        val tunnel = sin(1.0 / dist * 3.0 + t * 5.0)
        val spiral = sin(angle * 4.0 + 1.0 / dist * 2.0)

        return ((tunnel * spiral) + 1.0) / 2.0
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val simulator = AsciiCamSimulator()
        simulator.isVisible = true
    }
}