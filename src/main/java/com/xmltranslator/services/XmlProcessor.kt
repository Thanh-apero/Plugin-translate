package com.xmltranslator.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

data class StringElement(
    val name: String,
    val text: String,
    val translatable: String? = null,
    val otherAttributes: Map<String, String> = emptyMap()
) {
    val isTranslatable: Boolean
        get() = translatable == null || translatable.lowercase() != "false"
        
    fun toXmlString(): String {
        val finalText = if (needsEscaping(text)) escapeXmlText(text) else text
        val attributes = mutableListOf<String>()
        
        if (translatable != null) {
            attributes.add("translatable=\"$translatable\"")
        }
        
        otherAttributes.forEach { (key, value) ->
            attributes.add("$key=\"$value\"")
        }
        
        val attributeString = if (attributes.isNotEmpty()) " ${attributes.joinToString(" ")}" else ""
        return "    <string name=\"$name\"$attributeString>$finalText</string>"
    }
    
    companion object {
        fun needsEscaping(text: String): Boolean {
            return text.contains("'") || text.contains("\"") || 
                   text.contains("&") || text.contains("\n") || 
                   text.contains("\t") || text.contains("\r")
        }
        
        fun escapeXmlText(text: String): String {
            return text
                .replace(Regex("&(?![a-zA-Z0-9#]+;)"), "&amp;")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
        }
    }
}

@Service
class XmlProcessor {
    
    private val stringFilter = service<StringFilter>()
    
    fun parseXml(xmlContent: String): Document {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(java.io.ByteArrayInputStream(xmlContent.toByteArray()))
    }
    
    fun extractStringElements(document: Document): List<Pair<String, String>> {
        val stringElements = mutableListOf<Pair<String, String>>()
        val nodeList = document.getElementsByTagName("string")
        
        for (i in 0 until nodeList.length) {
            val element = nodeList.item(i) as Element
            val translatable = element.getAttribute("translatable")
            
            if (translatable.isNotEmpty() && translatable.lowercase() == "false") {
                continue
            }
            
            val name = element.getAttribute("name")
            val text = element.textContent
            
            if (name.isNotEmpty() && text.isNotEmpty() && !stringFilter.shouldExcludeString(name)) {
                stringElements.add(name to text)
            }
        }
        
        return stringElements
    }
    
    fun saveTranslatedXml(translations: List<Pair<String, String>>, outputFile: File) {
        val xmlContent = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<resources>")
            
            translations.forEach { (name, text) ->
                val finalText = if (StringElement.needsEscaping(text)) StringElement.escapeXmlText(text) else text
                appendLine("    <string name=\"$name\">$finalText</string>")
            }
            
            appendLine("</resources>")
        }
        
        outputFile.writeText(xmlContent)
    }

    fun mergeTranslatedXml(translations: List<Pair<String, String>>, outputFile: File) {
        val existingStrings = if (outputFile.exists()) {
            try {
                val existingContent = outputFile.readText()
                val existingDocument = parseXml(existingContent)
                extractAllStringElementsWithAttributes(existingDocument)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        
        val newTranslationsMap = translations.toMap()
        val existingStringsMap = existingStrings.associateBy { it.name }
        val finalStringElements = mutableMapOf<String, StringElement>()
        
        existingStringsMap.forEach { (name, element) ->
            finalStringElements[name] = element
        }
        
        newTranslationsMap.forEach { (name, newText) ->
            val existing = existingStringsMap[name]
            if (existing != null && !existing.isTranslatable) {
                return@forEach
            } else {
                finalStringElements[name] = StringElement(name, newText)
            }
        }
        
        val sortedElements = finalStringElements.values.sortedBy { it.name }
        
        val xmlContent = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<resources>")
            
            sortedElements.forEach { element ->
                appendLine(element.toXmlString())
            }
            
            appendLine("</resources>")
        }
        
        outputFile.writeText(xmlContent)
    }

    private fun extractAllStringElementsWithAttributes(document: Document): List<StringElement> {
        val stringElements = mutableListOf<StringElement>()
        val nodeList = document.getElementsByTagName("string")
        
        for (i in 0 until nodeList.length) {
            val element = nodeList.item(i) as Element
            val name = element.getAttribute("name")
            val text = element.textContent
            
            if (name.isNotEmpty() && text.isNotEmpty()) {
                val translatable = element.getAttribute("translatable").takeIf { it.isNotEmpty() }
                
                val otherAttributes = mutableMapOf<String, String>()
                val attributes = element.attributes
                for (j in 0 until attributes.length) {
                    val attr = attributes.item(j)
                    if (attr.nodeName != "name" && attr.nodeName != "translatable") {
                        otherAttributes[attr.nodeName] = attr.nodeValue
                    }
                }
                
                stringElements.add(StringElement(name, text, translatable, otherAttributes))
            }
        }
        
        return stringElements
    }
    
    fun addOrUpdateStringInXml(xmlFile: File, stringName: String, text: String) {
        val content = if (xmlFile.exists()) {
            xmlFile.readText()
        } else {
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
</resources>"""
        }
        
        val existingElements = if (xmlFile.exists() && content.contains("<string")) {
            try {
                val document = parseXml(content)
                extractAllStringElementsWithAttributes(document)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        
        val existingElement = existingElements.find { it.name == stringName }
        
        if (existingElement != null) {
            val updatedElement = if (existingElement.isTranslatable) {
                existingElement.copy(text = text)
            } else {
                existingElement
            }
            
            val newStringLine = updatedElement.toXmlString()
            val regex = """    <string name="$stringName"[^>]*>.*?</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val updatedContent = regex.replace(content) { newStringLine }
            xmlFile.writeText(updatedContent)
        } else {
            val newElement = StringElement(stringName, text)
            val stringElement = newElement.toXmlString()
            
            val insertPosition = content.lastIndexOf("</resources>")
            if (insertPosition != -1) {
                val beforeResources = content.substring(0, insertPosition).trimEnd()
                val afterResources = content.substring(insertPosition)
                
                val newContent = when {
                    beforeResources.trim().endsWith("<resources>") -> {
                        "$beforeResources\n$stringElement\n$afterResources"
                    }
                    beforeResources.trim().endsWith("</string>") -> {
                        "$beforeResources\n$stringElement\n$afterResources"
                    }
                    else -> {
                        "$beforeResources\n$stringElement\n$afterResources"
                    }
                }
                
                xmlFile.writeText(newContent)
            }
        }
    }
    
    companion object {
        fun escapeXmlText(text: String): String {
            return text
                .replace(Regex("&(?![a-zA-Z0-9#]+;)"), "&amp;")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
        }
        
        fun unescapeXmlText(text: String): String {
            return text
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r") 
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("&amp;", "&")
        }
    }
} 