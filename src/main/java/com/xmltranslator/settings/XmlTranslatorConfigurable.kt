package com.xmltranslator.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBPasswordField
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class XmlTranslatorConfigurable : Configurable {
    
    private var settingsPanel: JPanel? = null
    private lateinit var useDefaultKeysCheckbox: JBCheckBox
    private lateinit var apiKeysTextArea: JBTextArea
    private lateinit var showKeysCheckbox: JBCheckBox
    
    override fun getDisplayName(): String = "XML Translator"
    
    override fun createComponent(): JComponent? {
        if (settingsPanel == null) {
            settingsPanel = createSettingsPanel()
        }
        return settingsPanel
    }
    
    private fun createSettingsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        // Title with icon
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = Insets(15, 15, 25, 15)
        val titleLabel = JBLabel("🌍 XML Translator Settings")
        titleLabel.font = titleLabel.font.deriveFont(16f)
        panel.add(titleLabel, gbc)
        
        // Use default keys checkbox with icon
        gbc.gridy = 1
        gbc.gridwidth = 2
        gbc.insets = Insets(5, 15, 5, 15)
        useDefaultKeysCheckbox = JBCheckBox("✅ Use default API keys (recommended)")
        useDefaultKeysCheckbox.addActionListener { 
            updateUIState()
        }
        panel.add(useDefaultKeysCheckbox, gbc)
        
        // Info label with better styling
        gbc.gridy = 2
        gbc.gridwidth = 2
        gbc.insets = Insets(0, 15, 25, 15)
        val infoLabel = JBLabel("<html><i>💡 Plugin bao gồm API keys sẵn có để sử dụng ngay. Custom keys bên dưới sẽ được thêm vào kèm với default keys.</i></html>")
        panel.add(infoLabel, gbc)
        
        // API keys section
        gbc.gridy = 3
        gbc.gridwidth = 1
        gbc.insets = Insets(5, 15, 5, 15)
        val keysLabel = JBLabel("🔑 Additional API Keys (one per line):")
        panel.add(keysLabel, gbc)
        
        // Show keys checkbox
        gbc.gridx = 1
        gbc.anchor = GridBagConstraints.EAST
        showKeysCheckbox = JBCheckBox("👁️ Show keys")
        showKeysCheckbox.addActionListener { updateKeysVisibility() }
        panel.add(showKeysCheckbox, gbc)
        
        // API keys text area with password-style hiding
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.insets = Insets(5, 15, 20, 15)
        gbc.anchor = GridBagConstraints.WEST
        
        apiKeysTextArea = JBTextArea()
        apiKeysTextArea.rows = 8
        apiKeysTextArea.toolTipText = "Enter your Google Generative AI API keys, one per line"
        apiKeysTextArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        
        val scrollPane = JScrollPane(apiKeysTextArea)
        scrollPane.border = BorderFactory.createLoweredBevelBorder()
        panel.add(scrollPane, gbc)
        
        // Instructions with better formatting
        gbc.gridy = 5
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        gbc.insets = Insets(5, 15, 10, 15)
        val instructionsLabel = JBLabel("<html>📚 Get your API key from: <b>https://aistudio.google.com/app/apikey</b></html>")
        panel.add(instructionsLabel, gbc)
        
        // Security note
        gbc.gridy = 6
        val securityLabel = JBLabel("<html><i>🔒 API keys are stored securely in your IDE settings</i></html>")
        panel.add(securityLabel, gbc)
        
        // Load settings
        loadSettings()
        
        return panel
    }
    
    private fun updateUIState() {
        val enabled = !useDefaultKeysCheckbox.isSelected
        apiKeysTextArea.isEnabled = enabled
        showKeysCheckbox.isEnabled = enabled
        
        if (!enabled) {
            showKeysCheckbox.isSelected = false
            updateKeysVisibility()
        }
    }
    
    private fun updateKeysVisibility() {
        if (showKeysCheckbox.isSelected) {
            // Show keys normally
            apiKeysTextArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
            apiKeysTextArea.foreground = java.awt.Color.BLACK
        } else {
            // Hide keys with dots/stars
            val text = apiKeysTextArea.text
            if (text.isNotEmpty()) {
                val hiddenText = text.lines().joinToString("\n") { line ->
                    if (line.trim().isNotEmpty()) "•".repeat(minOf(line.length, 40)) else ""
                }
                val originalText = apiKeysTextArea.text
                
                // Temporarily show hidden version
                SwingUtilities.invokeLater {
                    apiKeysTextArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 14)
                    apiKeysTextArea.foreground = java.awt.Color.GRAY
                    apiKeysTextArea.text = hiddenText
                    
                    // Store original text for saving
                    apiKeysTextArea.putClientProperty("originalText", originalText)
                }
            }
        }
    }
    
    private fun loadSettings() {
        val settings = XmlTranslatorSettings.getInstance()
        
        useDefaultKeysCheckbox.isSelected = settings.useDefaultKeys
        
        // Load API keys
        val keysText = settings.apiKeys.joinToString("\n")
        apiKeysTextArea.text = keysText
        
        updateUIState()
        updateKeysVisibility()
    }
    
    override fun isModified(): Boolean {
        val settings = XmlTranslatorSettings.getInstance()
        
        if (useDefaultKeysCheckbox.isSelected != settings.useDefaultKeys) {
            return true
        }
        
        // Get original text if keys are hidden
        val currentText = if (!showKeysCheckbox.isSelected && !showKeysCheckbox.isEnabled) {
            apiKeysTextArea.getClientProperty("originalText") as? String ?: apiKeysTextArea.text
        } else {
            apiKeysTextArea.text
        }
        
        val currentKeys = currentText.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        
        return currentKeys != settings.apiKeys
    }
    
    override fun apply() {
        val settings = XmlTranslatorSettings.getInstance()
        
        settings.useDefaultKeys = useDefaultKeysCheckbox.isSelected
        
        // Save API keys - get original text if keys are hidden
        settings.clearApiKeys()
        val currentText = if (!showKeysCheckbox.isSelected && !showKeysCheckbox.isEnabled) {
            apiKeysTextArea.getClientProperty("originalText") as? String ?: apiKeysTextArea.text
        } else {
            apiKeysTextArea.text
        }
        
        val keys = currentText.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        keys.forEach { key ->
            if (key.length >= 30 && !key.contains("•")) { // Don't save hidden keys
                settings.addApiKey(key)
            }
        }
        
        Messages.showInfoMessage(
            "✅ Settings saved successfully! Changes will take effect for new translations.",
            "Settings Saved"
        )
    }
    
    override fun reset() {
        loadSettings()
    }
    
    override fun disposeUIResources() {
        settingsPanel = null
    }
}