import java.awt.*
import java.awt.event.*
import java.awt.geom.Path2D
import javax.swing.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Main Application Entry Point.
 */
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("OutRun Retro Pseudo-3D Racer")
        val panel = OutRunRacerPanel()

        frame.add(panel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Main Pseudo-3D Engine Panel handling Projection Math, Road Rendering, and Physics.
 */
class OutRunRacerPanel : JPanel(), ActionListener, KeyListener {

    companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
        const val FPS = 60
        const val DELAY = 1000 / FPS

        // Pseudo-3D Projection Constants
        const val SEGMENT_LENGTH = 200.0
        const val ROAD_WIDTH = 2000.0
        const val CAMERA_HEIGHT = 1000.0
        const val CAMERA_DEPTH = 0.84
        const val DRAW_DISTANCE = 140

        // Physics Constants
        const val MAX_SPEED = 12000.0
        const val ACCEL = 180.0
        const val DECEL = -200.0
        const val OFF_ROAD_DECEL = -500.0
    }

    enum class GameState { START, PLAYING, GAMEOVER }

    enum class SpriteType { PALM_TREE, BILLBOARD, BUSH, CACTUS }

    data class Sprite(val type: SpriteType, val xOffset: Double)

    // Segment Strip for Pseudo-3D Projection
    class Segment(
        val index: Int,
        var curve: Double = 0.0,
        var hill: Double = 0.0,
        val sprites: MutableList<Sprite> = mutableListOf()
    ) {
        // Calculated 2D Projection Coordinates
        var p1X = 0.0; var p1Y = 0.0; var p1W = 0.0; var scale1 = 0.0
        var p2X = 0.0; var p2Y = 0.0; var p2W = 0.0; var scale2 = 0.0
    }

    // Game Variables
    private var state = GameState.START
    private val segments = mutableListOf<Segment>()
    private var trackLength = 0.0

    private var playerX = 0.0 // -1.0 (left edge) to 1.0 (right edge)
    private var playerZ = 0.0 // Position along track
    private var speed = 0.0

    private var timeRemaining = 60.0
    private var score = 0
    private var skyOffset = 0.0

    // Keyboard Control Flags
    private var keyLeft = false
    private var keyRight = false
    private var keyUp = false
    private var keyDown = false

    private val timer = Timer(DELAY, this)

    init {
        preferredSize = Dimension(WIDTH, HEIGHT)
        background = Color.BLACK
        isFocusable = true
        addKeyListener(this)

        buildTrack()
        timer.start()
    }

    /**
     * Builds procedural circuit track with curves, rolling hills, and roadside objects.
     */
    private fun buildTrack() {
        segments.clear()

        fun addSection(count: Int, curve: Double, hill: Double, spriteDensity: Double = 0.25) {
            val startIdx = segments.size
            for (i in 0 until count) {
                val seg = Segment(startIdx + i, curve, hill)

                if (Random.nextDouble() < spriteDensity) {
                    val side = if (Random.nextBoolean()) 1.4 + Random.nextDouble(0.2, 0.8) else -1.4 - Random.nextDouble(0.2, 0.8)
                    val type = SpriteType.values()[Random.nextInt(SpriteType.values().size)]
                    seg.sprites.add(Sprite(type, side))
                }
                segments.add(seg)
            }
        }

        // Circuit Sections: Straight, Curves, Rolling Hills, Hairpins
        addSection(100, 0.0, 0.0, 0.3)                   // Starting Straight
        addSection(150, 2.0, 300.0, 0.4)                // Hill & Gentle Right
        addSection(120, -1.8, -250.0, 0.35)              // Downhill & Left
        addSection(100, 0.0, 0.0, 0.2)                   // Straight
        addSection(200, 3.5, 0.0, 0.5)                  // Sharp Right Turn
        addSection(150, -3.0, 400.0, 0.4)               // Rollercoaster Left Hill
        addSection(180, 1.2, -300.0, 0.3)               // Gentle S-Curve
        addSection(100, 0.0, 0.0, 0.1)                   // Final Stretch

        trackLength = segments.size * SEGMENT_LENGTH
    }

    private fun resetGame() {
        playerX = 0.0
        playerZ = 0.0
        speed = 0.0
        timeRemaining = 60.0
        score = 0
        skyOffset = 0.0
        state = GameState.PLAYING
    }

    override fun actionPerformed(e: ActionEvent?) {
        if (state == GameState.PLAYING) {
            updatePhysics()
        }
        repaint()
    }

    /**
     * Vehicle Physics and Track Movement Loop
     */
    private fun updatePhysics() {
        val dt = 1.0 / FPS

        // Timer
        timeRemaining -= dt
        if (timeRemaining <= 0) {
            timeRemaining = 0.0
            state = GameState.GAMEOVER
        }

        // Acceleration & Braking
        if (keyUp) {
            speed += ACCEL * 60 * dt
        } else if (keyDown) {
            speed += DECEL * 60 * dt
        } else {
            speed += (DECEL * 0.3) * 60 * dt
        }

        // Off-road Grass Friction Drag
        val isOffRoad = abs(playerX) > 1.0
        if (isOffRoad && speed > MAX_SPEED * 0.35) {
            speed += OFF_ROAD_DECEL * 60 * dt
        }

        speed = speed.coerceIn(0.0, MAX_SPEED)

        // Steering & Curve Centrifugal Pull
        val speedRatio = speed / MAX_SPEED
        val currentSegIndex = ((playerZ / SEGMENT_LENGTH).toInt()) % segments.size
        val currentSeg = segments[currentSegIndex]

        if (keyLeft) playerX -= 2.2 * dt * speedRatio
        if (keyRight) playerX += 2.2 * dt * speedRatio

        // Centrifugal force pulling car outward on curves
        playerX -= currentSeg.curve * 0.0018 * speedRatio
        playerX = playerX.coerceIn(-2.2, 2.2)

        // Advance World Position
        playerZ += speed * dt
        if (playerZ >= trackLength) {
            playerZ -= trackLength
            score += 5000 // Lap Bonus
            timeRemaining += 25.0
        }

        score += (speed * 0.01).toInt()

        // Parallax Horizon Movement
        skyOffset -= currentSeg.curve * speedRatio * 0.8

        // Collision Check with Roadside Sprites
        for (sprite in currentSeg.sprites) {
            if (abs(playerX - sprite.xOffset) < 0.35 && speed > 1000) {
                speed = 0.0 // Crash Stop
                break
            }
        }
    }

    /**
     * Main Graphics Rendering Loop
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        drawSkyAndHorizon(g2)
        drawRoad(g2)
        drawPlayerCar(g2)
        drawHUD(g2)
    }

    private fun drawSkyAndHorizon(g2: Graphics2D) {
        // Sunset Gradient
        val skyGradient = GradientPaint(
            0f, 0f, Color(40, 10, 65),
            0f, (HEIGHT / 2).toFloat(), Color(255, 110, 80)
        )
        g2.paint = skyGradient
        g2.fillRect(0, 0, WIDTH, HEIGHT / 2)

        // Parallax Retro Sun
        g2.color = Color(255, 220, 50)
        g2.fillOval(WIDTH / 2 - 60, HEIGHT / 2 - 90, 120, 120)

        // Distant Mountain Silhouette
        g2.color = Color(25, 12, 35)
        val mountainPath = Path2D.Double()
        mountainPath.moveTo(0.0, (HEIGHT / 2).toDouble())

        val step = 40
        var mx = -(skyOffset % step)
        while (mx < WIDTH + step) {
            val peakY = HEIGHT / 2.0 - 25.0 - (sin(mx * 0.02) * 20.0)
            mountainPath.lineTo(mx, peakY)
            mx += step
        }
        mountainPath.lineTo(WIDTH.toDouble(), (HEIGHT / 2).toDouble())
        mountainPath.closePath()
        g2.fill(mountainPath)
    }

    /**
     * Pseudo-3D Road Projection & Polygon Rendering
     */
    private fun drawRoad(g2: Graphics2D) {
        val startPos = (playerZ / SEGMENT_LENGTH).toInt()
        var x = 0.0
        var dx = 0.0
        val camY = CAMERA_HEIGHT

        val startSeg = segments[startPos % segments.size]
        val playerYOffset = startSeg.hill

        // Pass 1: 3D to 2D Spatial Projection
        for (n in 0 until DRAW_DISTANCE) {
            val segIdx = (startPos + n) % segments.size
            val seg = segments[segIdx]

            val loopOffset = if (startPos + n >= segments.size) trackLength else 0.0
            val z1 = (seg.index * SEGMENT_LENGTH + loopOffset) - playerZ
            val z2 = z1 + SEGMENT_LENGTH

            if (z1 <= CAMERA_DEPTH) continue

            dx += seg.curve
            x += dx

            val scale1 = CAMERA_DEPTH / z1
            val scale2 = CAMERA_DEPTH / z2

            seg.scale1 = scale1
            seg.scale2 = scale2

            val worldY1 = seg.hill - playerYOffset
            val nextSeg = segments[(segIdx + 1) % segments.size]
            val worldY2 = nextSeg.hill - playerYOffset

            seg.p1X = (WIDTH / 2.0) + (scale1 * (x - playerX * ROAD_WIDTH) * WIDTH / 2.0)
            seg.p1Y = (HEIGHT / 2.0) - (scale1 * (worldY1 - camY) * HEIGHT / 2.0)
            seg.p1W = scale1 * ROAD_WIDTH * WIDTH / 2.0

            seg.p2X = (WIDTH / 2.0) + (scale2 * (x + dx - playerX * ROAD_WIDTH) * WIDTH / 2.0)
            seg.p2Y = (HEIGHT / 2.0) - (scale2 * (worldY2 - camY) * HEIGHT / 2.0)
            seg.p2W = scale2 * ROAD_WIDTH * WIDTH / 2.0
        }

        // Pass 2: Painter's Algorithm Back-to-Front Polygon Rendering
        var maxy = HEIGHT.toDouble()

        for (n in DRAW_DISTANCE - 1 downTo 0) {
            val segIdx = (startPos + n) % segments.size
            val seg = segments[segIdx]

            if (seg.p1Y >= maxy || seg.p2Y >= seg.p1Y) continue

            val isEven = (seg.index / 3) % 2 == 0

            val colorGrass = if (isEven) Color(16, 120, 48) else Color(12, 105, 40)
            val colorRumble = if (isEven) Color(220, 40, 40) else Color(240, 240, 240)
            val colorRoad = if (isEven) Color(75, 80, 90) else Color(68, 72, 82)
            val colorLane = Color(240, 240, 240)

            // Grass Polygon Fill
            g2.color = colorGrass
            g2.fillRect(0, seg.p2Y.toInt(), WIDTH, (seg.p1Y - seg.p2Y).toInt() + 1)

            // Rumble Strips
            val rumbleW1 = seg.p1W * 1.18
            val rumbleW2 = seg.p2W * 1.18
            drawTrapezoid(g2, seg.p1X, seg.p1Y, rumbleW1, seg.p2X, seg.p2Y, rumbleW2, colorRumble)

            // Main Road Strip
            drawTrapezoid(g2, seg.p1X, seg.p1Y, seg.p1W, seg.p2X, seg.p2Y, seg.p2W, colorRoad)

            // Center White Dashed Lane
            if (isEven) {
                drawTrapezoid(g2, seg.p1X, seg.p1Y, seg.p1W * 0.03, seg.p2X, seg.p2Y, seg.p2W * 0.03, colorLane)
            }

            // Draw Roadside Objects
            for (sprite in seg.sprites) {
                drawSprite(g2, seg, sprite)
            }

            maxy = seg.p1Y
        }
    }

    private fun drawTrapezoid(
        g2: Graphics2D,
        x1: Double, y1: Double, w1: Double,
        x2: Double, y2: Double, w2: Double,
        color: Color
    ) {
        val poly = Polygon()
        poly.addPoint((x1 - w1).toInt(), y1.toInt())
        poly.addPoint((x1 + w1).toInt(), y1.toInt())
        poly.addPoint((x2 + w2).toInt(), y2.toInt())
        poly.addPoint((x2 - w2).toInt(), y2.toInt())

        g2.color = color
        g2.fillPolygon(poly)
    }

    private fun drawSprite(g2: Graphics2D, seg: Segment, sprite: Sprite) {
        val spriteX = seg.p1X + (seg.scale1 * sprite.xOffset * ROAD_WIDTH * WIDTH / 2.0)
        val spriteY = seg.p1Y
        val spriteSize = seg.scale1 * 1600.0 * (WIDTH / 800.0)

        if (spriteSize < 4.0) return

        val sx = (spriteX - spriteSize / 2.0).toInt()
        val sy = (spriteY - spriteSize).toInt()
        val size = spriteSize.toInt()

        when (sprite.type) {
            SpriteType.PALM_TREE -> {
                g2.color = Color(110, 70, 35)
                g2.fillRect(sx + size * 4 / 10, sy + size * 3 / 10, size / 5, size * 7 / 10)
                g2.color = Color(0, 200, 80)
                g2.fillOval(sx, sy, size, size / 2)
            }
            SpriteType.BILLBOARD -> {
                g2.color = Color(80, 80, 80)
                g2.fillRect(sx + size / 5, sy + size / 2, size / 10, size / 2)
                g2.fillRect(sx + size * 7 / 10, sy + size / 2, size / 10, size / 2)
                g2.color = Color(240, 50, 90)
                g2.fillRect(sx, sy, size, size / 2)
                g2.color = Color.WHITE
                g2.drawRect(sx, sy, size, size / 2)
                g2.font = Font("Monospaced", Font.BOLD, max(8, size / 5))
                g2.drawString("OUTRUN", sx + size / 8, sy + size / 3)
            }
            SpriteType.BUSH -> {
                g2.color = Color(30, 160, 50)
                g2.fillOval(sx, sy + size / 2, size, size / 2)
            }
            SpriteType.CACTUS -> {
                g2.color = Color(40, 180, 90)
                g2.fillRect(sx + size * 4 / 10, sy, size / 5, size)
                g2.fillRect(sx + size / 5, sy + size / 3, size * 3 / 5, size / 6)
            }
        }
    }

    private fun drawPlayerCar(g2: Graphics2D) {
        val carW = 160
        val carH = 75
        val carX = WIDTH / 2 - carW / 2

        // Off-road bounce effect
        val bounce = if (abs(playerX) > 1.0 && speed > 500) (sin(System.currentTimeMillis() * 0.05) * 6).toInt() else 0
        val carY = HEIGHT - carH - 30 + bounce

        // Red Sports Car Graphic
        g2.color = Color(230, 20, 40)
        g2.fillRoundRect(carX, carY + 20, carW, carH - 20, 15, 15)

        // Cabin
        g2.color = Color(30, 30, 40)
        g2.fillRoundRect(carX + 25, carY, carW - 50, carH - 15, 10, 10)

        // Glass Reflection
        g2.color = Color(120, 180, 240, 180)
        g2.fillRect(carX + 35, carY + 8, carW - 70, 16)

        // Tires
        g2.color = Color(20, 20, 20)
        g2.fillRect(carX - 8, carY + carH - 25, 16, 25)
        g2.fillRect(carX + carW - 8, carY + carH - 25, 16, 25)

        // Tail Lights
        g2.color = Color(255, 60, 60)
        g2.fillRect(carX + 12, carY + 28, 30, 12)
        g2.fillRect(carX + carW - 42, carY + 28, 30, 12)
        g2.color = Color.YELLOW
        g2.fillRect(carX + 44, carY + 30, 12, 8)
        g2.fillRect(carX + carW - 56, carY + 30, 12, 8)
    }

    private fun drawHUD(g2: Graphics2D) {
        g2.font = Font("Monospaced", Font.BOLD, 20)

        if (state == GameState.PLAYING) {
            val mph = (speed / MAX_SPEED * 220).toInt()

            // Speedometer Panel
            g2.color = Color(20, 25, 35, 200)
            g2.fillRect(20, 20, 210, 80)
            g2.color = Color(0, 230, 255)
            g2.drawRect(20, 20, 210, 80)

            g2.color = Color.WHITE
            g2.drawString("SPEED: $mph MPH", 35, 50)

            g2.color = if (timeRemaining < 10) Color(255, 60, 60) else Color(255, 215, 0)
            g2.drawString("TIME : ${timeRemaining.toInt()}s", 35, 80)

            // Score Panel
            g2.color = Color(20, 25, 35, 200)
            g2.fillRect(WIDTH - 230, 20, 210, 50)
            g2.color = Color(0, 230, 255)
            g2.drawRect(WIDTH - 230, 20, 210, 50)

            g2.color = Color.WHITE
            g2.drawString("SCORE: $score", WIDTH - 215, 52)
        } else {
            drawOverlay(g2)
        }
    }

    private fun drawOverlay(g2: Graphics2D) {
        g2.color = Color(10, 15, 25, 220)
        g2.fillRect(0, 0, WIDTH, HEIGHT)

        g2.color = Color.WHITE
        g2.font = Font("Monospaced", Font.BOLD, 36)

        val title = if (state == GameState.START) "OUTRUN ARCADE RACER" else "GAME OVER"
        val tw = g2.fontMetrics.stringWidth(title)
        g2.drawString(title, (WIDTH - tw) / 2, HEIGHT / 2 - 40)

        g2.font = Font("Monospaced", Font.PLAIN, 18)
        g2.color = Color(0, 230, 255)

        val sub1 = if (state == GameState.START) "UP/DOWN = Accel/Brake  |  LEFT/RIGHT = Steer" else "Final Score: $score"
        val sw1 = g2.fontMetrics.stringWidth(sub1)
        g2.drawString(sub1, (WIDTH - sw1) / 2, HEIGHT / 2 + 15)

        g2.font = Font("Monospaced", Font.BOLD, 18)
        g2.color = Color(255, 215, 0)
        val sub2 = "Press SPACE to " + (if (state == GameState.START) "Start Race" else "Try Again")
        val sw2 = g2.fontMetrics.stringWidth(sub2)
        g2.drawString(sub2, (WIDTH - sw2) / 2, HEIGHT / 2 + 65)
    }

    // Input Controller
    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT, KeyEvent.VK_A -> keyLeft = true
            KeyEvent.VK_RIGHT, KeyEvent.VK_D -> keyRight = true
            KeyEvent.VK_UP, KeyEvent.VK_W -> keyUp = true
            KeyEvent.VK_DOWN, KeyEvent.VK_S -> keyDown = true
            KeyEvent.VK_SPACE -> {
                if (state != GameState.PLAYING) {
                    resetGame()
                }
            }
        }
    }

    override fun keyReleased(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT, KeyEvent.VK_A -> keyLeft = false
            KeyEvent.VK_RIGHT, KeyEvent.VK_D -> keyRight = false
            KeyEvent.VK_UP, KeyEvent.VK_W -> keyUp = false
            KeyEvent.VK_DOWN, KeyEvent.VK_S -> keyDown = false
        }
    }

    override fun keyTyped(e: KeyEvent) {}
}