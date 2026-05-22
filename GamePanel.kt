import java.awt.*
import java.awt.event.*
import javax.swing.*

class GamePanel : JPanel(), KeyListener, ActionListener {

    private val player = Unit(100, 250, 100, 20, true)
    private val enemy = Unit(600, 250, 100, 15, false)

    private var playerTurn = true
    private var gameOver = false

    private var score = 0

    private val timer = Timer(30, this)

    init {
        background = Color.BLACK
        addKeyListener(this)
        isFocusable = true
        timer.start()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        drawGrid(g)

        if (player.isAlive())
            player.draw(g)

        if (enemy.isAlive())
            enemy.draw(g)

        g.color = Color.WHITE
        g.drawString("Player Turn: $playerTurn", 10, 20)
        g.drawString("Score: $score", 10, 40)

        if (gameOver) {

            g.font = Font("Arial", Font.BOLD, 30)

            if (player.isAlive())
                g.drawString("YOU WIN", 300, 300)
            else
                g.drawString("GAME OVER", 280, 300)
        }
    }

    private fun drawGrid(g: Graphics) {

        g.color = Color.DARK_GRAY

        for (i in 0..800 step 50) {
            g.drawLine(i, 0, i, 600)
        }

        for (j in 0..600 step 50) {
            g.drawLine(0, j, 800, j)
        }
    }

    override fun keyPressed(e: KeyEvent) {

        if (gameOver)
            return

        if (!playerTurn)
            return

        when (e.keyCode) {

            KeyEvent.VK_RIGHT -> {
                player.x += 50
            }

            KeyEvent.VK_LEFT -> {
                player.x -= 50
            }

            KeyEvent.VK_SPACE -> {

                if (Math.abs(player.x - enemy.x) <= 100) {
                    enemy.health -= player.attack
                }

                playerTurn = false
            }
        }

        checkGame()
        repaint()
    }

    private fun enemyTurn() {

        if (!enemy.isAlive())
            return

        if (Math.abs(enemy.x - player.x) > 100) {
            enemy.x -= 50
        } else {
            player.health -= enemy.attack
        }

        playerTurn = true

        checkGame()
    }

    private fun checkGame() {

        if (!enemy.isAlive()) {

            gameOver = true
            score = 100

            val scoreService = ScoreService()
            scoreService.saveScore(1, score)
        }

        if (!player.isAlive()) {
            gameOver = true
        }
    }

    override fun actionPerformed(e: ActionEvent?) {

        if (!playerTurn && !gameOver) {
            enemyTurn()
        }

        repaint()
    }

    override fun keyReleased(e: KeyEvent) {}

    override fun keyTyped(e: KeyEvent) {}
}