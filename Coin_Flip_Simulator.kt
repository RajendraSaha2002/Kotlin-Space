import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.random.Random

fun main() {

    SwingUtilities.invokeLater {

        CoinFlipSimulator()
    }
}

class CoinFlipSimulator : JFrame(), ActionListener {

    private val flipButton = JButton("Flip Coin")

    private val resetButton = JButton("Reset")

    private val resultLabel = JLabel("Result: ")

    private val totalLabel = JLabel("Total Flips: 0")

    private val headsLabel = JLabel("Heads: 0")

    private val tailsLabel = JLabel("Tails: 0")

    private val currentStreakLabel =
        JLabel("Current Streak: 0")

    private val longestStreakLabel =
        JLabel("Longest Streak: 0")

    private var totalFlips = 0

    private var heads = 0

    private var tails = 0

    private var currentStreak = 0

    private var longestStreak = 0

    private var lastResult = ""

    init {

        title = "Coin Flip Simulator"

        setSize(400, 350)

        defaultCloseOperation = EXIT_ON_CLOSE

        layout = GridLayout(8, 1)

        add(resultLabel)

        add(totalLabel)

        add(headsLabel)

        add(tailsLabel)

        add(currentStreakLabel)

        add(longestStreakLabel)

        add(flipButton)

        add(resetButton)

        flipButton.addActionListener(this)

        resetButton.addActionListener(this)

        isVisible = true
    }

    override fun actionPerformed(e: ActionEvent?) {

        if (e?.source == flipButton) {

            flipCoin()

        } else if (e?.source == resetButton) {

            resetGame()
        }
    }

    private fun flipCoin() {

        val result: String

        if (Random.nextBoolean()) {

            result = "Heads"

        } else {

            result = "Tails"
        }

        resultLabel.text = "Result: $result"

        totalFlips++

        if (result == "Heads") {

            heads++

        } else {

            tails++
        }

        // streak calculation
        if (result == lastResult) {

            currentStreak++

        } else {

            currentStreak = 1
        }

        // longest streak update
        if (currentStreak > longestStreak) {

            longestStreak = currentStreak
        }

        lastResult = result

        updateLabels()
    }

    private fun updateLabels() {

        totalLabel.text =
            "Total Flips: $totalFlips"

        headsLabel.text =
            "Heads: $heads"

        tailsLabel.text =
            "Tails: $tails"

        currentStreakLabel.text =
            "Current Streak: $currentStreak"

        longestStreakLabel.text =
            "Longest Streak: $longestStreak"
    }

    private fun resetGame() {

        totalFlips = 0

        heads = 0

        tails = 0

        currentStreak = 0

        longestStreak = 0

        lastResult = ""

        resultLabel.text = "Result: "

        updateLabels()
    }
}