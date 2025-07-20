package com.xmltranslator.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.CancellationException

typealias TranslationRequest = ApiService.TranslationRequest
typealias StringItem = ApiService.StringItem
typealias TranslationResponse = ApiService.TranslationResponse
typealias TranslatedItem = ApiService.TranslatedItem

@Service(Service.Level.PROJECT)
class TranslationService(private val project: Project) {
    
    private val apiService by lazy { project.getService(ApiService::class.java) }
    private val xmlProcessor by lazy { project.getService(XmlProcessor::class.java) }
    private val stringFilter by lazy { project.getService(StringFilter::class.java) }
    private val projectScanner by lazy { project.getService(ProjectScanner::class.java) }

    fun translateModule(
        module: ProjectScanner.AndroidModule,
        targetLanguages: List<String>,
        onProgress: (String) -> Unit = {}
    ) {
        if (!module.hasStringsFile) {
            throw Exception("Module ${module.name} không có file strings.xml")
        }

        val availableApiKeys = apiService.getAvailableApiKeys()
        if (availableApiKeys.size < 2) {
            throw Exception("Cần ít nhất 2 API keys để sử dụng parallel translation")
        }

        val xmlContent = module.stringsFile!!.readText()
        val document = xmlProcessor.parseXml(xmlContent)
        val stringElements = xmlProcessor.extractStringElements(document)

        if (stringElements.isEmpty()) {
            throw Exception("Không tìm thấy strings có thể dịch trong ${module.name}")
        }

        val batchSize = 100
        val stringBatches = stringElements.chunked(batchSize)
        val totalCallsPerMinute = availableApiKeys.size * 10
        val callsPerLanguage = stringBatches.size
        val maxSimultaneousLanguages = totalCallsPerMinute / callsPerLanguage
        
        onProgress("⚡ ${stringElements.size} strings → ${stringBatches.size} batches | ${availableApiKeys.size} keys → ${maxSimultaneousLanguages} languages parallel")

        val languageGroups = targetLanguages.chunked(maxSimultaneousLanguages)
        
        for ((groupIndex, languageGroup) in languageGroups.withIndex()) {
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("Translation cancelled")
            }
            
            onProgress("🌍 Group ${groupIndex + 1}/${languageGroups.size}: ${languageGroup.joinToString(", ")}")
            
            val languageExecutor = Executors.newFixedThreadPool(languageGroup.size)
            val languageFutures = mutableListOf<Future<Unit>>()

            try {
                for (lang in languageGroup) {
                    val future = languageExecutor.submit<Unit> {
                        translateLanguage(stringBatches, lang, availableApiKeys, module.resDir, onProgress)
                    }
                    languageFutures.add(future)
                }

                for (future in languageFutures) {
                    future.get()
                }

            } catch (e: Exception) {
                languageFutures.forEach { it.cancel(true) }
                throw e
            } finally {
                languageExecutor.shutdown()
                languageExecutor.awaitTermination(5, TimeUnit.SECONDS)
            }

            if (groupIndex < languageGroups.size - 1) {
                Thread.sleep(2000)
            }
        }

        onProgress("✅ Translation completed")
    }

    fun addBatchStrings(
        stringItems: List<Pair<String, String>>,
        module: ProjectScanner.AndroidModule,
        targetFolders: Array<String>,
        onProgress: (String) -> Unit = {}
    ) {
        val availableApiKeys = apiService.getAvailableApiKeys()
        if (availableApiKeys.size < 2) {
            throw Exception("Cần ít nhất 2 API keys để sử dụng parallel translation")
        }

        val batchSize = 100
        val stringBatches = stringItems.chunked(batchSize)
        
        onProgress("⚡ ${stringItems.size} strings → ${stringBatches.size} batches")

        for (folder in targetFolders) {
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("Translation cancelled")
            }

            val lang = folder.removePrefix("values-")

            if (folder == "values") {
                for ((stringName, originalText) in stringItems) {
                    val xmlFile = File(module.resDir, "$folder/strings.xml")
                    xmlFile.parentFile.mkdirs()
                    xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, originalText)
                }
                onProgress("✅ Added ${stringItems.size} strings to $folder")
            } else {
                onProgress("🌍 Processing $folder...")
                
                val executor = Executors.newFixedThreadPool(availableApiKeys.size)
                val allFutures = mutableListOf<Future<Pair<Int, List<Pair<String, String>>>>>()

                try {
                    for ((batchIndex, batch) in stringBatches.withIndex()) {
                        val apiKeyIndex = batchIndex % availableApiKeys.size
                        val apiKey = availableApiKeys[apiKeyIndex]

                        val future = executor.submit<Pair<Int, List<Pair<String, String>>>> {
                            val request = TranslationRequest(
                                source_language = "en",
                                target_language = lang,
                                strings = batch.mapIndexed { index, (name, text) ->
                                    StringItem(index + 1, text, name)
                                }
                            )

                            val response = apiService.translateRequestWithApiKey(request, apiKey)
                            val translationMap = response.translations.associateBy { it.id }
                            
                            val batchTranslations = batch.mapIndexed { index, originalPair ->
                                val expectedId = index + 1
                                val translation = translationMap[expectedId]
                                    ?: throw Exception("Missing translation ID $expectedId")
                                originalPair.first to translation.text
                            }

                            batchIndex to batchTranslations
                        }

                        allFutures.add(future)
                    }

                    var processedCount = 0
                    for ((futureIndex, future) in allFutures.withIndex()) {
                        val (batchIndex, batchResult) = future.get()
                        
                        for ((stringName, translatedText) in batchResult) {
                            val xmlFile = File(module.resDir, "$folder/strings.xml") 
                            xmlFile.parentFile.mkdirs()
                            xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, translatedText)
                            processedCount++
                        }
                        
                        val progress = ((futureIndex + 1) * 100) / allFutures.size
                        onProgress("📦 [$folder] $progress% (${futureIndex + 1}/${allFutures.size})")
                    }

                    onProgress("✅ [$folder] $processedCount strings completed")

                } catch (e: Exception) {
                    allFutures.forEach { it.cancel(true) }
                    throw e
                } finally {
                    executor.shutdown()
                    executor.awaitTermination(5, TimeUnit.SECONDS)
                }
            }

            Thread.sleep(500)
        }

        onProgress("✅ Batch processing completed")
    }

    fun addBatchStringsToXmlFiles(
        stringItems: List<Pair<String, String>>,
        resourceDir: File,
        targetFolders: Array<String>,
        onProgress: (String) -> Unit = {}
    ) {
        val availableApiKeys = apiService.getAvailableApiKeys()
        if (availableApiKeys.size < 2) {
            throw Exception("Cần ít nhất 2 API keys để sử dụng parallel translation")
        }

        val batchSize = 100
        val stringBatches = stringItems.chunked(batchSize)
        
        onProgress("⚡ ${stringItems.size} strings → ${stringBatches.size} batches")

        for (folder in targetFolders) {
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("Translation cancelled")
            }

            val lang = folder.removePrefix("values-")

            if (folder == "values") {
                for ((stringName, originalText) in stringItems) {
                    val xmlFile = File(resourceDir, "$folder/strings.xml")
                    xmlFile.parentFile.mkdirs()
                    xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, originalText)
                }
                onProgress("✅ Added ${stringItems.size} strings to $folder")
            } else {
                onProgress("🌍 Processing $folder...")
                
                val executor = Executors.newFixedThreadPool(availableApiKeys.size)
                val allFutures = mutableListOf<Future<Pair<Int, List<Pair<String, String>>>>>()

                try {
                    for ((batchIndex, batch) in stringBatches.withIndex()) {
                        val apiKeyIndex = batchIndex % availableApiKeys.size
                        val apiKey = availableApiKeys[apiKeyIndex]

                        val future = executor.submit<Pair<Int, List<Pair<String, String>>>> {
                            val request = TranslationRequest(
                                source_language = "en",
                                target_language = lang,
                                strings = batch.mapIndexed { index, (name, text) ->
                                    StringItem(index + 1, text, name)
                                }
                            )

                            val response = apiService.translateRequestWithApiKey(request, apiKey)
                            val translationMap = response.translations.associateBy { it.id }
                            
                            val batchTranslations = batch.mapIndexed { index, originalPair ->
                                val expectedId = index + 1
                                val translation = translationMap[expectedId]
                                    ?: throw Exception("Missing translation ID $expectedId")
                                originalPair.first to translation.text
                            }

                            batchIndex to batchTranslations
                        }

                        allFutures.add(future)
                    }

                    var processedCount = 0
                    for ((futureIndex, future) in allFutures.withIndex()) {
                        val (batchIndex, batchResult) = future.get()
                        
                        for ((stringName, translatedText) in batchResult) {
                            val xmlFile = File(resourceDir, "$folder/strings.xml") 
                            xmlFile.parentFile.mkdirs()
                            xmlProcessor.addOrUpdateStringInXml(xmlFile, stringName, translatedText)
                            processedCount++
                        }
                        
                        val progress = ((futureIndex + 1) * 100) / allFutures.size
                        onProgress("📦 [$folder] $progress% (${futureIndex + 1}/${allFutures.size})")
                    }

                    onProgress("✅ [$folder] $processedCount strings completed")

                } catch (e: Exception) {
                    allFutures.forEach { it.cancel(true) }
                    throw e
                } finally {
                    executor.shutdown()
                    executor.awaitTermination(5, TimeUnit.SECONDS)
                }
            }

            Thread.sleep(500)
        }

        onProgress("✅ Batch processing completed")
    }

    private fun translateLanguage(
        stringBatches: List<List<Pair<String, String>>>,
        lang: String,
        apiKeys: List<String>,
        resDir: File,
        onProgress: (String) -> Unit
    ) {
        val executor = Executors.newFixedThreadPool(apiKeys.size)
        val allFutures = mutableListOf<Future<Pair<Int, List<Pair<String, String>>>>>()

        try {
            for ((batchIndex, batch) in stringBatches.withIndex()) {
                val apiKeyIndex = batchIndex % apiKeys.size
                val apiKey = apiKeys[apiKeyIndex]

                val future = executor.submit<Pair<Int, List<Pair<String, String>>>> {
                    val request = TranslationRequest(
                        source_language = "en",
                        target_language = lang,
                        strings = batch.mapIndexed { index, (name, text) ->
                            StringItem(index + 1, text, name)
                        }
                    )

                    val response = apiService.translateRequestWithApiKey(request, apiKey)
                    val translationMap = response.translations.associateBy { it.id }
                    
                    val batchTranslations = batch.mapIndexed { index, originalPair ->
                        val expectedId = index + 1
                        val translation = translationMap[expectedId]
                            ?: throw Exception("Missing translation ID $expectedId")
                        originalPair.first to translation.text
                    }

                    batchIndex to batchTranslations
                }

                allFutures.add(future)
            }

            val allTranslations = mutableListOf<Pair<String, String>>()
            for ((futureIndex, future) in allFutures.withIndex()) {
                val (batchIndex, batchResult) = future.get()
                allTranslations.addAll(batchResult)
                
                val progress = ((futureIndex + 1) * 100) / allFutures.size
                onProgress("📦 [$lang] $progress% (${futureIndex + 1}/${allFutures.size})")
            }

            // Sort and save
            val originalOrder = stringBatches.flatten().map { it.first }
            val sortedTranslations = allTranslations.sortedBy { originalOrder.indexOf(it.first) }

            val outputFile = File(resDir, "values-$lang/strings.xml")
            outputFile.parentFile.mkdirs()
            xmlProcessor.mergeTranslatedXml(sortedTranslations, outputFile)
            
            onProgress("💾 [$lang] ${sortedTranslations.size} strings saved")

        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    // Utility methods
    fun getFilteredValuesFolders(resourceDir: File): List<String> = stringFilter.getFilteredValuesFolders(resourceDir)
    fun getFilteringInfo(): String = stringFilter.getFilteringInfo()
    fun testExclusion(name: String, isFolder: Boolean = false): Pair<Boolean, String> = stringFilter.testExclusion(name, isFolder)
    fun getTimeoutInfo(stringCount: Int): String = apiService.getTimeoutInfo(stringCount)
    fun getRateLimitInfo(): String = apiService.getRateLimitInfo()
    fun getModelInfo(): String = apiService.getModelInfo()
    fun getAvailableModules(): List<ProjectScanner.AndroidModule> = projectScanner.scanProjectModules(project)
    fun getProjectInfo(): String = projectScanner.getProjectInfo(project)
    fun getFilteredValuesFolders(module: ProjectScanner.AndroidModule): List<String> = projectScanner.getFilteredValuesFolders(module)
}