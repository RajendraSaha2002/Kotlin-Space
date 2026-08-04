import kotlinx.coroutines.*
import kotlin.math.roundToInt
import kotlin.random.Random

// ============================================================================
// 1. ANSI TERMINAL ESCAPE SEQUENCES
// ============================================================================
object ANSI {
    const val HIDE_CURSOR = "\u001B[?25l"
    const val SHOW_CURSOR = "\u001B[?25h"
    const val ALT_BUFFER_ENABLE = "\u001B[?1049h"
    const val ALT_BUFFER_DISABLE = "\u001B[?1049l"
    const val CURSOR_HOME = "\u001B[H"
    const val RESET = "\u001B[0m"

    // 256-Color Matrix Gradient Levels
    const val COLOR_HEAD = "\u001B[38;5;255m\u001B[1m"   // Glowing Bright White
    const val COLOR_GREEN_1 = "\u001B[38;5;46m\u001B[1m" // Neon Bright Green
    const val COLOR_GREEN_2 = "\u001B[38;5;34m"          // Medium Green
    const val COLOR_GREEN_3 = "\u001B[38;5;28m"          // Dark Green
    const val COLOR_GREEN_4 = "\u001B[38;5;22m"          // Deep Faded Green
}

// ============================================================================
// 2. MATRIX STREAM DOMAIN MODEL
// ============================================================================
class MatrixStream(
    val column: Int,
    var headRow: Double,
    var speed: Double,
    var tailLength: Int
) {
    // Katakana + Latin + Numeric Matrix Character Set
    private val charPool = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$#@%&*+-=<>:;ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ".toCharArray()

    // Internal buffer of characters falling together
    private val streamChars = MutableList(40) { getRandomChar() }

    fun getRandomChar(): Char = charPool[Random.nextInt(charPool.size)]

    fun update(height: Int) {
        headRow += speed

        // Randomly mutate internal characters to simulate matrix digital noise
        if (Random.nextDouble() < 0.15) {
            val randomIndex = Random.nextInt(streamChars.size)
            streamChars[randomIndex] = getRandomChar()
        }

        // If entire stream has fallen off the screen bottom, reset back to top
        if (headRow - tailLength > height) {
            reset()
        }
    }

    fun reset() {
        headRow = -Random.nextDouble(2.0, 25.0)
        speed = Random.nextDouble(0.3, 1.1)
        tailLength = Random.nextInt(10, 28)
    }

    fun getCharForOffset(offset: Int): Char {
        val index = (headRow.toInt() - offset).coerceAtLeast(0) % streamChars.size
        return streamChars[index]
    }
}

// ============================================================================
// 3. TERMINAL SCREEN BUFFER & RENDERER
// ============================================================================
class MatrixBuffer(val width: Int, val height: Int) {
    private val charGrid = Array(height) { CharArray(width) { ' ' } }
    private val colorGrid = Array(height) { Array(width) { ANSI.RESET } }

    fun clear() {
        for (r in 0 until height) {
            for (c in 0 until width) {
                charGrid[r][c] = ' '
                colorGrid[r][c] = ANSI.RESET
            }
        }
    }

    fun setCell(row: Int, col: Int, char: Char, color: String) {
        if (row in 0 until height && col in 0 until width) {
            charGrid[row][col] = char
            colorGrid[row][col] = color
        }
    }

    fun flushToString(): String {
        val sb = StringBuilder(width * height * 12)
        sb.append(ANSI.CURSOR_HOME)

        var activeColor = ""
        for (r in 0 until height) {
            for (c in 0 until width) {
                val color = colorGrid[r][c]
                if (color != activeColor) {
                    sb.append(color)
                    activeColor = color
                }
                sb.append(charGrid[r][c])
            }
            if (r < height - 1) sb.append("\n")
        }
        return sb.toString()
    }
}

// ============================================================================
// 4. MAIN ENGINE & COROUTINE RUNTIME
// ============================================================================
fun getTerminalDimensions(): Pair<Int, Int> {
    // Attempt to parse system terminal dimensions or fallback to typical 100x30 default
    val cols = System.getenv("COLUMNS")?.toIntOrNull() ?: 100
    val lines = System.getenv("LINES")?.toIntOrNull() ?: 30
    return Pair(cols, lines)
}

fun main() = runBlocking {
    val (width, height) = getTerminalDimensions()

    // Switch to alternate screen buffer and hide cursor
    print(ANSI.ALT_BUFFER_ENABLE + ANSI.HIDE_CURSOR)

    // Register shutdown hook to clean up terminal buffer when interrupted (Ctrl+C)
    Runtime.getRuntime().addShutdownHook(Thread {
        print(ANSI.RESET + ANSI.SHOW_CURSOR + ANSI.ALT_BUFFER_DISABLE)
    })

    // Spawn falling matrix streams across columns
    val streams = ArrayList<MatrixStream>()
    for (col in 0 until width step 2) { // Spacing of 2 for aesthetic balance
        val stream = MatrixStream(
            column = col,
            headRow = -Random.nextDouble(0.0, 30.0),
            speed = Random.nextDouble(0.3, 1.1),
            tailLength = Random.nextInt(10, 26)
        )
        streams.add(stream)
    }

    val screenBuffer = MatrixBuffer(width, height)
    val frameDelayMs = 33L // ~30 FPS UI refresh rate

    // Launch background rendering coroutine loop
    val renderJob = launch(Dispatchers.Default) {
        while (isActive) {
            screenBuffer.clear()

            // Update stream positions & rasterize onto screen buffer
            for (stream in streams) {
                stream.update(height)

                val headInt = stream.headRow.roundToInt()

                for (i in 0 until stream.tailLength) {
                    val currentRow = headInt - i
                    if (currentRow in 0 until height) {
                        val ch = stream.getCharForOffset(i)

                        // Select color shade based on tail distance
                        val color = when (i) {
                            0 -> ANSI.COLOR_HEAD       // Glowing tip
                            1, 2 -> ANSI.COLOR_GREEN_1 // Bright upper body
                            in 3..6 -> ANSI.COLOR_GREEN_2
                            in 7..12 -> ANSI.COLOR_GREEN_3
                            else -> ANSI.COLOR_GREEN_4  // Faded tail
                        }

                        screenBuffer.setCell(currentRow, stream.column, ch, color)
                    }
                }
            }

            // Flush buffer output directly to terminal stdout
            print(screenBuffer.flushToString())

            delay(frameDelayMs)
        }
    }

    renderJob.join()
}