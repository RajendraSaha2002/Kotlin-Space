class ScoreService {

    fun saveScore(userId: Int, score: Int) {

        val conn = DBConnection.connect()

        val query = "INSERT INTO scores(user_id, score) VALUES (?, ?)"

        val stmt = conn.prepareStatement(query)

        stmt.setInt(1, userId)
        stmt.setInt(2, score)

        stmt.executeUpdate()

        stmt.close()
        conn.close()
    }
}