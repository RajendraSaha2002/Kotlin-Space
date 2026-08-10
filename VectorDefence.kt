import java.awt.*
import java.awt.event.*
import java.awt.geom.Ellipse2D
import javax.swing.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Main Application Entry Point.
 */
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Missile Command Vector Defense")
        val panel = VectorDefensePanel()

        frame.add(panel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Main Game Panel handling Physics, Ballistic Collisions, and Vector Rendering.
 */
class VectorDefensePanel : JPanel(), ActionListener, MouseListener {

    companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
        const val FPS = 60
        const val DELAY = 1000 / FPS
        const val MAX_EXPLOSION_RADIUS = 38.0
    }

    enum class GameState { START, PLAYING, WAVE_CLEARED, GAMEOVER }

    data class City(val x: Double, val y: Double, var alive: Boolean = true)

    data class Battery(
        val x: Double,
        val y: Double,
        var ammo: Int = 10,
        val maxAmmo: Int = 10,
        var alive: Boolean = true
    )

    // Ballistic Trajectory Enemy Missile
    class EnemyMissile(
        val startX: Double,
        val startY: Double,
        val targetX: Double,
        val targetY: Double,
        val speed: Double
    ) {
        var currentX: Double = startX
        var currentY: Double = startY
        var active: Boolean = true

        fun update(): Boolean {
            if (!active) return false
            val dx = targetX - startX
            val dy = targetY - startY
            val dist = hypot(dx, dy)
            if (dist == 0.0) return false

            val currentDist = hypot(currentX - startX, currentY - startY)
            if (currentDist >= dist) {
                currentX = targetX
                currentY = targetY
                return true // Impact at target location
            }

            currentX += (dx / dist) * speed
            currentY += (dy / dist) * speed
            return false
        }
    }

    // Player Counter Interceptor Missile
    class Interceptor(
        val startX: Double,
        val startY: Double,
        val targetX: Double,
        val targetY: Double,
        val speed: Double = 9.0
    ) {
        var currentX: Double = startX
        var currentY: Double = startY
        var active: Boolean = true

        fun update(): Boolean {
            if (!active) return false
            val dx = targetX - startX
            val dy = targetY - startY
            val totalDist = hypot(dx, dy)
            val currentDist = hypot(currentX - startX, currentY - startY)

            if (currentDist >= totalDist) {
                currentX = targetX
                currentY = targetY
                active = false
                return true // Reached target coordinate -> Deploy explosion ring
            }

            currentX += (dx / totalDist) * speed
            currentY += (dy / totalDist) * speed
            return false
        }
    }

    // Expanding Vector Explosion Ring
    class ExplosionRing(
        val x: Double,
        val y: Double,
        val maxRadius: Double = MAX_EXPLOSION_RADIUS,
        val color: Color = Color(0, 255, 220)
    ) {
        var currentRadius = 2.0
        var expanding = true
        var active = true
        private var holdTicks = 0

        fun update() {
            if (!active) return
            if (expanding) {
                currentRadius += 1.4
                if (currentRadius >= maxRadius) {
                    currentRadius = maxRadius
                    expanding = false
                }
            } else {
                holdTicks++
                if (holdTicks > 12) {
                    currentRadius -= 1.2
                    if (currentRadius <= 0) {
                        active = false
                    }
                }
            }
        }
    }

    // State & Score Tracking
    private var state = GameState.START
    private var score = 0
    private var highScore = 0
    private var wave = 1

    private val cities = mutableListOf<City>()
    private val batteries = mutableListOf<Battery>()
    private val enemyMissiles = mutableListOf<EnemyMissile>()
    private val interceptors = mutableListOf<Interceptor>()
    private val explosions = mutableListOf<ExplosionRing>()

    private var missilesToSpawnInWave = 0
    private var spawnTimerCount = 0
    private var spawnInterval = 90
    private var waveBonusTimer = 0

    private val timer = Timer(DELAY, this)

    init {
        preferredSize = Dimension(WIDTH, HEIGHT)
        background = Color(10, 12, 18)
        isFocusable = true
        addMouseListener(this)

        setupDefenseGrid()
        timer.start()
    }

    private fun setupDefenseGrid() {
        cities.clear()
        batteries.clear()

        val groundY = HEIGHT - 35.0

        // 3 Battery Launcher Domes
        batteries.add(Battery(60.0, groundY))
        batteries.add(Battery(WIDTH / 2.0, groundY))
        batteries.add(Battery(WIDTH - 60.0, groundY))

        // 6 Cities
        val cityXPositions = doubleArrayOf(
            130.0, 210.0, 290.0,
            510.0, 590.0, 670.0
        )
        for (x in cityXPositions) {
            cities.add(City(x, groundY))
        }
    }

    private fun startNewWave() {
        enemyMissiles.clear()
        interceptors.clear()
        explosions.clear()

        // Replenish ammo in functional batteries
        for (b in batteries) {
            if (b.alive) b.ammo = b.maxAmmo
        }

        missilesToSpawnInWave = 8 + wave * 3
        spawnInterval = max(25, 90 - wave * 6)
        spawnTimerCount = 0
        state = GameState.PLAYING
    }

    private fun resetFullGame() {
        score = 0
        wave = 1
        setupDefenseGrid()
        startNewWave()
    }

    override fun actionPerformed(e: ActionEvent?) {
        if (state == GameState.PLAYING) {
            updateGameLogic()
        } else if (state == GameState.WAVE_CLEARED) {
            waveBonusTimer++
            if (waveBonusTimer > 120) {
                wave++
                startNewWave()
            }
        }
        repaint()
    }

    /**
     * Active Game Step Physics & Collision Checks
     */
    private fun updateGameLogic() {
        // Spawn Ballistic Enemy Missiles
        spawnTimerCount++
        if (missilesToSpawnInWave > 0 && spawnTimerCount >= spawnInterval) {
            spawnTimerCount = 0
            spawnEnemyMissile()
            missilesToSpawnInWave--
        }

        // Update Counter Interceptors
        val interceptorIter = interceptors.iterator()
        while (interceptorIter.hasNext()) {
            val inc = interceptorIter.next()
            if (inc.update()) {
                // Target reached -> Spawn blast ring
                explosions.add(ExplosionRing(inc.targetX, inc.targetY))
            }
            if (!inc.active) interceptorIter.remove()
        }

        // Update Expanding Vector Explosions
        val expIter = explosions.iterator()
        while (expIter.hasNext()) {
            val exp = expIter.next()
            exp.update()
            if (!exp.active) expIter.remove()
        }

        // Update Enemy Missiles & Test Circular Blast Radius Collisions
        val enemyIter = enemyMissiles.iterator()
        while (enemyIter.hasNext()) {
            val enemy = enemyIter.next()
            val reachedTarget = enemy.update()

            // Check collision with any expanding explosion ring
            var destroyedByBlast = false
            for (exp in explosions) {
                if (!exp.active) continue
                val dist = hypot(enemy.currentX - exp.x, enemy.currentY - exp.y)
                if (dist <= exp.currentRadius) {
                    destroyedByBlast = true
                    score += 25
                    if (score > highScore) highScore = score
                    explosions.add(ExplosionRing(enemy.currentX, enemy.currentY, 18.0, Color(255, 200, 50)))
                    break
                }
            }

            if (destroyedByBlast) {
                enemyIter.remove()
                continue
            }

            if (reachedTarget) {
                handleImpact(enemy.targetX, enemy.targetY)
                enemyIter.remove()
            }
        }

        // Check End-of-Wave or Game Over Conditions
        if (cities.none { it.alive }) {
            state = GameState.GAMEOVER
        } else if (missilesToSpawnInWave <= 0 && enemyMissiles.isEmpty() && interceptors.isEmpty()) {
            // Wave Bonus
            var bonus = 0
            for (c in cities) if (c.alive) bonus += 100
            for (b in batteries) if (b.alive) bonus += b.ammo * 10
            score += bonus
            if (score > highScore) highScore = score

            waveBonusTimer = 0
            state = GameState.WAVE_CLEARED
        }
    }

    private fun spawnEnemyMissile() {
        val startX = Random.nextDouble(40.0, WIDTH - 40.0)
        val startY = 0.0

        // Target an active city or battery
        val validTargets = mutableListOf<Point2D>()
        for (c in cities) if (c.alive) validTargets.add(Point2D(c.x, c.y))
        for (b in batteries) if (b.alive) validTargets.add(Point2D(b.x, b.y))

        val target = if (validTargets.isNotEmpty()) {
            validTargets[Random.nextInt(validTargets.size)]
        } else {
            Point2D(Random.nextDouble(50.0, WIDTH - 50.0), HEIGHT - 35.0)
        }

        val speed = 1.0 + wave * 0.25
        enemyMissiles.add(EnemyMissile(startX, startY, target.x, target.y, speed))
    }

    private fun handleImpact(tx: Double, ty: Double) {
        explosions.add(ExplosionRing(tx, ty, 30.0, Color(255, 60, 60)))

        // Check City Impact
        for (c in cities) {
            if (c.alive && hypot(c.x - tx, c.y - ty) < 25.0) {
                c.alive = false
            }
        }

        // Check Battery Impact
        for (b in batteries) {
            if (b.alive && hypot(b.x - tx, b.y - ty) < 25.0) {
                b.alive = false
                b.ammo = 0
            }
        }
    }

    data class Point2D(val x: Double, val y: Double)

    /**
     * Mouse Click Handler to Deploy Anti-Missile Interceptors
     */
    override fun mousePressed(e: MouseEvent) {
        if (state != GameState.PLAYING) {
            if (state == GameState.START || state == GameState.GAMEOVER) {
                resetFullGame()
            }
            return
        }

        val mx = e.x.toDouble()
        val my = e.y.toDouble()

        if (my > HEIGHT - 70) return

        // Deploy from closest active battery with available ammo
        var bestBattery: Battery? = null
        var minDistance = Double.MAX_VALUE

        for (b in batteries) {
            if (b.alive && b.ammo > 0) {
                val dist = hypot(mx - b.x, my - b.y)
                if (dist < minDistance) {
                    minDistance = dist
                    bestBattery = b
                }
            }
        }

        bestBattery?.let { b ->
            b.ammo--
            interceptors.add(Interceptor(b.x, b.y, mx, my))
        }
    }

    /**
     * Vector Graphics Rendering Engine
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Ground Plane Line
        val groundY = HEIGHT - 35.0
        g2.color = Color(0, 200, 100)
        g2.stroke = BasicStroke(2f)
        g2.drawLine(0, groundY.toInt(), WIDTH, groundY.toInt())

        // Vector Drawings
        drawCities(g2)
        drawBatteries(g2)
        drawEnemyMissiles(g2)
        drawInterceptors(g2)
        drawExplosions(g2)
        drawHUD(g2)
    }

    private fun drawCities(g2: Graphics2D) {
        for (c in cities) {
            if (!c.alive) {
                g2.color = Color(100, 100, 100)
                g2.drawLine((c.x - 12).toInt(), c.y.toInt(), (c.x + 12).toInt(), c.y.toInt())
                continue
            }

            g2.color = Color(0, 230, 255) // Neon Cyan Buildings
            val cx = c.x.toInt()
            val cy = c.y.toInt()

            g2.drawRect(cx - 12, cy - 14, 8, 14)
            g2.drawRect(cx - 3, cy - 20, 8, 20)
            g2.drawRect(cx + 6, cy - 10, 8, 10)
        }
    }

    private fun drawBatteries(g2: Graphics2D) {
        for (b in batteries) {
            val bx = b.x.toInt()
            val by = b.y.toInt()

            if (!b.alive) {
                g2.color = Color(120, 50, 50)
                g2.drawArc(bx - 16, by - 12, 32, 24, 0, 180)
                continue
            }

            g2.color = Color(255, 215, 0) // Neon Yellow Dome
            g2.drawArc(bx - 18, by - 16, 36, 32, 0, 180)
            g2.drawLine(bx, by - 16, bx, by - 22)

            // Ammo Counter
            g2.font = Font("Monospaced", Font.BOLD, 12)
            g2.color = if (b.ammo > 0) Color.WHITE else Color.RED
            g2.drawString("${b.ammo}", bx - 6, by + 18)
        }
    }

    private fun drawEnemyMissiles(g2: Graphics2D) {
        g2.color = Color(255, 50, 80) // Red Vector Lines
        g2.stroke = BasicStroke(2f)

        for (m in enemyMissiles) {
            g2.drawLine(m.startX.toInt(), m.startY.toInt(), m.currentX.toInt(), m.currentY.toInt())
            g2.fillOval(m.currentX.toInt() - 2, m.currentY.toInt() - 2, 5, 5)
        }
    }

    private fun drawInterceptors(g2: Graphics2D) {
        for (inc in interceptors) {
            g2.color = Color(0, 255, 200)
            g2.stroke = BasicStroke(1.5f)
            g2.drawLine(inc.startX.toInt(), inc.startY.toInt(), inc.currentX.toInt(), inc.currentY.toInt())

            // Target Crosshair
            val tx = inc.targetX.toInt()
            val ty = inc.targetY.toInt()
            g2.color = Color(255, 255, 255, 180)
            g2.drawLine(tx - 5, ty, tx + 5, ty)
            g2.drawLine(tx, ty - 5, tx, ty + 5)
        }
    }

    private fun drawExplosions(g2: Graphics2D) {
        for (exp in explosions) {
            val r = exp.currentRadius.toFloat()
            val x = (exp.x - r).toFloat()
            val y = (exp.y - r).toFloat()
            val diameter = r * 2f

            g2.color = exp.color
            g2.stroke = BasicStroke(2f)
            g2.draw(Ellipse2D.Float(x, y, diameter, diameter))

            if (r > 10f) {
                g2.color = Color.WHITE
                g2.draw(Ellipse2D.Float(x + r * 0.3f, y + r * 0.3f, diameter * 0.7f, diameter * 0.7f))
            }
        }
    }

    private fun drawHUD(g2: Graphics2D) {
        g2.font = Font("Monospaced", Font.BOLD, 18)
        g2.color = Color.WHITE

        g2.drawString("SCORE: $score", 20, 30)
        g2.drawString("WAVE: $wave", WIDTH / 2 - 40, 30)
        g2.drawString("HIGH: $highScore", WIDTH - 150, 30)

        if (state == GameState.START || state == GameState.GAMEOVER || state == GameState.WAVE_CLEARED) {
            g2.color = Color(10, 14, 22, 200)
            g2.fillRect(0, 0, WIDTH, HEIGHT)

            g2.color = Color.WHITE
            g2.font = Font("Monospaced", Font.BOLD, 32)

            val title = when (state) {
                GameState.START -> "MISSILE COMMAND DEFENSE"
                GameState.GAMEOVER -> "ALL CITIES DESTROYED"
                GameState.WAVE_CLEARED -> "WAVE $wave CLEARED!"
                else -> ""
            }

            val tw = g2.fontMetrics.stringWidth(title)
            g2.drawString(title, (WIDTH - tw) / 2, HEIGHT / 2 - 30)

            if (state == GameState.START || state == GameState.GAMEOVER) {
                g2.font = Font("Monospaced", Font.PLAIN, 18)
                g2.color = Color(0, 230, 180)
                val sub = "Click Anywhere to Deploy Counter-Missiles"
                val sw = g2.fontMetrics.stringWidth(sub)
                g2.drawString(sub, (WIDTH - sw) / 2, HEIGHT / 2 + 30)
            } else if (state == GameState.WAVE_CLEARED) {
                g2.font = Font("Monospaced", Font.PLAIN, 18)
                g2.color = Color(255, 215, 0)
                val sub = "Incoming Wave ${wave + 1}..."
                val sw = g2.fontMetrics.stringWidth(sub)
                g2.drawString(sub, (WIDTH - sw) / 2, HEIGHT / 2 + 30)
            }
        }
    }

    override fun mouseClicked(e: MouseEvent) {}
    override fun mouseReleased(e: MouseEvent) {}
    override fun mouseEntered(e: MouseEvent) {}
    override fun mouseExited(e: MouseEvent) {}
}