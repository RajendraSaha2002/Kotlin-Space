import java.awt.*
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import javax.swing.*
import javax.swing.table.DefaultTableModel

class HospitalSystem : JFrame("Hospital Management System") {
    private val url = "jdbc:postgresql://localhost:5432/hospital_db"
    private val user = "postgres"
    private val pass = "varrie75"

    private val conn: Connection = DriverManager.getConnection(url, user, pass)

    private val patientModel = DefaultTableModel(arrayOf("ID", "Name", "Gender", "DOB", "Phone", "Email", "Address"), 0)
    private val doctorModel = DefaultTableModel(arrayOf("ID", "Name", "Specialization", "Phone", "Email"), 0)
    private val apptModel = DefaultTableModel(arrayOf("ID", "Patient", "Doctor", "Date", "Time", "Status"), 0)
    private val billModel = DefaultTableModel(arrayOf("BillID", "Appointment", "Amount", "Paid", "Date"), 0)

    private val revenueLabel = JLabel("Total Revenue: 0")

    init {
        setSize(1100, 650)
        layout = BorderLayout(8, 8)
        defaultCloseOperation = EXIT_ON_CLOSE

        val tabs = JTabbedPane()
        tabs.add("Patients", patientPanel())
        tabs.add("Doctors", doctorPanel())
        tabs.add("Appointments", appointmentPanel())
        tabs.add("Billing", billingPanel())
        tabs.add("Dashboard", dashboardPanel())

        add(tabs, BorderLayout.CENTER)

        loadAll()
        isVisible = true
    }

    private fun patientPanel(): JPanel {
        val table = JTable(patientModel)
        val name = JTextField()
        val gender = JTextField()
        val dob = JTextField()
        val phone = JTextField()
        val email = JTextField()
        val address = JTextField()

        val addBtn = JButton("Add Patient")
        addBtn.addActionListener {
            val sql = "INSERT INTO patients(full_name, gender, dob, phone, email, address) VALUES (?,?,?,?,?,?)"
            val ps = conn.prepareStatement(sql)
            ps.setString(1, name.text)
            ps.setString(2, gender.text)
            ps.setString(3, dob.text)
            ps.setString(4, phone.text)
            ps.setString(5, email.text)
            ps.setString(6, address.text)
            ps.executeUpdate()
            loadPatients()
        }

        val form = JPanel(GridLayout(7, 2))
        form.add(JLabel("Name")); form.add(name)
        form.add(JLabel("Gender")); form.add(gender)
        form.add(JLabel("DOB (YYYY-MM-DD)")); form.add(dob)
        form.add(JLabel("Phone")); form.add(phone)
        form.add(JLabel("Email")); form.add(email)
        form.add(JLabel("Address")); form.add(address)
        form.add(addBtn)

        val panel = JPanel(BorderLayout())
        panel.add(form, BorderLayout.WEST)
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun doctorPanel(): JPanel {
        val table = JTable(doctorModel)
        val name = JTextField()
        val spec = JTextField()
        val phone = JTextField()
        val email = JTextField()

        val addBtn = JButton("Add Doctor")
        addBtn.addActionListener {
            val sql = "INSERT INTO doctors(full_name, specialization, phone, email) VALUES (?,?,?,?)"
            val ps = conn.prepareStatement(sql)
            ps.setString(1, name.text)
            ps.setString(2, spec.text)
            ps.setString(3, phone.text)
            ps.setString(4, email.text)
            ps.executeUpdate()
            loadDoctors()
        }

        val form = JPanel(GridLayout(5, 2))
        form.add(JLabel("Name")); form.add(name)
        form.add(JLabel("Specialization")); form.add(spec)
        form.add(JLabel("Phone")); form.add(phone)
        form.add(JLabel("Email")); form.add(email)
        form.add(addBtn)

        val panel = JPanel(BorderLayout())
        panel.add(form, BorderLayout.WEST)
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun appointmentPanel(): JPanel {
        val table = JTable(apptModel)
        val patientId = JTextField()
        val doctorId = JTextField()
        val date = JTextField()
        val time = JTextField()

        val addBtn = JButton("Book Appointment")
        addBtn.addActionListener {
            val sql = "INSERT INTO appointments(patient_id, doctor_id, appointment_date, appointment_time) VALUES (?,?,?,?)"
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, patientId.text.toInt())
            ps.setInt(2, doctorId.text.toInt())
            ps.setString(3, date.text)
            ps.setString(4, time.text)
            ps.executeUpdate()
            loadAppointments()
        }

        val form = JPanel(GridLayout(5, 2))
        form.add(JLabel("Patient ID")); form.add(patientId)
        form.add(JLabel("Doctor ID")); form.add(doctorId)
        form.add(JLabel("Date (YYYY-MM-DD)")); form.add(date)
        form.add(JLabel("Time (HH:MM)")); form.add(time)
        form.add(addBtn)

        val panel = JPanel(BorderLayout())
        panel.add(form, BorderLayout.WEST)
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun billingPanel(): JPanel {
        val table = JTable(billModel)
        val apptId = JTextField()
        val amount = JTextField()
        val paid = JCheckBox("Paid")

        val addBtn = JButton("Create Bill")
        addBtn.addActionListener {
            val sql = "INSERT INTO billing(appointment_id, amount, paid) VALUES (?,?,?)"
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, apptId.text.toInt())
            ps.setDouble(2, amount.text.toDouble())
            ps.setBoolean(3, paid.isSelected)
            ps.executeUpdate()
            loadBilling()
            loadRevenue()
        }

        val form = JPanel(GridLayout(4, 2))
        form.add(JLabel("Appointment ID")); form.add(apptId)
        form.add(JLabel("Amount")); form.add(amount)
        form.add(paid)
        form.add(addBtn)

        val panel = JPanel(BorderLayout())
        panel.add(form, BorderLayout.WEST)
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun dashboardPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val refreshBtn = JButton("Refresh Revenue")
        refreshBtn.addActionListener { loadRevenue() }
        panel.add(revenueLabel, BorderLayout.CENTER)
        panel.add(refreshBtn, BorderLayout.SOUTH)
        return panel
    }

    private fun loadAll() {
        loadPatients()
        loadDoctors()
        loadAppointments()
        loadBilling()
        loadRevenue()
    }

    private fun loadPatients() {
        patientModel.rowCount = 0
        val rs = conn.createStatement().executeQuery("SELECT * FROM patients")
        while (rs.next()) {
            patientModel.addRow(arrayOf(
                rs.getInt("patient_id"), rs.getString("full_name"), rs.getString("gender"),
                rs.getDate("dob"), rs.getString("phone"), rs.getString("email"), rs.getString("address")
            ))
        }
    }

    private fun loadDoctors() {
        doctorModel.rowCount = 0
        val rs = conn.createStatement().executeQuery("SELECT * FROM doctors")
        while (rs.next()) {
            doctorModel.addRow(arrayOf(
                rs.getInt("doctor_id"), rs.getString("full_name"), rs.getString("specialization"),
                rs.getString("phone"), rs.getString("email")
            ))
        }
    }

    private fun loadAppointments() {
        apptModel.rowCount = 0
        val rs = conn.createStatement().executeQuery("""
            SELECT a.appointment_id, p.full_name AS patient, d.full_name AS doctor,
                   a.appointment_date, a.appointment_time, a.status
            FROM appointments a
            JOIN patients p ON a.patient_id = p.patient_id
            JOIN doctors d ON a.doctor_id = d.doctor_id
        """.trimIndent())
        while (rs.next()) {
            apptModel.addRow(arrayOf(
                rs.getInt("appointment_id"), rs.getString("patient"),
                rs.getString("doctor"), rs.getDate("appointment_date"),
                rs.getTime("appointment_time"), rs.getString("status")
            ))
        }
    }

    private fun loadBilling() {
        billModel.rowCount = 0
        val rs = conn.createStatement().executeQuery("SELECT * FROM billing")
        while (rs.next()) {
            billModel.addRow(arrayOf(
                rs.getInt("bill_id"), rs.getInt("appointment_id"),
                rs.getDouble("amount"), rs.getBoolean("paid"),
                rs.getDate("billed_date")
            ))
        }
    }

    private fun loadRevenue() {
        val rs = conn.createStatement().executeQuery("SELECT COALESCE(SUM(amount),0) AS total FROM billing WHERE paid = TRUE")
        if (rs.next()) {
            revenueLabel.text = "Total Revenue: ₹${rs.getDouble("total")}"
        }
    }
}

fun main() {
    SwingUtilities.invokeLater { HospitalSystem() }
}