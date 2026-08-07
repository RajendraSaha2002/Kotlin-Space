package com.devops.dashboard

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.math.max

// ============================================================================
// 1. DATA MODELS & ENUMS
// ============================================================================

enum class DashboardServerStatus(val displayName: String, val color: Color, val bgAlpha: Color) {
    HEALTHY("HEALTHY", Color(0, 230, 118), Color(0, 230, 118, 25)),
    HIGH_LOAD("HIGH LOAD", Color(255, 145, 0), Color(255, 145, 0, 30)),
    OFFLINE("OFFLINE", Color(255, 82, 82), Color(255, 82, 82, 45))
}

data class DashboardMetricPoint(
    val timestamp: Long,
    val latencyMs: Double,
    val packetLossPercent: Double
)

class DashboardServerNode(
    val id: String,
    val name: String,
    val region: String,
    val baseLatency: Double
) {
    @Volatile var status: DashboardServerStatus = DashboardServerStatus.HEALTHY
    @Volatile var currentLatency: Double = baseLatency
    @Volatile var currentPacketLoss: Double = 0.0
    @Volatile var lastSeen: Long = System.currentTimeMillis()

    val metricsHistory: MutableList<DashboardMetricPoint> = Collections.synchronizedList(LinkedList())

    // Visual animation states
    @Volatile var flashCount: Int = 0
    @Volatile var flashColor: Color? = null

    init {
        val now = System.currentTimeMillis()
        for (i in 40 downTo 0) {
            metricsHistory.add(
                DashboardMetricPoint(now - i * 1000L, max(5.0, baseLatency + (Math.random() * 8 - 4)), 0.0)
            )
        }
    }

    fun addMetric(point: DashboardMetricPoint, maxHistory: Int = 50) {
        synchronized(metricsHistory) {
            metricsHistory.add(point)
            while (metricsHistory.size > maxHistory) {
                metricsHistory.removeAt(0)
            }
        }
        currentLatency = point.latencyMs
        currentPacketLoss = point.packetLossPercent
        lastSeen = point.timestamp
    }

    fun triggerFlash(color: Color) {
        flashCount = 8
        flashColor = color
    }
}

// ============================================================================
// 2. ASYNC BACKGROUND ENGINE
// ============================================================================

class DashboardSimulationEngine(
    private val nodes: List<DashboardServerNode>,
    private val onTick: () -> Unit,
    private val onLogEvent: (String, Color) -> Unit
) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        scheduler.scheduleAtFixedRate({
            try {
                updateSimulation()
                SwingUtilities.invokeLater { onTick() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 0, 750, TimeUnit.MILLISECONDS)
    }

    private fun updateSimulation() {
        val now = System.currentTimeMillis()

        for (node in nodes) {
            val prevStatus = node.status
            val roll = Math.random()

            var newStatus = prevStatus
            var latency = node.currentLatency
            var packetLoss = node.currentPacketLoss

            when {
                // Recovering from Offline
                prevStatus == DashboardServerStatus.OFFLINE -> {
                    if (roll < 0.30) {
                        newStatus = DashboardServerStatus.HEALTHY
                        latency = node.baseLatency + (Math.random() * 10)
                        packetLoss = 0.0
                        onLogEvent("[RECOVERY] Node ${node.name} (${node.region}) is online again.", DashboardServerStatus.HEALTHY.color)
                    } else {
                        latency = 0.0
                        packetLoss = 100.0
                    }
                }
                // Spike failure to Offline
                roll < 0.025 -> {
                    newStatus = DashboardServerStatus.OFFLINE
                    latency = 0.0
                    packetLoss = 100.0
                    onLogEvent("[CRITICAL] Node ${node.name} (${node.region}) Connection Lost / Heartbeat Failed!", DashboardServerStatus.OFFLINE.color)
                }
                // High Load & Packet Loss Spike
                roll < 0.15 -> {
                    newStatus = DashboardServerStatus.HIGH_LOAD
                    latency = node.baseLatency * (2.2 + Math.random() * 2.5)
                    packetLoss = 3.0 + Math.random() * 18.0
                    if (prevStatus != DashboardServerStatus.HIGH_LOAD) {
                        onLogEvent("[WARNING] High latency & packet loss spike on ${node.name} (${node.region})", DashboardServerStatus.HIGH_LOAD.color)
                    }
                }
                // Normal Operation
                else -> {
                    newStatus = DashboardServerStatus.HEALTHY
                    val delta = (Math.random() * 12) - 6
                    latency = max(8.0, node.baseLatency + delta)
                    packetLoss = max(0.0, Math.random() * 0.4)
                }
            }

            node.status = newStatus
            node.addMetric(DashboardMetricPoint(now, latency, packetLoss))

            if (prevStatus != newStatus) {
                node.triggerFlash(newStatus.color)
            }
        }
    }

    fun stop() {
        scheduler.shutdown()
    }
}

// ============================================================================
// 3. UI THEME & CONSTANTS
// ============================================================================

object DevOpsDashboardTheme {
    val BG_DARK = Color(14, 17, 23)
    val CARD_BG = Color(22, 27, 38)
    val CARD_BORDER = Color(38, 46, 62)
    val CARD_SELECTED = Color(0, 229, 255)
    val TEXT_PRIMARY = Color(240, 244, 250)
    val TEXT_MUTED = Color(130, 142, 162)
    val ACCENT_CYAN = Color(0, 229, 255)
    val GRID_LINE = Color(28, 35, 48)
    val FONT_MAIN = Font("SansSerif", Font.PLAIN, 12)
    val FONT_BOLD = Font("SansSerif", Font.BOLD, 12)
    val FONT_TITLE = Font("SansSerif", Font.BOLD, 16)
    val FONT_MONO = Font("Monospaced", Font.PLAIN, 11)
}

// ============================================================================
// 4. GUI COMPONENTS
// ============================================================================

class ServerCardPanel(
    val node: DashboardServerNode,
    var isSelected: Boolean,
    private val onClick: () -> Unit
) : JPanel() {

    init {
        isOpaque = false
        preferredSize = Dimension(240, 115)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onClick()
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val w = width
        val h = height

        // Background & Flash glow
        var bgColor = DevOpsDashboardTheme.CARD_BG
        var borderColor = if (isSelected) DevOpsDashboardTheme.CARD_SELECTED else DevOpsDashboardTheme.CARD_BORDER

        if (node.flashCount > 0 && node.flashColor != null) {
            val alpha = (node.flashCount * 25).coerceIn(0, 180)
            val fc = node.flashColor!!
            bgColor = Color(fc.red, fc.green, fc.blue, alpha)
            borderColor = fc
        }

        // Draw Card Body
        g2.color = bgColor
        g2.fill(RoundRectangle2D.Float(0f, 0f, w - 1f, h - 1f, 12f, 12f))

        g2.color = borderColor
        g2.stroke = BasicStroke(if (isSelected) 2.0f else 1.0f)
        g2.draw(RoundRectangle2D.Float(0f, 0f, w - 1f, h - 1f, 12f, 12f))

        // Node Name & Region
        g2.font = DevOpsDashboardTheme.FONT_BOLD
        g2.color = DevOpsDashboardTheme.TEXT_PRIMARY
        g2.drawString(node.name, 12, 22)

        g2.font = DevOpsDashboardTheme.FONT_MAIN
        g2.color = DevOpsDashboardTheme.TEXT_MUTED
        g2.drawString(node.region, 12, 38)

        // Status Badge Pill
        val status = node.status
        val badgeText = status.displayName
        g2.font = Font("SansSerif", Font.BOLD, 10)
        val badgeWidth = g2.fontMetrics.stringWidth(badgeText) + 16
        val badgeX = w - badgeWidth - 12
        val badgeY = 12

        g2.color = status.bgAlpha
        g2.fillRoundRect(badgeX, badgeY, badgeWidth, 20, 10, 10)
        g2.color = status.color
        g2.drawRoundRect(badgeX, badgeY, badgeWidth, 20, 10, 10)

        // Status indicator dot
        g2.fillOval(badgeX + 6, badgeY + 6, 8, 8)
        g2.drawString(badgeText, badgeX + 18, badgeY + 14)

        // Metrics Text
        g2.font = DevOpsDashboardTheme.FONT_MAIN
        g2.color = DevOpsDashboardTheme.TEXT_PRIMARY
        if (status == DashboardServerStatus.OFFLINE) {
            g2.color = DashboardServerStatus.OFFLINE.color
            g2.drawString("LATENCY: UNREACHABLE", 12, 65)
            g2.drawString("LOSS: 100.0%", 12, 82)
        } else {
            val latText = String.format("LATENCY: %.1f ms", node.currentLatency)
            val lossText = String.format("LOSS: %.1f%%", node.currentPacketLoss)
            g2.drawString(latText, 12, 65)
            if (node.currentPacketLoss > 2.0) {
                g2.color = DashboardServerStatus.HIGH_LOAD.color
            } else {
                g2.color = DevOpsDashboardTheme.TEXT_MUTED
            }
            g2.drawString(lossText, 12, 82)
        }

        // Mini Sparkline (Bottom Right of Card)
        drawSparkline(g2, w - 90, 55, 78, 35)
    }

    private fun drawSparkline(g2: Graphics2D, x: Int, y: Int, sw: Int, sh: Int) {
        val history = synchronized(node.metricsHistory) { ArrayList(node.metricsHistory) }
        if (history.size < 2) return

        val recent = history.takeLast(15)
        val maxVal = recent.maxOf { it.latencyMs }.coerceAtLeast(100.0)

        val path = Path2D.Float()
        val stepX = sw.toFloat() / (recent.size - 1)

        for (i in recent.indices) {
            val pt = recent[i]
            val px = x + i * stepX
            val py = if (pt.latencyMs <= 0) (y + sh).toFloat()
            else (y + sh - (pt.latencyMs / maxVal * sh)).toFloat()

            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }

        g2.color = when (node.status) {
            DashboardServerStatus.HEALTHY -> Color(0, 230, 118, 180)
            DashboardServerStatus.HIGH_LOAD -> Color(255, 145, 0, 180)
            DashboardServerStatus.OFFLINE -> Color(255, 82, 82, 180)
        }
        g2.stroke = BasicStroke(1.5f)
        g2.draw(path)
    }
}

class LatencyChartPanel(private var selectedNode: DashboardServerNode?) : JPanel() {

    init {
        isOpaque = false
        background = DevOpsDashboardTheme.CARD_BG
    }

    fun setSelectedNode(node: DashboardServerNode) {
        this.selectedNode = node
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val w = width
        val h = height

        // Panel Background
        g2.color = DevOpsDashboardTheme.CARD_BG
        g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12)
        g2.color = DevOpsDashboardTheme.CARD_BORDER
        g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12)

        val node = selectedNode ?: return

        // Chart Header Info
        g2.font = DevOpsDashboardTheme.FONT_TITLE
        g2.color = DevOpsDashboardTheme.TEXT_PRIMARY
        g2.drawString("Real-time Latency & Infrastructure Packet Loss", 20, 30)

        g2.font = DevOpsDashboardTheme.FONT_MAIN
        g2.color = DevOpsDashboardTheme.ACCENT_CYAN
        g2.drawString("TARGET NODE: ${node.name} (${node.region})", 20, 50)

        // Plot Dimensions
        val padLeft = 50
        val padRight = 30
        val padTop = 70
        val padBottom = 40

        val plotW = w - padLeft - padRight
        val plotH = h - padTop - padBottom

        if (plotW <= 0 || plotH <= 0) return

        // Draw Horizontal Grid Lines
        val maxLatency = 300.0 // Ceiling scale ms
        val gridSteps = 5

        g2.font = DevOpsDashboardTheme.FONT_MONO
        for (i in 0..gridSteps) {
            val valMs = (maxLatency / gridSteps) * i
            val gy = padTop + plotH - (i * plotH / gridSteps)

            g2.color = DevOpsDashboardTheme.GRID_LINE
            g2.drawLine(padLeft, gy, padLeft + plotW, gy)

            g2.color = DevOpsDashboardTheme.TEXT_MUTED
            g2.drawString(String.format("%3.0f ms", valMs), 8, gy + 4)
        }

        // Draw Threshold Lines (Warning & Critical)
        val warnY = padTop + plotH - ((150.0 / maxLatency) * plotH).toInt()
        g2.color = Color(255, 145, 0, 90)
        g2.stroke = BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, floatArrayOf(4.0f), 0.0f)
        g2.drawLine(padLeft, warnY, padLeft + plotW, warnY)
        g2.drawString("150ms Threshold", padLeft + plotW - 95, warnY - 4)

        // Fetch Metrics History
        val history = synchronized(node.metricsHistory) { ArrayList(node.metricsHistory) }
        if (history.isEmpty()) return

        val stepX = plotW.toDouble() / (history.size - 1).coerceAtLeast(1)

        val chartPath = Path2D.Double()
        val areaPath = Path2D.Double()
        areaPath.moveTo(padLeft.toDouble(), (padTop + plotH).toDouble())

        for (i in history.indices) {
            val pt = history[i]
            val px = padLeft + i * stepX
            val clampedLat = pt.latencyMs.coerceIn(0.0, maxLatency)
            val py = padTop + plotH - (clampedLat / maxLatency * plotH)

            if (i == 0) {
                chartPath.moveTo(px, py)
                areaPath.lineTo(px, py)
            } else {
                chartPath.lineTo(px, py)
                areaPath.lineTo(px, py)
            }

            // Packet Loss Bar Visualizer
            if (pt.packetLossPercent > 0.0) {
                val lossH = (pt.packetLossPercent / 100.0 * plotH).coerceAtLeast(6.0)
                g2.color = Color(255, 82, 82, 120)
                g2.fillRect(px.toInt() - 2, (padTop + plotH - lossH).toInt(), 4, lossH.toInt())
            }
        }

        areaPath.lineTo(padLeft + plotW.toDouble(), (padTop + plotH).toDouble())
        areaPath.closePath()

        // Gradient Fill Under Chart Curve
        val lineGrad = GradientPaint(
            0f, padTop.toFloat(), Color(0, 229, 255, 80),
            0f, (padTop + plotH).toFloat(), Color(0, 229, 255, 0)
        )
        g2.paint = lineGrad
        g2.fill(areaPath)

        // Chart Stroke Line
        g2.color = DevOpsDashboardTheme.ACCENT_CYAN
        g2.stroke = BasicStroke(2.0f)
        g2.draw(chartPath)

        // Highlight Latest Data Point Dot
        val lastPt = history.last()
        val lastX = padLeft + (history.size - 1) * stepX
        val lastY = padTop + plotH - (lastPt.latencyMs.coerceIn(0.0, maxLatency) / maxLatency * plotH)

        g2.color = DevOpsDashboardTheme.ACCENT_CYAN
        g2.fillOval(lastX.toInt() - 5, lastY.toInt() - 5, 10, 10)
        g2.color = Color.WHITE
        g2.fillOval(lastX.toInt() - 2, lastY.toInt() - 2, 4, 4)

        // Tooltip Text at Point
        g2.font = DevOpsDashboardTheme.FONT_MONO
        g2.color = DevOpsDashboardTheme.TEXT_PRIMARY
        val overlayText = if (node.status == DashboardServerStatus.OFFLINE) "OFFLINE" else String.format("%.1f ms", lastPt.latencyMs)
        g2.drawString(overlayText, lastX.toInt() - 20, lastY.toInt() - 10)
    }
}

class HeaderSummaryPanel : JPanel() {
    private val totalLabel = JLabel("NODES: 0")
    private val healthyLabel = JLabel("HEALTHY: 0")
    private val loadLabel = JLabel("HIGH LOAD: 0")
    private val offlineLabel = JLabel("OFFLINE: 0")
    private val timeLabel = JLabel("00:00:00 UTC")

    init {
        isOpaque = false
        layout = BorderLayout()
        border = EmptyBorder(10, 15, 10, 15)

        val titleLabel = JLabel("MISSION CONTROL // INFRASTRUCTURE NODE DASHBOARD")
        titleLabel.font = Font("SansSerif", Font.BOLD, 18)
        titleLabel.foreground = DevOpsDashboardTheme.TEXT_PRIMARY

        val statsBox = Box.createHorizontalBox()
        val labels = listOf(totalLabel, healthyLabel, loadLabel, offlineLabel, timeLabel)
        val colors = listOf(
            DevOpsDashboardTheme.TEXT_PRIMARY,
            DashboardServerStatus.HEALTHY.color,
            DashboardServerStatus.HIGH_LOAD.color,
            DashboardServerStatus.OFFLINE.color,
            DevOpsDashboardTheme.ACCENT_CYAN
        )

        for (i in labels.indices) {
            labels[i].font = Font("Monospaced", Font.BOLD, 12)
            labels[i].foreground = colors[i]
            statsBox.add(labels[i])
            if (i < labels.size - 1) {
                statsBox.add(Box.createHorizontalStrut(20))
            }
        }

        add(titleLabel, BorderLayout.WEST)
        add(statsBox, BorderLayout.EAST)
    }

    fun updateMetrics(nodes: List<DashboardServerNode>) {
        val total = nodes.size
        val healthy = nodes.count { it.status == DashboardServerStatus.HEALTHY }
        val load = nodes.count { it.status == DashboardServerStatus.HIGH_LOAD }
        val offline = nodes.count { it.status == DashboardServerStatus.OFFLINE }

        totalLabel.text = "NODES: $total"
        healthyLabel.text = "HEALTHY: $healthy"
        loadLabel.text = "HIGH LOAD: $load"
        offlineLabel.text = "OFFLINE: $offline"

        val sdf = SimpleDateFormat("HH:mm:ss.SSS 'UTC'")
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        timeLabel.text = sdf.format(Date())
    }
}

class EventConsolePanel : JPanel() {
    private val listModel = DefaultListModel<String>()
    private val list = JList(listModel)

    init {
        isOpaque = false
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(DevOpsDashboardTheme.CARD_BORDER),
            " Real-time Infrastructure Event Stream ",
            0, 0, DevOpsDashboardTheme.FONT_BOLD, DevOpsDashboardTheme.ACCENT_CYAN
        )

        list.font = DevOpsDashboardTheme.FONT_MONO
        list.background = DevOpsDashboardTheme.CARD_BG
        list.foreground = DevOpsDashboardTheme.TEXT_PRIMARY
        list.selectionBackground = DevOpsDashboardTheme.CARD_BORDER

        val scroll = JScrollPane(list)
        scroll.border = null
        scroll.verticalScrollBar.unitIncrement = 10
        add(scroll, BorderLayout.CENTER)
    }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss").format(Date())
        listModel.addElement("[$timestamp] $message")
        if (listModel.size > 100) {
            listModel.remove(0)
        }
        list.ensureIndexIsVisible(listModel.size - 1)
    }
}

// ============================================================================
// 5. MAIN DASHBOARD FRAME
// ============================================================================

class DashboardFrame : JFrame("Live Server Health & Infrastructure Console") {

    private val nodes = listOf(
        DashboardServerNode("n1", "us-east-api-01", "US East (N. Virginia)", 24.0),
        DashboardServerNode("n2", "us-west-auth-02", "US West (Oregon)", 48.0),
        DashboardServerNode("n3", "eu-central-db-01", "EU Central (Frankfurt)", 110.0),
        DashboardServerNode("n4", "eu-west-cache-03", "EU West (Ireland)", 95.0),
        DashboardServerNode("n5", "ap-south-gateway-01", "AP South (Mumbai)", 185.0),
        DashboardServerNode("n6", "ap-northeast-api-02", "AP Northeast (Tokyo)", 160.0),
        DashboardServerNode("n7", "sa-east-edge-01", "SA East (São Paulo)", 140.0),
        DashboardServerNode("n8", "af-south-node-01", "AF South (Cape Town)", 210.0)
    )

    private var selectedNode: DashboardServerNode = nodes.first()
    private val cardPanels = mutableListOf<ServerCardPanel>()

    private val headerPanel = HeaderSummaryPanel()
    private val latencyChartPanel = LatencyChartPanel(selectedNode)
    private val consolePanel = EventConsolePanel()
    private val cardsGridContainer = JPanel()

    private val engine: DashboardSimulationEngine
    private val animTimer: javax.swing.Timer

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(1280, 800)
        minimumSize = Dimension(1024, 680)
        contentPane.background = DevOpsDashboardTheme.BG_DARK
        layout = BorderLayout(10, 10)

        // Top Header
        add(headerPanel, BorderLayout.NORTH)

        // Left Grid Panel (Server Cards)
        cardsGridContainer.isOpaque = false
        cardsGridContainer.layout = GridLayout(0, 2, 10, 10)

        nodes.forEach { node ->
            val card = ServerCardPanel(node, node == selectedNode) {
                selectNode(node)
            }
            cardPanels.add(card)
            cardsGridContainer.add(card)
        }

        val gridScroll = JScrollPane(cardsGridContainer)
        gridScroll.isOpaque = false
        gridScroll.viewport.isOpaque = false
        gridScroll.border = EmptyBorder(0, 10, 10, 0)
        gridScroll.preferredSize = Dimension(510, 0)
        gridScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

        // Center Area (Chart + Console Log Split)
        val centerSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        centerSplit.isOpaque = false
        centerSplit.dividerSize = 6
        centerSplit.resizeWeight = 0.65
        centerSplit.border = EmptyBorder(0, 0, 10, 10)

        centerSplit.topComponent = latencyChartPanel
        centerSplit.bottomComponent = consolePanel

        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        mainSplit.isOpaque = false
        mainSplit.dividerSize = 6
        mainSplit.leftComponent = gridScroll
        mainSplit.rightComponent = centerSplit
        mainSplit.border = null

        add(mainSplit, BorderLayout.CENTER)

        // Start Async Engine
        engine = DashboardSimulationEngine(
            nodes = nodes,
            onTick = {
                headerPanel.updateMetrics(nodes)
                latencyChartPanel.repaint()
                cardsGridContainer.repaint()
            },
            onLogEvent = { message, _ ->
                consolePanel.log(message)
            }
        )
        engine.start()

        // Animation Timer for Smooth Card Flashing Effects (~30 FPS)
        animTimer = javax.swing.Timer(33) {
            var needsRepaint = false
            for (node in nodes) {
                if (node.flashCount > 0) {
                    node.flashCount--
                    needsRepaint = true
                }
            }
            if (needsRepaint) {
                cardsGridContainer.repaint()
            }
        }
        animTimer.start()

        // Initial log
        consolePanel.log("System initialized. Monitoring ${nodes.size} regional telemetry nodes...")
    }

    private fun selectNode(node: DashboardServerNode) {
        selectedNode = node
        cardPanels.forEach { panel ->
            panel.isSelected = (panel.node == node)
        }
        latencyChartPanel.setSelectedNode(node)
        cardsGridContainer.repaint()
    }
}

// ============================================================================
// 6. MAIN ENTRY POINT
// ============================================================================

fun main() {
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")

    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
            // Fallback to standard Look & Feel
        }

        val dashboard = DashboardFrame()
        dashboard.setLocationRelativeTo(null)
        dashboard.isVisible = true
    }
}