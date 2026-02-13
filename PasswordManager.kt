import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.swing.*
import javax.swing.table.DefaultTableModel
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

data class VaultEntry(
    var site: String,
    var username: String,
    var password: String,
    var tags: String
)

class PasswordManager : JFrame("Offline Password Manager") {
    private val vaultFile = File("vault.enc")
    private val masterFile = File("master.hash")
    private var masterKey: ByteArray? = null
    private var unlocked = false

    private val model = DefaultTableModel(arrayOf("Site", "Username", "Tags"), 0)
    private val table = JTable(model)

    private val siteField = JTextField()
    private val userField = JTextField()
    private val passField = JPasswordField()
    private val tagsField = JTextField()

    private val searchField = JTextField()
    private val showPass = JCheckBox("Show Password")
    private val statusLabel = JLabel("Locked")
    private val autoLogoutSeconds = 120
    private var idleCounter = autoLogoutSeconds

    private val entries = mutableListOf<VaultEntry>()

    init {
        setSize(900, 600)
        layout = BorderLayout(8, 8)
        defaultCloseOperation = EXIT_ON_CLOSE

        add(buildTopPanel(), BorderLayout.NORTH)
        add(buildCenterPanel(), BorderLayout.CENTER)
        add(buildBottomPanel(), BorderLayout.SOUTH)

        setupIdleTimer()
        promptMasterPassword()
        isVisible = true
    }

    private fun buildTopPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val left = JPanel(FlowLayout(FlowLayout.LEFT))
        val right = JPanel(FlowLayout(FlowLayout.RIGHT))

        val lockBtn = JButton("Lock")
        val unlockBtn = JButton("Unlock")
        lockBtn.addActionListener { lock() }
        unlockBtn.addActionListener { promptMasterPassword() }

        left.add(lockBtn)
        left.add(unlockBtn)
        left.add(statusLabel)

        right.add(JLabel("Search:"))
        right.add(searchField)
        val searchBtn = JButton("Search")
        val resetBtn = JButton("Reset")
        searchBtn.addActionListener { search() }
        resetBtn.addActionListener { refreshTable(entries) }
        right.add(searchBtn)
        right.add(resetBtn)

        panel.add(left, BorderLayout.WEST)
        panel.add(right, BorderLayout.EAST)
        return panel
    }

    private fun buildCenterPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("Saved Passwords")
        panel.add(JScrollPane(table), BorderLayout.CENTER)

        val actions = JPanel(FlowLayout(FlowLayout.LEFT))
        val viewBtn = JButton("View Password")
        val copyBtn = JButton("Copy Password")
        val deleteBtn = JButton("Delete")
        viewBtn.addActionListener { viewPassword() }
        copyBtn.addActionListener { copyPassword() }
        deleteBtn.addActionListener { deleteEntry() }
        actions.add(viewBtn)
        actions.add(copyBtn)
        actions.add(deleteBtn)

        panel.add(actions, BorderLayout.SOUTH)
        return panel
    }

    private fun buildBottomPanel(): JPanel {
        val panel = JPanel(GridLayout(6, 2, 5, 5))
        panel.border = BorderFactory.createTitledBorder("Add / Update Entry")

        panel.add(JLabel("Website"))
        panel.add(siteField)
        panel.add(JLabel("Username"))
        panel.add(userField)
        panel.add(JLabel("Password"))
        panel.add(passField)
        panel.add(JLabel("Tags"))
        panel.add(tagsField)
        panel.add(showPass)

        showPass.addActionListener {
            passField.echoChar = if (showPass.isSelected) 0.toChar() else '•'
        }

        val addBtn = JButton("Add/Update")
        val genBtn = JButton("Generate Password")
        val exportBtn = JButton("Export Vault")
        val importBtn = JButton("Import Vault")

        addBtn.addActionListener { addOrUpdate() }
        genBtn.addActionListener { generatePassword() }
        exportBtn.addActionListener { exportVault() }
        importBtn.addActionListener { importVault() }

        panel.add(addBtn)
        panel.add(genBtn)
        panel.add(exportBtn)
        panel.add(importBtn)
        return panel
    }

    private fun promptMasterPassword() {
        val input = JOptionPane.showInputDialog(this, "Enter Master Password")
        if (input.isNullOrBlank()) return

        if (!masterFile.exists()) {
            masterFile.writeText(hash(input))
            masterKey = deriveKey(input)
            unlocked = true
            statusLabel.text = "Unlocked"
            loadVault()
            return
        }

        val savedHash = masterFile.readText().trim()
        if (hash(input) == savedHash) {
            masterKey = deriveKey(input)
            unlocked = true
            statusLabel.text = "Unlocked"
            loadVault()
        } else {
            JOptionPane.showMessageDialog(this, "Invalid master password", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun lock() {
        unlocked = false
        masterKey = null
        entries.clear()
        refreshTable(entries)
        statusLabel.text = "Locked"
    }

    private fun addOrUpdate() {
        if (!unlocked) return showError("Unlock first.")
        val site = siteField.text.trim()
        val user = userField.text.trim()
        val pass = String(passField.password).trim()
        val tags = tagsField.text.trim()

        if (site.isBlank() || user.isBlank() || pass.isBlank()) {
            showError("Website, Username, Password required.")
            return
        }

        val existing = entries.find { it.site == site && it.username == user }
        if (existing != null) {
            existing.password = pass
            existing.tags = tags
        } else {
            entries.add(VaultEntry(site, user, pass, tags))
        }
        saveVault()
        refreshTable(entries)
        clearForm()
    }

    private fun viewPassword() {
        if (!unlocked) return showError("Unlock first.")
        val row = table.selectedRow
        if (row < 0) return
        val entry = entries[row]
        JOptionPane.showMessageDialog(this, "Password: ${entry.password}")
    }

    private fun copyPassword() {
        if (!unlocked) return showError("Unlock first.")
        val row = table.selectedRow
        if (row < 0) return
        val entry = entries[row]
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(entry.password), null)
        JOptionPane.showMessageDialog(this, "Copied to clipboard (auto-clear in 10s)")
        fixedRateTimer(initialDelay = 10000, period = 10000, daemon = true) {
            clipboard.setContents(StringSelection(""), null)
            cancel()
        }
    }

    private fun deleteEntry() {
        if (!unlocked) return showError("Unlock first.")
        val row = table.selectedRow
        if (row < 0) return
        entries.removeAt(row)
        saveVault()
        refreshTable(entries)
    }

    private fun search() {
        val q = searchField.text.trim().lowercase()
        val result = entries.filter {
            it.site.lowercase().contains(q) || it.username.lowercase().contains(q)
        }
        refreshTable(result)
    }

    private fun refreshTable(list: List<VaultEntry>) {
        model.rowCount = 0
        list.forEach {
            model.addRow(arrayOf(it.site, it.username, it.tags))
        }
    }

    private fun generatePassword() {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#\$%&!"
        val pass = (1..12).map { chars.random() }.joinToString("")
        passField.text = pass
    }

    private fun exportVault() {
        if (!unlocked) return showError("Unlock first.")
        saveVault()
        JOptionPane.showMessageDialog(this, "Vault exported to ${vaultFile.name}")
    }

    private fun importVault() {
        if (!unlocked) return showError("Unlock first.")
        loadVault()
        JOptionPane.showMessageDialog(this, "Vault imported")
    }

    private fun saveVault() {
        val data = buildString {
            entries.forEach {
                appendLine("${it.site}|${it.username}|${it.password}|${it.tags}")
            }
        }
        val enc = encrypt(data, masterKey!!)
        vaultFile.writeText(enc)
    }

    private fun loadVault() {
        entries.clear()
        if (!vaultFile.exists() || masterKey == null) return
        val enc = vaultFile.readText()
        val data = decrypt(enc, masterKey!!)
        data.lines().filter { it.isNotBlank() }.forEach {
            val parts = it.split("|")
            if (parts.size >= 4) {
                entries.add(VaultEntry(parts[0], parts[1], parts[2], parts[3]))
            }
        }
        refreshTable(entries)
    }

    private fun clearForm() {
        siteField.text = ""
        userField.text = ""
        passField.text = ""
        tagsField.text = ""
    }

    private fun setupIdleTimer() {
        fixedRateTimer(initialDelay = 1000, period = 1000, daemon = true) {
            if (unlocked) {
                idleCounter--
                if (idleCounter <= 0) {
                    SwingUtilities.invokeLater { lock() }
                    idleCounter = autoLogoutSeconds
                }
            }
        }
        val reset = { idleCounter = autoLogoutSeconds }
        val listener = object : java.awt.event.MouseAdapter() {
            override fun mouseMoved(e: java.awt.event.MouseEvent?) = reset()
            override fun mouseClicked(e: java.awt.event.MouseEvent?) = reset()
        }
        addMouseListener(listener)
        addMouseMotionListener(listener)
    }

    private fun hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun deriveKey(password: String): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(password.toByteArray()).copyOf(16)
    }

    private fun encrypt(text: String, key: ByteArray): String {
        val cipher = Cipher.getInstance("AES")
        val secret = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray()))
    }

    private fun decrypt(text: String, key: ByteArray): String {
        val cipher = Cipher.getInstance("AES")
        val secret = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secret)
        val decoded = Base64.getDecoder().decode(text)
        return String(cipher.doFinal(decoded))
    }

    private fun showError(msg: String) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE)
    }
}

fun main() {
    SwingUtilities.invokeLater { PasswordManager() }
}