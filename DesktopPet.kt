import java.awt.*
import java.awt.event.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.util.Random
import javax.swing.*
import kotlin.math.*

// --- Enums & Data Classes ---
enum class Expression {
    HAPPY, BLINK, EXCITED, SLEEPY, REMINDER
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1.0f,
    val text: String = "♥",
    val color: Color = Color(255, 120, 160)
)

// --- Main Desktop Pet Canvas ---
class PetCanvas(private val parentWindow: JFrame) : JPanel() {
    private val random = Random()

    // Pet Physics & State Variables
    private var petYOffset = 0.0f
    private var petVelocityY = 0.0f
    private var isGrounded = true
    private var earWiggle = 0.0f

    var expression = Expression.HAPPY
    private var blinkTimer = 0

    // Speech Bubble State
    private var speechText: String? = "Hi! I'm Mochi! ✨"
    private var speechAlpha = 1.0f
    private var speechDisplayTicks = 180 // ~3 seconds at 60 FPS

    // Particles
    private val particles = mutableListOf<Particle>()

    // Idle Movement Wave
    private var idleTick = 0f

    init {
        isOpaque = false
        preferredSize = Dimension(240, 260)
    }

    fun triggerJump() {
        if (isGrounded) {
            petVelocityY = -11.0f
            isGrounded = false
            expression = Expression.EXCITED
            spawnParticles(6)
            sayQuote(getRandomInteractionQuote())
        }
    }

    fun triggerBreakReminder(message: String) {
        expression = Expression.REMINDER
        petVelocityY = -7.0f
        isGrounded = false
        sayQuote(message, durationTicks = 400)
        spawnParticles(10)
    }

    fun sayQuote(text: String, durationTicks: Int = 220) {
        speechText = text
        speechAlpha = 1.0f
        speechDisplayTicks = durationTicks
    }

    private fun spawnParticles(count: Int) {
        val symbols = listOf("♥", "✨", "⭐", "💧", "🌸")
        val colors = listOf(
            Color(255, 100, 150),
            Color(255, 215, 0),
            Color(120, 220, 255),
            Color(200, 140, 255)
        )
        for (i in 0 until count) {
            particles.add(
                Particle(
                    x = width / 2.0f + random.nextInt(40) - 20,
                    y = height - 90.0f,
                    vx = (random.nextFloat() - 0.5f) * 4.0f,
                    vy = -3.0f - random.nextFloat() * 4.0f,
                    text = symbols[random.nextInt(symbols.size)],
                    color = colors[random.nextInt(colors.size)]
                )
            )
        }
    }

    // --- Animation & Physics Loop Step ---
    fun updatePhysics() {
        idleTick += 0.05f

        // Gravity & Jump Physics
        if (!isGrounded) {
            petYOffset += petVelocityY
            petVelocityY += 0.65f // Gravity
            earWiggle = sin(petYOffset * 0.2f) * 12f

            if (petYOffset >= 0.0f) {
                petYOffset = 0.0f
                petVelocityY = 0.0f
                isGrounded = true
                earWiggle = 0.0f
                if (expression == Expression.EXCITED) {
                    expression = Expression.HAPPY
                }
            }
        }

        // Random Blinking Cycle
        blinkTimer++
        if (blinkTimer > 180 + random.nextInt(120)) {
            if (expression == Expression.HAPPY) {
                expression = Expression.BLINK
            }
            if (blinkTimer > 200 + random.nextInt(120)) {
                if (expression == Expression.BLINK) {
                    expression = Expression.HAPPY
                }
                blinkTimer = 0
            }
        }

        // Speech Bubble Fade Out
        if (speechDisplayTicks > 0) {
            speechDisplayTicks--
            if (speechDisplayTicks < 30) {
                speechAlpha = (speechDisplayTicks / 30.0f).coerceIn(0.0f, 1.0f)
            }
        } else {
            speechText = null
        }

        // Particle System Physics
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 0.02f
            if (p.alpha <= 0.0f) iterator.remove()
        }

        repaint()
    }

    // --- Procedural Vector Rendering Engine ---
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D

        // High Quality Rendering Hints
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val cx = width / 2.0
        val floatOffset = sin(idleTick.toDouble()).toFloat() * 3.0f
        val baseY = height - 70.0 + petYOffset + floatOffset

        // 1. Draw Speech Bubble (Fixed Kotlin standard call: isNullOrEmpty)
        if (!speechText.isNullOrEmpty() && speechAlpha > 0.01f) {
            drawSpeechBubble(g2, cx, baseY - 65, speechText!!)
        }

        // 2. Draw Vector Pet Mascot ("Mochi")
        drawPetBody(g2, cx, baseY)

        // 3. Draw Floating Particles
        drawParticles(g2)

        g2.dispose()
    }

    private fun drawSpeechBubble(g2: Graphics2D, cx: Double, topY: Double, text: String) {
        g2.font = Font("Segoe UI", Font.BOLD, 12)
        val fm = g2.fontMetrics
        val padding = 12
        val textWidth = fm.stringWidth(text)
        val bubbleWidth = textWidth + padding * 2
        val bubbleHeight = 32
        val bx = (cx - bubbleWidth / 2.0).coerceAtLeast(10.0)
        val by = topY - bubbleHeight

        val alphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, speechAlpha.coerceIn(0.0f, 1.0f))
        g2.composite = alphaComposite

        // Bubble Shadow & Background
        g2.color = Color(30, 32, 48, 220)
        val rect = RoundRectangle2D.Double(bx, by, bubbleWidth.toDouble(), bubbleHeight.toDouble(), 16.0, 16.0)
        g2.fill(rect)

        g2.color = Color(137, 180, 250, (255 * speechAlpha).toInt().coerceIn(0, 255))
        g2.stroke = BasicStroke(1.5f)
        g2.draw(rect)

        // Bubble Pointer Triangle
        val pointer = Path2D.Double()
        pointer.moveTo(cx - 5, by + bubbleHeight)
        pointer.lineTo(cx + 5, by + bubbleHeight)
        pointer.lineTo(cx, by + bubbleHeight + 6)
        pointer.closePath()
        g2.fill(pointer)

        // Bubble Text
        g2.color = Color(205, 214, 244, (255 * speechAlpha).toInt().coerceIn(0, 255))
        g2.drawString(text, (bx + padding).toFloat(), (by + 20).toFloat())
    }

    private fun drawPetBody(g2: Graphics2D, cx: Double, baseY: Double) {
        // Shadow on Desktop
        val shadowWidth = 70.0 - (petYOffset * 0.3).coerceAtMost(30.0)
        g2.color = Color(0, 0, 0, (60 + (petYOffset * 1.5)).toInt().coerceIn(10, 80))
        g2.fill(Ellipse2D.Double(cx - shadowWidth / 2, baseY + 32, shadowWidth, 12.0))

        // Body Gradient Colors (Soft Cream Pink Aesthetic)
        val bodyWidth = 80.0
        val bodyHeight = 70.0
        val bx = cx - bodyWidth / 2
        val by = baseY - bodyHeight + 35

        // Ears
        g2.color = Color(255, 205, 210)
        val leftEar = Path2D.Double()
        leftEar.moveTo(cx - 28, by + 10)
        leftEar.quadTo(cx - 40 - earWiggle, by - 25, cx - 12, by + 2)
        leftEar.closePath()
        g2.fill(leftEar)

        val rightEar = Path2D.Double()
        rightEar.moveTo(cx + 28, by + 10)
        rightEar.quadTo(cx + 40 + earWiggle, by - 25, cx + 12, by + 2)
        rightEar.closePath()
        g2.fill(rightEar)

        // Main Body Oval
        val gradient = GradientPaint(
            cx.toFloat(), by.toFloat(), Color(255, 240, 245),
            cx.toFloat(), (by + bodyHeight).toFloat(), Color(255, 215, 225)
        )
        g2.paint = gradient
        val bodyShape = Ellipse2D.Double(bx, by, bodyWidth, bodyHeight)
        g2.fill(bodyShape)

        // Outline
        g2.color = Color(230, 170, 185)
        g2.stroke = BasicStroke(2.0f)
        g2.draw(bodyShape)

        // Cute Rosy Cheeks
        g2.color = Color(255, 140, 170, 180)
        g2.fill(Ellipse2D.Double(cx - 30, by + 34, 12.0, 7.0))
        g2.fill(Ellipse2D.Double(cx + 18, by + 34, 12.0, 7.0))

        // Eyes & Expression Logic
        g2.color = Color(40, 40, 60)
        val eyeY = by + 26

        when (expression) {
            Expression.HAPPY -> {
                g2.fill(Ellipse2D.Double(cx - 22, eyeY, 10.0, 12.0))
                g2.fill(Ellipse2D.Double(cx + 12, eyeY, 10.0, 12.0))
                // Eye Shine
                g2.color = Color.WHITE
                g2.fill(Ellipse2D.Double(cx - 20, eyeY + 2, 4.0, 4.0))
                g2.fill(Ellipse2D.Double(cx + 14, eyeY + 2, 4.0, 4.0))
            }
            Expression.BLINK -> {
                g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.drawLine((cx - 22).toInt(), (eyeY + 6).toInt(), (cx - 12).toInt(), (eyeY + 6).toInt())
                g2.drawLine((cx + 12).toInt(), (eyeY + 6).toInt(), (cx + 22).toInt(), (eyeY + 6).toInt())
            }
            Expression.EXCITED -> {
                // Curved ^ ^ eyes
                g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val leftArc = Path2D.Double()
                leftArc.moveTo(cx - 24, eyeY + 8)
                leftArc.quadTo(cx - 17, eyeY, cx - 10, eyeY + 8)
                g2.draw(leftArc)

                val rightArc = Path2D.Double()
                rightArc.moveTo(cx + 10, eyeY + 8)
                rightArc.quadTo(cx + 17, eyeY, cx + 24, eyeY + 8)
                g2.draw(rightArc)
            }
            Expression.REMINDER, Expression.SLEEPY -> {
                // Wide surprise / focused eyes
                g2.fill(Ellipse2D.Double(cx - 23, eyeY - 2, 12.0, 14.0))
                g2.fill(Ellipse2D.Double(cx + 11, eyeY - 2, 12.0, 14.0))
                g2.color = Color.WHITE
                g2.fill(Ellipse2D.Double(cx - 20, eyeY, 5.0, 5.0))
                g2.fill(Ellipse2D.Double(cx + 14, eyeY, 5.0, 5.0))
            }
        }

        // Mouth (Cute 'w' shape)
        g2.color = Color(60, 50, 70)
        g2.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val mouth = Path2D.Double()
        mouth.moveTo(cx - 6, by + 40)
        mouth.quadTo(cx - 3, by + 44, cx, by + 40)
        mouth.quadTo(cx + 3, by + 44, cx + 6, by + 40)
        g2.draw(mouth)
    }

    private fun drawParticles(g2: Graphics2D) {
        for (p in particles) {
            val alpha = p.alpha.coerceIn(0.0f, 1.0f)
            g2.color = Color(p.color.red, p.color.green, p.color.blue, (255 * alpha).toInt())
            g2.font = Font("Segoe UI Emoji", Font.BOLD, 14)
            g2.drawString(p.text, p.x, p.y)
        }
    }

    private fun getRandomInteractionQuote(): String {
        val quotes = listOf(
            "Yay! Pet me again! ✨",
            "Boop! 🌸",
            "You're doing great today! 💖",
            "Remember to stay hydrated! 💧",
            "Keep up the awesome work! ⭐",
            "Don't forget to smile! 😊"
        )
        return quotes[random.nextInt(quotes.size)]
    }
}

// --- Main Desktop Pet Frame ---
class DesktopPetWindow : JFrame() {
    private val canvas = PetCanvas(this)
    private var initialClick: Point? = null

    // 45 Minute Break Timer Mechanics
    private val breakIntervalMs = 45 * 60 * 1000L // 45 Minutes
    private var lastBreakTime = System.currentTimeMillis()

    private val breakTips = listOf(
        "Time for a 45m Stretch Break! 🧘",
        "Drink a fresh glass of water! 💧",
        "Rest your eyes: Look 20ft away! 👁️",
        "Roll your shoulders & relax! ✨",
        "Take 3 deep breath cycles... 🌬️"
    )
    private var tipIndex = 0

    init {
        title = "Desktop Pet Companion"
        isUndecorated = true
        isAlwaysOnTop = true
        type = Type.UTILITY // Keeps window floating off taskbar focus
        background = Color(0, 0, 0, 0) // Fully transparent background

        contentPane = canvas
        pack()

        // Position window in bottom-right corner of screen
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        setLocation(screenSize.width - width - 40, screenSize.height - height - 60)

        // Mouse Drag & Click Handlers
        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    initialClick = e.point as Point?
                    canvas.triggerJump()
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e.point)
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && initialClick != null) {
                    val curr = location
                    val xMoved = e.xOnScreen - (curr.x + initialClick!!.x)
                    val yMoved = e.yOnScreen - (curr.y + initialClick!!.y)
                    setLocation(curr.x + xMoved, curr.y + yMoved)
                }
            }
        }

        canvas.addMouseListener(mouseAdapter)
        canvas.addMouseMotionListener(mouseAdapter)

        // Start 60 FPS Render Physics Loop
        Timer(16) {
            canvas.updatePhysics()
            checkBreakTimer()
        }.start()

        // Start Async Random Behavior / Idle Movement Loop
        Timer(4000) {
            if (Math.random() < 0.3) {
                // Subtle horizontal step movement on screen
                val shift = if (Math.random() > 0.5) 8 else -8
                val newX = (location.x + shift).coerceIn(10, screenSize.width - width - 10)
                setLocation(newX, location.y)
            }
        }.start()
    }

    private fun checkBreakTimer() {
        val now = System.currentTimeMillis()
        if (now - lastBreakTime >= breakIntervalMs) {
            triggerStretchBreak()
        }
    }

    fun triggerStretchBreak() {
        lastBreakTime = System.currentTimeMillis()
        val tip = breakTips[tipIndex % breakTips.size]
        tipIndex++
        canvas.triggerBreakReminder(tip)
    }

    private fun showContextMenu(point: java.awt.Point) {
        val menu = JPopupMenu()

        val breakItem = JMenuItem("🧘 Trigger Stretch Break Now")
        breakItem.addActionListener { triggerStretchBreak() }

        val minsLeft = maxOf(0, 45 - ((System.currentTimeMillis() - lastBreakTime) / 60000))
        val timerItem = JMenuItem("⏱ Next Break in: $minsLeft mins")
        timerItem.isEnabled = false

        val exitItem = JMenuItem("✕ Goodbye (Exit)")
        exitItem.addActionListener { System.exit(0) }

        menu.add(timerItem)
        menu.add(breakItem)
        menu.addSeparator()
        menu.add(exitItem)

        menu.show(canvas, point.x, point.y)
    }
}

// --- Main Application Entry Point ---
fun main() {
    SwingUtilities.invokeLater {
        try {
            System.setProperty("sun.java2d.noddraw", "true")
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {}

        val petApp = DesktopPetWindow()
        petApp.isVisible = true
    }
}