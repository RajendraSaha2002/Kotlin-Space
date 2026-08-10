import java.awt.*
import java.awt.event.*
import javax.swing.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Main Application Entry Point.
 */
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Vertical Endless Highway Avoidance")
        val panel = HighwayGamePanel()

        frame.add(panel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Main Highway Panel handling Physics, AABB Collisions, and Visual Rendering.
 */
class HighwayGamePanel : JPanel(), ActionListener, KeyListener {

    companion object {
        const val WIDTH = 500
        const val HEIGHT = 700
        const val FPS = 60
        const val DELAY = 1000 / FPS

        const val NUM_LANES = 4
        const val ROAD_MARGIN = 60.0
        const val ROAD_WIDTH = WIDTH - (ROAD_MARGIN * 2)
        const val LANE_WIDTH = ROAD_WIDTH / NUM_LANES

        const val CAR_WIDTH = 42.0
        const val CAR_HEIGHT = 75.0
    }

    enum class GameState { START, PLAYING, GAMEOVER }

    // Commuter Traffic Vehicle Model
    data class TrafficCar(
        var x: Double,
        var y: Double,
        val width: Double = CAR_WIDTH,
        val height: Double = CAR_HEIGHT,
        val speed: Double,
        val color: Color
    )

    private var state = GameState.START

    // Player Vehicle State
    private var playerX = ROAD_MARGIN + (LANE_WIDTH * 1.5) - (CAR_WIDTH / 2.0)
    private val playerY = HEIGHT - CAR_HEIGHT - 40.0
    private var targetLane = 1 // Lanes 0, 1, 2, 3

    // Traffic & Highway Scrolling State
    private val trafficCars = mutableListOf<TrafficCar>()
    private var roadScrollOffset = 0.0
    private var baseTrafficSpeed = 6.0
    private var spawnTimer = 0

    // Stats & Score
    private var survivalTimeSeconds = 0.0
    private var score = 0
    private var highScore = 0
    private var carsPassed = 0

    private val timer = Timer(DELAY, this)

    init {
        preferredSize = Dimension(WIDTH, HEIGHT)
        background = Color(20, 24, 32)
        isFocusable = true
        addKeyListener(this)

        timer.start()
    }

    private fun resetGame() {
        targetLane = 1
        playerX = getLaneCenterX(targetLane) - (CAR_WIDTH / 2.0)
        trafficCars.clear()
        survivalTimeSeconds = 0.0
        score = 0
        carsPassed = 0
        baseTrafficSpeed = 6.0
        spawnTimer = 0
        state = GameState.PLAYING
    }

    private fun getLaneCenterX(laneIndex: Int): Double {
        return ROAD_MARGIN + (laneIndex * LANE_WIDTH) + (LANE_WIDTH / 2.0)
    }

    override fun actionPerformed(e: ActionEvent?) {
        if (state == GameState.PLAYING) {
            updateGameLogic()
        }
        repaint()
    }

    /**
     * Active Game Physics & Collision Loop
     */
    private fun updateGameLogic() {
        val dt = 1.0 / FPS
        survivalTimeSeconds += dt

        // Scale difficulty & traffic speed over time
        baseTrafficSpeed = 6.0 + (survivalTimeSeconds * 0.15).coerceAtMost(8.0)

        // Road Scrolling Animation
        roadScrollOffset = (roadScrollOffset + baseTrafficSpeed) % 40.0

        // Smooth Interpolated Steering between lanes
        val targetX = getLaneCenterX(targetLane) - (CAR_WIDTH / 2.0)
        playerX += (targetX - playerX) * 0.25

        // Spawn Commuter Vehicles
        spawnTimer++
        val spawnInterval = max(25, 65 - (survivalTimeSeconds * 0.8).toInt())

        if (spawnTimer >= spawnInterval) {
            spawnTimer = 0
            spawnTrafficCar()
        }

        // Update Traffic Movement & Test Collisions
        val iterator = trafficCars.iterator()
        while (iterator.hasNext()) {
            val car = iterator.next()
            car.y += car.speed

            // 2D Axis-Aligned Bounding Box (AABB) Collision Test
            if (checkAABB(playerX, playerY, CAR_WIDTH, CAR_HEIGHT,
                    car.x, car.y, car.width, car.height)) {
                state = GameState.GAMEOVER
                if (score > highScore) highScore = score
                return
            }

            // Remove off-screen traffic
            if (car.y > HEIGHT + 100) {
                iterator.remove()
                carsPassed++
            }
        }

        // Update Score
        score = (survivalTimeSeconds * 10).toInt() + (carsPassed * 25)
        if (score > highScore) highScore = score
    }

    private fun spawnTrafficCar() {
        val lane = Random.nextInt(NUM_LANES)
        val laneX = getLaneCenterX(lane) - (CAR_WIDTH / 2.0)

        // Prevent overlapping spawns in the same lane
        for (existing in trafficCars) {
            if (existing.y < 120.0 && abs(existing.x - laneX) < 10.0) {
                return
            }
        }

        val colors = arrayOf(
            Color(230, 60, 60),   // Red
            Color(240, 200, 40),  // Yellow
            Color(40, 180, 220),  // Cyan
            Color(220, 220, 230), // Silver
            Color(160, 60, 220)   // Purple
        )

        val speed = baseTrafficSpeed + Random.nextDouble(-1.5, 2.5)
        val color = colors[Random.nextInt(colors.size)]

        trafficCars.add(TrafficCar(laneX, -CAR_HEIGHT - 20.0, CAR_WIDTH, CAR_HEIGHT, speed, color))
    }

    /**
     * 2D Axis-Aligned Bounding Box (AABB) Collision Formula
     */
    private fun checkAABB(
        ax: Double, ay: Double, aw: Double, ah: Double,
        bx: Double, by: Double, bw: Double, bh: Double
    ): Boolean {
        return ax < bx + bw &&
                ax + aw > bx &&
                ay < by + bh &&
                ay + ah > by
    }

    /**
     * Graphics Rendering Engine
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw Highway
        drawHighway(g2)

        // Draw Traffic Vehicles
        for (car in trafficCars) {
            drawCar(g2, car.x, car.y, car.width, car.height, car.color, isPlayer = false)
        }

        // Draw Player Sports Car
        drawCar(g2, playerX, playerY, CAR_WIDTH, CAR_HEIGHT, Color(0, 230, 140), isPlayer = true)

        // Draw HUD Overlays
        drawHUD(g2)
    }

    private fun drawHighway(g2: Graphics2D) {
        // Grass Shoulders
        g2.color = Color(30, 80, 45)
        g2.fillRect(0, 0, ROAD_MARGIN.toInt(), HEIGHT)
        g2.fillRect((WIDTH - ROAD_MARGIN).toInt(), 0, ROAD_MARGIN.toInt(), HEIGHT)

        // Asphalt Road Bed
        g2.color = Color(40, 45, 55)
        g2.fillRect(ROAD_MARGIN.toInt(), 0, ROAD_WIDTH.toInt(), HEIGHT)

        // Solid Yellow Shoulder Lines
        g2.color = Color(240, 200, 40)
        g2.stroke = BasicStroke(4f)
        g2.drawLine(ROAD_MARGIN.toInt(), 0, ROAD_MARGIN.toInt(), HEIGHT)
        g2.drawLine((WIDTH - ROAD_MARGIN).toInt(), 0, (WIDTH - ROAD_MARGIN).toInt(), HEIGHT)

        // Dashed White Lane Markings
        g2.color = Color(240, 240, 240)
        g2.stroke = BasicStroke(2f)

        for (i in 1 until NUM_LANES) {
            val laneX = (ROAD_MARGIN + i * LANE_WIDTH).toInt()
            var y = -40.0 + roadScrollOffset
            while (y < HEIGHT + 40) {
                g2.drawLine(laneX, y.toInt(), laneX, (y + 20).toInt())
                y += 40.0
            }
        }
    }

    private fun drawCar(
        g2: Graphics2D,
        x: Double, y: Double, w: Double, h: Double,
        color: Color, isPlayer: Boolean
    ) {
        val xi = x.toInt()
        val yi = y.toInt()
        val wi = w.toInt()
        val hi = h.toInt()

        // Drop Shadow
        g2.color = Color(0, 0, 0, 90)
        g2.fillRoundRect(xi + 4, yi + 4, wi, hi, 12, 12)

        // Car Body
        g2.color = color
        g2.fillRoundRect(xi, yi, wi, hi, 12, 12)

        // Roof Cabin
        g2.color = color.darker()
        g2.fillRoundRect(xi + 5, yi + 18, wi - 10, hi - 36, 8, 8)

        // Windshield Glass
        g2.color = Color(40, 50, 70)
        if (isPlayer) {
            g2.fillRect(xi + 7, yi + 18, wi - 14, 12)     // Front Windshield
            g2.fillRect(xi + 7, yi + hi - 26, wi - 14, 8) // Rear Glass
        } else {
            g2.fillRect(xi + 7, yi + hi - 28, wi - 14, 12) // Front Windshield
            g2.fillRect(xi + 7, yi + 18, wi - 14, 8)      // Rear Glass
        }

        // Headlights & Taillights
        if (isPlayer) {
            // Player Cyan Headlights
            g2.color = Color(200, 255, 255)
            g2.fillRect(xi + 4, yi + 2, 8, 4)
            g2.fillRect(xi + wi - 12, yi + 2, 8, 4)

            // Red Taillights
            g2.color = Color(255, 40, 40)
            g2.fillRect(xi + 4, yi + hi - 4, 8, 3)
            g2.fillRect(xi + wi - 12, yi + hi - 4, 8, 3)
        } else {
            // Traffic Headlights
            g2.color = Color(255, 250, 200)
            g2.fillRect(xi + 4, yi + hi - 4, 8, 3)
            g2.fillRect(xi + wi - 12, yi + hi - 4, 8, 3)

            // Taillights
            g2.color = Color(255, 50, 50)
            g2.fillRect(xi + 4, yi + 2, 8, 3)
            g2.fillRect(xi + wi - 12, yi + 2, 8, 3)
        }
    }

    private fun drawHUD(g2: Graphics2D) {
        g2.font = Font("Monospaced", Font.BOLD, 18)

        if (state == GameState.PLAYING) {
            g2.color = Color.WHITE
            g2.drawString("SCORE: $score", 20, 30)

            val timeStr = String.format("TIME: %.1fs", survivalTimeSeconds)
            val tw = g2.fontMetrics.stringWidth(timeStr)
            g2.drawString(timeStr, WIDTH - 20 - tw, 30)
        } else {
            drawOverlay(g2)
        }
    }

    private fun drawOverlay(g2: Graphics2D) {
        g2.color = Color(10, 14, 22, 210)
        g2.fillRect(0, 0, WIDTH, HEIGHT)

        g2.color = Color.WHITE
        g2.font = Font("Monospaced", Font.BOLD, 28)

        val title = if (state == GameState.START) "HIGHWAY AVOIDANCE" else "TRAFFIC CRASH!"
        val tw = g2.fontMetrics.stringWidth(title)
        g2.drawString(title, (WIDTH - tw) / 2, HEIGHT / 2 - 40)

        g2.font = Font("Monospaced", Font.PLAIN, 16)
        g2.color = Color(200, 220, 255)

        val sub1 = if (state == GameState.START) "Use LEFT / RIGHT Arrow Keys to Switch Lanes" else "Final Score: $score  |  Best: $highScore"
        val sw1 = g2.fontMetrics.stringWidth(sub1)
        g2.drawString(sub1, (WIDTH - sw1) / 2, HEIGHT / 2 + 15)

        g2.font = Font("Monospaced", Font.BOLD, 16)
        g2.color = Color(0, 230, 140)
        val sub2 = "Press SPACE to " + (if (state == GameState.START) "Start Drive" else "Restart")
        val sw2 = g2.fontMetrics.stringWidth(sub2)
        g2.drawString(sub2, (WIDTH - sw2) / 2, HEIGHT / 2 + 65)
    }

    // Input Controller
    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT, KeyEvent.VK_A -> {
                if (state == GameState.PLAYING && targetLane > 0) {
                    targetLane--
                }
            }
            KeyEvent.VK_RIGHT, KeyEvent.VK_D -> {
                if (state == GameState.PLAYING && targetLane < NUM_LANES - 1) {
                    targetLane++
                }
            }
            KeyEvent.VK_SPACE -> {
                if (state != GameState.PLAYING) {
                    resetGame()
                }
            }
        }
    }

    override fun keyReleased(e: KeyEvent) {}
    override fun keyTyped(e: KeyEvent) {}
}