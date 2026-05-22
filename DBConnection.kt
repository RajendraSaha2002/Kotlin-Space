import java.sql.Connection
import java.sql.DriverManager

object DBConnection {

    private const val URL = "jdbc:postgresql://localhost:5432/war_game"
    private const val USER = "postgres"
    private const val PASSWORD = "sharma30@"

    fun connect(): Connection {
        return DriverManager.getConnection(URL, USER, PASSWORD)
    }
}