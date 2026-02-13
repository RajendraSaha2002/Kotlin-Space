import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*
import kotlin.math.*

fun main() {
    SwingUtilities.invokeLater { CalculatorUI().show() }
}

class CalculatorUI {
    private val frame = JFrame("Advanced Calculator")
    private val display = JTextField()
    private val historyModel = DefaultListModel<String>()
    private val historyList = JList(historyModel)
    private var memory = 0.0
    private var darkMode = false

    fun show() {
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.layout = BorderLayout(8, 8)
        frame.minimumSize = Dimension(750, 520)

        display.font = Font("Consolas", Font.PLAIN, 28)
        display.horizontalAlignment = JTextField.RIGHT
        display.isEditable = true
        display.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val topPanel = JPanel(BorderLayout())
        val themeToggle = JToggleButton("Dark Mode")
        themeToggle.addActionListener {
            darkMode = themeToggle.isSelected
            applyTheme()
        }
        topPanel.add(display, BorderLayout.CENTER)
        topPanel.add(themeToggle, BorderLayout.EAST)

        val buttonPanel = JPanel(GridLayout(6, 5, 6, 6))
        val buttons = listOf(
            "(", ")", "C", "⌫", "/",
            "sin", "cos", "tan", "log", "sqrt",
            "7", "8", "9", "*", "^",
            "4", "5", "6", "-", "%",
            "1", "2", "3", "+", "MR",
            "0", ".", "=", "M+", "M-"
        )

        buttons.forEach { label ->
            val btn = JButton(label)
            btn.font = Font("Segoe UI", Font.BOLD, 18)
            btn.addActionListener { onButton(label) }
            buttonPanel.add(btn)
        }

        val memoryPanel = JPanel(GridLayout(1, 2, 6, 6))
        val mc = JButton("MC")
        val ms = JButton("MS")
        mc.addActionListener { memory = 0.0; showInfo("Memory cleared") }
        ms.addActionListener { memory = display.text.toDoubleOrNull() ?: 0.0; showInfo("Stored to memory") }
        memoryPanel.add(mc)
        memoryPanel.add(ms)

        val leftPanel = JPanel(BorderLayout(6, 6))
        leftPanel.add(buttonPanel, BorderLayout.CENTER)
        leftPanel.add(memoryPanel, BorderLayout.SOUTH)

        val historyPanel = JPanel(BorderLayout(6, 6))
        historyPanel.border = BorderFactory.createTitledBorder("History")
        historyList.font = Font("Consolas", Font.PLAIN, 14)
        val clearHistory = JButton("Clear History")
        clearHistory.addActionListener { historyModel.clear() }
        historyPanel.add(JScrollPane(historyList), BorderLayout.CENTER)
        historyPanel.add(clearHistory, BorderLayout.SOUTH)

        frame.add(topPanel, BorderLayout.NORTH)
        frame.add(leftPanel, BorderLayout.CENTER)
        frame.add(historyPanel, BorderLayout.EAST)

        installKeyBindings()

        applyTheme()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    private fun onButton(label: String) {
        when (label) {
            "C" -> display.text = ""
            "⌫" -> if (display.text.isNotEmpty()) display.text = display.text.dropLast(1)
            "=" -> evaluate()
            "MR" -> display.text += memory.toString()
            "M+" -> {
                memory += display.text.toDoubleOrNull() ?: 0.0
                showInfo("Added to memory")
            }
            "M-" -> {
                memory -= display.text.toDoubleOrNull() ?: 0.0
                showInfo("Subtracted from memory")
            }
            "sin", "cos", "tan", "log", "sqrt" -> display.text += "$label("
            else -> display.text += label
        }
    }

    private fun evaluate() {
        val expr = display.text.trim()
        if (expr.isEmpty()) return
        try {
            val result = ExpressionEvaluator.eval(expr)
            display.text = result.toString()
            historyModel.addElement("$expr = $result")
        } catch (e: Exception) {
            display.text = "Error"
            showError("Invalid expression")
        }
    }

    private fun showInfo(msg: String) {
        JOptionPane.showMessageDialog(frame, msg, "Info", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun showError(msg: String) {
        JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE)
    }

    private fun applyTheme() {
        val bg = if (darkMode) Color(30, 30, 30) else Color(245, 245, 245)
        val fg = if (darkMode) Color(220, 220, 220) else Color(20, 20, 20)

        frame.contentPane.background = bg
        display.background = if (darkMode) Color(50, 50, 50) else Color.WHITE
        display.foreground = fg

        setComponentTheme(frame.contentPane, bg, fg)
        frame.repaint()
    }

    private fun setComponentTheme(component: Component, bg: Color, fg: Color) {
        when (component) {
            is JPanel -> {
                component.background = bg
                component.components.forEach { setComponentTheme(it, bg, fg) }
            }
            is JButton -> {
                component.background = if (darkMode) Color(70, 70, 70) else Color(230, 230, 230)
                component.foreground = fg
            }
            is JLabel -> {
                component.foreground = fg
            }
            is JList<*> -> {
                component.background = if (darkMode) Color(45, 45, 45) else Color.WHITE
                component.foreground = fg
            }
            is JScrollPane -> {
                component.background = bg
                component.viewport.background = bg
                component.viewport.view?.let { setComponentTheme(it, bg, fg) }
            }
        }
    }

    private fun installKeyBindings() {
        val inputMap = frame.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = frame.rootPane.actionMap

        fun bind(key: String, action: () -> Unit) {
            inputMap.put(KeyStroke.getKeyStroke(key), key)
            actionMap.put(key, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) = action()
            })
        }

        "0123456789".forEach { digit ->
            bind("typed $digit") { display.text += digit }
        }
        listOf("+", "-", "*", "/", "%", "^", "(", ")", ".").forEach { op ->
            bind("typed $op") { display.text += op }
        }
        bind("ENTER") { evaluate() }
        bind("BACK_SPACE") { if (display.text.isNotEmpty()) display.text = display.text.dropLast(1) }
        bind("ESCAPE") { display.text = "" }

        // Function shortcuts
        bind("typed s") { display.text += "sin(" }
        bind("typed c") { display.text += "cos(" }
        bind("typed t") { display.text += "tan(" }
        bind("typed l") { display.text += "log(" }
        bind("typed r") { display.text += "sqrt(" }
    }
}

object ExpressionEvaluator {
    private data class Token(val type: String, val value: String)

    private val functions = setOf("sin", "cos", "tan", "log", "sqrt")

    fun eval(expression: String): Double {
        val tokens = tokenize(expression)
        val rpn = toRpn(tokens)
        return evalRpn(rpn)
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var prevType: String? = null

        while (i < expr.length) {
            val ch = expr[i]

            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(Token("NUM", expr.substring(start, i)))
                    prevType = "NUM"
                }
                ch.isLetter() -> {
                    val start = i
                    while (i < expr.length && expr[i].isLetter()) i++
                    val name = expr.substring(start, i)
                    if (functions.contains(name)) tokens.add(Token("FUNC", name))
                    else throw IllegalArgumentException("Unknown function: $name")
                    prevType = "FUNC"
                }
                ch == '-' -> {
                    val unary = prevType == null || prevType in setOf("OP", "LPAREN")
                    if (unary) {
                        tokens.add(Token("OP", "u-"))
                    } else {
                        tokens.add(Token("OP", "-"))
                    }
                    i++
                    prevType = "OP"
                }
                ch in "+*/%^" -> {
                    tokens.add(Token("OP", ch.toString()))
                    i++
                    prevType = "OP"
                }
                ch == '(' -> {
                    tokens.add(Token("LPAREN", "("))
                    i++
                    prevType = "LPAREN"
                }
                ch == ')' -> {
                    tokens.add(Token("RPAREN", ")"))
                    i++
                    prevType = "RPAREN"
                }
                else -> throw IllegalArgumentException("Invalid character: $ch")
            }
        }
        return tokens
    }

    private fun precedence(op: String): Int = when (op) {
        "u-" -> 4
        "^" -> 3
        "*", "/", "%" -> 2
        "+", "-" -> 1
        else -> 0
    }

    private fun isRightAssociative(op: String) = op == "^" || op == "u-"

    private fun toRpn(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()

        for (t in tokens) {
            when (t.type) {
                "NUM" -> output.add(t)
                "FUNC" -> stack.addLast(t)
                "OP" -> {
                    while (stack.isNotEmpty()) {
                        val top = stack.last()
                        if (top.type == "OP" &&
                            ((precedence(top.value) > precedence(t.value)) ||
                                    (precedence(top.value) == precedence(t.value) && !isRightAssociative(t.value)))
                        ) {
                            output.add(stack.removeLast())
                        } else break
                    }
                    stack.addLast(t)
                }
                "LPAREN" -> stack.addLast(t)
                "RPAREN" -> {
                    while (stack.isNotEmpty() && stack.last().type != "LPAREN") {
                        output.add(stack.removeLast())
                    }
                    if (stack.isEmpty()) throw IllegalArgumentException("Mismatched parentheses")
                    stack.removeLast()
                    if (stack.isNotEmpty() && stack.last().type == "FUNC") {
                        output.add(stack.removeLast())
                    }
                }
            }
        }
        while (stack.isNotEmpty()) {
            val t = stack.removeLast()
            if (t.type in setOf("LPAREN", "RPAREN")) throw IllegalArgumentException("Mismatched parentheses")
            output.add(t)
        }
        return output
    }

    private fun evalRpn(rpn: List<Token>): Double {
        val stack = ArrayDeque<Double>()
        for (t in rpn) {
            when (t.type) {
                "NUM" -> stack.addLast(t.value.toDouble())
                "OP" -> {
                    if (t.value == "u-") {
                        val a = stack.removeLast()
                        stack.addLast(-a)
                    } else {
                        val b = stack.removeLast()
                        val a = stack.removeLast()
                        val res = when (t.value) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> a / b
                            "%" -> a % b
                            "^" -> a.pow(b)
                            else -> error("Unknown op")
                        }
                        stack.addLast(res)
                    }
                }
                "FUNC" -> {
                    val a = stack.removeLast()
                    val res = when (t.value) {
                        "sin" -> sin(a)
                        "cos" -> cos(a)
                        "tan" -> tan(a)
                        "log" -> ln(a)
                        "sqrt" -> sqrt(a)
                        else -> error("Unknown func")
                    }
                    stack.addLast(res)
                }
            }
        }
        if (stack.size != 1) throw IllegalArgumentException("Invalid expression")
        return stack.last()
    }
}