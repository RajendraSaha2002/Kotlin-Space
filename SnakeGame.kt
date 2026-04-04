import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.LinkedList
import javax.swing.*
import kotlin.random.Random

// --- Enums & Data ---
enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameState { MENU, PLAYING, PAUSED, GAME_OVER }
data class Point(var x: Int, var y: Int)

/**
 * Advanced Snake Game Panel handling rendering and logic.
 */
class SnakeGamePanel : JPanel(), ActionListener {

    // --- Game Configuration ---
    private val tileSize = 25
    private val gridWidth = 32
    private val gridHeight = 24
    private val screenWidth = gridWidth * tileSize
    private val screenHeight = gridHeight * tileSize
    private val delay = 90 // Game speed (ms per frame)

    // --- Game State Variables ---
    private val snake = LinkedList<Point>()
    private var food = Point(0, 0)

    private var currentDir = Direction.RIGHT
    private var nextDir = Direction.RIGHT // Input buffer to prevent self-collision on rapid inputs

    private var state = GameState.MENU
    private var score = 0
    private var highScore = 0

    private val timer = Timer(delay, this)

    // --- Colors & Fonts ---
    private val colorBg = Color(28, 28, 30)
    private val colorGrid = Color(40, 40, 45)
    private val colorHead = Color(76, 217, 100)
    private val colorBody = Color(52, 199, 89)
    private val colorFood = Color(255, 59, 48)
    private val fontMain = Font("Segoe UI", Font.BOLD, 24)
    private val fontTitle = Font("Segoe UI", Font.BOLD, 48)

    init {
        preferredSize = Dimension(screenWidth, screenHeight)
        background = colorBg
        isFocusable = true

        // Handle Keyboard Inputs
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                handleKeyPress(e.keyCode)
            }
        })
    }

    private fun startGame() {
        snake.clear()
        // Initial snake size of 3
        snake.add(Point(gridWidth / 2, gridHeight / 2))
        snake.add(Point(gridWidth / 2 - 1, gridHeight / 2))
        snake.add(Point(gridWidth / 2 - 2, gridHeight / 2))

        currentDir = Direction.RIGHT
        nextDir = Direction.RIGHT
        score = 0
        state = GameState.PLAYING

        spawnFood()
        timer.start()
    }

    private fun spawnFood() {
        var valid = false
        while (!valid) {
            val fx = Random.nextInt(gridWidth)
            val fy = Random.nextInt(gridHeight)
            food = Point(fx, fy)
            // Ensure food doesn't spawn on the snake
            valid = snake.none { it.x == fx && it.y == fy }
        }
    }

    override fun actionPerformed(e: ActionEvent?) {
        if (state == GameState.PLAYING) {
            move()
            checkCollision()
        }
        repaint()
    }

    private fun move() {
        currentDir = nextDir
        val head = snake.first

        val newHead = when (currentDir) {
            Direction.UP -> Point(head.x, head.y - 1)
            Direction.DOWN -> Point(head.x, head.y + 1)
            Direction.LEFT -> Point(head.x - 1, head.y)
            Direction.RIGHT -> Point(head.x + 1, head.y)
        }

        snake.addFirst(newHead)

        // Check food collision
        if (newHead.x == food.x && newHead.y == food.y) {
            score++
            spawnFood()
        } else {
            // Remove tail if no food eaten
            snake.removeLast()
        }
    }

    private fun checkCollision() {
        val head = snake.first

        // 1. Wall Collision
        if (head.x < 0 || head.x >= gridWidth || head.y < 0 || head.y >= gridHeight) {
            triggerGameOver()
        }

        // 2. Self Collision
        for (i in 1 until snake.size) {
            if (head.x == snake[i].x && head.y == snake[i].y) {
                triggerGameOver()
            }
        }
    }

    private fun triggerGameOver() {
        state = GameState.GAME_OVER
        if (score > highScore) highScore = score
        timer.stop()
    }

    private fun handleKeyPress(keyCode: Int) {
        when (state) {
            GameState.MENU, GameState.GAME_OVER -> {
                if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) startGame()
            }
            GameState.PLAYING -> {
                when (keyCode) {
                    KeyEvent.VK_UP, KeyEvent.VK_W -> if (currentDir != Direction.DOWN) nextDir = Direction.UP
                    KeyEvent.VK_DOWN, KeyEvent.VK_S -> if (currentDir != Direction.UP) nextDir = Direction.DOWN
                    KeyEvent.VK_LEFT, KeyEvent.VK_A -> if (currentDir != Direction.RIGHT) nextDir = Direction.LEFT
                    KeyEvent.VK_RIGHT, KeyEvent.VK_D -> if (currentDir != Direction.LEFT) nextDir = Direction.RIGHT
                    KeyEvent.VK_ESCAPE, KeyEvent.VK_P -> {
                        state = GameState.PAUSED
                        timer.stop()
                    }
                }
            }
            GameState.PAUSED -> {
                if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_P || keyCode == KeyEvent.VK_SPACE) {
                    state = GameState.PLAYING
                    timer.start()
                }
            }
        }
    }

    // --- Rendering ---
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        drawGrid(g2d)

        when (state) {
            GameState.MENU -> drawCenteredText(g2d, "SNAKE", "Press ENTER to Start", -50)
            GameState.PLAYING, GameState.PAUSED -> {
                drawFood(g2d)
                drawSnake(g2d)
                drawScore(g2d)
                if (state == GameState.PAUSED) {
                    drawOverlay(g2d)
                    drawCenteredText(g2d, "PAUSED", "Press ESC to Resume", -50)
                }
            }
            GameState.GAME_OVER -> {
                drawFood(g2d)
                drawSnake(g2d)
                drawOverlay(g2d)
                drawCenteredText(g2d, "GAME OVER", "Score: $score | High Score: $highScore\nPress ENTER to Restart", -50)
            }
        }
    }

    private fun drawGrid(g2d: Graphics2D) {
        g2d.color = colorGrid
        for (i in 0 until screenWidth step tileSize) {
            g2d.drawLine(i, 0, i, screenHeight)
        }
        for (i in 0 until screenHeight step tileSize) {
            g2d.drawLine(0, i, screenWidth, i)
        }
    }

    private fun drawSnake(g2d: Graphics2D) {
        snake.forEachIndexed { index, point ->
            // Smooth gradient effect from head to tail
            if (index == 0) {
                g2d.color = colorHead
                g2d.fillRoundRect(point.x * tileSize, point.y * tileSize, tileSize, tileSize, 10, 10)
            } else {
                val fade = Math.max(100, 255 - (index * 3))
                g2d.color = Color(colorBody.red, colorBody.green, colorBody.blue, fade)
                g2d.fillRoundRect(point.x * tileSize + 2, point.y * tileSize + 2, tileSize - 4, tileSize - 4, 8, 8)
            }
        }
    }

    private fun drawFood(g2d: Graphics2D) {
        g2d.color = colorFood
        g2d.fillOval(food.x * tileSize + 2, food.y * tileSize + 2, tileSize - 4, tileSize - 4)
    }

    private fun drawScore(g2d: Graphics2D) {
        g2d.color = Color.WHITE
        g2d.font = Font("Segoe UI", Font.BOLD, 18)
        val metrics = g2d.fontMetrics
        val scoreText = "Score: $score"
        g2d.drawString(scoreText, screenWidth - metrics.stringWidth(scoreText) - 15, 25)
    }

    private fun drawOverlay(g2d: Graphics2D) {
        g2d.color = Color(0, 0, 0, 150)
        g2d.fillRect(0, 0, screenWidth, screenHeight)
    }

    private fun drawCenteredText(g2d: Graphics2D, title: String, subtitle: String, yOffset: Int) {
        val metricsTitle = g2d.getFontMetrics(fontTitle)
        val metricsSub = g2d.getFontMetrics(fontMain)

        g2d.color = colorHead
        g2d.font = fontTitle
        val titleX = (screenWidth - metricsTitle.stringWidth(title)) / 2
        val titleY = (screenHeight / 2) + yOffset
        g2d.drawString(title, titleX, titleY)

        g2d.color = Color.LIGHT_GRAY
        g2d.font = fontMain

        // Handle multiline subtitles (for Game Over)
        val lines = subtitle.split("\n")
        var currentY = titleY + 40
        for (line in lines) {
            val subX = (screenWidth - metricsSub.stringWidth(line)) / 2
            g2d.drawString(line, subX, currentY)
            currentY += 30
        }
    }
}

// --- Main Application Entry Point ---
fun main() {
    // Enable platform-specific UI look and feel
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Schedule a job for the event-dispatching thread
    SwingUtilities.invokeLater {
        JFrame("Kotlin Pro Snake").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            isResizable = false

            val gamePanel = SnakeGamePanel()
            add(gamePanel)

            pack() // Sizes the frame so all contents are at preferred sizes
            setLocationRelativeTo(null) // Center on screen
            isVisible = true
        }
    }
}