import java.awt.*
import java.awt.event.*
import javax.swing.*
import kotlin.random.Random

/**
 * Main Application Entry Point.
 */
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Conway's Game of Life Sandbox")
        val app = GameOfLifeApp()

        frame.add(app)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Main Application Panel containing UI Toolbar and Cellular Automata Grid Canvas.
 */
class GameOfLifeApp : JPanel() {

    companion object {
        const val COLS = 100
        const val ROWS = 60
        const val CELL_SIZE = 10
    }

    // 2D Matrix Grid State
    private var grid = Array(ROWS) { BooleanArray(COLS) }
    private var isRunning = false
    private var generation = 0L

    // GUI Components
    private val gridPanel = GridPanel()
    private val playPauseBtn = JButton("Play")
    private val stepBtn = JButton("Step")
    private val clearBtn = JButton("Clear")
    private val randomBtn = JButton("Randomize")
    private val speedSlider = JSlider(1, 100, 40)
    private val statsLabel = JLabel(" Gen: 0 | Pop: 0 ")
    private val presetCombo = JComboBox(arrayOf("Presets...", "Glider", "Blinker", "Pulsar", "Gosper Gun"))

    // Simulation Loop Timer
    private val timer: Timer

    init {
        layout = BorderLayout()

        // Timer initialization mapped to initial slider position
        val initialDelay = sliderToDelay(speedSlider.value)
        timer = Timer(initialDelay) {
            stepSimulation()
        }

        // --- Top Controls Toolbar ---
        val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 8))
        controlPanel.background = Color(24, 28, 38)

        playPauseBtn.addActionListener { togglePlayPause() }
        stepBtn.addActionListener {
            if (!isRunning) {
                stepSimulation()
            }
        }
        clearBtn.addActionListener {
            isRunning = false
            playPauseBtn.text = "Play"
            timer.stop()
            clearGrid()
        }
        randomBtn.addActionListener {
            randomizeGrid()
        }

        speedSlider.addChangeListener {
            timer.delay = sliderToDelay(speedSlider.value)
        }
        speedSlider.background = Color(24, 28, 38)
        speedSlider.foreground = Color.WHITE

        presetCombo.addActionListener {
            val selected = presetCombo.selectedItem as String
            if (selected != "Presets...") {
                loadPreset(selected)
                presetCombo.selectedIndex = 0
            }
        }

        statsLabel.foreground = Color(0, 230, 180)
        statsLabel.font = Font("Monospaced", Font.BOLD, 14)

        // Remove button focus ring outlines
        val buttons = arrayOf(playPauseBtn, stepBtn, clearBtn, randomBtn)
        for (btn in buttons) {
            btn.isFocusable = false
        }

        controlPanel.add(playPauseBtn)
        controlPanel.add(stepBtn)
        controlPanel.add(clearBtn)
        controlPanel.add(randomBtn)
        controlPanel.add(JLabel("  Speed:").apply { foreground = Color.WHITE })
        controlPanel.add(speedSlider)
        controlPanel.add(presetCombo)
        controlPanel.add(statsLabel)

        add(controlPanel, BorderLayout.NORTH)
        add(gridPanel, BorderLayout.CENTER)

        updateStats()
    }

    /**
     * Converts slider value (1..100) to delay milliseconds (500ms..5ms).
     */
    private fun sliderToDelay(valIn: Int): Int {
        return 505 - (valIn * 5)
    }

    private fun togglePlayPause() {
        isRunning = !isRunning
        if (isRunning) {
            playPauseBtn.text = "Pause"
            timer.start()
        } else {
            playPauseBtn.text = "Play"
            timer.stop()
        }
    }

    private fun clearGrid() {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                grid[r][c] = false
            }
        }
        generation = 0
        updateStats()
        gridPanel.repaint()
    }

    private fun randomizeGrid() {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                grid[r][c] = Random.nextDouble() < 0.22
            }
        }
        generation = 0
        updateStats()
        gridPanel.repaint()
    }

    /**
     * Core Conway Automata Logic Update Step.
     */
    private fun stepSimulation() {
        val nextGrid = Array(ROWS) { BooleanArray(COLS) }

        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val liveNeighbors = countLiveNeighbors(r, c)
                val isAlive = grid[r][c]

                // Standard Conway Survival & Reproduction Rules
                if (isAlive) {
                    nextGrid[r][c] = (liveNeighbors == 2 || liveNeighbors == 3)
                } else {
                    nextGrid[r][c] = (liveNeighbors == 3)
                }
            }
        }

        grid = nextGrid
        generation++
        updateStats()
        gridPanel.repaint()
    }

    /**
     * Counts surrounding live cells using Toroidal (wrapped border) coordinates.
     */
    private fun countLiveNeighbors(r: Int, c: Int): Int {
        var count = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = (r + dr + ROWS) % ROWS
                val nc = (c + dc + COLS) % COLS
                if (grid[nr][nc]) count++
            }
        }
        return count
    }

    private fun countPopulation(): Int {
        var count = 0
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (grid[r][c]) count++
            }
        }
        return count
    }

    private fun updateStats() {
        statsLabel.text = " Gen: $generation | Pop: ${countPopulation()} "
    }

    /**
     * Load built-in cellular automata preset patterns.
     */
    private fun loadPreset(name: String) {
        clearGrid()
        val midR = ROWS / 2
        val midC = COLS / 2

        when (name) {
            "Glider" -> {
                val pattern = arrayOf(Pair(0, 1), Pair(1, 2), Pair(2, 0), Pair(2, 1), Pair(2, 2))
                for ((dr, dc) in pattern) {
                    grid[midR + dr][midC + dc] = true
                }
            }
            "Blinker" -> {
                grid[midR][midC - 1] = true
                grid[midR][midC] = true
                grid[midR][midC + 1] = true
            }
            "Pulsar" -> {
                val pattern = arrayOf(
                    Pair(-2, -4), Pair(-2, -3), Pair(-2, -2), Pair(-2, 2), Pair(-2, 3), Pair(-2, 4),
                    Pair(2, -4), Pair(2, -3), Pair(2, -2), Pair(2, 2), Pair(2, 3), Pair(2, 4),
                    Pair(-4, -2), Pair(-3, -2), Pair(-2, -2), Pair(2, -2), Pair(3, -2), Pair(4, -2),
                    Pair(-4, 2), Pair(-3, 2), Pair(-2, 2), Pair(2, 2), Pair(3, 2), Pair(4, 2),
                    Pair(-4, -7), Pair(-3, -7), Pair(-2, -7), Pair(2, -7), Pair(3, -7), Pair(4, -7),
                    Pair(-4, 7), Pair(-3, 7), Pair(-2, 7), Pair(2, 7), Pair(3, 7), Pair(4, 7),
                    Pair(-7, -4), Pair(-7, -3), Pair(-7, -2), Pair(-7, 2), Pair(-7, 3), Pair(-7, 4),
                    Pair(7, -4), Pair(7, -3), Pair(7, -2), Pair(7, 2), Pair(7, 3), Pair(7, 4)
                )
                for ((dr, dc) in pattern) {
                    val r = midR + dr
                    val c = midC + dc
                    if (r in 0 until ROWS && c in 0 until COLS) {
                        grid[r][c] = true
                    }
                }
            }
            "Gosper Gun" -> {
                val gun = arrayOf(
                    Pair(5,1), Pair(5,2), Pair(6,1), Pair(6,2),
                    Pair(5,11), Pair(6,11), Pair(7,11), Pair(4,12), Pair(8,12), Pair(3,13), Pair(9,13), Pair(3,14), Pair(9,14),
                    Pair(6,15), Pair(4,16), Pair(8,16), Pair(5,17), Pair(6,17), Pair(7,17), Pair(6,18),
                    Pair(3,21), Pair(4,21), Pair(5,21), Pair(3,22), Pair(4,22), Pair(5,22), Pair(2,23), Pair(6,23),
                    Pair(1,25), Pair(2,25), Pair(6,25), Pair(7,25),
                    Pair(3,35), Pair(4,35), Pair(3,36), Pair(4,36)
                )
                for ((r, c) in gun) {
                    val targetR = r + 8
                    val targetC = c + 10
                    if (targetR in 0 until ROWS && targetC in 0 until COLS) {
                        grid[targetR][targetC] = true
                    }
                }
            }
        }
        updateStats()
        gridPanel.repaint()
    }

    /**
     * Custom Panel for rendering cell grid and handling Mouse interactive drawing.
     */
    inner class GridPanel : JPanel() {
        private var drawModeState: Boolean? = null

        init {
            preferredSize = Dimension(COLS * CELL_SIZE, ROWS * CELL_SIZE)
            background = Color(14, 16, 22)

            val mouseHandler = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val c = e.x / CELL_SIZE
                    val r = e.y / CELL_SIZE

                    if (r in 0 until ROWS && c in 0 until COLS) {
                        // Left Click = Draw Alive, Right Click = Erase
                        val stateToSet = SwingUtilities.isLeftMouseButton(e)
                        drawModeState = stateToSet
                        grid[r][c] = stateToSet
                        updateStats()
                        repaint()
                    }
                }

                override fun mouseDragged(e: MouseEvent) {
                    val c = e.x / CELL_SIZE
                    val r = e.y / CELL_SIZE

                    if (r in 0 until ROWS && c in 0 until COLS) {
                        drawModeState?.let { state ->
                            grid[r][c] = state
                            updateStats()
                            repaint()
                        }
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    drawModeState = null
                }
            }

            addMouseListener(mouseHandler)
            addMouseMotionListener(mouseHandler)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D

            // Draw Cells
            for (r in 0 until ROWS) {
                for (c in 0 until COLS) {
                    if (grid[r][c]) {
                        g2.color = Color(0, 230, 180) // Vibrant Neon Green / Cyan Cell
                        g2.fillRect(c * CELL_SIZE, r * CELL_SIZE, CELL_SIZE - 1, CELL_SIZE - 1)
                    } else {
                        g2.color = Color(22, 26, 36) // Grid Background Slot
                        g2.fillRect(c * CELL_SIZE, r * CELL_SIZE, CELL_SIZE - 1, CELL_SIZE - 1)
                    }
                }
            }
        }
    }
}