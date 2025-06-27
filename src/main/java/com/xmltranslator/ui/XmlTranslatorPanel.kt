package com.xmltranslator.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.xmltranslator.services.TranslationService
import java.util.concurrent.Executors
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.Timer
import javax.swing.text.Position

class XmlTranslatorPanel(private val project: Project) : JPanel() {
    
    private val translationService = project.service<TranslationService>()
    private val executorService = Executors.newSingleThreadExecutor()
    
    // Hover state for language list
    private var hoveredIndex = -1
    
    // File Translation Tab Components
    private val inputFileField = JBTextField()
    private val outputDirField = JBTextField()
    private val languageListModel = DefaultListModel<String>()
    private val languageList = JBList(languageListModel)
    private val languageInput = JBTextField()
    private val fileStatusArea = JBTextArea()
    
    // String Addition Tab Components - Updated for better layout
    private val resourceDirField = JBTextField()
    private val valuesListModel = DefaultListModel<String>()
    private val valuesList = JBList(valuesListModel)
    private val stringStatusArea = JBTextArea()
    private val stringTableModel = DefaultTableModel(arrayOf("Name", "Text"), 0)
    private val stringTable = JBTable(stringTableModel)
    private val bulkTextArea = JBTextArea(6, 50) // For bulk XML input
    
    // Common languages for quick selection
    private val commonLanguages = mapOf(
        "Vietnamese" to "vi",
        "Chinese" to "zh", 
        "Korean" to "ko",
        "Japanese" to "ja",
        "Italian" to "it",
        "French" to "fr",
        "German" to "de",
        "Spanish" to "es",
        "Arabic" to "ar",
        "Bengali" to "bn",
        "Greek" to "el",
        "Hindi" to "hi",
        "Indonesian" to "in",
        "Marathi" to "mr",
        "Malay" to "ms",
        "Portuguese" to "pt",
        "Portuguese (Brazil)" to "pt-rBR",
        "Russian" to "ru",
        "Tamil" to "ta",
        "Telugu" to "te",
        "Thai" to "th",
        "Turkish" to "tr",
        "Filipino" to "tl"
    )
    
    init {
        setupUI()
        autoDetectResourceDirectory()
    }
    
    private fun setupUI() {
        layout = BorderLayout()
        
        val tabbedPane = JTabbedPane()
        
        // File Translation Tab
        val filePanel = createFileTranslationPanel()
        tabbedPane.addTab("Translate XML File", filePanel)
        
        // String Addition Tab - Now scrollable
        val stringPanel = createScrollableStringAdditionPanel()
        tabbedPane.addTab("Add New String", stringPanel)
        
        add(tabbedPane, BorderLayout.CENTER)
    }
    
    private fun createFileTranslationPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // Input section
        val inputPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        // Input file
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
        inputPanel.add(JLabel("Input XML file:"), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        inputPanel.add(inputFileField, gbc)
        
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        val browseInputButton = JButton("Browse")
        browseInputButton.addActionListener { browseInputFile() }
        inputPanel.add(browseInputButton, gbc)
        
        // Output directory
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST
        inputPanel.add(JLabel("Output directory:"), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        inputPanel.add(outputDirField, gbc)
        
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        val browseOutputButton = JButton("Browse")
        browseOutputButton.addActionListener { browseOutputDir() }
        inputPanel.add(browseOutputButton, gbc)
        
        panel.add(inputPanel, BorderLayout.NORTH)
        
        // Language selection
        val langPanel = JPanel(BorderLayout())
        langPanel.border = BorderFactory.createTitledBorder("Target Languages")
        
        val langInputPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        langInputPanel.add(JLabel("Language code:"))
        langInputPanel.add(languageInput)
        
        val addLangButton = JButton("Add")
        addLangButton.addActionListener { addLanguage() }
        langInputPanel.add(addLangButton)
        
        val removeLangButton = JButton("Remove")
        removeLangButton.addActionListener { removeLanguage() }
        langInputPanel.add(removeLangButton)
        
        langPanel.add(langInputPanel, BorderLayout.NORTH)
        langPanel.add(JScrollPane(languageList), BorderLayout.CENTER)
        
        // Common languages buttons
        val commonLangPanel = JPanel(GridLayout(6, 4, 5, 5))
        commonLanguages.forEach { (name, code) ->
            val button = JButton(name)
            button.addActionListener { addLanguageCode(code) }
            commonLangPanel.add(button)
        }
        
        langPanel.add(JScrollPane(commonLangPanel), BorderLayout.SOUTH)
        
        panel.add(langPanel, BorderLayout.CENTER)
        
        // Status and translate button
        val bottomPanel = JPanel(BorderLayout())
        
        fileStatusArea.isEditable = false
        fileStatusArea.rows = 6
        bottomPanel.add(JScrollPane(fileStatusArea), BorderLayout.CENTER)
        
        val translateButton = JButton("Translate All")
        translateButton.addActionListener { translateFile() }
        bottomPanel.add(translateButton, BorderLayout.SOUTH)
        
        panel.add(bottomPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createScrollableStringAdditionPanel(): JScrollPane {
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        
        // String input section
        val stringsFrame = createStringInputSection()
        mainPanel.add(stringsFrame)
        
        // Add some spacing
        mainPanel.add(Box.createRigidArea(Dimension(0, 10)))
        
        // Resource directory section
        val dirFrame = createResourceDirectorySection()
        mainPanel.add(dirFrame)
        
        // Add some spacing
        mainPanel.add(Box.createRigidArea(Dimension(0, 10)))
        
        // Status and action section
        val statusFrame = createStatusSection()
        mainPanel.add(statusFrame)
        
        // Create scrollable panel
        val scrollPane = JScrollPane(mainPanel)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.preferredSize = Dimension(800, 600)
        
        return scrollPane
    }

    private fun createStringInputSection(): JPanel {
        val stringsFrame = JPanel(BorderLayout())
        stringsFrame.border = BorderFactory.createTitledBorder("📝 Strings to Translate")
        
        // Bulk input section - Enhanced design
        val bulkPanel = JPanel(BorderLayout())
        bulkPanel.border = BorderFactory.createTitledBorder("📋 XML String Input")
        
        // Instructions panel
        val instructionsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val instructionsLabel = JBLabel("<html><i>Paste your XML strings below, then click Parse to add them to the translation list</i></html>")
        instructionsPanel.add(instructionsLabel)
        bulkPanel.add(instructionsPanel, BorderLayout.NORTH)
        
        // Text area with better formatting
        bulkTextArea.lineWrap = true
        bulkTextArea.wrapStyleWord = true
        bulkTextArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        bulkTextArea.text = """"""
        
        val bulkScrollPane = JScrollPane(bulkTextArea)
        bulkScrollPane.preferredSize = Dimension(700, 140)
        bulkScrollPane.border = BorderFactory.createLoweredBevelBorder()
        bulkPanel.add(bulkScrollPane, BorderLayout.CENTER)
        
        // Parse button with better styling
        val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        val parseButton = JButton("🔄 Parse XML Strings")
        parseButton.preferredSize = Dimension(150, 30)
        parseButton.addActionListener { parseBulkInput() }
        buttonPanel.add(parseButton)
        
        val clearInputButton = JButton("🗑️ Clear Input")
        clearInputButton.preferredSize = Dimension(120, 30)
        clearInputButton.addActionListener { 
            bulkTextArea.text = ""
            stringStatusArea.append("Input cleared\n")
        }
        buttonPanel.add(clearInputButton)
        
        bulkPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        stringsFrame.add(bulkPanel, BorderLayout.CENTER)
        
        // String list table with enhanced design
        val tablePanel = JPanel(BorderLayout())
        tablePanel.border = BorderFactory.createTitledBorder("📊 Translation Queue")
        
        stringTable.fillsViewportHeight = true
        stringTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        stringTable.rowHeight = 25
        stringTable.gridColor = java.awt.Color.LIGHT_GRAY
        stringTable.setShowGrid(true)
        
        // Set column widths
        stringTable.columnModel.getColumn(0).preferredWidth = 250 // Name
        stringTable.columnModel.getColumn(1).preferredWidth = 450 // Text
        
        val tableScrollPane = JScrollPane(stringTable)
        tableScrollPane.preferredSize = Dimension(700, 180)
        tableScrollPane.border = BorderFactory.createLoweredBevelBorder()
        tablePanel.add(tableScrollPane, BorderLayout.CENTER)
        
        // Table action buttons with icons
        val tableButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        
        val removeButton = JButton("❌ Remove Selected")
        removeButton.preferredSize = Dimension(140, 30)
        removeButton.addActionListener { removeSelectedString() }
        tableButtonPanel.add(removeButton)
        
        val clearButton = JButton("🗑️ Clear All")
        clearButton.preferredSize = Dimension(100, 30)
        clearButton.addActionListener { clearAllStrings() }
        tableButtonPanel.add(clearButton)
        
        // Add quick add single string button
        val quickAddButton = JButton("➕ Quick Add")
        quickAddButton.preferredSize = Dimension(110, 30)
        quickAddButton.addActionListener { showQuickAddDialog() }
        tableButtonPanel.add(quickAddButton)
        
        tablePanel.add(tableButtonPanel, BorderLayout.SOUTH)
        
        stringsFrame.add(tablePanel, BorderLayout.SOUTH)
        
        return stringsFrame
    }
    
    private fun createResourceDirectorySection(): JPanel {
        val dirFrame = JPanel(BorderLayout())
        dirFrame.border = BorderFactory.createTitledBorder("📁 Output Directory")
        
        // Directory selection with enhanced styling
        val dirInputPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
        gbc.insets = Insets(5, 5, 5, 5)
        dirInputPanel.add(JLabel("📂 Resource directory:"), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        resourceDirField.preferredSize = Dimension(450, 28)
        resourceDirField.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11)
        dirInputPanel.add(resourceDirField, gbc)
        
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        val browseResourceButton = JButton("📁 Browse")
        browseResourceButton.preferredSize = Dimension(90, 28)
        browseResourceButton.addActionListener { browseResourceDir() }
        dirInputPanel.add(browseResourceButton, gbc)
        
        gbc.gridx = 3
        val autoDetectButton = JButton("🔍 Auto Detect")
        autoDetectButton.preferredSize = Dimension(110, 28)
        autoDetectButton.addActionListener { autoDetectResourceDirectory() }
        dirInputPanel.add(autoDetectButton, gbc)
        
        dirFrame.add(dirInputPanel, BorderLayout.NORTH)
        
        // Values folders selection with enhanced styling
        val valuesPanel = JPanel(BorderLayout())
        val valuesLabel = JLabel("🌍 Target language folders:")
        valuesLabel.border = BorderFactory.createEmptyBorder(10, 5, 5, 5)
        valuesPanel.add(valuesLabel, BorderLayout.NORTH)
        
        valuesList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        valuesList.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        val valuesScrollPane = JScrollPane(valuesList)
        valuesScrollPane.preferredSize = Dimension(700, 100)
        valuesScrollPane.border = BorderFactory.createLoweredBevelBorder()
        valuesPanel.add(valuesScrollPane, BorderLayout.CENTER)
        
        // Enhanced values list with hover delete functionality
        setupLanguageListHover()
        
        // Simple refresh button only
        val langButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        langButtonPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        val refreshButton = JButton("🔄 Refresh")
        refreshButton.preferredSize = Dimension(90, 28)
        refreshButton.toolTipText = "Re-scan directory for language folders"
        refreshButton.addActionListener { 
            val dirPath = resourceDirField.text.trim()
            if (dirPath.isNotEmpty()) {
                updateValuesFolders(dirPath)
            }
        }
        langButtonPanel.add(refreshButton)
        
        valuesPanel.add(langButtonPanel, BorderLayout.SOUTH)
        
        dirFrame.add(valuesPanel, BorderLayout.CENTER)
        
        return dirFrame
    }
    
    private fun createStatusSection(): JPanel {
        val statusFrame = JPanel(BorderLayout())
        statusFrame.border = BorderFactory.createTitledBorder("🚀 Translation Status & Actions")
        
        // Status area with proper IDE-themed styling
        stringStatusArea.isEditable = false
        stringStatusArea.rows = 8
        stringStatusArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        // Use system default colors for better consistency
        stringStatusArea.background = UIManager.getColor("TextArea.background")
        stringStatusArea.foreground = UIManager.getColor("TextArea.foreground")
        stringStatusArea.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        
        val statusScrollPane = JScrollPane(stringStatusArea)
        statusScrollPane.preferredSize = Dimension(700, 150)
        statusScrollPane.border = BorderFactory.createLoweredBevelBorder()
        statusFrame.add(statusScrollPane, BorderLayout.CENTER)
        
        // Action buttons panel with enhanced styling
        val actionPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        actionPanel.border = BorderFactory.createEmptyBorder(10, 0, 5, 0)
        
        val translateButton = JButton("🌍 Translate All Strings")
        translateButton.preferredSize = Dimension(180, 35)
        translateButton.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 12)
        // Use more subtle IDE-appropriate colors
        translateButton.background = java.awt.Color(59, 142, 234) // IntelliJ blue
        translateButton.foreground = java.awt.Color.WHITE
        translateButton.isFocusPainted = false
        translateButton.addActionListener { translateStrings() }
        actionPanel.add(translateButton)
        
        val clearStatusButton = JButton("🗑️ Clear Status")
        clearStatusButton.preferredSize = Dimension(120, 35)
        clearStatusButton.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11)
        clearStatusButton.isFocusPainted = false
        clearStatusButton.addActionListener { 
            stringStatusArea.text = ""
            stringStatusArea.append("Status cleared ✨\n")
        }
        actionPanel.add(clearStatusButton)
        
        statusFrame.add(actionPanel, BorderLayout.SOUTH)
        
        return statusFrame
    }
    
    private fun autoDetectResourceDirectory() {
        try {
            val projectPath = project.basePath
            if (projectPath != null) {
                // Try common Android project structures
                val possiblePaths = listOf(
                    "$projectPath/app/src/main/res",
                    "$projectPath/src/main/res",
                    "$projectPath/main/res",
                    "$projectPath/res"
                )
                
                for (path in possiblePaths) {
                    val dir = File(path)
                    if (dir.exists() && dir.isDirectory) {
                        // Check if it contains values folder or values-* folders
                        val hasValuesFolder = dir.listFiles()?.any { 
                            it.isDirectory && (it.name == "values" || it.name.startsWith("values-"))
                        } ?: false
                        
                        if (hasValuesFolder) {
                            resourceDirField.text = path
                            updateValuesFolders(path)
                            stringStatusArea.append("Auto-detected resource directory: $path\n")
                            return
                        }
                    }
                }
                
                // If no res folder found, use project root
                resourceDirField.text = projectPath
                stringStatusArea.append("Using project root directory: $projectPath\n")
            }
        } catch (e: Exception) {
            stringStatusArea.append("Auto-detection failed: ${e.message}\n")
        }
    }
    
    private fun parseBulkInput() {
        val bulkText = bulkTextArea.text.trim()
        if (bulkText.isEmpty()) {
            Messages.showWarningDialog(project, "Please enter XML strings to parse!", "Warning")
            return
        }
        
        try {
            // Simple regex to match string elements
            val pattern = """<string\s+name="([^"]+)"[^>]*>(.*?)</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val matches = pattern.findAll(bulkText)
            
            var count = 0
            for (match in matches) {
                val name = match.groupValues[1]
                val text = match.groupValues[2].trim()
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                
                if (name.isNotEmpty() && text.isNotEmpty()) {
                    stringTableModel.addRow(arrayOf(name, text))
                    count++
                }
            }
            
            if (count > 0) {
                stringStatusArea.append("Successfully parsed $count strings from XML\n")
                bulkTextArea.text = "" // Clear after successful parse
            } else {
                Messages.showWarningDialog(project, "No valid string elements found!", "Warning")
            }
            
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Error parsing XML strings: ${e.message}", "Error")
        }
    }
    
    private fun browseInputFile() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
        descriptor.withFileFilter { it.extension == "xml" }
        descriptor.title = "Select XML File"
        
        val file = FileChooser.chooseFile(descriptor, project, null)
        file?.let {
            inputFileField.text = it.path
        }
    }
    
    private fun browseOutputDir() {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        descriptor.title = "Select Output Directory"
        
        val file = FileChooser.chooseFile(descriptor, project, null)
        file?.let {
            outputDirField.text = it.path
        }
    }
    
    private fun browseResourceDir() {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        descriptor.title = "Select Resource Directory"
        
        val file = FileChooser.chooseFile(descriptor, project, null)
        file?.let {
            resourceDirField.text = it.path
            updateValuesFolders(it.path)
        }
    }
    
    private fun updateValuesFolders(dirPath: String) {
        valuesListModel.clear()
        
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory) {
            val valuesDirs = dir.listFiles { file ->
                file.isDirectory && (file.name == "values" || file.name.startsWith("values-"))
            }?.map { it.name }?.sorted() ?: emptyList()
            
            // Ensure 'values' comes first
            val sortedDirs = valuesDirs.sortedWith { a, b ->
                when {
                    a == "values" -> -1
                    b == "values" -> 1
                    else -> a.compareTo(b)
                }
            }
            
            sortedDirs.forEach { valuesListModel.addElement(it) }
            
            // Select all by default
            if (valuesListModel.size() > 0) {
                valuesList.selectionModel.setSelectionInterval(0, valuesListModel.size() - 1)
            }
            
            // Update status with enhanced feedback
            if (sortedDirs.isNotEmpty()) {
                stringStatusArea.append("🔍 Found ${sortedDirs.size} language folders: ${sortedDirs.joinToString(", ")} ✅\n")
            } else {
                stringStatusArea.append("⚠️  No language folders found in: $dirPath\n")
                stringStatusArea.append("💡 Create language folders (e.g., values-vi, values-zh) to enable translation.\n")
            }
        } else {
            stringStatusArea.append("❌ Directory does not exist: $dirPath\n")
            stringStatusArea.append("💡 Please select a valid resource directory.\n")
        }
    }
    
    private fun addLanguage() {
        val code = languageInput.text.trim()
        if (code.isNotEmpty() && !languageListModel.contains(code)) {
            languageListModel.addElement(code)
            languageInput.text = ""
        }
    }
    
    private fun addLanguageCode(code: String) {
        if (!languageListModel.contains(code)) {
            languageListModel.addElement(code)
        }
    }
    
    private fun removeLanguage() {
        val selected = languageList.selectedValuesList
        selected.forEach { languageListModel.removeElement(it) }
    }
    
    private fun removeSelectedString() {
        val selected = stringTable.selectedRows
        if (selected.isNotEmpty()) {
            for (i in selected.indices.reversed()) {
                val stringName = stringTableModel.getValueAt(selected[i], 0) as String
                stringTableModel.removeRow(selected[i])
                stringStatusArea.append("Removed string: '$stringName' ❌\n")
            }
        } else {
            Messages.showInfoMessage(project, "Please select strings to remove!", "No Selection")
        }
    }
    
    private fun setupLanguageListHover() {
        valuesList.setCellRenderer { list, value, index, isSelected, _ ->
            val panel = JPanel()
            panel.layout = null
            panel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            
            if (isSelected) {
                panel.background = list.selectionBackground
            } else {
                panel.background = list.background
            }
            
            val label = JLabel(value as String)
            label.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
            if (isSelected) {
                label.foreground = list.selectionForeground
            } else {
                label.foreground = list.foreground
            }
            
            val metrics = label.getFontMetrics(label.font)
            val textWidth = metrics.stringWidth(value as String)
            val textHeight = metrics.height
            
            label.setBounds(8, 4, textWidth + 10, textHeight)
            panel.add(label)
            
            if (index == hoveredIndex) {
                val deleteLabel = JLabel("×")
                deleteLabel.toolTipText = "Xóa folder ngôn ngữ này"
                deleteLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                deleteLabel.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 12)
                deleteLabel.foreground = java.awt.Color.RED
                deleteLabel.isOpaque = false
                deleteLabel.horizontalAlignment = JLabel.CENTER
                deleteLabel.verticalAlignment = JLabel.CENTER
                
                val deleteSize = 14
                deleteLabel.setBounds(textWidth + 2, 0, deleteSize, deleteSize)
                panel.add(deleteLabel)
            }
            
            panel.preferredSize = Dimension(textWidth + 25, textHeight + 8)
            panel
        }
        
        // Mouse motion listener for hover effect
        valuesList.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = valuesList.locationToIndex(e.point)
                if (index != hoveredIndex) {
                    hoveredIndex = index
                    valuesList.repaint()
                }
            }
        })
        
        // Mouse listener for clicks
        valuesList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = valuesList.locationToIndex(e.point)
                if (index >= 0 && index == hoveredIndex) {
                    val cellBounds = valuesList.getCellBounds(index, index)
                    if (cellBounds != null) {
                        val folderName = valuesListModel.getElementAt(index)
                        val metrics = valuesList.getFontMetrics(valuesList.font)
                        val textWidth = metrics.stringWidth(folderName)
                        
                        val deleteButtonX = cellBounds.x + 8 + textWidth + 2
                        val deleteButtonY = cellBounds.y
                        val deleteButtonSize = 14
                        
                        if (e.x >= deleteButtonX && e.x <= deleteButtonX + deleteButtonSize &&
                            e.y >= deleteButtonY && e.y <= deleteButtonY + deleteButtonSize) {
                            removeLanguageFolder(index)
                        }
                    }
                }
            }
            
            override fun mouseExited(e: MouseEvent) {
                hoveredIndex = -1
                valuesList.repaint()
            }
        })
    }
    
    private fun removeLanguageFolder(index: Int) {
        if (index >= 0 && index < valuesListModel.size()) {
            val folderName = valuesListModel.getElementAt(index)
            valuesListModel.removeElementAt(index)
            stringStatusArea.append("Removed language folder: '$folderName' ❌\n")
            
            // Update hover index if needed
            if (hoveredIndex >= valuesListModel.size()) {
                hoveredIndex = -1
            }
            valuesList.repaint()
        }
    }
    
    private fun clearAllStrings() {
        stringTableModel.rowCount = 0
        stringStatusArea.append("All strings cleared from queue ✨\n")
    }
    
    private fun showQuickAddDialog() {
        val dialog = JDialog()
        dialog.title = "➕ Quick Add String"
        dialog.modalityType = Dialog.ModalityType.APPLICATION_MODAL
        dialog.layout = BorderLayout()
        
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(10, 10, 10, 10)
        
        // String name
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
        panel.add(JLabel("String name:"), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        val nameField = JBTextField()
        nameField.preferredSize = Dimension(300, 25)
        panel.add(nameField, gbc)
        
        // String text
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.NORTHWEST
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("Text:"), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0
        val textArea = JBTextArea(4, 30)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        val scrollPane = JScrollPane(textArea)
        panel.add(scrollPane, gbc)
        
        dialog.add(panel, BorderLayout.CENTER)
        
        // Buttons
        val buttonPanel = JPanel(FlowLayout())
        val addButton = JButton("✅ Add")
        addButton.addActionListener {
            val name = nameField.text.trim()
            val text = textArea.text.trim()
            
            if (name.isNotEmpty() && text.isNotEmpty()) {
                stringTableModel.addRow(arrayOf(name, text))
                stringStatusArea.append("Added string: '$name' ✅\n")
                dialog.dispose()
            } else {
                Messages.showWarningDialog("Please enter both string name and text!", "Warning")
            }
        }
        buttonPanel.add(addButton)
        
        val cancelButton = JButton("❌ Cancel")
        cancelButton.addActionListener { dialog.dispose() }
        buttonPanel.add(cancelButton)
        
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }
    
    private fun translateFile() {
        val inputPath = inputFileField.text.trim()
        val outputPath = outputDirField.text.trim()
        val languages = (0 until languageListModel.size()).map { languageListModel.getElementAt(it) }
        
        if (inputPath.isEmpty() || outputPath.isEmpty() || languages.isEmpty()) {
            Messages.showWarningDialog(project, "Please fill in all fields and add languages!", "Warning")
            return
        }
        
        val inputFile = File(inputPath)
        val outputDir = File(outputPath)
        
        if (!inputFile.exists()) {
            Messages.showErrorDialog(project, "Input file does not exist!", "Error")
            return
        }
        
        fileStatusArea.text = ""
        
        executorService.submit {
            try {
                SwingUtilities.invokeLater {
                    fileStatusArea.append("Starting translation using Google Generative AI...\n")
                }
                
                // Use the real translation service
                translationService.translateXmlFile(
                    inputFile = inputFile,
                    outputDir = outputDir,
                    targetLanguages = languages
                ) { progress ->
                    SwingUtilities.invokeLater {
                        fileStatusArea.append("$progress\n")
                        fileStatusArea.caretPosition = fileStatusArea.document.length
                    }
                }
                
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    fileStatusArea.append("Error: ${e.message}\n")
                    Messages.showErrorDialog(project, "Translation failed: ${e.message}", "Error")
                }
            }
        }
    }
    
    private fun translateStrings() {
        val resourcePath = resourceDirField.text.trim()
        val selectedValues = valuesList.selectedValuesList
        
        if (resourcePath.isEmpty()) {
            Messages.showWarningDialog(project, "Please select resource directory!", "Warning")
            return
        }
        
        if (selectedValues.isEmpty()) {
            Messages.showWarningDialog(project, "Please select target values folders!", "Warning")
            return
        }
        
        if (stringTableModel.rowCount == 0) {
            Messages.showWarningDialog(project, "Please add some strings to translate!", "Warning")
            return
        }
        
        stringStatusArea.text = ""
        
        executorService.submit {
            try {
                SwingUtilities.invokeLater {
                    stringStatusArea.append("Starting batch string translation using Google Generative AI...\n")
                }
                
                val resourceDir = File(resourcePath)
                
                // Collect all strings into a list for batch processing
                val stringItems = mutableListOf<Pair<String, String>>()
                for (i in 0 until stringTableModel.rowCount) {
                    val stringName = stringTableModel.getValueAt(i, 0) as String
                    val stringText = stringTableModel.getValueAt(i, 1) as String
                    stringItems.add(stringName to stringText)
                }
                
                SwingUtilities.invokeLater {
                    stringStatusArea.append("Collected ${stringItems.size} strings for batch processing\n")
                    stringStatusArea.append("Using batches of 50 strings to optimize API calls\n")
                }
                
                // Use batch processing instead of individual string processing
                translationService.addBatchStringsToXmlFiles(
                    stringItems = stringItems,
                    resourceDir = resourceDir,
                    targetFolders = selectedValues.toTypedArray(),
                    onProgress = { progress ->
                        SwingUtilities.invokeLater {
                            stringStatusArea.append("$progress\n")
                            stringStatusArea.caretPosition = stringStatusArea.document.length
                        }
                    }
                )
                
                SwingUtilities.invokeLater {
                    stringStatusArea.append("All strings processed successfully using batch processing!\n")
                    clearAllStrings()
                }
                
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    stringStatusArea.append("Error: ${e.message}\n")
                }
            }
        }
    }
    
    /**
     * Import selected text from editor to the translator panel
     * Called by ImportSelectedTextAction
     */
    fun importSelectedText(selectedText: String) {
        println("DEBUG: importSelectedText called with text length: ${selectedText.length}")
        
        // First find and switch to Add New String tab
        focusOnAddStringTab()
        
        // Then process the text in EDT
        SwingUtilities.invokeLater {
            try {
                println("DEBUG: Starting import process...")
                
                // Clear existing content and show status
                stringStatusArea.text = ""
                stringStatusArea.append("=== IMPORTING SELECTED TEXT ===\n")
                stringStatusArea.append("📥 Processing ${selectedText.length} characters...\n")
                
                // Check if selected text contains string elements
                val hasStringElements = selectedText.contains("<string")
                println("DEBUG: Has string elements: $hasStringElements")
                
                if (hasStringElements) {
                    // If it contains string elements, add to bulk input and auto-parse
                    println("DEBUG: Setting bulk text area with XML strings")
                    bulkTextArea.text = selectedText
                    bulkTextArea.requestFocus()
                    stringStatusArea.append("📥 Added XML strings to bulk input area\n")
                    
                    // Auto-parse after a short delay to ensure UI is updated
                    Timer(500) { _ ->
                        SwingUtilities.invokeLater {
                            println("DEBUG: Auto-parsing imported strings")
                            stringStatusArea.append("🔄 Auto-parsing imported strings...\n")
                            parseBulkInput()
                        }
                    }.start()
                    
                } else {
                    // If it's just text, add to bulk input as template
                    println("DEBUG: Converting plain text to XML template")
                    val lines = selectedText.lines().filter { it.trim().isNotEmpty() }
                    val xmlTemplate = lines.mapIndexed { index, line ->
                        val name = "imported_string_${index + 1}"
                        "<string name=\"$name\">${line.trim()}</string>"
                    }.joinToString("\n")
                    
                    bulkTextArea.text = xmlTemplate
                    bulkTextArea.requestFocus()
                    stringStatusArea.append("📥 Converted ${lines.size} lines to XML template\n")
                    stringStatusArea.append("💡 Click '🔄 Parse XML Strings' to add to translation queue\n")
                }
                
                // Auto-detect resource directory if not set
                if (resourceDirField.text.trim().isEmpty()) {
                    println("DEBUG: Auto-detecting resource directory")
                    stringStatusArea.append("🔍 Auto-detecting resource directory...\n")
                    autoDetectResourceDirectory()
                }
                
                stringStatusArea.append("✅ Import completed successfully!\n")
                stringStatusArea.append("================================\n\n")
                
                // Ensure bulk text area is visible
                bulkTextArea.scrollRectToVisible(bulkTextArea.visibleRect)
                
                // Scroll status to bottom
                stringStatusArea.caretPosition = stringStatusArea.document.length
                
                println("DEBUG: Import process completed successfully")
                
            } catch (e: Exception) {
                println("DEBUG: Import failed with exception: ${e.message}")
                e.printStackTrace()
                stringStatusArea.append("❌ Import failed: ${e.message}\n")
                stringStatusArea.append("Please try again or check the console for details.\n")
            }
        }
    }
    
    /**
     * Focus on the "Add New String" tab to show imported content
     */
    private fun focusOnAddStringTab() {
        try {
            // Find the parent tabbed pane and switch to second tab (index 1)
            var parent = this.parent
            while (parent != null) {
                if (parent is JTabbedPane) {
                    println("DEBUG: Found tabbed pane, switching to Add New String tab")
                    parent.selectedIndex = 1 // "Add New String" tab
                    
                    // Ensure the tab switch is complete
                    SwingUtilities.invokeLater {
                        // Request focus on bulk text area
                        bulkTextArea.requestFocusInWindow()
                        
                        // Ensure bulk text area is visible
                        try {
                            val rect = bulkTextArea.getUI().modelToView2D(bulkTextArea, 0, javax.swing.text.Position.Bias.Forward)
                            if (rect != null) {
                                bulkTextArea.scrollRectToVisible(rect.bounds)
                            }
                        } catch (e: Exception) {
                            // Fallback if modelToView2D fails
                            bulkTextArea.scrollRectToVisible(bulkTextArea.visibleRect)
                        }
                    }
                    break
                }
                parent = parent.parent
            }
        } catch (e: Exception) {
            println("DEBUG: Could not switch tab: ${e.message}")
        }
    }
}