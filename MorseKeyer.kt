import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.sound.sampled.*
import javax.swing.*
import javax.swing.text.DefaultHighlighter
import kotlin.math.PI
import kotlin.math.sin

/**
 * Desktop Morse Code Audio Keyer
 * Audio keyer and text parser streaming 700Hz sine wave tones with live UI highlighting.
 */
class MorseKeyer : JFrame("Desktop Morse Code Audio Keyer") {

    // --- State Variables ---
    @Volatile private var wpm = 20           // Words Per Minute speed (PARIS standard)
    @Volatile private var isPlaying = false
    private var playbackThread: Thread? = null

    // Audio Engine
    private var audioLine: SourceDataLine? = null
    private val sampleRate = 44100.0f
    private val toneFrequency = 700.0        // Standard 700Hz pitch

    // --- International Morse Code Dictionary ---
    private val morseCodeMap = mapOf(
        'A' to ".-",    'B' to "-...",  'C' to "-.-.",  'D' to "-..",
        'E' to ".",     'F' to "..-.",  'G' to "--.",   'H' to "....",
        'I' to "..",    'J' to ".---",  'K' to "-.-",   'L' to ".-..",
        'M' to "--",    'N' to "-.",    'O' to "---",   'P' to ".--.",
        'Q' to "--.-",  'R' to ".-.",   'S' to "...",   'T' to "-",
        'U' to "..-",   'V' to "...-",  'W' to ".--",   'X' to "-..-",
        'Y' to "-.--",  'Z' to "--..",
        '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..",
        '9' to "----.", '0' to "-----",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.",
        '=' to "-...-",  '+' to ".-.-.",  '-' to "-....-"
    )

    // --- GUI Components ---
    private val inputArea = JTextArea(5, 35)
    private val morseDisplay = JLabel("READY", SwingConstants.CENTER)
    private val wpmLabel = JLabel("Speed: 20 WPM")
    private lateinit var playButton: JButton

    init {
        title = "Desktop Morse Code Audio Keyer"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false
        layout = BorderLayout()

        // Create Layout Panels
        val topToolbar = createTopToolbar()
        val centerPanel = createCenterPanel()
        val bottomControl = createBottomPanel()

        add(topToolbar, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        add(bottomControl, BorderLayout.SOUTH)

        // Ensure audio line closes on window shutdown
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                stopPlayback()
            }
        })

        pack()
        setLocationRelativeTo(null)
    }

    /**
     * Top Bar: Speed Slider & Preset Messages.
     */
    private fun createTopToolbar(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 10)).apply {
            background = Color(35, 35, 40)
        }

        wpmLabel.foreground = Color.WHITE
        wpmLabel.font = Font("SansSerif", Font.BOLD, 12)

        val wpmSlider = JSlider(JSlider.HORIZONTAL, 5, 45, wpm).apply {
            background = Color(35, 35, 40)
            preferredSize = Dimension(160, 25)
            isFocusable = false
            addChangeListener {
                wpm = value
                wpmLabel.text = "Speed: $wpm WPM"
            }
        }

        // Preset Selector
        val presets = arrayOf("Presets...", "CQ CQ CQ", "SOS SOS SOS", "HELLO WORLD", "73 DE KOTLIN")
        val presetBox = JComboBox(presets).apply {
            isFocusable = false
            addActionListener {
                if (selectedIndex > 0) {
                    inputArea.text = selectedItem as String
                }
            }
        }

        panel.add(wpmLabel)
        panel.add(wpmSlider)
        panel.add(Box.createHorizontalStrut(15))
        panel.add(JLabel("Sample Text: ").apply { foreground = Color.WHITE })
        panel.add(presetBox)

        return panel
    }

    /**
     * Center Panel: Input Text Area & Large Active Morse Visualizer Display.
     */
    private fun createCenterPanel(): JPanel {
        val panel = JPanel(BorderLayout(10, 10)).apply {
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
            background = Color(20, 20, 25)
        }

        // Text Input Area
        inputArea.apply {
            font = Font("Monospaced", Font.BOLD, 16)
            text = "CQ CQ CQ DE KOTLIN MORSE KEYER"
            lineWrap = true
            wrapStyleWord = true
            margin = Insets(8, 8, 8, 8)
        }
        val scrollPane = JScrollPane(inputArea).apply {
            border = BorderFactory.createTitledBorder("Input Text String")
        }

        // Live Morse Output Monitor Display
        morseDisplay.apply {
            font = Font("Monospaced", Font.BOLD, 28)
            foreground = Color(0, 230, 118) // Bright Cyan/Green
            preferredSize = Dimension(400, 60)
            isOpaque = true
            background = Color(10, 10, 12)
            border = BorderFactory.createLineBorder(Color(50, 50, 55), 2)
        }

        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(morseDisplay, BorderLayout.SOUTH)

        return panel
    }

    /**
     * Bottom Control Panel: Play/Stop & Clear Controls.
     */
    private fun createBottomPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 12)).apply {
            background = Color(35, 35, 40)
        }

        playButton = JButton("PLAY CODE").apply {
            font = Font("SansSerif", Font.BOLD, 14)
            background = Color(76, 175, 80)
            foreground = Color.WHITE
            preferredSize = Dimension(140, 38)
            isFocusPainted = false
            addActionListener {
                if (isPlaying) stopPlayback() else startPlayback()
            }
        }

        val clearButton = JButton("Clear Text").apply {
            font = Font("SansSerif", Font.PLAIN, 12)
            preferredSize = Dimension(100, 38)
            isFocusPainted = false
            addActionListener {
                if (!isPlaying) {
                    inputArea.text = ""
                    morseDisplay.text = "READY"
                }
            }
        }

        panel.add(playButton)
        panel.add(clearButton)

        return panel
    }

    /**
     * Initiates real-time background playback loop.
     */
    @Synchronized
    private fun startPlayback() {
        val text = inputArea.text.trim()
        if (text.isEmpty() || isPlaying) return

        isPlaying = true
        playButton.text = "STOP"
        playButton.background = Color(244, 67, 54)

        playbackThread = Thread {
            try {
                // Open PCM Audio Line
                val format = AudioFormat(sampleRate, 16, 1, true, false)
                audioLine = AudioSystem.getSourceDataLine(format)
                audioLine?.open(format, 4096)
                audioLine?.start()

                val highlighter = inputArea.highlighter
                val painter = DefaultHighlighter.DefaultHighlightPainter(Color(255, 235, 59)) // Yellow Highlight

                for (i in text.indices) {
                    if (!isPlaying) break

                    val char = text[i]
                    val upperChar = char.uppercaseChar()

                    // Highlight Active Character in Input Area
                    SwingUtilities.invokeLater {
                        highlighter.removeAllHighlights()
                        highlighter.addHighlight(i, i + 1, painter)
                    }

                    // Calculate PARIS Standard timing unit in milliseconds
                    val dotUnitMs = 1200 / wpm

                    if (char == ' ') {
                        // Word Gap = 7 dot units
                        SwingUtilities.invokeLater { morseDisplay.text = "[ SPACE ]" }
                        playSilence(dotUnitMs * 7)
                    } else {
                        val morseCode = morseCodeMap[upperChar]
                        if (morseCode != null) {
                            SwingUtilities.invokeLater {
                                morseDisplay.text = "$upperChar  :  $morseCode"
                            }

                            for (symbol in morseCode) {
                                if (!isPlaying) break

                                if (symbol == '.') {
                                    playTone(dotUnitMs)        // Dot = 1 unit
                                } else if (symbol == '-') {
                                    playTone(dotUnitMs * 3)    // Dash = 3 units
                                }

                                // Intra-character gap = 1 unit silence
                                playSilence(dotUnitMs)
                            }
                            // Inter-character gap = 3 units (1 played above + 2 additional)
                            playSilence(dotUnitMs * 2)
                        } else {
                            // Unmapped character delay
                            playSilence(dotUnitMs * 2)
                        }
                    }
                }

                audioLine?.drain()
                audioLine?.stop()
                audioLine?.close()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                SwingUtilities.invokeLater { resetUI() }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    private fun stopPlayback() {
        isPlaying = false
        audioLine?.stop()
        playbackThread?.interrupt()
        resetUI()
    }

    private fun resetUI() {
        isPlaying = false
        playButton.text = "PLAY CODE"
        playButton.background = Color(76, 175, 80)
        morseDisplay.text = "READY"
        inputArea.highlighter.removeAllHighlights()
    }

    /**
     * Synthesizes smooth 700Hz sine-wave beep audio samples.
     */
    private fun playTone(durationMs: Int) {
        if (!isPlaying) return
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ByteArray(numSamples * 2)

        // 4ms Ramp Envelope to eliminate audio clicks
        val rampSamples = (sampleRate * 0.004).toInt()

        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i * toneFrequency / sampleRate
            var sample = sin(angle)

            // Fade-in & Fade-out envelope
            if (i < rampSamples) {
                sample *= (i.toDouble() / rampSamples)
            } else if (i > numSamples - rampSamples) {
                sample *= ((numSamples - i).toDouble() / rampSamples)
            }

            val scaled = (sample * 28000.0).toInt().coerceIn(-32768, 32767)
            buffer[2 * i] = (scaled and 0xFF).toByte()
            buffer[2 * i + 1] = ((scaled shr 8) and 0xFF).toByte()
        }

        audioLine?.write(buffer, 0, buffer.size)
    }

    /**
     * Outputs silent PCM frames to preserve precise timing alignment.
     */
    private fun playSilence(durationMs: Int) {
        if (!isPlaying) return
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ByteArray(numSamples * 2) // Zero filled
        audioLine?.write(buffer, 0, buffer.size)
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val keyer = MorseKeyer()
        keyer.isVisible = true
    }
}