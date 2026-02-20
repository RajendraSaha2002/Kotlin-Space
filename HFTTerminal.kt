import org.postgresql.PGConnection
import java.awt.*
import java.sql.DriverManager
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.*
import kotlin.concurrent.thread

class HFTTerminal : JFrame("HFT Exchange Terminal - Ultra Low Latency") {
    // DB Config
    private val url = "jdbc:postgresql://localhost:5432/postgres"
    private val user = "postgres"
    private val password = "varrie75"

    // Data Structure for Charting (Last 50 ticks for AAPL)
    private val priceHistory = ConcurrentLinkedQueue<Double>()
    private val maxHistory = 50

    // Custom Chart Component
    private val chartPanel = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.color = Color(20, 20, 20) // Dark Background
            g.fillRect(0, 0, width, height)

            if (priceHistory.isEmpty()) return

            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.stroke = BasicStroke(2f)

            // Scaling logic
            val prices = priceHistory.toList()
            val maxPrice = prices.maxOrNull() ?: 100.0
            val minPrice = prices.minOrNull() ?: 0.0
            val range = maxPrice - minPrice

            val stepX = width.toDouble() / (maxHistory - 1)

            var prevX = 0
            var prevY = height / 2

            // Draw Line
            g2.color = Color(46, 204, 113) // Green Line
            for ((i, price) in prices.withIndex()) {
                val x = (i * stepX).toInt()
                // Normalize price to height
                val y = height - ((price - minPrice) / (if (range == 0.0) 1.0 else range) * (height - 40) + 20).toInt()

                if (i > 0) {
                    g2.drawLine(prevX, prevY, x, y)
                }
                prevX = x
                prevY = y
            }

            // Draw current price label
            g2.color = Color.WHITE
            g2.drawString("LIVE: ${prices.last()}", width - 100, 30)
        }
    }

    private val logArea = JTextArea()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(800, 600)
        layout = BorderLayout()

        // 1. Chart Area
        chartPanel.preferredSize = Dimension(800, 400)
        add(chartPanel, BorderLayout.CENTER)

        // 2. Tape (Log) Area
        logArea.rows = 10
        logArea.background = Color.BLACK
        logArea.foreground = Color.GREEN
        logArea.font = Font("Monospaced", Font.PLAIN, 12)
        add(JScrollPane(logArea), BorderLayout.SOUTH)

        isVisible = true

        // Start background listener
        thread { listenToMarketFeed() }
    }

    private fun listenToMarketFeed() {
        try {
            val conn = DriverManager.getConnection(url, user, password)
            val pgConn = conn.unwrap(PGConnection::class.java)

            val stmt = conn.createStatement()
            stmt.execute("LISTEN market_feed") // Subscribing to channel
            stmt.close()

            logToTape("Connected to Exchange Feed. Waiting for ticks...")

            while (true) {
                // Keep connection alive
                val selectStmt = conn.createStatement()
                selectStmt.executeQuery("SELECT 1")
                selectStmt.close()

                val notifications = pgConn.notifications
                if (notifications != null) {
                    for (n in notifications) {
                        val payload = n.parameter // "TICKER:PRICE:VOLUME"
                        processTick(payload)
                    }
                }
                Thread.sleep(100) // Poll every 100ms
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processTick(payload: String) {
        val parts = payload.split(":")
        val ticker = parts[0]
        val price = parts[1].toDouble()
        val volume = parts[2]

        // Update UI on Swing Thread
        SwingUtilities.invokeLater {
            logToTape("[$ticker] $price (Vol: $volume)")

            // Only chart AAPL for this demo
            if (ticker == "AAPL") {
                if (priceHistory.size >= maxHistory) {
                    priceHistory.poll()
                }
                priceHistory.add(price)
                chartPanel.repaint() // Force redraw of the graph
            }
        }
    }

    private fun logToTape(msg: String) {
        logArea.append("$msg\n")
        logArea.caretPosition = logArea.document.length
    }
}

fun main() {
    SwingUtilities.invokeLater { HFTTerminal() }
}