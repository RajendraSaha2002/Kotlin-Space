import java.awt.*
import java.awt.geom.Path2D
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer

/**
 * Main Entry Point
 */
fun main() {
    // Use the system's native look and feel for a cleaner UI
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Exception) {
        e.printStackTrace()
    }

    SwingUtilities.invokeLater {
        val app = NetworkMonitorApp()
        app.isVisible = true
    }
}

/**
 * Main Application Frame
 */
class NetworkMonitorApp : JFrame("Network Monitoring System") {

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(1000, 700)
        setLocationRelativeTo(null) // Center on screen

        // Start with the Login Panel
        contentPane = LoginPanel(this)
    }

    fun showMonitoringConsole() {
        contentPane = MainConsolePanel()
        revalidate()
        repaint()
    }
}

/**
 * 1. Login Panel
 */
class LoginPanel(private val app: NetworkMonitorApp) : JPanel(GridBagLayout()) {
    init {
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL

        val titleLabel = JLabel("Network Monitoring Login").apply {
            font = Font("Arial", Font.BOLD, 24)
            horizontalAlignment = SwingConstants.CENTER
        }

        val userLabel = JLabel("Username:")
        val userField = JTextField(15).apply { text = "admin" }

        val passLabel = JLabel("Password:")
        val passField = JPasswordField(15).apply { text = "password" }

        val loginBtn = JButton("Login").apply {
            font = Font("Arial", Font.BOLD, 14)
            addActionListener {
                // Dummy authentication
                if (userField.text == "admin" && String(passField.password) == "password") {
                    app.showMonitoringConsole()
                } else {
                    JOptionPane.showMessageDialog(this@LoginPanel, "Invalid Credentials", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; add(titleLabel, gbc)
        gbc.gridwidth = 1; gbc.gridy = 1; add(userLabel, gbc)
        gbc.gridx = 1; add(userField, gbc)
        gbc.gridx = 0; gbc.gridy = 2; add(passLabel, gbc)
        gbc.gridx = 1; add(passField, gbc)
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; add(loginBtn, gbc)
    }
}

/**
 * 2. Main Monitoring Console with Multi-tab navigation
 */
class MainConsolePanel : JPanel(BorderLayout()) {

    private val tabbedPane = JTabbedPane()

    // Core Panels
    private val devicePanel = DeviceStatusPanel()
    private val graphPanel = TrafficGraphPanel()
    private val pingPanel = PingSimulationPanel()
    private val logsPanel = LogsConsolePanel()
    private val alertsPanel = AlertDashboardPanel()

    // Simulator
    private val timer = javax.swing.Timer(1000) { simulateRealTimeUpdates() }

    init {
        tabbedPane.addTab("Device Status", ImageIcon(), devicePanel, "View status of all network devices")
        tabbedPane.addTab("Traffic Graph", ImageIcon(), graphPanel, "Real-time bandwidth usage")
        tabbedPane.addTab("Ping Tool", ImageIcon(), pingPanel, "Simulate ICMP Ping")
        tabbedPane.addTab("Logs", ImageIcon(), logsPanel, "System Logs")
        tabbedPane.addTab("Alerts", ImageIcon(), alertsPanel, "Active Network Alerts")

        add(tabbedPane, BorderLayout.CENTER)

        // Start real-time simulation
        timer.start()
        logsPanel.addLog("SYSTEM", "Monitoring engine started successfully.")
    }

    private fun simulateRealTimeUpdates() {
        // Update Devices (Random bandwidths, random offline events)
        devicePanel.updateDeviceData(logsPanel, alertsPanel)

        // Update Graph
        val totalBandwidth = devicePanel.getTotalBandwidth()
        graphPanel.addDataPoint(totalBandwidth)
    }
}

/**
 * 3. Device Status Panel (LEDs and Progress Bars)
 */
class DeviceStatusPanel : JPanel(BorderLayout()) {

    private val columnNames = arrayOf("Device Name", "IP Address", "Status", "Bandwidth Usage")
    private val tableModel = DefaultTableModel(columnNames, 0)
    private val table = JTable(tableModel)

    // Initial dummy data
    private val devices = mutableListOf(
        Device("Main Router", "192.168.1.1", true, 45),
        Device("Switch Core-1", "192.168.1.2", true, 80),
        Device("Web Server", "192.168.1.10", true, 20),
        Device("Database Server", "192.168.1.11", true, 10),
        Device("Firewall", "192.168.1.254", true, 60),
        Device("Access Point", "192.168.1.50", false, 0)
    )

    init {
        table.rowHeight = 30
        table.getColumnModel().getColumn(2).cellRenderer = StatusLedRenderer()
        table.getColumnModel().getColumn(3).cellRenderer = ProgressRenderer()

        refreshTable()
        add(JScrollPane(table), BorderLayout.CENTER)
    }

    private fun refreshTable() {
        tableModel.rowCount = 0
        for (device in devices) {
            tableModel.addRow(arrayOf(device.name, device.ip, device.isOnline, device.bandwidthUsage))
        }
    }

    fun updateDeviceData(logsPanel: LogsConsolePanel, alertsPanel: AlertDashboardPanel) {
        val random = Random()
        devices.forEach {
            if (it.isOnline) {
                // Fluctuate bandwidth
                var newBw = it.bandwidthUsage + (random.nextInt(21) - 10)
                if (newBw < 0) newBw = 0
                if (newBw > 100) newBw = 100
                it.bandwidthUsage = newBw

                // Randomly disconnect
                if (random.nextInt(100) > 97) {
                    it.isOnline = false
                    it.bandwidthUsage = 0
                    logsPanel.addLog("WARNING", "Device ${it.name} (${it.ip}) went OFFLINE.")
                    alertsPanel.addAlert("HIGH", "Connection lost to ${it.name}")
                }
            } else {
                // Randomly reconnect
                if (random.nextInt(100) > 90) {
                    it.isOnline = true
                    logsPanel.addLog("INFO", "Device ${it.name} (${it.ip}) is back ONLINE.")
                }
            }
        }
        refreshTable()
    }

    fun getTotalBandwidth(): Int {
        return devices.filter { it.isOnline }.sumOf { it.bandwidthUsage } / devices.size
    }

    data class Device(val name: String, val ip: String, var isOnline: Boolean, var bandwidthUsage: Int)
}

// Custom Renderer for LEDs
class StatusLedRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val label = super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column) as JLabel
        val isOnline = value as? Boolean ?: false
        label.icon = LedIcon(isOnline)
        label.horizontalAlignment = SwingConstants.CENTER
        return label
    }

    class LedIcon(private val isOnline: Boolean) : Icon {
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = if (isOnline) Color(50, 205, 50) else Color(220, 20, 60)
            g2.fillOval(x + 4, y + 2, iconWidth, iconHeight)
            g2.color = Color.DARK_GRAY
            g2.drawOval(x + 4, y + 2, iconWidth, iconHeight)
        }
        override fun getIconWidth() = 16
        override fun getIconHeight() = 16
    }
}

// Custom Renderer for Progress Bars
class ProgressRenderer : JProgressBar(0, 100), TableCellRenderer {
    init { isStringPainted = true }
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val progress = (value as? Int) ?: 0
        setValue(progress)
        foreground = when {
            progress > 85 -> Color(220, 20, 60) // Red if high
            progress > 60 -> Color(255, 140, 0) // Orange if medium
            else -> Color(50, 205, 50)          // Green if normal
        }
        return this
    }
}

/**
 * 4. Traffic Graph Panel (Custom Graphics2D)
 */
class TrafficGraphPanel : JPanel(BorderLayout()) {
    private val maxDataPoints = 60
    private val dataPoints = mutableListOf<Int>()
    private val graphCanvas = GraphCanvas()

    init {
        // Initialize with zeros
        for (i in 0 until maxDataPoints) dataPoints.add(0)

        val title = JLabel("Network Traffic (Avg Bandwidth %) - Last 60 Seconds", SwingConstants.CENTER)
        title.font = Font("Arial", Font.BOLD, 16)
        title.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        add(title, BorderLayout.NORTH)
        add(graphCanvas, BorderLayout.CENTER)
    }

    fun addDataPoint(value: Int) {
        if (dataPoints.size >= maxDataPoints) {
            dataPoints.removeAt(0)
        }
        dataPoints.add(value)
        graphCanvas.repaint()
    }

    inner class GraphCanvas : JPanel() {
        init { background = Color(30, 30, 30) } // Dark theme for graph

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val width = width
            val height = height
            val padding = 40

            // Draw Grid
            g2.color = Color(60, 60, 60)
            for (i in 0..10) {
                val y = height - padding - (i * (height - 2 * padding) / 10)
                g2.drawLine(padding, y, width - padding, y)
                g2.drawString("${i * 10}%", 5, y + 5)
            }

            // Draw axes
            g2.color = Color.WHITE
            g2.drawLine(padding, padding, padding, height - padding) // Y
            g2.drawLine(padding, height - padding, width - padding, height - padding) // X

            // Draw Line Graph
            if (dataPoints.isEmpty()) return

            val pointXOffset = (width - 2.0 * padding) / (maxDataPoints - 1)
            val path = Path2D.Double()

            for (i in dataPoints.indices) {
                val x = padding + (i * pointXOffset)
                // Normalize value to height
                val scaledY = (dataPoints[i] / 100.0) * (height - 2 * padding)
                val y = height - padding - scaledY

                if (i == 0) path.moveTo(x, y)
                else path.lineTo(x, y)
            }

            g2.color = Color(0, 191, 255) // Cyan line
            g2.stroke = BasicStroke(2f)
            g2.draw(path)

            // Fill area under graph
            path.lineTo(width - padding.toDouble(), (height - padding).toDouble())
            path.lineTo(padding.toDouble(), (height - padding).toDouble())
            path.closePath()
            g2.color = Color(0, 191, 255, 50)
            g2.fill(path)
        }
    }
}

/**
 * 5. Ping Simulation Window
 */
class PingSimulationPanel : JPanel(BorderLayout()) {
    private val consoleArea = JTextArea().apply {
        background = Color.BLACK
        foreground = Color.GREEN
        font = Font("Monospaced", Font.PLAIN, 14)
        isEditable = false
    }

    private val ipInput = JTextField("192.168.1.1", 15)
    private val startBtn = JButton("Start Ping")
    private val stopBtn = JButton("Stop").apply { isEnabled = false }

    private var pingCounter = 0
    private var pingTimer: javax.swing.Timer? = null

    init {
        val topPanel = JPanel().apply {
            add(JLabel("Target IP: "))
            add(ipInput)
            add(startBtn)
            add(stopBtn)
        }

        add(topPanel, BorderLayout.NORTH)
        add(JScrollPane(consoleArea), BorderLayout.CENTER)

        startBtn.addActionListener { startPing() }
        stopBtn.addActionListener { stopPing() }
    }

    private fun startPing() {
        val targetIp = ipInput.text
        if (targetIp.isBlank()) return

        consoleArea.text = "Pinging $targetIp with 32 bytes of data:\n\n"
        startBtn.isEnabled = false
        stopBtn.isEnabled = true
        ipInput.isEnabled = false
        pingCounter = 0

        val random = Random()
        pingTimer = javax.swing.Timer(1000) {
            pingCounter++
            val time = random.nextInt(40) + 1
            if (random.nextInt(100) > 95) {
                consoleArea.append("Request timed out.\n")
            } else {
                consoleArea.append("Reply from $targetIp: bytes=32 time=${time}ms TTL=64\n")
            }

            // Auto scroll to bottom
            consoleArea.caretPosition = consoleArea.document.length
        }
        pingTimer?.start()
    }

    private fun stopPing() {
        pingTimer?.stop()
        consoleArea.append("\nPing statistics for ${ipInput.text}:\n    Packets: Sent = $pingCounter\n")
        startBtn.isEnabled = true
        stopBtn.isEnabled = false
        ipInput.isEnabled = true
    }
}

/**
 * 6. Logs Console
 */
class LogsConsolePanel : JPanel(BorderLayout()) {
    private val logsArea = JTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 12)
        isEditable = false
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    init {
        val title = JLabel("System Event Logs", SwingConstants.LEFT).apply {
            font = Font("Arial", Font.BOLD, 14)
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        }
        add(title, BorderLayout.NORTH)
        add(JScrollPane(logsArea), BorderLayout.CENTER)
    }

    fun addLog(level: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] [$level] $message\n"
        logsArea.append(logEntry)
        logsArea.caretPosition = logsArea.document.length // Auto scroll
    }
}

/**
 * 7. Alert Dashboard
 */
class AlertDashboardPanel : JPanel(BorderLayout()) {
    private val columnNames = arrayOf("Timestamp", "Severity", "Message")
    private val tableModel = DefaultTableModel(columnNames, 0)
    private val table = JTable(tableModel)
    private val dateFormat = SimpleDateFormat("HH:mm:ss")

    init {
        table.rowHeight = 25
        table.getColumnModel().getColumn(1).cellRenderer = SeverityRenderer()

        val title = JLabel("Active Network Alerts", SwingConstants.LEFT).apply {
            font = Font("Arial", Font.BOLD, 14)
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        }

        add(title, BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
    }

    fun addAlert(severity: String, message: String) {
        val timestamp = dateFormat.format(Date())
        // Insert at the top
        tableModel.insertRow(0, arrayOf(timestamp, severity, message))

        // Keep only last 50 alerts
        if (tableModel.rowCount > 50) {
            tableModel.removeRow(50)
        }
    }

    // Custom renderer for Severity column
    class SeverityRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val severity = value as? String ?: ""

            cell.foreground = Color.WHITE
            when (severity.uppercase()) {
                "HIGH" -> cell.background = Color(220, 20, 60) // Red
                "MEDIUM" -> cell.background = Color(255, 140, 0) // Orange
                "LOW" -> cell.background = Color(255, 215, 0) // Gold
                else -> cell.background = table?.background ?: Color.WHITE
            }
            if (!isSelected && severity.isEmpty()) {
                cell.background = table?.background ?: Color.WHITE
                cell.foreground = table?.foreground ?: Color.BLACK
            }
            return cell
        }
    }
}