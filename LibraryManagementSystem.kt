import java.awt.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.DefaultTableModel

data class Book(
    var id: String,
    var title: String,
    var author: String,
    var category: String,
    var available: Boolean = true,
    var issuedTo: String? = null,
    var dueDate: LocalDate? = null
)

data class Member(
    var id: String,
    var name: String
)

data class HistoryEntry(
    val time: LocalDate,
    val action: String,
    val details: String
)

class LibrarySystem : JFrame("Library Management System") {
    private val books = mutableListOf<Book>()
    private val members = mutableListOf<Member>()
    private val history = mutableListOf<HistoryEntry>()

    private val model = DefaultTableModel(arrayOf("ID", "Title", "Author", "Category", "Status", "Due Date", "Issued To"), 0)
    private val table = JTable(model)

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val file = File("library_data.txt")
    private val historyFile = File("library_history.txt")

    private val idField = JTextField()
    private val titleField = JTextField()
    private val authorField = JTextField()
    private val categoryField = JTextField()

    private val memberIdField = JTextField()
    private val memberNameField = JTextField()

    private val searchField = JTextField()
    private val categoryFilter = JComboBox(arrayOf("All", "Science", "Math", "History", "Novel", "Other"))

    init {
        setSize(1100, 650)
        layout = BorderLayout(8, 8)
        defaultCloseOperation = EXIT_ON_CLOSE

        loadData()
        add(buildBookPanel(), BorderLayout.WEST)
        add(buildCenterPanel(), BorderLayout.CENTER)
        add(buildBottomPanel(), BorderLayout.SOUTH)

        refreshTable(books)
        isVisible = true
    }

    private fun buildBookPanel(): JPanel {
        val panel = JPanel(GridLayout(10, 2, 5, 5))
        panel.border = BorderFactory.createTitledBorder("Book Form")

        panel.add(JLabel("Book ID"))
        panel.add(idField)
        panel.add(JLabel("Title"))
        panel.add(titleField)
        panel.add(JLabel("Author"))
        panel.add(authorField)
        panel.add(JLabel("Category"))
        panel.add(categoryField)

        val addBtn = JButton("Add Book")
        val issueBtn = JButton("Issue Book")
        val returnBtn = JButton("Return Book")
        val payslipBtn = JButton("Export Report")

        addBtn.addActionListener { addBook() }
        issueBtn.addActionListener { issueBook() }
        returnBtn.addActionListener { returnBook() }
        payslipBtn.addActionListener { exportReport() }

        panel.add(addBtn)
        panel.add(issueBtn)
        panel.add(returnBtn)
        panel.add(payslipBtn)

        return panel
    }

    private fun buildCenterPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("Books")
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun buildBottomPanel(): JPanel {
        val panel = JPanel(GridLayout(2, 1))

        val memberPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        memberPanel.border = BorderFactory.createTitledBorder("Member")
        memberPanel.add(JLabel("ID"))
        memberPanel.add(memberIdField)
        memberPanel.add(JLabel("Name"))
        memberPanel.add(memberNameField)
        val addMemberBtn = JButton("Add Member")
        addMemberBtn.addActionListener { addMember() }
        memberPanel.add(addMemberBtn)

        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        searchPanel.add(JLabel("Search Title/Author:"))
        searchPanel.add(searchField)
        searchPanel.add(JLabel("Category:"))
        searchPanel.add(categoryFilter)
        val searchBtn = JButton("Search")
        val resetBtn = JButton("Reset")
        val historyBtn = JButton("View History")
        val overdueBtn = JButton("Overdue Reminder")

        searchBtn.addActionListener { searchBooks() }
        resetBtn.addActionListener { refreshTable(books) }
        historyBtn.addActionListener { showHistory() }
        overdueBtn.addActionListener { showOverdueReminder() }

        searchPanel.add(searchBtn)
        searchPanel.add(resetBtn)
        searchPanel.add(historyBtn)
        searchPanel.add(overdueBtn)

        panel.add(memberPanel)
        panel.add(searchPanel)
        return panel
    }

    private fun addBook() {
        val id = idField.text.trim()
        val title = titleField.text.trim()
        val author = authorField.text.trim()
        val category = categoryField.text.trim().ifEmpty { "Other" }

        if (id.isBlank() || title.isBlank() || author.isBlank()) {
            showError("ID, Title, Author required.")
            return
        }

        if (books.any { it.id == id }) {
            showError("Book ID already exists.")
            return
        }

        books.add(Book(id, title, author, category))
        history.add(HistoryEntry(LocalDate.now(), "ADD", "Added $title"))
        saveData()
        refreshTable(books)
        clearBookForm()
    }

    private fun issueBook() {
        val row = table.selectedRow
        if (row < 0) {
            showError("Select a book to issue.")
            return
        }

        val book = books[row]
        if (!book.available) {
            showError("Book already issued.")
            return
        }

        val memberId = memberIdField.text.trim()
        val member = members.find { it.id == memberId }
        if (member == null) {
            showError("Member not found.")
            return
        }

        book.available = false
        book.issuedTo = member.name
        book.dueDate = LocalDate.now().plusDays(7)

        history.add(HistoryEntry(LocalDate.now(), "ISSUE", "Issued ${book.title} to ${member.name}"))
        saveData()
        refreshTable(books)
    }

    private fun returnBook() {
        val row = table.selectedRow
        if (row < 0) {
            showError("Select a book to return.")
            return
        }

        val book = books[row]
        if (book.available) {
            showError("Book is already available.")
            return
        }

        val today = LocalDate.now()
        val due = book.dueDate ?: today
        val fine = if (today.isAfter(due)) {
            val daysLate = today.toEpochDay() - due.toEpochDay()
            daysLate * 5
        } else 0

        JOptionPane.showMessageDialog(this, "Fine: Rs $fine", "Return", JOptionPane.INFORMATION_MESSAGE)

        book.available = true
        book.issuedTo = null
        book.dueDate = null

        history.add(HistoryEntry(LocalDate.now(), "RETURN", "Returned ${book.title} with fine Rs $fine"))
        saveData()
        refreshTable(books)
    }

    private fun addMember() {
        val id = memberIdField.text.trim()
        val name = memberNameField.text.trim()

        if (id.isBlank() || name.isBlank()) {
            showError("Member ID and Name required.")
            return
        }

        if (members.any { it.id == id }) {
            showError("Member already exists.")
            return
        }

        members.add(Member(id, name))
        saveData()
        JOptionPane.showMessageDialog(this, "Member added")
        memberIdField.text = ""
        memberNameField.text = ""
    }

    private fun searchBooks() {
        val q = searchField.text.trim().lowercase()
        val cat = categoryFilter.selectedItem.toString()

        val result = books.filter {
            (it.title.lowercase().contains(q) || it.author.lowercase().contains(q)) &&
                    (cat == "All" || it.category == cat)
        }
        refreshTable(result)
    }

    private fun showOverdueReminder() {
        val today = LocalDate.now()
        val overdue = books.filter { !it.available && it.dueDate != null && today.isAfter(it.dueDate) }
        if (overdue.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No overdue books.")
            return
        }
        val msg = overdue.joinToString("\n") { "${it.title} (Due: ${it.dueDate})" }
        JOptionPane.showMessageDialog(this, msg, "Overdue Books", JOptionPane.WARNING_MESSAGE)
    }

    private fun showHistory() {
        val msg = history.joinToString("\n") { "[${it.time}] ${it.action}: ${it.details}" }
        JOptionPane.showMessageDialog(this, msg.ifBlank { "No history." }, "History", JOptionPane.INFORMATION_MESSAGE)
        historyFile.writeText(msg)
    }

    private fun exportReport() {
        val issued = books.filter { !it.available }
        val report = StringBuilder("Issued Books Report\n\n")
        issued.forEach {
            report.append("${it.title} -> ${it.issuedTo} (Due: ${it.dueDate})\n")
        }
        File("issued_report.txt").writeText(report.toString())
        JOptionPane.showMessageDialog(this, "Report saved to issued_report.txt")
    }

    private fun refreshTable(list: List<Book>) {
        model.rowCount = 0
        list.forEach {
            model.addRow(
                arrayOf(
                    it.id, it.title, it.author, it.category,
                    if (it.available) "Available" else "Issued",
                    it.dueDate?.format(formatter) ?: "-",
                    it.issuedTo ?: "-"
                )
            )
        }
    }

    private fun saveData() {
        val data = buildString {
            appendLine(books.size)
            books.forEach {
                appendLine("${it.id}|${it.title}|${it.author}|${it.category}|${it.available}|${it.issuedTo}|${it.dueDate}")
            }
            appendLine(members.size)
            members.forEach { appendLine("${it.id}|${it.name}") }
        }
        file.writeText(data)
    }

    private fun loadData() {
        if (!file.exists()) return
        val lines = file.readLines()
        var index = 0

        val bookCount = lines[index++].toInt()
        repeat(bookCount) {
            val parts = lines[index++].split("|")
            val book = Book(
                parts[0], parts[1], parts[2], parts[3],
                parts[4].toBoolean(),
                parts[5].takeIf { it != "null" },
                parts[6].takeIf { it != "null" }?.let { LocalDate.parse(it) }
            )
            books.add(book)
        }

        val memberCount = lines[index++].toInt()
        repeat(memberCount) {
            val parts = lines[index++].split("|")
            members.add(Member(parts[0], parts[1]))
        }
    }

    private fun clearBookForm() {
        idField.text = ""
        titleField.text = ""
        authorField.text = ""
        categoryField.text = ""
    }

    private fun showError(msg: String) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE)
    }
}

fun main() {
    SwingUtilities.invokeLater { LibrarySystem() }
}