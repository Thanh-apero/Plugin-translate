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
import com.xmltranslator.services.ProjectScanner
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.CancellationException
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseMotionAdapter
// Removed unused import
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.Timer
import javax.swing.text.Position

class XmlTranslatorPanel(private val project: Project) : JPanel() {
    
    private val translationService = project.service<TranslationService>()
    private val executorService = Executors.newSingleThreadExecutor()
    
    // Translation state tracking
    private var isFileTranslating = false
    private var isStringTranslating = false
    private var currentFileTask: Future<*>? = null
    private var currentStringTask: Future<*>? = null

    // Cancellation flags for immediate response
    @Volatile private var isStringTranslationCancelled = false
    @Volatile private var isFileTranslationCancelled = false
    
    // UI components for translation control
    private lateinit var fileTranslateButton: JButton
    private lateinit var stringTranslateButton: JButton
    
    // Hover state for language list
    private var hoveredIndex = -1
    
    // File Translation Tab Components - Updated for module-based translation
    private val moduleListModel = DefaultListModel<ProjectScanner.AndroidModule>()
    private val moduleList = JBList(moduleListModel)
    private val languageListModel = DefaultListModel<String>()
    private val languageList = JBList(languageListModel)
    private val languageInput = JBTextField()
    private val fileStatusArea = JBTextArea()
    private var selectedModule: ProjectScanner.AndroidModule? = null
    
    // String Addition Tab Components - Updated for better layout
    private val resourceDirField = JBTextField()
    private val valuesListModel = DefaultListModel<String>()
    private val valuesList = JBList(valuesListModel)
    private val stringStatusArea = JBTextArea()
    private val stringTableModel = DefaultTableModel(arrayOf("Name", "Text"), 0)
    private val stringTable = JBTable(stringTableModel)
    private val bulkTextArea = JBTextArea(6, 50) // For bulk XML input
    private lateinit var moduleDropdown: JComboBox<ProjectScanner.AndroidModule>
    
    init {
        setupUI()
        autoDetectResourceDirectory()
        loadAvailableModules()
        
        // Load modules for string addition dropdown
        SwingUtilities.invokeLater {
            updateStringModuleSelection()
        }
        
        // Ensure buttons have correct initial state
        updateFileTranslationUI()
        updateStringTranslationUI()
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
        
        // Module selection section - Fixed layout
        val modulePanel = JPanel(BorderLayout())
        modulePanel.border = BorderFactory.createTitledBorder("📦 Select Module to Translate")
        
        // Module selection with info - Using BoxLayout for responsive design
        val moduleTopPanel = JPanel()
        moduleTopPanel.layout = BoxLayout(moduleTopPanel, BoxLayout.X_AXIS)
        moduleTopPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        val infoLabel = JBLabel("<html><i>Select an Android module with strings.xml to translate</i></html>")
        moduleTopPanel.add(infoLabel)
        
        moduleTopPanel.add(Box.createHorizontalGlue())
        
        val refreshModulesButton = JButton("🔄 Refresh")
        refreshModulesButton.preferredSize = Dimension(100, 25)
        refreshModulesButton.maximumSize = Dimension(100, 25)
        refreshModulesButton.addActionListener { loadAvailableModules() }
        moduleTopPanel.add(refreshModulesButton)
        
        moduleTopPanel.add(Box.createRigidArea(Dimension(5, 0)))
        
        val showProjectInfoButton = JButton("ℹ️ Info")
        showProjectInfoButton.preferredSize = Dimension(80, 25)
        showProjectInfoButton.maximumSize = Dimension(80, 25)
        showProjectInfoButton.addActionListener { showProjectInfo() }
        moduleTopPanel.add(showProjectInfoButton)
        
        modulePanel.add(moduleTopPanel, BorderLayout.NORTH)
        
        // Module list - Fixed sizing
        moduleList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        moduleList.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11)
        
        // Simplified cell renderer to avoid layout issues
        moduleList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                
                if (value is ProjectScanner.AndroidModule) {
                    val status = if (value.hasStringsFile) "✅" else "❌"
                    text = "${value.name} ($status ${value.availableValuesFolders.size} folders)"
                    toolTipText = "Path: ${value.resDir.path}"
                }
                
                return this
            }
        }
        
        moduleList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                this@XmlTranslatorPanel.selectedModule = moduleList.selectedValue
                updateFileTranslationUI()
            }
        }
        
        val moduleScrollPane = JScrollPane(moduleList)
        moduleScrollPane.preferredSize = Dimension(680, 100)
        moduleScrollPane.minimumSize = Dimension(400, 80)
        moduleScrollPane.border = BorderFactory.createLoweredBevelBorder()
        
        setupSmartScrollBehavior(moduleList, moduleScrollPane)
        
        modulePanel.add(moduleScrollPane, BorderLayout.CENTER)
        
        // Fixed minimum height for module panel
        modulePanel.preferredSize = Dimension(700, 180)
        modulePanel.minimumSize = Dimension(500, 160)
        
        panel.add(modulePanel, BorderLayout.NORTH)
        
        // Language selection - Using BoxLayout for responsive design
        val langPanel = JPanel(BorderLayout())
        langPanel.border = BorderFactory.createTitledBorder("Target Languages")
        
        // Language input section - Responsive layout
        val langInputPanel = JPanel()
        langInputPanel.layout = BoxLayout(langInputPanel, BoxLayout.X_AXIS)
        langInputPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        langInputPanel.add(JLabel("Language code:"))
        langInputPanel.add(Box.createRigidArea(Dimension(5, 0)))
        
        languageInput.preferredSize = Dimension(80, 25)
        languageInput.maximumSize = Dimension(100, 25)
        langInputPanel.add(languageInput)
        
        langInputPanel.add(Box.createRigidArea(Dimension(5, 0)))
        
        val addLangButton = JButton("Add")
        addLangButton.preferredSize = Dimension(60, 25)
        addLangButton.maximumSize = Dimension(60, 25)
        addLangButton.addActionListener { addLanguage() }
        langInputPanel.add(addLangButton)
        
        langInputPanel.add(Box.createRigidArea(Dimension(5, 0)))
        
        val removeLangButton = JButton("Remove")
        removeLangButton.preferredSize = Dimension(80, 25)
        removeLangButton.maximumSize = Dimension(80, 25)
        removeLangButton.addActionListener { removeLanguage() }
        langInputPanel.add(removeLangButton)
        
        langInputPanel.add(Box.createHorizontalGlue())
        
        langPanel.add(langInputPanel, BorderLayout.NORTH)
        
        val languageScrollPane = JScrollPane(languageList)
        languageScrollPane.preferredSize = Dimension(680, 60)
        languageScrollPane.minimumSize = Dimension(400, 50)
        
        setupSmartScrollBehavior(languageList, languageScrollPane)
        
        langPanel.add(languageScrollPane, BorderLayout.CENTER)
        
        // Common languages buttons - Responsive grid
        val commonLangPanel = JPanel(GridLayout(0, 6, 3, 3)) // 0 rows = auto-adjust
        val commonLanguages = mapOf(
            "Vietnamese" to "vi", "Chinese" to "zh", "Korean" to "ko", "Japanese" to "ja",
            "Italian" to "it", "French" to "fr", "German" to "de", "Spanish" to "es",
            "Arabic" to "ar", "Bengali" to "bn", "Greek" to "el", "Hindi" to "hi",
            "Indonesian" to "in", "Marathi" to "mr", "Malay" to "ms", "Portuguese" to "pt",
            "Portuguese (Brazil)" to "pt-rBR", "Russian" to "ru", "Tamil" to "ta", 
            "Telugu" to "te", "Thai" to "th", "Turkish" to "tr", "Filipino" to "tl"
        )
        
        commonLanguages.forEach { (name, code) ->
            val button = JButton(name)
            button.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 9)
            button.margin = Insets(1, 2, 1, 2)
            button.addActionListener { addLanguageCode(code) }
            commonLangPanel.add(button)
        }
        
        val commonLangScrollPane = JScrollPane(commonLangPanel)
        commonLangScrollPane.preferredSize = Dimension(680, 120)
        commonLangScrollPane.minimumSize = Dimension(400, 100)
        langPanel.add(commonLangScrollPane, BorderLayout.SOUTH)
        
        // Set responsive sizing for language panel
        langPanel.preferredSize = Dimension(700, 300)
        langPanel.minimumSize = Dimension(500, 250)
        
        panel.add(langPanel, BorderLayout.CENTER)
        
        // Status and translate button - Fixed sizing
        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.preferredSize = Dimension(700, 200)
        bottomPanel.minimumSize = Dimension(500, 180)
        
        fileStatusArea.isEditable = false
        fileStatusArea.rows = 6
        val fileStatusScrollPane = JScrollPane(fileStatusArea)
        fileStatusScrollPane.preferredSize = Dimension(680, 150)
        fileStatusScrollPane.minimumSize = Dimension(400, 130)
        
        setupSmartScrollBehavior(fileStatusArea, fileStatusScrollPane)
        
        bottomPanel.add(fileStatusScrollPane, BorderLayout.CENTER)
        
        // Button panel with responsive layout
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        buttonPanel.add(Box.createHorizontalGlue())
        
        fileTranslateButton = JButton("🌍 Translate Module")
        fileTranslateButton.preferredSize = Dimension(160, 30)
        fileTranslateButton.maximumSize = Dimension(200, 30)
        fileTranslateButton.addActionListener { handleModuleTranslation() }
        buttonPanel.add(fileTranslateButton)
        
        buttonPanel.add(Box.createHorizontalGlue())
        
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH)
        
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
        stringsFrame.preferredSize = Dimension(700, 400)
        stringsFrame.minimumSize = Dimension(500, 350)
        
        // Bulk input section - Enhanced design
        val bulkPanel = JPanel(BorderLayout())
        bulkPanel.border = BorderFactory.createTitledBorder("📋 XML String Input")
        bulkPanel.preferredSize = Dimension(680, 200)
        
        // Instructions panel - Responsive
        val instructionsPanel = JPanel()
        instructionsPanel.layout = BoxLayout(instructionsPanel, BoxLayout.X_AXIS)
        instructionsPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        val instructionsLabel = JBLabel("<html><i>Paste your XML strings below, then click Parse</i></html>")
        instructionsPanel.add(instructionsLabel)
        instructionsPanel.add(Box.createHorizontalGlue())
        
        bulkPanel.add(instructionsPanel, BorderLayout.NORTH)
        
        // Text area with better formatting
        bulkTextArea.lineWrap = true
        bulkTextArea.wrapStyleWord = true
        bulkTextArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11)
        bulkTextArea.text = """"""
        
        val bulkScrollPane = JScrollPane(bulkTextArea)
        bulkScrollPane.preferredSize = Dimension(680, 120)
        bulkScrollPane.minimumSize = Dimension(400, 100)
        bulkScrollPane.border = BorderFactory.createLoweredBevelBorder()
        
        setupSmartScrollBehavior(bulkTextArea, bulkScrollPane)
        
        bulkPanel.add(bulkScrollPane, BorderLayout.CENTER)
        
        // Parse button with responsive layout
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        buttonPanel.add(Box.createHorizontalGlue())
        
        val parseButton = JButton("🔄 Parse XML")
        parseButton.preferredSize = Dimension(120, 25)
        parseButton.maximumSize = Dimension(120, 25)
        parseButton.addActionListener { parseBulkInput() }
        buttonPanel.add(parseButton)
        
        buttonPanel.add(Box.createRigidArea(Dimension(10, 0)))
        
        val clearInputButton = JButton("🗑️ Clear")
        clearInputButton.preferredSize = Dimension(80, 25)
        clearInputButton.maximumSize = Dimension(80, 25)
        clearInputButton.addActionListener { 
            bulkTextArea.text = ""
            stringStatusArea.append("Input cleared\n")
        }
        buttonPanel.add(clearInputButton)
        
        buttonPanel.add(Box.createHorizontalGlue())
        
        bulkPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        stringsFrame.add(bulkPanel, BorderLayout.NORTH)
        
        // String list table with enhanced design
        val tablePanel = JPanel(BorderLayout())
        tablePanel.border = BorderFactory.createTitledBorder("📊 Translation Queue")
        tablePanel.preferredSize = Dimension(680, 180)
        
        stringTable.fillsViewportHeight = true
        stringTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        stringTable.rowHeight = 22
        stringTable.gridColor = java.awt.Color.LIGHT_GRAY
        stringTable.setShowGrid(true)
        
        // Set column widths
        stringTable.columnModel.getColumn(0).preferredWidth = 200 // Name
        stringTable.columnModel.getColumn(1).preferredWidth = 400 // Text

        // Add custom cell renderer to display actual \n, \t, \r escape sequences in the table
        stringTable.columnModel.getColumn(1).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean,
                row: Int, column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

                if (value is String) {
                    val displayText = value.replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")
                    text = displayText
                    toolTipText = "<html>${value.replace("\n", "<br>").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")}</html>"
                }

                return component
            }
        }
        
        val tableScrollPane = JScrollPane(stringTable)
        tableScrollPane.preferredSize = Dimension(680, 130)
        tableScrollPane.minimumSize = Dimension(400, 100)
        tableScrollPane.border = BorderFactory.createLoweredBevelBorder()
        
        setupSmartScrollBehavior(stringTable, tableScrollPane)
        
        tablePanel.add(tableScrollPane, BorderLayout.CENTER)
        
        // Table action buttons - Responsive layout
        val tableButtonPanel = JPanel()
        tableButtonPanel.layout = BoxLayout(tableButtonPanel, BoxLayout.X_AXIS)
        tableButtonPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        val removeButton = JButton("❌ Remove")
        removeButton.preferredSize = Dimension(100, 25)
        removeButton.maximumSize = Dimension(100, 25)
        removeButton.addActionListener { removeSelectedString() }
        tableButtonPanel.add(removeButton)
        
        tableButtonPanel.add(Box.createRigidArea(Dimension(5, 0)))
        
        val clearButton = JButton("🗑️ Clear")
        clearButton.preferredSize = Dimension(80, 25)
        clearButton.maximumSize = Dimension(80, 25)
        clearButton.addActionListener { clearAllStrings() }
        tableButtonPanel.add(clearButton)
        
        tableButtonPanel.add(Box.createRigidArea(Dimension(5, 0)))
        
        val quickAddButton = JButton("➕ Add")
        quickAddButton.preferredSize = Dimension(80, 25)
        quickAddButton.maximumSize = Dimension(80, 25)
        quickAddButton.addActionListener { showQuickAddDialog() }
        tableButtonPanel.add(quickAddButton)
        
        tableButtonPanel.add(Box.createHorizontalGlue())
        
        tablePanel.add(tableButtonPanel, BorderLayout.SOUTH)
        
        stringsFrame.add(tablePanel, BorderLayout.CENTER)
        
        return stringsFrame
    }
    
    private fun createResourceDirectorySection(): JPanel {
        val dirFrame = JPanel(BorderLayout())
        dirFrame.border = BorderFactory.createTitledBorder("📦 Select Target Module")
        dirFrame.preferredSize = Dimension(700, 250)
        dirFrame.minimumSize = Dimension(500, 220)
        
        // Module selection for String Addition - Responsive layout
        val moduleSelectionPanel = JPanel(BorderLayout())
        
        // Top panel with responsive layout
        val moduleTopPanel = JPanel()
        moduleTopPanel.layout = BoxLayout(moduleTopPanel, BoxLayout.X_AXIS)
        moduleTopPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        val moduleLabel = JBLabel("<html><i>Select module to add strings to:</i></html>")
        moduleTopPanel.add(moduleLabel)
        
        moduleTopPanel.add(Box.createHorizontalGlue())
        
        val refreshModulesForStringButton = JButton("🔄 Refresh")
        refreshModulesForStringButton.preferredSize = Dimension(90, 25)
        refreshModulesForStringButton.maximumSize = Dimension(90, 25)
        refreshModulesForStringButton.addActionListener { 
            loadAvailableModules()
            updateStringModuleSelection()
        }
        moduleTopPanel.add(refreshModulesForStringButton)
        
        moduleSelectionPanel.add(moduleTopPanel, BorderLayout.NORTH)
        
        // Module dropdown - Fixed sizing
        val moduleDropdown = JComboBox<ProjectScanner.AndroidModule>()
        moduleDropdown.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11)
        moduleDropdown.preferredSize = Dimension(680, 25)
        moduleDropdown.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                
                if (value is ProjectScanner.AndroidModule) {
                    text = "${value.name} ${if (value.hasStringsFile) "✅" else "⚠️"}"
                    toolTipText = "${value.resDir.path}"
                }
                
                return this
            }
        }
        
        moduleDropdown.addActionListener { event ->
            val selectedStringModule = moduleDropdown.selectedItem as? ProjectScanner.AndroidModule
            if (selectedStringModule != null) {
                resourceDirField.text = selectedStringModule.resDir.path
                updateValuesFolders(selectedStringModule.resDir.path)
                
                stringStatusArea.append("📦 Chọn module: ${selectedStringModule.name}\n")
                stringStatusArea.append("📂 Resource dir: ${selectedStringModule.resDir.path}\n")
            }
        }
        
        val moduleDropdownPanel = JPanel(BorderLayout())
        moduleDropdownPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        moduleDropdownPanel.add(moduleDropdown, BorderLayout.CENTER)
        moduleSelectionPanel.add(moduleDropdownPanel, BorderLayout.CENTER)
        
        dirFrame.add(moduleSelectionPanel, BorderLayout.NORTH)
        
        // Legacy resource directory field - Responsive layout
        val dirInputPanel = JPanel()
        dirInputPanel.layout = BoxLayout(dirInputPanel, BoxLayout.X_AXIS)
        dirInputPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        dirInputPanel.add(JLabel("📂 Resource:"))
        dirInputPanel.add(Box.createRigidArea(Dimension(5, 0)))
        
        resourceDirField.preferredSize = Dimension(400, 25)
        resourceDirField.maximumSize = Dimension(Integer.MAX_VALUE, 25)
        resourceDirField.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 10)
        resourceDirField.isEditable = false
        resourceDirField.background = UIManager.getColor("TextField.inactiveBackground")
        dirInputPanel.add(resourceDirField)
        
        dirInputPanel.add(Box.createRigidArea(Dimension(5, 0)))
        
        val browseResourceButton = JButton("📁")
        browseResourceButton.preferredSize = Dimension(40, 25)
        browseResourceButton.maximumSize = Dimension(40, 25)
        browseResourceButton.addActionListener { browseResourceDir() }
        dirInputPanel.add(browseResourceButton)
        
        // Store dropdown reference for later use
        this.moduleDropdown = moduleDropdown
        
        dirFrame.add(dirInputPanel, BorderLayout.CENTER)
        
        // Values folders selection - Fixed sizing
        val valuesPanel = JPanel(BorderLayout())
        valuesPanel.preferredSize = Dimension(700, 150)
        val valuesLabel = JLabel("🌍 Target language folders:")
        valuesLabel.border = BorderFactory.createEmptyBorder(5, 5, 2, 5)
        valuesPanel.add(valuesLabel, BorderLayout.NORTH)
        
        valuesList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        valuesList.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11)
        val valuesScrollPane = JScrollPane(valuesList)
        valuesScrollPane.preferredSize = Dimension(680, 90)
        valuesScrollPane.minimumSize = Dimension(400, 70)
        valuesScrollPane.border = BorderFactory.createLoweredBevelBorder()
        
        setupSmartScrollBehavior(valuesList, valuesScrollPane)
        
        valuesPanel.add(valuesScrollPane, BorderLayout.CENTER)
        
        // Enhanced values list with hover delete functionality
        setupLanguageListHover()
        
        // Refresh button with responsive layout
        val langButtonPanel = JPanel()
        langButtonPanel.layout = BoxLayout(langButtonPanel, BoxLayout.X_AXIS)
        langButtonPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        val refreshButton = JButton("🔄 Refresh")
        refreshButton.preferredSize = Dimension(90, 25)
        refreshButton.maximumSize = Dimension(90, 25)
        refreshButton.toolTipText = "Re-scan directory for language folders"
        refreshButton.addActionListener { 
            val dirPath = resourceDirField.text.trim()
            if (dirPath.isNotEmpty()) {
                updateValuesFolders(dirPath)
            }
        }
        langButtonPanel.add(refreshButton)
        langButtonPanel.add(Box.createHorizontalGlue())
        
        valuesPanel.add(langButtonPanel, BorderLayout.SOUTH)
        
        dirFrame.add(valuesPanel, BorderLayout.SOUTH)
        
        return dirFrame
    }
    
    private fun createStatusSection(): JPanel {
        val statusFrame = JPanel(BorderLayout())
        statusFrame.border = BorderFactory.createTitledBorder("🚀 Translation Status & Actions")
        statusFrame.preferredSize = Dimension(700, 220)
        statusFrame.minimumSize = Dimension(500, 200)
        
        // Status area with proper IDE-themed styling
        stringStatusArea.isEditable = false
        stringStatusArea.rows = 6
        stringStatusArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11)
        stringStatusArea.background = UIManager.getColor("TextArea.background")
        stringStatusArea.foreground = UIManager.getColor("TextArea.foreground")
        stringStatusArea.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        val statusScrollPane = JScrollPane(stringStatusArea)
        statusScrollPane.preferredSize = Dimension(680, 150)
        statusScrollPane.minimumSize = Dimension(400, 130)
        statusScrollPane.border = BorderFactory.createLoweredBevelBorder()
        
        setupSmartScrollBehavior(stringStatusArea, statusScrollPane)
        
        statusFrame.add(statusScrollPane, BorderLayout.CENTER)
        
        // Action buttons panel - Responsive layout
        val actionPanel = JPanel()
        actionPanel.layout = BoxLayout(actionPanel, BoxLayout.X_AXIS)
        actionPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        actionPanel.add(Box.createHorizontalGlue())
        
        stringTranslateButton = JButton("🌍 Translate All Strings")
        stringTranslateButton.preferredSize = Dimension(170, 30)
        stringTranslateButton.maximumSize = Dimension(200, 30)
        stringTranslateButton.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 11)
        stringTranslateButton.background = java.awt.Color(59, 142, 234) // IntelliJ blue
        stringTranslateButton.foreground = java.awt.Color.WHITE
        stringTranslateButton.isFocusPainted = false
        stringTranslateButton.addActionListener { handleStringTranslation() }
        actionPanel.add(stringTranslateButton)
        
        actionPanel.add(Box.createRigidArea(Dimension(10, 0)))
        
        val clearStatusButton = JButton("🗑️ Clear")
        clearStatusButton.preferredSize = Dimension(80, 30)
        clearStatusButton.maximumSize = Dimension(80, 30)
        clearStatusButton.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11)
        clearStatusButton.isFocusPainted = false
        clearStatusButton.addActionListener { 
            stringStatusArea.text = ""
            stringStatusArea.append("Status cleared ✨\n")
        }
        actionPanel.add(clearStatusButton)
        
        actionPanel.add(Box.createHorizontalGlue())
        
        statusFrame.add(actionPanel, BorderLayout.SOUTH)
        
        return statusFrame
    }
    
    private fun autoDetectResourceDirectory() {
        try {
            // Show filtering info at startup
            stringStatusArea.append("📁 Loại bỏ thư mục qualifier (night, v29, v30, land, hdpi...)\n")
            stringStatusArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
            
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
                            stringStatusArea.append("🔍 Auto-detected resource directory: $path\n")
                            return
                        }
                    }
                }
                
                // If no res folder found, use project root
                resourceDirField.text = projectPath
                stringStatusArea.append("📂 Using project root directory: $projectPath\n")
            }
        } catch (e: Exception) {
            stringStatusArea.append("❌ Auto-detection failed: ${e.message}\n")
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
                val rawText = match.groupValues[2].trim()
                
                val text = rawText
                    // First handle double-escaped newlines (\\n -> \n)
                    .replace("\\\\n", "\n")
                    // Then handle single-escaped newlines (\n -> actual newline)
                    .replace("\\n", "\n")
                    // Handle other common escape sequences
                    .replace("\\\\t", "\t")
                    .replace("\\t", "\t")
                    .replace("\\\\r", "\r")
                    .replace("\\r", "\r")
                    // Handle XML entities
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                
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
    
    // Deprecated - kept for compatibility
    @Deprecated("Use module-based translation instead")
    private fun browseInputFile() {
        // Legacy method - no longer used in new UI
    }
    
    @Deprecated("Use module-based translation instead") 
    private fun browseOutputDir() {
        // Legacy method - no longer used in new UI
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
            // Use the enhanced filtering from TranslationService
            val filteredFolders = translationService.getFilteredValuesFolders(dir)
            
            // Ensure 'values' comes first
            val sortedDirs = filteredFolders.sortedWith { a, b ->
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
            
            // Enhanced status feedback with filtering information
            if (sortedDirs.isNotEmpty()) {
                stringStatusArea.append("🔍 Quét thư mục: $dirPath\n")
                stringStatusArea.append("✅ Tìm thấy ${sortedDirs.size} thư mục ngôn ngữ hợp lệ:\n")
                sortedDirs.forEach { folder ->
                    stringStatusArea.append("   📁 $folder\n")
                }
                stringStatusArea.append("🚫 Tự động loại bỏ các thư mục qualifier (night, v29, v30, land, hdpi...)\n")
                stringStatusArea.append("💡 Chỉ hiển thị thư mục ngôn ngữ cần dịch\n\n")
            } else {
                stringStatusArea.append("⚠️  Không tìm thấy thư mục ngôn ngữ hợp lệ trong: $dirPath\n")
                stringStatusArea.append("💡 Tạo thư mục ngôn ngữ (ví dụ: values-vi, values-zh) để bật tính năng dịch\n")
                stringStatusArea.append("🚫 Các thư mục qualifier sẽ tự động bị loại bỏ (values-night, values-v29, v.v.)\n\n")
            }
        } else {
            stringStatusArea.append("❌ Thư mục không tồn tại: $dirPath\n")
            stringStatusArea.append("💡 Vui lòng chọn thư mục resource hợp lệ.\n")
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
        stringStatusArea.append("✨ All strings cleared from queue\n")
    }
    
    private fun showQuickAddDialog() {
        val dialog = object : JDialog() {
            init {
                title = "➕ Quick Add String"
                modalityType = Dialog.ModalityType.APPLICATION_MODAL
                layout = BorderLayout()
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                preferredSize = Dimension(400, 250)
            }
        }
        
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        
        // String name
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
        panel.add(JLabel("String name:"), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        val nameField = JBTextField()
        nameField.preferredSize = Dimension(250, 25)
        panel.add(nameField, gbc)
        
        // String text
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.NORTHWEST
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("Text:"), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0
        val textArea = JBTextArea(4, 25)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        val scrollPane = JScrollPane(textArea)
        scrollPane.preferredSize = Dimension(250, 80)
        
        setupSmartScrollBehavior(textArea, scrollPane)
        
        panel.add(scrollPane, gbc)
        
        dialog.add(panel, BorderLayout.CENTER)
        
        // Buttons
        val buttonPanel = JPanel(FlowLayout())
        val addButton = JButton("✅ Add")
        addButton.preferredSize = Dimension(80, 25)
        addButton.addActionListener {
            val name = nameField.text.trim()
            val text = textArea.text.trim()
            
            if (name.isNotEmpty() && text.isNotEmpty()) {
                stringTableModel.addRow(arrayOf(name, text))
                stringStatusArea.append("Added string: '$name' ✅\n")
                dialog.dispose()
            } else {
                Messages.showWarningDialog(project, "Please enter both string name and text!", "Warning")
            }
        }
        buttonPanel.add(addButton)
        
        val cancelButton = JButton("❌ Cancel")
        cancelButton.preferredSize = Dimension(80, 25)
        cancelButton.addActionListener { dialog.dispose() }
        buttonPanel.add(cancelButton)
        
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }
    
    @Deprecated("Use translateModule() instead")
    private fun translateFile() {
        // Legacy method - redirects to module translation
        translateModule()
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

        // Set translation state
        isStringTranslating = true
        isStringTranslationCancelled = false // Reset cancellation flag
        updateStringTranslationUI()
        stringStatusArea.text = ""

        currentStringTask = executorService.submit {
            try {
                SwingUtilities.invokeLater {
                    stringStatusArea.append("Starting batch string translation using Google Generative AI...\n")
                }

                val resourceDir = File(resourcePath)

                // Collect all strings into a list for batch processing - Get fresh data from table
                val stringItems = mutableListOf<Pair<String, String>>()
                SwingUtilities.invokeAndWait {
                    // Get current data from table model at translation time
                    for (i in 0 until stringTableModel.rowCount) {
                        val stringName = stringTableModel.getValueAt(i, 0) as String
                        val stringText = stringTableModel.getValueAt(i, 1) as String
                        stringItems.add(stringName to stringText)
                    }
                }

                if (stringItems.isEmpty()) {
                    SwingUtilities.invokeLater {
                        stringStatusArea.append("❌ No strings found to translate. Translation queue is empty.\n")
                        Messages.showWarningDialog(project, "Translation queue is empty!", "Warning")
                    }
                    return@submit
                }

                // Check cancellation before starting
                if (isStringTranslationCancelled) {
                    SwingUtilities.invokeLater {
                        stringStatusArea.append("⏹️ Translation was cancelled before starting\n")
                    }
                    return@submit
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
                    onProgress = { progress: String ->
                        // Check cancellation on each progress update
                        if (isStringTranslationCancelled) {
                            throw java.util.concurrent.CancellationException("Translation cancelled by user")
                        }
                        SwingUtilities.invokeLater {
                            stringStatusArea.append("$progress\n")
                            stringStatusArea.caretPosition = stringStatusArea.document.length
                        }
                    }
                )

                // Final check before completion
                if (isStringTranslationCancelled) {
                    SwingUtilities.invokeLater {
                        stringStatusArea.append("⏹️ Translation was cancelled\n")
                    }
                    return@submit
                }

                SwingUtilities.invokeLater {
                    stringStatusArea.append("✅ All strings processed successfully using batch processing!\n")
                    clearAllStrings()
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    if (e is CancellationException) {
                        stringStatusArea.append("⏹️ Translation was cancelled\n")
                    } else {
                        stringStatusArea.append("Error: ${e.message}\n")
                    }
                }
            } finally {
                // Reset translation state
                isStringTranslating = false
                isStringTranslationCancelled = false
                updateStringTranslationUI()
                currentStringTask = null
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
                        // This is a workaround for the JBTextArea which doesn't expose modelToView2D
                        bulkTextArea.scrollRectToVisible(bulkTextArea.visibleRect)
                    }
                    break
                }
                parent = parent.parent
            }
        } catch (e: Exception) {
            println("DEBUG: Could not switch tab: ${e.message}")
        }
    }

    /**
     * Setup optimized scroll behavior for components
     * Forward scroll events to parent when component doesn't need them
     * Based on standard pattern from Java Swing Tips
     */
    private fun setupSmartScrollBehavior(component: JComponent, scrollPane: JScrollPane) {
        scrollPane.addMouseWheelListener { e ->
            val dir = e.wheelRotation
            val model = scrollPane.verticalScrollBar.model
            val ext = model.extent
            val min = model.minimum
            val max = model.maximum
            val value = model.value
            
            // Check if we've hit the scroll limits
            val atMaxAndScrollingDown = (value + ext >= max && dir > 0)
            val atMinAndScrollingUp = (value <= min && dir < 0)
            val isEmptyTextArea = component is JTextArea && component.document.length == 0
            
            if (atMaxAndScrollingDown || atMinAndScrollingUp || isEmptyTextArea) {
                // Find parent scroll pane and forward event
                var parent = scrollPane.parent
                while (parent != null) {
                    if (parent is JScrollPane) {
                        parent.dispatchEvent(SwingUtilities.convertMouseEvent(scrollPane, e, parent))
                        break
                    } else if (parent is JComponent) {
                        parent.dispatchEvent(SwingUtilities.convertMouseEvent(scrollPane, e, parent))
                        break
                    }
                    parent = parent.parent
                }
            }
        }
    }

    // Translation control methods
    private fun handleFileTranslation() {
        if (isFileTranslating) {
            stopFileTranslation()
        } else {
            translateFile()
        }
    }
    
    private fun handleStringTranslation() {
        if (isStringTranslating) {
            stopStringTranslation()
        } else {
            translateStrings()
        }
    }
    

    
    private fun updateStringTranslationUI() {
        SwingUtilities.invokeLater {
            if (isStringTranslating) {
                stringTranslateButton.text = "⏹️ Stop Translation"
                stringTranslateButton.background = java.awt.Color(220, 53, 69) // Red color
            } else {
                stringTranslateButton.text = "🌍 Translate All Strings"
                stringTranslateButton.background = java.awt.Color(59, 142, 234) // IntelliJ blue
            }
        }
    }
    
    private fun stopFileTranslation() {
        currentFileTask?.cancel(true)
        isFileTranslating = false
        updateFileTranslationUI()
        SwingUtilities.invokeLater {
            fileStatusArea.append("❌ Translation cancelled by user\n")
        }
    }
    
    private fun stopStringTranslation() {
        isStringTranslationCancelled = true // Set flag immediately for instant response
        currentStringTask?.cancel(true)
        isStringTranslating = false
        updateStringTranslationUI()
        SwingUtilities.invokeLater {
            stringStatusArea.append("❌ Translation cancelled by user\n")
            stringStatusArea.append("🧹 Clearing translation queue to start fresh next time...\n")
            
            // Clear the translation queue when stopping to prevent translating old strings
            clearAllStrings()
            
            stringStatusArea.append("✨ Translation queue cleared. Add new strings to translate fresh.\n")
        }
    }
    
    // New module-based methods
    private fun loadAvailableModules() {
        try {
            val modules = translationService.getAvailableModules()
            moduleListModel.clear()
            
            for (module in modules) {
                moduleListModel.addElement(module)
            }
            
            fileStatusArea.append("🔍 Tìm thấy ${modules.size} module(s) có thư mục values\n")
            
            if (modules.isEmpty()) {
                fileStatusArea.append("❌ Không tìm thấy module nào có cấu trúc Android\n")
                fileStatusArea.append("💡 Đảm bảo project có cấu trúc: module/src/main/res/values/\n")
            } else {
                fileStatusArea.append("✅ Chọn module để bắt đầu dịch\n")
                // Auto-select first valid module with strings.xml
                val firstValidModuleForFile = modules.firstOrNull { it.hasStringsFile }
                if (firstValidModuleForFile != null) {
                    moduleList.setSelectedValue(firstValidModuleForFile, true)
                    this.selectedModule = firstValidModuleForFile
                }
            }
        } catch (e: Exception) {
            fileStatusArea.append("❌ Lỗi khi scan modules: ${e.message}\n")
        }
    }
    
    private fun showProjectInfo() {
        val projectInfo = translationService.getProjectInfo()
        val dialog = JDialog()
        dialog.title = "📦 Project Information"
        dialog.modalityType = Dialog.ModalityType.APPLICATION_MODAL
        dialog.layout = BorderLayout()
        
        val textArea = JBTextArea(projectInfo)
        textArea.isEditable = false
        textArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        textArea.caretPosition = 0
        
        val scrollPane = JScrollPane(textArea)
        scrollPane.preferredSize = Dimension(600, 400)
        
        dialog.add(scrollPane, BorderLayout.CENTER)
        
        val closeButton = JButton("✅ Close")
        closeButton.addActionListener { dialog.dispose() }
        val buttonPanel = JPanel(FlowLayout())
        buttonPanel.add(closeButton)
        
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }
    
    private fun handleModuleTranslation() {
        if (isFileTranslating) {
            stopFileTranslation()
        } else {
            translateModule()
        }
    }
    
    private fun translateModule() {
        val module = selectedModule
        val languages = (0 until languageListModel.size()).map { languageListModel.getElementAt(it) }
        
        if (module == null) {
            Messages.showWarningDialog(project, "Vui lòng chọn một module để dịch!", "Warning")
            return
        }
        
        if (!module.hasStringsFile) {
            Messages.showWarningDialog(project, "Module được chọn không có file strings.xml!", "Warning")  
            return
        }
        
        if (languages.isEmpty()) {
            Messages.showWarningDialog(project, "Vui lòng thêm ít nhất một ngôn ngữ để dịch!", "Warning")
            return
        }
        
        // Set translation state
        isFileTranslating = true
        updateFileTranslationUI()
        fileStatusArea.text = ""
        
        currentFileTask = executorService.submit {
            try {
                SwingUtilities.invokeLater {
                    fileStatusArea.append("🚀 Bắt đầu dịch module sử dụng Google Generative AI...\n")
                }
                
                // Use the module translation service
                translationService.translateModule(
                    module = module,
                    targetLanguages = languages
                ) { progress ->
                    SwingUtilities.invokeLater {
                        fileStatusArea.append("$progress\n")
                        fileStatusArea.caretPosition = fileStatusArea.document.length
                    }
                }
                
                // Completed successfully
                SwingUtilities.invokeLater {
                    fileStatusArea.append("🎉 Dịch module hoàn tất thành công!\n")
                }
                
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    if (e is CancellationException ) {
                        fileStatusArea.append("⏹️ Việc dịch đã bị hủy\n")
                    } else {
                        fileStatusArea.append("❌ Lỗi: ${e.message}\n")
                        Messages.showErrorDialog(project, "Dịch thất bại: ${e.message}", "Error")
                    }
                }
            } finally {
                // Reset translation state
                isFileTranslating = false
                updateFileTranslationUI()
                currentFileTask = null
            }
        }
    }
    
    private fun updateFileTranslationUI() {
        SwingUtilities.invokeLater {
            val hasValidModule = selectedModule?.hasStringsFile == true
            val hasLanguages = languageListModel.size() > 0
            
            if (isFileTranslating) {
                fileTranslateButton.text = "⏹️ Dừng dịch"
                fileTranslateButton.background = java.awt.Color(220, 53, 69) // Red color
                fileTranslateButton.isEnabled = true
            } else {
                fileTranslateButton.text = "🌍 Dịch Module"
                fileTranslateButton.background = UIManager.getColor("Button.background")
                fileTranslateButton.isEnabled = hasValidModule && hasLanguages
            }
        }
    }
    
    private fun updateStringModuleSelection() {
        try {
            val modules = translationService.getAvailableModules()
            
            moduleDropdown.removeAllItems()
            for (module in modules) {
                moduleDropdown.addItem(module)
            }
            
            // Auto-select first valid module
            val firstValidModuleForString = modules.firstOrNull { it.hasStringsFile }
            if (firstValidModuleForString != null) {
                moduleDropdown.selectedItem = firstValidModuleForString
            }
            
        } catch (e: Exception) {
            stringStatusArea.append("❌ Lỗi khi load modules: ${e.message}\n")
        }
    }
}
