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
        val frame = JFrame("Procedural Maze Escape")
        val panel = MazeGamePanel()

        frame.add(panel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Main Game Panel handling Procedural Generation, Logic, and Rendering.
 */
class MazeGamePanel : JPanel(), KeyListener, ActionListener {

    companion object {
        const val MAZE_ROWS = 23 // Must be odd
        const val MAZE_COLS = 23 // Must be odd
        const val CELL_SIZE = 26

        const val PANEL_WIDTH = MAZE_COLS * CELL_SIZE
        const val HUD_HEIGHT = 60
        const val PANEL_HEIGHT = MAZE_ROWS * CELL_SIZE + HUD_HEIGHT

        const val WALL = 1
        const val PATH = 0
    }

    // Grid Array Representation stored in memory
    private val grid = Array(MAZE_ROWS) { IntArray(MAZE_COLS) { WALL } }

    // Player Coordinates
    private var playerR = 1
    private var playerC = 1

    // Exit Coordinates
    private val exitR = MAZE_ROWS - 2
    private val exitC = MAZE_COLS - 2

    // Metrics & Game State
    private var steps = 0
    private var mazeCount = 1
    private var gameWon = false
    private var startTimeMs = 0L
    private var elapsedTimeSec = 0L

    // Repaint & Timer (~60 FPS)
    private val timer = Timer(16, this)

    init {
        preferredSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        background = Color(16, 20, 28)
        isFocusable = true
        addKeyListener(this)

        generateMazeDFS()
        timer.start()
    }

    /**
     * Procedural Maze Generation Algorithm: Depth-First Search (Iterative Backtracker)
     */
    private fun generateMazeDFS() {
        // Step 1: Initialize grid to all WALLS
        for (r in 0 until MAZE_ROWS) {
            for (c in 0 until MAZE_COLS) {
                grid[r][c] = WALL
            }
        }

        val stack = mutableListOf<Pair<Int, Int>>()
        val visited = Array(MAZE_ROWS) { BooleanArray(MAZE_COLS) { false } }

        val startR = 1
        val startC = 1

        grid[startR][startC] = PATH
        visited[startR][startC] = true
        stack.add(Pair(startR, startC))

        // Direction offsets for 2 steps (North, South, East, West)
        val directions = listOf(
            Pair(-2, 0),
            Pair(2, 0),
            Pair(0, 2),
            Pair(0, -2)
        )

        // Step 2: Carve paths using DFS
        while (stack.isNotEmpty()) {
            val current = stack.last()
            val r = current.first
            val c = current.second

            val unvisitedNeighbors = mutableListOf<Pair<Int, Int>>()

            for (dir in directions) {
                val nr = r + dir.first
                val nc = c + dir.second

                if (nr in 1 until MAZE_ROWS - 1 && nc in 1 until MAZE_COLS - 1) {
                    if (!visited[nr][nc]) {
                        unvisitedNeighbors.add(Pair(nr, nc))
                    }
                }
            }

            if (unvisitedNeighbors.isNotEmpty()) {
                val chosen = unvisitedNeighbors[Random.nextInt(unvisitedNeighbors.size)]
                val nr = chosen.first
                val nc = chosen.second

                // Remove the wall node between current cell and chosen cell
                val wallR = r + (nr - r) / 2
                val wallC = c + (nc - c) / 2

                grid[wallR][wallC] = PATH
                grid[nr][nc] = PATH

                visited[nr][nc] = true
                stack.add(chosen)
            } else {
                stack.removeAt(stack.size - 1) // Backtrack
            }
        }

        // Reset game stats
        playerR = 1
        playerC = 1
        steps = 0
        gameWon = false
        startTimeMs = System.currentTimeMillis()
        elapsedTimeSec = 0
    }

    /**
     * Handles movement and collision detection.
     */
    private fun movePlayer(dr: Int, dc: Int) {
        if (gameWon) return

        val newR = playerR + dr
        val newC = playerC + dc

        // Check array bounds and path collision
        if (newR in 0 until MAZE_ROWS && newC in 0 until MAZE_COLS) {
            if (grid[newR][newC] == PATH) {
                playerR = newR
                playerC = newC
                steps++

                // Check Victory Trigger Condition
                if (playerR == exitR && playerC == exitC) {
                    gameWon = true
                }
            }
        }
    }

    override fun actionPerformed(e: ActionEvent?) {
        if (!gameWon && startTimeMs > 0) {
            elapsedTimeSec = (System.currentTimeMillis() - startTimeMs) / 1000
        }
        repaint()
    }

    /**
     * Render Maze Grid, Player Character, and UI Overlays.
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw Maze Array
        for (r in 0 until MAZE_ROWS) {
            for (c in 0 until MAZE_COLS) {
                val x = c * CELL_SIZE
                val y = r * CELL_SIZE + HUD_HEIGHT

                if (grid[r][c] == WALL) {
                    // Wall Nodes
                    g2.color = Color(32, 42, 60)
                    g2.fillRect(x, y, CELL_SIZE, CELL_SIZE)

                    g2.color = Color(22, 28, 42)
                    g2.drawRect(x, y, CELL_SIZE, CELL_SIZE)
                } else {
                    // Open Path Nodes
                    g2.color = Color(12, 16, 24)
                    g2.fillRect(x, y, CELL_SIZE, CELL_SIZE)
                }
            }
        }

        // Draw Exit Coordinate (Pulsing Portal)
        val exitX = exitC * CELL_SIZE
        val exitY = exitR * CELL_SIZE + HUD_HEIGHT
        val pulse = (sin(System.currentTimeMillis() / 180.0) * 3).toInt()

        g2.color = Color(0, 255, 150)
        g2.fillRect(exitX + 4 - pulse / 2, exitY + 4 - pulse / 2, CELL_SIZE - 8 + pulse, CELL_SIZE - 8 + pulse)
        g2.color = Color.WHITE
        g2.drawRect(exitX + 4 - pulse / 2, exitY + 4 - pulse / 2, CELL_SIZE - 8 + pulse, CELL_SIZE - 8 + pulse)

        // Draw Start Position Indicator
        val startX = 1 * CELL_SIZE
        val startY = 1 * CELL_SIZE + HUD_HEIGHT
        g2.color = Color(60, 80, 110, 120)
        g2.fillRect(startX + 6, startY + 6, CELL_SIZE - 12, CELL_SIZE - 12)

        // Draw Player Character (Cyan Glowing Dot)
        val playerX = playerC * CELL_SIZE
        val playerY = playerR * CELL_SIZE + HUD_HEIGHT

        // Outer Glow
        g2.color = Color(0, 200, 255, 80)
        g2.fillOval(playerX + 1, playerY + 1, CELL_SIZE - 2, CELL_SIZE - 2)

        // Player Core Dot
        g2.color = Color(0, 220, 255)
        g2.fillOval(playerX + 5, playerY + 5, CELL_SIZE - 10, CELL_SIZE - 10)
        g2.color = Color.WHITE
        g2.drawOval(playerX + 5, playerY + 5, CELL_SIZE - 10, CELL_SIZE - 10)

        // Render HUD Bar
        g2.color = Color(22, 28, 40)
        g2.fillRect(0, 0, PANEL_WIDTH, HUD_HEIGHT)
        g2.color = Color(0, 200, 255)
        g2.drawLine(0, HUD_HEIGHT - 1, PANEL_WIDTH, HUD_HEIGHT - 1)

        g2.font = Font("Monospaced", Font.BOLD, 15)
        g2.color = Color.WHITE
        g2.drawString("MAZE #$mazeCount", 20, 35)
        g2.drawString("STEPS: $steps", 160, 35)
        g2.drawString("TIME: ${elapsedTimeSec}s", 310, 35)

        g2.font = Font("Monospaced", Font.PLAIN, 12)
        g2.color = Color.LIGHT_GRAY
        g2.drawString("[R] Reset", PANEL_WIDTH - 90, 35)

        // Victory Screen Modal Overlay
        if (gameWon) {
            g2.color = Color(10, 14, 22, 210)
            g2.fillRect(0, HUD_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT - HUD_HEIGHT)

            val boxW = 380
            val boxH = 200
            val boxX = (PANEL_WIDTH - boxW) / 2
            val boxY = HUD_HEIGHT + (PANEL_HEIGHT - HUD_HEIGHT - boxH) / 2

            g2.color = Color(20, 28, 42)
            g2.fillRect(boxX, boxY, boxW, boxH)

            g2.color = Color(0, 255, 150)
            g2.stroke = BasicStroke(2f)
            g2.drawRect(boxX, boxY, boxW, boxH)

            g2.font = Font("Monospaced", Font.BOLD, 26)
            val title = "MAZE ESCAPED!"
            val tw = g2.fontMetrics.stringWidth(title)
            g2.drawString(title, (PANEL_WIDTH - tw) / 2, boxY + 50)

            g2.font = Font("Monospaced", Font.PLAIN, 16)
            g2.color = Color.WHITE
            val info = "Steps: $steps  |  Time: ${elapsedTimeSec}s"
            val iw = g2.fontMetrics.stringWidth(info)
            g2.drawString(info, (PANEL_WIDTH - iw) / 2, boxY + 105)

            g2.font = Font("Monospaced", Font.BOLD, 15)
            g2.color = Color(0, 200, 255)
            val prompt = "Press SPACE for Next Maze"
            val pw = g2.fontMetrics.stringWidth(prompt)
            g2.drawString(prompt, (PANEL_WIDTH - pw) / 2, boxY + 155)
        }
    }

    // Input Controller
    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_UP, KeyEvent.VK_W -> movePlayer(-1, 0)
            KeyEvent.VK_DOWN, KeyEvent.VK_S -> movePlayer(1, 0)
            KeyEvent.VK_LEFT, KeyEvent.VK_A -> movePlayer(0, -1)
            KeyEvent.VK_RIGHT, KeyEvent.VK_D -> movePlayer(0, 1)
            KeyEvent.VK_R -> generateMazeDFS()
            KeyEvent.VK_SPACE -> {
                if (gameWon) {
                    mazeCount++
                    generateMazeDFS()
                }
            }
        }
    }

    override fun keyReleased(e: KeyEvent) {}
    override fun keyTyped(e: KeyEvent) {}
}