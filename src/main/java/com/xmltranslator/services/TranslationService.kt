package com.xmltranslator.services

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.xmltranslator.settings.XmlTranslatorSettings
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.PROJECT)
class TranslationService(private val project: Project) {
    
    private val gson = Gson()
    private var currentKeyIndex = AtomicInteger(0)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private fun getApiKeys(): List<String> {
        val settings = XmlTranslatorSettings.getInstance()
        val keys = settings.getValidApiKeys()
        
        if (keys.isEmpty()) {
            throw Exception("No API keys configured. Please add API keys in Settings > Tools > XML Translator")
        }
        
        return keys
    }
    
    private fun getNextApiKey(): String {
        val keys = getApiKeys()
        val index = currentKeyIndex.getAndUpdate { (it + 1) % keys.size }
        return keys[index]
    }

    // Data classes for Google AI API
    data class GeminiRequest(
        val contents: List<Content>
    )
    
    data class Content(
        val parts: List<Part>
    )
    
    data class Part(
        val text: String
    )
    
    data class GeminiResponse(
        val candidates: List<Candidate>?
    )
    
    data class Candidate(
        val content: Content?
    )
    
    data class TranslationRequest(
        val source_language: String,
        val target_language: String,
        val strings: List<StringItem>
    )
    
    data class StringItem(
        val id: Int,
        val text: String,
        val name: String? = null
    )
    
    data class TranslationResponse(
        val translations: List<TranslatedItem>
    )
    
    data class TranslatedItem(
        val id: Int,
        val text: String
    )

    fun translateText(text: String, sourceLang: String, targetLang: String): String {
        return try {
            println("DEBUG: Translating '$text' from $sourceLang to $targetLang")
            
            val request = TranslationRequest(
                source_language = sourceLang,
                target_language = targetLang,
                strings = listOf(StringItem(1, text))
            )
            
            val response = makeTranslationRequest(request)
            response.translations.firstOrNull()?.text ?: text
        } catch (e: Exception) {
            println("Translation error: ${e.message}")
            text // Return original text on error
        }
    }
    
    fun translateStrings(
        strings: List<Pair<String, String>>, // name to text pairs
        sourceLang: String,
        targetLang: String,
        onProgress: (String) -> Unit = {}
    ): List<Pair<String, String>> {
        return try {
            onProgress("Preparing translation request...")
            
            val request = TranslationRequest(
                source_language = sourceLang,
                target_language = targetLang,
                strings = strings.mapIndexed { index, (name, text) ->
                    StringItem(index + 1, text, name)
                }
            )
            
            onProgress("Sending request to translation API...")
            val response = makeTranslationRequest(request)
            
            onProgress("Processing translation results...")
            strings.zip(response.translations).map { (original, translated) ->
                original.first to translated.text
            }
        } catch (e: Exception) {
            onProgress("Translation failed: ${e.message}")
            strings // Return original strings on error
        }
    }
    
    private fun makeTranslationRequest(request: TranslationRequest): TranslationResponse {
        val apiKey = getNextApiKey()
        println("DEBUG: Making translation request to Google AI API")
        println("DEBUG: Using API key: ${apiKey.substring(0, 10)}...")
        
        val prompt = buildPrompt(request)
        
        try {
            val response = callGeminiAPI(apiKey, prompt)
            return parseGeminiResponse(response)
        } catch (e: Exception) {
            // Retry with different API key if available
            println("First attempt failed: ${e.message}")
            
            val keys = getApiKeys()
            if (keys.size > 1) {
                println("Retrying with different API key...")
                Thread.sleep(4000) // Wait before retry
                
                val retryApiKey = getNextApiKey()
                val retryResponse = callGeminiAPI(retryApiKey, prompt)
                return parseGeminiResponse(retryResponse)
            } else {
                throw e
            }
        }
    }
    
    private fun callGeminiAPI(apiKey: String, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
        val geminiRequest = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(prompt))
                )
            )
        )
        
        val requestBody = gson.toJson(geminiRequest)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(10))
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw Exception("API request failed with status ${response.statusCode()}: ${response.body()}")
        }
        
        val geminiResponse = gson.fromJson(response.body(), GeminiResponse::class.java)
        val generatedText = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No content in API response")
        
        return generatedText
    }
    
    private fun buildPrompt(request: TranslationRequest): String {
        // Use the same examples as Python version - complete set
        val examples = """
input: {
  "source_language": "en",
  "target_language": "ko",
  "strings": [
    {"id": 1, "text": "Your document has been saved successfully."},
    {"id": 2, "text": "Please check your internet connection and try again."},
    {"id": 3, "text": "This feature is not available in the free version."}
  ]
}
output: {
  "translations": [
    {"id": 1, "text": "문서가 성공적으로 저장되었습니다."},
    {"id": 2, "text": "인터넷 연결을 확인하고 다시 시도하세요."},
    {"id": 3, "text": "이 기능은 무료 버전에서 사용할 수 없습니다."}
  ]
}

input: {
  "source_language": "en",
  "target_language": "fr",
  "strings": [
    {"id": 1, "text": "QR & Barcode Scanner"}
  ]
}
output: {
  "translations": [
    {"id": 1, "text": "Scanner de QR & code-barres"}
  ]
}

input: {
  "source_language": "en",
  "target_language": "ko",
  "strings": [
    {"id": 1, "text": "Don't forget to write tag #XpertScan"},
    {"id": 2, "text": "Can't find an app that supports this action"}
  ]
}
output: {
  "translations": [
    {"id": 1, "text": "#XpertScan 태그를 작성하는 것을 잊지 마세요"},
    {"id": 2, "text": "이 작업을 지원하는 앱을 찾을 수 없습니다"}
  ]
}

input: {
  "source_language": "en",
  "target_language": "zh",
  "strings": [
    {"id": 1, "text": "Export as PDF failed"},
    {"id": 2, "text": "Share as PDF failed"},
    {"id": 3, "text": "Export to gallery failed"},
    {"id": 4, "text": "Share as picture failed"},
    {"id": 5, "text": "Print PDF failed"},
    {"id": 6, "text": "Insert password"}
  ]
}
output: {
  "translations": [
    {"id": 1, "text": "导出为PDF失败"},
    {"id": 2, "text": "分享为PDF失败"},
    {"id": 3, "text": "导出到图库失败"},
    {"id": 4, "text": "分享为图片失败"},
    {"id": 5, "text": "打印PDF失败"},
    {"id": 6, "text": "请输入密码"}
  ]
}

input: {
  "source_language": "en",
  "target_language": "vi",
  "strings": [
    {"id": 1, "text": "Export as PDF failed"},
    {"id": 2, "text": "Share as PDF failed"},
    {"id": 3, "text": "Export to gallery failed"},
    {"id": 4, "text": "Share as picture failed"},
    {"id": 5, "text": "Print PDF failed"},
    {"id": 6, "text": "Insert password"}
  ]
}
output: {
  "translations": [
    {"id": 1, "text": "Xuất PDF thất bại"},
    {"id": 2, "text": "Chia sẻ dưới dạng PDF thất bại"},
    {"id": 3, "text": "Xuất vào thư viện thất bại"},
    {"id": 4, "text": "Chia sẻ dưới dạng hình ảnh thất bại"},
    {"id": 5, "text": "In PDF thất bại"},
    {"id": 6, "text": "Nhập mật khẩu"}
  ]
}

input: {
  "source_language": "en",
  "target_language": "it",
  "strings": [
    {"id": 1, "text": "Export as PDF failed"},
    {"id": 2, "text": "Share as PDF failed"},
    {"id": 3, "text": "Export to gallery failed"},
    {"id": 4, "text": "Share as picture failed"},
    {"id": 5, "text": "Print PDF failed"},
    {"id": 6, "text": "Insert password"}
  ]
}
output: {
  "translations": [
    {"id": 1, "text": "Esportazione come PDF fallita"},
    {"id": 2, "text": "Condivisione come PDF fallita"},
    {"id": 3, "text": "Esportazione nella galleria fallita"},
    {"id": 4, "text": "Condivisione come immagine fallita"},
    {"id": 5, "text": "Stampa PDF fallita"},
    {"id": 6, "text": "Inserisci la password"}
  ]
}

input: {
  "source_language": "en",
  "target_language": "vi",
  "strings": [
    {"id": 1, "text": "Enable <b>Notifications</b> for continuous using when the app is closed."}
  ]
}
output: {
  "translations": [
    {"id": 1, "text": "Bật <b>Thông báo</b> của ứng dụng để tiếp tục sử dụng khi ứng dụng bị đóng."}
  ]
}

input: {
  "source_language": "en",
  "target_language": "vi",
  "strings": [
    {
      "id": 1,
      "text": "Dear User,\\n\\nThank you for using our service.\\r\\nPlease note the following:\\n\\t- Your subscription expires soon.\\n\\t- Renew to continue enjoying premium features.\\n\\nBest regards,\\nThe Support Team"
    },
    {
      "id": 2,
      "text": "Error!\\r\\n\\tSomething went wrong while processing your request.\\nPlease try again later or contact support."
    }
  ]
}
output: {
  "translations": [
    {
      "id": 1,
      "text": "Kính gửi người dùng,\\n\\nCảm ơn bạn đã sử dụng dịch vụ của chúng tôi.\\r\\nVui lòng lưu ý:\\n\\t- Gói đăng ký của bạn sắp hết hạn.\\n\\t- Gia hạn để tiếp tục tận hưởng các tính năng cao cấp.\\n\\nTrân trọng,\\nĐội ngũ Hỗ trợ"
    },
    {
      "id": 2,
      "text": "Lỗi!\\r\\n\\tĐã xảy ra sự cố khi xử lý yêu cầu của bạn.\\nVui lòng thử lại sau hoặc liên hệ bộ phận hỗ trợ."
    }
  ]
}
        """.trimIndent()
        
        val requestJson = gson.toJson(request)
        return "$examples\n\ninput 2: $requestJson\noutput 2: "
    }
    
    private fun parseGeminiResponse(responseText: String): TranslationResponse {
        try {
            // Clean the response text - same as Python version
            val cleanContent = responseText
                .replace("```json\n", "")
                .replace("\n```", "")
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            println("DEBUG: Clean response: $cleanContent")
            
            return gson.fromJson(cleanContent, TranslationResponse::class.java)
        } catch (e: Exception) {
            println("ERROR: Failed to parse response: ${e.message}")
            println("Response text: $responseText")
            throw Exception("Failed to parse translation response: ${e.message}")
        }
    }
    
    fun translateXmlFile(
        inputFile: File,
        outputDir: File,
        targetLanguages: List<String>,
        onProgress: (String) -> Unit = {}
    ) {
        onProgress("Reading XML file...")
        
        val xmlContent = inputFile.readText()
        val document = parseXml(xmlContent)
        val stringElements = extractStringElements(document)
        
        if (stringElements.isEmpty()) {
            onProgress("No translatable strings found in XML file")
            return
        }
        
        onProgress("Found ${stringElements.size} strings to translate")
        
        for (lang in targetLanguages) {
            try {
                onProgress("Translating to $lang...")
                
                val translations = translateStrings(
                    stringElements,
                    "en",
                    lang
                ) { progress ->
                    onProgress("$lang: $progress")
                }
                
                val outputFile = File(outputDir, "values-$lang/strings.xml")
                outputFile.parentFile.mkdirs()
                
                saveTranslatedXml(translations, outputFile)
                onProgress("Saved translations for $lang")
                
                // Wait between translations to avoid API limits
                Thread.sleep(2000)
                
            } catch (e: Exception) {
                onProgress("Error translating to $lang: ${e.message}")
            }
        }
        
        onProgress("Translation completed!")
    }
    
    private fun parseXml(xmlContent: String): Document {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(java.io.ByteArrayInputStream(xmlContent.toByteArray()))
    }
    
    private fun extractStringElements(document: Document): List<Pair<String, String>> {
        val stringElements = mutableListOf<Pair<String, String>>()
        val nodeList = document.getElementsByTagName("string")
        
        for (i in 0 until nodeList.length) {
            val element = nodeList.item(i) as Element
            val translatable = element.getAttribute("translatable")
            
            if (translatable.isEmpty() || translatable.lowercase() != "false") {
                val name = element.getAttribute("name")
                val text = element.textContent
                
                if (name.isNotEmpty() && text.isNotEmpty()) {
                    stringElements.add(name to text)
                }
            }
        }
        
        return stringElements
    }
    
    private fun saveTranslatedXml(translations: List<Pair<String, String>>, outputFile: File) {
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
    
    private fun escapeXmlText(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("'", "&apos;")
            .replace("\"", "&quot;")
    }
    
    private fun addOrUpdateStringInXml(xmlFile: File, stringName: String, text: String) {
        // Enhanced logic with proper line break detection
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
            // Update existing string
            val regex = """    <string name="$stringName"[^>]*>.*?</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val updatedContent = content.replace(regex, stringElement)
            xmlFile.writeText(updatedContent)
        } else {
            // Add new string with proper formatting
            val insertPosition = content.lastIndexOf("</resources>")
            if (insertPosition != -1) {
                val beforeResources = content.substring(0, insertPosition)
                val afterResources = content.substring(insertPosition)
                
                // Check if we need a newline before </resources>
                val needsNewlineBeforeClosing = !beforeResources.endsWith("\n")
                val needsNewlineAfterString = !beforeResources.trimEnd().endsWith("</string>")
                
                val newContent = when {
                    // First string in empty resources
                    beforeResources.trim().endsWith("<resources>") -> {
                        beforeResources + "\n$stringElement\n$afterResources"
                    }
                    // Add after existing strings
                    beforeResources.trimEnd().endsWith("</string>") -> {
                        val lineBreak = if (needsNewlineBeforeClosing) "\n" else ""
                        beforeResources + "\n$stringElement$lineBreak$afterResources"
                    }
                    // Other cases - ensure proper spacing
                    else -> {
                        val lineBreak = if (needsNewlineBeforeClosing) "\n" else ""
                        beforeResources + "\n$stringElement$lineBreak$afterResources"
                    }
                }
                
                xmlFile.writeText(newContent)
            }
        }
    }
    
    fun addStringToXmlFiles(
        stringName: String,
        originalText: String,
        resourceDir: File,
        targetFolders: Array<String>,
        onProgress: (String) -> Unit = {}
    ) {
        try {
            for (folder in targetFolders) {
                val translatedText = if (folder == "values") {
                    originalText
                } else {
                    val lang = folder.removePrefix("values-")
                    onProgress("Translating to $lang...")
                    translateText(originalText, "en", lang)
                }
                
                val xmlFile = File(resourceDir, "$folder/strings.xml")
                xmlFile.parentFile.mkdirs()
                
                addOrUpdateStringInXml(xmlFile, stringName, translatedText)
                onProgress("Added string to $folder")
            }
            
            onProgress("String added to all selected folders!")
            
        } catch (e: Exception) {
            onProgress("Error: ${e.message}")
        }
    }
    
    fun addBatchStringsToXmlFiles(
        stringItems: List<Pair<String, String>>, // (name, text) pairs
        resourceDir: File,
        targetFolders: Array<String>,
        onProgress: (String) -> Unit = {},
        batchSize: Int = 50
    ) {
        try {
            val totalStrings = stringItems.size
            onProgress("Starting batch translation for $totalStrings strings...")
            
            // Group strings into batches
            val batches = stringItems.chunked(batchSize)
            onProgress("Split into ${batches.size} batches of up to $batchSize strings each")
            
            for (folder in targetFolders) {
                val lang = folder.removePrefix("values-")
                onProgress("Processing folder: $folder")
                
                if (folder == "values") {
                    // Original language - no translation needed
                    for ((stringName, originalText) in stringItems) {
                        val xmlFile = File(resourceDir, "$folder/strings.xml")
                        xmlFile.parentFile.mkdirs()
                        addOrUpdateStringInXml(xmlFile, stringName, originalText)
                    }
                    onProgress("Added $totalStrings strings to $folder (original)")
                } else {
                    // Target language - needs translation
                    var processedInLang = 0
                    for ((batchIndex, batch) in batches.withIndex()) {
                        onProgress("Translating batch ${batchIndex + 1}/${batches.size} to $lang (${batch.size} strings)...")
                        
                        // Translate entire batch at once
                        val translatedBatch = translateStrings(
                            batch,
                            "en",
                            lang
                        ) { progress ->
                            onProgress("$lang batch ${batchIndex + 1}: $progress")
                        }
                        
                        // Add translated strings to XML
                        for ((originalPair, translatedPair) in batch.zip(translatedBatch)) {
                            val stringName = originalPair.first
                            val translatedText = translatedPair.second
                            
                            val xmlFile = File(resourceDir, "$folder/strings.xml")
                            xmlFile.parentFile.mkdirs()
                            addOrUpdateStringInXml(xmlFile, stringName, translatedText)
                            processedInLang++
                        }
                        
                        onProgress("Added batch ${batchIndex + 1} to $folder ($processedInLang/$totalStrings strings)")
                        
                        // Add delay between batches to respect API limits
                        if (batchIndex < batches.size - 1) {
                            Thread.sleep(2000)
                        }
                    }
                    onProgress("Completed all batches for $folder ($processedInLang strings)")
                }
                
                // Add delay between languages
                Thread.sleep(1000)
            }
            
            onProgress("All $totalStrings strings added to all selected folders!")
            
        } catch (e: Exception) {
            onProgress("Batch processing error: ${e.message}")
            throw e
        }
    }
}