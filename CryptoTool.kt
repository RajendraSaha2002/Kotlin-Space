import java.text.DecimalFormat
import java.util.Scanner

// ============================================================================
// 1. ANSI STYLING & TERMINAL FORMATTING
// ============================================================================
object ConsoleStyle {
    const val RESET = "\u001B[0m"
    const val BOLD = "\u001B[1m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val PURPLE = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"

    fun header(title: String) {
        println("\n${BOLD}${CYAN}=== $title ===${RESET}")
    }

    fun success(msg: String) = println("${GREEN}✔ $msg${RESET}")
    fun warning(msg: String) = println("${YELLOW}⚠ $msg${RESET}")
    fun error(msg: String)   = println("${RED}✖ $msg${RESET}")
    fun info(msg: String)    = println("${CYAN}ℹ $msg${RESET}")
}

// ============================================================================
// 2. CAESAR CIPHER MATHEMATICS ENGINE
// ============================================================================
object CaesarCipher {

    private fun shiftChar(ch: Char, shift: Int): Char {
        val normalizedShift = (shift % 26 + 26) % 26
        return when (ch) {
            in 'A'..'Z' -> 'A' + (ch - 'A' + normalizedShift) % 26
            in 'a'..'z' -> 'a' + (ch - 'a' + normalizedShift) % 26
            else -> ch
        }
    }

    fun encrypt(plainText: String, shift: Int): String {
        return plainText.map { shiftChar(it, shift) }.joinToString("")
    }

    fun decrypt(cipherText: String, shift: Int): String {
        return plainTextShift(cipherText, -shift)
    }

    private fun plainTextShift(text: String, shift: Int) = text.map { shiftChar(it, shift) }.joinToString("")
}

// ============================================================================
// 3. VIGENÈRE CIPHER MATHEMATICS ENGINE
// ============================================================================
object VigenereCipher {

    fun encrypt(plainText: String, key: String): String {
        val cleanKey = key.filter { it.isLetter() }.uppercase()
        if (cleanKey.isEmpty()) return plainText

        var keyIdx = 0
        return plainText.map { ch ->
            if (ch.isLetter()) {
                val shift = cleanKey[keyIdx % cleanKey.length] - 'A'
                keyIdx++
                shiftChar(ch, shift)
            } else {
                ch
            }
        }.joinToString("")
    }

    fun decrypt(cipherText: String, key: String): String {
        val cleanKey = key.filter { it.isLetter() }.uppercase()
        if (cleanKey.isEmpty()) return cipherText

        var keyIdx = 0
        return cipherText.map { ch ->
            if (ch.isLetter()) {
                val shift = cleanKey[keyIdx % cleanKey.length] - 'A'
                keyIdx++
                shiftChar(ch, -shift)
            } else {
                ch
            }
        }.joinToString("")
    }

    private fun shiftChar(ch: Char, shift: Int): Char {
        val normalizedShift = (shift % 26 + 26) % 26
        return when (ch) {
            in 'A'..'Z' -> 'A' + (ch - 'A' + normalizedShift) % 26
            in 'a'..'z' -> 'a' + (ch - 'a' + normalizedShift) % 26
            else -> ch
        }
    }
}

// ============================================================================
// 4. FREQUENCY ANALYSIS & CHI-SQUARED CAESAR CRACKER
// ============================================================================
object FrequencyAnalyzer {

    // Standard English letter frequency distribution (proportions)
    private val ENGLISH_FREQUENCIES = mapOf(
        'A' to 0.0817, 'B' to 0.0129, 'C' to 0.0278, 'D' to 0.0425,
        'E' to 0.1270, 'F' to 0.0223, 'G' to 0.0202, 'H' to 0.0609,
        'I' to 0.0697, 'J' to 0.0015, 'K' to 0.0077, 'L' to 0.0403,
        'M' to 0.0241, 'N' to 0.0675, 'O' to 0.0751, 'P' to 0.0193,
        'Q' to 0.0009, 'R' to 0.0599, 'S' to 0.0633, 'T' to 0.0906,
        'U' to 0.0276, 'V' to 0.0098, 'W' to 0.0236, 'X' to 0.0015,
        'Y' to 0.0197, 'Z' to 0.0007
    )

    data class CrackCandidate(
        val shift: Int,
        val chiSquaredScore: Double,
        val plainText: String
    )

    fun calculateChiSquared(text: String): Double {
        val lettersOnly = text.uppercase().filter { it in 'A'..'Z' }
        val totalCount = lettersOnly.length
        if (totalCount == 0) return Double.MAX_VALUE

        val observedCounts = lettersOnly.groupingBy { it }.eachCount()

        var chiSquared = 0.0
        for (c in 'A'..'Z') {
            val observed = observedCounts.getOrDefault(c, 0).toDouble()
            val expected = totalCount * (ENGLISH_FREQUENCIES[c] ?: 0.0)
            chiSquared += ((observed - expected) * (observed - expected)) / expected
        }
        return chiSquared
    }

    fun crackCaesar(cipherText: String): List<CrackCandidate> {
        val candidates = mutableListOf<CrackCandidate>()

        for (shift in 0..25) {
            val candidateText = CaesarCipher.decrypt(cipherText, shift)
            val score = calculateChiSquared(candidateText)
            candidates.add(CrackCandidate(shift, score, candidateText))
        }

        // Sort candidates by lowest Chi-Squared score (best English match)
        return candidates.sortedBy { it.chiSquaredScore }
    }
}

// ============================================================================
// 5. CLI INTERFACE & MENU CONTROLLER
// ============================================================================
class CryptoApp {

    private val scanner = Scanner(System.`in`)
    private val df = DecimalFormat("#,##0.000")

    fun start() {
        while (true) {
            ConsoleStyle.header("CLASSICAL CRYPTOGRAPHY TOOLKIT")
            println("1. Caesar Cipher (Encrypt / Decrypt)")
            println("2. Vigenère Cipher (Encrypt / Decrypt)")
            println("3. Auto-Crack Caesar Cipher (Frequency Analysis)")
            println("4. Exit")
            print("\n${ConsoleStyle.BOLD}Select an option (1-4): ${ConsoleStyle.RESET}")

            when (scanner.nextLine().trim()) {
                "1" -> handleCaesarMenu()
                "2" -> handleVigenereMenu()
                "3" -> handleCaesarCracker()
                "4" -> {
                    ConsoleStyle.info("Exiting Cryptography Toolkit. Goodbye!")
                    break
                }
                else -> ConsoleStyle.error("Invalid choice! Please select 1-4.")
            }
        }
    }

    private fun handleCaesarMenu() {
        ConsoleStyle.header("CAESAR CIPHER")
        println("1. Encrypt")
        println("2. Decrypt")
        print("\nChoose mode (1-2): ")

        val mode = scanner.nextLine().trim()
        if (mode != "1" && mode != "2") {
            ConsoleStyle.error("Invalid mode!")
            return
        }

        print("Enter input text: ")
        val text = scanner.nextLine()

        print("Enter numeric shift key (e.g. 3, 13, 25): ")
        val shiftInput = scanner.nextLine().toIntOrNull()
        if (shiftInput == null) {
            ConsoleStyle.error("Shift must be a valid integer!")
            return
        }

        val result = if (mode == "1") {
            CaesarCipher.encrypt(text, shiftInput)
        } else {
            CaesarCipher.decrypt(text, shiftInput)
        }

        val actionName = if (mode == "1") "Encrypted" else "Decrypted"
        ConsoleStyle.success("$actionName Output:\n${ConsoleStyle.BOLD}$result${ConsoleStyle.RESET}")
    }

    private fun handleVigenereMenu() {
        ConsoleStyle.header("VIGENÈRE CIPHER")
        println("1. Encrypt")
        println("2. Decrypt")
        print("\nChoose mode (1-2): ")

        val mode = scanner.nextLine().trim()
        if (mode != "1" && mode != "2") {
            ConsoleStyle.error("Invalid mode!")
            return
        }

        print("Enter input text: ")
        val text = scanner.nextLine()

        print("Enter secret alphanumeric key (e.g. 'KEY', 'LEMON'): ")
        val key = scanner.nextLine()

        if (key.none { it.isLetter() }) {
            ConsoleStyle.error("Key must contain at least one letter!")
            return
        }

        val result = if (mode == "1") {
            VigenereCipher.encrypt(text, key)
        } else {
            VigenereCipher.decrypt(text, key)
        }

        val actionName = if (mode == "1") "Encrypted" else "Decrypted"
        ConsoleStyle.success("$actionName Output:\n${ConsoleStyle.BOLD}$result${ConsoleStyle.RESET}")
    }

    private fun handleCaesarCracker() {
        ConsoleStyle.header("AUTOMATED CAESAR FREQUENCY CRACKER")
        print("Enter encrypted ciphertext to break: ")
        val cipherText = scanner.nextLine()

        if (cipherText.none { it.isLetter() }) {
            ConsoleStyle.warning("Input text contains no alphabetic characters to analyze!")
            return
        }

        ConsoleStyle.info("Analyzing letter frequencies and evaluating Chi-Squared (χ²) scores...")

        val candidates = FrequencyAnalyzer.crackCaesar(cipherText)
        val best = candidates.first()

        ConsoleStyle.success("CRACK COMPLETE! Best Candidate Match:")
        println("  • Key Shift: ${ConsoleStyle.BOLD}${best.shift}${ConsoleStyle.RESET}")
        println("  • χ² Score : ${ConsoleStyle.BOLD}${df.format(best.chiSquaredScore)}${ConsoleStyle.RESET} (Lower is better)")
        println("  • Plaintext: ${ConsoleStyle.BOLD}${ConsoleStyle.GREEN}${best.plainText}${ConsoleStyle.RESET}\n")

        print("View top 5 candidate shifts? (y/N): ")
        if (scanner.nextLine().trim().lowercase() == "y") {
            println("\n${ConsoleStyle.CYAN}Top Candidates by Frequency Goodness-of-Fit:${ConsoleStyle.RESET}")
            println(String.format("%-8s | %-12s | %s", "Shift", "χ² Score", "Decrypted Text"))
            println("-".repeat(50))
            candidates.take(5).forEach { candidate ->
                val preview = candidate.plainText.take(30) + if (candidate.plainText.length > 30) "..." else ""
                println(String.format("%-8d | %-12s | %s", candidate.shift, df.format(candidate.chiSquaredScore), preview))
            }
        }
    }
}

// ============================================================================
// 6. MAIN ENTRY POINT
// ============================================================================
fun main() {
    val app = CryptoApp()
    app.start()
}