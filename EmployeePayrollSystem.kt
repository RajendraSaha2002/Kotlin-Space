import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel

data class Employee(
    var id: String,
    var name: String,
    var dept: String,
    var role: String,
    var basic: Double,
    var hra: Double,
    var tax: Double,
    var bonus: Double,
    var overtime: Double
) {
    fun monthlySalary(): Double = basic + hra + bonus + overtime - tax
    fun annualSalary(): Double = monthlySalary() * 12
}

class PayrollSystem : JFrame("Employee Payroll System") {
    private val employees = mutableListOf<Employee>()

    private val model = DefaultTableModel(
        arrayOf("ID", "Name", "Dept", "Role", "Basic", "HRA", "Tax", "Bonus", "OT", "Monthly", "Annual"),
        0
    )
    private val table = JTable(model)

    private val idField = JTextField()
    private val nameField = JTextField()
    private val deptField = JTextField()
    private val roleField = JTextField()
    private val basicField = JTextField()
    private val hraField = JTextField()
    private val taxField = JTextField()
    private val bonusField = JTextField()
    private val overtimeField = JTextField()

    private val searchField = JTextField()
    private val deptFilter = JComboBox(arrayOf("All", "HR", "IT", "Sales", "Finance"))
    private val roleFilter = JComboBox(arrayOf("All", "Manager", "Developer", "Analyst", "Executive"))

    init {
        setSize(1000, 650)
        layout = BorderLayout(8, 8)
        defaultCloseOperation = EXIT_ON_CLOSE

        add(buildFormPanel(), BorderLayout.WEST)
        add(buildCenterPanel(), BorderLayout.CENTER)
        add(buildBottomPanel(), BorderLayout.SOUTH)

        isVisible = true
    }

    private fun buildFormPanel(): JPanel {
        val panel = JPanel(GridLayout(12, 2, 5, 5))
        panel.border = BorderFactory.createTitledBorder("Employee Form")

        panel.add(JLabel("ID"))
        panel.add(idField)
        panel.add(JLabel("Name"))
        panel.add(nameField)
        panel.add(JLabel("Department"))
        panel.add(deptField)
        panel.add(JLabel("Role"))
        panel.add(roleField)
        panel.add(JLabel("Basic"))
        panel.add(basicField)
        panel.add(JLabel("HRA"))
        panel.add(hraField)
        panel.add(JLabel("Tax"))
        panel.add(taxField)
        panel.add(JLabel("Bonus"))
        panel.add(bonusField)
        panel.add(JLabel("Overtime"))
        panel.add(overtimeField)

        val addBtn = JButton("Add")
        val updateBtn = JButton("Update")
        val clearBtn = JButton("Clear")

        addBtn.addActionListener { addEmployee() }
        updateBtn.addActionListener { updateEmployee() }
        clearBtn.addActionListener { clearForm() }

        panel.add(addBtn)
        panel.add(updateBtn)
        panel.add(clearBtn)

        return panel
    }

    private fun buildCenterPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("Employees")
        table.selectionModel.addListSelectionListener {
            val row = table.selectedRow
            if (row >= 0) fillFormFromRow(row)
        }
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun buildBottomPanel(): JPanel {
        val panel = JPanel(GridLayout(2, 1))

        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        searchPanel.add(JLabel("Search (ID/Name):"))
        searchPanel.add(searchField)
        val searchBtn = JButton("Search")
        val resetBtn = JButton("Reset")
        searchBtn.addActionListener { searchEmployee() }
        resetBtn.addActionListener { refreshTable(employees) }
        searchPanel.add(searchBtn)
        searchPanel.add(resetBtn)

        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        filterPanel.add(JLabel("Filter Dept:"))
        filterPanel.add(deptFilter)
        filterPanel.add(JLabel("Filter Role:"))
        filterPanel.add(roleFilter)
        val filterBtn = JButton("Apply Filter")
        filterBtn.addActionListener { applyFilter() }
        filterPanel.add(filterBtn)

        panel.add(searchPanel)
        panel.add(filterPanel)
        return panel
    }

    private fun addEmployee() {
        try {
            val emp = Employee(
                idField.text.trim(),
                nameField.text.trim(),
                deptField.text.trim(),
                roleField.text.trim(),
                basicField.text.toDouble(),
                hraField.text.toDouble(),
                taxField.text.toDouble(),
                bonusField.text.toDouble(),
                overtimeField.text.toDouble()
            )
            if (emp.id.isBlank() || emp.name.isBlank()) {
                showError("ID and Name are required.")
                return
            }
            employees.add(emp)
            refreshTable(employees)
            generatePayslip(emp)
            clearForm()
        } catch (e: Exception) {
            showError("Invalid input. Please enter numeric values where needed.")
        }
    }

    private fun updateEmployee() {
        val row = table.selectedRow
        if (row < 0) {
            showError("Select an employee to update.")
            return
        }
        try {
            val emp = employees[row]
            emp.id = idField.text.trim()
            emp.name = nameField.text.trim()
            emp.dept = deptField.text.trim()
            emp.role = roleField.text.trim()
            emp.basic = basicField.text.toDouble()
            emp.hra = hraField.text.toDouble()
            emp.tax = taxField.text.toDouble()
            emp.bonus = bonusField.text.toDouble()
            emp.overtime = overtimeField.text.toDouble()
            refreshTable(employees)
        } catch (e: Exception) {
            showError("Invalid input.")
        }
    }

    private fun searchEmployee() {
        val q = searchField.text.trim().lowercase()
        val result = employees.filter {
            it.id.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
        refreshTable(result)
    }

    private fun applyFilter() {
        val d = deptFilter.selectedItem.toString()
        val r = roleFilter.selectedItem.toString()
        val result = employees.filter {
            (d == "All" || it.dept == d) && (r == "All" || it.role == r)
        }
        refreshTable(result)
    }

    private fun refreshTable(list: List<Employee>) {
        model.rowCount = 0
        list.forEach {
            model.addRow(
                arrayOf(
                    it.id, it.name, it.dept, it.role,
                    it.basic, it.hra, it.tax, it.bonus, it.overtime,
                    it.monthlySalary(), it.annualSalary()
                )
            )
        }
    }

    private fun generatePayslip(emp: Employee) {
        val msg = """
            ===== Payslip =====
            ID: ${emp.id}
            Name: ${emp.name}
            Dept: ${emp.dept}
            Role: ${emp.role}

            Basic: ${emp.basic}
            HRA: ${emp.hra}
            Bonus: ${emp.bonus}
            Overtime: ${emp.overtime}
            Tax: ${emp.tax}

            Monthly Salary: ${emp.monthlySalary()}
            Annual Salary: ${emp.annualSalary()}
        """.trimIndent()

        JOptionPane.showMessageDialog(this, msg, "Payslip", JOptionPane.INFORMATION_MESSAGE)

        val file = File("payslip_${emp.id}.txt")
        file.writeText(msg)
    }

    private fun fillFormFromRow(row: Int) {
        idField.text = model.getValueAt(row, 0).toString()
        nameField.text = model.getValueAt(row, 1).toString()
        deptField.text = model.getValueAt(row, 2).toString()
        roleField.text = model.getValueAt(row, 3).toString()
        basicField.text = model.getValueAt(row, 4).toString()
        hraField.text = model.getValueAt(row, 5).toString()
        taxField.text = model.getValueAt(row, 6).toString()
        bonusField.text = model.getValueAt(row, 7).toString()
        overtimeField.text = model.getValueAt(row, 8).toString()
    }

    private fun clearForm() {
        listOf(idField, nameField, deptField, roleField, basicField, hraField, taxField, bonusField, overtimeField)
            .forEach { it.text = "" }
    }

    private fun showError(msg: String) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE)
    }
}

fun main() {
    SwingUtilities.invokeLater { PayrollSystem() }
}