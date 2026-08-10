import java.awt.*
import java.awt.event.*
import javax.swing.*
import kotlin.math.*

/**
 * Main application entry point.
 */
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Retro Brick Breaker")
        val gamePanel = BrickBreakerPanel()

        frame.add(gamePanel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Game logic and rendering panel using Java Swing.
 */
class BrickBreakerPanel : JPanel(), KeyListener, MouseMotionListener, MouseListener, ActionListener {

    companion object {
        const val WIDTH = 800
        const val HEIGHT = 600

        const val PADDLE_WIDTH = 110
        const val PADDLE_HEIGHT = 14
        const val BALL_RADIUS = 8

        const val BRICK_ROWS = 5
        const val BRICK_COLS = 10
        const val BRICK_PADDING = 6
        const val BRICK_OFFSET_TOP = 60
        const val BRICK_OFFSET_LEFT = 35

        const val BALL_SPEED = 7.0
        const val PADDLE_SPEED = 9.0
    }

    // Data structure for a single Brick
    data class Brick(
        val rect: Rectangle,
        val color: Color,
        val points: Int,
        var active: Boolean = true
    )

    // Game state
    private var score = 0
    private var lives = 3
    private var gameOver = false
    private var gameWon = false
    private var gameStarted = false

    // Paddle state
    private var paddleX = (WIDTH - PADDLE_WIDTH) / 2.0
    private val paddleY = HEIGHT - 50.0

    // Ball state
    private var ballX = paddleX + PADDLE_WIDTH / 2.0 - BALL_RADIUS
    private var ballY = paddleY - BALL_RADIUS * 2
    private var ballDx = 0.0
    private var ballDy = -BALL_SPEED

    // Control flags
    private var leftPressed = false
    private var rightPressed = false

    // Grid of Bricks
    private val bricks = mutableListOf<Brick>()

    // Game loop timer (~60 FPS)
    private val timer = Timer(16, this)

    init {
        preferredSize = Dimension(WIDTH, HEIGHT)
        background = Color(15, 15, 20) // Dark retro background
        isFocusable = true

        addKeyListener(this)
        addMouseMotionListener(this)
        addMouseListener(this)

        initGame()
        timer.start()
    }

    /**
     * Initializes or resets the game grid and stats.
     */
    private fun initGame() {
        bricks.clear()

        val totalPaddingX = (BRICK_COLS - 1) * BRICK_PADDING
        val availableWidth = WIDTH - (BRICK_OFFSET_LEFT * 2) - totalPaddingX
        val brickWidth = availableWidth / BRICK_COLS
        val brickHeight = 20

        // Retro Vibrant Color Palette
        val colors = arrayOf(
            Color(255, 60, 90),   // Neon Red
            Color(255, 150, 0),   // Bright Orange
            Color(255, 220, 0),   // Electric Yellow
            Color(50, 225, 100),  // Lime Green
            Color(0, 200, 255)    // Cyan
        )
        val rowPoints = arrayOf(50, 40, 30, 20, 10)

        for (row in 0 until BRICK_ROWS) {
            for (col in 0 until BRICK_COLS) {
                val bx = BRICK_OFFSET_LEFT + col * (brickWidth + BRICK_PADDING)
                val by = BRICK_OFFSET_TOP + row * (brickHeight + BRICK_PADDING)

                bricks.add(
                    Brick(
                        rect = Rectangle(bx, by, brickWidth, brickHeight),
                        color = colors[row % colors.size],
                        points = rowPoints[row % rowPoints.size]
                    )
                )
            }
        }

        score = 0
        lives = 3
        gameOver = false
        gameWon = false
        resetBallAndPaddle()
    }

    /**
     * Resets ball and paddle positions when a life is lost or at start.
     */
    private fun resetBallAndPaddle() {
        paddleX = (WIDTH - PADDLE_WIDTH) / 2.0
        ballX = paddleX + PADDLE_WIDTH / 2.0 - BALL_RADIUS
        ballY = paddleY - BALL_RADIUS * 2
        ballDx = 0.0
        ballDy = -BALL_SPEED
        gameStarted = false
    }

    /**
     * Main Game Loop step (action triggered by Swing Timer).
     */
    override fun actionPerformed(e: ActionEvent?) {
        if (!gameOver && !gameWon) {
            update()
        }
        repaint()
    }

    /**
     * Updates physics, movements, and collision logic.
     */
    private fun update() {
        // Handle keyboard paddle movement
        if (leftPressed) {
            paddleX = (paddleX - PADDLE_SPEED).coerceAtLeast(0.0)
        }
        if (rightPressed) {
            paddleX = (paddleX + PADDLE_SPEED).coerceAtMost((WIDTH - PADDLE_WIDTH).toDouble())
        }

        // Stick ball to paddle before launch
        if (!gameStarted) {
            ballX = paddleX + PADDLE_WIDTH / 2.0 - BALL_RADIUS
            ballY = paddleY - BALL_RADIUS * 2
            return
        }

        // Move Ball
        ballX += ballDx
        ballY += ballDy

        val ballDiameter = BALL_RADIUS * 2

        // --- Ball vs Screen Boundary Collisions ---
        if (ballX <= 0) {
            ballX = 0.0
            ballDx = -ballDx
        } else if (ballX + ballDiameter >= WIDTH) {
            ballX = (WIDTH - ballDiameter).toDouble()
            ballDx = -ballDx
        }

        if (ballY <= 0) {
            ballY = 0.0
            ballDy = -ballDy
        } else if (ballY + ballDiameter >= HEIGHT) {
            // Ball falls off screen bottom
            lives--
            if (lives <= 0) {
                gameOver = true
            } else {
                resetBallAndPaddle()
            }
            return
        }

        // --- Ball vs Paddle Collision (With Structural Angle Deflection) ---
        val ballRect = Rectangle(ballX.toInt(), ballY.toInt(), ballDiameter, ballDiameter)
        val paddleRect = Rectangle(paddleX.toInt(), paddleY.toInt(), PADDLE_WIDTH, PADDLE_HEIGHT)

        if (ballRect.intersects(paddleRect) && ballDy > 0) {
            val paddleCenter = paddleX + PADDLE_WIDTH / 2.0
            val ballCenter = ballX + BALL_RADIUS

            // Normalize hit offset between -1.0 (far left) and 1.0 (far right)
            val hitOffset = (ballCenter - paddleCenter) / (PADDLE_WIDTH / 2.0)
            val clampedOffset = hitOffset.coerceIn(-0.9, 0.9)

            // Deflect up to 60 degrees from vertical normal
            val maxBounceAngle = Math.toRadians(60.0)
            val bounceAngle = clampedOffset * maxBounceAngle

            ballDx = BALL_SPEED * sin(bounceAngle)
            ballDy = -BALL_SPEED * cos(bounceAngle)

            // Reposition to prevent sticking
            ballY = paddleY - ballDiameter
        }

        // --- Ball vs Bricks Collisions ---
        var activeBricksLeft = false
        for (brick in bricks) {
            if (!brick.active) continue
            activeBricksLeft = true

            if (ballRect.intersects(brick.rect)) {
                brick.active = false
                score += brick.points

                // Structural directional collision detection
                val prevBallX = ballX - ballDx
                val prevBallY = ballY - ballDy

                val hitLeft = prevBallX + ballDiameter <= brick.rect.x
                val hitRight = prevBallX >= brick.rect.x + brick.rect.width
                val hitTop = prevBallY + ballDiameter <= brick.rect.y
                val hitBottom = prevBallY >= brick.rect.y + brick.rect.height

                if (hitLeft || hitRight) {
                    ballDx = -ballDx
                }
                if (hitTop || hitBottom) {
                    ballDy = -ballDy
                }
                if (!hitLeft && !hitRight && !hitTop && !hitBottom) {
                    ballDy = -ballDy // Corner fallback
                }

                break // Handle single collision per tick
            }
        }

        if (!activeBricksLeft) {
            gameWon = true
        }
    }

    /**
     * Renders game visual elements using standard Java Swing Graphics2D.
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw Bricks
        for (brick in bricks) {
            if (!brick.active) continue

            g2.color = brick.color
            g2.fillRoundRect(brick.rect.x, brick.rect.y, brick.rect.width, brick.rect.height, 5, 5)

            // Subtle highlight border
            g2.color = brick.color.brighter()
            g2.drawRoundRect(brick.rect.x, brick.rect.y, brick.rect.width, brick.rect.height, 5, 5)
        }

        // Draw Paddle
        g2.color = Color(0, 230, 255)
        g2.fillRoundRect(paddleX.toInt(), paddleY.toInt(), PADDLE_WIDTH, PADDLE_HEIGHT, 8, 8)
        g2.color = Color.WHITE
        g2.drawRoundRect(paddleX.toInt(), paddleY.toInt(), PADDLE_WIDTH, PADDLE_HEIGHT, 8, 8)

        // Draw Ball
        g2.color = Color.WHITE
        g2.fillOval(ballX.toInt(), ballY.toInt(), BALL_RADIUS * 2, BALL_RADIUS * 2)

        // Draw HUD (Score & Lives)
        g2.color = Color.WHITE
        g2.font = Font("Monospaced", Font.BOLD, 18)
        g2.drawString("SCORE: $score", 25, 35)

        val livesText = "LIVES: " + "♥ ".repeat(lives.coerceAtLeast(0))
        val livesWidth = g2.fontMetrics.stringWidth(livesText)
        g2.color = Color(255, 80, 100)
        g2.drawString(livesText, WIDTH - 25 - livesWidth, 35)

        // Game Over / Game Start Overlays
        if (gameOver || gameWon || !gameStarted) {
            if (gameOver || gameWon) {
                g2.color = Color(0, 0, 0, 190)
                g2.fillRect(0, 0, WIDTH, HEIGHT)
            }

            g2.color = Color.WHITE
            g2.font = Font("Monospaced", Font.BOLD, 36)

            val titleText = when {
                gameOver -> "GAME OVER"
                gameWon -> "YOU WIN!"
                else -> "PRESS SPACE / CLICK TO LAUNCH"
            }

            val titleWidth = g2.fontMetrics.stringWidth(titleText)
            g2.drawString(titleText, (WIDTH - titleWidth) / 2, HEIGHT / 2 - 10)

            g2.font = Font("Monospaced", Font.PLAIN, 18)
            val subText = when {
                gameOver || gameWon -> "Press 'R' to Restart"
                else -> "Controls: Mouse or Left/Right Arrow Keys"
            }
            val subWidth = g2.fontMetrics.stringWidth(subText)
            g2.color = Color.LIGHT_GRAY
            g2.drawString(subText, (WIDTH - subWidth) / 2, HEIGHT / 2 + 35)
        }
    }

    // --- Input Handling ---

    private fun launchBall() {
        if (!gameStarted && !gameOver && !gameWon) {
            gameStarted = true
            // Launch slightly randomized left/right
            val randomDirection = if (Math.random() > 0.5) 1.0 else -1.0
            ballDx = 2.5 * randomDirection
            ballDy = -sqrt(BALL_SPEED * BALL_SPEED - ballDx * ballDx)
        }
    }

    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT -> leftPressed = true
            KeyEvent.VK_RIGHT -> rightPressed = true
            KeyEvent.VK_SPACE -> launchBall()
            KeyEvent.VK_R -> {
                if (gameOver || gameWon) {
                    initGame()
                }
            }
        }
    }

    override fun keyReleased(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT -> leftPressed = false
            KeyEvent.VK_RIGHT -> rightPressed = false
        }
    }

    override fun keyTyped(e: KeyEvent) {}

    override fun mouseMoved(e: MouseEvent) {
        paddleX = (e.x - PADDLE_WIDTH / 2.0).coerceIn(0.0, (WIDTH - PADDLE_WIDTH).toDouble())
    }

    override fun mouseDragged(e: MouseEvent) {
        mouseMoved(e)
    }

    override fun mousePressed(e: MouseEvent) {
        launchBall()
        if (gameOver || gameWon) {
            initGame()
        }
    }

    override fun mouseClicked(e: MouseEvent) {}
    override fun mouseReleased(e: MouseEvent) {}
    override fun mouseEntered(e: MouseEvent) {}
    override fun mouseExited(e: MouseEvent) {}
}