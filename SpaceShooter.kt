import java.awt.*
import java.awt.event.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import javax.swing.*
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────────
//  CONSTANTS
// ─────────────────────────────────────────────
const val W = 800; const val H = 600
const val PLAYER_W = 48; const val PLAYER_H = 36
const val BULLET_W = 4;  const val BULLET_H = 14
const val ENEMY_W  = 38; const val ENEMY_H  = 30
const val PLAYER_SPEED  = 5
const val BULLET_SPEED  = 9
const val STAR_COUNT    = 110

// ─────────────────────────────────────────────
//  STAR (parallax background)
// ─────────────────────────────────────────────
data class Star(var x: Float, var y: Float, val speed: Float, val size: Int, val brightness: Int)

fun makeStar() = Star(
    Random.nextFloat() * W,
    Random.nextFloat() * H,
    Random.nextFloat() * 1.8f + 0.3f,
    Random.nextInt(1, 4),
    Random.nextInt(120, 255)
)

// ─────────────────────────────────────────────
//  BULLET
// ─────────────────────────────────────────────
class Bullet(var x: Int, var y: Int) {
    var alive = true
    fun update() { y -= BULLET_SPEED; if (y + BULLET_H < 0) alive = false }
    fun rect() = Rectangle(x - BULLET_W / 2, y - BULLET_H, BULLET_W, BULLET_H)
    fun draw(g2: Graphics2D) {
        if (!alive) return
        val r = rect()
        // glow core
        g2.color = Color(180, 240, 255, 80)
        g2.fillRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 4, 4)
        g2.color = Color(100, 200, 255)
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 3, 3)
        g2.color = Color.WHITE
        g2.fillRoundRect(r.x + 1, r.y + 2, r.width - 2, r.height / 3, 2, 2)
    }
}

// ─────────────────────────────────────────────
//  EXPLOSION PARTICLE
// ─────────────────────────────────────────────
class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, val color: Color) {
    var life = 1.0f
    val decay = Random.nextFloat() * 0.04f + 0.02f
    var alive = true
    fun update() { x += vx; y += vy; life -= decay; if (life <= 0) alive = false }
    fun draw(g2: Graphics2D) {
        val alpha = (life * 255).toInt().coerceIn(0, 255)
        g2.color = Color(color.red, color.green, color.blue, alpha)
        val sz = (life * 5).toInt().coerceAtLeast(1)
        g2.fillOval((x - sz / 2).toInt(), (y - sz / 2).toInt(), sz, sz)
    }
}

fun explode(x: Float, y: Float, list: MutableList<Particle>) {
    val cols = listOf(Color(255,180,40), Color(255,100,30), Color(255,220,100), Color(200,60,20), Color.WHITE)
    repeat(28) {
        val angle = Random.nextFloat() * 2 * PI.toFloat()
        val spd   = Random.nextFloat() * 3.5f + 0.5f
        list += Particle(x, y, cos(angle)*spd, sin(angle)*spd, cols.random())
    }
}

// ─────────────────────────────────────────────
//  PLAYER
// ─────────────────────────────────────────────
class Player {
    var x = W / 2 - PLAYER_W / 2
    var y = H - PLAYER_H - 16
    var left = false; var right = false
    val bullets = mutableListOf<Bullet>()
    var shootCooldown = 0
    var shield = 3          // lives visualised as shield pips
    var invincible = 0      // invincibility frames after hit

    fun update() {
        if (left  && x > 0)          x -= PLAYER_SPEED
        if (right && x + PLAYER_W < W) x += PLAYER_SPEED
        if (shootCooldown > 0) shootCooldown--
        if (invincible > 0)   invincible--
        bullets.removeIf { !it.alive }
        bullets.forEach { it.update() }
    }

    fun shoot() {
        if (shootCooldown > 0) return
        bullets += Bullet(x + PLAYER_W / 2, y + 4)
        shootCooldown = 12
    }

    fun rect() = Rectangle(x, y, PLAYER_W, PLAYER_H)

    fun draw(g2: Graphics2D) {
        if (invincible > 0 && (invincible / 4) % 2 == 0) return   // blink when hit

        // engine glow
        g2.color = Color(100, 180, 255, 90)
        g2.fillOval(x + PLAYER_W/2 - 10, y + PLAYER_H - 6, 20, 14)

        // body
        val body = Polygon()
        body.addPoint(x + PLAYER_W / 2, y)
        body.addPoint(x + PLAYER_W,     y + PLAYER_H)
        body.addPoint(x + PLAYER_W * 3/4, y + PLAYER_H - 8)
        body.addPoint(x + PLAYER_W / 4,  y + PLAYER_H - 8)
        body.addPoint(x,                 y + PLAYER_H)
        g2.color = Color(60, 160, 255)
        g2.fillPolygon(body)
        g2.color = Color(130, 210, 255)
        g2.drawPolygon(body)

        // cockpit
        val cock = Polygon()
        cock.addPoint(x + PLAYER_W / 2, y + 8)
        cock.addPoint(x + PLAYER_W * 2/3, y + PLAYER_H / 2)
        cock.addPoint(x + PLAYER_W / 3,   y + PLAYER_H / 2)
        g2.color = Color(180, 230, 255, 200)
        g2.fillPolygon(cock)

        // wings
        g2.color = Color(40, 100, 200)
        g2.fillPolygon(intArrayOf(x, x + PLAYER_W/4, x + PLAYER_W/2), intArrayOf(y+PLAYER_H, y+PLAYER_H-8, y+PLAYER_H/2), 3)
        g2.fillPolygon(intArrayOf(x+PLAYER_W, x+PLAYER_W*3/4, x+PLAYER_W/2), intArrayOf(y+PLAYER_H, y+PLAYER_H-8, y+PLAYER_H/2), 3)

        bullets.forEach { it.draw(g2) }
    }
}

// ─────────────────────────────────────────────
//  ENEMY  (3 types)
// ─────────────────────────────────────────────
enum class EType { GRUNT, SPEEDER, TANK }

class Enemy(var x: Int, var y: Int, val type: EType) {
    var alive = true
    var hp = when (type) { EType.GRUNT -> 1; EType.SPEEDER -> 1; EType.TANK -> 3 }
    val speed = when (type) { EType.GRUNT -> 1.4f; EType.SPEEDER -> 2.8f; EType.TANK -> 0.8f }
    val score = when (type) { EType.GRUNT -> 10; EType.SPEEDER -> 20; EType.TANK -> 50 }
    var vy = speed
    var vx = if (type == EType.SPEEDER) (if (Random.nextBoolean()) 1.2f else -1.2f) else 0f
    var drift = 0f

    fun update() {
        drift += 0.03f
        y += vy.toInt()
        if (type == EType.GRUNT) x += (sin(drift.toDouble()) * 0.8).toInt()
        if (type == EType.SPEEDER) {
            x += vx.toInt()
            if (x <= 0 || x + ENEMY_W >= W) vx = -vx
        }
        if (y > H) alive = false
    }

    fun rect() = Rectangle(x, y, ENEMY_W, ENEMY_H)

    fun draw(g2: Graphics2D) {
        when (type) {
            EType.GRUNT   -> drawGrunt(g2)
            EType.SPEEDER -> drawSpeeder(g2)
            EType.TANK    -> drawTank(g2)
        }
    }

    private fun drawGrunt(g2: Graphics2D) {
        g2.color = Color(200, 60, 60)
        g2.fillOval(x, y + 6, ENEMY_W, ENEMY_H - 6)
        g2.color = Color(255, 100, 100)
        g2.fillOval(x + 8, y, ENEMY_W - 16, 14)
        g2.color = Color(255, 60, 60)
        g2.drawOval(x, y + 6, ENEMY_W, ENEMY_H - 6)
        // eyes
        g2.color = Color(255, 220, 0)
        g2.fillOval(x + 8, y + 12, 6, 6)
        g2.fillOval(x + ENEMY_W - 14, y + 12, 6, 6)
    }

    private fun drawSpeeder(g2: Graphics2D) {
        val p = Polygon()
        p.addPoint(x + ENEMY_W/2, y + ENEMY_H)
        p.addPoint(x,             y)
        p.addPoint(x + ENEMY_W/2, y + 8)
        p.addPoint(x + ENEMY_W,   y)
        g2.color = Color(255, 160, 0)
        g2.fillPolygon(p)
        g2.color = Color(255, 220, 80)
        g2.drawPolygon(p)
        g2.color = Color(255, 255, 150, 160)
        g2.fillOval(x + ENEMY_W/2 - 5, y + 4, 10, 10)
    }

    private fun drawTank(g2: Graphics2D) {
        g2.color = Color(100, 60, 180)
        g2.fillRoundRect(x, y, ENEMY_W, ENEMY_H, 8, 8)
        g2.color = Color(160, 100, 255)
        g2.drawRoundRect(x, y, ENEMY_W, ENEMY_H, 8, 8)
        // HP pips
        for (i in 0 until hp) {
            g2.color = Color(220, 180, 255)
            g2.fillRect(x + 4 + i * 10, y + ENEMY_H - 7, 8, 4)
        }
        g2.color = Color(200, 160, 255, 180)
        g2.fillOval(x + ENEMY_W/2 - 6, y + 4, 12, 12)
    }
}

// ─────────────────────────────────────────────
//  GAME PANEL
// ─────────────────────────────────────────────
class GamePanel : JPanel(), KeyListener {

    private val player  = Player()
    private val enemies = mutableListOf<Enemy>()
    private val particles = mutableListOf<Particle>()
    private val stars   = MutableList(STAR_COUNT) { makeStar() }

    private var score   = 0
    private var hiScore = 0
    private var level   = 1
    private var spawnTimer  = 0
    private var spawnRate   = 60   // ticks between spawns
    private var gameOver = false
    private var started  = false

    private val backBuf = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)

    init {
        preferredSize = Dimension(W, H)
        background = Color.BLACK
        isFocusable = true
        addKeyListener(this)
        Timer(16) { tick() }.start()
    }

    // ── GAME LOOP ───────────────────────────────
    private fun tick() {
        if (!started || gameOver) { repaint(); return }

        // stars
        stars.forEach { s -> s.y += s.speed; if (s.y > H) { s.y = 0f; s.x = Random.nextFloat() * W } }

        // spawning
        spawnTimer++
        if (spawnTimer >= spawnRate) {
            spawnTimer = 0
            spawnEnemy()
        }

        // level up every 300 score
        val newLevel = score / 300 + 1
        if (newLevel != level) { level = newLevel; spawnRate = (60 - level * 5).coerceAtLeast(20) }

        player.update()
        enemies.forEach { it.update() }
        particles.removeIf { !it.alive }
        particles.forEach  { it.update() }

        // bullet ↔ enemy collision
        val deadBullets = mutableSetOf<Bullet>()
        val deadEnemies = mutableSetOf<Enemy>()
        for (b in player.bullets.filter { it.alive }) {
            for (e in enemies.filter { it.alive }) {
                if (b.rect().intersects(e.rect())) {
                    e.hp--
                    deadBullets += b
                    if (e.hp <= 0) {
                        deadEnemies += e
                        score += e.score
                        if (score > hiScore) hiScore = score
                        explode(e.x + ENEMY_W / 2f, e.y + ENEMY_H / 2f, particles)
                    }
                    break
                }
            }
        }
        deadBullets.forEach { it.alive = false }
        deadEnemies.forEach  { it.alive = false }
        enemies.removeIf { !it.alive }

        // enemy reaches bottom OR hits player
        for (e in enemies) {
            if (e.y + ENEMY_H >= H) { triggerGameOver(); return }
            if (e.rect().intersects(player.rect()) && player.invincible == 0) {
                player.shield--
                player.invincible = 90
                explode(e.x + ENEMY_W / 2f, e.y + ENEMY_H / 2f, particles)
                e.alive = false
                enemies.remove(e)
                if (player.shield <= 0) { triggerGameOver(); return }
                break
            }
        }

        repaint()
    }

    private fun spawnEnemy() {
        val ex = Random.nextInt(0, W - ENEMY_W)
        val type = when {
            level >= 3 && Random.nextInt(10) < 2 -> EType.TANK
            level >= 2 && Random.nextInt(10) < 4 -> EType.SPEEDER
            else -> EType.GRUNT
        }
        enemies += Enemy(ex, -ENEMY_H, type)
        // extra enemy per level above 1
        if (level > 1 && Random.nextInt(3) == 0) enemies += Enemy(Random.nextInt(0, W - ENEMY_W), -ENEMY_H - 40, EType.GRUNT)
    }

    private fun triggerGameOver() { gameOver = true; repaint() }

    // ── RENDERING ───────────────────────────────
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = backBuf.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = Color.BLACK; g2.fillRect(0, 0, W, H)

        drawStars(g2)
        if (!started) { drawTitle(g2) }
        else {
            enemies.forEach { it.draw(g2) }
            player.draw(g2)
            particles.forEach { it.draw(g2) }
            drawHUD(g2)
            if (gameOver) drawGameOver(g2)
        }
        g2.dispose()
        g.drawImage(backBuf, 0, 0, null)
    }

    private fun drawStars(g2: Graphics2D) {
        stars.forEach { s ->
            g2.color = Color(s.brightness, s.brightness, s.brightness)
            g2.fillRect(s.x.toInt(), s.y.toInt(), s.size, s.size)
        }
    }

    private fun drawHUD(g2: Graphics2D) {
        g2.font = Font("Monospaced", Font.BOLD, 15)
        g2.color = Color(100, 200, 255)
        g2.drawString("SCORE  $score", 14, 24)
        g2.drawString("HI     $hiScore", 14, 44)
        g2.drawString("LEVEL  $level", 14, 64)

        // shield pips
        for (i in 0 until 3) {
            val col = if (i < player.shield) Color(80, 220, 255) else Color(40, 60, 80)
            g2.color = col
            g2.fillRoundRect(W - 30 - i * 22, 10, 16, 22, 4, 4)
            g2.color = Color(150, 220, 255, 80)
            g2.drawRoundRect(W - 30 - i * 22, 10, 16, 22, 4, 4)
        }
        g2.font = Font("Monospaced", Font.PLAIN, 10)
        g2.color = Color(100, 180, 220)
        g2.drawString("SHIELD", W - 74, 44)

        // controls reminder
        g2.font = Font("Monospaced", Font.PLAIN, 11)
        g2.color = Color(70, 100, 130)
        g2.drawString("◀ ▶  MOVE     SPACE  SHOOT", W / 2 - 108, H - 8)
    }

    private fun drawTitle(g2: Graphics2D) {
        // title glow
        g2.font = Font("Monospaced", Font.BOLD, 52)
        for (blur in 6 downTo 1) {
            g2.color = Color(0, 120, 255, 20 * blur)
            g2.drawString("SPACE ASSAULT", W / 2 - 198 + blur, H / 2 - 80 + blur)
        }
        g2.color = Color(100, 200, 255)
        g2.drawString("SPACE ASSAULT", W / 2 - 198, H / 2 - 80)

        g2.font = Font("Monospaced", Font.PLAIN, 16)
        g2.color = Color(180, 220, 255)
        g2.drawString("← → Move     SPACE Shoot", W / 2 - 126, H / 2)
        g2.color = Color(255, 200, 60)
        g2.font = Font("Monospaced", Font.BOLD, 18)
        val blink = (System.currentTimeMillis() / 500) % 2 == 0L
        if (blink) g2.drawString("Press  SPACE  to Start", W / 2 - 140, H / 2 + 50)

        g2.font = Font("Monospaced", Font.PLAIN, 13)
        g2.color = Color(120, 160, 200)
        g2.drawString("🔴 Grunt  +10     🟠 Speeder  +20     🟣 Tank  +50", W / 2 - 196, H / 2 + 100)
    }

    private fun drawGameOver(g2: Graphics2D) {
        g2.color = Color(0, 0, 10, 190); g2.fillRect(0, 0, W, H)
        g2.font = Font("Monospaced", Font.BOLD, 48)
        val title = "GAME  OVER"
        // glow
        for (b in 5 downTo 1) {
            g2.color = Color(255, 60, 60, 18 * b)
            g2.drawString(title, W / 2 - 172 + b, H / 2 - 60 + b)
        }
        g2.color = Color(255, 80, 80)
        g2.drawString(title, W / 2 - 172, H / 2 - 60)

        g2.font = Font("Monospaced", Font.PLAIN, 18)
        g2.color = Color.WHITE
        g2.drawString("Score : $score", W / 2 - 80, H / 2)
        g2.drawString("Best  : $hiScore", W / 2 - 80, H / 2 + 28)

        g2.font = Font("Monospaced", Font.BOLD, 16)
        g2.color = Color(255, 200, 60)
        val blink = (System.currentTimeMillis() / 500) % 2 == 0L
        if (blink) g2.drawString("Press  R  to  Play  Again", W / 2 - 140, H / 2 + 76)
    }

    // ── INPUT ───────────────────────────────────
    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT  -> player.left  = true
            KeyEvent.VK_RIGHT -> player.right = true
            KeyEvent.VK_SPACE -> {
                if (!started)        { started = true; return }
                if (gameOver)        return
                player.shoot()
            }
            KeyEvent.VK_R -> if (gameOver) restart()
        }
    }

    override fun keyReleased(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT  -> player.left  = false
            KeyEvent.VK_RIGHT -> player.right = false
        }
    }

    override fun keyTyped(e: KeyEvent) {}

    // ── RESTART ─────────────────────────────────
    private fun restart() {
        player.apply { x = W/2 - PLAYER_W/2; y = H - PLAYER_H - 16; left=false; right=false; shield=3; invincible=0; bullets.clear() }
        enemies.clear(); particles.clear()
        score=0; level=1; spawnRate=60; spawnTimer=0; gameOver=false
    }
}

// ─────────────────────────────────────────────
//  ENTRY POINT
// ─────────────────────────────────────────────
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("🚀  Space Assault")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        val panel = GamePanel()
        frame.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        panel.requestFocusInWindow()
    }
}