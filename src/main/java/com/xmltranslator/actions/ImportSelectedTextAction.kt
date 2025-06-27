package com.xmltranslator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.xmltranslator.ui.XmlTranslatorPanel

class ImportSelectedTextAction : AnAction("🔄 Import to XML Translator") {
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        
        // Show action only when:
        // 1. We have a project and editor
        // 2. File is XML
        // 3. There's selected text
        val isVisible = project != null && 
                       editor != null && 
                       file != null &&
                       file.name.endsWith(".xml") &&
                       editor.selectionModel.hasSelection()
        
        e.presentation.isEnabledAndVisible = isVisible
        
        if (isVisible && editor != null) {
            val selectedText = editor.selectionModel.selectedText ?: ""
            val hasStringElements = selectedText.contains("<string")
            
            e.presentation.text = if (hasStringElements) {
                "🔄 Import Selected Strings to Translator"
            } else {
                "🔄 Import Selection to Translator"
            }
            
            e.presentation.description = if (hasStringElements) {
                "Import selected XML string elements to XML Translator plugin"
            } else {
                "Import selected text to XML Translator plugin"
            }
        }
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText
        
        if (selectedText.isNullOrBlank()) {
            Messages.showWarningDialog(
                project,
                "No text selected. Please select XML strings to import.",
                "No Selection"
            )
            return
        }
        
        try {
            // Open/show the XML Translator tool window
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow("XML Translator")
            
            if (toolWindow != null) {
                toolWindow.show()
                
                // Wait a bit for tool window to fully initialize
                javax.swing.SwingUtilities.invokeLater {
                    try {
                        // Get the tool window content and import the selected text
                        val contentManager = toolWindow.contentManager
                        val content = contentManager.getContent(0)
                        
                        if (content != null) {
                            val translatorPanel = findTranslatorPanel(content.component)
                            if (translatorPanel != null) {
                                // Import the selected text
                                translatorPanel.importSelectedText(selectedText)
                                
                                // Show success message
                                val stringCount = countStringElements(selectedText)
                                javax.swing.SwingUtilities.invokeLater {
                                    if (stringCount > 0) {
                                        Messages.showInfoMessage(
                                            project,
                                            "Successfully imported $stringCount string elements to XML Translator!\n\nText has been added to bulk input and parsed automatically.",
                                            "Import Successful"
                                        )
                                    } else {
                                        Messages.showInfoMessage(
                                            project,
                                            "Selected text imported to XML Translator bulk input.\n\nClick 'Parse XML Strings' to add to translation queue.",
                                            "Import Successful"
                                        )
                                    }
                                }
                            } else {
                                Messages.showErrorDialog(
                                    project,
                                    "Could not find XML Translator panel. The tool window may still be loading.\n\nPlease wait a moment and try again.",
                                    "Import Failed"
                                )
                            }
                        } else {
                            Messages.showErrorDialog(
                                project,
                                "XML Translator tool window content not found.\n\nPlease ensure the plugin is properly installed.",
                                "Import Failed"
                            )
                        }
                    } catch (e: Exception) {
                        Messages.showErrorDialog(
                            project,
                            "Error during import: ${e.message}\n\nPlease try again or check the IDE logs.",
                            "Import Error"
                        )
                    }
                }
            } else {
                Messages.showErrorDialog(
                    project,
                    "XML Translator tool window not found. Please ensure the plugin is properly installed.",
                    "Tool Window Not Found"
                )
            }
            
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to import selected text: ${e.message}",
                "Import Error"
            )
        }
    }
    
    private fun findTranslatorPanel(component: java.awt.Component?): XmlTranslatorPanel? {
        if (component == null) {
            println("DEBUG: Component is null")
            return null
        }
        
        println("DEBUG: Checking component: ${component.javaClass.simpleName}")
        
        if (component is XmlTranslatorPanel) {
            println("DEBUG: Found XmlTranslatorPanel!")
            return component
        }
        
        if (component is java.awt.Container) {
            println("DEBUG: Searching in container with ${component.componentCount} children")
            for (i in 0 until component.componentCount) {
                val child = component.getComponent(i)
                println("DEBUG: Child $i: ${child.javaClass.simpleName}")
                val result = findTranslatorPanel(child)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
    
    private fun countStringElements(text: String): Int {
        val pattern = """<string\s+name="[^"]+"\s*[^>]*>.*?</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(text).count()
    }
} 