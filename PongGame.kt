import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import javax.swing.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// --- Configuration Constants ---
const val GAME_WIDTH = 800
const val GAME_HEIGHT = 600
const val PADDLE_WIDTH = 15
const val PADDLE_HEIGHT = 100
const val BALL_SIZE = 16
const val PADDLE_SPEED = 7
const val BALL_INITIAL_SPEED = 8.0
const val MAX_BOUNCE_ANGLE = 5 * PI / 12 // 75 degrees

// --- OOP Models ---

/**
 * Represents a Paddle (Player or AI).
 */
class Paddle(var x: Int, var y: Int, private val isAI: Boolean = false) {
    var score = 0
    private val bounds = Rectangle(x, y, PADDLE_WIDTH, PADDLE_HEIGHT)

    fun update(keys: Set<Int>, ball: Ball) {
        if (isAI) {
            // AI Logic: Follow the ball's Y position, constrained by PADDLE_SPEED
            val center = y + PADDLE_HEIGHT / 2
            if (center < ball.y - 10) y += PADDLE_SPEED - 1 // Slightly slower than player
            else if (center > ball.y + 10) y -= PADDLE_SPEED - 1
        } else {
            // Player Logic: Smooth movement using active keys
            if (keys.contains(KeyEvent.VK_W) || keys.contains(KeyEvent.VK_UP)) y -= PADDLE_SPEED
            if (keys.contains(KeyEvent.VK_S) || keys.contains(KeyEvent.VK_DOWN)) y += PADDLE_SPEED
        }

        // Clamp to screen bounds
        y = y.coerceIn(0, GAME_HEIGHT - PADDLE_HEIGHT)
        bounds.setLocation(x, y)
    }

    fun getBounds(): Rectangle = bounds

    fun draw(g2d: Graphics2D) {
        g2d.color = Color.WHITE
        g2d.fill(Rectangle2D.Double(x.toDouble(), y.toDouble(), PADDLE_WIDTH.toDouble(), PADDLE_HEIGHT.toDouble()))
    }
}

/**
 * Represents the Ball with advanced bouncing physics.
 */
class Ball(var x: Double, var y: Double) {
    var dx = 0.0
    var dy = 0.0
    private var speed = BALL_INITIAL_SPEED

    init {
        reset(1)
    }

    fun update(leftPaddle: Paddle, rightPaddle: Paddle) {
        x += dx
        y += dy

        // Top and Bottom Wall Collisions
        if (y <= 0) {
            y = 0.0
            dy = -dy
        } else if (y >= GAME_HEIGHT - BALL_SIZE) {
            y = (GAME_HEIGHT - BALL_SIZE).toDouble()
            dy = -dy
        }

        // Paddle Collisions
        val ballBounds = Rectangle(x.toInt(), y.toInt(), BALL_SIZE, BALL_SIZE)

        if (ballBounds.intersects(leftPaddle.getBounds())) {
            handlePaddleHit(leftPaddle, 1)
        } else if (ballBounds.intersects(rightPaddle.getBounds())) {
            handlePaddleHit(rightPaddle, -1)
        }
    }

    private fun handlePaddleHit(paddle: Paddle, directionX: Int) {
        // Calculate where the ball hit the paddle (normalized between -1 and 1)
        val paddleCenterY = paddle.y + PADDLE_HEIGHT / 2.0
        val ballCenterY = y + BALL_SIZE / 2.0
        val intersectY = paddleCenterY - ballCenterY
        val normalizedIntersect = intersectY / (PADDLE_HEIGHT / 2.0)

        // Calculate bounce angle (hitting edges gives steeper angles)
        val bounceAngle = normalizedIntersect * MAX_BOUNCE_ANGLE

        // Increase speed slightly on every hit
        speed = (speed + 0.5).coerceAtMost(18.0)

        // Apply new velocities based on the bounce angle
        dx = speed * cos(bounceAngle) * directionX
        dy = speed * -sin(bounceAngle)

        // Prevent getting stuck inside the paddle
        if (directionX == 1) {
            x = (paddle.x + PADDLE_WIDTH).toDouble()
        } else {
            x = (paddle.x - BALL_SIZE).toDouble()
        }
    }

    fun reset(serveDirectionX: Int) {
        x = (GAME_WIDTH / 2 - BALL_SIZE / 2).toDouble()
        y = (GAME_HEIGHT / 2 - BALL_SIZE / 2).toDouble()
        speed = BALL_INITIAL_SPEED

        // Serve straight with a slight random vertical angle
        val randomAngle = (Math.random() * (PI / 4)) - (PI / 8)
        dx = speed * cos(randomAngle) * serveDirectionX
        dy = speed * sin(randomAngle)
    }

    fun draw(g2d: Graphics2D) {
        g2d.color = Color(255, 69, 58) // Modern red/orange tint
        g2d.fill(Ellipse2D.Double(x, y, BALL_SIZE.toDouble(), BALL_SIZE.toDouble()))
    }
}

// --- Main Game Panel ---

class PongPanel : JPanel() {
    private val pressedKeys = mutableSetOf<Int>()

    // Instantiate OOP Entities
    private val leftPaddle = Paddle(30, GAME_HEIGHT / 2 - PADDLE_HEIGHT / 2, isAI = false)
    private val rightPaddle = Paddle(GAME_WIDTH - 30 - PADDLE_WIDTH, GAME_HEIGHT / 2 - PADDLE_HEIGHT / 2, isAI = true)
    private val ball = Ball((GAME_WIDTH / 2).toDouble(), (GAME_HEIGHT / 2).toDouble())

    private var state = State.MENU
    enum class State { MENU, PLAYING }

    init {
        preferredSize = Dimension(GAME_WIDTH, GAME_HEIGHT)
        background = Color(28, 28, 30) // Dark modern background
        isFocusable = true

        // Smooth key tracking
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (state == State.MENU && e.keyCode == KeyEvent.VK_SPACE) {
                    state = State.PLAYING
                }
                pressedKeys.add(e.keyCode)
            }

            override fun keyReleased(e: KeyEvent) {
                pressedKeys.remove(e.keyCode)
            }
        })

        // Game Loop (60 FPS)
        Timer(1000 / 60) {
            if (state == State.PLAYING) {
                updateGame()
            }
            repaint()
        }.start()
    }

    private fun updateGame() {
        leftPaddle.update(pressedKeys, ball)
        rightPaddle.update(pressedKeys, ball)
        ball.update(leftPaddle, rightPaddle)

        // Check for scoring
        if (ball.x < 0) {
            rightPaddle.score++
            ball.reset(1) // Serve to left
        } else if (ball.x > GAME_WIDTH) {
            leftPaddle.score++
            ball.reset(-1) // Serve to right
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        if (state == State.MENU) {
            drawMenu(g2d)
        } else {
            drawCourt(g2d)
            leftPaddle.draw(g2d)
            rightPaddle.draw(g2d)
            ball.draw(g2d)
            drawScore(g2d)
        }
    }

    private fun drawCourt(g2d: Graphics2D) {
        // Draw dashed center line
        val stroke = BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(15f, 15f), 0f)
        g2d.stroke = stroke
        g2d.color = Color(80, 80, 90)
        g2d.drawLine(GAME_WIDTH / 2, 0, GAME_WIDTH / 2, GAME_HEIGHT)
    }

    private fun drawScore(g2d: Graphics2D) {
        g2d.font = Font("Consolas", Font.BOLD, 72)
        g2d.color = Color(255, 255, 255, 100)

        val leftScoreStr = leftPaddle.score.toString()
        val rightScoreStr = rightPaddle.score.toString()

        val fm = g2d.fontMetrics
        g2d.drawString(leftScoreStr, GAME_WIDTH / 2 - fm.stringWidth(leftScoreStr) - 50, 80)
        g2d.drawString(rightScoreStr, GAME_WIDTH / 2 + 50, 80)
    }

    private fun drawMenu(g2d: Graphics2D) {
        g2d.color = Color.WHITE

        g2d.font = Font("Segoe UI", Font.BOLD, 72)
        val title = "PONG"
        val titleWidth = g2d.fontMetrics.stringWidth(title)
        g2d.drawString(title, (GAME_WIDTH - titleWidth) / 2, GAME_HEIGHT / 2 - 50)

        g2d.font = Font("Segoe UI", Font.PLAIN, 24)
        g2d.color = Color.LIGHT_GRAY
        val subtitle = "Press SPACE to Start"
        val subWidth = g2d.fontMetrics.stringWidth(subtitle)
        g2d.drawString(subtitle, (GAME_WIDTH - subWidth) / 2, GAME_HEIGHT / 2 + 20)

        val controls = "W/S or UP/DOWN to move"
        val conWidth = g2d.fontMetrics.stringWidth(controls)
        g2d.drawString(controls, (GAME_WIDTH - conWidth) / 2, GAME_HEIGHT / 2 + 60)
    }
}

// --- Main Application Entry Point ---
fun main() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Exception) {
        e.printStackTrace()
    }

    SwingUtilities.invokeLater {
        JFrame("Kotlin Pro Pong").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            isResizable = false

            add(PongPanel())
            pack()

            setLocationRelativeTo(null)
            isVisible = true
        }
    }
}