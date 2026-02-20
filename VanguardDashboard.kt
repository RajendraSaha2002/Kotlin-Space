import org.postgresql.PGConnection
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.geom.AffineTransform
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import javax.swing.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// Data class to hold asset state
data class AssetBlip(
    val callsign: String,
    val type: String,
    var lat: Double,
    var lon: Double,
    var heading: Double,
    var lastUpdate: Long = System.currentTimeMillis()
)

class VanguardDashboard : JFrame("VANGUARD: Joint Operations Command Center") {
    // DB Config
    private val url = "jdbc:postgresql://localhost:5432/postgres"
    private val user = "postgres"
    private val password = "varrie75"

    // State
    private val assets = ConcurrentHashMap<String, AssetBlip>()
    private var radarSweepAngle = 0.0

    // Custom Radar Panel
    private val radarScope = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // 1. Draw Background (Military Dark)
            val w = width
            val h = height
            g2.color = Color(10, 20, 30)
            g2.fillRect(0, 0, w, h)

            // 2. Draw Grid & Concentric Circles
            g2.color = Color(0, 100, 0)
            val cx = w / 2
            val cy = h / 2
            val maxRadius = minOf(w, h) / 2 - 20

            g2.drawOval(cx - maxRadius, cy - maxRadius, maxRadius * 2, maxRadius * 2)
            g2.drawOval(cx - (maxRadius/2), cy - (maxRadius/2), maxRadius, maxRadius)
            g2.drawLine(cx, 0, cx, h)
            g2.drawLine(0, cy, w, cy)

            // 3. Draw Assets (Blips)
            // Map logic: Rough mapping of Lat/Long to X/Y pixels
            // Center Lat: 20.5, Long: 78.9 (India)
            val scale = maxRadius / 3.0 // Zoom level

            assets.values.forEach { blip ->
                val dx = (blip.lon - 78.9629) * scale * 50 // Scale longitude
                val dy = -(blip.lat - 20.5937) * scale * 50 // Scale latitude (invert Y)

                val screenX = cx + dx
                val screenY = cy + dy

                // Draw Icon based on Type
                val oldTrans = g2.transform
                g2.translate(screenX, screenY)
                g2.rotate(Math.toRadians(blip.heading))

                if (blip.type == "FIGHTER") {
                    g2.color = Color.CYAN
                    // Draw Triangle
                    val xPoints = intArrayOf(0, -5, 5)
                    val yPoints = intArrayOf(-10, 5, 5)
                    g2.fillPolygon(xPoints, yPoints, 3)
                } else if (blip.type == "DESTROYER") {
                    g2.color = Color.ORANGE
                    // Draw Box
                    g2.fillRect(-5, -5, 10, 15)
                } else {
                    g2.color = Color.GREEN
                    g2.fillOval(-4, -4, 8, 8)
                }

                // Reset rotation for text
                g2.transform = oldTrans
                g2.color = Color.WHITE
                g2.font = Font("Consolas", Font.PLAIN, 10)
                g2.drawString(blip.callsign, screenX.toInt() + 10, screenY.toInt())
            }

            // 4. Draw Radar Sweep Line
            g2.color = Color(0, 255, 0, 100)
            val sweepX = cx + (maxRadius * cos(radarSweepAngle)).toInt()
            val sweepY = cy + (maxRadius * sin(radarSweepAngle)).toInt()
            g2.drawLine(cx, cy, sweepX, sweepY)

            // Sweep Fade Effect
            val grad = GradientPaint(cx.toFloat(), cy.toFloat(), Color(0, 255, 0, 50),
                sweepX.toFloat(), sweepY.toFloat(), Color(0, 0, 0, 0))
            g2.paint = grad
            g2.fillArc(cx - maxRadius, cy - maxRadius, maxRadius*2, maxRadius*2,
                (-Math.toDegrees(radarSweepAngle)).toInt(), 30)
        }
    }

    // Secure Chat Panel
    private val chatArea = JTextArea()
    private val chatInput = JTextField()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(1000, 700)
        layout = BorderLayout()

        // 1. Setup Radar View
        add(radarScope, BorderLayout.CENTER)

        // 2. Setup Secure Comms Sidebar
        val sidePanel = JPanel(BorderLayout())
        sidePanel.preferredSize = Dimension(250, height)
        sidePanel.background = Color(20, 20, 20)

        val lblTitle = JLabel("SECURE LINK [PMO]")
        lblTitle.foreground = Color.RED
        lblTitle.font = Font("Arial", Font.BOLD, 16)
        lblTitle.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        sidePanel.add(lblTitle, BorderLayout.NORTH)

        chatArea.background = Color.BLACK
        chatArea.foreground = Color.GREEN
        chatArea.font = Font("Monospaced", Font.PLAIN, 12)
        chatArea.isEditable = false
        sidePanel.add(JScrollPane(chatArea), BorderLayout.CENTER)

        val inputPanel = JPanel(BorderLayout())
        chatInput.background = Color(30, 30, 30)
        chatInput.foreground = Color.WHITE
        chatInput.addActionListener { sendSecureMessage() }

        val btnSend = JButton("SEND")
        btnSend.background = Color.RED
        btnSend.foreground = Color.WHITE
        btnSend.addActionListener { sendSecureMessage() }

        inputPanel.add(chatInput, BorderLayout.CENTER)
        inputPanel.add(btnSend, BorderLayout.EAST)
        sidePanel.add(inputPanel, BorderLayout.SOUTH)

        add(sidePanel, BorderLayout.EAST)

        // 3. Animation Timer (60 FPS)
        Timer(16) {
            radarSweepAngle += 0.05
            if (radarSweepAngle > 2 * PI) radarSweepAngle = 0.0
            radarScope.repaint()
        }.start()

        // 4. Start DB Listener
        Thread { listenToRadar() }.start()

        isVisible = true
    }

    private fun sendSecureMessage() {
        val msg = chatInput.text
        if (msg.isNotEmpty()) {
            chatArea.append("[CDS]: $msg\n")
            // Here you would INSERT into secure_messages table
            chatInput.text = ""
        }
    }

    private fun listenToRadar() {
        try {
            val conn = DriverManager.getConnection(url, user, password)
            val pgConn = conn.unwrap(PGConnection::class.java)
            val stmt = conn.createStatement()
            stmt.execute("LISTEN radar_feed")
            stmt.close()

            chatArea.append("SYSTEM: Radar Link Established.\n")

            while (true) {
                val selectStmt = conn.createStatement()
                selectStmt.executeQuery("SELECT 1") // Keepalive
                selectStmt.close()

                val notifications = pgConn.notifications
                if (notifications != null) {
                    for (n in notifications) {
                        val payload = n.parameter // CALLSIGN:TYPE:LAT:LONG:HEADING
                        updateAsset(payload)
                    }
                }
                Thread.sleep(100)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateAsset(payload: String) {
        val parts = payload.split(":")
        val callsign = parts[0]
        val type = parts[1]
        val lat = parts[2].toDouble()
        val lon = parts[3].toDouble()
        val heading = parts[4].toDouble()

        assets[callsign] = AssetBlip(callsign, type, lat, lon, heading)
    }
}

fun main() {
    SwingUtilities.invokeLater { VanguardDashboard() }
}