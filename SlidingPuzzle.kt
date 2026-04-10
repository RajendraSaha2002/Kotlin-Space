import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.*

// ─────────────────────────────────────────────
//  CONSTANTS  (no single-letter names → avoids KeyEvent.W/H clash)
// ─────────────────────────────────────────────
const val GRID_SIZE   = 4
const val TILE_PX     = 110
const val TILE_GAP    = 6
const val BOARD_PAD   = 20
const val PANEL_W     = GRID_SIZE * TILE_PX + (GRID_SIZE - 1) * TILE_GAP + BOARD_PAD * 2
const val HUD_H       = 90
const val GRID_H      = GRID_SIZE * TILE_PX + (GRID_SIZE - 1) * TILE_GAP + BOARD_PAD * 2
const val PANEL_H     = GRID_H + HUD_H

val GOAL_BOARD = Array(GRID_SIZE) { r ->
    IntArray(GRID_SIZE) { c ->
        if (r == GRID_SIZE - 1 && c == GRID_SIZE - 1) 0
        else r * GRID_SIZE + c + 1
    }
}

// ─────────────────────────────────────────────
//  TILE ANIMATION
// ─────────────────────────────────────────────
data class AnimTile(
    val value: Int,
    var sx: Float, var sy: Float,
    val tx: Float, val ty: Float
) {
    var progress = 0f
    val done get() = progress >= 1f
    fun update() { progress = (progress + 0.18f).coerceAtMost(1f) }
    fun cx() = sx + (tx - sx) * ease(progress)
    fun cy() = sy + (ty - sy) * ease(progress)
    private fun ease(t: Float) = ((1f - cos(t * PI.toFloat())) / 2f)
}

// ─────────────────────────────────────────────
//  IDA* AUTO-SOLVER
// ─────────────────────────────────────────────
data class PuzzleState(
    val board: Array<IntArray>,
    val zr: Int,
    val zc: Int
) {
    fun h(): Int {
        var sum = 0
        for (r in 0 until GRID_SIZE) for (c in 0 until GRID_SIZE) {
            val v = board[r][c]; if (v == 0) continue
            val gr = (v - 1) / GRID_SIZE; val gc = (v - 1) % GRID_SIZE
            sum += abs(r - gr) + abs(c - gc)
        }
        return sum
    }
    fun key() = board.flatMap { it.toList() }.joinToString(",")
    override fun equals(other: Any?) = other is PuzzleState && key() == other.key()
    override fun hashCode() = key().hashCode()
}

val MOVE_DIRS = listOf(
    "U" to (-1 to 0),
    "D" to ( 1 to 0),
    "L" to ( 0 to -1),
    "R" to ( 0 to  1)
)

fun solvePuzzle(initial: Array<IntArray>, zr0: Int, zc0: Int): List<String>? {
    val start = PuzzleState(initial.map { it.clone() }.toTypedArray(), zr0, zc0)
    if (start.h() == 0) return emptyList()
    var threshold = start.h()
    val path = mutableListOf<String>()

    fun search(state: PuzzleState, g: Int, bound: Int, visited: MutableSet<String>): Int {
        val f = g + state.h()
        if (f > bound) return f
        if (state.h() == 0) return -1
        var minT = Int.MAX_VALUE
        visited.add(state.key())
        for ((dir, delta) in MOVE_DIRS) {
            val nr = state.zr + delta.first
            val nc = state.zc + delta.second
            if (nr !in 0 until GRID_SIZE || nc !in 0 until GRID_SIZE) continue
            val nb = state.board.map { it.clone() }.toTypedArray()
            nb[state.zr][state.zc] = nb[nr][nc]; nb[nr][nc] = 0
            val ns = PuzzleState(nb, nr, nc)
            if (ns.key() in visited) continue
            path.add(dir)
            val t = search(ns, g + 1, bound, visited)
            if (t == -1) return -1
            if (t < minT) minT = t
            path.removeLast()
        }
        visited.remove(state.key())
        return minT
    }

    repeat(80) {
        val t = search(start, 0, threshold, mutableSetOf())
        if (t == -1) return path.toList()
        if (t == Int.MAX_VALUE) return null
        threshold = t
    }
    return null
}

// ─────────────────────────────────────────────
//  SHUFFLE  (always solvable — random legal moves)
// ─────────────────────────────────────────────
fun shuffleBoard(): Pair<Array<IntArray>, Pair<Int, Int>> {
    val b = Array(GRID_SIZE) { r ->
        IntArray(GRID_SIZE) { c ->
            if (r == GRID_SIZE - 1 && c == GRID_SIZE - 1) 0
            else r * GRID_SIZE + c + 1
        }
    }
    var zr = GRID_SIZE - 1; var zc = GRID_SIZE - 1
    val rng = java.util.Random()
    repeat(300) {
        val moves = MOVE_DIRS.filter { (_, d) ->
            zr + d.first in 0 until GRID_SIZE && zc + d.second in 0 until GRID_SIZE
        }
        val (_, delta) = moves[rng.nextInt(moves.size)]
        val nr = zr + delta.first; val nc = zc + delta.second
        b[zr][zc] = b[nr][nc]; b[nr][nc] = 0
        zr = nr; zc = nc
    }
    return b to (zr to zc)
}

// ─────────────────────────────────────────────
//  COLOUR  per tile value
// ─────────────────────────────────────────────
fun tileColor(value: Int): Color {
    val pct = value / 15f
    val r = (30  + pct * 160).toInt().coerceIn(0, 255)
    val g = (120 - pct * 40 ).toInt().coerceIn(0, 255)
    val b = (220 - pct * 80 ).toInt().coerceIn(0, 255)
    return Color(r, g, b)
}

// ─────────────────────────────────────────────
//  GAME PANEL
// ─────────────────────────────────────────────
class PuzzlePanel : JPanel(), KeyListener {

    private var board = Array(GRID_SIZE) { r ->
        IntArray(GRID_SIZE) { c ->
            if (r == GRID_SIZE - 1 && c == GRID_SIZE - 1) 0
            else r * GRID_SIZE + c + 1
        }
    }
    private var zr = GRID_SIZE - 1
    private var zc = GRID_SIZE - 1
    private var moveCount = 0
    private var startMs   = System.currentTimeMillis()
    private var elapsed   = 0L
    private var won       = false
    private var solving   = false

    private val animQueue = ArrayDeque<String>()
    private val animTiles = mutableListOf<AnimTile>()
    private var animating = false

    private val backBuf = BufferedImage(PANEL_W, PANEL_H, BufferedImage.TYPE_INT_ARGB)

    init {
        preferredSize = Dimension(PANEL_W, PANEL_H)
        isFocusable   = true
        addKeyListener(this)
        newGame()
        Timer(16) { tick() }.start()
    }

    // ── helpers ──────────────────────────────
    private fun tilePixelX(col: Int) = BOARD_PAD + col * (TILE_PX + TILE_GAP)
    private fun tilePixelY(row: Int) = BOARD_PAD + HUD_H + row * (TILE_PX + TILE_GAP)

    private fun checkWin() = board.contentDeepEquals(GOAL_BOARD)

    // ── new game ─────────────────────────────
    private fun newGame() {
        val (b, z) = shuffleBoard()
        board = b; zr = z.first; zc = z.second
        moveCount = 0
        startMs   = System.currentTimeMillis()
        elapsed   = 0L
        won       = false
        solving   = false
        animQueue.clear(); animTiles.clear(); animating = false
        repaint()
    }

    // ── move one tile ─────────────────────────
    private fun tryMove(dr: Int, dc: Int): Boolean {
        val nr = zr + dr; val nc = zc + dc
        if (nr !in 0 until GRID_SIZE || nc !in 0 until GRID_SIZE) return false
        val fromX = tilePixelX(nc).toFloat(); val fromY = tilePixelY(nr).toFloat()
        val toX   = tilePixelX(zc).toFloat(); val toY   = tilePixelY(zr).toFloat()
        animTiles += AnimTile(board[nr][nc], fromX, fromY, toX, toY)
        board[zr][zc] = board[nr][nc]; board[nr][nc] = 0
        zr = nr; zc = nc
        moveCount++
        return true
    }

    private fun dirToDelta(dir: String): Pair<Int, Int> = when (dir) {
        "U"  -> -1 to  0
        "D"  ->  1 to  0
        "L"  ->  0 to -1
        else ->  0 to  1
    }

    // ── game tick ────────────────────────────
    private fun tick() {
        if (!won && !animating) elapsed = System.currentTimeMillis() - startMs

        if (animQueue.isNotEmpty() && animTiles.isEmpty()) {
            val dir = animQueue.removeFirst()
            val (dr, dc) = dirToDelta(dir)
            tryMove(dr, dc)
            if (checkWin()) { won = true; solving = false }
        }

        animTiles.forEach { it.update() }
        animTiles.removeIf { it.done }
        animating = animTiles.isNotEmpty()

        if (!animating && !won && animQueue.isEmpty() && checkWin()) won = true
        repaint()
    }

    // ── auto-solve ───────────────────────────
    private fun autoSolve() {
        if (won || solving) return
        solving = true; animQueue.clear()
        Thread {
            val copy = board.map { it.clone() }.toTypedArray()
            val sol  = solvePuzzle(copy, zr, zc)
            SwingUtilities.invokeLater {
                if (sol == null) { solving = false; return@invokeLater }
                animQueue.addAll(sol)
            }
        }.start()
    }

    // ── hint ─────────────────────────────────
    private fun showHint() {
        if (won || solving) return
        for ((_, d) in MOVE_DIRS) {
            val nr = zr + d.first; val nc = zc + d.second
            if (nr !in 0 until GRID_SIZE || nc !in 0 until GRID_SIZE) continue
            val v = board[nr][nc]; if (v == 0) continue
            val gr = (v - 1) / GRID_SIZE; val gc = (v - 1) % GRID_SIZE
            if (nr != gr || nc != gc) {
                tryMove(d.first, d.second)
                return
            }
        }
    }

    // ── paint ────────────────────────────────
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = backBuf.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val bg = GradientPaint(0f, 0f, Color(18, 22, 38), PANEL_W.toFloat(), PANEL_H.toFloat(), Color(28, 34, 58))
        g2.paint = bg; g2.fillRect(0, 0, PANEL_W, PANEL_H)

        drawHUD(g2)
        drawGrid(g2)
        if (won) drawWinScreen(g2)
        g2.dispose()
        g.drawImage(backBuf, 0, 0, null)
    }

    private fun drawHUD(g2: Graphics2D) {
        g2.color = Color(30, 38, 65, 200)
        g2.fillRoundRect(BOARD_PAD, 10, PANEL_W - BOARD_PAD * 2, HUD_H - 14, 16, 16)

        val sec = elapsed / 1000
        val mm  = sec / 60; val ss = sec % 60

        g2.font = Font("Monospaced", Font.BOLD, 13)
        g2.color = Color(130, 190, 255)
        g2.drawString("TIME   %02d:%02d".format(mm, ss), BOARD_PAD + 10, 38)
        g2.color = Color(255, 200, 100)
        g2.drawString("MOVES  $moveCount", BOARD_PAD + 10, 60)

        g2.font = Font("Monospaced", Font.PLAIN, 11)
        g2.color = Color(80, 110, 160)
        g2.drawString("WASD / Arrows = Move   SPACE = Auto-Solve   H = Hint   N = New", BOARD_PAD + 2, 78)

        if (solving) {
            g2.font  = Font("Monospaced", Font.BOLD, 12)
            g2.color = Color(100, 255, 160)
            g2.drawString("AUTO-SOLVING…", PANEL_W - 155, 38)
        }
    }

    private fun drawGrid(g2: Graphics2D) {
        val bx = BOARD_PAD - TILE_GAP
        val by = BOARD_PAD + HUD_H - TILE_GAP
        val bw = GRID_SIZE * (TILE_PX + TILE_GAP) + TILE_GAP
        val bh = GRID_SIZE * (TILE_PX + TILE_GAP) + TILE_GAP
        g2.color = Color(14, 18, 32)
        g2.fillRoundRect(bx, by, bw, bh, 14, 14)

        val animValues = animTiles.map { it.value }.toSet()
        for (r in 0 until GRID_SIZE) for (c in 0 until GRID_SIZE) {
            val v = board[r][c]
            if (v == 0 || v in animValues) continue
            drawTile(g2, v, tilePixelX(c).toFloat(), tilePixelY(r).toFloat())
        }
        animTiles.forEach { at -> drawTile(g2, at.value, at.cx(), at.cy()) }

        // empty cell
        val ex = tilePixelX(zc); val ey = tilePixelY(zr)
        g2.color = Color(10, 14, 26)
        g2.fillRoundRect(ex, ey, TILE_PX, TILE_PX, 12, 12)
        g2.color = Color(30, 40, 70)
        g2.drawRoundRect(ex, ey, TILE_PX, TILE_PX, 12, 12)
    }

    private fun drawTile(g2: Graphics2D, value: Int, x: Float, y: Float) {
        val xi = x.toInt(); val yi = y.toInt()
        val base = tileColor(value)

        // shadow
        g2.color = Color(0, 0, 0, 80)
        g2.fillRoundRect(xi + 3, yi + 4, TILE_PX, TILE_PX, 12, 12)

        // body gradient
        g2.paint = GradientPaint(x, y, base.brighter(), x, y + TILE_PX, base.darker().darker())
        g2.fillRoundRect(xi, yi, TILE_PX, TILE_PX, 12, 12)
        g2.paint = null

        // edge highlights
        g2.color = Color(255, 255, 255, 60)
        g2.drawRoundRect(xi + 1, yi + 1, TILE_PX - 2, TILE_PX - 2, 12, 12)
        g2.color = Color(0, 0, 0, 50)
        g2.drawRoundRect(xi, yi, TILE_PX, TILE_PX, 12, 12)

        // number — green if already in correct position
        val inGoal = run {
            val gr = (value - 1) / GRID_SIZE; val gc = (value - 1) % GRID_SIZE
            tilePixelX(gc) == xi && tilePixelY(gr) == yi
        }
        g2.font  = Font("Monospaced", Font.BOLD, 36)
        g2.color = if (inGoal) Color(200, 255, 180) else Color(255, 255, 255)
        val fm   = g2.fontMetrics
        val tx   = xi + (TILE_PX - fm.stringWidth(value.toString())) / 2
        val ty   = yi + (TILE_PX + fm.ascent - fm.descent) / 2 - 2
        g2.drawString(value.toString(), tx, ty)
    }

    private fun drawWinScreen(g2: Graphics2D) {
        g2.color = Color(0, 0, 0, 150); g2.fillRect(0, 0, PANEL_W, PANEL_H)
        val title = "PUZZLE SOLVED!"
        g2.font = Font("Monospaced", Font.BOLD, 44)
        for (b in 6 downTo 1) {
            g2.color = Color(60, 255, 140, 16 * b)
            g2.drawString(title, PANEL_W / 2 - 192 + b, PANEL_H / 2 - 50 + b)
        }
        g2.color = Color(80, 255, 160)
        g2.drawString(title, PANEL_W / 2 - 192, PANEL_H / 2 - 50)

        val sec = elapsed / 1000; val mm = sec / 60; val ss = sec % 60
        g2.font  = Font("Monospaced", Font.PLAIN, 18)
        g2.color = Color.WHITE
        g2.drawString("Moves : $moveCount",           PANEL_W / 2 - 80, PANEL_H / 2 + 10)
        g2.drawString("Time  : %02d:%02d".format(mm, ss), PANEL_W / 2 - 80, PANEL_H / 2 + 36)

        g2.font  = Font("Monospaced", Font.BOLD, 15)
        g2.color = Color(255, 220, 80)
        if ((System.currentTimeMillis() / 500) % 2 == 0L)
            g2.drawString("Press  N  for a new game", PANEL_W / 2 - 142, PANEL_H / 2 + 80)
    }

    // ── keyboard input ───────────────────────
    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_N -> { newGame(); return }
            KeyEvent.VK_SPACE -> { autoSolve(); return }
            KeyEvent.VK_H -> { showHint(); return }
        }
        if (won || animating || solving) return
        val (dr, dc) = when (e.keyCode) {
            KeyEvent.VK_W, KeyEvent.VK_UP    -> -1 to  0
            KeyEvent.VK_S, KeyEvent.VK_DOWN  ->  1 to  0
            KeyEvent.VK_A, KeyEvent.VK_LEFT  ->  0 to -1
            KeyEvent.VK_D, KeyEvent.VK_RIGHT ->  0 to  1
            else -> return
        }
        tryMove(dr, dc)
    }
    override fun keyReleased(e: KeyEvent) {}
    override fun keyTyped(e: KeyEvent)   {}
}

// ─────────────────────────────────────────────
//  ENTRY POINT
// ─────────────────────────────────────────────
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("15-Puzzle")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        val panel = PuzzlePanel()
        frame.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        panel.requestFocusInWindow()
    }
}