import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Scanner

class BankAccount(
    private val accountNumber: String,
    private var ownerName: String,
    private var pin: String,
    private var balance: Double = 0.0
) {
    private val history = mutableListOf<String>()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun authenticate(inputPin: String): Boolean = inputPin == pin

    fun deposit(amount: Double) {
        require(amount > 0) { "Amount must be positive" }
        balance += amount
        addHistory("Deposit: +$amount | Balance: $balance")
    }

    fun withdraw(amount: Double): Boolean {
        require(amount > 0) { "Amount must be positive" }
        return if (amount <= balance) {
            balance -= amount
            addHistory("Withdraw: -$amount | Balance: $balance")
            true
        } else {
            addHistory("Failed Withdraw: -$amount | Insufficient Balance")
            false
        }
    }

    fun checkBalance(): Double = balance

    fun changePin(oldPin: String, newPin: String): Boolean {
        return if (oldPin == pin) {
            pin = newPin
            addHistory("PIN changed successfully")
            true
        } else {
            addHistory("Failed PIN change (wrong old PIN)")
            false
        }
    }

    fun getHistory(): List<String> = history.toList()

    private fun addHistory(entry: String) {
        val time = LocalDateTime.now().format(formatter)
        history.add("[$time] $entry")
    }

    fun saveToFile(file: File) {
        val data = buildString {
            appendLine(accountNumber)
            appendLine(ownerName)
            appendLine(pin)
            appendLine(balance)
            appendLine(history.size)
            history.forEach { appendLine(it) }
        }
        file.writeText(data)
    }

    companion object {
        fun loadFromFile(file: File): BankAccount? {
            if (!file.exists()) return null
            val lines = file.readLines()
            if (lines.size < 5) return null
            val accNo = lines[0]
            val name = lines[1]
            val pin = lines[2]
            val balance = lines[3].toDoubleOrNull() ?: 0.0
            val historyCount = lines[4].toIntOrNull() ?: 0
            val account = BankAccount(accNo, name, pin, balance)
            for (i in 0 until historyCount) {
                val index = 5 + i
                if (index < lines.size) {
                    account.history.add(lines[index])
                }
            }
            return account
        }
    }
}

fun main() {
    val scanner = Scanner(System.`in`)
    val dataFile = File("bank_account.txt")

    val account = BankAccount.loadFromFile(dataFile)
        ?: BankAccount("ACC1001", "Rajendra", "1234", 500.0)

    println("=== Bank Account Simulation ===")
    print("Enter PIN: ")
    val inputPin = scanner.nextLine().trim()

    if (!account.authenticate(inputPin)) {
        println("Invalid PIN. Access denied.")
        return
    }

    while (true) {
        println("\n1. Check Balance")
        println("2. Deposit")
        println("3. Withdraw")
        println("4. Transaction History")
        println("5. Change PIN")
        println("6. Save & Exit")
        print("Choose: ")

        when (scanner.nextLine().trim()) {
            "1" -> println("Balance: ${account.checkBalance()}")
            "2" -> {
                print("Enter amount: ")
                val amount = scanner.nextLine().toDoubleOrNull() ?: 0.0
                try {
                    account.deposit(amount)
                    println("Deposited successfully.")
                } catch (e: IllegalArgumentException) {
                    println(e.message)
                }
            }
            "3" -> {
                print("Enter amount: ")
                val amount = scanner.nextLine().toDoubleOrNull() ?: 0.0
                try {
                    val success = account.withdraw(amount)
                    if (success) println("Withdraw successful.")
                    else println("Insufficient balance.")
                } catch (e: IllegalArgumentException) {
                    println(e.message)
                }
            }
            "4" -> {
                val history = account.getHistory()
                if (history.isEmpty()) println("No transactions yet.")
                else history.forEach { println(it) }
            }
            "5" -> {
                print("Enter old PIN: ")
                val oldPin = scanner.nextLine().trim()
                print("Enter new PIN: ")
                val newPin = scanner.nextLine().trim()
                if (account.changePin(oldPin, newPin)) {
                    println("PIN changed.")
                } else {
                    println("Old PIN incorrect.")
                }
            }
            "6" -> {
                account.saveToFile(dataFile)
                println("Saved. Goodbye!")
                break
            }
            else -> println("Invalid choice.")
        }
    }
}