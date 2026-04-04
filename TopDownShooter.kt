package topdownshooter

import java.awt.*
import java.awt.event.*
import java.awt.geom.AffineTransform
import javax.swing.*
import kotlin.math.*
import kotlin.random.Random

// --- Configuration ---
const val GAME_WIDTH = 1000
const val GAME_HEIGHT = 700
const val FPS = 60

// --- Math Helpers ---
data class Vector2(var x: Double, var y: Double) {
    fun add(v: Vector2) { x += v.x; y += v.y }
    fun sub(v: Vector2): Vector2 = Vector2(x - v.x, y - v.y)
    fun mult(scalar: Double) { x *= scalar; y *= scalar }
    fun magnitude(): Double = hypot(x, y)
    fun normalize() {
        val mag = magnitude()
        if (mag != 0.0) { x /= mag; y /= mag }
    }
    fun copy() = Vector2(x, y)
}

// --- Game Entities ---
abstract class GameObject(var pos: Vector2, var radius: Double, var color: Color) {
    var isDead = false
    abstract fun update()
    abstract fun draw(g2d: Graphics2D)

    fun collidesWith(other: GameObject): Boolean {
        val dist = hypot(pos.x - other.pos.x, pos.y - other.pos.y)
        return dist < this.radius + other.radius
    }
}

class Player(pos: Vector2) : GameObject(pos, 15.0, Color(0, 255, 255)) {
    var vel = Vector2(0.0, 0.0)
    var angle = 0.0
    var hp = 100
    val maxHp = 100
    var score = 0

    fun update(keys: Set<Int>, mousePos: Point) {
        // Movement Physics
        val accel = 0.6
        val friction = 0.85

        if (keys.contains(KeyEvent.VK_W) || keys.contains(KeyEvent.VK_UP)) vel.y -= accel
        if (keys.contains(KeyEvent.VK_S) || keys.contains(KeyEvent.VK_DOWN)) vel.y += accel
        if (keys.contains(KeyEvent.VK_A) || keys.contains(KeyEvent.VK_LEFT)) vel.x -= accel
        if (keys.contains(KeyEvent.VK_D) || keys.contains(KeyEvent.VK_RIGHT)) vel.x += accel

        vel.mult(friction)
        pos.add(vel)

        // Clamp to screen
        pos.x = pos.x.coerceIn(radius, GAME_WIDTH - radius)
        pos.y = pos.y.coerceIn(radius, GAME_HEIGHT - radius)

        // Calculate Angle towards mouse
        angle = atan2(mousePos.y - pos.y, mousePos.x - pos.x)
    }

    override fun update() {} // Handled above

    override fun draw(g2d: Graphics2D) {
        val oldTransform = g2d.transform
        g2d.translate(pos.x, pos.y)
        g2d.rotate(angle)

        g2d.color = color
        g2d.stroke = BasicStroke(3f)

        // Draw a spaceship (triangle)
        val xPoints = intArrayOf(15, -10, -10)
        val yPoints = intArrayOf(0, -10, 10)
        g2d.drawPolygon(xPoints, yPoints, 3)

        g2d.transform = oldTransform
    }
}

class Bullet(pos: Vector2, var angle: Double, var speed: Double, var damage: Int) : GameObject(pos, 4.0, Color.WHITE) {
    val vel = Vector2(cos(angle) * speed, sin(angle) * speed)

    override fun update() {
        pos.add(vel)
        if (pos.x < 0 || pos.x > GAME_WIDTH || pos.y < 0 || pos.y > GAME_HEIGHT) {
            isDead = true
        }
    }

    override fun draw(g2d: Graphics2D) {
        g2d.color = color
        g2d.fillOval((pos.x - radius).toInt(), (pos.y - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
    }
}

enum class EnemyType(val color: Color, val speed: Double, val hp: Int, val radius: Double, val points: Int) {
    CHASER(Color(255, 0, 255), 2.5, 10, 14.0, 100),
    FAST(Color(255, 255, 0), 4.5, 5, 10.0, 150),
    TANK(Color(255, 50, 50), 1.2, 50, 25.0, 300)
}

class Enemy(pos: Vector2, val type: EnemyType) : GameObject(pos, type.radius, type.color) {
    var hp = type.hp

    fun update(playerPos: Vector2) {
        val dir = playerPos.sub(pos)
        dir.normalize()
        dir.mult(type.speed)
        pos.add(dir)
    }

    override fun update() {} // Handled above

    override fun draw(g2d: Graphics2D) {
        g2d.color = color
        g2d.stroke = BasicStroke(3f)
        when (type) {
            EnemyType.CHASER -> g2d.drawRect((pos.x - radius).toInt(), (pos.y - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
            EnemyType.FAST -> g2d.drawOval((pos.x - radius).toInt(), (pos.y - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
            EnemyType.TANK -> g2d.fillRoundRect((pos.x - radius).toInt(), (pos.y - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt(), 10, 10)
        }
    }
}

class Particle(pos: Vector2, val vel: Vector2, color: Color) : GameObject(pos, Random.nextDouble(2.0, 5.0), color) {
    var alpha = 255
    val decay = Random.nextInt(5, 15)

    override fun update() {
        pos.add(vel)
        vel.mult(0.9) // friction
        alpha -= decay
        if (alpha <= 0) {
            alpha = 0
            isDead = true
        }
    }

    override fun draw(g2d: Graphics2D) {
        g2d.color = Color(color.red, color.green, color.blue, alpha)
        g2d.fillOval((pos.x - radius).toInt(), (pos.y - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
    }
}

// --- Main Game Engine ---
class GamePanel : JPanel() {
    private val keys = mutableSetOf<Int>()
    private var mousePos = Point(GAME_WIDTH / 2, GAME_HEIGHT / 2)
    private var isShooting = false

    private val player = Player(Vector2(GAME_WIDTH / 2.0, GAME_HEIGHT / 2.0))
    private val bullets = mutableListOf<Bullet>()
    private val enemies = mutableListOf<Enemy>()
    private val particles = mutableListOf<Particle>()

    private var lastShotTime = 0L
    private var frameCount = 0
    private var isGameOver = false

    init {
        preferredSize = Dimension(GAME_WIDTH, GAME_HEIGHT)
        background = Color(20, 20, 25)
        isFocusable = true

        // Input Handling
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) { keys.add(e.keyCode) }
            override fun keyReleased(e: KeyEvent) { keys.remove(e.keyCode) }
        })

        val mouseAdapter = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) { mousePos = e.point }
            override fun mouseDragged(e: MouseEvent) { mousePos = e.point }
            override fun mousePressed(e: MouseEvent) {
                if (isGameOver) restart() else isShooting = true
            }
            override fun mouseReleased(e: MouseEvent) { isShooting = false }
        }
        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)

        // Game Loop
        Timer(1000 / FPS) {
            if (!isGameOver) {
                update()
                checkCollisions()
                spawnEnemies()
            }
            repaint()
        }.start()
    }

    private fun restart() {
        player.hp = player.maxHp
        player.score = 0
        player.pos = Vector2(GAME_WIDTH / 2.0, GAME_HEIGHT / 2.0)
        bullets.clear()
        enemies.clear()
        particles.clear()
        isGameOver = false
        frameCount = 0
    }

    private fun update() {
        frameCount++
        player.update(keys, mousePos)

        // Dynamic Weapon Upgrades based on Score
        var fireCooldown = 200L
        if (player.score > 2000) fireCooldown = 150L
        if (player.score > 5000) fireCooldown = 100L

        // Shooting Logic
        if (isShooting && System.currentTimeMillis() - lastShotTime > fireCooldown) {
            shoot()
            lastShotTime = System.currentTimeMillis()
        }

        bullets.forEach { it.update() }
        enemies.forEach { it.update(player.pos) }
        particles.forEach { it.update() }

        // Cleanup dead entities
        bullets.removeAll { it.isDead }
        enemies.removeAll { it.isDead }
        particles.removeAll { it.isDead }
    }

    private fun shoot() {
        val angle = atan2(mousePos.y - player.pos.y, mousePos.x - player.pos.x)

        if (player.score < 2000) {
            // Single Shot
            bullets.add(Bullet(player.pos.copy(), angle, 15.0, 10))
        } else if (player.score < 5000) {
            // Double Shot
            bullets.add(Bullet(player.pos.copy(), angle - 0.1, 15.0, 10))
            bullets.add(Bullet(player.pos.copy(), angle + 0.1, 15.0, 10))
        } else {
            // Spread Shot
            bullets.add(Bullet(player.pos.copy(), angle, 18.0, 15))
            bullets.add(Bullet(player.pos.copy(), angle - 0.2, 18.0, 15))
            bullets.add(Bullet(player.pos.copy(), angle + 0.2, 18.0, 15))
        }
    }

    private fun spawnEnemies() {
        // Spawn rate gets faster over time
        val spawnRate = max(10, 60 - (frameCount / 300))

        if (frameCount % spawnRate == 0) {
            // Spawn around perimeter
            var ex = 0.0
            var ey = 0.0
            if (Random.nextBoolean()) {
                ex = if (Random.nextBoolean()) -30.0 else GAME_WIDTH + 30.0
                ey = Random.nextDouble(0.0, GAME_HEIGHT.toDouble())
            } else {
                ex = Random.nextDouble(0.0, GAME_WIDTH.toDouble())
                ey = if (Random.nextBoolean()) -30.0 else GAME_HEIGHT + 30.0
            }

            // Randomize enemy type
            val roll = Random.nextInt(100)
            val type = when {
                roll < 15 -> EnemyType.TANK     // 15% chance
                roll < 45 -> EnemyType.FAST     // 30% chance
                else -> EnemyType.CHASER        // 55% chance
            }

            enemies.add(Enemy(Vector2(ex, ey), type))
        }
    }

    private fun checkCollisions() {
        // Bullets hitting Enemies
        for (bullet in bullets) {
            for (enemy in enemies) {
                if (!bullet.isDead && !enemy.isDead && bullet.collidesWith(enemy)) {
                    bullet.isDead = true
                    enemy.hp -= bullet.damage

                    if (enemy.hp <= 0) {
                        enemy.isDead = true
                        player.score += enemy.type.points
                        spawnExplosion(enemy.pos, enemy.color, 20)
                    } else {
                        // Small spark for hit marker
                        spawnExplosion(bullet.pos, Color.WHITE, 3)
                    }
                }
            }
        }

        // Enemies hitting Player
        for (enemy in enemies) {
            if (!enemy.isDead && enemy.collidesWith(player)) {
                enemy.isDead = true
                player.hp -= 15
                spawnExplosion(enemy.pos, enemy.color, 15)
                spawnExplosion(player.pos, player.color, 5)

                if (player.hp <= 0) {
                    player.hp = 0
                    isGameOver = true
                }
            }
        }
    }

    private fun spawnExplosion(pos: Vector2, color: Color, amount: Int) {
        for (i in 0 until amount) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextDouble(2.0, 8.0)
            val vel = Vector2(cos(angle) * speed, sin(angle) * speed)
            particles.add(Particle(pos.copy(), vel, color))
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Grid Background
        g2d.color = Color(30, 30, 40)
        g2d.stroke = BasicStroke(1f)
        for (i in 0..GAME_WIDTH step 50) g2d.drawLine(i, 0, i, GAME_HEIGHT)
        for (i in 0..GAME_HEIGHT step 50) g2d.drawLine(0, i, GAME_WIDTH, i)

        particles.forEach { it.draw(g2d) }
        bullets.forEach { it.draw(g2d) }
        enemies.forEach { it.draw(g2d) }

        if (!isGameOver) {
            player.draw(g2d)
        }

        drawHUD(g2d)
    }

    private fun drawHUD(g2d: Graphics2D) {
        // Score
        g2d.color = Color.WHITE
        g2d.font = Font("Consolas", Font.BOLD, 24)
        g2d.drawString("SCORE: ${player.score}", 20, 40)

        // Weapon Level
        g2d.font = Font("Consolas", Font.PLAIN, 16)
        val weaponStr = when {
            player.score > 5000 -> "Weapon: SPREAD (Lv 3)"
            player.score > 2000 -> "Weapon: DOUBLE (Lv 2)"
            else -> "Weapon: SINGLE (Lv 1)"
        }
        g2d.drawString(weaponStr, 20, 65)

        // Health Bar
        g2d.color = Color.RED
        g2d.fillRect(GAME_WIDTH / 2 - 100, 20, 200, 20)
        g2d.color = Color.GREEN
        g2d.fillRect(GAME_WIDTH / 2 - 100, 20, (player.hp / player.maxHp.toDouble() * 200).toInt(), 20)
        g2d.color = Color.WHITE
        g2d.drawRect(GAME_WIDTH / 2 - 100, 20, 200, 20)

        // Game Over Screen
        if (isGameOver) {
            g2d.color = Color(0, 0, 0, 180)
            g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT)

            g2d.color = Color.RED
            g2d.font = Font("Consolas", Font.BOLD, 72)
            val msg1 = "SYSTEM FAILURE"
            val metrics1 = g2d.fontMetrics
            g2d.drawString(msg1, (GAME_WIDTH - metrics1.stringWidth(msg1)) / 2, GAME_HEIGHT / 2 - 20)

            g2d.color = Color.WHITE
            g2d.font = Font("Consolas", Font.PLAIN, 24)
            val msg2 = "Final Score: ${player.score} | Click to Restart"
            val metrics2 = g2d.fontMetrics
            g2d.drawString(msg2, (GAME_WIDTH - metrics2.stringWidth(msg2)) / 2, GAME_HEIGHT / 2 + 30)
        }
    }
}

fun main() {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    SwingUtilities.invokeLater {
        val frame = JFrame("Neon Top-Down Shooter")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false

        // Hide standard mouse cursor inside the game window for better aiming feel
        val blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
            java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
            Point(0, 0), "blank cursor"
        )
        val panel = GamePanel()
        panel.cursor = blankCursor

        frame.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}