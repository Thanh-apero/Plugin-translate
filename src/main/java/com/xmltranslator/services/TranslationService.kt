package com.xmltranslator.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

// Type aliases để maintain compatibility - moved outside class
typealias TranslationRequest = ApiService.TranslationRequest
typealias StringItem = ApiService.StringItem
typealias TranslationResponse = ApiService.TranslationResponse
typealias TranslatedItem = ApiService.TranslatedItem

/**
 * Main translation service orchestrator - refactored để tách responsibilities
 * Sử dụng các services khác cho specific tasks và sửa lỗi error handling
 * 
 * @author Thanh Nguyen <thanhnguyen6702@gmail.com>
 */
@Service(Service.Level.PROJECT)
class TranslationService(private val project: Project) {
    
    private val apiService by lazy { project.getService(ApiService::class.java) }
    private val xmlProcessor by lazy { project.getService(XmlProcessor::class.java) }
    private val stringFilter by lazy { project.getService(StringFilter::class.java) }
    private val projectScanner by lazy { project.getService(ProjectScanner::class.java) }
    
    /**
     * Delegate to StringFilter service
     */
    fun getFilteredValuesFolders(resourceDir: File): List<String> {
        return stringFilter.getFilteredValuesFolders(resourceDir)
    }
    
    /**
     * Delegate to StringFilter service
     */
    fun getFilteringInfo(): String {
        return stringFilter.getFilteringInfo()
    }
    
    /**
     * Delegate to StringFilter service
     */
    fun testExclusion(name: String, isFolder: Boolean = false): Pair<Boolean, String> {
        return stringFilter.testExclusion(name, isFolder)
    }
    
    /**
     * Get timeout information for a given number of strings
     */
    fun getTimeoutInfo(stringCount: Int): String {
        return apiService.getTimeoutInfo(stringCount)
    }
    
    /**
     * Scan project và trả về danh sách modules có thể dịch
     */
    fun getAvailableModules(): List<ProjectScanner.AndroidModule> {
        return projectScanner.scanProjectModules(project)
    }
    
    /**
     * Get project info for debugging
     */
    fun getProjectInfo(): String {
        return projectScanner.getProjectInfo(project)
    }
    
    /**
     * Get filtered values folders for a specific module
     */
    fun getFilteredValuesFolders(module: ProjectScanner.AndroidModule): List<String> {
        return projectScanner.getFilteredValuesFolders(module)
    }

    /**
     * Translate một text đơn lẻ - THROW exception khi lỗi (không fallback)
     */
    fun translateText(text: String, sourceLang: String, targetLang: String): String {
        println("DEBUG: Dịch '$text' từ $sourceLang sang $targetLang")
        
        val request = TranslationRequest(
            source_language = sourceLang,
            target_language = targetLang,
            strings = listOf(StringItem(1, text))
        )
        
        val response = apiService.translateRequest(request)
        return response.translations.firstOrNull()?.text 
            ?: throw Exception("Không nhận được kết quả translation cho text: $text")
    }
    
    /**
     * Translate danh sách strings - THROW exception khi lỗi (không fallback)
     */
    fun translateStrings(
        strings: List<Pair<String, String>>, // name to text pairs
        sourceLang: String,
        targetLang: String,
        onProgress: (String) -> Unit = {}
    ): List<Pair<String, String>> {
        onProgress("Chuẩn bị yêu cầu translation...")
        
        val request = TranslationRequest(
            source_language = sourceLang,
            target_language = targetLang,
            strings = strings.mapIndexed { index, (name, text) ->
                StringItem(index + 1, text, name)
            }
        )
        
        onProgress("Gửi yêu cầu tới translation API...")
        val response = apiService.translateRequest(request)
        
        onProgress("Xử lý kết quả translation...")
        return strings.zip(response.translations).map { (original, translated) ->
            original.first to translated.text
        }
    }

    
    fun translateXmlFile(
        inputFile: File,
        outputDir: File,
        targetLanguages: List<String>,
        onProgress: (String) -> Unit = {}
    ) {
        onProgress("Đọc file XML...")
        
        val xmlContent = inputFile.readText()
        val document = xmlProcessor.parseXml(xmlContent)
        val stringElements = xmlProcessor.extractStringElements(document)
        
        if (stringElements.isEmpty()) {
            onProgress("Không tìm thấy strings có thể dịch trong file XML")
            return
        }
        
        onProgress("Tìm thấy ${stringElements.size} strings để dịch")
        
        for (lang in targetLanguages) {
            onProgress("Đang dịch sang $lang...")
            
            // KHÔNG catch exception - để lỗi bubble up và ngăn việc ghi file
            val translations = translateStrings(
                stringElements,
                "en",
                lang
            ) { progress ->
                onProgress("$lang: $progress")
            }
            
            val outputFile = File(outputDir, "values-$lang/strings.xml")
            outputFile.parentFile.mkdirs()
            
            xmlProcessor.saveTranslatedXml(translations, outputFile)
            onProgress("Đã lưu translations cho $lang")
            
            // Wait between translations to avoid API limits
            Thread.sleep(2000)
        }
        
        onProgress("Dịch hoàn tất!")
    }
    
    /**
     * Translate strings.xml trong một module cụ thể
     */
    fun translateModule(
        module: ProjectScanner.AndroidModule,
        targetLanguages: List<String>,
        onProgress: (String) -> Unit = {}
    ) {
        if (!module.hasStringsFile) {
            throw Exception("Module ${module.name} không có file strings.xml")
        }
        
        onProgress("📦 Đang dịch module: ${module.name}")
        onProgress("📄 Input: ${module.stringsFile!!.path}")
        
        val xmlContent = module.stringsFile.readText()
        val document = xmlProcessor.parseXml(xmlContent)
        val stringElements = xmlProcessor.extractStringElements(document)
        
        if (stringElements.isEmpty()) {
            onProgress("❌ Không tìm thấy strings có thể dịch trong ${module.name}")
            return
        }
        
        onProgress("✅ Tìm thấy ${stringElements.size} strings để dịch")
        
        for (lang in targetLanguages) {
            onProgress("🌍 Đang dịch sang $lang...")
            
            // KHÔNG catch exception - để lỗi bubble up và ngăn việc ghi file
            val translations = translateStrings(
                stringElements,
                "en",
                lang
            ) { progress ->
                onProgress("   $lang: $progress")
            }
            
            val outputFile = File(module.resDir, "values-$lang/strings.xml")
            outputFile.parentFile.mkdirs()
            
            xmlProcessor.saveTranslatedXml(translations, outputFile)
            onProgress("✅ Đã lưu translations cho $lang tại ${outputFile.path}")
            
            // Wait between translations to avoid API limits
            Thread.sleep(2000)
        }
        
        onProgress("🎉 Dịch module ${module.name} hoàn tất!")
    }
    

    

    
    fun addStringToXmlFiles(
        stringName: String,
        originalText: String,
        resourceDir: File,
        targetFolders: Array<String>,
        onProgress: (String) -> Unit = {}
    ) {
        for (folder in targetFolders) {
            val translatedText = if (folder == "values") {
                originalText
            } else {
                val lang = folder.removePrefix("values-")
                onProgress("Đang dịch sang $lang...")
                // KHÔNG catch exception - để lỗi bubble up
                translateText(originalText, "en", lang)
            }
            
            val xmlFile = File(resourceDir, "$folder/strings.xml")
            xmlFile.parentFile.mkdirs()
            
            xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, translatedText)
            onProgress("Đã thêm string vào $folder")
        }
        
        onProgress("Đã thêm string vào tất cả folders được chọn!")
    }
    
    /**
     * Add strings vào một module cụ thể
     */
    fun addStringToModule(
        stringName: String,
        originalText: String,
        module: ProjectScanner.AndroidModule,
        targetFolders: Array<String>,
        onProgress: (String) -> Unit = {}
    ) {
        onProgress("📦 Đang thêm string vào module: ${module.name}")
        
        for (folder in targetFolders) {
            val translatedText = if (folder == "values") {
                originalText
            } else {
                val lang = folder.removePrefix("values-")
                onProgress("🌍 Đang dịch sang $lang...")
                // KHÔNG catch exception - để lỗi bubble up
                translateText(originalText, "en", lang)
            }
            
            val xmlFile = File(module.resDir, "$folder/strings.xml")
            xmlFile.parentFile.mkdirs()
            
            xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, translatedText)
            onProgress("✅ Đã thêm string vào $folder")
        }
        
        onProgress("🎉 Đã thêm string vào tất cả folders được chọn trong module ${module.name}!")
    }
    
    fun addBatchStringsToXmlFiles(
        stringItems: List<Pair<String, String>>, // (name, text) pairs
        resourceDir: File,
        targetFolders: Array<String>,
        onProgress: (String) -> Unit = {},
        batchSize: Int = 50
    ) {
        val totalStrings = stringItems.size
        onProgress("Bắt đầu batch translation cho $totalStrings strings...")
        
        // Group strings into batches
        val batches = stringItems.chunked(batchSize)
        onProgress("Chia thành ${batches.size} batches với tối đa $batchSize strings mỗi batch")
        
        for (folder in targetFolders) {
            val lang = folder.removePrefix("values-")
            onProgress("Xử lý folder: $folder")
            
            if (folder == "values") {
                // Original language - không cần dịch
                for ((stringName, originalText) in stringItems) {
                    val xmlFile = File(resourceDir, "$folder/strings.xml")
                    xmlFile.parentFile.mkdirs()
                    xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, originalText)
                }
                onProgress("Đã thêm $totalStrings strings vào $folder (original)")
            } else {
                // Target language - cần dịch
                var processedInLang = 0
                for ((batchIndex, batch) in batches.withIndex()) {
                    onProgress("Dịch batch ${batchIndex + 1}/${batches.size} sang $lang (${batch.size} strings)...")
                    
                    // Translate entire batch at once - KHÔNG catch exception
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
                        xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, translatedText)
                        processedInLang++
                    }
                    
                    onProgress("Đã thêm batch ${batchIndex + 1} vào $folder ($processedInLang/$totalStrings strings)")
                    
                    // Add delay between batches to respect API limits
                    if (batchIndex < batches.size - 1) {
                        Thread.sleep(2000)
                    }
                }
                onProgress("Hoàn thành tất cả batches cho $folder ($processedInLang strings)")
            }
            
            // Add delay between languages
            Thread.sleep(1000)
        }
        
        onProgress("Tất cả $totalStrings strings đã được thêm vào tất cả folders được chọn!")
    }
    
    /**
     * Add batch strings vào một module cụ thể
     */
    fun addBatchStringsToModule(
        stringItems: List<Pair<String, String>>, // (name, text) pairs
        module: ProjectScanner.AndroidModule,
        targetFolders: Array<String>,
        onProgress: (String) -> Unit = {},
        batchSize: Int = 50
    ) {
        val totalStrings = stringItems.size
        onProgress("📦 Bắt đầu batch translation cho $totalStrings strings trong module ${module.name}...")
        
        // Group strings into batches
        val batches = stringItems.chunked(batchSize)
        onProgress("📊 Chia thành ${batches.size} batches với tối đa $batchSize strings mỗi batch")
        
        for (folder in targetFolders) {
            val lang = folder.removePrefix("values-")
            onProgress("📂 Xử lý folder: $folder")
            
            if (folder == "values") {
                // Original language - không cần dịch
                for ((stringName, originalText) in stringItems) {
                    val xmlFile = File(module.resDir, "$folder/strings.xml")
                    xmlFile.parentFile.mkdirs()
                    xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, originalText)
                }
                onProgress("✅ Đã thêm $totalStrings strings vào $folder (original)")
            } else {
                // Target language - cần dịch
                var processedInLang = 0
                for ((batchIndex, batch) in batches.withIndex()) {
                    onProgress("🌍 Dịch batch ${batchIndex + 1}/${batches.size} sang $lang (${batch.size} strings)...")
                    
                    // Translate entire batch at once - KHÔNG catch exception
                    val translatedBatch = translateStrings(
                        batch,
                        "en",
                        lang
                    ) { progress ->
                        onProgress("   $lang batch ${batchIndex + 1}: $progress")
                    }
                    
                    // Add translated strings to XML
                    for ((originalPair, translatedPair) in batch.zip(translatedBatch)) {
                        val stringName = originalPair.first
                        val translatedText = translatedPair.second
                        
                        val xmlFile = File(module.resDir, "$folder/strings.xml")
                        xmlFile.parentFile.mkdirs()
                        xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, translatedText)
                        processedInLang++
                    }
                    
                    onProgress("✅ Đã thêm batch ${batchIndex + 1} vào $folder ($processedInLang/$totalStrings strings)")
                    
                    // Add delay between batches to respect API limits
                    if (batchIndex < batches.size - 1) {
                        Thread.sleep(2000)
                    }
                }
                onProgress("🎉 Hoàn thành tất cả batches cho $folder ($processedInLang strings)")
            }
            
            // Add delay between languages
            Thread.sleep(1000)
        }
        
        onProgress("🎉 Tất cả $totalStrings strings đã được thêm vào module ${module.name}!")
    }
}
