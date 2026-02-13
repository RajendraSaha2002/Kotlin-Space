import java.awt.*
import java.io.File
import javax.swing.*
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

data class Question(
    val category: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int
)

class QuizApp : JFrame("Advanced Quiz Application") {
    private val questions = mutableListOf(
        Question("Math", "2 + 2 = ?", listOf("3", "4", "5", "6"), 1),
        Question("Math", "5 * 3 = ?", listOf("8", "15", "10", "12"), 1),
        Question("Science", "Water formula is?", listOf("H2O", "CO2", "O2", "NaCl"), 0),
        Question("Science", "Earth is the ____ planet from Sun?", listOf("2nd", "3rd", "4th", "5th"), 1),
        Question("History", "Who discovered India?", listOf("Columbus", "Vasco da Gama", "Cook", "Magellan"), 1),
        Question("History", "First President of India?", listOf("Nehru", "Gandhi", "Rajendra Prasad", "Patel"), 2)
    )

    private var filtered = mutableListOf<Question>()
    private var currentIndex = 0
    private var score = 0
    private var wrong = 0
    private var timerSeconds = 15
    private var timerTask = fixedRateTimer(initialDelay = 1000, period = 1000) {}
    private val userAnswers = mutableListOf<Int?>()

    private val questionLabel = JLabel("", SwingConstants.CENTER)
    private val optionButtons = List(4) { JRadioButton() }
    private val group = ButtonGroup()
    private val nextButton = JButton("Next")
    private val scoreLabel = JLabel("Score: 0", SwingConstants.CENTER)
    private val timerLabel = JLabel("Time: 15", SwingConstants.CENTER)
    private val progressBar = JProgressBar()

    init {
        setSize(650, 450)
        layout = BorderLayout(8, 8)
        defaultCloseOperation = EXIT_ON_CLOSE

        questionLabel.font = Font("Segoe UI", Font.BOLD, 18)
        scoreLabel.font = Font("Segoe UI", Font.PLAIN, 14)
        timerLabel.font = Font("Segoe UI", Font.PLAIN, 14)

        val topPanel = JPanel(BorderLayout())
        topPanel.add(scoreLabel, BorderLayout.WEST)
        topPanel.add(timerLabel, BorderLayout.EAST)
        topPanel.add(progressBar, BorderLayout.SOUTH)

        val optionsPanel = JPanel(GridLayout(4, 1))
        optionButtons.forEach {
            it.font = Font("Segoe UI", Font.PLAIN, 16)
            group.add(it)
            optionsPanel.add(it)
        }

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(questionLabel, BorderLayout.NORTH)
        centerPanel.add(optionsPanel, BorderLayout.CENTER)

        add(topPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        add(nextButton, BorderLayout.SOUTH)

        nextButton.addActionListener { nextQuestion() }

        val categories = questions.map { it.category }.distinct()
        val category = JOptionPane.showInputDialog(
            this, "Select Category", "Category",
            JOptionPane.QUESTION_MESSAGE, null, categories.toTypedArray(), categories.first()
        ) as String?

        filtered = if (category != null) {
            questions.filter { it.category == category }.shuffled().toMutableList()
        } else {
            questions.shuffled().toMutableList()
        }

        progressBar.maximum = filtered.size
        userAnswers.addAll(List(filtered.size) { null })

        showQuestion()
        startTimer()

        isVisible = true
    }

    private fun startTimer() {
        timerTask.cancel()
        timerSeconds = 15
        timerLabel.text = "Time: $timerSeconds"
        timerTask = fixedRateTimer(initialDelay = 1000, period = 1000) {
            SwingUtilities.invokeLater {
                timerSeconds--
                timerLabel.text = "Time: $timerSeconds"
                if (timerSeconds <= 0) {
                    nextQuestion()
                }
            }
        }
    }

    private fun showQuestion() {
        if (currentIndex >= filtered.size) {
            finishQuiz()
            return
        }

        val q = filtered[currentIndex]
        questionLabel.text = "Q${currentIndex + 1}. ${q.text}"

        optionButtons.forEachIndexed { i, btn ->
            btn.text = q.options[i]
            btn.isSelected = false
        }

        progressBar.value = currentIndex
        startTimer()
    }

    private fun nextQuestion() {
        timerTask.cancel()

        val selected = optionButtons.indexOfFirst { it.isSelected }
        userAnswers[currentIndex] = if (selected >= 0) selected else null

        val correct = filtered[currentIndex].correctIndex
        if (selected == correct) {
            score += 1
        } else if (selected != -1) {
            score -= 1 // negative marking
            wrong += 1
        }

        scoreLabel.text = "Score: $score"
        currentIndex++
        group.clearSelection()
        showQuestion()
    }

    private fun finishQuiz() {
        timerTask.cancel()
        val correctCount = filtered.indices.count { userAnswers[it] == filtered[it].correctIndex }
        val result = StringBuilder()
        result.append("Final Score: $score\n")
        result.append("Correct: $correctCount | Wrong: $wrong\n\n")
        result.append("Answer Review:\n")

        filtered.forEachIndexed { i, q ->
            val userAns = userAnswers[i]?.let { q.options[it] } ?: "Not Answered"
            val correctAns = q.options[q.correctIndex]
            result.append("Q${i + 1}: ${q.text}\n")
            result.append("Your Answer: $userAns\n")
            result.append("Correct Answer: $correctAns\n\n")
        }

        val file = File("quiz_result.txt")
        file.writeText(result.toString())

        JOptionPane.showMessageDialog(this, result.toString(), "Quiz Result", JOptionPane.INFORMATION_MESSAGE)
        dispose()
    }
}

fun main() {
    SwingUtilities.invokeLater { QuizApp() }
}