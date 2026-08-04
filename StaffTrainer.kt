import java.awt.*
import java.awt.event.ActionListener
import javax.sound.midi.MidiChannel
import javax.sound.midi.MidiSystem
import javax.sound.midi.Synthesizer
import javax.swing.*

/**
 * Interactive Music Staff & Sheet Music Trainer
 * Visual learning tool for practicing reading classical music notation.
 */
class StaffTrainer : JFrame("Interactive Sheet Music Trainer") {

    enum class ClefType { TREBLE, BASS }

    data class NoteData(
        val letter: String,      // Pitch letter (C, D, E, F, G, A, B)
        val midiPitch: Int,      // MIDI Note number
        val staffPosition: Int,  // 0 = Bottom line of staff (E4 in Treble, G2 in Bass)
        val octaveLabel: String  // Full note label (e.g. C4, G4)
    )

    // --- Note Pools ---
    private val trebleNotes = listOf(
        NoteData("C", 60, -2, "C4"),
        NoteData("D", 62, -1, "D4"),
        NoteData("E", 64,  0, "E4"),
        NoteData("F", 65,  1, "F4"),
        NoteData("G", 67,  2, "G4"),
        NoteData("A", 69,  3, "A4"),
        NoteData("B", 71,  4, "B4"),
        NoteData("C", 72,  5, "C5"),
        NoteData("D", 74,  6, "D5"),
        NoteData("E", 76,  7, "E5"),
        NoteData("F", 77,  8, "F5"),
        NoteData("G", 79,  9, "G5"),
        NoteData("A", 81, 10, "A5")
    )

    private val bassNotes = listOf(
        NoteData("E", 40, -2, "E2"),
        NoteData("F", 41, -1, "F2"),
        NoteData("G", 43,  0, "G2"),
        NoteData("A", 45,  1, "A2"),
        NoteData("B", 47,  2, "B2"),
        NoteData("C", 48,  3, "C3"),
        NoteData("D", 50,  4, "D3"),
        NoteData("E", 52,  5, "E3"),
        NoteData("F", 53,  6, "F3"),
        NoteData("G", 55,  7, "G3"),
        NoteData("A", 57,  8, "A3"),
        NoteData("B", 59,  9, "B3"),
        NoteData("C", 60, 10, "C4")
    )

    // --- State ---
    private var currentClef = ClefType.TREBLE
    private var currentNote = trebleNotes[2] // Default E4
    private var correctCount = 0
    private var totalCount = 0
    private var streakCount = 0
    private var isInputBlocked = false

    // --- MIDI System ---
    private var synthesizer: Synthesizer? = null
    private var midiChannel: MidiChannel? = null

    // --- GUI Components ---
    private val staffPanel = StaffPanel()
    private val scoreLabel = JLabel("Score: 0/0 (0%) | Streak: 0", SwingConstants.CENTER)
    private val inputButtons = mutableListOf<JButton>()

    init {
        title = "Interactive Sheet Music Trainer"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false
        layout = BorderLayout()

        initMidi()

        // Create Layout
        val topBar = createTopBar()
        val bottomControls = createInputPanel()

        add(topBar, BorderLayout.NORTH)
        add(staffPanel, BorderLayout.CENTER)
        add(bottomControls, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(null)

        nextQuestion()
    }

    /**
     * Initializes General MIDI engine on Channel 0 (Acoustic Grand Piano).
     */
    private fun initMidi() {
        try {
            synthesizer = MidiSystem.getSynthesizer().apply {
                open()
                midiChannel = channels[0]
                midiChannel?.programChange(0) // Acoustic Piano
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "MIDI Audio Error: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    /**
     * Top Bar containing Clef Selector and Score Tracker.
     */
    private fun createTopBar(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            background = Color(240, 242, 245)
            border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
        }

        // Clef Dropdown
        val clefCombo = JComboBox(arrayOf("Treble Clef (G-Clef)", "Bass Clef (F-Clef)")).apply {
            isFocusable = false
            addActionListener {
                currentClef = if (selectedIndex == 0) ClefType.TREBLE else ClefType.BASS
                resetScore()
                nextQuestion()
            }
        }

        // Score Display Label
        scoreLabel.font = Font("SansSerif", Font.BOLD, 14)
        scoreLabel.foreground = Color(30, 30, 30)

        // Reset Button
        val resetBtn = JButton("Reset Score").apply {
            isFocusPainted = false
            addActionListener { resetScore() }
        }

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            isOpaque = false
            add(JLabel("Clef: "))
            add(clefCombo)
        }

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(scoreLabel, BorderLayout.CENTER)
        panel.add(resetBtn, BorderLayout.EAST)

        return panel
    }

    /**
     * Creates interactive piano-like note input buttons (C, D, E, F, G, A, B).
     */
    private fun createInputPanel(): JPanel {
        val outerPanel = JPanel(BorderLayout()).apply {
            background = Color(230, 233, 238)
            border = BorderFactory.createTitledBorder("Click the matching Note Name")
        }

        val btnPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 15)).apply {
            isOpaque = false
        }

        val noteLetters = listOf("C", "D", "E", "F", "G", "A", "B")

        for (letter in noteLetters) {
            val btn = JButton(letter).apply {
                font = Font("SansSerif", Font.BOLD, 18)
                preferredSize = Dimension(65, 55)
                background = Color.WHITE
                foreground = Color(30, 30, 35)
                isFocusPainted = false
                addActionListener { handleAnswer(letter) }
            }
            inputButtons.add(btn)
            btnPanel.add(btn)
        }

        outerPanel.add(btnPanel, BorderLayout.CENTER)
        return outerPanel
    }

    /**
     * Validates user answer, provides auditory & visual feedback, and updates statistics.
     */
    private fun handleAnswer(selectedLetter: String) {
        if (isInputBlocked) return

        totalCount++
        val isCorrect = selectedLetter.equals(currentNote.letter, ignoreCase = true)

        if (isCorrect) {
            correctCount++
            streakCount++

            // Play auditory reinforcement note
            midiChannel?.noteOn(currentNote.midiPitch, 100)

            staffPanel.setFeedback("Correct! That's ${currentNote.octaveLabel}", Color(46, 125, 50))
        } else {
            streakCount = 0
            staffPanel.setFeedback("Incorrect! That was ${currentNote.octaveLabel}", Color(198, 40, 40))
        }

        updateScoreLabel()

        // Block input briefly before loading next question
        isInputBlocked = true
        Timer(850) {
            midiChannel?.allNotesOff()
            nextQuestion()
            isInputBlocked = false
        }.apply { isRepeats = false; start() }
    }

    /**
     * Selects a new random note from the current active clef pool.
     */
    private fun nextQuestion() {
        val pool = if (currentClef == ClefType.TREBLE) trebleNotes else bassNotes
        var newNote: NoteData
        do {
            newNote = pool.random()
        } while (newNote == currentNote && pool.size > 1)

        currentNote = newNote
        staffPanel.drawNote(currentClef, currentNote)
    }

    private fun resetScore() {
        correctCount = 0
        totalCount = 0
        streakCount = 0
        updateScoreLabel()
        staffPanel.clearFeedback()
    }

    private fun updateScoreLabel() {
        val accuracy = if (totalCount > 0) (correctCount.toDouble() / totalCount * 100).toInt() else 0
        scoreLabel.text = "Score: $correctCount/$totalCount ($accuracy%) | Streak: $streakCount"
    }

    /**
     * Inner Component: Custom Graphics Panel drawing the Staff Lines, Clef, Notehead, and Ledger Lines.
     */
    private inner class StaffPanel : JPanel() {
        private var clef = ClefType.TREBLE
        private var note = trebleNotes[0]
        private var feedbackText = ""
        private var feedbackColor = Color.BLACK

        init {
            preferredSize = Dimension(550, 220)
            background = Color.WHITE
        }

        fun drawNote(clef: ClefType, note: NoteData) {
            this.clef = clef
            this.note = note
            repaint()
        }

        fun setFeedback(text: String, color: Color) {
            feedbackText = text
            feedbackColor = color
            repaint()
        }

        fun clearFeedback() {
            feedbackText = ""
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val width = this.width
            val height = this.height

            // --- Staff Dimensions ---
            val lineSpacing = 16
            val stepHeight = lineSpacing / 2 // 8px per line/space step
            val staffWidth = 420
            val startX = (width - staffWidth) / 2
            val bottomLineY = 140

            // 1. Draw 5 Classical Staff Lines
            g2.color = Color(60, 60, 60)
            g2.stroke = BasicStroke(2.0f)

            for (i in 0 until 5) {
                val y = bottomLineY - (i * lineSpacing)
                g2.drawLine(startX, y, startX + staffWidth, y)
            }

            // Draw Staff End Borders
            g2.drawLine(startX, bottomLineY, startX, bottomLineY - (4 * lineSpacing))
            g2.drawLine(startX + staffWidth, bottomLineY, startX + staffWidth, bottomLineY - (4 * lineSpacing))

            // 2. Draw Clef Symbol
            g2.font = Font("Serif", Font.PLAIN, 56)
            g2.color = Color.BLACK
            if (clef == ClefType.TREBLE) {
                // Treble Clef
                g2.drawString("𝄞", startX + 15, bottomLineY - 6)
            } else {
                // Bass Clef
                g2.drawString("𝄢", startX + 15, bottomLineY - 20)
            }

            // 3. Render Note Head
            val noteX = startX + 220
            val noteY = bottomLineY - (note.staffPosition * stepHeight)

            val noteHeadWidth = 18
            val noteHeadHeight = 14

            g2.fillOval(noteX - (noteHeadWidth / 2), noteY - (noteHeadHeight / 2), noteHeadWidth, noteHeadHeight)

            // 4. Render Note Stem
            g2.stroke = BasicStroke(2.5f)
            val stemHeight = 38
            if (note.staffPosition < 4) {
                // Stem goes UP on right side
                g2.drawLine(noteX + (noteHeadWidth / 2) - 2, noteY, noteX + (noteHeadWidth / 2) - 2, noteY - stemHeight)
            } else {
                // Stem goes DOWN on left side
                g2.drawLine(noteX - (noteHeadWidth / 2) + 2, noteY, noteX - (noteHeadWidth / 2) + 2, noteY + stemHeight)
            }

            // 5. Render Ledger Lines (for notes above or below main 5 lines)
            val ledgerLineWidth = 30
            if (note.staffPosition <= -2) {
                // Ledger line below staff (e.g. Middle C4 in Treble)
                val lineY = bottomLineY + lineSpacing
                g2.drawLine(noteX - (ledgerLineWidth / 2), lineY, noteX + (ledgerLineWidth / 2), lineY)
            } else if (note.staffPosition >= 10) {
                // Ledger line above staff (e.g. A5 in Treble)
                val lineY = bottomLineY - (5 * lineSpacing)
                g2.drawLine(noteX - (ledgerLineWidth / 2), lineY, noteX + (ledgerLineWidth / 2), lineY)
            }

            // 6. Draw Feedback Banner Message
            if (feedbackText.isNotEmpty()) {
                g2.font = Font("SansSerif", Font.BOLD, 16)
                g2.color = feedbackColor
                val fontMetrics = g2.fontMetrics
                val textWidth = fontMetrics.stringWidth(feedbackText)
                g2.drawString(feedbackText, (width - textWidth) / 2, 30)
            }
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val trainer = StaffTrainer()
        trainer.isVisible = true
    }
}