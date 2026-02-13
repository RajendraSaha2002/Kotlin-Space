import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.*
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.swing.*
import javax.swing.border.TitledBorder
import kotlin.concurrent.thread

private const val SALT_LEN = 16
private const val IV_LEN = 16
private const val ITERATIONS = 65536
private const val KEY_LEN = 256
private const val MAX_FILE_MB = 100

class FileEncryptionTool : JFrame("File Encryption Tool") {
    private var selectedFile: File? = null
    private var outputDir: File? = null

    private val statusLabel = JLabel("Select a file")
    private val progress = JProgressBar(0, 100)
    private val recentModel = DefaultListModel<String>()
    private val recentList = JList(recentModel)
    private val logArea = JTextArea(6, 30)

    private val passwordField = JPasswordField()
    private val autoDelete = JCheckBox("Auto-delete original after operation")

    init {
        setSize(850, 600)
        layout = BorderLayout(8, 8)
        defaultCloseOperation = EXIT_ON_CLOSE

        add(buildTopPanel(), BorderLayout.NORTH)
        add(buildCenterPanel(), BorderLayout.CENTER)
        add(buildBottomPanel(), BorderLayout.SOUTH)

        enableDragDrop()
        isVisible = true
    }

    private fun buildTopPanel(): JPanel {
        val panel = JPanel(BorderLayout(5, 5))
        panel.border = TitledBorder("File Selection")

        val fileBtn = JButton("Select File")
        val outBtn = JButton("Select Output Folder")
        fileBtn.addActionListener { chooseFile() }
        outBtn.addActionListener { chooseOutputDir() }

        val left = JPanel(FlowLayout(FlowLayout.LEFT))
        left.add(fileBtn)
        left.add(outBtn)

        panel.add(left, BorderLayout.WEST)
        panel.add(statusLabel, BorderLayout.CENTER)

        return panel
    }

    private fun buildCenterPanel(): JPanel {
        val panel = JPanel(GridLayout(1, 2, 8, 8))

        val left = JPanel(BorderLayout())
        left.border = TitledBorder("Controls")
        val form = JPanel(GridLayout(5, 1, 5, 5))
        form.add(JLabel("Password (key):"))
        form.add(passwordField)

        val encBtn = JButton("Encrypt")
        val decBtn = JButton("Decrypt")
        encBtn.addActionListener { processFile(true) }
        decBtn.addActionListener { processFile(false) }

        val btnRow = JPanel(GridLayout(1, 2, 6, 6))
        btnRow.add(encBtn)
        btnRow.add(decBtn)

        form.add(autoDelete)
        form.add(btnRow)
        left.add(form, BorderLayout.NORTH)

        val right = JPanel(BorderLayout())
        right.border = TitledBorder("Recent Files")
        right.add(JScrollPane(recentList), BorderLayout.CENTER)

        panel.add(left)
        panel.add(right)
        return panel
    }

    private fun buildBottomPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Status / Log")

        progress.isStringPainted = true
        logArea.isEditable = false

        panel.add(progress, BorderLayout.NORTH)
        panel.add(JScrollPane(logArea), BorderLayout.CENTER)
        return panel
    }

    private fun chooseFile() {
        val fc = JFileChooser()
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.selectedFile
            statusLabel.text = "Selected: ${selectedFile!!.absolutePath}"
            addRecent(selectedFile!!.absolutePath)
        }
    }

    private fun chooseOutputDir() {
        val fc = JFileChooser()
        fc.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDir = fc.selectedFile
            log("Output folder: ${outputDir!!.absolutePath}")
        }
    }

    private fun processFile(encrypt: Boolean) {
        val file = selectedFile ?: return showError("Select a file first.")
        val pass = String(passwordField.password).trim()
        if (pass.isBlank()) return showError("Enter password.")

        if (file.length() > MAX_FILE_MB * 1024L * 1024L) {
            return showError("File too large. Max ${MAX_FILE_MB}MB.")
        }

        progress.value = 0
        thread {
            try {
                if (encrypt) encryptFile(file, pass) else decryptFile(file, pass)
                if (autoDelete.isSelected) file.delete()
                SwingUtilities.invokeLater {
                    progress.value = 100
                    showStatus(if (encrypt) "Encrypted successfully" else "Decrypted successfully")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
                log("ERROR: ${e.message}")
            }
        }
    }

    private fun encryptFile(file: File, password: String) {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val outFile = File(outputDir ?: file.parentFile, file.name + ".enc")
        Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            FileInputStream(file).use { fis ->
                FileOutputStream(outFile).use { fos ->
                    fos.write(salt)
                    fos.write(iv)
                    CipherOutputStream(fos, this).use { cos ->
                        copyWithProgress(fis, cos, file.length())
                    }
                }
            }
        }
    }

    private fun decryptFile(file: File, password: String) {
        FileInputStream(file).use { fis ->
            val salt = fis.readNBytes(SALT_LEN)
            val iv = fis.readNBytes(IV_LEN)
            val key = deriveKey(password, salt)

            val outName = if (file.name.endsWith(".enc")) file.name.removeSuffix(".enc") else file.name + ".dec"
            val outFile = File(outputDir ?: file.parentFile, outName)

            Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                CipherInputStream(fis, this).use { cis ->
                    FileOutputStream(outFile).use { fos ->
                        copyWithProgress(cis, fos, file.length())
                    }
                }
            }
        }
    }

    private fun copyWithProgress(input: InputStream, output: OutputStream, total: Long) {
        val buffer = ByteArray(4096)
        var read: Int
        var processed = 0L
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            processed += read
            val percent = ((processed * 100) / total).toInt().coerceIn(0, 100)
            SwingUtilities.invokeLater { progress.value = percent }
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }

    private fun enableDragDrop() {
        val dropTarget = DropTarget(this, object : DropTargetAdapter() {
            override fun drop(dtde: DropTargetDropEvent) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY)
                val data = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                val f = data.firstOrNull() as? File ?: return
                selectedFile = f
                statusLabel.text = "Selected: ${f.absolutePath}"
                addRecent(f.absolutePath)
            }
        })
        this.dropTarget = dropTarget
    }

    private fun addRecent(path: String) {
        if (!recentModel.contains(path)) {
            recentModel.addElement(path)
        }
    }

    private fun showStatus(msg: String) {
        statusLabel.text = msg
        log(msg)
    }

    private fun log(msg: String) {
        logArea.append(msg + "\n")
    }

    private fun showError(msg: String) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun main() {
    SwingUtilities.invokeLater { FileEncryptionTool() }
}