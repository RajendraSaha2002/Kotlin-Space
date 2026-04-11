import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ─────────────────────────────────────────────
//  CONSTANTS  — all prefixed to avoid AWT clashes
//  (ENEMY_W / ENEMY_H clashed with KeyEvent constants)
// ─────────────────────────────────────────────
const val GAME_WIN_W     = 480
const val GAME_WIN_H     = 700
const val ROAD_X_LEFT    = 60
const val ROAD_X_RIGHT   = 420
const val ROAD_WIDTH     = ROAD_X_RIGHT - ROAD_X_LEFT   // 360
const val NUM_LANES      = 4
const val LANE_WIDTH     = ROAD_WIDTH / NUM_LANES        // 90

const val PLAYER_CAR_W   = 46
const val PLAYER_CAR_H   = 80
const val ENEMY_CAR_W    = 44
const val ENEMY_CAR_H    = 76
const val POWERUP_BOX_SZ = 32
const val DASH_BOX_H     = 40
const val DASH_BOX_GAP   = 60

// ─────────────────────────────────────────────
//  HELPER  — centre-x of a lane
// ─────────────────────────────────────────────
fun laneCenter(lane: Int): Int =
    ROAD_X_LEFT + lane * LANE_WIDTH + LANE_WIDTH / 2

// ─────────────────────────────────────────────
//  ROAD DASH  (animated lane dividers)
// ─────────────────────────────────────────────
class RoadDash(var posY: Float) {
    fun update(spd: Float) { posY += spd }
    fun reset()            { posY = -DASH_BOX_H.toFloat() }
}

// ─────────────────────────────────────────────
//  PLAYER CAR
// ─────────────────────────────────────────────
class PlayerCar {
    var posX        = (GAME_WIN_W / 2 - PLAYER_CAR_W / 2).toFloat()
    var posY        = (GAME_WIN_H - PLAYER_CAR_H - 30).toFloat()
    var velX        = 0f
    var hasShield   = false
    var shieldTicks = 0
    var hasSlow     = false
    var slowTicks   = 0

    fun update() {
        posX += velX
        posX  = posX.coerceIn(ROAD_X_LEFT.toFloat(), (ROAD_X_RIGHT - PLAYER_CAR_W).toFloat())
        velX *= 0.75f
        if (shieldTicks > 0) { shieldTicks--; if (shieldTicks == 0) hasShield = false }
        if (slowTicks   > 0) { slowTicks--;   if (slowTicks   == 0) hasSlow   = false }
    }

    fun moveLeft()  { velX -= 6f }
    fun moveRight() { velX += 6f }

    fun hitBox() = Rectangle(posX.toInt(), posY.toInt(), PLAYER_CAR_W, PLAYER_CAR_H)

    fun draw(g2: Graphics2D) {
        val xi = posX.toInt(); val yi = posY.toInt()
        if (hasShield) {
            g2.color = Color(80, 200, 255, 60)
            g2.fillOval(xi - 10, yi - 10, PLAYER_CAR_W + 20, PLAYER_CAR_H + 20)
            g2.color  = Color(80, 220, 255, 140)
            g2.stroke = BasicStroke(2.5f)
            g2.drawOval(xi - 10, yi - 10, PLAYER_CAR_W + 20, PLAYER_CAR_H + 20)
            g2.stroke = BasicStroke(1f)
        }
        paintCar(g2, xi, yi, PLAYER_CAR_W, PLAYER_CAR_H,
            Color(50, 160, 255), Color(30, 100, 200), isPlayer = true)
    }
}

// ─────────────────────────────────────────────
//  ENEMY CAR
// ─────────────────────────────────────────────
enum class EnemyTint { RED, YELLOW, PURPLE, GREEN }

class EnemyCar(var lane: Int) {
    var posX      = laneCenter(lane).toFloat() - ENEMY_CAR_W / 2f
    var posY      = -ENEMY_CAR_H.toFloat()
    var targetPosX = posX
    var alive     = true
    val tint      = EnemyTint.values().random()
    private var switchCd = Random.nextInt(60, 180)

    fun update(spd: Float, playerPosX: Float) {
        posY += spd
        posX += (targetPosX - posX) * 0.12f
        switchCd--
        if (switchCd <= 0) {
            switchCd = Random.nextInt(80, 220)
            val pLane = ((playerPosX + PLAYER_CAR_W / 2f - ROAD_X_LEFT) / LANE_WIDTH)
                .toInt().coerceIn(0, NUM_LANES - 1)
            lane = when {
                Random.nextInt(3) == 0 -> Random.nextInt(NUM_LANES)
                lane < pLane           -> min(lane + 1, NUM_LANES - 1)
                lane > pLane           -> max(lane - 1, 0)
                else                   -> lane
            }
            targetPosX = laneCenter(lane).toFloat() - ENEMY_CAR_W / 2f
        }
        if (posY > GAME_WIN_H + ENEMY_CAR_H) alive = false
    }

    fun hitBox() = Rectangle(posX.toInt(), posY.toInt(), ENEMY_CAR_W, ENEMY_CAR_H)

    fun draw(g2: Graphics2D) {
        val (base, shadow) = when (tint) {
            EnemyTint.RED    -> Color(230, 60,  60)  to Color(160, 20,  20)
            EnemyTint.YELLOW -> Color(240, 200, 30)  to Color(180, 140,  0)
            EnemyTint.PURPLE -> Color(180, 80,  220) to Color(110, 30, 160)
            EnemyTint.GREEN  -> Color(60,  200, 100) to Color(20,  130,  60)
        }
        paintCar(g2, posX.toInt(), posY.toInt(), ENEMY_CAR_W, ENEMY_CAR_H,
            base, shadow, isPlayer = false)
    }
}

// ─────────────────────────────────────────────
//  POWER-UP
// ─────────────────────────────────────────────
enum class PowerKind { SHIELD, SLOW, SCORE }

class PowerUp(var posX: Float, var posY: Float, val kind: PowerKind) {
    var alive = true
    fun update(spd: Float) { posY += spd * 0.6f; if (posY > GAME_WIN_H + 40) alive = false }
    fun hitBox() = Rectangle(posX.toInt(), posY.toInt(), POWERUP_BOX_SZ, POWERUP_BOX_SZ)
    fun draw(g2: Graphics2D) {
        val col = when (kind) {
            PowerKind.SHIELD -> Color(80,  200, 255)
            PowerKind.SLOW   -> Color(255, 200,  60)
            PowerKind.SCORE  -> Color(100, 255, 120)
        }
        val sym = when (kind) {
            PowerKind.SHIELD -> "S"; PowerKind.SLOW -> "T"; PowerKind.SCORE -> "*"
        }
        g2.color = col
        g2.fillRoundRect(posX.toInt(), posY.toInt(), POWERUP_BOX_SZ, POWERUP_BOX_SZ, 8, 8)
        g2.color = Color.WHITE
        g2.font  = Font("Monospaced", Font.BOLD, 18)
        g2.drawString(sym, posX.toInt() + 9, posY.toInt() + 22)
    }
}

// ─────────────────────────────────────────────
//  SHARED CAR PAINTER
// ─────────────────────────────────────────────
fun paintCar(
    g2: Graphics2D, xi: Int, yi: Int,
    cw: Int, ch: Int,
    base: Color, shadow: Color, isPlayer: Boolean
) {
    // body gradient
    val grad = GradientPaint(
        xi.toFloat(), yi.toFloat(), base,
        (xi + cw).toFloat(), (yi + ch).toFloat(), shadow
    )
    g2.paint = grad
    g2.fillRoundRect(xi, yi, cw, ch, 10, 10)
    g2.paint = null

    // roof gloss
    g2.color = Color(255, 255, 255, 40)
    g2.fillRoundRect(xi + cw / 5, yi + ch / 5, cw * 3 / 5, ch * 2 / 5, 6, 6)

    // windshield
    g2.color = Color(160, 220, 255, 160)
    if (isPlayer)
        g2.fillRoundRect(xi + 6, yi + 10, cw - 12, ch / 4, 4, 4)
    else
        g2.fillRoundRect(xi + 5, yi + ch - ch / 4 - 8, cw - 10, ch / 4, 4, 4)

    // lights
    val lightY = if (isPlayer) ch - 12 else 4
    g2.color = if (isPlayer) Color(255, 255, 180) else Color(255, 80, 80)
    g2.fillRoundRect(xi + 4,       yi + lightY, 10, 6, 3, 3)
    g2.fillRoundRect(xi + cw - 14, yi + lightY, 10, 6, 3, 3)

    // wheels
    g2.color = Color(30, 30, 30)
    g2.fillRoundRect(xi - 6,       yi + 8,       12, 20, 4, 4)
    g2.fillRoundRect(xi + cw - 6,  yi + 8,       12, 20, 4, 4)
    g2.fillRoundRect(xi - 6,       yi + ch - 28, 12, 20, 4, 4)
    g2.fillRoundRect(xi + cw - 6,  yi + ch - 28, 12, 20, 4, 4)

    // outline
    g2.color  = Color(0, 0, 0, 60)
    g2.stroke = BasicStroke(1.5f)
    g2.drawRoundRect(xi, yi, cw, ch, 10, 10)
    g2.stroke = BasicStroke(1f)
}

// ─────────────────────────────────────────────
//  SPARK PARTICLE
// ─────────────────────────────────────────────
class Spark(var spkX: Float, var spkY: Float) {
    val velX  = (Random.nextFloat() - 0.5f) * 8f
    val velY  = (Random.nextFloat() - 0.5f) * 8f
    var life  = 1f
    val col   = listOf(Color(255, 200, 60), Color(255, 100, 30), Color(255, 255, 100)).random()
    var alive = true

    fun update() {
        spkX += velX; spkY += velY
        life -= 0.05f
        if (life <= 0f) alive = false
    }

    fun draw(g2: Graphics2D) {
        val alpha = (life * 255).toInt().coerceIn(0, 255)
        g2.color = Color(col.red, col.green, col.blue, alpha)
        val sz = (life * 8).toInt().coerceAtLeast(1)
        g2.fillOval((spkX - sz / 2).toInt(), (spkY - sz / 2).toInt(), sz, sz)
    }
}

fun spawnSparks(cx: Float, cy: Float, list: MutableList<Spark>) {
    repeat(30) { list += Spark(cx, cy) }
}

// ─────────────────────────────────────────────
//  GAME PANEL
// ─────────────────────────────────────────────
class CarGamePanel : JPanel() {

    private val player    = PlayerCar()
    private val enemies   = mutableListOf<EnemyCar>()
    private val powerUps  = mutableListOf<PowerUp>()
    private val sparks    = mutableListOf<Spark>()
    private val dashes    = mutableListOf<RoadDash>()

    private var score     = 0
    private var hiScore   = 0
    private var gameSpeed = 4f
    private var gameOver  = false
    private var started   = false
    private var distance  = 0f
    private var bgScroll  = 0f

    private var leftDown    = false
    private var rightDown   = false
    private var enemyTick   = 0
    private var enemyRate   = 80
    private var powerTick   = 0

    private val backBuf = BufferedImage(GAME_WIN_W, GAME_WIN_H, BufferedImage.TYPE_INT_ARGB)

    init {
        preferredSize = Dimension(GAME_WIN_W, GAME_WIN_H)
        isFocusable   = true
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_LEFT  -> leftDown  = true
                    KeyEvent.VK_RIGHT -> rightDown = true
                    KeyEvent.VK_SPACE -> {
                        if (!started) started = true
                        else if (gameOver) restart()
                    }
                }
            }
            override fun keyReleased(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_LEFT  -> leftDown  = false
                    KeyEvent.VK_RIGHT -> rightDown = false
                }
            }
        })
        // build road dashes
        var dy = 0f
        while (dy < GAME_WIN_H + DASH_BOX_H) {
            dashes += RoadDash(dy); dy += (DASH_BOX_H + DASH_BOX_GAP).toFloat()
        }
        Timer(16) { tick() }.start()
    }

    // ── game tick ─────────────────────────────
    private fun tick() {
        if (!started || gameOver) { repaint(); return }

        val effSpd = if (player.hasSlow) gameSpeed * 0.45f else gameSpeed

        if (leftDown)  player.moveLeft()
        if (rightDown) player.moveRight()
        player.update()

        bgScroll = (bgScroll + effSpd) % GAME_WIN_H
        dashes.forEach { d -> d.update(effSpd); if (d.posY > GAME_WIN_H) d.reset() }

        distance  += effSpd
        gameSpeed  = 4f + distance / 4000f
        score      = (distance / 10f).toInt()
        if (score > hiScore) hiScore = score
        enemyRate  = max(28, 80 - (distance / 600f).toInt())

        // spawn enemies
        enemyTick++
        if (enemyTick >= enemyRate) {
            enemyTick = 0
            enemies += EnemyCar(Random.nextInt(NUM_LANES))
            if (gameSpeed > 7f && Random.nextInt(3) == 0)
                enemies += EnemyCar(Random.nextInt(NUM_LANES))
        }

        // spawn power-ups
        powerTick++
        if (powerTick >= 320) {
            powerTick = 0
            val px  = (ROAD_X_LEFT + Random.nextInt(ROAD_WIDTH - POWERUP_BOX_SZ)).toFloat()
            val kind = PowerKind.values()[Random.nextInt(PowerKind.values().size)]
            powerUps += PowerUp(px, -POWERUP_BOX_SZ.toFloat(), kind)
        }

        enemies.forEach  { it.update(effSpd, player.posX) }
        powerUps.forEach { it.update(effSpd) }
        sparks.forEach   { it.update() }
        enemies.removeIf  { !it.alive }
        powerUps.removeIf { !it.alive }
        sparks.removeIf   { !it.alive }

        // collisions — enemies
        val pr = player.hitBox()
        for (en in enemies) {
            if (!en.hitBox().intersects(pr)) continue
            if (player.hasShield) {
                spawnSparks(en.posX + ENEMY_CAR_W / 2f, en.posY + ENEMY_CAR_H / 2f, sparks)
                en.alive = false
            } else {
                spawnSparks(player.posX + PLAYER_CAR_W / 2f, player.posY + PLAYER_CAR_H / 2f, sparks)
                gameOver = true
            }
        }

        // collisions — power-ups
        for (pu in powerUps) {
            if (!pu.hitBox().intersects(pr)) continue
            pu.alive = false
            when (pu.kind) {
                PowerKind.SHIELD -> { player.hasShield = true; player.shieldTicks = 300 }
                PowerKind.SLOW   -> { player.hasSlow   = true; player.slowTicks   = 240 }
                PowerKind.SCORE  -> { score += 50 }
            }
        }

        repaint()
    }

    // ── rendering ─────────────────────────────
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = backBuf.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        drawBackground(g2)
        drawRoad(g2)
        if (!started) drawTitleScreen(g2)
        else {
            powerUps.forEach { it.draw(g2) }
            enemies.forEach  { it.draw(g2) }
            player.draw(g2)
            sparks.forEach   { it.draw(g2) }
            drawHUD(g2)
            if (gameOver) drawGameOverScreen(g2)
        }
        g2.dispose()
        g.drawImage(backBuf, 0, 0, null)
    }

    private fun drawBackground(g2: Graphics2D) {
        val grad = GradientPaint(
            0f, 0f, Color(30, 30, 50),
            0f, GAME_WIN_H.toFloat(), Color(50, 50, 40)
        )
        g2.paint = grad; g2.fillRect(0, 0, GAME_WIN_W, GAME_WIN_H); g2.paint = null

        g2.color = Color(40, 80, 40, 80)
        var sy = -GAME_WIN_H + bgScroll.toInt() % GAME_WIN_H
        while (sy < GAME_WIN_H) {
            g2.fillRect(0, sy, ROAD_X_LEFT - 2, 30)
            g2.fillRect(ROAD_X_RIGHT + 2, sy, GAME_WIN_W - ROAD_X_RIGHT, 30)
            sy += 80
        }
    }

    private fun drawRoad(g2: Graphics2D) {
        val roadGrad = GradientPaint(
            ROAD_X_LEFT.toFloat(),  0f, Color(65, 65, 70),
            ROAD_X_RIGHT.toFloat(), 0f, Color(55, 55, 60)
        )
        g2.paint = roadGrad
        g2.fillRect(ROAD_X_LEFT, 0, ROAD_WIDTH, GAME_WIN_H)
        g2.paint = null

        g2.color  = Color(255, 200, 0)
        g2.stroke = BasicStroke(4f)
        g2.drawLine(ROAD_X_LEFT,  0, ROAD_X_LEFT,  GAME_WIN_H)
        g2.drawLine(ROAD_X_RIGHT, 0, ROAD_X_RIGHT, GAME_WIN_H)
        g2.stroke = BasicStroke(1f)

        g2.color  = Color(255, 255, 255, 180)
        g2.stroke = BasicStroke(2f)
        for (lane in 1 until NUM_LANES) {
            val lx = ROAD_X_LEFT + lane * LANE_WIDTH
            dashes.forEach { d ->
                g2.drawLine(lx, d.posY.toInt(), lx, d.posY.toInt() + DASH_BOX_H)
            }
        }
        g2.stroke = BasicStroke(1f)
    }

    private fun drawHUD(g2: Graphics2D) {
        g2.color = Color(0, 0, 0, 140)
        g2.fillRoundRect(8, 8, 200, 72, 10, 10)
        g2.font  = Font("Monospaced", Font.BOLD, 14)
        g2.color = Color(255, 220, 80)
        g2.drawString("SCORE  $score",  18, 30)
        g2.color = Color(180, 220, 255)
        g2.drawString("BEST   $hiScore", 18, 50)
        g2.color = Color(255, 140, 60)
        g2.drawString("SPEED  ${"%.1f".format(gameSpeed)}x", 18, 70)

        // power-up icons (text labels instead of emoji to avoid font issues)
        var iconX = GAME_WIN_W - 44
        if (player.hasShield) {
            g2.color = Color(80, 200, 255, 200)
            g2.fillRoundRect(iconX, 10, 32, 32, 8, 8)
            g2.color = Color.WHITE
            g2.font  = Font("Monospaced", Font.BOLD, 11)
            g2.drawString("SHD", iconX + 2, 30)
            iconX -= 38
        }
        if (player.hasSlow) {
            g2.color = Color(255, 200, 60, 200)
            g2.fillRoundRect(iconX, 10, 32, 32, 8, 8)
            g2.color = Color.WHITE
            g2.font  = Font("Monospaced", Font.BOLD, 11)
            g2.drawString("SLW", iconX + 2, 30)
        }

        g2.font  = Font("Monospaced", Font.PLAIN, 10)
        g2.color = Color(100, 100, 120)
        g2.drawString("LEFT / RIGHT  to steer", GAME_WIN_W / 2 - 76, GAME_WIN_H - 8)
    }

    private fun drawTitleScreen(g2: Graphics2D) {
        g2.color = Color(0, 0, 0, 160); g2.fillRect(0, 0, GAME_WIN_W, GAME_WIN_H)
        g2.font  = Font("Monospaced", Font.BOLD, 42)
        for (b in 6 downTo 1) {
            g2.color = Color(255, 160, 0, 18 * b)
            g2.drawString("ROAD RUSH", GAME_WIN_W / 2 - 162 + b, GAME_WIN_H / 2 - 70 + b)
        }
        g2.color = Color(255, 200, 60)
        g2.drawString("ROAD RUSH", GAME_WIN_W / 2 - 162, GAME_WIN_H / 2 - 70)

        g2.font  = Font("Monospaced", Font.PLAIN, 14)
        g2.color = Color(200, 200, 200)
        g2.drawString("LEFT / RIGHT  to steer your car", GAME_WIN_W / 2 - 148, GAME_WIN_H / 2 - 10)
        g2.drawString("[S] Shield   [T] Slow-Mo   [*] Bonus", GAME_WIN_W / 2 - 156, GAME_WIN_H / 2 + 18)

        g2.font  = Font("Monospaced", Font.BOLD, 16)
        g2.color = Color(100, 255, 140)
        if ((System.currentTimeMillis() / 500) % 2L == 0L)
            g2.drawString("Press  SPACE  to Start", GAME_WIN_W / 2 - 130, GAME_WIN_H / 2 + 60)
    }

    private fun drawGameOverScreen(g2: Graphics2D) {
        g2.color = Color(0, 0, 0, 170); g2.fillRect(0, 0, GAME_WIN_W, GAME_WIN_H)
        g2.font  = Font("Monospaced", Font.BOLD, 44)
        val title = "CRASH!"
        for (b in 6 downTo 1) {
            g2.color = Color(255, 60, 60, 18 * b)
            g2.drawString(title, GAME_WIN_W / 2 - 88 + b, GAME_WIN_H / 2 - 60 + b)
        }
        g2.color = Color(255, 80, 80)
        g2.drawString(title, GAME_WIN_W / 2 - 88, GAME_WIN_H / 2 - 60)

        g2.font  = Font("Monospaced", Font.PLAIN, 18)
        g2.color = Color.WHITE
        g2.drawString("Score : $score",   GAME_WIN_W / 2 - 70, GAME_WIN_H / 2)
        g2.drawString("Best  : $hiScore", GAME_WIN_W / 2 - 70, GAME_WIN_H / 2 + 30)

        g2.font  = Font("Monospaced", Font.BOLD, 15)
        g2.color = Color(255, 220, 60)
        if ((System.currentTimeMillis() / 500) % 2L == 0L)
            g2.drawString("Press  SPACE  to Restart", GAME_WIN_W / 2 - 142, GAME_WIN_H / 2 + 74)
    }

    // ── restart ───────────────────────────────
    private fun restart() {
        player.posX        = (GAME_WIN_W / 2 - PLAYER_CAR_W / 2).toFloat()
        player.velX        = 0f
        player.hasShield   = false; player.shieldTicks = 0
        player.hasSlow     = false; player.slowTicks   = 0
        enemies.clear(); powerUps.clear(); sparks.clear()
        score = 0; gameSpeed = 4f; distance = 0f; bgScroll = 0f
        enemyTick = 0; enemyRate = 80; powerTick = 0
        gameOver = false
    }
}

// ─────────────────────────────────────────────
//  ENTRY POINT
// ─────────────────────────────────────────────
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Road Rush")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        val panel = CarGamePanel()
        frame.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        panel.requestFocusInWindow()
    }
}