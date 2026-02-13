import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Scanner

// Data class to represent a single Task
data class Task(
    val id: Int,
    var description: String,
    var priority: Priority,
    var dueDate: LocalDate?,
    var isCompleted: Boolean = false
) {
    // Override toString for easy file saving format (simple CSV-like structure for this example)
    override fun toString(): String {
        return "$id|$description|${priority.name}|${dueDate?.toString() ?: "null"}|$isCompleted"
    }

    companion object {
        // Parse a line of text back into a Task object
        fun fromString(line: String): Task? {
            return try {
                val parts = line.split("|")
                val id = parts[0].toInt()
                val desc = parts[1]
                val prio = Priority.valueOf(parts[2])
                val date = if (parts[3] != "null") LocalDate.parse(parts[3]) else null
                val completed = parts[4].toBoolean()
                Task(id, desc, prio, date, completed)
            } catch (e: Exception) {
                null // Skip corrupted lines
            }
        }
    }
}

// Enum for Priority levels
enum class Priority {
    HIGH, MEDIUM, LOW
}

class TaskManager {
    private val scanner = Scanner(System.`in`)
    private val tasks = mutableListOf<Task>()
    private val dataFile = File("tasks_data.txt")
    private var nextId = 1

    init {
        loadTasks()
    }

    fun start() {
        println("=== CLI Task Manager ===")
        var running = true

        while (running) {
            printMenu()
            print("> ")
            val choice = scanner.nextLine().trim()

            when (choice) {
                "1" -> addTask()
                "2" -> listTasks()
                "3" -> completeTask()
                "4" -> deleteTask()
                "5" -> {
                    saveTasks()
                    println("Goodbye!")
                    running = false
                }
                else -> println("Invalid option, please try again.")
            }
            println() // Empty line for readability
        }
    }

    private fun printMenu() {
        println("1. Add New Task")
        println("2. List All Tasks")
        println("3. Mark Task as Complete")
        println("4. Delete Task")
        println("5. Save & Exit")
    }

    private fun addTask() {
        println("--- Add New Task ---")

        // 1. Description
        print("Enter description: ")
        val desc = scanner.nextLine()

        // 2. Priority
        var priority: Priority? = null
        while (priority == null) {
            print("Enter priority (HIGH, MEDIUM, LOW): ")
            try {
                priority = Priority.valueOf(scanner.nextLine().uppercase())
            } catch (e: IllegalArgumentException) {
                println("Invalid priority. Please type HIGH, MEDIUM, or LOW.")
            }
        }

        // 3. Due Date
        var dueDate: LocalDate? = null
        var validDate = false
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        while (!validDate) {
            print("Enter due date (YYYY-MM-DD) or press Enter to skip: ")
            val dateInput = scanner.nextLine()
            if (dateInput.isBlank()) {
                validDate = true
            } else {
                try {
                    dueDate = LocalDate.parse(dateInput, dateFormatter)
                    validDate = true
                } catch (e: DateTimeParseException) {
                    println("Invalid format. Please use YYYY-MM-DD.")
                }
            }
        }

        tasks.add(Task(nextId++, desc, priority, dueDate))
        println("Task added successfully!")
    }

    private fun listTasks() {
        println("--- Task List ---")
        if (tasks.isEmpty()) {
            println("No tasks found.")
        } else {
            // Sort by completion status (incomplete first), then priority, then due date
            val sortedTasks = tasks.sortedWith(
                compareBy<Task> { it.isCompleted }
                    .thenBy { it.priority } // enum order: HIGH=0, MEDIUM=1, LOW=2 naturally sorts if defined in that order? No, Enum sorts by ordinal.
                // Let's fix priority sorting logic explicitly if needed, but standard enum ordinal is usually declaration order.
            )

            // Header
            println("%-4s | %-30s | %-8s | %-12s | %-10s".format("ID", "Description", "Priority", "Due Date", "Status"))
            println("-".repeat(75))

            for (task in tasks) {
                val status = if (task.isCompleted) "[X] Done" else "[ ] Todo"
                val dateStr = task.dueDate?.toString() ?: "No Date"
                println("%-4d | %-30s | %-8s | %-12s | %-10s".format(
                    task.id,
                    truncate(task.description, 30),
                    task.priority,
                    dateStr,
                    status
                ))
            }
        }
    }

    private fun completeTask() {
        listTasks()
        if (tasks.isEmpty()) return

        print("Enter ID of task to complete: ")
        val idInput = scanner.nextLine().toIntOrNull()

        if (idInput != null) {
            val task = tasks.find { it.id == idInput }
            if (task != null) {
                task.isCompleted = true
                println("Task marked as completed!")
            } else {
                println("Task not found.")
            }
        } else {
            println("Invalid ID.")
        }
    }

    private fun deleteTask() {
        listTasks()
        if (tasks.isEmpty()) return

        print("Enter ID of task to delete: ")
        val idInput = scanner.nextLine().toIntOrNull()

        if (idInput != null) {
            val removed = tasks.removeIf { it.id == idInput }
            if (removed) {
                println("Task deleted.")
            } else {
                println("Task not found.")
            }
        } else {
            println("Invalid ID.")
        }
    }

    private fun saveTasks() {
        try {
            dataFile.printWriter().use { out ->
                tasks.forEach { task ->
                    out.println(task.toString())
                }
            }
            println("Tasks saved to ${dataFile.absolutePath}")
        } catch (e: Exception) {
            println("Error saving tasks: ${e.message}")
        }
    }

    private fun loadTasks() {
        if (!dataFile.exists()) return

        try {
            dataFile.forEachLine { line ->
                val task = Task.fromString(line)
                if (task != null) {
                    tasks.add(task)
                    // Ensure nextId is always higher than the highest loaded ID
                    if (task.id >= nextId) {
                        nextId = task.id + 1
                    }
                }
            }
            println("Loaded ${tasks.size} tasks from file.")
        } catch (e: Exception) {
            println("Error loading tasks: ${e.message}")
        }
    }

    private fun truncate(str: String, width: Int): String {
        return if (str.length > width) str.take(width - 3) + "..." else str
    }
}

fun main() {
    val app = TaskManager()
    app.start()
}