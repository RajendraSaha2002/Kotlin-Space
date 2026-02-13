import java.awt.*
import javax.swing.*

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Login System")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(350, 200)
        frame.layout = GridBagLayout()

        val grid = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            fill = GridBagConstraints.HORIZONTAL
        }

        val userLabel = JLabel("Username:")
        val passLabel = JLabel("Password:")
        val userField = JTextField(15)
        val passField = JPasswordField(15)
        val loginButton = JButton("Login")
        val messageLabel = JLabel("", SwingConstants.CENTER)

        grid.gridx = 0; grid.gridy = 0
        frame.add(userLabel, grid)

        grid.gridx = 1
        frame.add(userField, grid)

        grid.gridx = 0; grid.gridy = 1
        frame.add(passLabel, grid)

        grid.gridx = 1
        frame.add(passField, grid)

        grid.gridx = 0; grid.gridy = 2; grid.gridwidth = 2
        frame.add(loginButton, grid)

        grid.gridy = 3
        frame.add(messageLabel, grid)

        loginButton.addActionListener {
            val username = userField.text.trim()
            val password = String(passField.password)

            // Hardcoded credentials
            if (username == "admin" && password == "1234") {
                messageLabel.text = "Login Successful!"
                messageLabel.foreground = Color(0, 128, 0)
            } else {
                messageLabel.text = "Invalid Username or Password"
                messageLabel.foreground = Color.RED
            }
        }

        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}