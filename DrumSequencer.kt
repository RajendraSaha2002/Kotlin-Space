import java.awt.*
import javax.sound.midi.MidiChannel
import javax.sound.midi.MidiSystem
import javax.sound.midi.Synthesizer
import javax.swing.*

/**
 * Programmable MIDI Drum Step Sequencer
 * Multi-row visual grid matrix driving real-time native MIDI percussion loops.
 */
class DrumSequencer : JFrame("Programmable MIDI Drum Step Sequencer") {

    private val numSteps = 16
    private var bpm = 120
    private var isPlaying = false
    private var currentStep = 0

    // MIDI System variables
    private var synthesizer: Synthesizer? = null
    private var drumChannel: MidiChannel? = null

    // Swing Timer for precise playback looping
    private lateinit var timer: Timer

    // Instrument Configuration (GM Drum Pitch Map)
    data class DrumInstrument(val name: String, val midiNote: Int)

    private val instruments = listOf(
        DrumInstrument("Bass Drum", 36),
        DrumInstrument("Snare Drum", 38),
        DrumInstrument("Closed Hi-Hat", 42),
        DrumInstrument("Open Hi-Hat", 46),
        DrumInstrument("Hand Clap", 39),
        DrumInstrument("Low Tom", 45),
        DrumInstrument("High Tom", 50),
        DrumInstrument("Crash Cymbal", 49)
    )

    // GUI Grid matrix of toggle buttons [row/instrument][column/step]
    private val gridButtons = Array(instruments.size) { Array(numSteps) { JToggleButton() } }
    private val stepIndicators = Array(numSteps) { JPanel() }

    private lateinit var playButton: JButton

    init {
        title = "Programmable MIDI Drum Step Sequencer"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false
        layout = BorderLayout()

        initMidi()
        initPlaybackTimer()

        // Build UI
        val topToolbar = createToolbar()
        val gridPanel = createGridPanel()

        add(topToolbar, BorderLayout.NORTH)
        add(gridPanel, BorderLayout.CENTER)

        pack()
        setLocationRelativeTo(null)
    }

    /**
     * Initializes General MIDI Synthesizer on Channel 10 (Percussion Channel index 9).
     */
    private fun initMidi() {
        try {
            synthesizer = MidiSystem.getSynthesizer().apply {
                open()
                // Channel index 9 is reserved for General MIDI Percussion
                drumChannel = channels[9]
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to initialize MIDI Synthesizer: ${e.message}",
                "MIDI Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * Initializes the background timer driving the step playback loop.
     */
    private fun initPlaybackTimer() {
        val intervalMs = calculateStepInterval(bpm)
        timer = Timer(intervalMs) {
            executeStep()
        }
    }

    /**
     * Calculates time step interval in milliseconds for 16th note steps.
     */
    private fun calculateStepInterval(bpm: Int): Int {
        // (60,000 ms / BPM) = Quarter Note duration; divide by 4 for 16th note step
        return (15000.0 / bpm).toInt().coerceAtLeast(10)
    }

    /**
     * Advances the sequencer by one step, triggers active MIDI drums, and updates visual indicator.
     */
    private fun executeStep() {
        val prevStep = (currentStep - 1 + numSteps) % numSteps

        // Reset previous step indicator highlight
        stepIndicators[prevStep].background = Color(70, 70, 70)

        // Highlight current step indicator
        stepIndicators[currentStep].background = Color(255, 214, 0) // Vivid Yellow

        // Trigger notes for all active toggles in current column step
        for (r in instruments.indices) {
            if (gridButtons[r][currentStep].isSelected) {
                val pitch = instruments[r].midiNote
                drumChannel?.noteOn(pitch, 100) // Strike drum with velocity 100
            }
        }

        // Advance step
        currentStep = (currentStep + 1) % numSteps
    }

    /**
     * Builds top control toolbar with Play, BPM Slider, and Beat Presets.
     */
    private fun createToolbar(): JToolBar {
        val toolbar = JToolBar().apply {
            isFloatable = false
            layout = FlowLayout(FlowLayout.LEFT, 12, 8)
            background = Color(40, 40, 40)
        }

        // Play / Stop Button
        playButton = JButton("Play").apply {
            font = Font("SansSerif", Font.BOLD, 12)
            background = Color(76, 175, 80) // Green
            foreground = Color.WHITE
            isFocusPainted = false
            addActionListener { togglePlayback() }
        }

        // BPM Slider
        val bpmLabel = JLabel("BPM: $bpm").apply {
            foreground = Color.WHITE
            font = Font("SansSerif", Font.BOLD, 12)
        }

        val bpmSlider = JSlider(JSlider.HORIZONTAL, 60, 240, bpm).apply {
            preferredSize = Dimension(150, 25)
            background = Color(40, 40, 40)
            addChangeListener {
                bpm = value
                bpmLabel.text = "BPM: $bpm"
                timer.delay = calculateStepInterval(bpm)
            }
        }

        // Clear Grid Button
        val clearBtn = JButton("Clear Grid").apply {
            isFocusPainted = false
            addActionListener { clearGrid() }
        }

        // Preset Dropdown
        val presets = arrayOf("Select Preset...", "Basic Rock", "Four on the Floor", "Hip-Hop Bounce")
        val presetBox = JComboBox(presets).apply {
            isFocusable = false
            addActionListener {
                when (selectedIndex) {
                    1 -> loadRockPreset()
                    2 -> loadFourOnFloorPreset()
                    3 -> loadHipHopPreset()
                }
            }
        }

        toolbar.add(playButton)
        toolbar.addSeparator(Dimension(15, 20))
        toolbar.add(bpmLabel)
        toolbar.add(bpmSlider)
        toolbar.addSeparator(Dimension(15, 20))
        toolbar.add(clearBtn)
        toolbar.add(JLabel("  Presets: ").apply { foreground = Color.WHITE })
        toolbar.add(presetBox)

        return toolbar
    }

    /**
     * Builds the main step sequencer grid panel.
     */
    private fun createGridPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            background = Color(25, 25, 25)
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        }

        val gbc = GridBagConstraints().apply {
            insets = Insets(2, 2, 2, 2)
            fill = GridBagConstraints.BOTH
        }

        // Top Row: Step Indicators (Columns 1 to 16)
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JLabel("Steps -> ").apply {
            foreground = Color.GRAY
            font = Font("SansSerif", Font.BOLD, 11)
        }, gbc)

        for (c in 0 until numSteps) {
            gbc.gridx = c + 1
            val indicator = JPanel().apply {
                preferredSize = Dimension(32, 10)
                background = Color(70, 70, 70)
                border = BorderFactory.createLineBorder(Color.BLACK)
            }
            stepIndicators[c] = indicator
            panel.add(indicator, gbc)
        }

        // Instrument Rows
        for (r in instruments.indices) {
            gbc.gridy = r + 1

            // Row Label (Instrument Name)
            gbc.gridx = 0
            val label = JLabel(instruments[r].name).apply {
                foreground = Color.WHITE
                font = Font("SansSerif", Font.BOLD, 12)
                preferredSize = Dimension(110, 30)
            }
            panel.add(label, gbc)

            // Step Toggle Buttons for this Instrument
            for (c in 0 until numSteps) {
                gbc.gridx = c + 1

                val beatGroup = c / 4
                val isEvenBeatGroup = beatGroup % 2 == 0

                val toggleBtn = JToggleButton().apply {
                    preferredSize = Dimension(32, 32)
                    isFocusPainted = false
                    border = BorderFactory.createLineBorder(Color(20, 20, 20))

                    // Styling based on active vs inactive state
                    val defaultBg = if (isEvenBeatGroup) Color(55, 58, 60) else Color(40, 42, 44)
                    background = defaultBg

                    addActionListener {
                        if (isSelected) {
                            background = Color(255, 64, 129) // Neon Pink when active
                        } else {
                            background = defaultBg
                        }
                    }
                }

                gridButtons[r][c] = toggleBtn
                panel.add(toggleBtn, gbc)
            }
        }

        return panel
    }

    private fun togglePlayback() {
        if (isPlaying) {
            timer.stop()
            isPlaying = false
            playButton.text = "Play"
            playButton.background = Color(76, 175, 80) // Green
            resetStepIndicators()
        } else {
            currentStep = 0
            timer.start()
            isPlaying = true
            playButton.text = "Stop"
            playButton.background = Color(244, 67, 54) // Red
        }
    }

    private fun resetStepIndicators() {
        for (i in 0 until numSteps) {
            stepIndicators[i].background = Color(70, 70, 70)
        }
    }

    private fun clearGrid() {
        for (r in instruments.indices) {
            for (c in 0 until numSteps) {
                gridButtons[r][c].isSelected = false
                val beatGroup = c / 4
                gridButtons[r][c].background = if (beatGroup % 2 == 0) Color(55, 58, 60) else Color(40, 42, 44)
            }
        }
    }

    // --- PRESETS ---

    private fun loadRockPreset() {
        clearGrid()
        // Kick on 1 and 9
        setStep(0, 0); setStep(0, 8)
        // Snare on 5 and 13
        setStep(1, 4); setStep(1, 12)
        // Hi-Hat on every eighth note
        for (c in 0 until numSteps step 2) setStep(2, c)
    }

    private fun loadFourOnFloorPreset() {
        clearGrid()
        // Kick on quarter notes
        setStep(0, 0); setStep(0, 4); setStep(0, 8); setStep(0, 12)
        // Snare on 5 and 13
        setStep(1, 4); setStep(1, 12)
        // Off-beat Open Hi-Hat
        setStep(3, 2); setStep(3, 6); setStep(3, 10); setStep(3, 14)
        // Clap on 5 and 13
        setStep(4, 4); setStep(4, 12)
    }

    private fun loadHipHopPreset() {
        clearGrid()
        // Kick pattern
        setStep(0, 0); setStep(0, 7); setStep(0, 10)
        // Snare on 4 and 12
        setStep(1, 4); setStep(1, 12)
        // Closed Hi-Hat 16th notes
        for (c in 0 until numSteps) setStep(2, c)
        // Open Hi-Hat accent
        setStep(3, 14)
    }

    private fun setStep(row: Int, col: Int) {
        if (row in instruments.indices && col in 0 until numSteps) {
            gridButtons[row][col].isSelected = true
            gridButtons[row][col].background = Color(255, 64, 129)
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val sequencer = DrumSequencer()
        sequencer.isVisible = true
    }
}