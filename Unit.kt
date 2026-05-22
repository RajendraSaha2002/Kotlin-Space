import java.awt.Color
import java.awt.Graphics

class Unit(
    var x: Int,
    var y: Int,
    var health: Int,
    var attack: Int,
    var isPlayer: Boolean
) {

    fun draw(g: Graphics) {
        if (isPlayer)
            g.color = Color.GREEN
        else
            g.color = Color.RED

        g.fillRect(x, y, 50, 50)

        g.color = Color.WHITE
        g.drawString("HP: $health", x, y - 5)
    }

    fun isAlive(): Boolean {
        return health > 0
    }
}