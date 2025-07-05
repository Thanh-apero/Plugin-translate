package com.xmltranslator.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

/**
 * Service for handling XML processing operations
 * 
 * @author Thanh Nguyen <thanhnguyen6702@gmail.com>
 */
@Service
class XmlProcessor {
    
    private val stringFilter = service<StringFilter>()
    
    /**
     * Parse XML content và trả về Document
     */
    fun parseXml(xmlContent: String): Document {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(java.io.ByteArrayInputStream(xmlContent.toByteArray()))
    }
    
    /**
     * Extract string elements từ XML document và filter theo rules
     */
    fun extractStringElements(document: Document): List<Pair<String, String>> {
        val stringElements = mutableListOf<Pair<String, String>>()
        val excludedElements = mutableListOf<String>()
        val nonTranslatableElements = mutableListOf<String>()
        val nodeList = document.getElementsByTagName("string")
        
        for (i in 0 until nodeList.length) {
            val element = nodeList.item(i) as Element
            val translatable = element.getAttribute("translatable")
            
            if (translatable.isNotEmpty() && translatable.lowercase() == "false") {
                val name = element.getAttribute("name")
                if (name.isNotEmpty()) {
                    nonTranslatableElements.add(name)
                }
            } else {
                val name = element.getAttribute("name")
                val text = element.textContent
                
                if (name.isNotEmpty() && text.isNotEmpty()) {
                    if (stringFilter.shouldExcludeString(name)) {
                        excludedElements.add(name)
                    } else {
                        stringElements.add(name to text)
                    }
                }
            }
        }
        
        // Summary log
        println("📊 Phân tích XML: ${stringElements.size} strings để dịch")
        if (excludedElements.isNotEmpty()) {
            println("🚫 Bỏ qua ${excludedElements.size} strings: ${excludedElements.take(3).joinToString(", ")}${if (excludedElements.size > 3) "..." else ""}")
        }
        if (nonTranslatableElements.isNotEmpty()) {
            println("⏭️ Bỏ qua ${nonTranslatableElements.size} strings có translatable=\"false\"")
        }
        
        return stringElements
    }
    
    /**
     * Save translated strings thành XML file
     */
    fun saveTranslatedXml(translations: List<Pair<String, String>>, outputFile: File) {
        val xmlContent = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<resources>")
            
            translations.forEach { (name, text) ->
                val escapedText = escapeXmlText(text)
                appendLine("    <string name=\"$name\">$escapedText</string>")
            }
            
            appendLine("</resources>")
        }
        
        outputFile.writeText(xmlContent)
    }
    
    /**
     * Add hoặc update một string trong XML file
     */
    fun addOrUpdateStringInXml(xmlFile: File, stringName: String, text: String) {
        val content = if (xmlFile.exists()) {
            xmlFile.readText()
        } else {
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
</resources>"""
        }
        
        val escapedText = escapeXmlText(text)
        val stringElement = "    <string name=\"$stringName\">$escapedText</string>"
        
        if (content.contains("name=\"$stringName\"")) {
            // Update existing string - Fix for "Illegal group reference" error
            val regex = """    <string name="$stringName"[^>]*>.*?</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            // Use literal replacement to avoid issues with $ characters in replacement string
            val updatedContent = regex.replace(content) { stringElement }
            xmlFile.writeText(updatedContent)
        } else {
            // Add new string with proper formatting
            val insertPosition = content.lastIndexOf("</resources>")
            if (insertPosition != -1) {
                val beforeResources = content.substring(0, insertPosition).trimEnd()
                val afterResources = content.substring(insertPosition)
                
                val newContent = when {
                    beforeResources.trim().endsWith("<resources>") -> {
                        // First string in empty resources
                        "$beforeResources\n$stringElement\n$afterResources"
                    }
                    beforeResources.trim().endsWith("</string>") -> {
                        // Adding after existing strings
                        "$beforeResources\n$stringElement\n$afterResources"
                    }
                    else -> {
                        // Fallback case
                        "$beforeResources\n$stringElement\n$afterResources"
                    }
                }
                
                xmlFile.writeText(newContent)
            }
        }
    }
    
    /**
     * Escape XML text để tránh parsing errors
     */
    private fun escapeXmlText(text: String): String {
        // Preserve formatting characters as escape sequences in XML
        // This ensures \n characters are kept as \n in the output XML
        return text
            .replace(Regex("&(?![a-zA-Z0-9#]+;)"), "&amp;") // Only escape unescaped ampersands
            .replace("'", "&apos;") // Escape single quotes
            .replace("\n", "\\n") // Convert actual newlines to \n escape sequence
            .replace("\t", "\\t") // Convert actual tabs to \t escape sequence
            .replace("\r", "\\r") // Convert actual carriage returns to \r escape sequence
        // Note: < > and " are preserved by AI via prompt instructions for HTML formatting
    }
} 