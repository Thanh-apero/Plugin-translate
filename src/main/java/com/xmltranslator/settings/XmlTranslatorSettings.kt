package com.xmltranslator.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "XmlTranslatorSettings",
    storages = [Storage("xmlTranslatorSettings.xml")]
)
@Service(Service.Level.APP)
class XmlTranslatorSettings : PersistentStateComponent<XmlTranslatorSettings> {
    
    var apiKeys: MutableList<String> = mutableListOf()
    
    var useDefaultKeys: Boolean = true
    
    override fun getState(): XmlTranslatorSettings {
        return this
    }
    
    override fun loadState(state: XmlTranslatorSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    private fun getDefaultApiKeys(): List<String> {
        val keys = mutableListOf<String>()
        
        // Check environment variables first
        System.getenv("GOOGLE_AI_API_KEY")?.let { key ->
            if (key.isNotBlank() && isValidApiKey(key)) keys.add(key)
        }
        
        System.getenv("GOOGLE_AI_API_KEY_1")?.let { key ->
            if (key.isNotBlank() && isValidApiKey(key)) keys.add(key)
        }
        
        System.getenv("GOOGLE_AI_API_KEY_2")?.let { key ->
            if (key.isNotBlank() && isValidApiKey(key)) keys.add(key)
        }
        
        System.getProperty("google.ai.api.key")?.let { key ->
            if (key.isNotBlank() && isValidApiKey(key) && !keys.contains(key)) keys.add(key)
        }
        
        // Load from properties file
        if (keys.isEmpty()) {
            try {
                val properties = java.util.Properties()
                val inputStream = javaClass.classLoader.getResourceAsStream("default-keys.properties")
                inputStream?.use { stream ->
                    properties.load(stream)
                    
                    properties.getProperty("google.ai.api.key.1")?.let { key ->
                        if (key.isNotBlank() && isValidApiKey(key)) keys.add(key)
                    }
                    
                    properties.getProperty("google.ai.api.key.2")?.let { key ->
                        if (key.isNotBlank() && isValidApiKey(key)) keys.add(key)
                    }
                }
                
                if (keys.isEmpty()) {
                    println("⚠️ default-keys.properties chứa placeholder keys, cần thay thế bằng API keys thực tế")
                }
            } catch (e: Exception) {
                println("⚠️ Không thể load default API keys: ${e.message}")
            }
        }
        
        return keys
    }
    
    private fun isValidApiKey(key: String): Boolean {
        // Check if it's a valid Google AI API key format
        return key.startsWith("AIzaSy") && key.length >= 35 && !key.contains("REPLACE_WITH") && !key.contains("YOUR_ACTUAL")
    }
    
    fun getValidApiKeys(): List<String> {
        val allKeys = mutableListOf<String>()
        
        if (useDefaultKeys) {
            val defaultKeys = getDefaultApiKeys()
            allKeys.addAll(defaultKeys)
            println("🔑 Loaded ${defaultKeys.size} default API keys")
        }
        
        val userKeys = apiKeys.filter { it.isNotBlank() }
        userKeys.forEach { key ->
            if (!allKeys.contains(key)) {
                allKeys.add(key)
            }
        }
        
        if (userKeys.isNotEmpty()) {
            println("🔑 Loaded ${userKeys.size} user API keys")
        }
        
        if (allKeys.isEmpty()) {
            val errorMessage = buildString {
                appendLine("❌ Không tìm thấy API key nào!")
                appendLine()
                appendLine("Hướng dẫn khắc phục:")
                appendLine("1. Lấy API key miễn phí tại: https://aistudio.google.com/app/apikey")
                appendLine("2. Vào Settings > Tools > XML Translator")
                appendLine("3. Thêm API key vào mục 'Additional API Keys'")
                appendLine()
                appendLine("Hoặc set environment variable:")
                appendLine("export GOOGLE_AI_API_KEY=your_api_key_here")
            }
            throw Exception(errorMessage)
        }
        
        println("✅ Tổng cộng ${allKeys.size} API keys sẵn sàng")
        return allKeys
    }
    
    fun addApiKey(key: String) {
        if (key.isNotBlank() && !apiKeys.contains(key)) {
            apiKeys.add(key)
        }
    }
    
    fun removeApiKey(key: String) {
        apiKeys.remove(key)
    }
    
    fun clearApiKeys() {
        apiKeys.clear()
    }
    
    companion object {
        fun getInstance(): XmlTranslatorSettings {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(XmlTranslatorSettings::class.java)
        }
    }
}
