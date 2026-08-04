import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.sound.sampled.*
import javax.swing.*
import kotlin.math.*

/**
 * Digital Audio Sine-Wave Synthesizer
 * Generates raw PCM audio wave byte arrays mathematically in real time.
 */
class AudioSynthesizer : JFrame("Digital Audio Wave Synthesizer") {

    enum class WaveType(val displayName: String) {
        SINE("Sine Wave"),
        SQUARE("Square Wave"),
        SAWTOOTH("Sawtooth Wave"),
        TRIANGLE("Triangle Wave");

        override fun toString(): String = displayName
    }

    // --- Audio Engine Variables ---
    private val sampleRate = 44100.0f
    @Volatile private var frequency = 440.0 // Default A4 note (440 Hz)
    @Volatile private var volume = 0.5      // Amplitude (0.0 to 1.0)
    @Volatile private var waveType = WaveType.SINE
    @Volatile private var isPlaying = false

    private var sourceDataLine: SourceDataLine? = null
    private var audioThread: Thread? = null
    private var phase = 0.0 // Phase accumulator for smooth frequency transitions

    // --- GUI Components ---
    private val waveVisualizer = WaveformVisualizer()
    private lateinit var playToggleButton: JToggleButton
    private lateinit var freqLabel: JLabel
    private lateinit var volLabel: JLabel

    init {
        title = "Digital Audio Sine-Wave Synthesizer"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false
        layout = BorderLayout()

        // Create UI Panels
        val visualizerPanel = createVisualizerPanel()
        val controlsPanel = createControlsPanel()
        val presetsPanel = createPresetsPanel()

        add(visualizerPanel, BorderLayout.NORTH)
        add(controlsPanel, BorderLayout.CENTER)
        add(presetsPanel, BorderLayout.SOUTH)

        // Ensure audio line is stopped when application closes
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                stopAudio()
            }
        })

        pack()
        setLocationRelativeTo(null)
    }

    /**
     * Starts the background thread streaming PCM audio to the SourceDataLine.
     */
    @Synchronized
    private fun startAudio() {
        if (isPlaying) return

        try {
            // 16-bit, Mono, Signed, Little-Endian Audio Format
            val format = AudioFormat(sampleRate, 16, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                JOptionPane.showMessageDialog(this, "Audio Line Format Not Supported!", "Audio Error", JOptionPane.ERROR_MESSAGE)
                return
            }

            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            sourceDataLine?.open(format, 4096)
            sourceDataLine?.start()

            isPlaying = true

            audioThread = Thread {
                val bufferSize = 1024 // 512 16-bit samples per chunk
                val buffer = ByteArray(bufferSize)

                while (isPlaying) {
                    generateAudioChunk(buffer)
                    sourceDataLine?.write(buffer, 0, buffer.size)
                }

                sourceDataLine?.drain()
                sourceDataLine?.stop()
                sourceDataLine?.close()
            }.apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
                start()
            }

        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Audio System Error: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    /**
     * Stops the real-time audio playback thread.
     */
    @Synchronized
    private fun stopAudio() {
        isPlaying = false
        audioThread?.join(500)
    }

    /**
     * Mathematical Wave Generator Engine: Fills the byte array buffer with PCM samples.
     */
    private fun generateAudioChunk(buffer: ByteArray) {
        val numSamples = buffer.size / 2
        val currentVol = volume
        val currentFreq = frequency
        val currentType = waveType

        // Phase advance per sample
        val phaseIncrement = (2.0 * PI * currentFreq) / sampleRate

        for (i in 0 until numSamples) {
            // Calculate raw math waveform value between -1.0 and +1.0
            val sampleValue = when (currentType) {
                WaveType.SINE -> sin(phase)
                WaveType.SQUARE -> if (phase < PI) 1.0 else -1.0
                WaveType.SAWTOOTH -> 1.0 - (phase / PI)
                WaveType.TRIANGLE -> (2.0 / PI) * asin(sin(phase))
            }

            // Scale sample to 16-bit signed integer range (-32768 to 32767) with volume
            val scaledSample = (sampleValue * currentVol * 32767.0).toInt().coerceIn(-32768, 32767)

            // Convert to Little-Endian Byte Order
            buffer[2 * i] = (scaledSample and 0xFF).toByte()            // Low byte
            buffer[2 * i + 1] = ((scaledSample shr 8) and 0xFF).toByte() // High byte

            // Advance accumulator phase continuously to avoid audio clicking artifacts
            phase += phaseIncrement
            if (phase >= 2.0 * PI) {
                phase -= 2.0 * PI
            }
        }
    }

    /**
     * Visual panel containing live waveform renderer.
     */
    private fun createVisualizerPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Waveform Oscilloscope")
            background = Color(20, 20, 20)
        }
        waveVisualizer.preferredSize = Dimension(550, 140)
        panel.add(waveVisualizer, BorderLayout.CENTER)
        return panel
    }

    /**
     * Sliders and selectors for Frequency, Volume, and Wave shape.
     */
    private fun createControlsPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(15, 20, 15, 20)
        }

        val gbc = GridBagConstraints().apply {
            insets = Insets(8, 8, 8, 8)
            fill = GridBagConstraints.HORIZONTAL
        }

        // 1. Waveform Selection
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("Wave Shape:"), gbc)

        gbc.gridx = 1
        val waveCombo = JComboBox(WaveType.values()).apply {
            addActionListener {
                waveType = selectedItem as WaveType
                waveVisualizer.updateWave(waveType, frequency)
            }
        }
        panel.add(waveCombo, gbc)

        // 2. Pitch / Frequency Slider (20 Hz to 2000 Hz)
        gbc.gridx = 0; gbc.gridy = 1
        freqLabel = JLabel("Frequency: 440 Hz")
        panel.add(freqLabel, gbc)

        gbc.gridx = 1
        val freqSlider = JSlider(JSlider.HORIZONTAL, 20, 2000, 440).apply {
            preferredSize = Dimension(320, 30)
            addChangeListener {
                frequency = value.toDouble()
                freqLabel.text = "Frequency: $value Hz"
                waveVisualizer.updateWave(waveType, frequency)
            }
        }
        panel.add(freqSlider, gbc)

        // 3. Volume Amplitude Slider (0% to 100%)
        gbc.gridx = 0; gbc.gridy = 2
        volLabel = JLabel("Volume: 50%")
        panel.add(volLabel, gbc)

        gbc.gridx = 1
        val volSlider = JSlider(JSlider.HORIZONTAL, 0, 100, 50).apply {
            preferredSize = Dimension(320, 30)
            addChangeListener {
                volume = value / 100.0
                volLabel.text = "Volume: $value%"
            }
        }
        panel.add(volSlider, gbc)

        // 4. Power Toggle Button
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2
        playToggleButton = JToggleButton("Start Synthesizer Audio").apply {
            font = Font("SansSerif", Font.BOLD, 13)
            background = Color(76, 175, 80)
            foreground = Color.WHITE
            isFocusPainted = false
            addActionListener {
                if (isSelected) {
                    text = "Stop Audio Stream"
                    background = Color(244, 67, 54)
                    startAudio()
                } else {
                    text = "Start Synthesizer Audio"
                    background = Color(76, 175, 80)
                    stopAudio()
                }
            }
        }
        panel.add(playToggleButton, gbc)

        return panel
    }

    /**
     * Musical pitch presets panel.
     */
    private fun createPresetsPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 10)).apply {
            border = BorderFactory.createTitledBorder("Musical Pitch Quick Presets")
        }

        val notes = mapOf(
            "C4 (261Hz)" to 261.63,
            "E4 (329Hz)" to 329.63,
            "G4 (392Hz)" to 392.00,
            "A4 (440Hz)" to 440.00,
            "C5 (523Hz)" to 523.25
        )

        for ((name, pitch) in notes) {
            val btn = JButton(name).apply {
                isFocusPainted = false
                addActionListener {
                    frequency = pitch
                    freqLabel.text = "Frequency: ${pitch.toInt()} Hz"
                    waveVisualizer.updateWave(waveType, frequency)
                }
            }
            panel.add(btn)
        }

        return panel
    }

    /**
     * Inner Component: Custom oscilloscope panel drawing mathematical wave plots dynamically.
     */
    private inner class WaveformVisualizer : JPanel() {
        private var currentType = WaveType.SINE
        private var currentFreq = 440.0

        init {
            background = Color(15, 15, 20)
        }

        fun updateWave(type: WaveType, freq: Double) {
            currentType = type
            currentFreq = freq
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val width = this.width
            val height = this.height
            val centerY = height / 2

            // Draw Grid Lines
            g2.color = Color(40, 50, 40)
            g2.drawLine(0, centerY, width, centerY)
            g2.drawLine(width / 2, 0, width / 2, height)

            // Draw Wave Plot
            g2.color = Color(0, 230, 118) // Bright Cyan/Green Wave Trace
            g2.stroke = BasicStroke(2.0f)

            val cycles = 3.0 // Render 3 full wave periods across window width
            val points = width

            var prevX = 0
            var prevY = centerY

            for (x in 0 until points) {
                val phase = (x.toDouble() / points) * cycles * 2.0 * PI

                val sample = when (currentType) {
                    WaveType.SINE -> sin(phase)
                    WaveType.SQUARE -> if (phase % (2.0 * PI) < PI) 1.0 else -1.0
                    WaveType.SAWTOOTH -> 1.0 - ((phase % (2.0 * PI)) / PI)
                    WaveType.TRIANGLE -> (2.0 / PI) * asin(sin(phase))
                }

                val y = centerY - (sample * (height / 2.5)).toInt()

                if (x > 0) {
                    g2.drawLine(prevX, prevY, x, y)
                }

                prevX = x
                prevY = y
            }
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val synth = AudioSynthesizer()
        synth.isVisible = true
    }
}