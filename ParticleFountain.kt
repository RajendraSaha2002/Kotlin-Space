import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Vector Video Particle Fountain Renderer
 * High-performance 60 FPS particle engine with alpha-blended motion blur video trails.
 */
class ParticleFountain : JFrame("Vector Video Particle Fountain Renderer") {

    // --- Physics Controls State ---
    @Volatile private var gravity = 450.0f       // Downward gravity force (px/s²)
    @Volatile private var wind = 0.0f            // Horizontal wind force (px/s²)
    @Volatile private var emissionRate = 250     // Particles emitted per frame
    @Volatile private var initialSpeed = 550.0f  // Fountain velocity magnitude
    @Volatile private var trailBlurAlpha = 0.15f // Motion blur trail fade opacity (5% to 50%)

    // --- Particle Pool ---
    private val maxParticles = 20_000
    private val particles = Array(maxParticles) { Particle() }

    // --- Emitter Origin ---
    private var customEmitterPos: Point? = null

    // --- Engine Thread ---
    @Volatile private var isRunning = false
    private var renderThread: Thread? = null
    private var currentFps = 60
    private var activeParticleCount = 0

    // --- GUI Components ---
    private val canvasPanel = ParticleCanvas()
    private val fpsLabel = JLabel("FPS: 60 | Particles: 0")

    // Fullscreen Toggle
    private var isFullscreen = false
    private val graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice

    init {
        title = "Vector Video Particle Fountain Renderer"
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(1100, 750)
        layout = BorderLayout()

        // Create Canvas & Slider Control Panel
        val bottomControls = createControlsPanel()

        add(canvasPanel, BorderLayout.CENTER)
        add(bottomControls, BorderLayout.SOUTH)

        setupMouseEmitter()

        pack()
        setLocationRelativeTo(null)

        startEngine()
    }

    /**
     * Particle Struct with Object Recycling (Zero GC allocations in game loop).
     */
    class Particle {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var life = 0f
        var maxLife = 1f
        var hue = 0f
        var size = 4f
        var active = false

        fun spawn(startX: Float, startY: Float, initVx: Float, initVy: Float, initLife: Float, initHue: Float, initSize: Float) {
            x = startX
            y = startY
            vx = initVx
            vy = initVy
            life = initLife
            maxLife = initLife
            hue = initHue
            size = initSize
            active = true
        }

        fun update(dt: Float, gravity: Float, wind: Float): Boolean {
            if (!active) return false
            vx += wind * dt
            vy += gravity * dt
            x += vx * dt
            y += vy * dt
            life -= dt

            if (life <= 0f) {
                active = false
            }
            return active
        }
    }

    /**
     * Starts the high-performance 60 FPS update and render loop thread.
     */
    private fun startEngine() {
        isRunning = true
        renderThread = Thread {
            var lastTime = System.nanoTime()
            var fpsTimer = System.currentTimeMillis()
            var frames = 0

            val targetNs = 1_000_000_000L / 60

            while (isRunning) {
                val now = System.nanoTime()
                val delta = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
                lastTime = now

                // 1. Emit & Update Physics
                emitParticles()
                updatePhysics(delta)

                // 2. Render Frame
                canvasPanel.renderFrame(particles, trailBlurAlpha, activeParticleCount, currentFps)

                frames++
                if (System.currentTimeMillis() - fpsTimer >= 1000) {
                    currentFps = frames
                    frames = 0
                    fpsTimer += 1000
                }

                // 3. Sleep to maintain 60 FPS
                val elapsedNs = System.nanoTime() - now
                val sleepNs = targetNs - elapsedNs
                if (sleepNs > 0) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000, (sleepNs % 1_000_000).toInt())
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    /**
     * Emits new vector particles into the pre-allocated pool.
     */
    private fun emitParticles() {
        val w = canvasPanel.width.coerceAtLeast(100)
        val h = canvasPanel.height.coerceAtLeast(100)

        // Emitter origin: Mouse location if dragged, else bottom-center
        val originX = customEmitterPos?.x?.toFloat() ?: (w / 2.0f)
        val originY = customEmitterPos?.y?.toFloat() ?: (h - 30.0f)

        var spawned = 0
        var poolIdx = 0

        val baseHue = (System.currentTimeMillis() % 10000) / 10000.0f

        while (spawned < emissionRate && poolIdx < maxParticles) {
            val p = particles[poolIdx]
            if (!p.active) {
                // Cone angle spread: -65 deg to -115 deg (Upwards cone)
                val angle = Math.toRadians(-90.0 + Random.nextDouble(-30.0, 30.0)).toFloat()
                val speed = initialSpeed * Random.nextFloat().coerceAtLeast(0.3f)

                val vx = cos(angle) * speed
                val vy = sin(angle) * speed
                val life = Random.nextFloat() * 2.2f + 1.2f
                val hue = (baseHue + Random.nextFloat() * 0.25f) % 1.0f
                val size = Random.nextFloat() * 5.0f + 2.0f

                p.spawn(originX, originY, vx, vy, life, hue, size)
                spawned++
            }
            poolIdx++
        }
    }

    /**
     * Step physics for all active particles.
     */
    private fun updatePhysics(dt: Float) {
        var activeCount = 0
        val currentGravity = gravity
        val currentWind = wind

        for (i in 0 until maxParticles) {
            val p = particles[i]
            if (p.active) {
                if (p.update(dt, currentGravity, currentWind)) {
                    activeCount++
                }
            }
        }
        activeParticleCount = activeCount
    }

    /**
     * Allows user to move fountain emitter origin by clicking/dragging the mouse.
     */
    private fun setupMouseEmitter() {
        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                customEmitterPos = e.point as Point?
            }
            override fun mouseDragged(e: MouseEvent) {
                customEmitterPos = e.point as Point?
            }
            override fun mouseReleased(e: MouseEvent) {
                customEmitterPos = null // Reset back to bottom-center
            }
        }
        canvasPanel.addMouseListener(mouseAdapter)
        canvasPanel.addMouseMotionListener(mouseAdapter)
    }

    /**
     * Bottom Controls Panel with Sliders for Gravity, Wind, Emission Rate, Speed, and Blur.
     */
    private fun createControlsPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            background = Color(25, 25, 30)
            border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
        }

        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 8, 4, 8)
            fill = GridBagConstraints.HORIZONTAL
        }

        val labelColor = Color.WHITE

        // 1. Gravity Slider
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("Gravity:").apply { foreground = labelColor }, gbc)

        gbc.gridx = 1
        val gravSlider = JSlider(JSlider.HORIZONTAL, -200, 1200, gravity.toInt()).apply {
            background = Color(25, 25, 30)
            preferredSize = Dimension(140, 25)
            addChangeListener { gravity = value.toFloat() }
        }
        panel.add(gravSlider, gbc)

        // 2. Wind Slider
        gbc.gridx = 2
        panel.add(JLabel("Wind Force:").apply { foreground = labelColor }, gbc)

        gbc.gridx = 3
        val windSlider = JSlider(JSlider.HORIZONTAL, -800, 800, wind.toInt()).apply {
            background = Color(25, 25, 30)
            preferredSize = Dimension(140, 25)
            addChangeListener { wind = value.toFloat() }
        }
        panel.add(windSlider, gbc)

        // 3. Emission Rate Slider
        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JLabel("Emission Rate:").apply { foreground = labelColor }, gbc)

        gbc.gridx = 1
        val emitSlider = JSlider(JSlider.HORIZONTAL, 20, 600, emissionRate).apply {
            background = Color(25, 25, 30)
            preferredSize = Dimension(140, 25)
            addChangeListener { emissionRate = value }
        }
        panel.add(emitSlider, gbc)

        // 4. Trail Blur Slider
        gbc.gridx = 2
        panel.add(JLabel("Motion Blur Trail:").apply { foreground = labelColor }, gbc)

        gbc.gridx = 3
        val blurSlider = JSlider(JSlider.HORIZONTAL, 3, 50, (trailBlurAlpha * 100).toInt()).apply {
            background = Color(25, 25, 30)
            preferredSize = Dimension(140, 25)
            addChangeListener { trailBlurAlpha = value / 100.0f }
        }
        panel.add(blurSlider, gbc)

        // 5. Fullscreen Toggle Button
        gbc.gridx = 4; gbc.gridy = 0; gbc.gridheight = 2
        val fullBtn = JButton("Toggle Fullscreen").apply {
            isFocusPainted = false
            addActionListener { toggleFullscreen() }
        }
        panel.add(fullBtn, gbc)

        return panel
    }

    private fun toggleFullscreen() {
        dispose()
        isFullscreen = !isFullscreen
        isUndecorated = isFullscreen

        if (isFullscreen) {
            graphicsDevice.fullScreenWindow = this
        } else {
            graphicsDevice.fullScreenWindow = null
            isVisible = true
        }
    }

    /**
     * Inner Component: Custom Offscreen Double-Buffered Canvas with Alpha-Blending Trail Renderer.
     */
    private inner class ParticleCanvas : JPanel() {
        private var offscreenImage: BufferedImage? = null
        private var bufferG2d: Graphics2D? = null

        init {
            background = Color.BLACK
        }

        fun renderFrame(particles: Array<Particle>, blurAlpha: Float, activeCount: Int, fps: Int) {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return

            // Prepare or resize buffer
            if (offscreenImage == null || offscreenImage?.width != w || offscreenImage?.height != h) {
                offscreenImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                bufferG2d = offscreenImage?.createGraphics()?.apply {
                    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    color = Color.BLACK
                    fillRect(0, 0, w, h)
                }
            }

            val g2 = bufferG2d ?: return

            // 1. Motion Blur Effect: Draw semi-transparent black overlay before particle render
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, blurAlpha)
            g2.color = Color.BLACK
            g2.fillRect(0, 0, w, h)

            // 2. Render Active Particles
            g2.composite = AlphaComposite.SrcOver
            for (i in particles.indices) {
                val p = particles[i]
                if (p.active) {
                    val lifeRatio = (p.life / p.maxLife).coerceIn(0f, 1f)
                    val baseColor = Color.getHSBColor(p.hue, 0.9f, 1.0f)

                    // Fade out color alpha as life decreases
                    g2.color = Color(baseColor.red, baseColor.green, baseColor.blue, (lifeRatio * 255).toInt())

                    val currentSize = p.size * (0.4f + 0.6f * lifeRatio)
                    val drawX = (p.x - currentSize / 2f).toInt()
                    val drawY = (p.y - currentSize / 2f).toInt()
                    val sz = currentSize.toInt().coerceAtLeast(1)

                    g2.fillOval(drawX, drawY, sz, sz)
                }
            }

            // 3. Render FPS & Particle Count Overlay
            g2.color = Color(0, 230, 118)
            g2.font = Font("Monospaced", Font.BOLD, 13)
            g2.drawString("FPS: $fps | Active Particles: $activeCount", 15, 25)
            g2.drawString("Tip: Click & Drag mouse to move emitter", 15, 42)

            // Trigger repaint on Swing EDT
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (offscreenImage != null) {
                g.drawImage(offscreenImage, 0, 0, null)
            }
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val renderer = ParticleFountain()
        renderer.isVisible = true
    }
}