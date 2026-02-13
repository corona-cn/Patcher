@file:Suppress("Unused", "Nothing_to_inline")
package patcher.core.ui
import patcher.core.utils.*

import kotlinx.coroutines.*
import kotlin.concurrent.*

import java.awt.datatransfer.*
import java.lang.foreign.*
import java.awt.event.*
import java.awt.*

import javax.swing.border.*
import javax.swing.*

class PatcherUI : JFrame("Patcher - Memory Patch Tool") {
    private val processCombo = JComboBox<String>().apply {
        preferredSize = Dimension(300, 28)
        isEditable = false
        toolTipText = "选择目标进程"
    }

    private val refreshButton = JButton("刷新").apply {
        preferredSize = Dimension(90, 28)
        toolTipText = "刷新进程列表"
    }

    private val attachButton = JButton("附加").apply {
        preferredSize = Dimension(90, 28)
        isEnabled = false
        toolTipText = "附加到选中的进程"
    }

    private val processStatusLabel = JLabel("未附加").apply {
        foreground = Color.GRAY
        font = font.deriveFont(Font.PLAIN, 12f)
    }

    private val pidLabel = JLabel("PID: -").apply {
        foreground = Color.GRAY
        font = font.deriveFont(Font.PLAIN, 11f)
    }

    private val addressField = JTextField().apply {
        preferredSize = Dimension(250, 28)
        toolTipText = "十六进制地址，例如：0x7FF6A3B5C3C0 或 7FF6A3B5C3C0"
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    private val valueField = JTextField().apply {
        preferredSize = Dimension(250, 28)
        toolTipText = "十六进制字节，例如：90 90 EB 1F 或 9090EB1F"
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    private val sizeSpinner = JSpinner(SpinnerNumberModel(16, 1, 1024, 1)).apply {
        preferredSize = Dimension(80, 28)
        toolTipText = "读取字节数"
    }

    private val readButton = JButton("读取").apply {
        preferredSize = Dimension(100, 32)
        isEnabled = false
    }

    private val writeButton = JButton("写入").apply {
        preferredSize = Dimension(100, 32)
        isEnabled = false
        background = Color(70, 130, 180)
        foreground = Color.WHITE
    }

    private val resultArea = JTextArea().apply {
        isEditable = false
        font = Font("Monospaced", Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
        background = Color(245, 245, 245)
        rows = 5
    }

    private val resultScrollPane = JScrollPane(resultArea).apply {
        preferredSize = Dimension(700, 120)
        border = BorderFactory.createTitledBorder("读取结果")
    }

    private val copyButton = JButton("📋 复制").apply {
        preferredSize = Dimension(80, 28)
        isEnabled = false
        toolTipText = "复制读取结果"
    }

    private val statusBar = JLabel("就绪").apply {
        foreground = Color.DARK_GRAY
        border = EmptyBorder(5, 10, 5, 10)
        font = font.deriveFont(Font.PLAIN, 12f)
    }

    private var currentProcess: Process? = null

    private var currentHandle: MemorySegment? = null

    private var currentPatcher: MemoryPatcher? = null

    private var lastReadData: ByteArray = byteArrayOf()

    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        initUI()
        initEvents()
        initProcessList()
    }

    private fun initUI() {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(800, 550)
        setLocationRelativeTo(null)
        layout = BorderLayout()

        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(15, 15, 15, 15)
            background = Color.WHITE
        }

        mainPanel.add(createProcessPanel())
        mainPanel.add(Box.createVerticalStrut(15))

        mainPanel.add(createMemoryPanel())
        mainPanel.add(Box.createVerticalStrut(15))

        mainPanel.add(resultScrollPane)

        add(mainPanel, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)

        updateButtonStates()
    }

    private fun createProcessPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            background = Color.WHITE
            border = BorderFactory.createTitledBorder("目标进程")

            val c = GridBagConstraints()
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(5, 5, 5, 5)

            c.gridx = 0; c.gridy = 0; c.weightx = 0.0
            add(JLabel("进程:"), c)

            c.gridx = 1; c.weightx = 1.0
            add(processCombo, c)

            c.gridx = 2; c.weightx = 0.0
            add(refreshButton, c)

            c.gridx = 3; c.weightx = 0.0
            add(attachButton, c)

            c.gridx = 0; c.gridy = 1; c.gridwidth = 1
            add(JLabel("状态:"), c)

            c.gridx = 1; c.gridwidth = 2
            add(processStatusLabel, c)

            c.gridx = 3
            add(pidLabel, c)
        }
    }

    private fun createMemoryPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            background = Color.WHITE
            border = BorderFactory.createTitledBorder("内存操作")

            val c = GridBagConstraints()
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(5, 5, 5, 5)

            c.gridx = 0; c.gridy = 0; c.weightx = 0.0
            add(JLabel("地址:"), c)

            c.gridx = 1; c.weightx = 1.0; c.gridwidth = 3
            add(addressField, c)

            c.gridx = 0; c.gridy = 1; c.gridwidth = 1
            add(JLabel("写入:"), c)

            c.gridx = 1; c.gridwidth = 3
            add(valueField, c)

            c.gridx = 0; c.gridy = 2
            add(JLabel("读取:"), c)

            c.gridx = 1; c.gridwidth = 1; c.weightx = 0.0
            add(JLabel("长度"), c)

            c.gridx = 2; c.weightx = 0.0
            add(sizeSpinner, c)

            c.gridx = 3; c.weightx = 0.0
            add(JLabel("字节"), c)

            c.gridx = 0; c.gridy = 3; c.gridwidth = 4
            c.insets = Insets(15, 5, 5, 5)

            val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 0)).apply {
                background = Color.WHITE
                add(readButton)
                add(writeButton)
                add(copyButton)
            }
            add(buttonPanel, c)
        }
    }

    private fun initEvents() {
        refreshButton.addActionListener {
            refreshProcessList()
        }

        attachButton.addActionListener {
            attachToProcess()
        }

        readButton.addActionListener {
            readMemory()
        }

        writeButton.addActionListener {
            writeMemory()
        }

        copyButton.addActionListener {
            copyResultToClipboard()
        }

        processCombo.addActionListener {
            attachButton.isEnabled = processCombo.selectedItem != null
        }

        addressField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && readButton.isEnabled) {
                    readMemory()
                }
            }
        })

        valueField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && writeButton.isEnabled) {
                    writeMemory()
                }
            }
        })
    }

    private fun refreshProcessList() {
        uiScope.launch {
            statusBar.text = "正在扫描进程..."
            refreshButton.isEnabled = false
            processCombo.isEnabled = false

            val processes = withContext(Dispatchers.IO) {
                try {
                    Process.getAllProcesses()
                        .map { it.getName() }
                        .filter { it.isNotBlank() && it.endsWith(".exe", ignoreCase = true) }
                        .distinct()
                        .sorted()
                } catch (e: Exception) {
                    emptyList<String>()
                }
            }

            processCombo.removeAllItems()
            processes.forEach { processCombo.addItem(it) }

            statusBar.text = "已加载 ${processes.size} 个进程"
            refreshButton.isEnabled = true
            processCombo.isEnabled = true
            attachButton.isEnabled = processCombo.selectedItem != null
        }
    }

    private fun initProcessList() {
        uiScope.launch {
            refreshProcessList()
        }
    }

    private fun attachToProcess() {
        val processName = processCombo.selectedItem as? String ?: return

        uiScope.launch {
            statusBar.text = "正在附加到 $processName..."
            attachButton.isEnabled = false

            val result = withContext(Dispatchers.IO) {
                try {
                    val pid = Process.getAllProcesses()
                        .find { it.getName().equals(processName, ignoreCase = true) }
                        ?.getPid()

                    if (pid == null) {
                        return@withContext "❌ 未找到进程: $processName"
                    }

                    val handle = Process.openHandle(pid) ?: return@withContext "❌ 无法打开进程，请以管理员身份运行"
                    val patcher = MemoryPatcher.fromHandle(handle)

                    currentProcess = Process.getAllProcesses().find { it.getPid() == pid }
                    currentHandle = handle
                    currentPatcher = patcher

                    "✅ 已附加到 $processName (PID: $pid)"
                } catch (e: Exception) {
                    "❌ 附加失败: ${e.message}"
                }
            }

            statusBar.text = result
            attachButton.isEnabled = true

            if (result.startsWith("✅")) {
                processStatusLabel.text = "已附加"
                processStatusLabel.foreground = Color(0, 128, 0)
                pidLabel.text = "PID: ${currentProcess?.getPid() ?: "-"}"
                updateButtonStates(true)
            } else {
                detachProcess()
            }
        }
    }

    private suspend fun detachProcess() {
        withContext(Dispatchers.IO) {
            currentPatcher?.close()
        }

        currentProcess = null
        currentHandle = null
        currentPatcher = null

        processStatusLabel.text = "未附加"
        processStatusLabel.foreground = Color.GRAY
        pidLabel.text = "PID: -"

        updateButtonStates(false)
    }

    private fun readMemory() {
        val patcher = currentPatcher ?: run {
            statusBar.text = "❌ 请先附加进程"
            return
        }

        val addressText = addressField.text.trim()
        if (addressText.isEmpty()) {
            statusBar.text = "❌ 地址不能为空"
            return
        }

        val address = addressText.removePrefix("0x").toLongOrNull(16)
        if (address == null) {
            statusBar.text = "❌ 地址格式错误，应为十六进制"
            return
        }

        val size = sizeSpinner.value as Int

        uiScope.launch {
            statusBar.text = "正在读取内存..."
            readButton.isEnabled = false

            val result = withContext(Dispatchers.IO) {
                try {
                    val data = patcher.read(address, size)
                    if (data.isEmpty()) {
                        "❌ 读取失败（地址不可读或权限不足）"
                    } else {
                        lastReadData = data

                        val hex = data.joinToString(" ") { "%02X".format(it) }
                        val ascii = data.map {
                            if (it in 32..126) it.toInt().toChar() else '.'
                        }.joinToString("")

                        """
                        地址: 0x${address.toString(16).uppercase()}
                        长度: ${data.size} 字节
                        十六进制: $hex
                        ASCII: $ascii
                        """.trimIndent()
                    }
                } catch (e: Exception) {
                    "❌ 读取异常: ${e.message}"
                }
            }

            resultArea.text = result
            copyButton.isEnabled = !lastReadData.isEmpty()
            statusBar.text = if (result.startsWith("✅") || result.startsWith("地址")) "✅ 读取成功" else result
            readButton.isEnabled = true
        }
    }

    private fun writeMemory() {
        val patcher = currentPatcher ?: run {
            statusBar.text = "❌ 请先附加进程"
            return
        }

        val addressText = addressField.text.trim()
        if (addressText.isEmpty()) {
            statusBar.text = "❌ 地址不能为空"
            return
        }

        val valueText = valueField.text.trim()
        if (valueText.isEmpty()) {
            statusBar.text = "❌ 写入值不能为空"
            return
        }

        val address = addressText.removePrefix("0x").toLongOrNull(16)
        if (address == null) {
            statusBar.text = "❌ 地址格式错误，应为十六进制"
            return
        }

        uiScope.launch {
            statusBar.text = "正在写入内存..."
            writeButton.isEnabled = false

            val result = withContext(Dispatchers.IO) {
                try {
                    val success = patcher.writeHex(address, valueText)
                    if (success) {
                        "✅ 写入成功 (${valueText.replace(" ", "").length / 2} 字节)"
                    } else {
                        "❌ 写入失败（地址不可写或权限不足）"
                    }
                } catch (e: Exception) {
                    "❌ 写入异常: ${e.message}"
                }
            }

            statusBar.text = result
            writeButton.isEnabled = true

            if (result.startsWith("✅")) {
                readMemory()
            }
        }
    }

    private fun copyResultToClipboard() {
        val text = resultArea.text
        if (text.isNotBlank()) {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            statusBar.text = "✅ 已复制到剪贴板"
        }
    }

    private fun updateButtonStates(attached: Boolean = currentProcess != null) {
        readButton.isEnabled = attached
        writeButton.isEnabled = attached
        copyButton.isEnabled = attached && !lastReadData.isEmpty()
    }

    override fun dispose() {
        uiScope.coroutineContext.cancelChildren()

        thread(name = "Patcher-Shutdown") {
            currentPatcher?.close()
            currentHandle = null
            currentProcess = null
        }

        super.dispose()
    }
}