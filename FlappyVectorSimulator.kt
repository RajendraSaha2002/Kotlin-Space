import java.awt.*
import java.awt.event.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import javax.swing.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Main Entry Point.
 */
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Flappy Bird Vector Simulator")
        val panel = FlappyBirdPanel()
        frame.add(panel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Game logic, physics loop, and vector graphics renderer panel.
 */
class FlappyBirdPanel : JPanel(), ActionListener, KeyListener, MouseListener {

    companion object {
        const val WIDTH = 480
        const val HEIGHT = 640
        const val FPS = 60
        const val DELAY = 1000 / FPS

        // Physics Constants
        const val GRAVITY = 0.45
        const val FLAP_IMPULSE = -8.5
        const val PIPE_SPEED = 3.2
        const val PIPE_SPAWN_INTERVAL = 95 // Frame interval
        const val PIPE_WIDTH = 68.0
        const val PIPE_GAP = 150.0
        const val BIRD_RADIUS = 15.0
        const val GROUND_HEIGHT = 80.0
    }

    enum class GameState { START, PLAYING, GAMEOVER }

    // State Management
    private var state = GameState.START

    // Bird Vector Properties
    private var birdX = 110.0
    private var birdY = HEIGHT / 2.0
    private var birdVy = 0.0
    private var birdRotation = 0.0

    // Score Tracking
    private var score = 0
    private var highScore = 0

    // Obstacle Data Structure
    data class PipePair(
        var x: Double,
        val topHeight: Double,
        val bottomY: Double,
        var passed: Boolean = false
    )

    private val pipes = mutableListOf<PipePair>()
    private var frameCounter = 0
    private var groundOffset = 0.0

    // Game Loop Timer (~60 FPS)
    private val timer = Timer(DELAY, this)

    init {
        preferredSize = Dimension(WIDTH, HEIGHT)
        background = Color(16, 20, 30) // Dark Vector Simulator Background
        isFocusable = true
        addKeyListener(this)
        addMouseListener(this)
        timer.start()
    }

    private fun resetGame() {
        birdX = 110.0
        birdY = HEIGHT / 2.0 - 20.0
        birdVy = 0.0
        birdRotation = 0.0
        score = 0
        pipes.clear()
        frameCounter = 0
        state = GameState.PLAYING
        applyFlapImpulse()
    }

    private fun applyFlapImpulse() {
        birdVy = FLAP_IMPULSE
    }

    private fun spawnPipe() {
        val minTop = 60.0
        val maxTop = HEIGHT - GROUND_HEIGHT - PIPE_GAP - 60.0
        val topHeight = Random.nextDouble(minTop, maxTop)
        val bottomY = topHeight + PIPE_GAP

        pipes.add(PipePair(WIDTH.toDouble(), topHeight, bottomY))
    }

    override fun actionPerformed(e: ActionEvent?) {
        updatePhysics()
        repaint()
    }

    /**
     * Active Physics and Movement Update Step
     */
    private fun updatePhysics() {
        // Ground parallax scrolling
        groundOffset = (groundOffset + PIPE_SPEED) % 20.0

        if (state != GameState.PLAYING) return

        frameCounter++

        // Apply downward acceleration (Gravity)
        birdVy += GRAVITY
        birdY += birdVy

        // Dynamic vector rotation angle calculation based on vertical velocity
        birdRotation = (birdVy * 3.2).coerceIn(-30.0, 75.0)

        // Spawn Pipes
        if (frameCounter % PIPE_SPAWN_INTERVAL == 0) {
            spawnPipe()
        }

        // Pipe physics update & score detection
        val iterator = pipes.iterator()
        val birdLeft = birdX - BIRD_RADIUS

        while (iterator.hasNext()) {
            val pipe = iterator.next()
            pipe.x -= PIPE_SPEED

            // Check if bird passed the pipe obstacle
            if (!pipe.passed && pipe.x + PIPE_WIDTH < birdLeft) {
                pipe.passed = true
                score++
                if (score > highScore) {
                    highScore = score
                }
            }

            // Recycle off-screen pipes
            if (pipe.x + PIPE_WIDTH < -30) {
                iterator.remove()
            }
        }

        // Collision Check Loop
        checkCollisions()
    }

    /**
     * Precise Geometric Collision Loop
     */
    private fun checkCollisions() {
        val groundY = HEIGHT - GROUND_HEIGHT

        // Ground or Ceiling Boundary Collision
        if (birdY + BIRD_RADIUS >= groundY || birdY - BIRD_RADIUS <= 0) {
            triggerCrash()
            return
        }

        // Bird bounding geometry
        val birdHitbox = Ellipse2D.Double(
            birdX - BIRD_RADIUS + 2,
            birdY - BIRD_RADIUS + 2,
            (BIRD_RADIUS - 2) * 2,
            (BIRD_RADIUS - 2) * 2
        )

        // Pipe Collision Loops
        for (pipe in pipes) {
            val topPipeRect = Rectangle2D.Double(pipe.x, 0.0, PIPE_WIDTH, pipe.topHeight)
            val bottomPipeRect = Rectangle2D.Double(pipe.x, pipe.bottomY, PIPE_WIDTH, groundY - pipe.bottomY)

            if (topPipeRect.intersects(birdHitbox.bounds2D) || bottomPipeRect.intersects(birdHitbox.bounds2D)) {
                triggerCrash()
                return
            }
        }
    }

    private fun triggerCrash() {
        state = GameState.GAMEOVER
    }

    /**
     * Vector Graphics Rendering Engine
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Background Vector Grid
        drawVectorGrid(g2)

        // Draw Obstacles (Pipes)
        drawVectorPipes(g2)

        // Draw Ground Line
        drawVectorGround(g2)

        // Draw Vector Bird
        drawVectorBird(g2)

        // Draw Heads-Up Display
        drawHUD(g2)
    }

    private fun drawVectorGrid(g2: Graphics2D) {
        g2.color = Color(28, 36, 52)
        val step = 40
        for (x in 0 until WIDTH step step) {
            g2.drawLine(x, 0, x, HEIGHT)
        }
        for (y in 0 until HEIGHT step step) {
            g2.drawLine(0, y, WIDTH, y)
        }
    }

    private fun drawVectorPipes(g2: Graphics2D) {
        val groundY = HEIGHT - GROUND_HEIGHT

        for (pipe in pipes) {
            // Pipe Body Fill
            g2.color = Color(0, 220, 160)
            g2.fill(Rectangle2D.Double(pipe.x, 0.0, PIPE_WIDTH, pipe.topHeight))
            g2.fill(Rectangle2D.Double(pipe.x, pipe.bottomY, PIPE_WIDTH, groundY - pipe.bottomY))

            // Vector Outline
            g2.color = Color.WHITE
            g2.stroke = BasicStroke(2f)
            g2.draw(Rectangle2D.Double(pipe.x, 0.0, PIPE_WIDTH, pipe.topHeight))
            g2.draw(Rectangle2D.Double(pipe.x, pipe.bottomY, PIPE_WIDTH, groundY - pipe.bottomY))

            // Vector Cap Outlines
            val capHeight = 16.0
            val capMargin = 4.0

            g2.color = Color(0, 255, 190)
            g2.fillRect((pipe.x - capMargin).toInt(), (pipe.topHeight - capHeight).toInt(), (PIPE_WIDTH + capMargin * 2).toInt(), capHeight.toInt())
            g2.fillRect((pipe.x - capMargin).toInt(), pipe.bottomY.toInt(), (PIPE_WIDTH + capMargin * 2).toInt(), capHeight.toInt())

            g2.color = Color.WHITE
            g2.drawRect((pipe.x - capMargin).toInt(), (pipe.topHeight - capHeight).toInt(), (PIPE_WIDTH + capMargin * 2).toInt(), capHeight.toInt())
            g2.drawRect((pipe.x - capMargin).toInt(), pipe.bottomY.toInt(), (PIPE_WIDTH + capMargin * 2).toInt(), capHeight.toInt())
        }
    }

    private fun drawVectorGround(g2: Graphics2D) {
        val groundY = HEIGHT - GROUND_HEIGHT

        // Ground Background
        g2.color = Color(20, 26, 38)
        g2.fillRect(0, groundY.toInt(), WIDTH, GROUND_HEIGHT.toInt())

        // Top Vector Boundary Line
        g2.color = Color(0, 230, 180)
        g2.stroke = BasicStroke(3f)
        g2.drawLine(0, groundY.toInt(), WIDTH, groundY.toInt())

        // Scrolling Diagonal Vector Stripes
        g2.color = Color(40, 52, 72)
        g2.stroke = BasicStroke(2f)
        val step = 20
        var x = -groundOffset.toInt()
        while (x < WIDTH + step) {
            g2.drawLine(x, groundY.toInt(), x - 15, HEIGHT)
            x += step
        }
    }

    private fun drawVectorBird(g2: Graphics2D) {
        val originalTransform = g2.transform

        g2.translate(birdX, birdY)
        g2.rotate(Math.toRadians(birdRotation))

        // Vector Bird Body
        g2.color = Color(255, 210, 0) // Neon Yellow
        g2.fillOval(-BIRD_RADIUS.toInt(), -BIRD_RADIUS.toInt(), (BIRD_RADIUS * 2).toInt(), (BIRD_RADIUS * 2).toInt())

        g2.color = Color.WHITE
        g2.stroke = BasicStroke(2f)
        g2.drawOval(-BIRD_RADIUS.toInt(), -BIRD_RADIUS.toInt(), (BIRD_RADIUS * 2).toInt(), (BIRD_RADIUS * 2).toInt())

        // Eye
        g2.color = Color.BLACK
        g2.fillOval(3, -7, 6, 6)

        // Wing Vector
        g2.color = Color(255, 130, 0)
        val wingOffsetY = if (birdVy < 0) -8 else 0
        g2.fillOval(-11, wingOffsetY, 12, 8)
        g2.color = Color.WHITE
        g2.drawOval(-11, wingOffsetY, 12, 8)

        // Beak Vector
        g2.color = Color(255, 60, 60)
        val beakX = intArrayOf(7, 17, 7)
        val beakY = intArrayOf(-3, 1, 7)
        g2.fillPolygon(beakX, beakY, 3)
        g2.color = Color.WHITE
        g2.drawPolygon(beakX, beakY, 3)

        // Draw Velocity Force Vector Indicator (Simulator Touch)
        if (state == GameState.PLAYING) {
            g2.color = Color(255, 80, 80, 200)
            g2.stroke = BasicStroke(2f)
            g2.drawLine(0, 0, 0, (birdVy * 4.5).toInt())
        }

        g2.transform = originalTransform
    }

    private fun drawHUD(g2: Graphics2D) {
        g2.font = Font("Monospaced", Font.BOLD, 28)

        if (state == GameState.PLAYING) {
            g2.color = Color.WHITE
            val sText = "$score"
            val textWidth = g2.fontMetrics.stringWidth(sText)
            g2.drawString(sText, (WIDTH - textWidth) / 2, 70)
        } else if (state == GameState.START) {
            drawOverlay(g2, "FLAPPY VECTOR", "Press SPACE / Click to Flap", "BEST SCORE: $highScore")
        } else if (state == GameState.GAMEOVER) {
            drawOverlay(g2, "SYSTEM CRASH", "SCORE: $score  |  BEST: $highScore", "Press SPACE / Click to Restart")
        }
    }

    private fun drawOverlay(g2: Graphics2D, title: String, sub1: String, sub2: String) {
        val boxWidth = WIDTH - 70
        val boxHeight = 210
        val boxX = 35
        val boxY = HEIGHT / 2 - 105

        // Semi-transparent overlay box
        g2.color = Color(10, 15, 24, 220)
        g2.fillRect(boxX, boxY, boxWidth, boxHeight)

        // Vector Box Border
        g2.color = Color(0, 230, 180)
        g2.stroke = BasicStroke(2f)
        g2.drawRect(boxX, boxY, boxWidth, boxHeight)

        // Title
        g2.color = Color.WHITE
        g2.font = Font("Monospaced", Font.BOLD, 30)
        var tw = g2.fontMetrics.stringWidth(title)
        g2.drawString(title, (WIDTH - tw) / 2, boxY + 55)

        // Subtitle 1
        g2.font = Font("Monospaced", Font.PLAIN, 16)
        g2.color = Color(200, 220, 255)
        tw = g2.fontMetrics.stringWidth(sub1)
        g2.drawString(sub1, (WIDTH - tw) / 2, boxY + 115)

        // Subtitle 2
        g2.font = Font("Monospaced", Font.BOLD, 16)
        g2.color = Color(255, 210, 0)
        tw = g2.fontMetrics.stringWidth(sub2)
        g2.drawString(sub2, (WIDTH - tw) / 2, boxY + 160)
    }

    // --- Input Listener Implementations ---

    private fun handleTrigger() {
        when (state) {
            GameState.START -> resetGame()
            GameState.PLAYING -> applyFlapImpulse()
            GameState.GAMEOVER -> resetGame()
        }
    }

    override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_SPACE) {
            handleTrigger()
        }
    }

    override fun keyReleased(e: KeyEvent) {}
    override fun keyTyped(e: KeyEvent) {}

    override fun mousePressed(e: MouseEvent) {
        handleTrigger()
    }

    override fun mouseClicked(e: MouseEvent) {}
    override fun mouseReleased(e: MouseEvent) {}
    override fun mouseEntered(e: MouseEvent) {}
    override fun mouseExited(e: MouseEvent) {}
}