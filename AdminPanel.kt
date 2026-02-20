import java.awt.*
import java.sql.DriverManager
import javax.swing.*
import javax.swing.table.DefaultTableModel

class AdminPanel : JFrame("University Admin Control Panel") {
    // DB Config - Update Password!
    private val url = "jdbc:postgresql://localhost:5432/postgres"
    private val user = "postgres"
    private val password = "varrie75"

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(900, 600)
        layout = BorderLayout()

        // 1. Sidebar
        val sidebar = JPanel()
        sidebar.layout = GridLayout(5, 1, 5, 5)
        sidebar.background = Color(44, 62, 80)
        sidebar.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val btnUsers = createStyledButton("Manage Users")
        val btnCourses = createStyledButton("Course Allocation")
        val btnBackup = createStyledButton("Backup Database")

        sidebar.add(btnUsers)
        sidebar.add(btnCourses)
        sidebar.add(btnBackup)

        add(sidebar, BorderLayout.WEST)

        // 2. Main Content Area (Tabbed)
        val tabbedPane = JTabbedPane()

        // Tab A: Faculty Workload
        val workloadPanel = createWorkloadPanel()
        tabbedPane.addTab("Faculty Workload", workloadPanel)

        // Tab B: System Messages
        val msgPanel = createMessagePanel()
        tabbedPane.addTab("System Messages", msgPanel)

        add(tabbedPane, BorderLayout.CENTER)

        isVisible = true
    }

    private fun createStyledButton(text: String): JButton {
        val btn = JButton(text)
        btn.background = Color(52, 152, 219)
        btn.foreground = Color.WHITE
        btn.isFocusPainted = false
        return btn
    }

    private fun createWorkloadPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val columns = arrayOf("Faculty Name", "Course Code", "Course Name", "Credits")
        val model = DefaultTableModel(columns, 0)
        val table = JTable(model)

        // Fetch Data via JDBC
        try {
            val conn = DriverManager.getConnection(url, user, password)
            val sql = """
                SELECT u.full_name, c.course_code, c.course_name, c.credits
                FROM courses c
                JOIN users u ON c.faculty_id = u.user_id
                WHERE u.role_id = 2
            """
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery(sql)

            while (rs.next()) {
                model.addRow(arrayOf(
                    rs.getString("full_name"),
                    rs.getString("course_code"),
                    rs.getString("course_name"),
                    rs.getInt("credits")
                ))
            }
            conn.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        panel.add(JScrollPane(table), BorderLayout.CENTER)

        val btnRefresh = JButton("Refresh Workload")
        panel.add(btnRefresh, BorderLayout.SOUTH)

        return panel
    }

    private fun createMessagePanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val textArea = JTextArea()
        textArea.isEditable = false
        textArea.text = "Loading system messages...\n"

        try {
            val conn = DriverManager.getConnection(url, user, password)
            val sql = "SELECT subject, body, sent_at FROM messages ORDER BY sent_at DESC LIMIT 5"
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery(sql)

            val sb = StringBuilder()
            if (!rs.isBeforeFirst) {
                sb.append("No messages found.")
            }
            while (rs.next()) {
                sb.append("Subject: ${rs.getString("subject")}\n")
                sb.append("Time: ${rs.getString("sent_at")}\n")
                sb.append("Body: ${rs.getString("body")}\n")
                sb.append("-----------------------------\n")
            }
            textArea.text = sb.toString()
            conn.close()
        } catch (e: Exception) {
            textArea.text = "Error loading messages."
        }

        panel.add(JScrollPane(textArea), BorderLayout.CENTER)
        return panel
    }
}

fun main() {
    // Ensure you have postgresql-42.x.x.jar in your classpath
    SwingUtilities.invokeLater { AdminPanel() }
}