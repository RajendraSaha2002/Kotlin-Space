package com.fintech.crypto.portfolio

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// 1. DATA MODELS & ENUMS
// ============================================================================

enum class TimeframeFilter(val label: String, val pointCount: Int, val timeframeName: String) {
    DAY("1D", 24, "Past 24 Hours"),
    WEEK("1W", 28, "Past 7 Days"),
    MONTH("1M", 30, "Past 30 Days")
}

data class AssetHolding(
    val symbol: String,
    val name: String,
    val holdings: Double,
    val avgBuyPrice: Double,
    var livePrice: Double,
    var initial24hPrice: Double
) {
    val totalValue: Double get() = holdings * livePrice
    val totalCost: Double get() = holdings * avgBuyPrice
    val totalPnL: Double get() = totalValue - totalCost
    val change24hPercent: Double get() = if (initial24hPrice == 0.0) 0.0 else ((livePrice - initial24hPrice) / initial24hPrice) * 100.0
}

data class PortfolioSnapshot(
    val timestamp: Long,
    val totalWealth: Double
)

// ============================================================================
// 2. REAL-TIME PRICE FLUCTUATION GENERATOR
// ============================================================================

class MarketSimulationEngine(
    val assets: List<AssetHolding>,
    private val onTick: (Double) -> Unit
) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Historical datasets for trendline filters
    val history1D: MutableList<PortfolioSnapshot> = Collections.synchronizedList(LinkedList())
    val history1W: MutableList<PortfolioSnapshot> = Collections.synchronizedList(LinkedList())
    val history1M: MutableList<PortfolioSnapshot> = Collections.synchronizedList(LinkedList())

    init {
        seedHistoricalData()
    }

    private fun seedHistoricalData() {
        val now = System.currentTimeMillis()
        val currentWealth = assets.sumOf { it.totalValue }

        // Seed 1D (24 hours)
        var simulatedValue = currentWealth * 0.96
        for (i in 24 downTo 1) {
            simulatedValue += (Math.random() - 0.48) * (currentWealth * 0.012)
            history1D.add(PortfolioSnapshot(now - i * 3600_000L, simulatedValue))
        }

        // Seed 1W (28 points)
        simulatedValue = currentWealth * 0.91
        for (i in 28 downTo 1) {
            simulatedValue += (Math.random() - 0.47) * (currentWealth * 0.025)
            history1W.add(PortfolioSnapshot(now - i * 6 * 3600_000L, simulatedValue))
        }

        // Seed 1M (30 points)
        simulatedValue = currentWealth * 0.82
        for (i in 30 downTo 1) {
            simulatedValue += (Math.random() - 0.46) * (currentWealth * 0.035)
            history1M.add(PortfolioSnapshot(now - i * 24 * 3600_000L, simulatedValue))
        }
    }

    fun start() {
        // Runs every 2 seconds
        scheduler.scheduleAtFixedRate({
            try {
                updatePrices()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 0, 2000, TimeUnit.MILLISECONDS)
    }

    private fun updatePrices() {
        for (asset in assets) {
            // Realistic micro-fluctuation (-0.8% to +0.85%)
            val percentChange = (Math.random() - 0.49) * 0.017
            asset.livePrice = max(0.0001, asset.livePrice * (1.0 + percentChange))
        }

        val totalWealth = assets.sumOf { it.totalValue }
        val now = System.currentTimeMillis()

        // Append snapshot to history lists
        history1D.add(PortfolioSnapshot(now, totalWealth))
        if (history1D.size > 50) history1D.removeAt(0)

        history1W.add(PortfolioSnapshot(now, totalWealth))
        if (history1W.size > 50) history1W.removeAt(0)

        history1M.add(PortfolioSnapshot(now, totalWealth))
        if (history1M.size > 50) history1M.removeAt(0)

        SwingUtilities.invokeLater {
            onTick(totalWealth)
        }
    }

    fun stop() {
        scheduler.shutdown()
    }
}

// ============================================================================
// 3. UI THEME & CONSTANTS
// ============================================================================

object PortfolioTheme {
    val BG_DARK = Color(11, 14, 20)
    val PANEL_BG = Color(21, 25, 34)
    val CARD_BG = Color(28, 34, 46)
    val BORDER_COLOR = Color(38, 46, 62)
    val TEXT_PRIMARY = Color(240, 244, 248)
    val TEXT_MUTED = Color(138, 150, 168)
    val GAIN_GREEN = Color(0, 230, 118)
    val LOSS_RED = Color(255, 82, 82)
    val ACCENT_PURPLE = Color(108, 92, 231)
    val ACCENT_CYAN = Color(0, 229, 255)
    val FONT_MAIN = Font("SansSerif", Font.PLAIN, 12)
    val FONT_BOLD = Font("SansSerif", Font.BOLD, 12)
    val FONT_TITLE = Font("SansSerif", Font.BOLD, 22)
    val FONT_MONO = Font("Monospaced", Font.PLAIN, 12)
}

// ============================================================================
// 4. GUI COMPONENTS
// ============================================================================

class PortfolioTableModel(val assets: List<AssetHolding>) : AbstractTableModel() {
    private val columnNames = arrayOf(
        "ASSET", "HOLDINGS", "AVG BUY PRICE", "LIVE PRICE", "TOTAL VALUE", "24H CHANGE", "PROFIT / LOSS"
    )

    override fun getRowCount(): Int = assets.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val asset = assets[rowIndex]
        return when (columnIndex) {
            0 -> "${asset.symbol} - ${asset.name}"
            1 -> asset.holdings
            2 -> asset.avgBuyPrice
            3 -> asset.livePrice
            4 -> asset.totalValue
            5 -> asset.change24hPercent
            6 -> asset.totalPnL
            else -> ""
        }
    }
}

class PortfolioTableRenderer : DefaultTableCellRenderer() {
    private val currencyFmt = DecimalFormat("$#,##0.00")
    private val percentFmt = DecimalFormat("+0.00%;-0.00%")
    private val pnlFmt = DecimalFormat("+$#,##0.00;-$#,##0.00")

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        font = PortfolioTheme.FONT_MONO
        background = if (isSelected) PortfolioTheme.CARD_BG else PortfolioTheme.PANEL_BG
        foreground = PortfolioTheme.TEXT_PRIMARY
        horizontalAlignment = if (column == 0) LEFT else RIGHT

        when (column) {
            1 -> {
                if (value is Double) text = String.format("%.4f", value)
            }
            2, 3, 4 -> {
                if (value is Double) text = currencyFmt.format(value)
            }
            5 -> {
                if (value is Double) {
                    text = percentFmt.format(value / 100.0)
                    foreground = if (value >= 0) PortfolioTheme.GAIN_GREEN else PortfolioTheme.LOSS_RED
                }
            }
            6 -> {
                if (value is Double) {
                    text = pnlFmt.format(value)
                    foreground = if (value >= 0) PortfolioTheme.GAIN_GREEN else PortfolioTheme.LOSS_RED
                }
            }
        }

        return c
    }
}

class TrendlineChartCanvas(
    private val engine: MarketSimulationEngine,
    var activeFilter: TimeframeFilter = TimeframeFilter.DAY
) : JPanel() {

    init {
        isOpaque = false
        preferredSize = Dimension(0, 220)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val w = width
        val h = height

        // Background Frame
        g2.color = PortfolioTheme.PANEL_BG
        g2.fill(RoundRectangle2D.Float(0f, 0f, w - 1f, h - 1f, 12f, 12f))
        g2.color = PortfolioTheme.BORDER_COLOR
        g2.draw(RoundRectangle2D.Float(0f, 0f, w - 1f, h - 1f, 12f, 12f))

        // Get snapshot data based on active timeframe filter
        val rawData = when (activeFilter) {
            TimeframeFilter.DAY -> engine.history1D
            TimeframeFilter.WEEK -> engine.history1W
            TimeframeFilter.MONTH -> engine.history1M
        }

        val snapshots = synchronized(rawData) { ArrayList(rawData) }
        if (snapshots.size < 2) return

        val padLeft = 70
        val padRight = 30
        val padTop = 45
        val padBottom = 35

        val plotW = w - padLeft - padRight
        val plotH = h - padTop - padBottom

        if (plotW <= 0 || plotH <= 0) return

        val minVal = snapshots.minOf { it.totalWealth } * 0.995
        val maxVal = snapshots.maxOf { it.totalWealth } * 1.005
        val range = max(1.0, maxVal - minVal)

        // Draw Title & Timeframe badge
        g2.font = PortfolioTheme.FONT_BOLD
        g2.color = PortfolioTheme.TEXT_PRIMARY
        g2.drawString("PORTFOLIO WEALTH TRENDLINE (${activeFilter.timeframeName.uppercase()})", 18, 28)

        // Horizontal Grid Lines
        val fmt = DecimalFormat("$#,##0")
        val gridSteps = 4
        g2.font = PortfolioTheme.FONT_MONO

        for (i in 0..gridSteps) {
            val valStep = minVal + (range / gridSteps) * i
            val gy = padTop + plotH - (i * plotH / gridSteps)

            g2.color = PortfolioTheme.BORDER_COLOR
            g2.drawLine(padLeft, gy, padLeft + plotW, gy)

            g2.color = PortfolioTheme.TEXT_MUTED
            g2.drawString(fmt.format(valStep), 8, gy + 4)
        }

        // Determine gain or loss trend for curve color
        val firstVal = snapshots.first().totalWealth
        val lastVal = snapshots.last().totalWealth
        val isOverallGain = lastVal >= firstVal

        val lineColor = if (isOverallGain) PortfolioTheme.GAIN_GREEN else PortfolioTheme.LOSS_RED

        val stepX = plotW.toDouble() / (snapshots.size - 1)
        val chartPath = Path2D.Double()
        val areaPath = Path2D.Double()

        areaPath.moveTo(padLeft.toDouble(), (padTop + plotH).toDouble())

        for (i in snapshots.indices) {
            val pt = snapshots[i]
            val px = padLeft + i * stepX
            val py = padTop + plotH - ((pt.totalWealth - minVal) / range * plotH)

            if (i == 0) {
                chartPath.moveTo(px, py)
                areaPath.lineTo(px, py)
            } else {
                chartPath.lineTo(px, py)
                areaPath.lineTo(px, py)
            }
        }

        areaPath.lineTo(padLeft + plotW.toDouble(), (padTop + plotH).toDouble())
        areaPath.closePath()

        // Area Gradient Fill
        val fillGradient = GradientPaint(
            0f, padTop.toFloat(), Color(lineColor.red, lineColor.green, lineColor.blue, 75),
            0f, (padTop + plotH).toFloat(), Color(lineColor.red, lineColor.green, lineColor.blue, 0)
        )
        g2.paint = fillGradient
        g2.fill(areaPath)

        // Draw Trend Line
        g2.color = lineColor
        g2.stroke = BasicStroke(2.2f)
        g2.draw(chartPath)

        // Glowing end point marker
        val lastX = padLeft + (snapshots.size - 1) * stepX
        val lastY = padTop + plotH - ((lastVal - minVal) / range * plotH)

        g2.color = lineColor
        g2.fillOval(lastX.toInt() - 5, lastY.toInt() - 5, 10, 10)
        g2.color = Color.WHITE
        g2.fillOval(lastX.toInt() - 2, lastY.toInt() - 2, 4, 4)

        // Floating tooltip value at current point
        g2.font = PortfolioTheme.FONT_MONO
        g2.color = PortfolioTheme.TEXT_PRIMARY
        val currentStr = DecimalFormat("$#,##0.00").format(lastVal)
        g2.drawString(currentStr, lastX.toInt() - 55, lastY.toInt() - 10)
    }
}

class HeaderMetricsPanel : JPanel() {
    private val totalWealthLabel = JLabel("$0.00")
    private val pnlLabel = JLabel("+$0.00 (+0.00%)")
    private val topGainerLabel = JLabel("Top Gainer: --")

    init {
        isOpaque = false
        layout = BorderLayout()
        border = EmptyBorder(15, 20, 15, 20)

        val leftBox = Box.createVerticalBox()
        val title = JLabel("NET WEALTH BALANCE")
        title.font = PortfolioTheme.FONT_BOLD
        title.foreground = PortfolioTheme.TEXT_MUTED

        totalWealthLabel.font = PortfolioTheme.FONT_TITLE
        totalWealthLabel.foreground = PortfolioTheme.TEXT_PRIMARY

        leftBox.add(title)
        leftBox.add(Box.createVerticalStrut(4))
        leftBox.add(totalWealthLabel)

        val rightBox = Box.createVerticalBox()
        val pnlTitle = JLabel("TOTAL 24H PERFORMANCE")
        pnlTitle.font = PortfolioTheme.FONT_BOLD
        pnlTitle.foreground = PortfolioTheme.TEXT_MUTED

        pnlLabel.font = Font("Monospaced", Font.BOLD, 18)
        pnlLabel.foreground = PortfolioTheme.GAIN_GREEN

        topGainerLabel.font = PortfolioTheme.FONT_MAIN
        topGainerLabel.foreground = PortfolioTheme.ACCENT_CYAN

        rightBox.add(pnlTitle)
        rightBox.add(Box.createVerticalStrut(4))
        rightBox.add(pnlLabel)
        rightBox.add(topGainerLabel)

        add(leftBox, BorderLayout.WEST)
        add(rightBox, BorderLayout.EAST)
    }

    fun updateMetrics(assets: List<AssetHolding>) {
        val totalWealth = assets.sumOf { it.totalValue }
        val totalCost = assets.sumOf { it.totalCost }
        val totalPnL = totalWealth - totalCost
        val percentPnL = if (totalCost == 0.0) 0.0 else (totalPnL / totalCost) * 100.0

        val currencyFmt = DecimalFormat("$#,##0.00")
        totalWealthLabel.text = currencyFmt.format(totalWealth)

        val sign = if (totalPnL >= 0) "+" else ""
        pnlLabel.text = String.format("%s$%,.2f (%s%.2f%%)", sign, totalPnL, sign, percentPnL)

        if (totalPnL >= 0) {
            pnlLabel.foreground = PortfolioTheme.GAIN_GREEN
        } else {
            pnlLabel.foreground = PortfolioTheme.LOSS_RED
        }

        val topGainer = assets.maxByOrNull { it.change24hPercent }
        if (topGainer != null) {
            topGainerLabel.text = String.format("Top 24h Move: %s (+%.2f%%)", topGainer.symbol, topGainer.change24hPercent)
        }
    }
}

// ============================================================================
// 5. MAIN DASHBOARD WINDOW
// ============================================================================

class PortfolioTrackerFrame : JFrame("Multi-Currency Crypto & Asset Portfolio Tracker") {

    private val assets = listOf(
        AssetHolding("BTC", "Bitcoin", 1.45, 54200.0, 64500.0, 63800.0),
        AssetHolding("ETH", "Ethereum", 12.8, 2850.0, 3450.0, 3410.0),
        AssetHolding("SOL", "Solana", 145.0, 110.0, 142.0, 138.5),
        AssetHolding("DOT", "Polkadot", 850.0, 6.20, 7.15, 7.30),
        AssetHolding("AVAX", "Avalanche", 220.0, 24.50, 28.40, 27.90),
        AssetHolding("LINK", "Chainlink", 500.0, 13.80, 16.90, 16.40),
        AssetHolding("NEAR", "NEAR Protocol", 1200.0, 4.10, 5.25, 5.10),
        AssetHolding("ADA", "Cardano", 8500.0, 0.38, 0.42, 0.43)
    )

    private val tableModel = PortfolioTableModel(assets)
    private val table = JTable(tableModel)

    private val engine: MarketSimulationEngine
    private val chartCanvas: TrendlineChartCanvas
    private val headerPanel = HeaderMetricsPanel()

    private val filterButtons = mutableListOf<JButton>()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(1200, 820)
        minimumSize = Dimension(980, 680)
        contentPane.background = PortfolioTheme.BG_DARK
        layout = BorderLayout(12, 12)

        // Initialize Simulation Engine
        engine = MarketSimulationEngine(assets) {
            tableModel.fireTableDataChanged()
            headerPanel.updateMetrics(assets)
            chartCanvas.repaint()
        }

        chartCanvas = TrendlineChartCanvas(engine)

        // Table Setup
        table.rowHeight = 32
        table.background = PortfolioTheme.PANEL_BG
        table.foreground = PortfolioTheme.TEXT_PRIMARY
        table.gridColor = PortfolioTheme.BORDER_COLOR
        table.selectionBackground = PortfolioTheme.CARD_BG
        table.selectionForeground = PortfolioTheme.ACCENT_CYAN
        table.font = PortfolioTheme.FONT_MAIN
        table.tableHeader.font = PortfolioTheme.FONT_BOLD
        table.tableHeader.background = PortfolioTheme.CARD_BG
        table.tableHeader.foreground = PortfolioTheme.TEXT_MUTED
        table.tableHeader.preferredSize = Dimension(0, 35)

        val renderer = PortfolioTableRenderer()
        for (i in 0 until table.columnCount) {
            table.columnModel.getColumn(i).cellRenderer = renderer
        }

        val tableScroll = JScrollPane(table)
        tableScroll.border = BorderFactory.createLineBorder(PortfolioTheme.BORDER_COLOR)
        tableScroll.viewport.background = PortfolioTheme.PANEL_BG

        // Filter Bar (1D, 1W, 1M Quick Action Buttons)
        val filterBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8))
        filterBar.isOpaque = false

        val filterTitle = JLabel("TIMEFRAME FILTER:")
        filterTitle.font = PortfolioTheme.FONT_BOLD
        filterTitle.foreground = PortfolioTheme.TEXT_MUTED
        filterBar.add(filterTitle)

        TimeframeFilter.values().forEach { filter ->
            val btn = JButton(filter.label)
            btn.font = PortfolioTheme.FONT_BOLD
            btn.isFocusPainted = false
            btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            updateButtonStyle(btn, filter == chartCanvas.activeFilter)

            btn.addActionListener {
                chartCanvas.activeFilter = filter
                filterButtons.forEach { updateButtonStyle(it, it.text == filter.label) }
                chartCanvas.repaint()
            }

            filterButtons.add(btn)
            filterBar.add(btn)
        }

        // Chart Container Panel (Table directly above Chart Canvas)
        val centerPanel = JPanel(BorderLayout(10, 10))
        centerPanel.isOpaque = false
        centerPanel.border = EmptyBorder(0, 15, 10, 15)

        val chartContainer = JPanel(BorderLayout(6, 6))
        chartContainer.isOpaque = false
        chartContainer.add(filterBar, BorderLayout.NORTH)
        chartContainer.add(chartCanvas, BorderLayout.CENTER)

        // Table directly above Chart Canvas
        val verticalSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        verticalSplit.isOpaque = false
        verticalSplit.dividerSize = 6
        verticalSplit.resizeWeight = 0.55
        verticalSplit.border = null
        verticalSplit.topComponent = tableScroll
        verticalSplit.bottomComponent = chartContainer

        centerPanel.add(verticalSplit, BorderLayout.CENTER)

        // Add to main frame
        add(headerPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)

        // Initial updates & start thread
        headerPanel.updateMetrics(assets)
        engine.start()
    }

    private fun updateButtonStyle(btn: JButton, isActive: Boolean) {
        if (isActive) {
            btn.background = PortfolioTheme.ACCENT_PURPLE
            btn.foreground = Color.WHITE
            btn.border = BorderFactory.createLineBorder(PortfolioTheme.ACCENT_CYAN, 2)
        } else {
            btn.background = PortfolioTheme.CARD_BG
            btn.foreground = PortfolioTheme.TEXT_MUTED
            btn.border = BorderFactory.createLineBorder(PortfolioTheme.BORDER_COLOR, 1)
        }
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

        val frame = PortfolioTrackerFrame()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}