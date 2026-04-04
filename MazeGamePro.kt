package mazepro

import java.awt.*
import java.awt.event.*
import java.util.*
import javax.swing.*
import javax.swing.Timer
import kotlin.math.abs

// --- Configuration ---
const val CELL_SIZE = 22
const val GRID_COLS = 41
const val GRID_ROWS = 31

// --- Core Data Models ---
data class Node(val x: Int, val y: Int)

enum class Algorithm { A_STAR, BFS }

class MazeModel {
    val grid = Array(GRID_ROWS) { BooleanArray(GRID_COLS) { false } } // false = empty, true = wall
    var player = Node(1, 1)
    var target = Node(GRID_COLS - 2, GRID_ROWS - 2)

    var exploredNodes = mutableListOf<Node>()
    var shortestPath = mutableListOf<Node>()

    fun clearPaths() {
        exploredNodes.clear()
        shortestPath.clear()
    }

    fun clearBoard() {
        for (y in 0 until GRID_ROWS) {
            for (x in 0 until GRID_COLS) {
                grid[y][x] = false
            }
        }
        clearPaths()
        player = Node(1, 1)
    }

    // Iterative DFS Maze Generation
    fun generatePerfectMaze() {
        clearBoard()
        // Fill with walls
        for (y in 0 until GRID_ROWS) {
            for (x in 0 until GRID_COLS) grid[y][x] = true
        }

        val stack = Stack<Node>()
        val start = Node(1, 1)
        grid[start.y][start.x] = false
        stack.push(start)

        val random = Random()
        val dirs = arrayOf(Node(0, -2), Node(0, 2), Node(-2, 0), Node(2, 0))

        while (stack.isNotEmpty()) {
            val current = stack.peek()
            val neighbors = dirs.map { Node(current.x + it.x, current.y + it.y) }
                .filter { it.x in 1 until GRID_COLS - 1 && it.y in 1 until GRID_ROWS - 1 && grid[it.y][it.x] }

            if (neighbors.isNotEmpty()) {
                val next = neighbors[random.nextInt(neighbors.size)]
                val wallX = current.x + (next.x - current.x) / 2
                val wallY = current.y + (next.y - current.y) / 2
                grid[wallY][wallX] = false
                grid[next.y][next.x] = false
                stack.push(next)
            } else {
                stack.pop()
            }
        }
        player = Node(1, 1)
    }
}

// --- Pathfinding Algorithms ---
object Pathfinders {

    // FIXED: Moved AStarNode here so all functions in Pathfinders can see it
    data class AStarNode(val node: Node, val g: Int, val h: Int, val parent: AStarNode?) : Comparable<AStarNode> {
        val f get() = g + h
        override fun compareTo(other: AStarNode) = this.f.compareTo(other.f)
    }

    // 1. A* (A-Star) Algorithm (Advanced Heuristic-based search)
    fun solveAStar(model: MazeModel): Pair<List<Node>, List<Node>> {
        val openSet = PriorityQueue<AStarNode>()
        val closedSet = mutableSetOf<Node>()
        val explored = mutableListOf<Node>()

        val startNode = AStarNode(model.player, 0, heuristic(model.player, model.target), null)
        openSet.add(startNode)

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()

            if (!closedSet.add(current.node)) continue
            if (current.node != model.player && current.node != model.target) explored.add(current.node)

            if (current.node == model.target) {
                return Pair(explored, buildPath(current))
            }

            getNeighbors(current.node, model).forEach { neighbor ->
                if (neighbor !in closedSet) {
                    val gCost = current.g + 1
                    val hCost = heuristic(neighbor, model.target)
                    openSet.add(AStarNode(neighbor, gCost, hCost, current))
                }
            }
        }
        return Pair(explored, emptyList())
    }

    // 2. Breadth-First Search (Guarantees shortest path, but explores uniformly)
    fun solveBFS(model: MazeModel): Pair<List<Node>, List<Node>> {
        val queue: Queue<Pair<Node, List<Node>>> = LinkedList()
        val visited = mutableSetOf<Node>()
        val explored = mutableListOf<Node>()

        queue.add(Pair(model.player, listOf(model.player)))
        visited.add(model.player)

        while (queue.isNotEmpty()) {
            val (current, path) = queue.poll()

            if (current != model.player && current != model.target) explored.add(current)

            if (current == model.target) return Pair(explored, path)

            getNeighbors(current, model).forEach { neighbor ->
                if (visited.add(neighbor)) {
                    queue.add(Pair(neighbor, path + neighbor))
                }
            }
        }
        return Pair(explored, emptyList())
    }

    private fun heuristic(a: Node, b: Node): Int = abs(a.x - b.x) + abs(a.y - b.y)

    private fun getNeighbors(node: Node, model: MazeModel): List<Node> {
        val dirs = arrayOf(Node(0, -1), Node(0, 1), Node(-1, 0), Node(1, 0))
        return dirs.map { Node(node.x + it.x, node.y + it.y) }
            .filter { it.x in 0 until GRID_COLS && it.y in 0 until GRID_ROWS && !model.grid[it.y][it.x] }
    }

    private fun buildPath(endNode: AStarNode): List<Node> {
        val path = mutableListOf<Node>()
        var curr: AStarNode? = endNode
        while (curr != null) {
            path.add(curr.node)
            curr = curr.parent
        }
        return path.reversed()
    }
}

// --- User Interface & Rendering ---
class MazePanel(private val model: MazeModel) : JPanel() {
    var isAnimating = false
    private var drawingWall = true

    init {
        preferredSize = Dimension(GRID_COLS * CELL_SIZE, GRID_ROWS * CELL_SIZE)
        background = Color(30, 30, 36)
        isFocusable = true

        // Keyboard Movement
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (isAnimating) return
                var nx = model.player.x
                var ny = model.player.y
                when (e.keyCode) {
                    KeyEvent.VK_UP, KeyEvent.VK_W -> ny--
                    KeyEvent.VK_DOWN, KeyEvent.VK_S -> ny++
                    KeyEvent.VK_LEFT, KeyEvent.VK_A -> nx--
                    KeyEvent.VK_RIGHT, KeyEvent.VK_D -> nx++
                }
                if (nx in 0 until GRID_COLS && ny in 0 until GRID_ROWS && !model.grid[ny][nx]) {
                    model.player = Node(nx, ny)
                    model.clearPaths() // Clear old path if user moves
                    repaint()
                }
            }
        })

        // Mouse Drawing (Obstacles)
        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (isAnimating) return
                val col = e.x / CELL_SIZE
                val row = e.y / CELL_SIZE
                if (isValidGridClick(col, row)) {
                    drawingWall = !model.grid[row][col]
                    model.grid[row][col] = drawingWall
                    model.clearPaths()
                    repaint()
                }
            }
            override fun mouseDragged(e: MouseEvent) {
                if (isAnimating) return
                val col = e.x / CELL_SIZE
                val row = e.y / CELL_SIZE
                if (isValidGridClick(col, row)) {
                    model.grid[row][col] = drawingWall
                    model.clearPaths()
                    repaint()
                }
            }
        }
        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
    }

    private fun isValidGridClick(col: Int, row: Int): Boolean {
        if (col !in 0 until GRID_COLS || row !in 0 until GRID_ROWS) return false
        val clicked = Node(col, row)
        return clicked != model.player && clicked != model.target
    }

    fun animateSolution(explored: List<Node>, path: List<Node>) {
        isAnimating = true
        model.clearPaths()

        var exploredIndex = 0
        var pathIndex = 0

        val timer = Timer(5) { t ->
            if (exploredIndex < explored.size) {
                // Animate exploration
                val chunk = Math.min(5, explored.size - exploredIndex) // Speed up by drawing 5 at a time
                for (i in 0 until chunk) {
                    model.exploredNodes.add(explored[exploredIndex++])
                }
                repaint()
            } else if (pathIndex < path.size) {
                // Animate final path
                (t.source as Timer).delay = 20
                model.shortestPath.add(path[pathIndex++])
                repaint()
            } else {
                (t.source as Timer).stop()
                isAnimating = false
            }
        }
        timer.start()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw Grid & Walls
        for (y in 0 until GRID_ROWS) {
            for (x in 0 until GRID_COLS) {
                g2d.color = Color(40, 42, 54)
                g2d.drawRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE)
                if (model.grid[y][x]) {
                    g2d.color = Color(98, 114, 164) // Wall color
                    g2d.fillRect(x * CELL_SIZE + 1, y * CELL_SIZE + 1, CELL_SIZE - 1, CELL_SIZE - 1)
                }
            }
        }

        // Draw Explored Nodes
        g2d.color = Color(139, 233, 253, 150) // Cyan translucent
        model.exploredNodes.forEach {
            g2d.fillRect(it.x * CELL_SIZE + 2, it.y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4)
        }

        // Draw Final Path
        if (model.shortestPath.isNotEmpty()) {
            g2d.color = Color(241, 250, 140) // Yellow
            g2d.stroke = BasicStroke(CELL_SIZE / 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (i in 0 until model.shortestPath.size - 1) {
                val p1 = model.shortestPath[i]
                val p2 = model.shortestPath[i + 1]
                val offset = CELL_SIZE / 2
                g2d.drawLine(p1.x * CELL_SIZE + offset, p1.y * CELL_SIZE + offset, p2.x * CELL_SIZE + offset, p2.y * CELL_SIZE + offset)
            }
        }

        // Draw Target/End
        g2d.color = Color(80, 250, 123) // Green
        g2d.fillRoundRect(model.target.x * CELL_SIZE + 2, model.target.y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4, 8, 8)

        // Draw Player/Start
        g2d.color = Color(255, 85, 85) // Red
        g2d.fillOval(model.player.x * CELL_SIZE + 2, model.player.y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4)
    }
}

// --- Main Application ---
fun main() {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

    SwingUtilities.invokeLater {
        val frame = JFrame("Pro Pathfinding & Maze Visualizer")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.layout = BorderLayout()

        val model = MazeModel()
        model.generatePerfectMaze()

        val mazePanel = MazePanel(model)

        // --- Control Panel ---
        val controlPanel = JPanel().apply {
            background = Color(40, 42, 54)
            layout = FlowLayout(FlowLayout.CENTER, 15, 10)
        }

        val btnGenerate = JButton("Generate Maze").apply {
            addActionListener {
                if (!mazePanel.isAnimating) {
                    model.generatePerfectMaze()
                    mazePanel.repaint()
                    mazePanel.requestFocusInWindow()
                }
            }
        }

        val btnClear = JButton("Clear Board").apply {
            addActionListener {
                if (!mazePanel.isAnimating) {
                    model.clearBoard()
                    mazePanel.repaint()
                    mazePanel.requestFocusInWindow()
                }
            }
        }

        val comboAlgorithm = JComboBox(Algorithm.values()).apply {
            toolTipText = "Select Pathfinding Algorithm"
        }

        val btnSolve = JButton("Solve / Visualize").apply {
            background = Color(80, 250, 123)
            foreground = Color.BLACK
            isOpaque = true
            isBorderPainted = false // FIXED: Changed borderPainted to isBorderPainted
            addActionListener {
                if (mazePanel.isAnimating) return@addActionListener

                val result = when (comboAlgorithm.selectedItem as Algorithm) {
                    Algorithm.A_STAR -> Pathfinders.solveAStar(model)
                    Algorithm.BFS -> Pathfinders.solveBFS(model)
                }

                if (result.second.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "No valid path found!", "Error", JOptionPane.WARNING_MESSAGE)
                } else {
                    mazePanel.animateSolution(result.first, result.second)
                }
                mazePanel.requestFocusInWindow()
            }
        }

        // Add components
        controlPanel.add(btnGenerate)
        controlPanel.add(btnClear)
        controlPanel.add(JLabel("Algorithm:").apply { foreground = Color.WHITE })
        controlPanel.add(comboAlgorithm)
        controlPanel.add(btnSolve)

        val instructions = JLabel("  Use WASD/Arrows to move Player (Red). Mouse Drag to draw Walls.  ").apply {
            foreground = Color.LIGHT_GRAY
            horizontalAlignment = SwingConstants.CENTER
            border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        }

        frame.add(mazePanel, BorderLayout.CENTER)

        val bottomContainer = JPanel(BorderLayout()).apply {
            background = Color(40, 42, 54)
            add(controlPanel, BorderLayout.CENTER)
            add(instructions, BorderLayout.SOUTH)
        }
        frame.add(bottomContainer, BorderLayout.SOUTH)

        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        mazePanel.requestFocusInWindow()
    }
}