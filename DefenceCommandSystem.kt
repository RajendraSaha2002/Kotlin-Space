import java.awt.*
import java.sql.Connection
import java.sql.DriverManager
import javax.swing.*
import javax.swing.table.DefaultTableModel
import kotlin.system.exitProcess

class DefenceCommandSystem : JFrame("Defence Operations Command Panel") {

    // UPDATE THESE WITH YOUR POSTGRESQL CREDENTIALS
    private val url = "jdbc:postgresql://localhost:5432/postgres" // Change 'postgres' to your db name
    private val dbUser = "postgres"
    private val dbPass = "varrie75"

    private lateinit var conn: Connection

    private val missionModel = DefaultTableModel(arrayOf("ID", "Mission Name", "Status", "Location"), 0)
    private val unitModel = DefaultTableModel(arrayOf("ID", "Unit Name", "Status", "Personnel"), 0)
    private val logArea = JTextArea(10, 50)
    private val alertLight = JPanel()

    init {
        connectDatabase()
        showLoginScreen()
    }

    private fun connectDatabase() {
        try {
            conn = DriverManager.getConnection(url, dbUser, dbPass)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, "Database Connection Failed: ${e.message}")
            exitProcess(1)
        }
    }

    private fun showLoginScreen() {
        setSize(400, 200)
        layout = BorderLayout()
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)

        val panel = JPanel(GridLayout(3, 2, 10, 10))
        panel.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
        panel.background = Color.DARK_GRAY

        val userLabel = JLabel("Username:").apply { foreground = Color.GREEN }
        val passLabel = JLabel("Password:").apply { foreground = Color.GREEN }

        val userField = JTextField()
        val passField = JPasswordField()
        val loginBtn = JButton("SECURE LOGIN").apply {
            background = Color.BLACK
            foreground = Color.GREEN
        }

        panel.add(userLabel)
        panel.add(userField)
        panel.add(passLabel)
        panel.add(passField)
        panel.add(JLabel(""))
        panel.add(loginBtn)

        add(panel, BorderLayout.CENTER)

        loginBtn.addActionListener {
            val username = userField.text
            val password = String(passField.password)

            val stmt = conn.prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?")
            stmt.setString(1, username)
            stmt.setString(2, password)
            val rs = stmt.executeQuery()

            if (rs.next()) {
                logAction("User $username logged in. Clearance Level: ${rs.getInt("clearance_level")}")
                buildCommandCenter()
            } else {
                JOptionPane.showMessageDialog(this, "ACCESS DENIED", "Security Alert", JOptionPane.ERROR_MESSAGE)
            }
        }

        isVisible = true
    }

    private fun buildCommandCenter() {
        contentPane.removeAll()
        setSize(1200, 800)
        setLocationRelativeTo(null)
        layout = BorderLayout(10, 10)
        contentPane.background = Color.BLACK

        // Top Panel: Alert System & Animated Lights
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply { background = Color.BLACK }
        val title = JLabel("COMMAND CENTER ONLINE").apply {
            foreground = Color.GREEN
            font = Font("Monospaced", Font.BOLD, 24)
        }

        alertLight.preferredSize = Dimension(30, 30)
        alertLight.background = Color.DARK_GRAY
        startAnimatedLight()

        val emergencyBtn = JButton("TRIGGER EMERGENCY").apply { background = Color.RED; foreground = Color.WHITE }
        emergencyBtn.addActionListener { triggerEmergency() }

        topPanel.add(alertLight)
        topPanel.add(title)
        topPanel.add(Box.createHorizontalStrut(50))
        topPanel.add(emergencyBtn)

        // Center Panel: Multi-pane layout (Missions & Units)
        val centerSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createMissionPanel(), createUnitPanel())
        centerSplit.dividerLocation = 550

        // Bottom Panel: Communications & Logs
        val bottomPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GREEN), "Communication Console / Logs")
            (border as javax.swing.border.TitledBorder).titleColor = Color.GREEN
            background = Color.BLACK
        }
        logArea.background = Color.BLACK
        logArea.foreground = Color.GREEN
        logArea.font = Font("Monospaced", Font.PLAIN, 14)
        logArea.isEditable = false
        bottomPanel.add(JScrollPane(logArea), BorderLayout.CENTER)

        add(topPanel, BorderLayout.NORTH)
        add(centerSplit, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        refreshData()
        revalidate()
        repaint()
    }

    private fun createMissionPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply { background = Color.BLACK }
        panel.border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GREEN), "Mission Status")
        (panel.border as javax.swing.border.TitledBorder).titleColor = Color.GREEN

        val table = JTable(missionModel).apply {
            background = Color.BLACK
            foreground = Color.GREEN
            gridColor = Color.DARK_GRAY
        }
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun createUnitPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply { background = Color.BLACK }
        panel.border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GREEN), "Unit Deployment")
        (panel.border as javax.swing.border.TitledBorder).titleColor = Color.GREEN

        val table = JTable(unitModel).apply {
            background = Color.BLACK
            foreground = Color.GREEN
            gridColor = Color.DARK_GRAY
        }
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun startAnimatedLight() {
        var isRed = false
        Timer(800) {
            alertLight.background = if (isRed) Color.DARK_GRAY else Color.RED
            isRed = !isRed
            alertLight.repaint()
        }.start()
    }

    private fun triggerEmergency() {
        logAction("DEFCON 1: EMERGENCY OVERRIDE TRIGGERED!")
        JOptionPane.showMessageDialog(this, "EMERGENCY PROTOCOL ACTIVATED!", "DEFCON 1", JOptionPane.WARNING_MESSAGE)
    }

    private fun logAction(message: String) {
        val stmt = conn.prepareStatement("INSERT INTO logs (message) VALUES (?)")
        stmt.setString(1, message)
        stmt.executeUpdate()
        refreshLogs()
    }

    private fun refreshData() {
        // Load Missions
        missionModel.rowCount = 0
        var rs = conn.createStatement().executeQuery("SELECT * FROM missions")
        while (rs.next()) {
            missionModel.addRow(arrayOf(rs.getInt("id"), rs.getString("mission_name"), rs.getString("status"), rs.getString("location")))
        }

        // Load Units
        unitModel.rowCount = 0
        rs = conn.createStatement().executeQuery("SELECT * FROM units")
        while (rs.next()) {
            unitModel.addRow(arrayOf(rs.getInt("id"), rs.getString("unit_name"), rs.getString("deployment_status"), rs.getInt("personnel_count")))
        }

        refreshLogs()
    }

    private fun refreshLogs() {
        logArea.text = ""
        val rs = conn.createStatement().executeQuery("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 20")
        while (rs.next()) {
            logArea.append("[${rs.getTimestamp("timestamp")}] ${rs.getString("message")}\n")
        }
    }
}

fun main() {
    // Set UI to a darker theme to fit the military vibe
    UIManager.put("Panel.background", Color.BLACK)
    UIManager.put("Label.foreground", Color.GREEN)

    SwingUtilities.invokeLater { DefenceCommandSystem() }
}