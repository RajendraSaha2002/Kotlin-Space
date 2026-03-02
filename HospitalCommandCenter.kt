import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Hospital Command Center System
 * Features: Login, Emergency Monitoring, Patient Queue, Doctor Assignment, Billing, and Logs.
 * Designed for Kotlin / IntelliJ IDEA.
 */

// --- Data Models ---

data class User(val username: String, val role: String)

enum class EmergencyStatus(val label: String, val colorCode: String) {
    CRITICAL("CRITICAL", "\u001B[31m"), // Red
    STABLE("STABLE", "\u001B[32m"),    // Green
    WARNING("WARNING", "\u001B[33m"),   // Yellow
    PENDING("PENDING", "\u001B[34m")    // Blue
}

data class Patient(
    val id: String,
    val name: String,
    var status: EmergencyStatus,
    val arrivalTime: LocalDateTime,
    var assignedDoctor: String? = null,
    var billAmount: Double = 0.0
)

data class ActivityLog(val timestamp: LocalDateTime, val message: String)

// --- System State ---

object CommandCenterState {
    val patients = mutableListOf<Patient>()
    val logs = mutableListOf<ActivityLog>()
    val doctors = listOf("Dr. Smith (ER)", "Dr. Adams (ICU)", "Dr. Baker (GP)", "Dr. Clark (Surgery)")
    var currentUser: User? = null
    val scanner = Scanner(System.`in`)

    init {
        // Seed initial data
        addLog("System initialized.")
        patients.add(Patient("P001", "John Doe", EmergencyStatus.CRITICAL, LocalDateTime.now().minusMinutes(10), "Dr. Smith (ER)", 1500.0))
        patients.add(Patient("P002", "Jane Roe", EmergencyStatus.STABLE, LocalDateTime.now().minusMinutes(5), null, 200.0))
    }

    fun addLog(msg: String) {
        logs.add(ActivityLog(LocalDateTime.now(), msg))
    }
}

// --- UI Utilities ---

object UI {
    private const val RESET = "\u001B[0m"
    private const val BOLD = "\u001B[1m"
    private const val CYAN = "\u001B[36m"

    fun clear() {
        // Simulate clear screen
        repeat(30) { println() }
    }

    fun header(title: String) {
        println("${CYAN}${BOLD}==========================================")
        println("   HOSPITAL COMMAND CENTER: $title")
        println("==========================================${RESET}")
    }

    fun alert(msg: String) {
        println("\n\u001B[41m\u001B[37m ALERT: $msg \u001B[0m\n")
    }

    fun formatTime(time: LocalDateTime): String {
        return time.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }
}

// --- Panel Controllers ---

fun loginPanel() {
    UI.header("LOGIN")
    print("Username: ")
    val username = CommandCenterState.scanner.next()
    print("Password: ")
    val password = CommandCenterState.scanner.next() // Simple simulation

    if (password == "admin123") {
        CommandCenterState.currentUser = User(username, "Administrator")
        CommandCenterState.addLog("User $username logged in.")
        dashboardPanel()
    } else {
        UI.alert("Invalid Credentials!")
        loginPanel()
    }
}

fun dashboardPanel() {
    while (true) {
        UI.clear()
        UI.header("DASHBOARD")
        println("Welcome, ${CommandCenterState.currentUser?.username} [${CommandCenterState.currentUser?.role}]")
        println("\n1. Emergency Monitoring")
        println("2. Patient Queue")
        println("3. Doctor Assignment")
        println("4. Billing Panel")
        println("5. Activity Logs")
        println("6. Logout")
        print("\nSelect Panel: ")

        when (CommandCenterState.scanner.next()) {
            "1" -> emergencyMonitoring()
            "2" -> patientQueue()
            "3" -> doctorAssignment()
            "4" -> billingPanel()
            "5" -> activityLogs()
            "6" -> {
                CommandCenterState.currentUser = null
                return
            }
        }
    }
}

fun emergencyMonitoring() {
    UI.clear()
    UI.header("EMERGENCY MONITORING")
    println(String.format("%-10s | %-15s | %-12s", "ID", "Name", "Status"))
    println("------------------------------------------")

    CommandCenterState.patients.forEach { p ->
        val color = p.status.colorCode
        println(String.format("%-10s | %-15s | %s%-12s\u001B[0m", p.id, p.name, color, p.status.label))
    }

    println("\n[M] Back to Menu | [U] Update Status")
    val choice = CommandCenterState.scanner.next().uppercase()
    if (choice == "U") {
        print("Enter Patient ID: ")
        val id = CommandCenterState.scanner.next()
        val patient = CommandCenterState.patients.find { it.id == id }
        if (patient != null) {
            println("1. CRITICAL 2. STABLE 3. WARNING 4. PENDING")
            val sIdx = CommandCenterState.scanner.next().toIntOrNull() ?: 4
            patient.status = EmergencyStatus.values().getOrElse(sIdx - 1) { EmergencyStatus.PENDING }
            CommandCenterState.addLog("Updated ${patient.name} to ${patient.status.label}")
        }
    }
}

fun patientQueue() {
    UI.clear()
    UI.header("PATIENT QUEUE (Real-time)")
    println(String.format("%-10s | %-15s | %-10s | %-15s", "ID", "Name", "Arrival", "Doctor"))
    println("------------------------------------------------------------")

    CommandCenterState.patients.sortedBy { it.arrivalTime }.forEach { p ->
        println(String.format("%-10s | %-15s | %-10s | %-15s",
            p.id, p.name, UI.formatTime(p.arrivalTime), p.assignedDoctor ?: "WAITING..."))
    }

    println("\n[A] Add Patient | [M] Back to Menu")
    val choice = CommandCenterState.scanner.next().uppercase()
    if (choice == "A") {
        print("Name: ")
        val name = CommandCenterState.scanner.next()
        val newId = "P${(CommandCenterState.patients.size + 1).toString().padStart(3, '0')}"
        CommandCenterState.patients.add(Patient(newId, name, EmergencyStatus.PENDING, LocalDateTime.now()))
        CommandCenterState.addLog("New patient admitted: $name ($newId)")
        UI.alert("Patient Added to Queue Successfully!")
    }
}

fun doctorAssignment() {
    UI.clear()
    UI.header("DOCTOR ASSIGNMENT")
    val unassigned = CommandCenterState.patients.filter { it.assignedDoctor == null }

    if (unassigned.isEmpty()) {
        println("All patients currently assigned.")
    } else {
        unassigned.forEachIndexed { idx, p -> println("${idx + 1}. ${p.name} (ID: ${p.id})") }
        print("\nSelect Patient # to assign: ")
        val pIdx = CommandCenterState.scanner.nextInt() - 1

        println("\nAvailable Doctors:")
        CommandCenterState.doctors.forEachIndexed { idx, d -> println("${idx + 1}. $d") }
        print("Select Doctor #: ")
        val dIdx = CommandCenterState.scanner.nextInt() - 1

        val p = unassigned[pIdx]
        p.assignedDoctor = CommandCenterState.doctors[dIdx]
        CommandCenterState.addLog("Assigned ${p.assignedDoctor} to ${p.name}")
    }

    println("\nPress any key to return...")
    CommandCenterState.scanner.next()
}

fun billingPanel() {
    UI.clear()
    UI.header("BILLING PANEL")
    println(String.format("%-10s | %-15s | %-10s", "ID", "Name", "Balance"))
    println("------------------------------------------")

    CommandCenterState.patients.forEach { p ->
        println(String.format("%-10s | %-15s | $%.2f", p.id, p.name, p.billAmount))
    }

    println("\n[P] Process Payment | [M] Menu")
    if (CommandCenterState.scanner.next().uppercase() == "P") {
        print("Enter Patient ID: ")
        val id = CommandCenterState.scanner.next()
        print("Add Amount: ")
        val amt = CommandCenterState.scanner.nextDouble()
        CommandCenterState.patients.find { it.id == id }?.let {
            it.billAmount += amt
            CommandCenterState.addLog("Billed $amt to ${it.name}")
        }
    }
}

fun activityLogs() {
    UI.clear()
    UI.header("SYSTEM ACTIVITY LOGS")
    CommandCenterState.logs.takeLast(15).reversed().forEach { log ->
        println("[${UI.formatTime(log.timestamp)}] ${log.message}")
    }
    println("\nPress any key to return...")
    CommandCenterState.scanner.next()
}

// --- Main Entry ---

fun main() {
    println("Starting Hospital Command Center...")
    Thread.sleep(1000)

    try {
        loginPanel()
    } catch (e: Exception) {
        println("Critical System Error: ${e.message}")
    } finally {
        println("System Shutdown.")
    }
}