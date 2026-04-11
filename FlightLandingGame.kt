import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────
//  CONSTANTS
// ─────────────────────────────────────────────
const val FLT_GAME_W       = 900
const val FLT_GAME_H       = 600
const val FLT_GROUND_Y     = 540
const val FLT_RUNWAY_X     = 580
const val FLT_RUNWAY_LEN   = 220
const val FLT_MAX_SPD      = 3.2f
const val FLT_MAX_ANGLE    = 0.30f
const val FLT_GRAVITY      = 0.018f
const val FLT_LIFT         = 0.045f
const val FLT_DRAG         = 0.0012f
const val FLT_FUEL_MAX     = 100f
const val FLT_FUEL_BURN    = 0.04f
const val FLT_WIND_STR     = 0.008f

// ─────────────────────────────────────────────
//  CLOUD
// ─────────────────────────────────────────────
class FltCloud(var cx: Float, var cy: Float, val radius: Float, val spd: Float) {
    fun update() { cx -= spd; if (cx + radius * 2 < 0) cx = FLT_GAME_W + radius }
    fun draw(g2: Graphics2D) {
        g2.color = Color(255, 255, 255, 180)
        g2.fillOval((cx - radius).toInt(),           (cy - radius * 0.6f).toInt(),
            (radius * 2).toInt(),              (radius * 1.2f).toInt())
        g2.fillOval((cx - radius * 0.5f).toInt(),    (cy - radius).toInt(),
            (radius * 1.4f).toInt(),           (radius * 1.4f).toInt())
        g2.fillOval((cx + radius * 0.3f).toInt(),    (cy - radius * 0.7f).toInt(),
            (radius * 1.2f).toInt(),           (radius * 1.1f).toInt())
    }
}

// ─────────────────────────────────────────────
//  PARTICLE  — renamed FltParticle to avoid clash
// ─────────────────────────────────────────────
class FltParticle(
    var px: Float, var py: Float,
    val vx: Float, val vy: Float,
    val col: Color, val maxLife: Int
) {
    var life = maxLife
    val alive get() = life > 0
    fun update() { px += vx; py += vy; life-- }
    fun draw(g2: Graphics2D) {
        val alpha = (255 * life / maxLife).coerceIn(0, 255)
        val sz    = (6  * life / maxLife).coerceAtLeast(1)
        g2.color  = Color(col.red, col.green, col.blue, alpha)
        g2.fillOval((px - sz / 2).toInt(), (py - sz / 2).toInt(), sz, sz)
    }
}

// ─────────────────────────────────────────────
//  FLOAT RANGE HELPERS  — prefixed to avoid clash
// ─────────────────────────────────────────────
fun ClosedRange<Float>.fltRandom() =
    start + (endInclusive - start) * kotlin.random.Random.nextFloat()
fun IntRange.fltRandom() = kotlin.random.Random.nextInt(first, last + 1)

// ─────────────────────────────────────────────
//  PLANE
// ─────────────────────────────────────────────
class Plane {
    var posX    = 60f;  var posY  = 120f
    var velX    = 2.8f; var velY  = 0f
    var angle   = 0f
    var speed   = 2.8f
    var fuel    = FLT_FUEL_MAX
    var alive   = true
    var landed  = false
    val exhaust = mutableListOf<FltParticle>()

    var throttleUp   = false; var throttleDown = false
    var tiltLeft     = false; var tiltRight    = false
    var windX        = 0f

    fun update() {
        if (!alive || landed) return
        if (tiltLeft)  angle -= 0.018f
        if (tiltRight) angle += 0.018f
        angle = angle.coerceIn(-0.60f, 0.70f)

        if (throttleUp   && fuel > 0) { speed = (speed - 0.06f).coerceAtLeast(0.2f); fuel -= FLT_FUEL_BURN }
        if (throttleDown && fuel > 0) { speed = (speed + 0.05f).coerceAtMost(7f);    fuel -= FLT_FUEL_BURN * 1.5f }
        fuel = fuel.coerceAtLeast(0f)

        val lift = speed * FLT_LIFT
        val drag = speed * speed * FLT_DRAG
        velX = speed * cos(angle.toDouble()).toFloat() - drag + windX
        velY = speed * sin(angle.toDouble()).toFloat() + FLT_GRAVITY - lift
        posX += velX;  posY += velY
        if (posX < -60) posX = FLT_GAME_W + 40f

        if ((throttleDown || speed > 1f) && fuel > 0) {
            val ex = posX - cos(angle.toDouble()).toFloat() * 28f
            val ey = posY - sin(angle.toDouble()).toFloat() * 28f
            repeat(2) {
                exhaust += FltParticle(
                    ex + (-3..3).fltRandom().toFloat(),
                    ey + (-2..2).fltRandom().toFloat(),
                    (-0.4f..0f).fltRandom(), (-0.3f..0.3f).fltRandom(),
                    Color(200, 200, 200), 30
                )
            }
        }
        exhaust.removeIf { !it.alive }
        exhaust.forEach  { it.update() }
    }

    fun landingSpeed() = sqrt(velX * velX + velY * velY)
}

// ─────────────────────────────────────────────
//  GAME STATE
// ─────────────────────────────────────────────
enum class FlightState { FLYING, LANDED, CRASHED, TITLE }

// ─────────────────────────────────────────────
//  GAME PANEL
// ─────────────────────────────────────────────
class FlightPanel : JPanel() {

    private var state   = FlightState.TITLE
    private var plane   = Plane()
    private val clouds  = mutableListOf<FltCloud>()
    private val sparks  = mutableListOf<FltParticle>()
    private var windX   = 0f
    private var windTimer = 0
    private var score   = 0; private var hiScore = 0; private var level = 1
    private var msg1    = ""; private var msg2 = ""

    private val backBuf = BufferedImage(FLT_GAME_W, FLT_GAME_H, BufferedImage.TYPE_INT_ARGB)

    init {
        preferredSize = Dimension(FLT_GAME_W, FLT_GAME_H)
        isFocusable   = true
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_UP    -> plane.throttleUp   = true
                    KeyEvent.VK_DOWN  -> plane.throttleDown = true
                    KeyEvent.VK_LEFT  -> plane.tiltLeft     = true
                    KeyEvent.VK_RIGHT -> plane.tiltRight    = true
                    KeyEvent.VK_SPACE, KeyEvent.VK_R -> onAction()
                }
            }
            override fun keyReleased(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_UP    -> plane.throttleUp   = false
                    KeyEvent.VK_DOWN  -> plane.throttleDown = false
                    KeyEvent.VK_LEFT  -> plane.tiltLeft     = false
                    KeyEvent.VK_RIGHT -> plane.tiltRight    = false
                }
            }
        })
        spawnClouds()
        Timer(16) { tick() }.start()
    }

    private fun onAction() {
        if (state != FlightState.FLYING) startLevel()
    }

    private fun startLevel() {
        plane       = Plane()
        plane.posX  = 60f
        plane.posY  = (80 + level * 10).coerceAtMost(200).toFloat()
        plane.speed = 2.6f + level * 0.08f
        plane.velX  = plane.speed
        sparks.clear()
        state = FlightState.FLYING
        msg1 = ""; msg2 = ""
    }

    private fun spawnClouds() {
        clouds.clear()
        repeat(7) {
            clouds += FltCloud(
                (30..FLT_GAME_W).fltRandom().toFloat(),
                (40..300).fltRandom().toFloat(),
                (30..70).fltRandom().toFloat(),
                (0.2f..0.7f).fltRandom()
            )
        }
    }

    private fun tick() {
        clouds.forEach { it.update() }
        sparks.forEach { it.update() }
        sparks.removeIf { !it.alive }
        windTimer++
        if (windTimer > 180) {
            windTimer = 0
            windX = (-1..1).fltRandom() * FLT_WIND_STR * level
        }
        plane.windX = windX
        if (state == FlightState.FLYING) { plane.update(); checkCollisions() }
        repaint()
    }

    private fun checkCollisions() {
        val px = plane.posX; val py = plane.posY
        if (py >= FLT_GROUND_Y) {
            val onRunway = px >= FLT_RUNWAY_X && px <= FLT_RUNWAY_X + FLT_RUNWAY_LEN
            if (onRunway) {
                val spd = plane.landingSpeed(); val ang = abs(plane.angle)
                if (spd <= FLT_MAX_SPD && ang <= FLT_MAX_ANGLE) successLanding(spd, ang)
                else crash(if (spd > FLT_MAX_SPD) "Too fast!" else "Angle too steep!")
            } else crash("Missed the runway!")
            return
        }
        if (py < 0) { plane.posY = 0f; plane.velY = 0.1f }
        if (px > FLT_GAME_W + 60) crash("Flew past the runway!")
    }

    private fun successLanding(spd: Float, ang: Float) {
        state = FlightState.LANDED
        val pts = ((100 + (FLT_MAX_SPD - spd) * 50 + (FLT_MAX_ANGLE - ang) * 100).toInt() * level)
        score += pts; if (score > hiScore) hiScore = score; level++
        msg1 = "PERFECT LANDING!"; msg2 = "+$pts pts   |   Press SPACE for Level $level"
        repeat(20) {
            sparks += FltParticle(
                plane.posX + (-10..10).fltRandom().toFloat(), FLT_GROUND_Y.toFloat(),
                (-1.5f..1.5f).fltRandom(), (-3f..-0.5f).fltRandom(),
                Color(255, 220, 80), 40
            )
        }
    }

    private fun crash(reason: String) {
        state = FlightState.CRASHED
        msg1 = "CRASHED!  $reason"; msg2 = "Press SPACE to retry"
        repeat(50) {
            sparks += FltParticle(
                plane.posX + (-8..8).fltRandom().toFloat(),
                plane.posY + (-8..8).fltRandom().toFloat(),
                (-3f..3f).fltRandom(), (-3f..3f).fltRandom(),
                listOf(Color(255,80,20), Color(255,180,0), Color(255,255,100)).random(), 55
            )
        }
        plane.alive = false
    }

    // ── render ────────────────────────────────
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = backBuf.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        drawSky(g2); drawClouds(g2); drawGround(g2); drawRunway(g2)
        sparks.forEach { it.draw(g2) }
        if (state != FlightState.TITLE) {
            plane.exhaust.forEach { it.draw(g2) }
            if (plane.alive) drawPlane(g2)
        }
        drawHUD(g2)
        when (state) {
            FlightState.TITLE   -> drawTitle(g2)
            FlightState.LANDED  -> drawOverlay(g2, Color(60, 200, 100, 180), msg1, msg2)
            FlightState.CRASHED -> drawOverlay(g2, Color(200, 50, 50, 180),  msg1, msg2)
            FlightState.FLYING  -> {}
        }
        g2.dispose()
        g.drawImage(backBuf, 0, 0, null)
    }

    private fun drawSky(g2: Graphics2D) {
        val grad = GradientPaint(0f, 0f, Color(30, 80, 160), 0f, FLT_GROUND_Y.toFloat(), Color(130, 190, 255))
        g2.paint = grad; g2.fillRect(0, 0, FLT_GAME_W, FLT_GAME_H); g2.paint = null
    }

    private fun drawClouds(g2: Graphics2D) = clouds.forEach { it.draw(g2) }

    private fun drawGround(g2: Graphics2D) {
        val grad = GradientPaint(0f, FLT_GROUND_Y.toFloat(), Color(60, 120, 40),
            0f, FLT_GAME_H.toFloat(),   Color(40, 80, 25))
        g2.paint = grad
        g2.fillRect(0, FLT_GROUND_Y, FLT_GAME_W, FLT_GAME_H - FLT_GROUND_Y)
        g2.paint = null
        g2.color = Color(30, 80, 20); g2.stroke = BasicStroke(2f)
        g2.drawLine(0, FLT_GROUND_Y, FLT_GAME_W, FLT_GROUND_Y); g2.stroke = BasicStroke(1f)
    }

    private fun drawRunway(g2: Graphics2D) {
        g2.color = Color(60, 60, 65)
        g2.fillRect(FLT_RUNWAY_X, FLT_GROUND_Y - 6, FLT_RUNWAY_LEN, 10)
        g2.color = Color(255, 255, 255, 200); g2.stroke = BasicStroke(3f)
        g2.drawLine(FLT_RUNWAY_X, FLT_GROUND_Y - 6, FLT_RUNWAY_X, FLT_GROUND_Y + 4)
        g2.drawLine(FLT_RUNWAY_X + FLT_RUNWAY_LEN, FLT_GROUND_Y - 6, FLT_RUNWAY_X + FLT_RUNWAY_LEN, FLT_GROUND_Y + 4)
        g2.stroke = BasicStroke(2f)
        var dx = FLT_RUNWAY_X + 20
        while (dx < FLT_RUNWAY_X + FLT_RUNWAY_LEN - 20) {
            g2.drawLine(dx, FLT_GROUND_Y - 1, dx + 20, FLT_GROUND_Y - 1); dx += 36
        }
        g2.stroke = BasicStroke(1f)
        g2.color = Color(255, 255, 180, 160); g2.font = Font("Monospaced", Font.BOLD, 11)
        g2.drawString("LAND HERE", FLT_RUNWAY_X + 68, FLT_GROUND_Y - 10)
        for (i in 0..2) {
            val lx = FLT_RUNWAY_X - 30 - i * 18
            g2.color = if (i == 0) Color(0, 220, 60) else Color(0, 200, 50, 140)
            g2.fillOval(lx, FLT_GROUND_Y - 8, 8, 8)
        }
    }

    private fun drawPlane(g2: Graphics2D) {
        val cx = plane.posX; val cy = plane.posY
        val oldTx = g2.transform
        val at    = AffineTransform.getRotateInstance(plane.angle.toDouble(), cx.toDouble(), cy.toDouble())
        g2.transform = at

        val fuse = Path2D.Float()
        fuse.moveTo((cx-30).toDouble(),(cy-5).toDouble()); fuse.lineTo((cx+30).toDouble(),(cy-3).toDouble())
        fuse.lineTo((cx+36).toDouble(), cy.toDouble());    fuse.lineTo((cx+30).toDouble(),(cy+3).toDouble())
        fuse.lineTo((cx-30).toDouble(),(cy+5).toDouble()); fuse.closePath()
        g2.paint = GradientPaint(cx-30,cy-5f,Color(220,220,230),cx+36,cy+5f,Color(140,150,170))
        g2.fill(fuse); g2.paint = null; g2.color = Color(80,90,110); g2.draw(fuse)

        val wGrad = GradientPaint(cx,cy-28f,Color(180,50,50),cx,cy,Color(220,80,80))
        val wingT = Path2D.Float()
        wingT.moveTo((cx-5).toDouble(),(cy-4).toDouble()); wingT.lineTo((cx+8).toDouble(),(cy-4).toDouble())
        wingT.lineTo((cx+2).toDouble(),(cy-28).toDouble()); wingT.lineTo((cx-10).toDouble(),(cy-28).toDouble()); wingT.closePath()
        val wingB = Path2D.Float()
        wingB.moveTo((cx-5).toDouble(),(cy+4).toDouble()); wingB.lineTo((cx+8).toDouble(),(cy+4).toDouble())
        wingB.lineTo((cx+2).toDouble(),(cy+28).toDouble()); wingB.lineTo((cx-10).toDouble(),(cy+28).toDouble()); wingB.closePath()
        g2.paint = wGrad; g2.fill(wingT); g2.fill(wingB); g2.paint = null
        g2.color = Color(160,40,40); g2.draw(wingT); g2.draw(wingB)

        val tail = Path2D.Float()
        tail.moveTo((cx-22).toDouble(),(cy-4).toDouble()); tail.lineTo((cx-16).toDouble(),(cy-4).toDouble())
        tail.lineTo((cx-20).toDouble(),(cy-16).toDouble()); tail.closePath()
        g2.color = Color(200,60,60); g2.fill(tail)
        g2.color = Color(160,220,255,200); g2.fillOval((cx+14).toInt(),(cy-4).toInt(),14,8)
        if (plane.speed > 1f && plane.fuel > 0) {
            g2.color = Color(255,160,30,140); g2.fillOval((cx-38).toInt(),(cy-4).toInt(),10,8)
        }
        g2.transform = oldTx

        if (plane.posY > FLT_GROUND_Y - 80) {
            g2.color = Color(60,60,60); g2.stroke = BasicStroke(2f)
            val gx = cx; val gy = cy + 6f
            g2.drawLine(gx.toInt(), gy.toInt(), gx.toInt(), (gy+10).toInt())
            g2.drawLine((gx-6).toInt(),(gy+10).toInt(),(gx+6).toInt(),(gy+10).toInt())
            g2.stroke = BasicStroke(1f)
        }
    }

    private fun drawHUD(g2: Graphics2D) {
        if (state == FlightState.TITLE) return
        g2.color = Color(0,0,0,150); g2.fillRoundRect(8,8,220,120,10,10)
        g2.font  = Font("Monospaced", Font.BOLD, 13)
        val spd  = plane.landingSpeed()
        g2.color = if (spd > FLT_MAX_SPD) Color(255,80,80) else Color(100,220,100)
        g2.drawString("SPEED  ${"%.2f".format(spd)} / $FLT_MAX_SPD", 16, 28)
        val angDeg = Math.toDegrees(plane.angle.toDouble())
        g2.color = if (abs(plane.angle) > FLT_MAX_ANGLE) Color(255,80,80) else Color(100,220,100)
        g2.drawString("ANGLE  ${"%.1f".format(angDeg)} deg", 16, 48)
        val alt = (FLT_GROUND_Y - plane.posY).toInt().coerceAtLeast(0)
        g2.color = Color(180,200,255); g2.drawString("ALT    $alt ft", 16, 68)
        val fp = plane.fuel / FLT_FUEL_MAX
        g2.color = Color(60,60,60); g2.fillRoundRect(16,78,160,10,4,4)
        g2.color = when { fp > 0.5f -> Color(60,200,80); fp > 0.2f -> Color(255,180,30); else -> Color(255,60,60) }
        g2.fillRoundRect(16,78,(160*fp).toInt(),10,4,4)
        g2.color = Color(180,180,180); g2.font = Font("Monospaced", Font.PLAIN,10); g2.drawString("FUEL",182,87)
        g2.font = Font("Monospaced", Font.BOLD,12); g2.color = Color(200,200,100)
        val wDir = if (windX > 0.001f) ">>>" else if (windX < -0.001f) "<<<" else "---"
        g2.drawString("WIND  $wDir", 16, 104)
        g2.font = Font("Monospaced", Font.BOLD,13)
        g2.color = Color(255,220,80);  g2.drawString("SCORE $score   LV $level", FLT_GAME_W-210, 24)
        g2.color = Color(160,200,255); g2.drawString("BEST  $hiScore",            FLT_GAME_W-210, 44)
        g2.font = Font("Monospaced", Font.PLAIN,10); g2.color = Color(100,120,160)
        g2.drawString("UP=Slow  DOWN=Throttle  LEFT/RIGHT=Tilt", FLT_GAME_W/2-158, FLT_GAME_H-8)
    }

    private fun drawTitle(g2: Graphics2D) {
        g2.color = Color(0,0,0,140); g2.fillRect(0,0,FLT_GAME_W,FLT_GAME_H)
        g2.font  = Font("Monospaced", Font.BOLD, 48)
        for (b in 5 downTo 1) { g2.color = Color(60,160,255,20*b); g2.drawString("FLIGHT LANDING",FLT_GAME_W/2-246+b,FLT_GAME_H/2-60+b) }
        g2.color = Color(100,200,255); g2.drawString("FLIGHT LANDING",FLT_GAME_W/2-246,FLT_GAME_H/2-60)
        g2.font  = Font("Monospaced", Font.PLAIN,15); g2.color = Color(200,220,255)
        listOf(
            "Land safely on the runway  —  avoid crashing!",
            "UP = Reduce throttle    DOWN = Add throttle",
            "LEFT = Tilt nose up     RIGHT = Tilt nose down",
            "Safe speed < $FLT_MAX_SPD    Safe angle < ${Math.toDegrees(FLT_MAX_ANGLE.toDouble()).toInt()} deg"
        ).forEachIndexed { i, l -> g2.drawString(l, FLT_GAME_W/2-228, FLT_GAME_H/2+i*26) }
        g2.font = Font("Monospaced", Font.BOLD,17); g2.color = Color(100,255,160)
        if ((System.currentTimeMillis()/500)%2L==0L) g2.drawString("Press  SPACE  to Start",FLT_GAME_W/2-140,FLT_GAME_H/2+130)
    }

    private fun drawOverlay(g2: Graphics2D, bgCol: Color, line1: String, line2: String) {
        g2.color = bgCol; g2.fillRoundRect(FLT_GAME_W/2-300,FLT_GAME_H/2-56,600,110,16,16)
        g2.font  = Font("Monospaced", Font.BOLD, 22); g2.color = Color.WHITE
        g2.drawString(line1, FLT_GAME_W/2 - g2.fontMetrics.stringWidth(line1)/2, FLT_GAME_H/2-10)
        g2.font  = Font("Monospaced", Font.PLAIN,14); g2.color = Color(220,240,255)
        g2.drawString(line2, FLT_GAME_W/2 - g2.fontMetrics.stringWidth(line2)/2, FLT_GAME_H/2+24)
    }
}

// ─────────────────────────────────────────────
//  ENTRY POINT
// ─────────────────────────────────────────────
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Flight Landing Simulator")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.add(FlightPanel())
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}