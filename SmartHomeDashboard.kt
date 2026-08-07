package com.smarthome.iot.dashboard

import java.awt.*
import java.awt.event.ActionListener
import java.awt.geom.Arc2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.math.max

// ============================================================================
// 1. DATA MODELS & ENUMS
// ============================================================================

enum class TariffState(
    val title: String,
    val ratePerKwh: Double,
    val accentColor: Color,
    val bgGlowColor: Color
) {
    OFF_PEAK("OFF-PEAK TARIFF", 0.12, Color(0, 230, 118), Color(0, 230, 118, 30)),
    PEAK_HOURS("PEAK PRICING HOURS", 0.38, Color(255, 61, 0), Color(255, 61, 0, 45))
}

data class ApplianceItem(
    val id: String,
    val name: String,
    val category: String,
    val baseWattage: Int,
    var isOn: Boolean = false
)

// ============================================================================
// 2. UI THEME & CONSTANTS
// ============================================================================

object SmartHomeTheme {
    val BG_DARK = Color(12, 16, 23)
    val CARD_BG = Color(22, 28, 38)
    val CARD_BORDER_DEFAULT = Color(38, 48, 64)
    val TEXT_PRIMARY = Color(242, 246, 252)
    val TEXT_MUTED = Color(140, 152, 172)
    val ACCENT_CYAN = Color(0, 229, 255)
    val ACCENT_BLUE = Color(41, 121, 255)
    val FONT_TITLE = Font("SansSerif", Font.BOLD, 16)
    val FONT_MAIN = Font("SansSerif", Font.PLAIN, 12)
    val FONT_BOLD = Font("SansSerif", Font.BOLD, 12)
    val FONT_BIG_NUM = Font("Monospaced", Font.BOLD, 22)
    val FONT_MONO = Font("Monospaced", Font.PLAIN, 11)
}

// ============================================================================
// 3. GUI COMPONENTS
// ============================================================================

/**
 * Modern Custom JToggleButton styled as a Smart Switch Pill
 */
class ModernToggleSwitch(val appliance: ApplianceItem, private val onStateChanged: (Boolean) -> Unit) : JToggleButton() {

    init {
        isSelected = appliance.isOn
        isOpaque = false
        isFocusPainted = false
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(54, 28)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        addActionListener {
            appliance.isOn = isSelected
            onStateChanged(isSelected)
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val w = width
        val h = height

        // Pill track background
        val trackColor = if (isSelected) SmartHomeTheme.ACCENT_CYAN else Color(45, 55, 72)
        g2.color = trackColor
        g2.fill(RoundRectangle2D.Float(0f, 0f, w - 1f, h - 1f, h.toFloat(), h.toFloat()))

        // Thumb slider circle
        val thumbRadius = h - 6
        val thumbX = if (isSelected) w - thumbRadius - 3 else 3
        val thumbY = 3

        g2.color = Color.WHITE
        g2.fillOval(thumbX, thumbY, thumbRadius, thumbRadius)
    }
}

/**
 * Custom Vector Radial Gauge Dial Component
 */
class RadialGaugeDial(
    val title: String,
    val unit: String,
    val minVal: Double,
    val maxVal: Double,
    var currentVal: Double,
    val gaugeColor: Color
) : JPanel() {

    init {
        isOpaque = false
        preferredSize = Dimension(140, 140)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val size = minOf(width, height) - 16
        val x = (width - size) / 2
        val y = (height - size) / 2 + 6

        val startAngle = 225.0
        val sweepAngle = -270.0

        // Background Arc Track
        g2.stroke = BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = Color(35, 45, 60)
        g2.draw(Arc2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble(), startAngle, sweepAngle, Arc2D.OPEN))

        // Value Arc Progress
        val fraction = ((currentVal - minVal) / (maxVal - minVal)).coerceIn(0.0, 1.0)
        val activeSweep = sweepAngle * fraction

        g2.color = gaugeColor
        g2.draw(Arc2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble(), startAngle, activeSweep, Arc2D.OPEN))

        // Value Text Display
        g2.font = SmartHomeTheme.FONT_BIG_NUM
        g2.color = SmartHomeTheme.TEXT_PRIMARY
        val valStr = if (currentVal >= 100) String.format("%.0f", currentVal) else String.format("%.1f", currentVal)
        val valWidth = g2.fontMetrics.stringWidth(valStr)
        g2.drawString(valStr, width / 2 - valWidth / 2, y + size / 2 + 4)

        // Title & Unit Subtext
        g2.font = Font("SansSerif", Font.BOLD, 10)
        g2.color = SmartHomeTheme.TEXT_MUTED
        val unitWidth = g2.fontMetrics.stringWidth(unit)
        g2.drawString(unit, width / 2 - unitWidth / 2, y + size / 2 + 18)

        val titleWidth = g2.fontMetrics.stringWidth(title.uppercase())
        g2.drawString(title.uppercase(), width / 2 - titleWidth / 2, y - 2)
    }
}

/**
 * Animated Live Power Grid Wattage Graph Panel
 */
class WattageGraphPanel : JPanel() {
    val history = Collections.synchronizedList(LinkedList<Int>())
    var currentTariff: TariffState = TariffState.OFF_PEAK

    init {
        isOpaque = false
        for (i in 0..40) {
            history.add(450)
        }
    }

    fun addValue(wattage: Int) {
        history.add(wattage)
        while (history.size > 50) {
            history.removeAt(0)
        }
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val w = width
        val h = height

        val padLeft = 50
        val padRight = 20
        val padTop = 30
        val padBottom = 30

        val plotW = w - padLeft - padRight
        val plotH = h - padTop - padBottom

        if (plotW <= 0 || plotH <= 0) return

        val data = synchronized(history) { ArrayList(history) }
        val maxWatt = max(3500, (data.maxOrNull() ?: 1000) + 500)

        // Horizontal Gridlines
        g2.font = SmartHomeTheme.FONT_MONO
        val steps = 4
        for (i in 0..steps) {
            val valStep = (maxWatt / steps) * i
            val gy = padTop + plotH - (i * plotH / steps)

            g2.color = Color(32, 42, 58)
            g2.drawLine(padLeft, gy, padLeft + plotW, gy)

            g2.color = SmartHomeTheme.TEXT_MUTED
            g2.drawString("${valStep}W", 8, gy + 4)
        }

        // Plot Wattage Curve
        val stepX = plotW.toDouble() / (data.size - 1).coerceAtLeast(1)
        val path = Path2D.Double()
        val areaPath = Path2D.Double()

        areaPath.moveTo(padLeft.toDouble(), (padTop + plotH).toDouble())

        for (i in data.indices) {
            val px = padLeft + i * stepX
            val py = padTop + plotH - (data[i].toDouble() / maxWatt * plotH)

            if (i == 0) {
                path.moveTo(px, py)
                areaPath.lineTo(px, py)
            } else {
                path.lineTo(px, py)
                areaPath.lineTo(px, py)
            }
        }

        areaPath.lineTo(padLeft + plotW.toDouble(), (padTop + plotH).toDouble())
        areaPath.closePath()

        // Dynamic Accent Shift Color Gradient
        val strokeColor = currentTariff.accentColor
        val fillGrad = GradientPaint(
            0f, padTop.toFloat(), Color(strokeColor.red, strokeColor.green, strokeColor.blue, 90),
            0f, (padTop + plotH).toFloat(), Color(strokeColor.red, strokeColor.green, strokeColor.blue, 0)
        )

        g2.paint = fillGrad
        g2.fill(areaPath)

        g2.color = strokeColor
        g2.stroke = BasicStroke(2.2f)
        g2.draw(path)

        // Live Endpoint Marker Glow
        if (data.isNotEmpty()) {
            val lastX = padLeft + (data.size - 1) * stepX
            val lastY = padTop + plotH - (data.last().toDouble() / maxWatt * plotH)

            g2.color = strokeColor
            g2.fillOval(lastX.toInt() - 5, lastY.toInt() - 5, 10, 10)
            g2.color = Color.WHITE
            g2.fillOval(lastX.toInt() - 2, lastY.toInt() - 2, 4, 4)

            g2.font = SmartHomeTheme.FONT_BOLD
            g2.color = SmartHomeTheme.TEXT_PRIMARY
            g2.drawString("${data.last()} W", lastX.toInt() - 40, lastY.toInt() - 10)
        }
    }
}

/**
 * Base Wrapper Panel for Dashboard Quadrants
 */
class QuadrantCardPanel(val title: String) : JPanel() {
    var borderGlowColor: Color = SmartHomeTheme.CARD_BORDER_DEFAULT

    init {
        isOpaque = false
        layout = BorderLayout(8, 8)
        border = EmptyBorder(12, 14, 12, 14)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val w = width
        val h = height

        // Background Card
        g2.color = SmartHomeTheme.CARD_BG
        g2.fill(RoundRectangle2D.Float(0f, 0f, w - 1f, h - 1f, 14f, 14f))

        // Dynamic Border Lighting Accent
        g2.color = borderGlowColor
        g2.stroke = BasicStroke(1.5f)
        g2.draw(RoundRectangle2D.Float(0f, 0f, w - 1f, h - 1f, 14f, 14f))

        // Quadrant Title
        g2.font = SmartHomeTheme.FONT_TITLE
        g2.color = SmartHomeTheme.TEXT_PRIMARY
        g2.drawString(title, 16, 26)
    }
}

// ============================================================================
// 4. MAIN COMMAND CENTER DASHBOARD FRAME
// ============================================================================

class SmartHomeDashboardFrame : JFrame("Smart Home IoT Automation & Energy Grid Dashboard") {

    private val appliances = listOf(
        ApplianceItem("a1", "HVAC Climate Control", "Climate", 1800, true),
        ApplianceItem("a2", "EV Fast Charger", "Garage", 3200, false),
        ApplianceItem("a3", "Smart Washer / Dryer", "Utility", 1200, false),
        ApplianceItem("a4", "Water Heater Unit", "Utility", 1500, true),
        ApplianceItem("a5", "Kitchen Oven & Range", "Kitchen", 1100, false),
        ApplianceItem("a6", "Living Room Lighting", "Lighting", 180, true)
    )

    private var currentTariff: TariffState = TariffState.OFF_PEAK

    // Quadrants & Custom Controls
    private val q1Panel = QuadrantCardPanel("QUADRANT I: LIVE ENERGY GRID LOAD")
    private val q2Panel = QuadrantCardPanel("QUADRANT II: UTILITY & AMBIENT DIALS")
    private val q3Panel = QuadrantCardPanel("QUADRANT III: APPLIANCE CONTROL CENTER")
    private val q4Panel = QuadrantCardPanel("QUADRANT IV: AUTOMATION & TARIFF STATS")

    private val wattageGraph = WattageGraphPanel()

    private val powerDial = RadialGaugeDial("Power", "kW", 0.0, 10.0, 2.48, SmartHomeTheme.ACCENT_CYAN)
    private val waterDial = RadialGaugeDial("Water Flow", "L/min", 0.0, 25.0, 4.2, SmartHomeTheme.ACCENT_BLUE)
    private val solarDial = RadialGaugeDial("Solar Output", "kW", 0.0, 6.0, 3.85, Color(255, 214, 0))
    private val tempDial = RadialGaugeDial("Indoor Temp", "°C", 10.0, 40.0, 21.5, Color(255, 145, 0))

    private val tariffBadge = JLabel("OFF-PEAK TARIFF")
    private val costLabel = JLabel("Est. Rate: $0.12 / kWh")
    private val dailyTotalLabel = JLabel("Today's Cost: $3.42")
    private val solarOffsetLabel = JLabel("Solar Offset: 62%")

    private val mainTimer: javax.swing.Timer

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(1280, 820)
        minimumSize = Dimension(1024, 700)
        contentPane.background = SmartHomeTheme.BG_DARK
        layout = BorderLayout(10, 10)

        val mainGrid = JPanel(GridLayout(2, 2, 12, 12))
        mainGrid.isOpaque = false
        mainGrid.border = EmptyBorder(10, 12, 12, 12)

        setupQuadrant1()
        setupQuadrant2()
        setupQuadrant3()
        setupQuadrant4()

        mainGrid.add(q1Panel)
        mainGrid.add(q2Panel)
        mainGrid.add(q3Panel)
        mainGrid.add(q4Panel)

        add(createHeaderPanel(), BorderLayout.NORTH)
        add(mainGrid, BorderLayout.CENTER)

        // Main Refresh Loop (Runs every 1 second)
        mainTimer = javax.swing.Timer(1000, ActionListener {
            updateDashboardMetrics()
        })
        mainTimer.start()
    }

    private fun createHeaderPanel(): JPanel {
        val header = JPanel(BorderLayout())
        header.isOpaque = false
        header.border = EmptyBorder(12, 18, 0, 18)

        val titleLabel = JLabel("RESIDENTIAL COMMAND CENTER // IOT ENERGY AUTOMATION")
        titleLabel.font = Font("SansSerif", Font.BOLD, 18)
        titleLabel.foreground = SmartHomeTheme.TEXT_PRIMARY

        val tariffToggleBtn = JButton("TOGGLE PEAK/OFF-PEAK HOURS")
        tariffToggleBtn.font = SmartHomeTheme.FONT_BOLD
        tariffToggleBtn.foreground = Color.WHITE
        tariffToggleBtn.background = Color(38, 48, 64)
        tariffToggleBtn.isFocusPainted = false
        tariffToggleBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        tariffToggleBtn.addActionListener {
            currentTariff = if (currentTariff == TariffState.OFF_PEAK) TariffState.PEAK_HOURS else TariffState.OFF_PEAK
            applyTariffAccentShift()
        }

        header.add(titleLabel, BorderLayout.WEST)
        header.add(tariffToggleBtn, BorderLayout.EAST)
        return header
    }

    private fun setupQuadrant1() {
        q1Panel.add(Box.createVerticalStrut(25), BorderLayout.NORTH)
        q1Panel.add(wattageGraph, BorderLayout.CENTER)
    }

    private fun setupQuadrant2() {
        val dialsGrid = JPanel(GridLayout(2, 2, 8, 8))
        dialsGrid.isOpaque = false
        dialsGrid.border = EmptyBorder(25, 0, 0, 0)

        dialsGrid.add(powerDial)
        dialsGrid.add(waterDial)
        dialsGrid.add(solarDial)
        dialsGrid.add(tempDial)

        q2Panel.add(dialsGrid, BorderLayout.CENTER)
    }

    private fun setupQuadrant3() {
        val listPanel = JPanel()
        listPanel.isOpaque = false
        listPanel.layout = BoxLayout(listPanel, BoxLayout.Y_AXIS)
        listPanel.border = EmptyBorder(30, 8, 8, 8)

        appliances.forEach { item ->
            val row = JPanel(BorderLayout())
            row.isOpaque = false
            row.maximumSize = Dimension(Int.MAX_VALUE, 38)

            val lbl = JLabel("${item.name} (${item.baseWattage}W)")
            lbl.font = SmartHomeTheme.FONT_BOLD
            lbl.foreground = SmartHomeTheme.TEXT_PRIMARY

            val categoryLbl = JLabel(item.category.uppercase())
            categoryLbl.font = SmartHomeTheme.FONT_MONO
            categoryLbl.foreground = SmartHomeTheme.TEXT_MUTED

            val leftBox = Box.createHorizontalBox()
            leftBox.add(lbl)
            leftBox.add(Box.createHorizontalStrut(10))
            leftBox.add(categoryLbl)

            val toggle = ModernToggleSwitch(item) {
                updateDashboardMetrics()
            }

            row.add(leftBox, BorderLayout.WEST)
            row.add(toggle, BorderLayout.EAST)

            listPanel.add(row)
            listPanel.add(Box.createVerticalStrut(6))
        }

        q3Panel.add(listPanel, BorderLayout.CENTER)
    }

    private fun setupQuadrant4() {
        val container = JPanel()
        container.isOpaque = false
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(35, 12, 12, 12)

        tariffBadge.font = Font("SansSerif", Font.BOLD, 16)
        tariffBadge.foreground = currentTariff.accentColor

        costLabel.font = SmartHomeTheme.FONT_BIG_NUM
        costLabel.foreground = SmartHomeTheme.TEXT_PRIMARY

        dailyTotalLabel.font = SmartHomeTheme.FONT_MAIN
        dailyTotalLabel.foreground = SmartHomeTheme.TEXT_MUTED

        solarOffsetLabel.font = SmartHomeTheme.FONT_MAIN
        solarOffsetLabel.foreground = SmartHomeTheme.ACCENT_CYAN

        val logArea = JTextArea("14:22:01 - Solar Array output peaking @ 3.85kW\n14:20:12 - HVAC Climate cycle active\n14:15:00 - Grid Tariff state initialized [OFF-PEAK]")
        logArea.font = SmartHomeTheme.FONT_MONO
        logArea.background = Color(16, 22, 30)
        logArea.foreground = SmartHomeTheme.TEXT_MUTED
        logArea.isEditable = false
        logArea.border = EmptyBorder(8, 8, 8, 8)

        container.add(tariffBadge)
        container.add(Box.createVerticalStrut(6))
        container.add(costLabel)
        container.add(Box.createVerticalStrut(4))
        container.add(dailyTotalLabel)
        container.add(solarOffsetLabel)
        container.add(Box.createVerticalStrut(12))
        container.add(JScrollPane(logArea))

        q4Panel.add(container, BorderLayout.CENTER)
    }

    /**
     * Shifts background accent lighting states based on peak energy pricing hours
     */
    private fun applyTariffAccentShift() {
        wattageGraph.currentTariff = currentTariff
        val glow = currentTariff.accentColor

        // Shift panel borders lighting
        q1Panel.borderGlowColor = glow
        q2Panel.borderGlowColor = glow
        q3Panel.borderGlowColor = glow
        q4Panel.borderGlowColor = glow

        tariffBadge.text = currentTariff.title
        tariffBadge.foreground = currentTariff.accentColor

        costLabel.text = String.format("Est. Rate: $%.2f / kWh", currentTariff.ratePerKwh)

        repaint()
    }

    private fun updateDashboardMetrics() {
        // Calculate dynamic active wattage based on ON/OFF appliance switches
        val baseBaselineNoise = 320 // Ambient household baseline load
        val activeWattage = baseBaselineNoise + appliances.filter { it.isOn }.sumOf { it.baseWattage }

        // Update Wattage Graph
        wattageGraph.addValue(activeWattage)

        // Update Dials
        powerDial.currentVal = activeWattage / 1000.0
        waterDial.currentVal = if (appliances.first { it.id == "a3" }.isOn) 14.5 else 2.1
        solarDial.currentVal = max(0.0, 3.85 + (Math.random() * 0.4 - 0.2))
        tempDial.currentVal = if (appliances.first { it.id == "a1" }.isOn) 21.0 else 24.5

        val currentPowerKw = powerDial.currentVal
        val solarKw = solarDial.currentVal
        val offset = if (currentPowerKw <= 0) 100 else ((solarKw / currentPowerKw) * 100).toInt().coerceIn(0, 100)
        solarOffsetLabel.text = "Solar Offset: $offset%"

        // Refresh Panels
        powerDial.repaint()
        waterDial.repaint()
        solarDial.repaint()
        tempDial.repaint()
    }
}

// ============================================================================
// 5. MAIN ENTRY POINT
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

        val frame = SmartHomeDashboardFrame()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}