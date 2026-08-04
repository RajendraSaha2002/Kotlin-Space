import java.util.ArrayDeque
import kotlin.math.pow
import kotlin.math.round

// ============================================================================
// EVALUATION RESULT MODEL
// ============================================================================

sealed class EvalResult {
    data class NumberVal(val value: Double) : EvalResult()
    data class StringVal(val value: String) : EvalResult()
    data class ErrorVal(val msg: String) : EvalResult()
    object Empty : EvalResult()

    fun asDouble(): Double? = when (this) {
        is NumberVal -> value
        is StringVal -> value.toDoubleOrNull()
        Empty -> 0.0
        is ErrorVal -> null
    }

    fun displayText(): String = when (this) {
        is NumberVal -> {
            if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                String.format("%.2f", value)
            }
        }
        is StringVal -> value
        is ErrorVal -> msg
        Empty -> ""
    }
}

// ============================================================================
// CELL ADDRESS HELPER
// ============================================================================

data class CellAddress(val col: Int, val row: Int) {
    val name: String get() = "${toColName(col)}$row"

    companion object {
        fun parse(str: String): CellAddress? {
            val match = Regex("""^([A-Za-z]+)([1-9][0-9]*)$""").matchEntire(str.trim().uppercase()) ?: return null
            val colStr = match.groupValues[1]
            val rowNum = match.groupValues[2].toIntOrNull() ?: return null
            var col = 0
            for (ch in colStr) {
                col = col * 26 + (ch - 'A' + 1)
            }
            return CellAddress(col - 1, rowNum)
        }

        fun toColName(colIndex: Int): String {
            var c = colIndex
            val sb = StringBuilder()
            while (c >= 0) {
                sb.append(('A'.code + (c % 26)).toChar())
                c = c / 26 - 1
            }
            return sb.reverse().toString()
        }
    }
}

// ============================================================================
// CELL DATA MODEL
// ============================================================================

class Cell(
    val address: String,
    var rawInput: String = "",
    var evalValue: EvalResult = EvalResult.Empty,
    val dependencies: MutableSet<String> = mutableSetOf(),
    val dependents: MutableSet<String> = mutableSetOf()
)

// ============================================================================
// FORMULA PARSER & EVALUATOR
// ============================================================================

class FormulaParser(
    formulaStr: String,
    private val cellResolver: (String) -> EvalResult
) {
    private var pos = 0
    private val tokens = tokenize(formulaStr)

    sealed class Token {
        data class Num(val value: Double) : Token()
        data class Str(val value: String) : Token()
        data class Ident(val name: String) : Token()
        data class Op(val symbol: String) : Token()
        object LParen : Token()
        object RParen : Token()
        object Comma : Token()
    }

    private fun tokenize(input: String): List<Token> {
        val list = mutableListOf<Token>()
        var i = 0
        val src = input.trim().let { if (it.startsWith("=")) it.substring(1) else it }

        while (i < src.length) {
            val ch = src[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || (ch == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    val num = src.substring(start, i).toDoubleOrNull() ?: 0.0
                    list.add(Token.Num(num))
                }
                ch == '"' || ch == '\'' -> {
                    val quote = ch
                    i++
                    val start = i
                    while (i < src.length && src[i] != quote) i++
                    val strVal = src.substring(start, i)
                    if (i < src.length) i++
                    list.add(Token.Str(strVal))
                }
                ch.isLetter() -> {
                    val start = i
                    while (i < src.length && src[i].isLetterOrDigit()) i++
                    list.add(Token.Ident(src.substring(start, i).uppercase()))
                }
                ch in "+-*/%^=()<>" -> {
                    if (ch == '<' && i + 1 < src.length && (src[i + 1] == '>' || src[i + 1] == '=')) {
                        list.add(Token.Op(src.substring(i, i + 2)))
                        i += 2
                    } else if (ch == '>' && i + 1 < src.length && src[i + 1] == '=') {
                        list.add(Token.Op(">="))
                        i += 2
                    } else if (ch == '!' && i + 1 < src.length && src[i + 1] == '=') { // Fixed parenthesis here
                        list.add(Token.Op("!="))
                        i += 2
                    } else if (ch == '(') {
                        list.add(Token.LParen)
                        i++
                    } else if (ch == ')') {
                        list.add(Token.RParen)
                        i++
                    } else {
                        list.add(Token.Op(ch.toString()))
                        i++
                    }
                }
                ch == ',' -> {
                    list.add(Token.Comma)
                    i++
                }
                else -> i++
            }
        }
        return list
    }

    fun parse(): EvalResult {
        if (tokens.isEmpty()) return EvalResult.Empty
        return try {
            val res = parseExpression()
            if (pos < tokens.size) EvalResult.ErrorVal("#SYNTAX!") else res
        } catch (e: Exception) {
            EvalResult.ErrorVal("#ERROR!")
        }
    }

    private fun peek(): Token? = if (pos < tokens.size) tokens[pos] else null
    private fun consume(): Token? = if (pos < tokens.size) tokens[pos++] else null

    private fun parseExpression(): EvalResult = parseEquality()

    private fun parseEquality(): EvalResult {
        var left = parseRelational()
        if (left is EvalResult.ErrorVal) return left

        while (true) {
            val tok = peek() as? Token.Op ?: break
            if (tok.symbol !in listOf("=", "<>", "!=")) break
            consume()
            val right = parseRelational()
            if (right is EvalResult.ErrorVal) return right

            val lNum = left.asDouble()
            val rNum = right.asDouble()

            val resBool = when (tok.symbol) {
                "=" -> if (lNum != null && rNum != null) lNum == rNum else left.displayText() == right.displayText()
                "<>", "!=" -> if (lNum != null && rNum != null) lNum != rNum else left.displayText() != right.displayText()
                else -> false
            }
            left = EvalResult.NumberVal(if (resBool) 1.0 else 0.0)
        }
        return left
    }

    private fun parseRelational(): EvalResult {
        var left = parseAddSub()
        if (left is EvalResult.ErrorVal) return left

        while (true) {
            val tok = peek() as? Token.Op ?: break
            if (tok.symbol !in listOf("<", "<=", ">", ">=")) break
            consume()
            val right = parseAddSub()
            if (right is EvalResult.ErrorVal) return right

            val lNum = left.asDouble() ?: return EvalResult.ErrorVal("#VALUE!")
            val rNum = right.asDouble() ?: return EvalResult.ErrorVal("#VALUE!")

            val resBool = when (tok.symbol) {
                "<" -> lNum < rNum
                "<=" -> lNum <= rNum
                ">" -> lNum > rNum
                ">=" -> lNum >= rNum
                else -> false
            }
            left = EvalResult.NumberVal(if (resBool) 1.0 else 0.0)
        }
        return left
    }

    private fun parseAddSub(): EvalResult {
        var left = parseMulDiv()
        if (left is EvalResult.ErrorVal) return left

        while (true) {
            val tok = peek() as? Token.Op ?: break
            if (tok.symbol !in listOf("+", "-")) break
            consume()
            val right = parseMulDiv()
            if (right is EvalResult.ErrorVal) return right

            val lNum = left.asDouble()
            val rNum = right.asDouble()

            if (lNum == null || rNum == null) {
                if (tok.symbol == "+") {
                    left = EvalResult.StringVal(left.displayText() + right.displayText())
                } else {
                    return EvalResult.ErrorVal("#VALUE!")
                }
            } else {
                left = when (tok.symbol) {
                    "+" -> EvalResult.NumberVal(lNum + rNum)
                    "-" -> EvalResult.NumberVal(lNum - rNum)
                    else -> left
                }
            }
        }
        return left
    }

    private fun parseMulDiv(): EvalResult {
        var left = parsePower()
        if (left is EvalResult.ErrorVal) return left

        while (true) {
            val tok = peek() as? Token.Op ?: break
            if (tok.symbol !in listOf("*", "/", "%")) break
            consume()
            val right = parsePower()
            if (right is EvalResult.ErrorVal) return right

            val lNum = left.asDouble() ?: return EvalResult.ErrorVal("#VALUE!")
            val rNum = right.asDouble() ?: return EvalResult.ErrorVal("#VALUE!")

            left = when (tok.symbol) {
                "*" -> EvalResult.NumberVal(lNum * rNum)
                "/" -> if (rNum == 0.0) EvalResult.ErrorVal("#DIV/0!") else EvalResult.NumberVal(lNum / rNum)
                "%" -> if (rNum == 0.0) EvalResult.ErrorVal("#DIV/0!") else EvalResult.NumberVal(lNum % rNum)
                else -> left
            }
        }
        return left
    }

    private fun parsePower(): EvalResult {
        var left = parseUnary()
        if (left is EvalResult.ErrorVal) return left

        while (true) {
            val tok = peek() as? Token.Op ?: break
            if (tok.symbol != "^") break
            consume()
            val right = parseUnary()
            if (right is EvalResult.ErrorVal) return right

            val lNum = left.asDouble() ?: return EvalResult.ErrorVal("#VALUE!")
            val rNum = right.asDouble() ?: return EvalResult.ErrorVal("#VALUE!")

            left = EvalResult.NumberVal(lNum.pow(rNum))
        }
        return left
    }

    private fun parseUnary(): EvalResult {
        val tok = peek() as? Token.Op
        if (tok != null && tok.symbol in listOf("+", "-")) {
            consume()
            val operand = parseUnary()
            if (operand is EvalResult.ErrorVal) return operand
            val num = operand.asDouble() ?: return EvalResult.ErrorVal("#VALUE!")
            return EvalResult.NumberVal(if (tok.symbol == "-") -num else num)
        }
        return parsePrimary()
    }

    private fun parsePrimary(): EvalResult {
        val tok = consume() ?: return EvalResult.ErrorVal("#SYNTAX!")
        return when (tok) {
            is Token.Num -> EvalResult.NumberVal(tok.value)
            is Token.Str -> EvalResult.StringVal(tok.value)
            is Token.LParen -> {
                val res = parseExpression()
                if (consume() !is Token.RParen) {
                    EvalResult.ErrorVal("#SYNTAX!")
                } else {
                    res
                }
            }
            is Token.Ident -> {
                if (peek() is Token.LParen) {
                    consume() // Consume LParen
                    val args = mutableListOf<EvalResult>()
                    if (peek() !is Token.RParen) {
                        while (true) {
                            args.add(parseExpression())
                            if (peek() is Token.Comma) consume() else break
                        }
                    }
                    if (consume() !is Token.RParen) {
                        EvalResult.ErrorVal("#SYNTAX!")
                    } else {
                        evaluateFunction(tok.name, args)
                    }
                } else {
                    cellResolver(tok.name)
                }
            }
            else -> EvalResult.ErrorVal("#SYNTAX!")
        }
    }

    private fun evaluateFunction(name: String, args: List<EvalResult>): EvalResult {
        for (arg in args) {
            if (arg is EvalResult.ErrorVal) return arg
        }
        val nums = args.mapNotNull { it.asDouble() }

        return when (name) {
            "SUM" -> EvalResult.NumberVal(nums.sum())
            "AVG", "AVERAGE" -> if (nums.isEmpty()) EvalResult.ErrorVal("#DIV/0!") else EvalResult.NumberVal(nums.average())
            "MIN" -> if (nums.isEmpty()) EvalResult.ErrorVal("#VALUE!") else EvalResult.NumberVal(nums.minOrNull()!!)
            "MAX" -> if (nums.isEmpty()) EvalResult.ErrorVal("#VALUE!") else EvalResult.NumberVal(nums.maxOrNull()!!)
            "COUNT" -> EvalResult.NumberVal(nums.size.toDouble())
            "IF" -> {
                if (args.size < 2) return EvalResult.ErrorVal("#ARGS!")
                val cond = args[0].asDouble() ?: 0.0
                val thenVal = args[1]
                val elseVal = if (args.size > 2) args[2] else EvalResult.Empty
                if (cond != 0.0) thenVal else elseVal
            }
            "ROUND" -> {
                if (args.isEmpty()) return EvalResult.ErrorVal("#ARGS!")
                val valToRound = args[0].asDouble() ?: return EvalResult.ErrorVal("#VALUE!")
                val decimals = if (args.size > 1) (args[1].asDouble()?.toInt() ?: 0) else 0
                val factor = 10.0.pow(decimals.toDouble())
                EvalResult.NumberVal(round(valToRound * factor) / factor)
            }
            "CONCAT" -> {
                val sb = StringBuilder()
                for (a in args) sb.append(a.displayText())
                EvalResult.StringVal(sb.toString())
            }
            else -> EvalResult.ErrorVal("#FUNC?")
        }
    }
}

// ============================================================================
// SPREADSHEET ENGINE
// ============================================================================

class SpreadsheetEngine(var numCols: Int = 5, var numRows: Int = 10) {
    val cells = mutableMapOf<String, Cell>()

    init {
        for (r in 1..numRows) {
            for (c in 0 until numCols) {
                val addr = "${CellAddress.toColName(c)}$r"
                cells[addr] = Cell(address = addr)
            }
        }
    }

    fun getCell(addr: String): Cell {
        val norm = addr.uppercase()
        return cells.getOrPut(norm) { Cell(address = norm) }
    }

    private fun expandIfNeeded(addr: String) {
        val parsed = CellAddress.parse(addr) ?: return
        if (parsed.col >= numCols) numCols = parsed.col + 1
        if (parsed.row > numRows) numRows = parsed.row
    }

    private fun expandRanges(raw: String): String {
        val rangeRegex = Regex("""\b([A-Za-z]+[1-9][0-9]*):([A-Za-z]+[1-9][0-9]*)\b""")
        return rangeRegex.replace(raw) { match ->
            val start = CellAddress.parse(match.groupValues[1])
            val end = CellAddress.parse(match.groupValues[2])
            if (start != null && end != null) {
                val minCol = minOf(start.col, end.col)
                val maxCol = maxOf(start.col, end.col)
                val minRow = minOf(start.row, end.row)
                val maxRow = maxOf(start.row, end.row)

                val expanded = mutableListOf<String>()
                for (c in minCol..maxCol) {
                    for (r in minRow..maxRow) {
                        expanded.add("${CellAddress.toColName(c)}$r")
                    }
                }
                expanded.joinToString(",")
            } else {
                match.value
            }
        }
    }

    fun extractDependencies(rawInput: String): Set<String> {
        if (!rawInput.startsWith("=")) return emptySet()
        val expanded = expandRanges(rawInput)
        val refRegex = Regex("""\b[A-Za-z]+[1-9][0-9]*\b""")
        val keywords = setOf("SUM", "AVG", "AVERAGE", "MIN", "MAX", "COUNT", "IF", "ROUND", "CONCAT", "AND", "OR", "NOT")
        val deps = mutableSetOf<String>()
        for (match in refRegex.findAll(expanded)) {
            val valStr = match.value.uppercase()
            if (valStr !in keywords && CellAddress.parse(valStr) != null) {
                deps.add(valStr)
            }
        }
        return deps
    }

    private fun canReach(from: String, target: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
        if (from == target) return true
        if (!visited.add(from)) return false
        val cell = cells[from] ?: return false
        for (dep in cell.dependents) {
            if (canReach(dep, target, visited)) return true
        }
        return false
    }

    fun setCell(addr: String, input: String) {
        val normAddr = addr.uppercase()
        if (CellAddress.parse(normAddr) == null) {
            println("Error: Invalid cell coordinate '$addr'")
            return
        }

        expandIfNeeded(normAddr)
        val targetCell = getCell(normAddr)
        val trimmedInput = input.trim()
        val newDeps = extractDependencies(trimmedInput)

        // Check for Circular Dependency
        var circularDetected = false
        for (dep in newDeps) {
            if (canReach(normAddr, dep)) {
                circularDetected = true
                break
            }
        }

        // Unbind old dependency edges
        for (oldDep in targetCell.dependencies) {
            cells[oldDep]?.dependents?.remove(normAddr)
        }
        targetCell.dependencies.clear()

        if (circularDetected) {
            targetCell.rawInput = trimmedInput
            targetCell.evalValue = EvalResult.ErrorVal("#CIRCULAR!")
            reevaluateDependents(normAddr)
            return
        }

        // Bind new dependencies
        targetCell.rawInput = trimmedInput
        targetCell.dependencies.addAll(newDeps)
        for (dep in newDeps) {
            getCell(dep).dependents.add(normAddr)
        }

        reevaluateDependents(normAddr)
    }

    fun clearCell(addr: String) {
        setCell(addr, "")
    }

    private fun reevaluateDependents(startAddr: String) {
        val order = getTopologicalOrder(startAddr)
        for (cellAddr in order) {
            val cell = getCell(cellAddr)
            cell.evalValue = evaluateCell(cell)
        }
    }

    private fun evaluateCell(cell: Cell): EvalResult {
        val input = cell.rawInput
        if (input.isEmpty()) return EvalResult.Empty

        if (!input.startsWith("=")) {
            input.toDoubleOrNull()?.let { return EvalResult.NumberVal(it) }
            return EvalResult.StringVal(input)
        }

        val expandedInput = expandRanges(input)
        val parser = FormulaParser(expandedInput) { ref ->
            val refCell = cells[ref.uppercase()]
            refCell?.evalValue ?: EvalResult.Empty
        }
        return parser.parse()
    }

    private fun getTopologicalOrder(startAddr: String): List<String> {
        val affected = mutableSetOf<String>()
        fun collect(curr: String) {
            if (affected.add(curr)) {
                cells[curr]?.dependents?.forEach { collect(it) }
            }
        }
        collect(startAddr)

        val inDegree = mutableMapOf<String, Int>()
        for (node in affected) inDegree[node] = 0

        for (node in affected) {
            val cell = cells[node] ?: continue
            for (dep in cell.dependents) {
                if (dep in affected) {
                    inDegree[dep] = (inDegree[dep] ?: 0) + 1
                }
            }
        }

        val queue = ArrayDeque<String>()
        for ((node, deg) in inDegree) {
            if (deg == 0) queue.add(node)
        }

        val order = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            order.add(curr)
            val cell = cells[curr] ?: continue
            for (dep in cell.dependents) {
                if (dep in affected) {
                    val newDeg = (inDegree[dep] ?: 1) - 1
                    inDegree[dep] = newDeg
                    if (newDeg == 0) queue.add(dep)
                }
            }
        }
        return order
    }

    fun renderGrid() {
        val colWidth = 13
        val borderChar = "-"
        val totalWidth = 6 + numCols * (colWidth + 1) + 1

        println(borderChar.repeat(totalWidth))
        print(String.format("| %-4s |", ""))
        for (c in 0 until numCols) {
            val colName = CellAddress.toColName(c)
            print(String.format(" %-${colWidth - 1}s |", colName))
        }
        println()
        println(borderChar.repeat(totalWidth))

        for (r in 1..numRows) {
            print(String.format("| %-4d |", r))
            for (c in 0 until numCols) {
                val addr = "${CellAddress.toColName(c)}$r"
                val cell = cells[addr]
                val text = cell?.evalValue?.displayText() ?: ""
                val truncated = if (text.length > colWidth) text.substring(0, colWidth - 2) + ".." else text
                print(String.format(" %-${colWidth - 1}s |", truncated))
            }
            println()
        }
        println(borderChar.repeat(totalWidth))
    }

    fun loadDemoBudget() {
        setCell("A1", "Category")
        setCell("B1", "Amount ($)")
        setCell("C1", "Share (%)")

        setCell("A2", "Salary")
        setCell("B2", "5000")

        setCell("A3", "Freelance")
        setCell("B3", "1500")

        setCell("A4", "Total Income")
        setCell("B4", "=SUM(B2:B3)")

        setCell("A5", "Rent")
        setCell("B5", "1800")

        setCell("A6", "Groceries")
        setCell("B6", "600")

        setCell("A7", "Utilities")
        setCell("B7", "350")

        setCell("A8", "Total Expense")
        setCell("B8", "=SUM(B5:B7)")

        setCell("A9", "Net Profit")
        setCell("B9", "=B4-B8")

        setCell("C2", "=ROUND(B2/B4*100, 1)")
        setCell("C3", "=ROUND(B3/B4*100, 1)")
        setCell("C5", "=ROUND(B5/B4*100, 1)")
        setCell("C6", "=ROUND(B6/B4*100, 1)")
        setCell("C7", "=ROUND(B7/B4*100, 1)")
    }
}

// ============================================================================
// CLI INTERACTIVE APPLICATION
// ============================================================================

fun printHelp() {
    println(
        """
        ==========================================================================
        SPREADSHEET SIMULATOR COMMANDS
        ==========================================================================
        SET <cell> <value|formula>  : Set value or formula (e.g. SET A1 500, SET A3 =SUM(A1:A2))
        CLEAR <cell>               : Clear a cell's contents (e.g. CLEAR A1)
        SHOW / GRID                : Redraw the spreadsheet grid
        INSPECT <cell>             : Inspect raw formula, dependencies, and dependents
        DEMO                       : Load pre-populated budget ledger demo
        HELP                       : Display this user manual
        EXIT / QUIT                : Exit spreadsheet simulator
        ==========================================================================
        Supported Functions: SUM, AVG, MIN, MAX, COUNT, IF, ROUND, CONCAT
        Supported Operators: +, -, *, /, %, ^, =, <>, !=, <, <=, >, >=
        Ranges: A1:B5 (Expanded inside function calls)
        ==========================================================================
        """.trimIndent()
    )
}

fun main() {
    val engine = SpreadsheetEngine(numCols = 5, numRows = 10)

    println("\n==========================================================================")
    println("          INTERACTIVE BUDGET SPREADSHEET SIMULATOR (KOTLIN CLI)           ")
    println("==========================================================================")
    println("Type 'DEMO' to load a sample budget, or 'HELP' for options.")
    println()

    engine.renderGrid()

    while (true) {
        print("\nSpreadsheet > ")
        val line = readlnOrNull()?.trim() ?: break
        if (line.isEmpty()) continue

        val parts = line.split(Regex("""\s+"""), limit = 3)
        val cmd = parts[0].uppercase()

        when (cmd) {
            "EXIT", "QUIT" -> {
                println("Exiting Spreadsheet Simulator. Goodbye!")
                break
            }
            "HELP" -> printHelp()
            "SHOW", "GRID" -> engine.renderGrid()
            "DEMO" -> {
                engine.loadDemoBudget()
                println("\n>>> Demo Budget Ledger Loaded Successfully! <<<\n")
                engine.renderGrid()
            }
            "CLEAR", "DEL" -> {
                if (parts.size < 2) {
                    println("Usage: CLEAR <CELL> (e.g., CLEAR A1)")
                } else {
                    engine.clearCell(parts[1])
                    engine.renderGrid()
                }
            }
            "INSPECT" -> {
                if (parts.size < 2) {
                    println("Usage: INSPECT <CELL> (e.g., INSPECT B4)")
                } else {
                    val addr = parts[1].uppercase()
                    val cell = engine.cells[addr]
                    if (cell == null) {
                        println("Cell $addr does not exist.")
                    } else {
                        println("----------------------------------------")
                        println("Cell Coordinate : ${cell.address}")
                        println("Raw Input       : ${cell.rawInput}")
                        println("Evaluated Value : ${cell.evalValue.displayText()} (${cell.evalValue::class.simpleName})")
                        println("Dependencies    : ${if (cell.dependencies.isEmpty()) "None" else cell.dependencies.joinToString(", ")}")
                        println("Dependents      : ${if (cell.dependents.isEmpty()) "None" else cell.dependents.joinToString(", ")}")
                        println("----------------------------------------")
                    }
                }
            }
            "SET" -> {
                if (parts.size < 3) {
                    println("Usage: SET <CELL> <VALUE|FORMULA> (e.g., SET A1 100 or SET A3 =A1+A2)")
                } else {
                    val addr = parts[1].uppercase()
                    val value = parts[2]
                    engine.setCell(addr, value)
                    engine.renderGrid()
                }
            }
            else -> {
                if (CellAddress.parse(cmd) != null && parts.size >= 2) {
                    val value = line.substringAfter(parts[0]).trim()
                    engine.setCell(cmd, value)
                    engine.renderGrid()
                } else {
                    println("Unknown command: '$cmd'. Type 'HELP' for available commands.")
                }
            }
        }
    }
}