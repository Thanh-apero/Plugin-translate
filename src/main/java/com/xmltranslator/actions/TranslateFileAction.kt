package com.xmltranslator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.xmltranslator.services.TranslationService

class TranslateFileAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        if (file.extension != "xml") {
            Messages.showWarningDialog(project, "Please select an XML file!", "Warning")
            return
        }
        
        // Open the tool window
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("XML Translator")
        
        if (toolWindow != null) {
            toolWindow.activate(null)
            
            // Pre-fill the input file field
            // This would require accessing the UI components, which we can do through the content
            val content = toolWindow.contentManager.contents.firstOrNull()
            // For now, just show a message that the tool window is opened
            Messages.showInfoMessage(
                project,
                "XML Translator opened. Please use the tool window to translate '${file.name}'",
                "XML Translator"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        
        // Show action only for XML files in Android projects
        e.presentation.isVisible = project != null && 
                                  file != null && 
                                  file.extension == "xml" &&
                                  (file.name.contains("strings") || file.path.contains("res"))
    }
}