import java.util.ArrayDeque
import java.util.Random
import java.util.Scanner

// ============================================================================
// TERMINAL ANSI CONTROLLER & STYLING
// ============================================================================

object Terminal {
    const val RESET = "\u001B[0m"
    const val CLEAR_SCREEN = "\u001B[2J"
    const val CURSOR_HOME = "\u001B[H"
    const val HIDE_CURSOR = "\u001B[?25l"
    const val SHOW_CURSOR = "\u001B[?25h"

    // Foreground Colors
    const val CYAN_BOLD = "\u001B[1;36m"
    const val GREEN_BOLD = "\u001B[1;32m"
    const val YELLOW_BOLD = "\u001B[1;33m"
    const val WHITE_BOLD = "\u001B[1;37m"
    const val GRAY_DIM = "\u001B[2;37m"
    const val BLUE_HEADER = "\u001B[1;34m"
    const val MAGENTA_TEXT = "\u001B[1;35m"

    fun hideCursor() {
        print(HIDE_CURSOR)
        System.out.flush()
    }

    fun showCursor() {
        print(SHOW_CURSOR)
        System.out.flush()
    }

    fun clearScreen() {
        print(CLEAR_SCREEN)
        print(CURSOR_HOME)
        System.out.flush()
    }
}

// ============================================================================
// CONWAY'S GAME OF LIFE MATRIX ENGINE
// ============================================================================

class GameOfLifeGrid(
    val rows: Int = 28,
    val cols: Int = 55,
    val wrapEdges: Boolean = true
) {
    // Stores cell age: 0 = dead, 1 = newly born, >1 = mature surviving cell
    private var currentGrid = Array(rows) { IntArray(cols) }
    private var nextGrid = Array(rows) { IntArray(cols) }

    var generation: Long = 0L
        private set

    val population: Int
        get() {
            var count = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (currentGrid[r][c] > 0) count++
                }
            }
            return count
        }

    fun clear() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                currentGrid[r][c] = 0
                nextGrid[r][c] = 0
            }
        }
        generation = 0L
    }

    fun setCell(r: Int, c: Int, alive: Boolean) {
        if (r in 0 until rows && c in 0 until cols) {
            currentGrid[r][c] = if (alive) 1 else 0
        }
    }

    fun getAge(r: Int, c: Int): Int {
        return if (r in 0 until rows && c in 0 until cols) currentGrid[r][c] else 0
    }

    fun randomize(density: Double = 0.25) {
        clear()
        val random = Random()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (random.nextDouble() < density) {
                    currentGrid[r][c] = 1
                }
            }
        }
    }

    private fun countNeighbors(r: Int, c: Int): Int {
        var count = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                var nr = r + dr
                var nc = c + dc

                if (wrapEdges) {
                    nr = (nr + rows) % rows
                    nc = (nc + cols) % cols
                    if (currentGrid[nr][nc] > 0) count++
                } else {
                    if (nr in 0 until rows && nc in 0 until cols && currentGrid[nr][nc] > 0) {
                        count++
                    }
                }
            }
        }
        return count
    }

    fun step() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val neighbors = countNeighbors(r, c)
                val age = currentGrid[r][c]
                val isCurrentlyAlive = age > 0

                if (isCurrentlyAlive) {
                    if (neighbors == 2 || neighbors == 3) {
                        nextGrid[r][c] = age + 1 // Survives & matures
                    } else {
                        nextGrid[r][c] = 0 // Underpopulation or Overpopulation death
                    }
                } else {
                    if (neighbors == 3) {
                        nextGrid[r][c] = 1 // Reproduction birth
                    } else {
                        nextGrid[r][c] = 0
                    }
                }
            }
        }

        // Swap state grids
        val temp = currentGrid
        currentGrid = nextGrid
        nextGrid = temp

        generation++
    }
}

// ============================================================================
// PRESET BIOLOGICAL PATTERNS
// ============================================================================

object Patterns {
    fun stampGlider(grid: GameOfLifeGrid, startR: Int, startC: Int) {
        val pattern = listOf(
            Pair(0, 1), Pair(1, 2), Pair(2, 0), Pair(2, 1), Pair(2, 2)
        )
        for ((dr, dc) in pattern) {
            grid.setCell(startR + dr, startC + dc, true)
        }
    }

    fun stampGosperGliderGun(grid: GameOfLifeGrid, startR: Int, startC: Int) {
        val pattern = listOf(
            Pair(5, 1), Pair(5, 2), Pair(6, 1), Pair(6, 2),
            Pair(5, 11), Pair(6, 11), Pair(7, 11), Pair(4, 12), Pair(8, 12),
            Pair(3, 13), Pair(9, 13), Pair(3, 14), Pair(9, 14), Pair(6, 15),
            Pair(4, 16), Pair(8, 16), Pair(5, 17), Pair(6, 17), Pair(7, 17),
            Pair(6, 18), Pair(3, 21), Pair(4, 21), Pair(5, 21), Pair(3, 22),
            Pair(4, 22), Pair(5, 22), Pair(2, 23), Pair(6, 23), Pair(1, 25),
            Pair(2, 25), Pair(6, 25), Pair(7, 25), Pair(3, 35), Pair(4, 35),
            Pair(3, 36), Pair(4, 36)
        )
        for ((dr, dc) in pattern) {
            grid.setCell(startR + dr, startC + dc, true)
        }
    }

    fun stampPulsar(grid: GameOfLifeGrid, centerR: Int, centerC: Int) {
        val offsets = listOf(2, 3, 4, 8, 9, 10)
        for (i in offsets) {
            grid.setCell(centerR - 6, centerC - 6 + i, true)
            grid.setCell(centerR - 1, centerC - 6 + i, true)
            grid.setCell(centerR + 1, centerC - 6 + i, true)
            grid.setCell(centerR + 6, centerC - 6 + i, true)

            grid.setCell(centerR - 6 + i, centerC - 6, true)
            grid.setCell(centerR - 6 + i, centerC - 1, true)
            grid.setCell(centerR - 6 + i, centerC + 1, true)
            grid.setCell(centerR - 6 + i, centerC + 6, true)
        }
    }

    fun stampLightweightSpaceship(grid: GameOfLifeGrid, startR: Int, startC: Int) {
        val pattern = listOf(
            Pair(0, 1), Pair(0, 4), Pair(1, 0), Pair(2, 0),
            Pair(2, 4), Pair(3, 0), Pair(3, 1), Pair(3, 2), Pair(3, 3)
        )
        for ((dr, dc) in pattern) {
            grid.setCell(startR + dr, startC + dc, true)
        }
    }

    fun stampAcorn(grid: GameOfLifeGrid, startR: Int, startC: Int) {
        val pattern = listOf(
            Pair(0, 1), Pair(1, 3), Pair(2, 0), Pair(2, 1),
            Pair(2, 4), Pair(2, 5), Pair(2, 6)
        )
        for ((dr, dc) in pattern) {
            grid.setCell(startR + dr, startC + dc, true)
        }
    }

    fun stampPentadecathlon(grid: GameOfLifeGrid, startR: Int, startC: Int) {
        val pattern = listOf(
            Pair(1, 0), Pair(1, 1), Pair(0, 2), Pair(2, 2),
            Pair(1, 3), Pair(1, 4), Pair(1, 5), Pair(1, 6),
            Pair(0, 7), Pair(2, 7), Pair(1, 8), Pair(1, 9)
        )
        for ((dr, dc) in pattern) {
            grid.setCell(startR + dr, startC + dc, true)
        }
    }
}

// ============================================================================
// ANIMATION RENDERER & SIMULATION LOOP
// ============================================================================

class SimulationRenderer(private val grid: GameOfLifeGrid) {

    fun render(fps: Double, patternName: String) {
        val sb = StringBuilder()
        sb.append(Terminal.CURSOR_HOME)

        val innerWidth = grid.cols * 2
        val titleText = " CONWAY'S GAME OF LIFE - GENERATION ${grid.generation} "
        val statsText = " Seed: $patternName | Pop: ${grid.population} | Speed: ${String.format("%.1f", fps)} gen/s "

        val borderTop = "╔" + "═".repeat(innerWidth + 2) + "╗\n"
        val borderMiddle = "╠" + "═".repeat(innerWidth + 2) + "╣\n"
        val borderBottom = "╚" + "═".repeat(innerWidth + 2) + "╝\n"

        sb.append(Terminal.BLUE_HEADER).append(borderTop)
        sb.append("║ ").append(Terminal.WHITE_BOLD).append(titleText.padEnd(innerWidth)).append(Terminal.BLUE_HEADER).append(" ║\n")
        sb.append("║ ").append(Terminal.CYAN_BOLD).append(statsText.padEnd(innerWidth)).append(Terminal.BLUE_HEADER).append(" ║\n")
        sb.append(borderMiddle).append(Terminal.RESET)

        for (r in 0 until grid.rows) {
            sb.append(Terminal.BLUE_HEADER).append("║ ").append(Terminal.RESET)
            for (c in 0 until grid.cols) {
                val age = grid.getAge(r, c)
                when {
                    age == 0 -> sb.append(Terminal.GRAY_DIM).append(" ·")
                    age == 1 -> sb.append(Terminal.CYAN_BOLD).append(" █")
                    age in 2..5 -> sb.append(Terminal.GREEN_BOLD).append(" █")
                    else -> sb.append(Terminal.YELLOW_BOLD).append(" █")
                }
            }
            sb.append(Terminal.BLUE_HEADER).append(" ║\n").append(Terminal.RESET)
        }

        sb.append(Terminal.BLUE_HEADER).append(borderBottom).append(Terminal.RESET)
        sb.append(Terminal.MAGENTA_TEXT)
        sb.append(" Running infinite generational loop. Press [CTRL+C] to stop.\n")
        sb.append(Terminal.RESET)

        print(sb.toString())
        System.out.flush()
    }
}

// ============================================================================
// MAIN ENTRY POINT
// ============================================================================

fun main() {
    // Shutdown hook to cleanly restore terminal cursor
    Runtime.getRuntime().addShutdownHook(Thread {
        Terminal.showCursor()
        println("\n${Terminal.RESET}Simulation stopped. Cursor restored.")
    })

    val scanner = Scanner(System.`in`)

    println("==========================================================================")
    println("              CONWAY'S GAME OF LIFE - KOTLIN SIMULATOR                   ")
    println("==========================================================================")
    println("Select an initial seed configuration:")
    println(" 1) Gosper Glider Gun (Infinite glider stream generator)")
    println(" 2) Pulsar (Period-3 Oscillator)")
    println(" 3) Acorn (Long-lived Methuselah seed)")
    println(" 4) Lightweight Spaceship (Moving organism)")
    println(" 5) Pentadecathlon (Period-15 Oscillator)")
    println(" 6) Primordial Soup (25% Random density matrix)")
    println(" 7) Ecosystem Combo (Gun + Pulsar + Spaceships)")
    print("\nEnter choice (1-7) [Default: 1]: ")

    val input = scanner.nextLine().trim()
    val grid = GameOfLifeGrid(rows = 26, cols = 50, wrapEdges = true)
    var patternName = "Gosper Glider Gun"

    when (input) {
        "2" -> {
            patternName = "Pulsar"
            Patterns.stampPulsar(grid, 13, 25)
        }
        "3" -> {
            patternName = "Acorn"
            Patterns.stampAcorn(grid, 12, 20)
        }
        "4" -> {
            patternName = "Spaceship (LWSS)"
            Patterns.stampLightweightSpaceship(grid, 12, 5)
        }
        "5" -> {
            patternName = "Pentadecathlon"
            Patterns.stampPentadecathlon(grid, 12, 20)
        }
        "6" -> {
            patternName = "Random Soup (25%)"
            grid.randomize(0.25)
        }
        "7" -> {
            patternName = "Ecosystem Combo"
            Patterns.stampGosperGliderGun(grid, 1, 1)
            Patterns.stampPulsar(grid, 18, 38)
            Patterns.stampLightweightSpaceship(grid, 20, 5)
        }
        else -> {
            patternName = "Gosper Glider Gun"
            Patterns.stampGosperGliderGun(grid, 2, 2)
        }
    }

    Terminal.clearScreen()
    Terminal.hideCursor()

    val renderer = SimulationRenderer(grid)
    val frameDelayMs = 70L // ~14 FPS animation speed
    var lastTime = System.currentTimeMillis()

    while (true) {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastTime).coerceAtLeast(1)
        val actualFps = 1000.0 / elapsed
        lastTime = now

        renderer.render(actualFps, patternName)
        grid.step()

        try {
            Thread.sleep(frameDelayMs)
        } catch (e: InterruptedException) {
            break
        }
    }
}