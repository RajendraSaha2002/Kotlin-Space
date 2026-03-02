import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel
import kotlin.random.Random

fun main() {
    SwingUtilities.invokeLater {
        LoginFrame()
    }
}

// ================= LOGIN FRAME =================

class LoginFrame : JFrame("Secure Admin Login") {

    private val usernameField = JTextField()
    private val passwordField = JPasswordField()
    private val otpField = JTextField()
    private var generatedOTP = ""

    init {
        layout = GridLayout(5, 2, 10, 10)
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(400, 250)
        setLocationRelativeTo(null)

        add(JLabel("Username:"))
        add(usernameField)

        add(JLabel("Password:"))
        add(passwordField)

        add(JLabel("Enter OTP:"))
        add(otpField)

        val generateBtn = JButton("Generate OTP")
        val loginBtn = JButton("Login")

        add(generateBtn)
        add(loginBtn)

        generateBtn.addActionListener {
            generatedOTP = (100000..999999).random().toString()
            JOptionPane.showMessageDialog(this, "Your OTP: $generatedOTP")
        }

        loginBtn.addActionListener {
            if (usernameField.text == "admin" &&
                String(passwordField.password) == "admin123" &&
                otpField.text == generatedOTP
            ) {
                dispose()
                DashboardFrame()
            } else {
                JOptionPane.showMessageDialog(this, "Invalid Credentials")
            }
        }

        isVisible = true
    }
}

// ================= DASHBOARD FRAME =================

class DashboardFrame : JFrame("Secure Admin Control Panel") {

    private val logArea = JTextArea()
    private val statusLabel = JLabel("System Status: Monitoring...")
    private var darkMode = false

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(1000, 600)
        setLocationRelativeTo(null)

        layout = BorderLayout()

        // ===== Sidebar =====
        val sidebar = JPanel()
        sidebar.layout = GridLayout(6, 1, 5, 5)
        sidebar.border = EmptyBorder(10, 10, 10, 10)

        val userBtn = JButton("User Management")
        val logsBtn = JButton("Activity Logs")
        val monitorBtn = JButton("System Monitoring")
        val alertBtn = JButton("Alert Panel")
        val themeBtn = JButton("Toggle Theme")
        val logoutBtn = JButton("Logout")

        sidebar.add(userBtn)
        sidebar.add(logsBtn)
        sidebar.add(monitorBtn)
        sidebar.add(alertBtn)
        sidebar.add(themeBtn)
        sidebar.add(logoutBtn)

        // ===== Main Panel =====
        val mainPanel = JPanel(CardLayout())

        val userPanel = createUserPanel()
        val logsPanel = createLogsPanel()
        val monitorPanel = createMonitorPanel()
        val alertPanel = createAlertPanel()

        mainPanel.add(userPanel, "users")
        mainPanel.add(logsPanel, "logs")
        mainPanel.add(monitorPanel, "monitor")
        mainPanel.add(alertPanel, "alerts")

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, mainPanel)
        splitPane.dividerLocation = 200

        add(splitPane, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        val layoutManager = mainPanel.layout as CardLayout

        userBtn.addActionListener { layoutManager.show(mainPanel, "users") }
        logsBtn.addActionListener { layoutManager.show(mainPanel, "logs") }
        monitorBtn.addActionListener { layoutManager.show(mainPanel, "monitor") }
        alertBtn.addActionListener { layoutManager.show(mainPanel, "alerts") }

        themeBtn.addActionListener { toggleTheme(this) }

        logoutBtn.addActionListener {
            dispose()
            LoginFrame()
        }

        startStatusAnimation()

        isVisible = true
    }

    // ===== User Management Panel =====
    private fun createUserPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val tableModel = DefaultTableModel(arrayOf("User ID", "Role"), 0)
        val table = JTable(tableModel)

        tableModel.addRow(arrayOf("1", "Admin"))
        tableModel.addRow(arrayOf("2", "Operator"))

        panel.add(JScrollPane(table), BorderLayout.CENTER)

        return panel
    }

    // ===== Logs Panel =====
    private fun createLogsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        logArea.isEditable = false
        logArea.append("System started...\n")
        panel.add(JScrollPane(logArea), BorderLayout.CENTER)
        return panel
    }

    // ===== Monitoring Panel =====
    private fun createMonitorPanel(): JPanel {
        val panel = JPanel()
        panel.layout = GridLayout(3, 1)

        val cpu = JLabel("CPU Usage: ${Random.nextInt(10, 90)}%")
        val memory = JLabel("Memory Usage: ${Random.nextInt(20, 80)}%")
        val network = JLabel("Network Traffic: ${Random.nextInt(100, 900)} KB/s")

        panel.add(cpu)
        panel.add(memory)
        panel.add(network)

        return panel
    }

    // ===== Alert Panel =====
    private fun createAlertPanel(): JPanel {
        val panel = JPanel()
        val alertBtn = JButton("Simulate Security Alert")

        alertBtn.addActionListener {
            JOptionPane.showMessageDialog(panel, "⚠ Suspicious Activity Detected!")
            logArea.append("Security Alert Triggered!\n")
        }

        panel.add(alertBtn)
        return panel
    }

    // ===== Animated Status =====
    private fun startStatusAnimation() {
        Timer(1000) {
            statusLabel.text = "System Status: Monitoring... ${Random.nextInt(100)}"
        }.start()
    }

    // ===== Theme Switching =====
    private fun toggleTheme(frame: JFrame) {
        darkMode = !darkMode
        val bg = if (darkMode) Color.DARK_GRAY else Color.WHITE
        val fg = if (darkMode) Color.WHITE else Color.BLACK

        frame.contentPane.background = bg
        updateComponentTree(frame.contentPane, bg, fg)
    }

    private fun updateComponentTree(comp: Component, bg: Color, fg: Color) {
        comp.background = bg
        comp.foreground = fg
        if (comp is Container) {
            for (child in comp.components) {
                updateComponentTree(child, bg, fg)
            }
        }
    }
}