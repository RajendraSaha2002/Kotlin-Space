import javax.swing.JFrame

fun main() {
    val frame = JFrame("Strategy War Game")

    frame.add(GamePanel())
    frame.setSize(800, 600)
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true
}