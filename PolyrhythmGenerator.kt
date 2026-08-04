import java.awt.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.sound.midi.MidiChannel
import javax.sound.midi.MidiSystem
import javax.sound.midi.Synthesizer
import javax.swing.*

/**
 * Polyrhythm Grid Generator
 * Visual and auditory trainer for practicing overlapping musical rhythms (e.g., 3:4, 5:7).
 */
class PolyrhythmGenerator : JFrame("Polyrhythm Grid Generator") {

    // --- State Variables ---
    @Volatile private var rhythmA = 3  // Beats per measure for Rhythm A
    @Volatile private var rhythmB = 4  // Beats per measure for Rhythm B
    @Volatile private var bpm = 90      // Master BPM speed
    @Volatile private var isPlaying = false

    private var currentTick = 0
    private var totalLcmTicks = 12

    // --- MIDI Audio Engine ---
    private var synthesizer: Synthesizer? = null
    private var midiChannel: MidiChannel? = null

    // Percussion Pitches
    private val pitchA = 56        // Cowbell (Rhythm A)
    private val pitchB = 77        // Low Woodblock (Rhythm B)
    private val pitchIntersect = 81 // Open Triangle / Accent (Intersection)

    // --- Scheduler Engine ---
    private var scheduler: ScheduledExecutorService? = null

    // --- GUI Components ---
    private lateinit var playButton: JButton
    private val intersectionBanner = JLabel("3 : 4 POLYRHYTHM", SwingConstants.CENTER)
    private val trackAPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 10))
    private val trackBPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 10))

    private val ledsA = mutableListOf<JPanel>()
    private val ledsB = mutableListOf<JPanel>()
    private val visualProgressPanel = ProgressCanvas()

    init {
        title = "Polyrhythm Grid Generator"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false
        layout = BorderLayout()

        initMidi()

        // Assemble GUI Panels
        val topToolbar = createToolbar()
        val centerMatrix = createMatrixPanel()

        add(topToolbar, BorderLayout.NORTH)
        add(centerMatrix, BorderLayout.CENTER)

        rebuildLedTracks()

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
                midiChannel = channels[9] // General MIDI Percussion Channel
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "MIDI Audio Error: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    /**
     * Top Controls Toolbar: Rhythm A/B spinners, Master BPM slider, Play/Stop button.
     */
    private fun createToolbar(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 10)).apply {
            background = Color(35, 35, 40)
        }

        val labelColor = Color.WHITE

        // Rhythm A Selector
        val spinnerA = JSpinner(SpinnerNumberModel(rhythmA, 1, 16, 1)).apply {
            preferredSize = Dimension(55, 25)
            addChangeListener {
                rhythmA = value as Int
                updateRhythmConfig()
            }
        }

        // Rhythm B Selector
        val spinnerB = JSpinner(SpinnerNumberModel(rhythmB, 1, 16, 1)).apply {
            preferredSize = Dimension(55, 25)
            addChangeListener {
                rhythmB = value as Int
                updateRhythmConfig()
            }
        }

        // Master BPM Slider
        val bpmLabel = JLabel("BPM: $bpm").apply { foreground = labelColor }
        val bpmSlider = JSlider(JSlider.HORIZONTAL, 30, 240, bpm).apply {
            preferredSize = Dimension(140, 25)
            background = Color(35, 35, 40)
            addChangeListener {
                bpm = value
                bpmLabel.text = "BPM: $bpm"
            }
        }

        // Play / Stop Button
        playButton = JButton("START").apply {
            font = Font("SansSerif", Font.BOLD, 13)
            background = Color(76, 175, 80)
            foreground = Color.WHITE
            isFocusPainted = false
            preferredSize = Dimension(100, 32)
            addActionListener { togglePlayback() }
        }

        panel.add(JLabel("Rhythm A:").apply { foreground = labelColor })
        panel.add(spinnerA)
        panel.add(Box.createHorizontalStrut(10))
        panel.add(JLabel("Rhythm B:").apply { foreground = labelColor })
        panel.add(spinnerB)
        panel.add(Box.createHorizontalStrut(15))
        panel.add(bpmLabel)
        panel.add(bpmSlider)
        panel.add(Box.createHorizontalStrut(15))
        panel.add(playButton)

        return panel
    }

    /**
     * Center Display: Visual LED Grid Matrix and Bouncing Progress Canvas.
     */
    private fun createMatrixPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            background = Color(20, 20, 25)
            border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
        }

        val gbc = GridBagConstraints().apply {
            insets = Insets(8, 8, 8, 8)
            fill = GridBagConstraints.HORIZONTAL
            gridx = 0
        }

        // 1. Intersection Banner
        intersectionBanner.apply {
            font = Font("SansSerif", Font.BOLD, 22)
            foreground = Color(255, 214, 0) // Gold
            isOpaque = true
            background = Color(30, 30, 35)
            preferredSize = Dimension(550, 45)
            border = BorderFactory.createLineBorder(Color(60, 60, 65), 2)
        }
        gbc.gridy = 0
        panel.add(intersectionBanner, gbc)

        // 2. Track A LEDs
        trackAPanel.background = Color(20, 20, 25)
        gbc.gridy = 1
        panel.add(trackAPanel, gbc)

        // 3. Dual Synchronized Progress Canvas
        visualProgressPanel.preferredSize = Dimension(550, 45)
        gbc.gridy = 2
        panel.add(visualProgressPanel, gbc)

        // 4. Track B LEDs
        trackBPanel.background = Color(20, 20, 25)
        gbc.gridy = 3
        panel.add(trackBPanel, gbc)

        return panel
    }

    /**
     * Rebuilds LED grid containers when Rhythm A or B ratios change.
     */
    private fun rebuildLedTracks() {
        trackAPanel.removeAll()
        trackBPanel.removeAll()
        ledsA.clear()
        ledsB.clear()

        // Track A LEDs (Cyan)
        for (i in 0 until rhythmA) {
            val led = JPanel().apply {
                preferredSize = Dimension(32, 32)
                background = Color(40, 45, 50)
                border = BorderFactory.createLineBorder(Color(0, 230, 118), 2)
            }
            ledsA.add(led)
            trackAPanel.add(led)
        }

        // Track B LEDs (Orange/Red)
        for (i in 0 until rhythmB) {
            val led = JPanel().apply {
                preferredSize = Dimension(32, 32)
                background = Color(40, 45, 50)
                border = BorderFactory.createLineBorder(Color(255, 87, 34), 2)
            }
            ledsB.add(led)
            trackBPanel.add(led)
        }

        intersectionBanner.text = "$rhythmA : $rhythmB POLYRHYTHM"

        trackAPanel.revalidate()
        trackBPanel.revalidate()
        repaint()
    }

    private fun updateRhythmConfig() {
        val wasRunning = isPlaying
        if (wasRunning) stopPlayback()

        totalLcmTicks = lcm(rhythmA, rhythmB)
        rebuildLedTracks()

        if (wasRunning) startPlayback()
    }

    @Synchronized
    private fun togglePlayback() {
        if (isPlaying) stopPlayback() else startPlayback()
    }

    private fun startPlayback() {
        isPlaying = true
        currentTick = 0
        totalLcmTicks = lcm(rhythmA, rhythmB)

        playButton.text = "STOP"
        playButton.background = Color(244, 67, 54)

        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduleNextTick()
    }

    private fun stopPlayback() {
        isPlaying = false
        scheduler?.shutdownNow()
        scheduler = null

        playButton.text = "START"
        playButton.background = Color(76, 175, 80)
        resetLeds()
    }

    /**
     * High-Precision Micro-Tick Scheduler Loop using Least Common Multiple (LCM).
     */
    private fun scheduleNextTick() {
        if (!isPlaying) return

        // 1 Measure = 240,000 / BPM milliseconds
        // Each LCM tick duration in nanoseconds:
        val measureNanos = (240.0 / bpm) * 1_000_000_000L
        val tickIntervalNanos = (measureNanos / totalLcmTicks).toLong()

        scheduler?.schedule({
            if (!isPlaying) return@schedule

            val stepAInterval = totalLcmTicks / rhythmA
            val stepBInterval = totalLcmTicks / rhythmB

            val isTriggerA = (currentTick % stepAInterval == 0)
            val isTriggerB = (currentTick % stepBInterval == 0)

            val indexA = currentTick / stepAInterval
            val indexB = currentTick / stepBInterval

            // Trigger MIDI Audio Hits
            if (isTriggerA && isTriggerB) {
                // Intersection Beat: Play both + Accent Triangle sound
                midiChannel?.noteOn(pitchA, 110)
                midiChannel?.noteOn(pitchB, 110)
                midiChannel?.noteOn(pitchIntersect, 120)
            } else if (isTriggerA) {
                midiChannel?.noteOn(pitchA, 100)
            } else if (isTriggerB) {
                midiChannel?.noteOn(pitchB, 100)
            }

            // Update UI Visuals on EDT
            SwingUtilities.invokeLater {
                updateVisuals(indexA, indexB, isTriggerA, isTriggerB, currentTick)
            }

            // Advance LCM clock tick
            currentTick = (currentTick + 1) % totalLcmTicks

            scheduleNextTick()

        }, tickIntervalNanos, TimeUnit.NANOSECONDS)
    }

    /**
     * Updates LED highlights and Intersection Flash Banner.
     */
    private fun updateVisuals(indexA: Int, indexB: Int, hitA: Boolean, hitB: Boolean, tick: Int) {
        // Reset LED colors
        for (led in ledsA) led.background = Color(40, 45, 50)
        for (led in ledsB) led.background = Color(40, 45, 50)

        // Highlight Active Rhythm A LED (Cyan)
        if (hitA && indexA in ledsA.indices) {
            ledsA[indexA].background = Color(0, 230, 118)
        }

        // Highlight Active Rhythm B LED (Orange)
        if (hitB && indexB in ledsB.indices) {
            ledsB[indexB].background = Color(255, 87, 34)
        }

        // Highlight Intersection Flash Banner
        if (hitA && hitB) {
            intersectionBanner.background = Color(255, 214, 0) // Gold Flash
            intersectionBanner.foreground = Color.BLACK
        } else {
            intersectionBanner.background = Color(30, 30, 35)
            intersectionBanner.foreground = Color(255, 214, 0)
        }

        // Update Position Indicator Canvas
        visualProgressPanel.setProgress(tick.toDouble() / totalLcmTicks)
    }

    private fun resetLeds() {
        for (led in ledsA) led.background = Color(40, 45, 50)
        for (led in ledsB) led.background = Color(40, 45, 50)
        intersectionBanner.background = Color(30, 30, 35)
        intersectionBanner.foreground = Color(255, 214, 0)
        visualProgressPanel.setProgress(0.0)
    }

    // --- Math Utilities ---
    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    private fun lcm(a: Int, b: Int): Int = (a * b) / gcd(a, b)

    /**
     * Inner Component: Smooth dual-track progress visualization pane.
     */
    private inner class ProgressCanvas : JPanel() {
        private var progress = 0.0

        init {
            background = Color(15, 15, 18)
            border = BorderFactory.createLineBorder(Color(50, 50, 55))
        }

        fun setProgress(valRatio: Double) {
            progress = valRatio
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val width = this.width
            val height = this.height

            // Track Guidelines
            g2.color = Color(40, 40, 45)
            g2.drawLine(20, height / 3, width - 20, height / 3)
            g2.drawLine(20, (height * 2) / 3, width - 20, (height * 2) / 3)

            // Progress Marker Ball
            val posX = 20 + (progress * (width - 40)).toInt()

            // Rhythm A Ball (Cyan)
            g2.color = Color(0, 230, 118)
            g2.fillOval(posX - 7, (height / 3) - 7, 14, 14)

            // Rhythm B Ball (Orange)
            g2.color = Color(255, 87, 34)
            g2.fillOval(posX - 7, ((height * 2) / 3) - 7, 14, 14)
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val generator = PolyrhythmGenerator()
        generator.isVisible = true
    }
}