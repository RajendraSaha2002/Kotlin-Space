package footsies

import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.geom.Rectangle2D
import java.util.Random
import javax.swing.*
import kotlin.math.*

// =============================================================================
// GAME CONSTANTS & CONFIGURATION
// =============================================================================
const val CANVAS_WIDTH = 900
const val CANVAS_HEIGHT = 500
const val GROUND_Y = 380.0
const val FPS = 60

const val STAGE_MIN_X = 60.0
const val STAGE_MAX_X = 840.0
const val PLAYER_WIDTH = 50.0
const val PLAYER_HEIGHT_STAND = 110.0
const val PLAYER_HEIGHT_CROUCH = 65.0
const val MOVE_SPEED = 4.2

// =============================================================================
// FRAME DATA & ATTACK DEFINITIONS
// =============================================================================
enum class FootsiesAttackType { LIGHT_PUNCH, HEAVY_KICK }

data class FootsiesAttackData(
    val type: FootsiesAttackType,
    val name: String,
    val startupFrames: Int,
    val activeFrames: Int,
    val recoveryFrames: Int,
    val damage: Double,
    val hitStunFrames: Int,
    val blockStunFrames: Int,
    val pushbackSelf: Double,
    val pushbackTarget: Double,
    val boxOffsetX: Double,
    val boxOffsetY: Double,
    val boxWidth: Double,
    val boxHeight: Double
)

val LIGHT_PUNCH_DATA = FootsiesAttackData(
    type = FootsiesAttackType.LIGHT_PUNCH,
    name = "Light Punch",
    startupFrames = 4,
    activeFrames = 3,
    recoveryFrames = 8,
    damage = 6.0,
    hitStunFrames = 14,
    blockStunFrames = 8,
    pushbackSelf = 4.0,
    pushbackTarget = 18.0,
    boxOffsetX = 35.0,
    boxOffsetY = -80.0,
    boxWidth = 45.0,
    boxHeight = 22.0
)

val HEAVY_KICK_DATA = FootsiesAttackData(
    type = FootsiesAttackType.HEAVY_KICK,
    name = "Heavy Kick",
    startupFrames = 11,
    activeFrames = 5,
    recoveryFrames = 18,
    damage = 16.0,
    hitStunFrames = 26,
    blockStunFrames = 14,
    pushbackSelf = 8.0,
    pushbackTarget = 38.0,
    boxOffsetX = 30.0,
    boxOffsetY = -45.0,
    boxWidth = 70.0,
    boxHeight = 30.0
)

// =============================================================================
// VISUAL PARTICLES & EFFECTS
// =============================================================================
data class FootsiesParticle(
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
// FIGHTER CHARACTER CLASS
// =============================================================================
class FootsiesFighter(
    val id: Int,
    var x: Double,
    val colorTheme: Color
) {
    var hp: Double = 100.0
    val maxHp: Double = 100.0
    var facingRight: Boolean = (id == 1)

    // State Flags
    var isCrouching: Boolean = false
    var isBlocking: Boolean = false
    var stunFrames: Int = 0
    var isBlockStun: Boolean = false

    // Physics
    var vx: Double = 0.0

    // Attack State Machine
    var currentAttack: FootsiesAttackData? = null
    var attackFrame: Int = 0
    var hasHitCurrentAttack: Boolean = false

    // Visual Effect Triggers
    var flashWhiteFrames: Int = 0

    val height: Double
        get() = if (isCrouching) PLAYER_HEIGHT_CROUCH else PLAYER_HEIGHT_STAND

    val y: Double
        get() = GROUND_Y - height

    // Precision Bounding Box for Hurtbox
    fun getHurtbox(): Rectangle2D.Double {
        return Rectangle2D.Double(x - PLAYER_WIDTH / 2.0, y, PLAYER_WIDTH, height)
    }

    // Precise Dynamic Hitbox Calculation
    fun getHitbox(): Rectangle2D.Double? {
        val atk = currentAttack ?: return null
        val totalStartup = atk.startupFrames
        val totalActive = atk.startupFrames + atk.activeFrames

        // Active Frame Check
        if (attackFrame in totalStartup until totalActive) {
            val boxX = if (facingRight) {
                x + atk.boxOffsetX
            } else {
                x - atk.boxOffsetX - atk.boxWidth
            }
            val boxY = GROUND_Y + atk.boxOffsetY
            return Rectangle2D.Double(boxX, boxY, atk.boxWidth, atk.boxHeight)
        }
        return null
    }

    fun canAct(): Boolean {
        return stunFrames == 0 && currentAttack == null
    }

    fun startAttack(atk: FootsiesAttackData) {
        if (!canAct()) return
        currentAttack = atk
        attackFrame = 0
        hasHitCurrentAttack = false
    }

    fun update(opponent: FootsiesFighter) {
        if (flashWhiteFrames > 0) flashWhiteFrames--

        // Automatic Face-Opponent Direction update when free
        if (canAct()) {
            facingRight = (x < opponent.x)
        }

        // Process Stun
        if (stunFrames > 0) {
            stunFrames--
            if (stunFrames == 0) isBlockStun = false
        }

        // Process Attack Animation Frame Data
        currentAttack?.let { atk ->
            attackFrame++
            val totalDuration = atk.startupFrames + atk.activeFrames + atk.recoveryFrames
            if (attackFrame >= totalDuration) {
                currentAttack = null
                attackFrame = 0
                hasHitCurrentAttack = false
            }
        }

        // Apply Friction to Knockback
        x += vx
        vx *= 0.82
        if (abs(vx) < 0.05) vx = 0.0

        // Stage Boundary Bounds Collision
        val halfW = PLAYER_WIDTH / 2.0
        x = x.coerceIn(STAGE_MIN_X + halfW, STAGE_MAX_X - halfW)
    }

    fun takeHit(damage: Double, stun: Int, pushback: Double, attackerFacingRight: Boolean, blocked: Boolean) {
        val dir = if (attackerFacingRight) 1.0 else -1.0
        if (blocked) {
            hp -= damage * 0.15 // Chip Damage
            stunFrames = stun
            isBlockStun = true
            vx = dir * (pushback * 0.6)
        } else {
            hp -= damage
            stunFrames = stun
            isBlockStun = false
            vx = dir * pushback
            flashWhiteFrames = 4
        }
        if (hp < 0.0) hp = 0.0
        // Cancel active attacks on hit
        currentAttack = null
        attackFrame = 0
    }
}

// =============================================================================
// MAIN CANVAS & GAME ENGINE PANEL
// =============================================================================
class BrawlerPanel : JPanel(), KeyListener, Runnable {
    private val p1 = FootsiesFighter(1, 280.0, Color(41, 128, 185)) // Blue Team
    private val p2 = FootsiesFighter(2, 620.0, Color(192, 57, 43))  // Red Team

    private val pressedKeys = HashSet<Int>()
    private val particles = ArrayList<FootsiesParticle>()
    private val rng = Random()

    private var roundTimer = 99
    private var tickCounter = 0
    private var gameOver = false
    private var statusMessage = "ROUND 1 - FIGHT!"
    private var showBoxes = true // Debug / Footsies Display Toggle

    private var running = false
    private var gameThread: Thread? = null

    init {
        preferredSize = Dimension(CANVAS_WIDTH, CANVAS_HEIGHT)
        background = Color(20, 24, 32)
        isFocusable = true
        addKeyListener(this)
    }

    fun startGame() {
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
                updateGame()
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
    // GAME ENGINE LOGIC & COLLISION SYSTEMS
    // =============================================================================
    private fun updateGame() {
        if (gameOver) {
            if (pressedKeys.contains(KeyEvent.VK_R)) resetMatch()
            return
        }

        // Round Timer Counter
        tickCounter++
        if (tickCounter >= FPS) {
            tickCounter = 0
            if (roundTimer > 0) roundTimer--
            if (roundTimer == 0) checkRoundEnd(timeOut = true)
        }

        // Handle Player Inputs
        processInput(p1, KeyEvent.VK_A, KeyEvent.VK_D, KeyEvent.VK_S, KeyEvent.VK_F, KeyEvent.VK_G)
        processInput(p2, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN, KeyEvent.VK_K, KeyEvent.VK_L)

        // Frame Simulation Updates
        p1.update(p2)
        p2.update(p1)

        // Solve Pushbox Body Collisions
        solvePushboxCollision()

        // Detect Precise Rectangle Intersections (Hitbox vs Hurtbox)
        checkCombatCollisions(p1, p2)
        checkCombatCollisions(p2, p1)

        // Update Particle Simulation
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.life--
            if (p.life <= 0) iterator.remove()
        }

        // Check Victory Conditions
        if (p1.hp <= 0 || p2.hp <= 0) checkRoundEnd(timeOut = false)
    }

    private fun processInput(f: FootsiesFighter, leftKey: Int, rightKey: Int, crouchKey: Int, lpKey: Int, hkKey: Int) {
        if (!f.canAct()) return

        val holdLeft = pressedKeys.contains(leftKey)
        val holdRight = pressedKeys.contains(rightKey)
        val holdCrouch = pressedKeys.contains(crouchKey)

        // Set Crouch State
        f.isCrouching = holdCrouch

        // Movement & Blocking Logic
        var moveDir = 0.0
        if (holdLeft) moveDir -= 1.0
        if (holdRight) moveDir += 1.0

        if (moveDir != 0.0) {
            val speed = if (f.isCrouching) MOVE_SPEED * 0.4 else MOVE_SPEED
            f.x += moveDir * speed

            // Check if walking backward (Blocking)
            f.isBlocking = (f.facingRight && moveDir < 0) || (!f.facingRight && moveDir > 0)
        } else {
            f.isBlocking = false
        }

        // Attacks Initiation
        if (pressedKeys.contains(lpKey)) {
            f.startAttack(LIGHT_PUNCH_DATA)
        } else if (pressedKeys.contains(hkKey)) {
            f.startAttack(HEAVY_KICK_DATA)
        }
    }

    private fun solvePushboxCollision() {
        val dist = abs(p1.x - p2.x)
        val minDist = PLAYER_WIDTH * 0.85
        if (dist < minDist) {
            val overlap = (minDist - dist) / 2.0
            if (p1.x < p2.x) {
                p1.x -= overlap
                p2.x += overlap
            } else {
                p1.x += overlap
                p2.x -= overlap
            }
        }
    }

    private fun checkCombatCollisions(attacker: FootsiesFighter, defender: FootsiesFighter) {
        val hitbox = attacker.getHitbox() ?: return
        if (attacker.hasHitCurrentAttack) return

        val hurtbox = defender.getHurtbox()

        // 2D RECTANGLE INTERSECTION FORMULA
        if (hitbox.intersects(hurtbox)) {
            attacker.hasHitCurrentAttack = true
            val atk = attacker.currentAttack!!

            // Determine Block vs Clean Hit
            val isBlocked = defender.isBlocking

            // Execute Hit Reaction
            defender.takeHit(
                damage = atk.damage,
                stun = if (isBlocked) atk.blockStunFrames else atk.hitStunFrames,
                pushback = atk.pushbackTarget,
                attackerFacingRight = attacker.facingRight,
                blocked = isBlocked
            )

            // Attacker Self Pushback Vector
            val selfDir = if (attacker.facingRight) -1.0 else 1.0
            attacker.vx = selfDir * atk.pushbackSelf

            // Visual Effects Spawn
            val hitX = (max(hitbox.minX, hurtbox.minX) + min(hitbox.maxX, hurtbox.maxX)) / 2.0
            val hitY = (max(hitbox.minY, hurtbox.minY) + min(hitbox.maxY, hurtbox.maxY)) / 2.0
            spawnHitSparks(hitX, hitY, if (isBlocked) Color.CYAN else Color.YELLOW)
        }
    }

    private fun spawnHitSparks(x: Double, y: Double, color: Color) {
        val count = 18
        for (i in 0 until count) {
            val angle = rng.nextDouble() * 2.0 * PI
            val speed = 2.0 + rng.nextDouble() * 7.0
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            val life = 8 + rng.nextInt(10)
            particles.add(FootsiesParticle(x, y, vx, vy, life, life, color, 3f + rng.nextFloat() * 4f))
        }
    }

    private fun checkRoundEnd(timeOut: Boolean) {
        gameOver = true
        statusMessage = when {
            p1.hp > p2.hp -> "PLAYER 1 WINS!"
            p2.hp > p1.hp -> "PLAYER 2 WINS!"
            else -> "DOUBLE KO - DRAW!"
        }
        if (timeOut) statusMessage = "TIME OUT! $statusMessage"
    }

    private fun resetMatch() {
        p1.hp = p1.maxHp
        p2.hp = p2.maxHp
        p1.x = 280.0
        p2.x = 620.0
        p1.stunFrames = 0
        p2.stunFrames = 0
        p1.currentAttack = null
        p2.currentAttack = null
        p1.vx = 0.0
        p2.vx = 0.0
        roundTimer = 99
        gameOver = false
        statusMessage = ""
    }

    // =============================================================================
    // RENDERING & DRAWING ENGINE
    // =============================================================================
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        drawBackground(g2)
        drawFighter(g2, p1)
        drawFighter(g2, p2)

        // Draw Particle Effects
        for (p in particles) {
            g2.color = p.color
            val alpha = (p.life.toFloat() / p.maxLife.toFloat()).coerceIn(0f, 1f)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            g2.fillOval(p.x.toInt(), p.y.toInt(), p.size.toInt(), p.size.toInt())
        }
        g2.composite = AlphaComposite.SrcOver

        drawHUD(g2)
    }

    private fun drawBackground(g2: Graphics2D) {
        // Retro Arcade Grid Stage
        g2.color = Color(30, 36, 48)
        g2.fillRect(0, 0, width, height)

        // Floor
        g2.color = Color(15, 18, 24)
        g2.fillRect(0, GROUND_Y.toInt(), width, height - GROUND_Y.toInt())
        g2.color = Color(0, 230, 180, 120)
        g2.setStroke(BasicStroke(3f))
        g2.drawLine(0, GROUND_Y.toInt(), width, GROUND_Y.toInt())

        // Perspective Floor Lines
        g2.color = Color(255, 255, 255, 15)
        for (i in 0 until width step 40) {
            g2.drawLine(i, GROUND_Y.toInt(), i + (i - width / 2), height)
        }
    }

    private fun drawFighter(g2: Graphics2D, f: FootsiesFighter) {
        val hurtbox = f.getHurtbox()

        // Flash white on getting hit
        if (f.flashWhiteFrames > 0) {
            g2.color = Color.WHITE
            g2.fillRect(hurtbox.x.toInt(), hurtbox.y.toInt(), hurtbox.width.toInt(), hurtbox.height.toInt())
            return
        }

        // Draw Character Sprite / Silhouette
        val bodyColor = when {
            f.stunFrames > 0 && f.isBlockStun -> Color.CYAN
            f.stunFrames > 0 -> Color.GRAY
            else -> f.colorTheme
        }
        g2.color = bodyColor

        // Main Body Primitive
        g2.fillRoundRect(
            hurtbox.x.toInt(),
            hurtbox.y.toInt(),
            hurtbox.width.toInt(),
            hurtbox.height.toInt(),
            16, 16
        )

        // Face Visor / Direction Indicator
        g2.color = Color.YELLOW
        val visorX = if (f.facingRight) hurtbox.x + hurtbox.width - 12.0 else hurtbox.x + 4.0
        val visorY = hurtbox.y + 12.0
        g2.fillRect(visorX.toInt(), visorY.toInt(), 8, 8)

        // Attack Pose Visualizer
        f.currentAttack?.let { atk ->
            g2.color = Color.ORANGE
            if (atk.type == FootsiesAttackType.LIGHT_PUNCH) {
                val armX = if (f.facingRight) hurtbox.maxX else hurtbox.minX - 35.0
                g2.fillRect(armX.toInt(), (hurtbox.y + 20).toInt(), 35, 12)
            } else if (atk.type == FootsiesAttackType.HEAVY_KICK) {
                val legX = if (f.facingRight) hurtbox.maxX else hurtbox.minX - 55.0
                g2.fillRect(legX.toInt(), (hurtbox.y + 55).toInt(), 55, 16)
            }
        }

        // --- FOOTSIES TRAINER OVERLAYS (HITBOX & HURTBOX) ---
        if (showBoxes) {
            // Draw Hurtbox (Green/Blue transparent)
            g2.color = Color(0, 255, 120, 70)
            g2.fillRect(hurtbox.x.toInt(), hurtbox.y.toInt(), hurtbox.width.toInt(), hurtbox.height.toInt())
            g2.color = Color(0, 255, 120, 200)
            g2.draw(hurtbox)

            // Draw Hitbox (Red transparent)
            f.getHitbox()?.let { hb ->
                g2.color = Color(255, 0, 50, 120)
                g2.fillRect(hb.x.toInt(), hb.y.toInt(), hb.width.toInt(), hb.height.toInt())
                g2.color = Color(255, 0, 50, 240)
                g2.draw(hb)
            }
        }
    }

    private fun drawHUD(g2: Graphics2D) {
        val barWidth = 320
        val barHeight = 22

        // Player 1 Health Bar
        g2.color = Color.DARK_GRAY
        g2.fillRect(40, 30, barWidth, barHeight)
        val p1HpW = ((p1.hp / p1.maxHp) * barWidth).toInt()
        g2.color = if (p1.hp > 30) Color.GREEN else Color.RED
        g2.fillRect(40, 30, p1HpW, barHeight)
        g2.color = Color.WHITE
        g2.drawRect(40, 30, barWidth, barHeight)
        g2.font = Font("Monospaced", Font.BOLD, 16)
        g2.drawString("P1 (A/D/S | F:LP G:HK)", 40, 24)

        // Player 2 Health Bar
        g2.color = Color.DARK_GRAY
        g2.fillRect(CANVAS_WIDTH - 40 - barWidth, 30, barWidth, barHeight)
        val p2HpW = ((p2.hp / p2.maxHp) * barWidth).toInt()
        g2.color = if (p2.hp > 30) Color.GREEN else Color.RED
        g2.fillRect(CANVAS_WIDTH - 40 - p2HpW, 30, p2HpW, barHeight)
        g2.color = Color.WHITE
        g2.drawRect(CANVAS_WIDTH - 40 - barWidth, 30, barWidth, barHeight)
        g2.drawString("P2 (ARROWS | K:LP L:HK)", CANVAS_WIDTH - 40 - barWidth, 24)

        // Timer Display
        g2.font = Font("Monospaced", Font.BOLD, 32)
        g2.color = Color.YELLOW
        g2.drawString(String.format("%02d", roundTimer), CANVAS_WIDTH / 2 - 20, 50)

        // Sub-text & Frame Debug Info
        g2.font = Font("SansSerif", Font.PLAIN, 12)
        g2.color = Color.LIGHT_GRAY
        g2.drawString("[H] Toggle Box Display", CANVAS_WIDTH / 2 - 65, 75)

        // Center Banner Messages
        if (gameOver || statusMessage.isNotEmpty()) {
            g2.font = Font("Impact", Font.ITALIC, 42)
            val metrics = g2.fontMetrics
            val txtWidth = metrics.stringWidth(statusMessage)
            g2.color = Color.BLACK
            g2.drawString(statusMessage, CANVAS_WIDTH / 2 - txtWidth / 2 + 2, 202)
            g2.color = Color.ORANGE
            g2.drawString(statusMessage, CANVAS_WIDTH / 2 - txtWidth / 2, 200)

            if (gameOver) {
                g2.font = Font("Monospaced", Font.BOLD, 18)
                g2.color = Color.WHITE
                val rstMsg = "PRESS 'R' TO RESTART ROUND"
                val rstW = g2.fontMetrics.stringWidth(rstMsg)
                g2.drawString(rstMsg, CANVAS_WIDTH / 2 - rstW / 2, 245)
            }
        }
    }

    // =============================================================================
    // KEYBOARD INPUT LISTENER
    // =============================================================================
    override fun keyPressed(e: KeyEvent) {
        pressedKeys.add(e.keyCode)
        if (e.keyCode == KeyEvent.VK_H) {
            showBoxes = !showBoxes
        }
    }

    override fun keyReleased(e: KeyEvent) {
        pressedKeys.remove(e.keyCode)
    }

    override fun keyTyped(e: KeyEvent) {}
}

// =============================================================================
// MAIN ENTRY POINT
// =============================================================================
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Retro 2D Footsies Trainer (2-Player Local)")
        val gamePanel = BrawlerPanel()

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.add(gamePanel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        gamePanel.startGame()
    }
}