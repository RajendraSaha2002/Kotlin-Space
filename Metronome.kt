import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.sound.midi.MidiChannel
import javax.sound.midi.MidiSystem
import javax.sound.midi.Synthesizer
import javax.swing.*

/**
 * Metronome with Visual Accent Flasher
 * High-precision timing utility combining MIDI audio clicks with visual LED cues.
 */
class Metronome : JFrame("Metronome & Visual Accent Flasher") {

    // --- Metronome State ---
    @Volatile private var bpm = 120
    @Volatile private var beatsPerMeasure = 4
    @Volatile private var isRunning = false
    private var currentBeat = 0

    // Sound Options (GM Percussion Pitches)
    private var accentPitch = 75 // High Woodblock / Claves
    private var normalPitch = 76 // Low Woodblock

    // --- Audio Engine ---
    private var synthesizer: Synthesizer? = null
    private var midiChannel: MidiChannel? = null

    // --- High Precision Scheduler ---
    private var scheduler: ScheduledExecutorService? = null

    // --- GUI Components ---
    private val bpmDisplay = JLabel("120", SwingConstants.CENTER)
    private val ledContainer = JPanel()
    private val ledPanels = mutableListOf<JPanel>()
    private val flashOverlayPanel = JPanel()
    private lateinit var playButton: JButton
    private lateinit var bpmSlider: JSlider

    // Tap Tempo Tracker
    private val tapHistory = mutableListOf<Long>()

    init {
        title = "Metronome & Visual Flasher"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false
        layout = BorderLayout()

        initMidi()

        // Construct GUI
        val topPanel = createTopBar()
        val centerPanel = createCenterDisplay()
        val bottomPanel = createControlPanel()

        add(topPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(null)
    }

    /**
     * Initializes General MIDI Percussion engine on Channel 10 (Index 9).
     */
    private fun initMidi() {
        try {
            synthesizer = MidiSystem.getSynthesizer().apply {
                open()
                midiChannel = channels[9] // GM Percussion Channel
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "MIDI Audio Error: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    /**
     * Top Bar: Time Signature Dropdown & Sound Selector.
     */
    private fun createTopBar(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 10)).apply {
            background = Color(35, 35, 40)
        }

        val labelColor = Color.WHITE

        // Time Signature Selector
        val timeSigLabel = JLabel("Time Signature:").apply { foreground = labelColor }
        val timeSigs = arrayOf("2/4", "3/4", "4/4", "5/4", "6/8", "7/8")
        val timeSigCombo = JComboBox(timeSigs).apply {
            selectedIndex = 2 // Default 4/4
            isFocusable = false
            addActionListener {
                val selected = selectedItem as String
                beatsPerMeasure = selected.split("/")[0].toInt()
                rebuildLedGrid()
            }
        }

        // Sound Preset Selector
        val soundLabel = JLabel("Sound:").apply { foreground = labelColor }
        val sounds = arrayOf("Woodblock", "Cowbell / Bell", "High Click")
        val soundCombo = JComboBox(sounds).apply {
            isFocusable = false
            addActionListener {
                when (selectedIndex) {
                    0 -> { accentPitch = 75; normalPitch = 76 } // Woodblock
                    1 -> { accentPitch = 56; normalPitch = 77 } // Cowbell / High Agogo
                    2 -> { accentPitch = 80; normalPitch = 81 } // Muted Triangle / Click
                }
            }
        }

        panel.add(timeSigLabel)
        panel.add(timeSigCombo)
        panel.add(Box.createHorizontalStrut(15))
        panel.add(soundLabel)
        panel.add(soundCombo)

        return panel
    }

    /**
     * Center Panel: Large BPM readout & LED visual beat indicators.
     */
    private fun createCenterDisplay(): JPanel {
        val outerPanel = JPanel(BorderLayout()).apply {
            background = Color(20, 20, 25)
            border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
        }

        // Flash Overlay Border Container
        flashOverlayPanel.apply {
            layout = BorderLayout()
            background = Color(20, 20, 25)
            border = BorderFactory.createLineBorder(Color(40, 40, 45), 4)
        }

        // BPM Counter Display
        bpmDisplay.apply {
            font = Font("SansSerif", Font.BOLD, 80)
            foreground = Color(0, 230, 118) // Bright Cyan/Green
        }

        val bpmUnitLabel = JLabel("BPM", SwingConstants.CENTER).apply {
            font = Font("SansSerif", Font.BOLD, 14)
            foreground = Color.GRAY
        }

        val readoutBox = JPanel(GridLayout(2, 1)).apply {
            isOpaque = false
            add(bpmDisplay)
            add(bpmUnitLabel)
        }

        // LED Indicators Container
        ledContainer.apply {
            layout = FlowLayout(FlowLayout.CENTER, 12, 15)
            isOpaque = false
        }
        rebuildLedGrid()

        flashOverlayPanel.add(readoutBox, BorderLayout.CENTER)
        flashOverlayPanel.add(ledContainer, BorderLayout.SOUTH)

        outerPanel.add(flashOverlayPanel, BorderLayout.CENTER)
        return outerPanel
    }

    /**
     * Reconstructs the virtual LED row whenever the time signature changes.
     */
    private fun rebuildLedGrid() {
        ledContainer.removeAll()
        ledPanels.clear()

        for (i in 0 until beatsPerMeasure) {
            val led = JPanel().apply {
                preferredSize = Dimension(36, 36)
                background = Color(50, 50, 55)
                border = BorderFactory.createLineBorder(if (i == 0) Color(255, 214, 0) else Color(80, 80, 85), 2)
            }
            ledPanels.add(led)
            ledContainer.add(led)
        }

        ledContainer.revalidate()
        ledContainer.repaint()
    }

    /**
     * Bottom Panel: Play/Stop toggle, Tap Tempo, BPM Slider, and Quick +/- adjustments.
     */
    private fun createControlPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            background = Color(35, 35, 40)
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        }

        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            fill = GridBagConstraints.HORIZONTAL
        }

        // Row 1: Start/Stop & Tap Tempo
        playButton = JButton("START").apply {
            font = Font("SansSerif", Font.BOLD, 16)
            background = Color(76, 175, 80)
            foreground = Color.WHITE
            isFocusPainted = false
            preferredSize = Dimension(140, 40)
            addActionListener { toggleMetronome() }
        }

        val tapButton = JButton("TAP TEMPO").apply {
            font = Font("SansSerif", Font.BOLD, 14)
            background = Color(33, 150, 243)
            foreground = Color.WHITE
            isFocusPainted = false
            preferredSize = Dimension(140, 40)
            addActionListener { processTapTempo() }
        }

        gbc.gridx = 0; gbc.gridy = 0
        panel.add(playButton, gbc)
        gbc.gridx = 1
        panel.add(tapButton, gbc)

        // Row 2: BPM Slider
        bpmSlider = JSlider(JSlider.HORIZONTAL, 30, 280, bpm).apply {
            background = Color(35, 35, 40)
            isFocusable = false
            addChangeListener {
                updateBpm(value)
            }
        }
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2
        panel.add(bpmSlider, gbc)

        // Row 3: Quick Adjust Buttons (-5, -1, +1, +5)
        val adjustPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
            isOpaque = false
        }
        val deltaValues = arrayOf(-5, -1, 1, 5)
        for (delta in deltaValues) {
            val btnText = if (delta > 0) "+$delta" else "$delta"
            val btn = JButton(btnText).apply {
                isFocusPainted = false
                addActionListener { updateBpm(bpm + delta) }
            }
            adjustPanel.add(btn)
        }

        gbc.gridy = 2
        panel.add(adjustPanel, gbc)

        return panel
    }

    /**
     * Start / Stop Metronome engine.
     */
    @Synchronized
    private fun toggleMetronome() {
        if (isRunning) {
            isRunning = false
            scheduler?.shutdownNow()
            scheduler = null

            playButton.text = "START"
            playButton.background = Color(76, 175, 80)
            resetVisuals()
        } else {
            isRunning = true
            currentBeat = 0

            playButton.text = "STOP"
            playButton.background = Color(244, 67, 54)

            scheduler = Executors.newSingleThreadScheduledExecutor()
            scheduleNextBeat()
        }
    }

    /**
     * High-Precision Recursive Scheduling Thread Loop.
     */
    private fun scheduleNextBeat() {
        if (!isRunning) return

        val intervalNanos = (60.0 / bpm * 1_000_000_000L).toLong()

        scheduler?.schedule({
            if (!isRunning) return@schedule

            val isAccent = (currentBeat == 0)

            // 1. Trigger MIDI Audio Click
            if (isAccent) {
                midiChannel?.noteOn(accentPitch, 120) // Loud Accent
            } else {
                midiChannel?.noteOn(normalPitch, 85)  // Normal Click
            }

            // 2. Trigger Visual Flashes on EDT Thread
            val beatIndex = currentBeat
            SwingUtilities.invokeLater {
                triggerVisualBeat(beatIndex, isAccent)
            }

            // Advance beat
            currentBeat = (currentBeat + 1) % beatsPerMeasure

            // Schedule next beat precisely
            scheduleNextBeat()

        }, intervalNanos, TimeUnit.NANOSECONDS)
    }

    /**
     * Handles visual LED beat updates and Beat 1 Screen Accent Flashing.
     */
    private fun triggerVisualBeat(beatIndex: Int, isAccent: Boolean) {
        if (beatIndex !in ledPanels.indices) return

        // Reset all LEDs
        for (i in ledPanels.indices) {
            ledPanels[i].background = Color(50, 50, 55)
        }

        val activeLed = ledPanels[beatIndex]

        if (isAccent) {
            // Beat 1: Bright Red/Gold LED + Screen Overlay Accent Flash
            activeLed.background = Color(255, 214, 0) // Gold LED
            flashOverlayPanel.border = BorderFactory.createLineBorder(Color(255, 214, 0), 4)

            // Auto-reset flash border
            Timer(80) {
                flashOverlayPanel.border = BorderFactory.createLineBorder(Color(40, 40, 45), 4)
            }.apply { isRepeats = false; start() }

        } else {
            // Normal Beat: Bright Cyan LED Flash
            activeLed.background = Color(0, 230, 118) // Green/Cyan
        }
    }

    private fun resetVisuals() {
        flashOverlayPanel.border = BorderFactory.createLineBorder(Color(40, 40, 45), 4)
        for (led in ledPanels) {
            led.background = Color(50, 50, 55)
        }
    }

    private fun updateBpm(newBpm: Int) {
        bpm = newBpm.coerceIn(30, 280)
        bpmDisplay.text = "$bpm"
        if (bpmSlider.value != bpm) {
            bpmSlider.value = bpm
        }
    }

    /**
     * Tap Tempo Logic: Averages time intervals between consecutive button clicks.
     */
    private fun processTapTempo() {
        val now = System.currentTimeMillis()

        // Reset history if last tap was over 2 seconds ago
        if (tapHistory.isNotEmpty() && (now - tapHistory.last()) > 2000) {
            tapHistory.clear()
        }

        tapHistory.add(now)

        if (tapHistory.size >= 2) {
            if (tapHistory.size > 5) tapHistory.removeAt(0) // Keep recent 5 taps

            var totalDelta = 0L
            for (i in 1 until tapHistory.size) {
                totalDelta += tapHistory[i] - tapHistory[i - 1]
            }

            val avgDeltaMs = totalDelta.toDouble() / (tapHistory.size - 1)
            val calculatedBpm = (60000.0 / avgDeltaMs).toInt()
            updateBpm(calculatedBpm)
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val metronome = Metronome()
        metronome.isVisible = true
    }
}