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
        val frame = JFrame("Infinite Isometric Runner (Tower Climb)")
        val panel = IsometricRunnerPanel()

        frame.add(panel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Main Game Panel handling Isometric Projection Math, Physics, and Rendering.
 */
class IsometricRunnerPanel : JPanel(), ActionListener, KeyListener {

    companion object {
        const val WIDTH = 800
        const val HEIGHT = 700
        const val FPS = 60
        const val DELAY = 1000 / FPS

        // Physics Constants
        const val GRAVITY = 0.55
        const val JUMP_IMPULSE = 11.5
        const val MOVE_SPEED = 4.2

        // 3D to 2.5D Isometric Projection Constants (30-degree isometric angle)
        const val ISO_ANGLE = 0.5235987755982988 // 30 degrees in radians
        val COS_30 = cos(ISO_ANGLE)
        val SIN_30 = sin(ISO_ANGLE)
    }

    enum class GameState { START, PLAYING, GAMEOVER }

    private var state = GameState.START

    // Camera State
    private var cameraZ = 0.0
    private var cameraScrollSpeed = 0.85

    // Player 3D Position & Velocity
    private var px = 0.0
    private var py = 0.0
    private var pz = 50.0
    private var vz = 0.0
    private var isGrounded = false

    private val playerW = 28.0
    private val playerD = 28.0
    private val playerH = 38.0

    // Platform Data Structure
    data class Platform(
        var x: Double,
        var y: Double,
        val z: Double,
        val w: Double,
        val d: Double,
        val h: Double,
        val color: Color,
        val isMoving: Boolean = false,
        var moveSpeed: Double = 0.0,
        var moveRange: Double = 0.0,
        val originX: Double = x
    )

    private val platforms = mutableListOf<Platform>()
    private var lastGeneratedZ = 0.0
    private var score = 0
    private var highScore = 0

    // Input Controls
    private var keyLeft = false
    private var keyRight = false
    private var keyUp = false
    private var keyDown = false

    private val timer = Timer(DELAY, this)

    init {
        preferredSize = Dimension(WIDTH, HEIGHT)
        background = Color(16, 20, 30)
        isFocusable = true
        addKeyListener(this)
        timer.start()
    }

    private fun resetGame() {
        px = 0.0
        py = 0.0
        pz = 60.0
        vz = 0.0
        cameraZ = 0.0
        cameraScrollSpeed = 0.85
        score = 0

        platforms.clear()

        // Starting Ground Platform
        platforms.add(Platform(-110.0, -110.0, 0.0, 220.0, 220.0, 30.0, Color(65, 75, 105)))
        lastGeneratedZ = 0.0

        // Populate initial tower stack
        for (i in 1..15) {
            generateNextPlatform()
        }

        state = GameState.PLAYING
    }

    private fun generateNextPlatform() {
        lastGeneratedZ += 75.0
        val z = lastGeneratedZ

        val w = Random.nextDouble(70.0, 110.0)
        val d = Random.nextDouble(70.0, 110.0)
        val h = 20.0

        val x = Random.nextDouble(-120.0, 120.0 - w)
        val y = Random.nextDouble(-120.0, 120.0 - d)

        val isMoving = (z > 140.0) && (Random.nextDouble() < 0.65)
        val moveSpeed = if (isMoving) Random.nextDouble(1.5, 3.2) else 0.0
        val moveRange = if (isMoving) Random.nextDouble(60.0, 110.0) else 0.0

        // Vibrant color shift as player ascends higher
        val hue = ((z / 12.0) % 360) / 360.0
        val color = Color.getHSBColor(hue.toFloat(), 0.75f, 0.85f)

        platforms.add(Platform(x, y, z, w, d, h, color, isMoving, moveSpeed, moveRange, x))
    }

    override fun actionPerformed(e: ActionEvent?) {
        if (state == GameState.PLAYING) {
            updatePhysics()
        }
        repaint()
    }

    /**
     * Active Game & Physics Loop
     */
    private fun updatePhysics() {
        // Continuous upward camera acceleration
        cameraScrollSpeed += 0.00025
        cameraZ += cameraScrollSpeed

        // Catch camera up to player if climbing high
        if (pz - cameraZ > 170.0) {
            cameraZ += (pz - cameraZ - 170.0) * 0.1
        }

        // Horizontal Movement Math
        var dx = 0.0
        var dy = 0.0
        if (keyLeft) { dx -= MOVE_SPEED; dy += MOVE_SPEED }
        if (keyRight) { dx += MOVE_SPEED; dy -= MOVE_SPEED }
        if (keyUp) { dx -= MOVE_SPEED; dy -= MOVE_SPEED }
        if (keyDown) { dx += MOVE_SPEED; dy += MOVE_SPEED }

        px += dx
        py += dy

        // Vertical Gravity Math
        vz -= GRAVITY
        pz += vz

        isGrounded = false

        // Update Platforms & Handle Collisions
        for (plat in platforms) {
            if (plat.isMoving) {
                plat.x += plat.moveSpeed
                if (abs(plat.x - plat.originX) > plat.moveRange) {
                    plat.moveSpeed = -plat.moveSpeed
                }
            }

            // Platform Top Collision Check
            val playerBottom = pz
            val prevPlayerBottom = pz - vz

            if (vz <= 0 && prevPlayerBottom >= plat.z + plat.h && playerBottom <= plat.z + plat.h + 2.0) {
                // Check if player footprint lands inside platform boundary
                if (px + playerW / 2 >= plat.x && px - playerW / 2 <= plat.x + plat.w &&
                    py + playerD / 2 >= plat.y && py - playerD / 2 <= plat.y + plat.d) {

                    pz = plat.z + plat.h
                    vz = 0.0
                    isGrounded = true

                    // Carry player along with moving platform
                    if (plat.isMoving) {
                        px += plat.moveSpeed
                    }
                }
            }
        }

        // Score tracking
        val currentScore = (pz / 10).toInt()
        if (currentScore > score) score = currentScore
        if (score > highScore) highScore = score

        // Infinite tower platform generation
        if (lastGeneratedZ - cameraZ < 600.0) {
            generateNextPlatform()
        }

        // Clean up platforms off-screen
        platforms.removeAll { it.z + it.h < cameraZ - 200.0 }

        // Trigger Game Over if fallen off bottom camera edge
        if (pz < cameraZ - 120.0) {
            state = GameState.GAMEOVER
        }
    }

    /**
     * Mathematical 2.5D Isometric Coordinate Transformation Function.
     * Maps 3D World (x, y, z) -> 2D Screen Space (isoX, isoY).
     */
    private fun toScreen(x: Double, y: Double, z: Double): Point2D {
        val isoX = WIDTH / 2.0 + (x - y) * COS_30
        val isoY = HEIGHT * 0.65 + (x + y) * SIN_30 - (z - cameraZ)
        return Point2D(isoX, isoY)
    }

    data class Point2D(val x: Double, val y: Double)

    /**
     * Screen Rendering Loop
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw Background
        val gradient = GradientPaint(0f, 0f, Color(14, 18, 28), 0f, HEIGHT.toFloat(), Color(28, 34, 50))
        g2.paint = gradient
        g2.fillRect(0, 0, WIDTH, HEIGHT)

        // Draw World Objects
        drawIsometricWorld(g2)

        // Draw HUD Overlays
        drawHUD(g2)
    }

    private fun drawIsometricWorld(g2: Graphics2D) {
        // Draw Platforms
        for (plat in platforms) {
            drawIsometricBlock(g2, plat.x, plat.y, plat.z, plat.w, plat.d, plat.h, plat.color)
        }

        // Draw Player (Glowing Gold Isometric Prism)
        val playerColor = Color(255, 215, 0)
        val playerX = px - playerW / 2
        val playerY = py - playerD / 2
        drawIsometricBlock(g2, playerX, playerY, pz, playerW, playerD, playerH, playerColor, isPlayer = true)
    }

    /**
     * Renders a 3D Isometric Prism (Top, Front-Left, Front-Right Faces).
     */
    private fun drawIsometricBlock(
        g2: Graphics2D,
        x: Double, y: Double, z: Double,
        w: Double, d: Double, h: Double,
        baseColor: Color,
        isPlayer: Boolean = false
    ) {
        // Calculate 2D Screen Points for Isometric Vertices
        val p1 = toScreen(x, y, z + h)         // Top Front
        val p2 = toScreen(x + w, y, z + h)     // Top Right
        val p3 = toScreen(x + w, y + d, z + h) // Top Back
        val p4 = toScreen(x, y + d, z + h)     // Top Left

        val p1b = toScreen(x, y, z)            // Bottom Front
        val p2b = toScreen(x + w, y, z)        // Bottom Right
        val p4b = toScreen(x, y + d, z)        // Bottom Left

        // Top Face Polygon
        val topFace = Path2D.Double().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(p4.x, p4.y)
            closePath()
        }

        // Front-Left Face Polygon
        val leftFace = Path2D.Double().apply {
            moveTo(p1.x, p1.y)
            lineTo(p4.x, p4.y)
            lineTo(p4b.x, p4b.y)
            lineTo(p1b.x, p1b.y)
            closePath()
        }

        // Front-Right Face Polygon
        val rightFace = Path2D.Double().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p2b.x, p2b.y)
            lineTo(p1b.x, p1b.y)
            closePath()
        }

        // Dynamic Lighting Color Shading
        val topColor = if (isPlayer) Color(255, 235, 120) else baseColor
        val leftColor = if (isPlayer) Color(220, 170, 0) else baseColor.darker()
        val rightColor = if (isPlayer) Color(170, 120, 0) else baseColor.darker().darker()

        // Render Faces
        g2.color = leftColor
        g2.fill(leftFace)

        g2.color = rightColor
        g2.fill(rightFace)

        g2.color = topColor
        g2.fill(topFace)

        // Draw Outlines
        g2.color = if (isPlayer) Color.WHITE else Color(255, 255, 255, 60)
        g2.stroke = BasicStroke(if (isPlayer) 2f else 1f)
        g2.draw(topFace)
        g2.draw(leftFace)
        g2.draw(rightFace)
    }

    private fun drawHUD(g2: Graphics2D) {
        g2.font = Font("Monospaced", Font.BOLD, 22)

        if (state == GameState.PLAYING) {
            g2.color = Color.WHITE
            g2.drawString("HEIGHT: $score", 25, 40)
            g2.color = Color(0, 230, 180)
            g2.drawString("BEST: $highScore", WIDTH - 170, 40)
        } else {
            drawOverlay(g2)
        }
    }

    private fun drawOverlay(g2: Graphics2D) {
        g2.color = Color(10, 14, 24, 215)
        g2.fillRect(0, 0, WIDTH, HEIGHT)

        g2.color = Color.WHITE
        g2.font = Font("Monospaced", Font.BOLD, 32)

        val title = if (state == GameState.START) "ISOMETRIC TOWER CLIMB" else "FALLEN OFF TOWER!"
        val tw = g2.fontMetrics.stringWidth(title)
        g2.drawString(title, (WIDTH - tw) / 2, HEIGHT / 2 - 50)

        g2.font = Font("Monospaced", Font.PLAIN, 17)
        g2.color = Color(200, 220, 255)

        val sub1 = if (state == GameState.START) "Arrow Keys / WASD = Move  |  SPACE = Jump" else "Final Height: $score  |  Best: $highScore"
        val sw1 = g2.fontMetrics.stringWidth(sub1)
        g2.drawString(sub1, (WIDTH - sw1) / 2, HEIGHT / 2 + 10)

        g2.font = Font("Monospaced", Font.BOLD, 18)
        g2.color = Color(255, 215, 0)
        val sub2 = "Press SPACE to " + (if (state == GameState.START) "Start" else "Restart")
        val sw2 = g2.fontMetrics.stringWidth(sub2)
        g2.drawString(sub2, (WIDTH - sw2) / 2, HEIGHT / 2 + 65)
    }

    // --- Input Listener Implementation ---

    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT, KeyEvent.VK_A -> keyLeft = true
            KeyEvent.VK_RIGHT, KeyEvent.VK_D -> keyRight = true
            KeyEvent.VK_UP, KeyEvent.VK_W -> keyUp = true
            KeyEvent.VK_DOWN, KeyEvent.VK_S -> keyDown = true
            KeyEvent.VK_SPACE -> {
                if (state == GameState.PLAYING && isGrounded) {
                    vz = JUMP_IMPULSE
                    isGrounded = false
                } else if (state != GameState.PLAYING) {
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