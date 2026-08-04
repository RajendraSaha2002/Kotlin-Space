import java.awt.*
import java.awt.event.*
import javax.sound.midi.*
import javax.swing.*

/**
 * Desktop Audio Synthesizer Piano
 * Real-time MIDI synthesizer with a custom visual keyboard.
 */
class PianoSynth : JFrame("Desktop Audio Synthesizer Piano") {

    private var synthesizer: Synthesizer? = null
    private var midiChannel: MidiChannel? = null

    private var octaveShift = 0 // In octaves (-2 to +2)
    private var velocity = 90  // Key strike volume (0-127)

    // Maps MIDI note number to its corresponding visual JButton component
    private val keyButtons = HashMap<Int, JButton>()

    // Set of currently playing active notes (prevents key-repeat stutter)
    private val activeNotes = HashSet<Int>()

    // QWERTY key mapping to relative MIDI pitch offsets from base note C4 (60)
    private val qwertyToPitchOffset = mapOf(
        'A' to 0,   // C4
        'W' to 1,   // C#4
        'S' to 2,   // D4
        'E' to 3,   // D#4
        'D' to 4,   // E4
        'F' to 5,   // F4
        'T' to 6,   // F#4
        'G' to 7,   // G4
        'Y' to 8,   // G#4
        'H' to 9,   // A4
        'U' to 10,  // A#4
        'J' to 11,  // B4
        'K' to 12,  // C5
        'O' to 13,  // C#5
        'L' to 14,  // D5
        'P' to 15,  // D#5
        ';' to 16,  // E5
        '\'' to 17  // F5
    )

    // Pre-defined MIDI Instrument Presets
    private val instruments = listOf(
        InstrumentPreset("Acoustic Grand Piano", 0),
        InstrumentPreset("Bright Electric Piano", 4),
        InstrumentPreset("Drawbar Organ", 16),
        InstrumentPreset("Church Organ", 19),
        InstrumentPreset("Acoustic Guitar", 24),
        InstrumentPreset("Electric Guitar", 27),
        InstrumentPreset("Violin Ensemble", 40),
        InstrumentPreset("Brass Trumpet", 56),
        InstrumentPreset("Synth Lead (Square)", 80),
        InstrumentPreset("Synth Pad (Warm)", 88),
        InstrumentPreset("Banjo", 105)
    )

    data class InstrumentPreset(val name: String, val programNumber: Int) {
        override fun toString(): String = name
    }

    init {
        title = "Desktop Audio Synthesizer Piano"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false
        layout = BorderLayout()

        initMidi()

        // Build GUI Layout
        val controlPanel = createControlPanel()
        val keyboardPanel = createKeyboardPanel()

        add(controlPanel, BorderLayout.NORTH)
        add(keyboardPanel, BorderLayout.CENTER)

        setupGlobalKeyListener()

        pack()
        setLocationRelativeTo(null)
    }

    /**
     * Initializes the native Java MIDI Synthesizer.
     */
    private fun initMidi() {
        try {
            synthesizer = MidiSystem.getSynthesizer().apply {
                open()
                // MIDI Channel 0 for playing melody notes
                midiChannel = channels[0]
                midiChannel?.programChange(instruments[0].programNumber)
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Error initializing MIDI System: ${e.message}\nAudio will not play.",
                "MIDI Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * Creates top control panel with Instrument dropdown, Volume slider, and Octave controls.
     */
    private fun createControlPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 10)).apply {
            background = Color(45, 45, 48)
        }

        val labelColor = Color.WHITE

        // 1. Instrument Selector
        val instLabel = JLabel("Instrument:").apply { foreground = labelColor }
        val instComboBox = JComboBox(instruments.toTypedArray()).apply {
            isFocusable = false
            addActionListener {
                val selected = selectedItem as InstrumentPreset
                midiChannel?.programChange(selected.programNumber)
            }
        }

        // 2. Volume Slider
        val volLabel = JLabel("Volume:").apply { foreground = labelColor }
        val volSlider = JSlider(JSlider.HORIZONTAL, 0, 127, velocity).apply {
            preferredSize = Dimension(100, 25)
            background = Color(45, 45, 48)
            isFocusable = false
            addChangeListener {
                velocity = value
            }
        }

        // 3. Octave Controls
        val octaveDisplay = JLabel("Octave: 0").apply { foreground = labelColor }
        val octaveDownBtn = JButton("-1 Oct").apply {
            isFocusable = false
            addActionListener {
                if (octaveShift > -2) {
                    stopAllNotes()
                    octaveShift--
                    octaveDisplay.text = "Octave: $octaveShift"
                }
            }
        }
        val octaveUpBtn = JButton("+1 Oct").apply {
            isFocusable = false
            addActionListener {
                if (octaveShift < 2) {
                    stopAllNotes()
                    octaveShift++
                    octaveDisplay.text = "Octave: $octaveShift"
                }
            }
        }

        panel.add(instLabel)
        panel.add(instComboBox)
        panel.add(Box.createHorizontalStrut(10))
        panel.add(volLabel)
        panel.add(volSlider)
        panel.add(Box.createHorizontalStrut(10))
        panel.add(octaveDownBtn)
        panel.add(octaveDisplay)
        panel.add(octaveUpBtn)

        return panel
    }

    /**
     * Constructs the visual piano keyboard (2 Octaves, 15 White Keys, 10 Black Keys).
     */
    private fun createKeyboardPanel(): JLayeredPane {
        val layeredPane = JLayeredPane().apply {
            preferredSize = Dimension(790, 240)
            background = Color(30, 30, 30)
            isOpaque = true
        }

        val whiteKeyWidth = 50
        val whiteKeyHeight = 220
        val blackKeyWidth = 32
        val blackKeyHeight = 135

        // Pitch pattern for two full octaves starting at C4 (60)
        // Note numbers relative to C: C=0, C#=1, D=2, D#=3, E=4, F=5, F#=6, G=7, G#=8, A=9, A#=10, B=11
        val whiteKeyNotes = intArrayOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83, 84)
        val noteLabels = arrayOf(
            "C4\n[A]", "D4\n[S]", "E4\n[D]", "F4\n[F]", "G4\n[G]", "A4\n[H]", "B4\n[J]",
            "C5\n[K]", "D5\n[L]", "E5\n[;]", "F5\n[']", "G5", "A5", "B5", "C6"
        )

        // Render White Keys
        for (i in whiteKeyNotes.indices) {
            val note = whiteKeyNotes[i]
            val xPos = i * (whiteKeyWidth + 2) + 15

            val whiteKey = JButton().apply {
                text = "<html><center>${noteLabels[i].replace("\n", "<br>")}</center></html>"
                font = Font("SansSerif", Font.BOLD, 10)
                verticalAlignment = SwingConstants.BOTTOM
                margin = Insets(0, 0, 10, 0)
                var backgroundColor = Color.WHITE
                background = Color.WHITE
                foreground = Color.DARK_GRAY
                isFocusable = false
                setBounds(xPos, 10, whiteKeyWidth, whiteKeyHeight)
            }

            attachMouseListeners(whiteKey, note)
            keyButtons[note] = whiteKey
            layeredPane.add(whiteKey, JLayeredPane.DEFAULT_LAYER)
        }

        // Render Black Keys (Offsets relative to white keys)
        val blackKeyNotes = intArrayOf(61, 63, 66, 68, 70, 73, 75, 78, 80, 82)
        val blackKeyIndices = intArrayOf(0, 1, 3, 4, 5, 7, 8, 10, 11, 12) // Indices of preceding white key
        val blackKeyLabels = arrayOf("C#4\n[W]", "D#4\n[E]", "F#4\n[T]", "G#4\n[Y]", "A#4\n[U]", "C#5\n[O]", "D#5\n[P]", "F#5", "G#5", "A#5")

        for (i in blackKeyNotes.indices) {
            val note = blackKeyNotes[i]
            val wIndex = blackKeyIndices[i]
            val xPos = (wIndex * (whiteKeyWidth + 2) + 15) + (whiteKeyWidth - blackKeyWidth / 2)

            val blackKey = JButton().apply {
                text = "<html><center>${blackKeyLabels[i].replace("\n", "<br>")}</center></html>"
                font = Font("SansSerif", Font.BOLD, 9)
                verticalAlignment = SwingConstants.BOTTOM
                margin = Insets(0, 0, 8, 0)
                background = Color.BLACK
                foreground = Color.WHITE
                isFocusable = false
                setBounds(xPos, 10, blackKeyWidth, blackKeyHeight)
            }

            attachMouseListeners(blackKey, note)
            keyButtons[note] = blackKey
            layeredPane.add(blackKey, JLayeredPane.PALETTE_LAYER)
        }

        return layeredPane
    }

    /**
     * Binds mouse click/press/release triggers to the key.
     */
    private fun attachMouseListeners(button: JButton, baseNote: Int) {
        button.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val actualNote = baseNote + (octaveShift * 12)
                noteOn(actualNote)
            }

            override fun mouseReleased(e: MouseEvent) {
                val actualNote = baseNote + (octaveShift * 12)
                noteOff(actualNote)
            }

            override fun mouseExited(e: MouseEvent) {
                // Stop note if mouse slips off key while dragging
                val actualNote = baseNote + (octaveShift * 12)
                if (activeNotes.contains(actualNote)) {
                    noteOff(actualNote)
                }
            }
        })
    }

    /**
     * Intercepts computer QWERTY keyboard events globally.
     */
    private fun setupGlobalKeyListener() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { e ->
            val char = e.keyChar.uppercaseChar()
            val pitchOffset = qwertyToPitchOffset[char]

            if (pitchOffset != null) {
                val actualNote = (60 + pitchOffset) + (octaveShift * 12)

                when (e.id) {
                    KeyEvent.KEY_PRESSED -> {
                        if (!activeNotes.contains(actualNote)) {
                            noteOn(actualNote)
                        }
                    }
                    KeyEvent.KEY_RELEASED -> {
                        noteOff(actualNote)
                    }
                }
                true // Consume key event
            } else {
                false
            }
        }
    }

    /**
     * Triggers MIDI Note On and applies active visual styling.
     */
    private fun noteOn(note: Int) {
        activeNotes.add(note)
        midiChannel?.noteOn(note, velocity)

        // Find visual button (adjust for octave offset to highlight the base key)
        val visualNote = note - (octaveShift * 12)
        keyButtons[visualNote]?.apply {
            background = Color(100, 180, 255) // Cyan active highlight
        }
    }

    /**
     * Triggers MIDI Note Off and restores normal visual styling.
     */
    private fun noteOff(note: Int) {
        activeNotes.remove(note)
        midiChannel?.noteOff(note)

        val visualNote = note - (octaveShift * 12)
        keyButtons[visualNote]?.apply {
            // Restore default color (white or black)
            background = if (isBlackKey(visualNote)) Color.BLACK else Color.WHITE
        }
    }

    /**
     * Stops all currently playing notes.
     */
    private fun stopAllNotes() {
        val activeList = HashSet(activeNotes)
        for (note in activeList) {
            noteOff(note)
        }
        midiChannel?.allNotesOff()
    }

    private fun isBlackKey(baseNote: Int): Boolean {
        val noteInOctave = baseNote % 12
        return noteInOctave == 1 || noteInOctave == 3 || noteInOctave == 6 || noteInOctave == 8 || noteInOctave == 10
    }
}

fun main() {
    // Launch GUI cleanly on Event Dispatch Thread
    SwingUtilities.invokeLater {
        val piano = PianoSynth()
        piano.isVisible = true
    }
}