package memorygameadvanced

import java.awt.*
import java.awt.event.*
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.Timer
import javax.swing.border.EmptyBorder

// --- Configuration & Data ---
val SYMBOLS = listOf(
    "🍎", "🍌", "🍒", "🍇", "🍉", "🍓", "🥑", "🥕", "🌽",
    "🥝", "🍍", "🥥", "🍔", "🍕", "🍩", "🍦", "🍭", "🍪"
)

enum class Difficulty(val rows: Int, val cols: Int, val title: String) {
    EASY(4, 4, "4x4 (Casual)"),
    HARD(6, 6, "6x6 (Expert)")
}

enum class CardState { HIDDEN, FLIPPED, MATCHED }

// --- Animated Card Component ---
class CardButton(val id: Int, val symbol: String, val onClick: (CardButton) -> Unit) : JButton() {
    var cardState = CardState.HIDDEN

    // Animation Variables
    var scaleX = 1.0
    var isAnimating = false
    private var targetState = CardState.HIDDEN

    init {
        font = Font("Segoe UI Emoji", Font.PLAIN, 40)
        isFocusPainted = false
        isBorderPainted = false
        isContentAreaFilled = false
        cursor = Cursor(Cursor.HAND_CURSOR)

        addActionListener {
            if (!isAnimating && cardState == CardState.HIDDEN) {
                onClick(this)
            }
        }
    }

    // Triggers the 3D-style flip animation
    fun flipTo(newState: CardState, onComplete: (() -> Unit)? = null) {
        if (cardState == newState) return
        isAnimating = true
        targetState = newState

        val timer = Timer(15, null)
        var shrinking = true

        timer.addActionListener {
            if (shrinking) {
                scaleX -= 0.15
                if (scaleX <= 0.0) {
                    scaleX = 0.0
                    shrinking = false
                    cardState = targetState // Change visual state at the "edge" of the flip
                }
            } else {
                scaleX += 0.15
                if (scaleX >= 1.0) {
                    scaleX = 1.0
                    isAnimating = false
                    timer.stop()
                    onComplete?.invoke()
                }
            }
            repaint()
        }
        timer.start()
    }

    override fun paintComponent(g: Graphics) {
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Apply scaling for flip animation
        val cx = width / 2.0
        val oldTransform = g2d.transform
        g2d.translate(cx, 0.0)
        g2d.scale(scaleX, 1.0)
        g2d.translate(-cx, 0.0)

        // Draw Card Background
        when (cardState) {
            CardState.HIDDEN -> {
                g2d.color = Color(68, 71, 90) // Dark Blue/Grey
                g2d.fillRoundRect(2, 2, width - 4, height - 4, 15, 15)
                g2d.color = Color(98, 114, 164)
                g2d.drawRoundRect(2, 2, width - 4, height - 4, 15, 15)
            }
            CardState.FLIPPED -> {
                g2d.color = Color(248, 248, 242) // White
                g2d.fillRoundRect(2, 2, width - 4, height - 4, 15, 15)
                g2d.color = Color.BLACK
                val fm = g2d.fontMetrics
                g2d.drawString(symbol, (width - fm.stringWidth(symbol)) / 2, (height + fm.ascent - fm.descent) / 2)
            }
            CardState.MATCHED -> {
                g2d.color = Color(80, 250, 123) // Green
                g2d.fillRoundRect(2, 2, width - 4, height - 4, 15, 15)
                g2d.color = Color.BLACK
                val fm = g2d.fontMetrics
                g2d.drawString(symbol, (width - fm.stringWidth(symbol)) / 2, (height + fm.ascent - fm.descent) / 2)
            }
        }

        g2d.transform = oldTransform
    }
}

// --- Main Game Panel ---
class MemoryGamePanel : JPanel() {
    private val prefs = Preferences.userRoot().node("MemoryGamePro")

    // UI Elements
    private val gridPanel = JPanel()
    private val lblTime = JLabel("Time: 0s")
    private val lblMoves = JLabel("Moves: 0")
    private val lblScore = JLabel("Score: 0")
    private val lblHighScore = JLabel("Best: ${prefs.getInt("highscore", 0)}")
    private val comboDifficulty = JComboBox(Difficulty.values())

    // Game State
    private var currentDifficulty = Difficulty.EASY
    private var cards = mutableListOf<CardButton>()
    private var firstCard: CardButton? = null
    private var secondCard: CardButton? = null

    private var isChecking = false
    private var pairsMatched = 0
    private var moves = 0
    private var secondsElapsed = 0

    private val gameTimer = Timer(1000) {
        secondsElapsed++
        updateHUD()
    }

    init {
        layout = BorderLayout()
        background = Color(40, 42, 54)
        border = EmptyBorder(15, 15, 15, 15)

        setupTopHUD()
        setupBottomControls()

        gridPanel.background = background
        add(gridPanel, BorderLayout.CENTER)

        comboDifficulty.addActionListener {
            val selected = comboDifficulty.selectedItem as Difficulty
            if (selected != currentDifficulty) {
                currentDifficulty = selected
                startGame()
            }
        }

        startGame()
    }

    private fun setupTopHUD() {
        val topPanel = JPanel(GridLayout(1, 3)).apply {
            background = background
            border = EmptyBorder(0, 0, 15, 0)
        }
        val font = Font("Segoe UI", Font.BOLD, 20)
        val color = Color(139, 233, 253) // Cyan

        arrayOf(lblTime, lblScore, lblMoves).forEach {
            it.font = font
            it.foreground = color
            it.horizontalAlignment = SwingConstants.CENTER
            topPanel.add(it)
        }
        add(topPanel, BorderLayout.NORTH)
    }

    private fun setupBottomControls() {
        val bottomPanel = JPanel(FlowLayout(FlowLayout.CENTER, 20, 10)).apply {
            background = background
            border = EmptyBorder(15, 0, 0, 0)
        }

        val btnRestart = JButton("Restart Game").apply {
            font = Font("Segoe UI", Font.BOLD, 16)
            background = Color(255, 85, 85)
            foreground = Color.WHITE
            isFocusPainted = false
            addActionListener { startGame() }
        }

        lblHighScore.font = Font("Segoe UI", Font.BOLD, 18)
        lblHighScore.foreground = Color(241, 250, 140) // Yellow

        bottomPanel.add(JLabel("Difficulty:").apply { foreground = Color.WHITE; font = Font("Segoe UI", Font.BOLD, 16) })
        bottomPanel.add(comboDifficulty)
        bottomPanel.add(btnRestart)
        bottomPanel.add(lblHighScore)

        add(bottomPanel, BorderLayout.SOUTH)
    }

    private fun startGame() {
        gameTimer.stop()
        secondsElapsed = 0
        moves = 0
        pairsMatched = 0
        isChecking = false
        firstCard = null
        secondCard = null
        updateHUD()

        // Rebuild Grid
        gridPanel.removeAll()
        gridPanel.layout = GridLayout(currentDifficulty.rows, currentDifficulty.cols, 10, 10)
        cards.clear()

        // Generate Deck
        val totalPairs = (currentDifficulty.rows * currentDifficulty.cols) / 2
        val deckSymbols = (SYMBOLS.take(totalPairs) + SYMBOLS.take(totalPairs)).shuffled()

        deckSymbols.forEachIndexed { index, symbol ->
            val card = CardButton(index, symbol) { handleCardClick(it) }
            cards.add(card)
            gridPanel.add(card)
        }

        revalidate()
        repaint()
        gameTimer.start()
    }

    private fun handleCardClick(card: CardButton) {
        if (isChecking || card.isAnimating) return

        card.flipTo(CardState.FLIPPED)

        if (firstCard == null) {
            firstCard = card
        } else {
            secondCard = card
            isChecking = true
            moves++
            updateHUD()
            checkMatch()
        }
    }

    private fun checkMatch() {
        val c1 = firstCard!!
        val c2 = secondCard!!

        if (c1.symbol == c2.symbol) {
            // Match
            pairsMatched++
            Timer(400) {
                c1.flipTo(CardState.MATCHED)
                c2.flipTo(CardState.MATCHED) {
                    resetTurn()
                    if (pairsMatched == cards.size / 2) handleWin()
                }
            }.apply { isRepeats = false }.start()
        } else {
            // Mismatch
            Timer(800) {
                c1.flipTo(CardState.HIDDEN)
                c2.flipTo(CardState.HIDDEN) { resetTurn() }
            }.apply { isRepeats = false }.start()
        }
    }

    private fun resetTurn() {
        firstCard = null
        secondCard = null
        isChecking = false
    }

    private fun updateHUD() {
        lblTime.text = "Time: ${secondsElapsed}s"
        lblMoves.text = "Moves: $moves"
        lblScore.text = "Score: ${calculateScore()}"
    }

    private fun calculateScore(): Int {
        if (moves == 0) return 0
        val baseScore = if (currentDifficulty == Difficulty.EASY) 5000 else 15000
        val timePenalty = secondsElapsed * 10
        val movePenalty = moves * 20
        return Math.max(0, baseScore - timePenalty - movePenalty)
    }

    private fun handleWin() {
        gameTimer.stop()
        val finalScore = calculateScore()
        val currentHighScore = prefs.getInt("highscore", 0)

        var message = "You matched all pairs in $secondsElapsed seconds with $moves moves!\nFinal Score: $finalScore"

        if (finalScore > currentHighScore) {
            prefs.putInt("highscore", finalScore)
            lblHighScore.text = "Best: $finalScore"
            message += "\n\n🎉 NEW HIGH SCORE! 🎉"
        }

        JOptionPane.showMessageDialog(this, message, "Victory!", JOptionPane.INFORMATION_MESSAGE)
    }
}

// --- Bootstrap ---
fun main() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Exception) {
        e.printStackTrace()
    }

    SwingUtilities.invokeLater {
        val frame = JFrame("Pro Memory Match")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.add(MemoryGamePanel())
        frame.setSize(700, 750)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}