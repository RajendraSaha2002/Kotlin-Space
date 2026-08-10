import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Main Application Entry Point.
 */
fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Clicker Tycoon Business Simulator")
        val gamePanel = ClickerTycoonPanel()

        frame.add(gamePanel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isResizable = false
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/**
 * Main Game Panel handling Tycoon Logic, Data Structures, and UI Layout.
 */
class ClickerTycoonPanel : JPanel() {

    companion object {
        /**
         * Formats raw values into standard financial suffixes ($1.25K, $3.50M, $10.20B, etc.).
         */
        fun formatMoney(amount: Double): String {
            if (amount < 1000.0) return String.format("$%.2f", amount)
            val suffixes = arrayOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp")
            var value = amount
            var index = 0
            while (value >= 1000.0 && index < suffixes.size - 1) {
                value /= 1000.0
                index++
            }
            return String.format("$%.2f %s", value, suffixes[index])
        }
    }

    // Business Node Data Model
    class Business(
        val name: String,
        val icon: String,
        val baseCost: Double,
        val baseIncomePerSec: Double,
        var count: Int = 0,
        val costMultiplier: Double = 1.15
    ) {
        fun getCost(): Double = baseCost * Math.pow(costMultiplier, count.toDouble())
        fun getIncomePerSec(): Double = count * baseIncomePerSec
    }

    // Upgrade Data Model
    class Upgrade(
        val name: String,
        val desc: String,
        val cost: Double,
        var bought: Boolean = false,
        val effect: () -> Unit
    )

    // Game State Variables
    private var money = 0.0
    private var clickPower = 1.0
    private var globalMultiplier = 1.0

    // Automated Resource Nodes
    private val businesses = listOf(
        Business("Lemonade Stand", "🍋", 15.0, 1.0),
        Business("Coffee Shop", "☕", 100.0, 6.0),
        Business("Tech Startup", "💻", 1100.0, 42.0),
        Business("Automobile Factory", "🚗", 12000.0, 320.0),
        Business("Bank Chain", "🏦", 130000.0, 2400.0),
        Business("Rocket Enterprise", "🚀", 1400000.0, 19000.0)
    )

    // Upgrades
    private val upgrades = mutableListOf<Upgrade>()

    // UI Dashboard Labels
    private val moneyLabel = JLabel("$0.00")
    private val incomeLabel = JLabel("$0.00 / sec")
    private val clickPowerLabel = JLabel("$1.00")

    // Component Binding Classes
    private class BusinessUI(
        val business: Business,
        val countLabel: JLabel,
        val incomeLabel: JLabel,
        val buyBtn: JButton
    )

    private val businessUIList = mutableListOf<BusinessUI>()

    private class UpgradeUI(
        val upgrade: Upgrade,
        val button: JButton
    )

    private val upgradeUIList = mutableListOf<UpgradeUI>()

    // Passive Background Timer (Updates 10 times per second = 100ms interval)
    private val timer = Timer(100) { tick() }

    init {
        preferredSize = Dimension(920, 640)
        background = Color(20, 24, 34)
        layout = BorderLayout(10, 10)
        border = EmptyBorder(15, 15, 15, 15)

        initUpgrades()

        // Header Dashboard
        add(createHeaderPanel(), BorderLayout.NORTH)

        // Split Main Panel (Left: Clicker Vault | Right: Shop Tabs)
        val centerPanel = JPanel(GridLayout(1, 2, 15, 0))
        centerPanel.isOpaque = false

        centerPanel.add(createClickerPanel())
        centerPanel.add(createShopPanel())

        add(centerPanel, BorderLayout.CENTER)

        refreshGameUI()
        timer.start()
    }

    private fun initUpgrades() {
        upgrades.add(Upgrade("Stronger Clicks", "Double click efficiency", 50.0) { clickPower *= 2.0 })
        upgrades.add(Upgrade("Lemonade Turbo", "Global income +20%", 250.0) { globalMultiplier *= 1.2 })
        upgrades.add(Upgrade("Coffee Boost", "Click power +$5.00", 600.0) { clickPower += 5.0 })
        upgrades.add(Upgrade("Automated Management", "Global income +50%", 5000.0) { globalMultiplier *= 1.5 })
        upgrades.add(Upgrade("Quantum Computing", "Global income x2", 50000.0) { globalMultiplier *= 2.0 })
    }

    private fun getTotalIncomePerSec(): Double {
        return businesses.sumOf { it.getIncomePerSec() } * globalMultiplier
    }

    /**
     * Background Timer Interval (Runs every 100ms)
     */
    private fun tick() {
        val incomeThisTick = getTotalIncomePerSec() * 0.1
        money += incomeThisTick
        refreshGameUI()
    }

    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(GridLayout(1, 3, 10, 0))
        panel.background = Color(30, 36, 50)
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(50, 60, 80), 1),
            EmptyBorder(15, 20, 15, 20)
        )

        // Capital
        val moneyBox = JPanel(GridLayout(2, 1))
        moneyBox.isOpaque = false
        val moneyTitle = JLabel("CURRENT CAPITAL")
        moneyTitle.foreground = Color(140, 160, 190)
        moneyTitle.font = Font("Monospaced", Font.BOLD, 12)

        moneyLabel.foreground = Color(0, 230, 140)
        moneyLabel.font = Font("Monospaced", Font.BOLD, 24)

        moneyBox.add(moneyTitle)
        moneyBox.add(moneyLabel)

        // Income / sec
        val incomeBox = JPanel(GridLayout(2, 1))
        incomeBox.isOpaque = false
        val incomeTitle = JLabel("PASSIVE INCOME")
        incomeTitle.foreground = Color(140, 160, 190)
        incomeTitle.font = Font("Monospaced", Font.BOLD, 12)

        incomeLabel.foreground = Color(0, 200, 255)
        incomeLabel.font = Font("Monospaced", Font.BOLD, 22)

        incomeBox.add(incomeTitle)
        incomeBox.add(incomeLabel)

        // Click Efficiency
        val clickBox = JPanel(GridLayout(2, 1))
        clickBox.isOpaque = false
        val clickTitle = JLabel("CLICK EFFICIENCY")
        clickTitle.foreground = Color(140, 160, 190)
        clickTitle.font = Font("Monospaced", Font.BOLD, 12)

        clickPowerLabel.foreground = Color(255, 215, 0)
        clickPowerLabel.font = Font("Monospaced", Font.BOLD, 22)

        clickBox.add(clickTitle)
        clickBox.add(clickPowerLabel)

        panel.add(moneyBox)
        panel.add(incomeBox)
        panel.add(clickBox)

        return panel
    }

    private fun createClickerPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 15))
        panel.background = Color(28, 34, 48)
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(45, 55, 75), 1),
            EmptyBorder(20, 20, 20, 20)
        )

        val title = JLabel("ASSET VAULT", SwingConstants.CENTER)
        title.foreground = Color.WHITE
        title.font = Font("Monospaced", Font.BOLD, 20)
        panel.add(title, BorderLayout.NORTH)

        // Main Mint Capital Button
        val clickBtn = JButton("<html><center><font size='7'>💎</font><br><br><b>MINT CAPITAL</b></center></html>")
        clickBtn.font = Font("Monospaced", Font.BOLD, 22)
        clickBtn.background = Color(0, 180, 130)
        clickBtn.foreground = Color.WHITE
        clickBtn.isFocusPainted = false
        clickBtn.cursor = Cursor(Cursor.HAND_CURSOR)

        clickBtn.addActionListener {
            money += clickPower
            refreshGameUI()
        }

        panel.add(clickBtn, BorderLayout.CENTER)

        val desc = JLabel("Click main asset to generate instant capital.", SwingConstants.CENTER)
        desc.foreground = Color(140, 160, 190)
        desc.font = Font("Monospaced", Font.PLAIN, 12)
        panel.add(desc, BorderLayout.SOUTH)

        return panel
    }

    private fun createShopPanel(): JPanel {
        val tabbedPane = JTabbedPane()
        tabbedPane.background = Color(28, 34, 48)
        tabbedPane.foreground = Color.WHITE
        tabbedPane.font = Font("Monospaced", Font.BOLD, 14)

        // --- Tab 1: Businesses ---
        val busContainer = JPanel()
        busContainer.layout = BoxLayout(busContainer, BoxLayout.Y_AXIS)
        busContainer.background = Color(20, 24, 34)

        for (b in businesses) {
            val bPanel = JPanel(BorderLayout(10, 0))
            bPanel.background = Color(32, 38, 54)
            bPanel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(45, 55, 75)),
                EmptyBorder(10, 10, 10, 10)
            )

            val infoBox = JPanel(GridLayout(2, 1))
            infoBox.isOpaque = false

            val nameLabel = JLabel("${b.icon} ${b.name}")
            nameLabel.foreground = Color.WHITE
            nameLabel.font = Font("Monospaced", Font.BOLD, 15)

            val incLabel = JLabel("+${formatMoney(b.baseIncomePerSec)}/s")
            incLabel.foreground = Color(0, 200, 255)
            incLabel.font = Font("Monospaced", Font.PLAIN, 12)

            infoBox.add(nameLabel)
            infoBox.add(incLabel)

            val countLabel = JLabel("x0", SwingConstants.RIGHT)
            countLabel.foreground = Color(255, 215, 0)
            countLabel.font = Font("Monospaced", Font.BOLD, 16)
            countLabel.border = EmptyBorder(0, 0, 0, 10)

            val buyBtn = JButton("Buy ${formatMoney(b.getCost())}")
            buyBtn.font = Font("Monospaced", Font.BOLD, 12)
            buyBtn.background = Color(50, 150, 220)
            buyBtn.foreground = Color.WHITE
            buyBtn.isFocusPainted = false
            buyBtn.cursor = Cursor(Cursor.HAND_CURSOR)

            buyBtn.addActionListener {
                val cost = b.getCost()
                if (money >= cost) {
                    money -= cost
                    b.count++
                    refreshGameUI()
                }
            }

            val rightBox = JPanel(BorderLayout())
            rightBox.isOpaque = false
            rightBox.add(countLabel, BorderLayout.WEST)
            rightBox.add(buyBtn, BorderLayout.EAST)

            bPanel.add(infoBox, BorderLayout.CENTER)
            bPanel.add(rightBox, BorderLayout.EAST)

            busContainer.add(bPanel)
            businessUIList.add(BusinessUI(b, countLabel, incLabel, buyBtn))
        }

        val busScroll = JScrollPane(busContainer)
        busScroll.border = null
        busScroll.verticalScrollBar.unitIncrement = 12

        // --- Tab 2: Upgrades ---
        val upContainer = JPanel()
        upContainer.layout = BoxLayout(upContainer, BoxLayout.Y_AXIS)
        upContainer.background = Color(20, 24, 34)

        for (u in upgrades) {
            val uPanel = JPanel(BorderLayout(10, 0))
            uPanel.background = Color(32, 38, 54)
            uPanel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(45, 55, 75)),
                EmptyBorder(10, 10, 10, 10)
            )

            val infoBox = JPanel(GridLayout(2, 1))
            infoBox.isOpaque = false

            val nameLabel = JLabel("⚡ ${u.name}")
            nameLabel.foreground = Color.WHITE
            nameLabel.font = Font("Monospaced", Font.BOLD, 14)

            val descLabel = JLabel(u.desc)
            descLabel.foreground = Color(140, 160, 190)
            descLabel.font = Font("Monospaced", Font.PLAIN, 11)

            infoBox.add(nameLabel)
            infoBox.add(descLabel)

            val buyBtn = JButton(formatMoney(u.cost))
            buyBtn.font = Font("Monospaced", Font.BOLD, 12)
            buyBtn.background = Color(220, 120, 40)
            buyBtn.foreground = Color.WHITE
            buyBtn.isFocusPainted = false
            buyBtn.cursor = Cursor(Cursor.HAND_CURSOR)

            buyBtn.addActionListener {
                if (!u.bought && money >= u.cost) {
                    money -= u.cost
                    u.bought = true
                    u.effect()
                    refreshGameUI()
                }
            }

            uPanel.add(infoBox, BorderLayout.CENTER)
            uPanel.add(buyBtn, BorderLayout.EAST)

            upContainer.add(uPanel)
            upgradeUIList.add(UpgradeUI(u, buyBtn))
        }

        val upScroll = JScrollPane(upContainer)
        upScroll.border = null
        upScroll.verticalScrollBar.unitIncrement = 12

        tabbedPane.addTab("NODES", busScroll)
        tabbedPane.addTab("UPGRADES", upScroll)

        val outerPanel = JPanel(BorderLayout())
        outerPanel.add(tabbedPane, BorderLayout.CENTER)
        return outerPanel
    }

    /**
     * Renamed to avoid overriding Swing's internal JComponent.updateUI() method.
     */
    private fun refreshGameUI() {
        moneyLabel.text = formatMoney(money)
        incomeLabel.text = "+${formatMoney(getTotalIncomePerSec())}/s"
        clickPowerLabel.text = formatMoney(clickPower)

        // Update Businesses UI
        for (bUI in businessUIList) {
            val b = bUI.business
            val cost = b.getCost()

            bUI.countLabel.text = "x${b.count}"
            bUI.incomeLabel.text = "+${formatMoney(b.getIncomePerSec() * globalMultiplier)}/s"
            bUI.buyBtn.text = "Buy ${formatMoney(cost)}"

            if (money >= cost) {
                bUI.buyBtn.isEnabled = true
                bUI.buyBtn.background = Color(0, 160, 220)
            } else {
                bUI.buyBtn.isEnabled = false
                bUI.buyBtn.background = Color(60, 70, 85)
            }
        }

        // Update Upgrades UI
        for (uUI in upgradeUIList) {
            val u = uUI.upgrade
            if (u.bought) {
                uUI.button.text = "OWNED"
                uUI.button.isEnabled = false
                uUI.button.background = Color(40, 140, 70)
            } else if (money >= u.cost) {
                uUI.button.isEnabled = true
                uUI.button.background = Color(230, 130, 30)
            } else {
                uUI.button.isEnabled = false
                uUI.button.background = Color(60, 70, 85)
            }
        }
    }
}