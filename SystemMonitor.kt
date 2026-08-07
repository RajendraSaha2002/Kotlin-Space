import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.lang.management.ManagementFactory
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.plaf.basic.BasicProgressBarUI

fun main() {
    // Enable system font anti-aliasing for smooth text rendering
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")

    SwingUtilities.invokeLater {
        val monitor = SystemMonitorWindow()
        monitor.isVisible = true
    }
}

/**
 * Transparent Always-On-Top Floating Window Frame
 */
class SystemMonitorWindow : JWindow() {

    private val osBean = ManagementFactory.getOperatingSystemMXBean()
    private val threadCount = Runtime.getRuntime().availableProcessors()

    private val cpuBar = ModernProgressBar()
    private val ramBar = ModernProgressBar()

    private val cpuTextLabel = JLabel("CPU ($threadCount Threads)")
    private val cpuValLabel = JLabel("0%")
    private val ramTextLabel = JLabel("RAM")
    private val ramValLabel = JLabel("0/0 GB")

    private var initialClick: Point? = null

    init {
        // Window Configuration
        isAlwaysOnTop = true
        background = Color(0, 0, 0, 0) // Fully transparent native container
        setSize(310, 160)

        // Initial position: Top-right corner of screen
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        setLocation(screenSize.width - 330, 40)

        // Glass Panel Container
        val mainPanel = GlassPanel()
        mainPanel.layout = BorderLayout()
        mainPanel.border = EmptyBorder(14, 16, 14, 16)

        // Header Section (Title + Close Button)
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            val title = JLabel("SYSTEM PERFORMANCE").apply {
                font = Font(Font.SANS_SERIF, Font.BOLD, 10)
                foreground = Color(140, 150, 165)
            }
            val closeBtn = JLabel("✕").apply {
                font = Font(Font.SANS_SERIF, Font.BOLD, 12)
                foreground = Color(140, 150, 165)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) = System.exit(0)
                    override fun mouseEntered(e: MouseEvent) { foreground = Color(231, 76, 60) }
                    override fun mouseExited(e: MouseEvent) { foreground = Color(140, 150, 165) }
                })
            }
            add(title, BorderLayout.WEST)
            add(closeBtn, BorderLayout.EAST)
        }

        // Content Section (CPU & RAM metrics)
        val contentPanel = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        fun createLabelRow(leftLabel: JLabel, rightLabel: JLabel): JPanel {
            return JPanel(BorderLayout()).apply {
                isOpaque = false
                leftLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
                leftLabel.foreground = Color(210, 215, 225)
                rightLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
                rightLabel.foreground = Color(255, 255, 255)
                add(leftLabel, BorderLayout.WEST)
                add(rightLabel, BorderLayout.EAST)
            }
        }

        // Add CPU Component Stack
        contentPanel.add(Box.createVerticalStrut(10))
        contentPanel.add(createLabelRow(cpuTextLabel, cpuValLabel))
        contentPanel.add(Box.createVerticalStrut(5))
        contentPanel.add(cpuBar)

        // Add RAM Component Stack
        contentPanel.add(Box.createVerticalStrut(12))
        contentPanel.add(createLabelRow(ramTextLabel, ramValLabel))
        contentPanel.add(Box.createVerticalStrut(5))
        contentPanel.add(ramBar)

        mainPanel.add(headerPanel, BorderLayout.NORTH)
        mainPanel.add(contentPanel, BorderLayout.CENTER)
        contentPane = mainPanel

        // Make window draggable anywhere on the glass panel
        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                initialClick = e.point as Point?
            }

            override fun mouseDragged(e: MouseEvent) {
                val click = initialClick ?: return
                val thisOnScreen = e.locationOnScreen
                setLocation(thisOnScreen.x - click.x, thisOnScreen.y - click.y)
            }
        }
        mainPanel.addMouseListener(mouseAdapter)
        mainPanel.addMouseMotionListener(mouseAdapter)

        // Right-click context menu
        val contextMenu = JPopupMenu().apply {
            val exitItem = JMenuItem("Exit Monitor").apply {
                addActionListener { System.exit(0) }
            }
            add(exitItem)
        }
        mainPanel.componentPopupMenu = contextMenu

        // 500ms Timer Polling Loop (Runs safely on Swing EDT)
        val timer = Timer(500) { updateMetrics() }
        timer.start()

        updateMetrics() // Immediate first update
    }

    /**
     * Polls System Hardware Metrics and updates UI components
     */
    private fun updateMetrics() {
        // Poll CPU Load
        val cpuUsage = getCpuLoad()
        cpuTextLabel.text = "CPU ($threadCount Threads)"
        cpuValLabel.text = String.format("%.0f%%", cpuUsage)
        cpuBar.value = cpuUsage.toInt()
        cpuBar.barColor = interpolateColor(cpuUsage)

        // Poll RAM Load
        val (ramPct, ramText) = getRamInfo()
        ramTextLabel.text = "RAM"
        ramValLabel.text = ramText
        ramBar.value = ramPct.toInt()
        ramBar.barColor = interpolateColor(ramPct)

        repaint()
    }

    /**
     * Uses reflection on ManagementFactory Bean for JVM/OS cross-version compatibility
     */
    private fun getCpuLoad(): Double {
        return try {
            val method = osBean.javaClass.getMethod("getCpuLoad")
            val res = method.invoke(osBean) as Double
            if (res < 0) 0.0 else res * 100.0
        } catch (e: Exception) {
            try {
                val method = osBean.javaClass.getMethod("getSystemCpuLoad")
                val res = method.invoke(osBean) as Double
                if (res < 0) 0.0 else res * 100.0
            } catch (ex: Exception) {
                0.0
            }
        }
    }

    private fun getRamInfo(): Pair<Double, String> {
        return try {
            val totalMethod = osBean.javaClass.getMethod("getTotalPhysicalMemorySize")
            val freeMethod = osBean.javaClass.getMethod("getFreePhysicalMemorySize")
            val total = totalMethod.invoke(osBean) as Long
            val free = freeMethod.invoke(osBean) as Long
            val used = total - free
            val pct = (used.toDouble() / total.toDouble()) * 100.0
            val usedGB = used / (1024.0 * 1024.0 * 1024.0)
            val totalGB = total / (1024.0 * 1024.0 * 1024.0)
            Pair(pct, String.format("%.1f / %.1f GB (%.0f%%)", usedGB, totalGB, pct))
        } catch (e: Exception) {
            val rt = Runtime.getRuntime()
            val total = rt.totalMemory()
            val free = rt.freeMemory()
            val used = total - free
            val pct = (used.toDouble() / total.toDouble()) * 100.0
            Pair(pct, String.format("%.0f%%", pct))
        }
    }

    /**
     * Smooth Color Interpolation: Green (0%) -> Yellow (50%) -> Red (100%)
     */
    private fun interpolateColor(pct: Double): Color {
        val clamped = pct.coerceIn(0.0, 100.0) / 100.0
        return if (clamped < 0.5) {
            val factor = clamped * 2.0
            Color(
                (46 + (241 - 46) * factor).toInt(),
                (204 + (196 - 204) * factor).toInt(),
                (113 + (15 - 113) * factor).toInt()
            )
        } else {
            val factor = (clamped - 0.5) * 2.0
            Color(
                (241 + (231 - 241) * factor).toInt(),
                (196 + (76 - 196) * factor).toInt(),
                (15 + (60 - 15) * factor).toInt()
            )
        }
    }
}

/**
 * Custom Anti-Aliased Smooth Progress Bar UI
 */
class ModernProgressBar : JProgressBar(0, 100) {
    var barColor: Color = Color(46, 204, 113)

    init {
        isOpaque = false
        isBorderPainted = false
        preferredSize = Dimension(100, 7)
        setUI(CustomProgressBarUI())
    }

    private inner class CustomProgressBarUI : BasicProgressBarUI() {
        override fun paintDeterminate(g: Graphics, c: JComponent) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val w = progressBar.width
            val h = progressBar.height
            val amountFull = getAmountFull(Insets(0, 0, 0, 0), w, h)

            // Track Background
            g2.color = Color(255, 255, 255, 20)
            g2.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))

            // Active Progress Bar Fill
            if (amountFull > 0) {
                g2.color = barColor
                g2.fill(RoundRectangle2D.Float(0f, 0f, amountFull.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
            }

            g2.dispose()
        }

        override fun paintIndeterminate(g: Graphics, c: JComponent) {}
    }
}

/**
 * Glassmorphic Background Panel (Semi-transparent rounded glass box)
 */
class GlassPanel : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val w = width.toFloat()
        val h = height.toFloat()
        val radius = 22f

        val glassRect = RoundRectangle2D.Float(0f, 0f, w - 1, h - 1, radius, radius)

        // Dark Translucent Glass Background
        g2.color = Color(18, 22, 32, 215)
        g2.fill(glassRect)

        // Subtle Ambient Highlight (Top Glow)
        val gloss = GradientPaint(
            0f, 0f, Color(255, 255, 255, 22),
            0f, h / 2f, Color(255, 255, 255, 0)
        )
        g2.paint = gloss
        g2.fill(glassRect)

        // Frosted Glass Thin Border Outline
        g2.stroke = BasicStroke(1.2f)
        g2.color = Color(255, 255, 255, 40)
        g2.draw(glassRect)

        g2.dispose()
    }
}