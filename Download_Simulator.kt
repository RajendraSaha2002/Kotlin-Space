import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.DecimalFormat
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

// ============================================================================
// ANSI ESCAPE CODES FOR CONSOLE ANIMATION & STYLING
// ============================================================================
object ConsoleColors {
    const val RESET = "\u001B[0m"
    const val BOLD = "\u001B[1m"

    // Foreground colors
    const val GREEN = "\u001B[32m"
    const val CYAN = "\u001B[36m"
    const val YELLOW = "\u001B[33m"
    const val RED = "\u001B[31m"
    const val GRAY = "\u001B[90m"
    const val WHITE = "\u001B[37m"

    // Cursor controls
    const val HIDE_CURSOR = "\u001B[?25l"
    const val SHOW_CURSOR = "\u001B[?25h"
    const val CLEAR_LINE = "\u001B[2K"

    fun moveUp(lines: Int) = "\u001B[${lines}A"
}

// ============================================================================
// TASK MODEL & THREAD-SAFE STATE
// ============================================================================
enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED }

class DownloadTask(
    val id: Int,
    val fileName: String,
    val totalBytes: Long
) {
    // Thread-safe volatile state updates across coroutines
    @Volatile var downloadedBytes: Long = 0
    @Volatile var currentSpeedBytesPerSec: Long = 0
    @Volatile var status: DownloadStatus = DownloadStatus.QUEUED
    @Volatile var errorMessage: String? = null

    val progress: Double
        get() = if (totalBytes > 0) downloadedBytes.toDouble() / totalBytes else 0.0

    val isFinished: Boolean
        get() = status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED
}

// ============================================================================
// DOWNLOAD ENGINE (COROUTINE SIMULATOR)
// ============================================================================
class DownloadEngine(
    private val maxConcurrentDownloads: Int = 3
) {
    private val semaphore = Semaphore(maxConcurrentDownloads)

    suspend fun download(task: DownloadTask) = semaphore.withPermit {
        task.status = DownloadStatus.DOWNLOADING
        val chunkSize = 1024 * 128 // 128 KB chunk size
        var lastCalcTime = System.currentTimeMillis()
        var bytesSinceLastCalc = 0L

        try {
            while (task.downloadedBytes < task.totalBytes) {
                // Simulate network latency & packet arrival variance
                val chunkDelay = Random.nextLong(15, 60)
                delay(chunkDelay)

                // Random speed fluctuations
                val dynamicChunk = (chunkSize * Random.nextDouble(0.4, 1.6)).toLong()
                val actualChunk = minOf(dynamicChunk, task.totalBytes - task.downloadedBytes)

                task.downloadedBytes += actualChunk
                bytesSinceLastCalc += actualChunk

                // Update download speed metrics every ~300ms
                val now = System.currentTimeMillis()
                val timeDiff = now - lastCalcTime
                if (timeDiff >= 300) {
                    task.currentSpeedBytesPerSec = (bytesSinceLastCalc * 1000) / timeDiff
                    bytesSinceLastCalc = 0L
                    lastCalcTime = now
                }

                // Simulate rare network failure (1% chance per iteration)
                if (Random.nextDouble() < 0.005) {
                    throw RuntimeException("Network Connection Reset")
                }
            }

            task.status = DownloadStatus.COMPLETED
            task.currentSpeedBytesPerSec = 0
        } catch (e: Exception) {
            task.status = DownloadStatus.FAILED
            task.errorMessage = e.message
            task.currentSpeedBytesPerSec = 0
        }
    }
}

// ============================================================================
// UI RENDERER (CLI CONSOLE ANIMATION)
// ============================================================================
class ConsoleRenderer {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun renderProgressBar(progress: Double, width: Int = 20): String {
        val filled = (progress * width).toInt().coerceIn(0, width)
        val empty = width - filled
        val filledBar = "█".repeat(filled)
        val emptyBar = "░".repeat(empty)
        return "${ConsoleColors.CYAN}$filledBar${ConsoleColors.GRAY}$emptyBar${ConsoleColors.RESET}"
    }

    fun render(tasks: List<DownloadTask>, isFirstRender: Boolean): Int {
        val lines = mutableListOf<String>()

        lines.add("${ConsoleColors.BOLD}${ConsoleColors.CYAN}=== Multi-Threaded CLI Downloader Simulator ===${ConsoleColors.RESET}")
        lines.add("")

        tasks.forEach { task ->
            val bar = renderProgressBar(task.progress)
            val percent = String.format("%3d%%", (task.progress * 100).toInt())
            val sizeInfo = "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"

            val statusStr = when (task.status) {
                DownloadStatus.QUEUED -> "${ConsoleColors.GRAY}[QUEUED]${ConsoleColors.RESET}"
                DownloadStatus.DOWNLOADING -> "${ConsoleColors.YELLOW}[DOWNLOADING]${ConsoleColors.RESET}"
                DownloadStatus.COMPLETED -> "${ConsoleColors.GREEN}[COMPLETED]${ConsoleColors.RESET}"
                DownloadStatus.FAILED -> "${ConsoleColors.RED}[FAILED: ${task.errorMessage}]${ConsoleColors.RESET}"
            }

            val speedStr = if (task.status == DownloadStatus.DOWNLOADING) {
                "${ConsoleColors.WHITE}${formatBytes(task.currentSpeedBytesPerSec)}/s${ConsoleColors.RESET}"
            } else {
                "${ConsoleColors.GRAY}0 B/s${ConsoleColors.RESET}"
            }

            val line = String.format(
                "%-22s %s %s | %-12s | %-18s | %s",
                task.fileName.take(20),
                bar,
                percent,
                speedStr,
                sizeInfo,
                statusStr
            )
            lines.add(line)
        }

        // Aggregate statistics
        val totalDownloaded = tasks.sumOf { it.downloadedBytes }
        val totalBytes = tasks.sumOf { it.totalBytes }
        val overallProgress = if (totalBytes > 0) totalDownloaded.toDouble() / totalBytes else 0.0
        val activeDownloads = tasks.count { it.status == DownloadStatus.DOWNLOADING }
        val completedDownloads = tasks.count { it.status == DownloadStatus.COMPLETED }

        lines.add("")
        lines.add("${ConsoleColors.GRAY}───────────────────────────────────────────────────────────────────────────────────────────${ConsoleColors.RESET}")
        lines.add(
            String.format(
                "${ConsoleColors.BOLD}Active:${ConsoleColors.RESET} %d | ${ConsoleColors.BOLD}Completed:${ConsoleColors.RESET} %d/%d | ${ConsoleColors.BOLD}Total Progress:${ConsoleColors.RESET} %.1f%% (%s / %s)",
                activeDownloads,
                completedDownloads,
                tasks.size,
                overallProgress * 100,
                formatBytes(totalDownloaded),
                formatBytes(totalBytes)
            )
        )

        // Reset cursor to overwrite previous UI block
        if (!isFirstRender) {
            print(ConsoleColors.moveUp(lines.size))
        }

        for (line in lines) {
            print("${ConsoleColors.CLEAR_LINE}\r$line\n")
        }

        return lines.size
    }
}

// ============================================================================
// MAIN APPLICATION ENTRY POINT
// ============================================================================
fun main() = runBlocking {
    // Hide terminal cursor for smooth animation
    print(ConsoleColors.HIDE_CURSOR)

    // Restore cursor automatically on program termination (Ctrl+C or completion)
    Runtime.getRuntime().addShutdownHook(Thread {
        print(ConsoleColors.SHOW_CURSOR + ConsoleColors.RESET)
    })

    // Prepare simulated task workload
    val downloads = listOf(
        DownloadTask(1, "ubuntu-22.04-desktop.iso", 3_650_722_304L),
        DownloadTask(2, "jdk-21_macos-aarch64.dmg", 190_123_400L),
        DownloadTask(3, "large-dataset-v2.tar.gz", 850_500_100L),
        DownloadTask(4, "nvidia-driver-win11.exe", 650_234_000L),
        DownloadTask(5, "linux-kernel-6.6.tar.xz", 135_000_000L),
        DownloadTask(6, "android-studio-2023.dmg", 1_100_450_000L)
    )

    val engine = DownloadEngine(maxConcurrentDownloads = 3)
    val renderer = ConsoleRenderer()

    // 1. Launch UI Rendering Loop Coroutine (updates console ~20 FPS)
    var isFirstRender = true
    val uiJob = launch(Dispatchers.Default) {
        while (isActive) {
            renderer.render(downloads, isFirstRender)
            isFirstRender = false
            delay(50) // Refresh rate ~50ms
        }
    }

    // 2. Launch Download Tasks using Coroutines Dispatchers.IO
    val downloadJobs = downloads.map { task ->
        launch(Dispatchers.IO) {
            engine.download(task)
        }
    }

    // Wait for all download tasks to complete
    downloadJobs.joinAll()

    // Cancel UI render loop and do one final flush render
    uiJob.cancelAndJoin()
    renderer.render(downloads, isFirstRender = false)

    // Clean terminal state
    print(ConsoleColors.SHOW_CURSOR)
    println("\n${ConsoleColors.BOLD}${ConsoleColors.GREEN}✔ All downloads completed/processed!${ConsoleColors.RESET}")
}