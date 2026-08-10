package sumoarena

import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.util.Random
import javax.swing.*
import kotlin.math.*

// =============================================================================
// ENGINE CONSTANTS & ARENA CONFIGURATION
// =============================================================================
const val WINDOW_WIDTH = 900
const val WINDOW_HEIGHT = 650
const val FPS = 60

const val DOHYO_CENTER_X = 450.0
const val DOHYO_CENTER_Y = 325.0
const val DOHYO_RADIUS = 220.0

const val FRICTION = 0.935        // Surface friction dampening
const val ACCEL_FORCE = 0.65       // Continuous keyboard acceleration
const val DASH_FORCE = 9.5         // Tachi-ai dash impulse boost
const val RESTITUTION = 0.85       // Elastic collision bounciness factor

// =============================================================================
// DUST & SPARK PARTICLE SYSTEM
// =============================================================================
data class SumoParticle(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    var life: Int,
    val maxLife: Int,
    val color: Color,
    val size: Float
)

// =============================================================================
// SUMO RIKISHI (PLAYER) PHYSICS CLASS
// =============================================================================
class SumoRikishi(
    val id: Int,
    var x: Double,
    var y: Double,
    val radius: Double = 28.0,
    val mass: Double = 1.0,
    val primaryColor: Color,
    val beltColor: Color
) {
    var vx: Double = 0.0
    var vy: Double = 0.0
    var facingAngle: Double = 0.0 // Radians

    var stamina: Double = 100.0
    val maxStamina: Double = 100.0
    var dashCooldown: Int = 0

    var wins: Int = 0
    var isDashing: Boolean = false
    var dashTrailTimer: Int = 0

    fun resetPosition(startX: Double, startY: Double, targetAngle: Double) {
        x = startX
        y = startY
        vx = 0.0
        vy = 0.0
        stamina = maxStamina
        dashCooldown = 0
        facingAngle = targetAngle
        isDashing = false
    }

    fun applyForce(fx: Double, fy: Double) {
        vx += fx / mass
        vy += fy / mass

        // Update facing angle based on motion direction
        if (abs(vx) > 0.1 || abs(vy) > 0.1) {
            facingAngle = atan2(vy, vx)
        }
    }

    fun performDash(): Boolean {
        if (dashCooldown == 0 && stamina >= 30.0) {
            stamina -= 30.0
            dashCooldown = 45 // 0.75s cooldown at 60 FPS
            isDashing = true
            dashTrailTimer = 8

            // Apply forward impulse vector based on current facing direction
            vx += cos(facingAngle) * DASH_FORCE
            vy += sin(facingAngle) * DASH_FORCE
            return true
        }
        return false
    }

    fun update() {
        // Integrate Physics Position
        x += vx
        y += vy

        // Apply Ground Surface Friction
        vx *= FRICTION
        vy *= FRICTION

        // Cooldowns & Stamina Regeneration
        if (dashCooldown > 0) dashCooldown--
        if (dashTrailTimer > 0) dashTrailTimer-- else isDashing = false

        if (stamina < maxStamina) {
            stamina = (stamina + 0.35).coerceAtMost(maxStamina)
        }
    }

    fun isRingOut(): Boolean {
        val distFromCenter = hypot(x - DOHYO_CENTER_X, y - DOHYO_CENTER_Y)
        // Ring-out occurs when the Rikishi's body center crosses outside the Dohyo perimeter
        return (distFromCenter + radius * 0.5) > DOHYO_RADIUS
    }

    fun render(g2: Graphics2D) {
        // Dash Aura Effect
        if (isDashing) {
            g2.color = Color(255, 200, 50, 120)
            g2.fill(Ellipse2D.Double(x - radius - 6, y - radius - 6, (radius + 6) * 2, (radius + 6) * 2))
        }

        // Main Rikishi Body (Circle)
        g2.color = primaryColor
        g2.fill(Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2))

        // Body Outline
        g2.color = Color.BLACK
        g2.stroke = BasicStroke(2.5f)
        g2.draw(Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2))

        // Mawashi Belt (Top-Down Visual)
        g2.color = beltColor
        g2.fill(Ellipse2D.Double(x - radius * 0.65, y - radius * 0.65, radius * 1.3, radius * 1.3))

        // Directional Indicator Line (Facing Vector)
        val noseX = x + cos(facingAngle) * radius
        val noseY = y + sin(facingAngle) * radius
        g2.color = Color.WHITE
        g2.stroke = BasicStroke(3.5f)
        g2.draw(Line2D.Double(x, y, noseX, noseY))

        // Topknot (Monomai Node)
        g2.color = Color(30, 30, 30)
        g2.fill(Ellipse2D.Double(x - 5.0, y - 5.0, 10.0, 10.0))
    }
}

// =============================================================================
// MAIN GAME CANVAS & PHYSICS ENGINE PANEL
// =============================================================================
class SumoArenaPanel : JPanel(), KeyListener, Runnable {

    enum class GameState { READY, FIGHTING, ROUND_OVER, MATCH_OVER }

    private val p1 = SumoRikishi(1, DOHYO_CENTER_X - 110.0, DOHYO_CENTER_Y, primaryColor = Color(240, 190, 140), beltColor = Color(30, 90, 200)) // Blue Mawashi
    private val p2 = SumoRikishi(2, DOHYO_CENTER_X + 110.0, DOHYO_CENTER_Y, primaryColor = Color(240, 190, 140), beltColor = Color(200, 30, 30))  // Red Mawashi

    private val pressedKeys = HashSet<Int>()
    private val particles = ArrayList<SumoParticle>()
    private val rng = Random()

    private var currentState = GameState.READY
    private var stateTimer = 90 // Frames countdown for round starts/ends
    private var roundWinnerMessage = ""
    private var screenShake = 0.0

    private var running = false
    private var gameThread: Thread? = null

    init {
        preferredSize = Dimension(WINDOW_WIDTH, WINDOW_HEIGHT)
        background = Color(35, 25, 20) // Dark wood aesthetic
        isFocusable = true
        addKeyListener(this)
        resetRoundPositions()
    }

    fun startEngine() {
        running = true
        gameThread = Thread(this)
        gameThread?.start()
    }

    override fun run() {
        var lastTime = System.nanoTime()
        val nsPerTick = 1_000_000_000.0 / FPS

        while (running) {
            val now = System.nanoTime()
            val delta = (now - lastTime) / nsPerTick
            if (delta >= 1.0) {
                updateEngine()
                repaint()
                lastTime = now
            }
            try {
                Thread.sleep(2)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    // =============================================================================
    // GAME & PHYSICS SIMULATION LOOPS
    // =============================================================================
    private fun updateEngine() {
        // Screen Shake Decay
        if (screenShake > 0) screenShake *= 0.85

        when (currentState) {
            GameState.READY -> {
                stateTimer--
                if (stateTimer <= 0) {
                    currentState = GameState.FIGHTING
                }
            }
            GameState.FIGHTING -> {
                handlePlayerInputs()
                p1.update()
                p2.update()

                // Resolve 2D Physics Circle-to-Circle Elastic Collision
                resolveCircleCollision(p1, p2)

                // Check Dohyo Ring-Out Conditions
                checkRingOuts()
            }
            GameState.ROUND_OVER -> {
                p1.update()
                p2.update()
                stateTimer--
                if (stateTimer <= 0) {
                    if (p1.wins >= 3 || p2.wins >= 3) {
                        currentState = GameState.MATCH_OVER
                    } else {
                        resetRoundPositions()
                        currentState = GameState.READY
                        stateTimer = 90
                    }
                }
            }
            GameState.MATCH_OVER -> {
                if (pressedKeys.contains(KeyEvent.VK_R)) {
                    p1.wins = 0
                    p2.wins = 0
                    resetRoundPositions()
                    currentState = GameState.READY
                    stateTimer = 90
                }
            }
        }

        // Particle System Simulation
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val pt = iter.next()
            pt.x += pt.vx
            pt.y += pt.vy
            pt.vx *= 0.92
            pt.vy *= 0.92
            pt.life--
            if (pt.life <= 0) iter.remove()
        }
    }

    private fun handlePlayerInputs() {
        // --- Player 1 Controls (WASD + Space for Dash) ---
        var p1Fx = 0.0
        var p1Fy = 0.0
        if (pressedKeys.contains(KeyEvent.VK_W)) p1Fy -= ACCEL_FORCE
        if (pressedKeys.contains(KeyEvent.VK_S)) p1Fy += ACCEL_FORCE
        if (pressedKeys.contains(KeyEvent.VK_A)) p1Fx -= ACCEL_FORCE
        if (pressedKeys.contains(KeyEvent.VK_D)) p1Fx += ACCEL_FORCE
        p1.applyForce(p1Fx, p1Fy)

        if (pressedKeys.contains(KeyEvent.VK_SPACE)) {
            if (p1.performDash()) spawnDashParticles(p1)
        }

        // --- Player 2 Controls (Arrow Keys + ENTER for Dash) ---
        var p2Fx = 0.0
        var p2Fy = 0.0
        if (pressedKeys.contains(KeyEvent.VK_UP)) p2Fy -= ACCEL_FORCE
        if (pressedKeys.contains(KeyEvent.VK_DOWN)) p2Fy += ACCEL_FORCE
        if (pressedKeys.contains(KeyEvent.VK_LEFT)) p2Fx -= ACCEL_FORCE
        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) p2Fx += ACCEL_FORCE
        p2.applyForce(p2Fx, p2Fy)

        if (pressedKeys.contains(KeyEvent.VK_ENTER)) {
            if (p2.performDash()) spawnDashParticles(p2)
        }
    }

    // =============================================================================
    // 2D ELASTIC CIRCLE COLLISION MATH ENGINE
    // =============================================================================
    private fun resolveCircleCollision(c1: SumoRikishi, c2: SumoRikishi) {
        val dx = c2.x - c1.x
        val dy = c2.y - c1.y
        val dist = hypot(dx, dy)
        val minDist = c1.radius + c2.radius

        // Check Rectangle/Circle Collision Overlap
        if (dist < minDist) {
            // Normal Vector along collision axis
            val nx = if (dist != 0.0) dx / dist else 1.0
            val ny = if (dist != 0.0) dy / dist else 0.0

            // 1. Positional Correction Vector (Separates overlapping geometry)
            val overlap = minDist - dist
            val separationRatio = 0.5
            c1.x -= nx * overlap * separationRatio
            c1.y -= ny * overlap * separationRatio
            c2.x += nx * overlap * separationRatio
            c2.y += ny * overlap * separationRatio

            // 2. Relative Velocity Vector
            val rvx = c2.vx - c1.vx
            val rvy = c2.vy - c1.vy

            // Calculate Velocity along the Normal Vector
            val velAlongNormal = rvx * nx + rvy * ny

            // Do not resolve if velocities are already separating
            if (velAlongNormal < 0) {
                // Calculate Impulse Scalar
                val impulseScalar = -(1.0 + RESTITUTION) * velAlongNormal / (1.0 / c1.mass + 1.0 / c2.mass)

                // Apply Impulse Vectors to Transfer Momentum
                val impulseX = impulseScalar * nx
                val impulseY = impulseScalar * ny

                c1.vx -= (1.0 / c1.mass) * impulseX
                c1.vy -= (1.0 / c1.mass) * impulseY
                c2.vx += (1.0 / c2.mass) * impulseX
                c2.vy += (1.0 / c2.mass) * impulseY

                // Impact FX & Camera Shake proportional to collision force
                val impactForce = abs(velAlongNormal)
                if (impactForce > 1.2) {
                    screenShake = min(impactForce * 2.5, 15.0)
                    spawnCollisionSparks((c1.x + c2.x) / 2.0, (c1.y + c2.y) / 2.0, impactForce)
                }
            }
        }
    }

    private fun checkRingOuts() {
        val p1Out = p1.isRingOut()
        val p2Out = p2.isRingOut()

        if (p1Out || p2Out) {
            currentState = GameState.ROUND_OVER
            stateTimer = 100

            when {
                p1Out && p2Out -> {
                    roundWinnerMessage = "TOR直し! (DRAW / BOTH OUT)"
                }
                p1Out -> {
                    p2.wins++
                    roundWinnerMessage = "PLAYER 2 WINS THE ROUND! (RING OUT)"
                    spawnRingOutDust(p1)
                }
                p2Out -> {
                    p1.wins++
                    roundWinnerMessage = "PLAYER 1 WINS THE ROUND! (RING OUT)"
                    spawnRingOutDust(p2)
                }
            }
        }
    }

    private fun resetRoundPositions() {
        p1.resetPosition(DOHYO_CENTER_X - 110.0, DOHYO_CENTER_Y, 0.0) // Facing East
        p2.resetPosition(DOHYO_CENTER_X + 110.0, DOHYO_CENTER_Y, PI)  // Facing West
    }

    // =============================================================================
    // VISUAL PARTICLES & SPARK GENERATORS
    // =============================================================================
    private fun spawnCollisionSparks(x: Double, y: Double, force: Double) {
        val count = (10 + force * 4).toInt()
        for (i in 0 until count) {
            val angle = rng.nextDouble() * 2.0 * PI
            val speed = 1.5 + rng.nextDouble() * force * 1.5
            val color = if (rng.nextBoolean()) Color(255, 220, 100) else Color(255, 140, 40)
            particles.add(
                SumoParticle(x, y, cos(angle) * speed, sin(angle) * speed, 12 + rng.nextInt(10), 22, color, 4f + rng.nextFloat() * 4f)
            )
        }
    }

    private fun spawnDashParticles(rikishi: SumoRikishi) {
        for (i in 0 until 12) {
            val angle = rikishi.facingAngle + PI + (rng.nextDouble() - 0.5) * 0.8
            val speed = 1.0 + rng.nextDouble() * 3.0
            particles.add(
                SumoParticle(rikishi.x, rikishi.y, cos(angle) * speed, sin(angle) * speed, 15, 15, Color(220, 200, 160, 180), 6f)
            )
        }
    }

    private fun spawnRingOutDust(rikishi: SumoRikishi) {
        for (i in 0 until 25) {
            val angle = rng.nextDouble() * 2.0 * PI
            val speed = 2.0 + rng.nextDouble() * 4.0
            particles.add(
                SumoParticle(rikishi.x, rikishi.y, cos(angle) * speed, sin(angle) * speed, 25, 25, Color(180, 140, 90, 200), 8f)
            )
        }
    }

    // =============================================================================
    // GRAPHICS RENDERING ENGINE
    // =============================================================================
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Apply Screen Shake Camera Offset
        if (screenShake > 0.5) {
            val offsetX = (rng.nextDouble() - 0.5) * screenShake
            val offsetY = (rng.nextDouble() - 0.5) * screenShake
            g2.translate(offsetX, offsetY)
        }

        drawDohyoArena(g2)

        // Draw Player Circular Avatars
        p1.render(g2)
        p2.render(g2)

        // Draw Particles
        for (p in particles) {
            g2.color = p.color
            val alpha = (p.life.toFloat() / p.maxLife.toFloat()).coerceIn(0f, 1f)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            g2.fill(Ellipse2D.Double(p.x - p.size / 2.0, p.y - p.size / 2.0, p.size.toDouble(), p.size.toDouble()))
        }
        g2.composite = AlphaComposite.SrcOver

        drawHUD(g2)
    }

    private fun drawDohyoArena(g2: Graphics2D) {
        // Dohyo Outer Elevated Square Clay
        g2.color = Color(180, 140, 95)
        g2.fillRoundRect(
            (DOHYO_CENTER_X - DOHYO_RADIUS - 30).toInt(),
            (DOHYO_CENTER_Y - DOHYO_RADIUS - 30).toInt(),
            ((DOHYO_RADIUS + 30) * 2).toInt(),
            ((DOHYO_RADIUS + 30) * 2).toInt(),
            40, 40
        )

        // Dohyo Circular Clay Arena Floor
        g2.color = Color(215, 175, 125)
        g2.fill(Ellipse2D.Double(DOHYO_CENTER_X - DOHYO_RADIUS, DOHYO_CENTER_Y - DOHYO_RADIUS, DOHYO_RADIUS * 2, DOHYO_RADIUS * 2))

        // Straw Bales Ring Boundary (Tawaraya)
        g2.color = Color(100, 65, 30)
        g2.stroke = BasicStroke(10f)
        g2.draw(Ellipse2D.Double(DOHYO_CENTER_X - DOHYO_RADIUS, DOHYO_CENTER_Y - DOHYO_RADIUS, DOHYO_RADIUS * 2, DOHYO_RADIUS * 2))

        // White Starting Lines (Shikiri-sen)
        g2.color = Color(240, 240, 240)
        g2.stroke = BasicStroke(6f)
        g2.draw(Line2D.Double(DOHYO_CENTER_X - 40.0, DOHYO_CENTER_Y - 25.0, DOHYO_CENTER_X - 40.0, DOHYO_CENTER_Y + 25.0))
        g2.draw(Line2D.Double(DOHYO_CENTER_X + 40.0, DOHYO_CENTER_Y - 25.0, DOHYO_CENTER_X + 40.0, DOHYO_CENTER_Y + 25.0))
    }

    private fun drawHUD(g2: Graphics2D) {
        // HUD Overlay Headers
        g2.font = Font("SansSerif", Font.BOLD, 22)

        // Player 1 HUD
        g2.color = Color(100, 180, 255)
        g2.drawString("P1 (WASD + SPACE)", 40, 40)
        drawStaminaBar(g2, 40, 50, p1)
        drawScoreStars(g2, 40, 90, p1.wins)

        // Player 2 HUD
        g2.color = Color(255, 100, 100)
        g2.drawString("P2 (ARROWS + ENTER)", WINDOW_WIDTH - 280, 40)
        drawStaminaBar(g2, WINDOW_WIDTH - 280, 50, p2)
        drawScoreStars(g2, WINDOW_WIDTH - 280, 90, p2.wins)

        // Announcer State Overlay
        g2.font = Font("Serif", Font.BOLD, 36)
        when (currentState) {
            GameState.READY -> {
                val msg = if (stateTimer > 30) "はっけよい... (READY)" else "のこった! (FIGHT!)"
                val w = g2.fontMetrics.stringWidth(msg)
                g2.color = Color.BLACK
                g2.drawString(msg, WINDOW_WIDTH / 2 - w / 2 + 2, 82)
                g2.color = Color.YELLOW
                g2.drawString(msg, WINDOW_WIDTH / 2 - w / 2, 80)
            }
            GameState.ROUND_OVER -> {
                g2.font = Font("SansSerif", Font.BOLD, 28)
                val w = g2.fontMetrics.stringWidth(roundWinnerMessage)
                g2.color = Color.BLACK
                g2.drawString(roundWinnerMessage, WINDOW_WIDTH / 2 - w / 2 + 2, 82)
                g2.color = Color.ORANGE
                g2.drawString(roundWinnerMessage, WINDOW_WIDTH / 2 - w / 2, 80)
            }
            GameState.MATCH_OVER -> {
                val champMsg = if (p1.wins >= 3) "PLAYER 1 IS THE YOKOZHUNA CHAMPION!" else "PLAYER 2 IS THE YOKOZHUNA CHAMPION!"
                g2.font = Font("SansSerif", Font.BOLD, 30)
                val w = g2.fontMetrics.stringWidth(champMsg)
                g2.color = Color.BLACK
                g2.drawString(champMsg, WINDOW_WIDTH / 2 - w / 2 + 2, WINDOW_HEIGHT / 2 + 2)
                g2.color = Color.GREEN
                g2.drawString(champMsg, WINDOW_WIDTH / 2 - w / 2, WINDOW_HEIGHT / 2)

                g2.font = Font("Monospaced", Font.BOLD, 20)
                val rst = "PRESS 'R' TO START NEW MATCH"
                val rw = g2.fontMetrics.stringWidth(rst)
                g2.color = Color.WHITE
                g2.drawString(rst, WINDOW_WIDTH / 2 - rw / 2, WINDOW_HEIGHT / 2 + 45)
            }
            else -> {}
        }
    }

    private fun drawStaminaBar(g2: Graphics2D, x: Int, y: Int, rikishi: SumoRikishi) {
        val width = 220
        val height = 16
        g2.color = Color.DARK_GRAY
        g2.fillRect(x, y, width, height)

        val fillW = ((rikishi.stamina / rikishi.maxStamina) * width).toInt()
        g2.color = if (rikishi.stamina >= 30.0) Color(255, 200, 0) else Color.GRAY
        g2.fillRect(x, y, fillW, height)

        g2.color = Color.WHITE
        g2.stroke = BasicStroke(1.5f)
        g2.drawRect(x, y, width, height)
    }

    private fun drawScoreStars(g2: Graphics2D, x: Int, y: Int, wins: Int) {
        for (i in 0 until 3) {
            if (i < wins) {
                g2.color = Color.YELLOW
                g2.fill(Ellipse2D.Double((x + i * 28).toDouble(), y.toDouble(), 20.0, 20.0))
            } else {
                g2.color = Color.GRAY
                g2.draw(Ellipse2D.Double((x + i * 28).toDouble(), y.toDouble(), 20.0, 20.0))
            }
        }
    }

    // =============================================================================
    // KEYBOARD INPUT LISTENER
    // =============================================================================
    override fun keyPressed(e: KeyEvent) {
        pressedKeys.add(e.keyCode)
    }

    override fun keyReleased(e: KeyEvent) {
        pressedKeys.remove(e.keyCode)
    }

    override fun keyTyped(e: KeyEvent) {}
}

// =============================================================================
// MAIN APPLICATION ENTRY POINT
// =============================================================================
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Sumo Arena Ring-Out Engine (Top-Down Physics)")
        val gamePanel = SumoArenaPanel()

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.add(gamePanel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        gamePanel.startEngine()
    }
}