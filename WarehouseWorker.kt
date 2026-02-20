import java.awt.*
import java.sql.DriverManager
import javax.swing.*
import org.postgresql.PGConnection

class WarehouseWorker : JFrame("Frontline Warehouse Terminal") {
    // DB Config
    private val url = "jdbc:postgresql://localhost:5432/postgres"
    private val user = "postgres"
    private val password = "varrie75"

    private val txtSku = JTextField(10)
    private val txtQty = JTextField(5)
    private val logArea = JTextArea()
    private val notificationLabel = JLabel("System Status: Online")

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(600, 400)
        layout = BorderLayout()

        // 1. Input Panel
        val inputPanel = JPanel()
        inputPanel.add(JLabel("Product SKU:"))
        inputPanel.add(txtSku)
        inputPanel.add(JLabel("Change Qty (- for pick):"))
        inputPanel.add(txtQty)

        val btnUpdate = JButton("Update Stock")
        btnUpdate.background = Color(46, 204, 113)
        btnUpdate.foreground = Color.WHITE
        btnUpdate.addActionListener { updateStock() }
        inputPanel.add(btnUpdate)

        add(inputPanel, BorderLayout.NORTH)

        // 2. Log Area
        logArea.isEditable = false
        add(JScrollPane(logArea), BorderLayout.CENTER)

        // 3. Notification Bar
        notificationLabel.isOpaque = true
        notificationLabel.background = Color.LIGHT_GRAY
        notificationLabel.horizontalAlignment = SwingConstants.CENTER
        notificationLabel.font = Font("Arial", Font.BOLD, 14)
        add(notificationLabel, BorderLayout.SOUTH)

        // Start Background Listener Thread
        Thread { listenForAlerts() }.start()

        isVisible = true
    }

    private fun updateStock() {
        val sku = txtSku.text
        val qtyStr = txtQty.text

        if (sku.isEmpty() || qtyStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter SKU and Qty")
            return
        }

        try {
            val conn = DriverManager.getConnection(url, user, password)
            val sql = "UPDATE products SET stock_qty = stock_qty + ? WHERE sku = ?"
            val pstmt = conn.prepareStatement(sql)
            pstmt.setInt(1, qtyStr.toInt())
            pstmt.setString(2, sku)

            val rows = pstmt.executeUpdate()
            if (rows > 0) {
                logArea.append("Updated $sku by $qtyStr units.\n")
            } else {
                logArea.append("Error: SKU $sku not found.\n")
            }
            conn.close()
        } catch (e: Exception) {
            logArea.append("Error: ${e.message}\n")
        }
    }

    // REAL-TIME LISTENER LOGIC
    private fun listenForAlerts() {
        try {
            val conn = DriverManager.getConnection(url, user, password)
            val pgConn = conn.unwrap(PGConnection::class.java)

            val stmt = conn.createStatement()
            stmt.execute("LISTEN stock_alert") // Subscribe to channel
            stmt.close()

            while (true) {
                // Dummy query to keep connection alive and check notifications
                val selectStmt = conn.createStatement()
                selectStmt.executeQuery("SELECT 1")
                selectStmt.close()

                val notifications = pgConn.notifications
                if (notifications != null) {
                    for (n in notifications) {
                        val payload = n.parameter
                        SwingUtilities.invokeLater {
                            notificationLabel.text = "⚠️ ALERT: Low Stock on $payload"
                            notificationLabel.background = Color.RED
                            notificationLabel.foreground = Color.WHITE
                            JOptionPane.showMessageDialog(this, "CRITICAL SHORTAGE: $payload", "Warehouse Alert", JOptionPane.WARNING_MESSAGE)
                        }
                    }
                }
                Thread.sleep(500) // Polling interval for the driver object
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun main() {
    SwingUtilities.invokeLater { WarehouseWorker() }
}