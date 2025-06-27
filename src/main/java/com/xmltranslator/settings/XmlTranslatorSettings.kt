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
        
        System.getenv("GOOGLE_AI_API_KEY")?.let { key ->
            if (key.isNotBlank()) keys.add(key)
        }
        
        System.getenv("GOOGLE_AI_API_KEY_1")?.let { key ->
            if (key.isNotBlank()) keys.add(key)
        }
        
        System.getenv("GOOGLE_AI_API_KEY_2")?.let { key ->
            if (key.isNotBlank()) keys.add(key)
        }
        
        System.getProperty("google.ai.api.key")?.let { key ->
            if (key.isNotBlank() && !keys.contains(key)) keys.add(key)
        }
        
        if (keys.isEmpty()) {
            try {
                val properties = java.util.Properties()
                val inputStream = javaClass.classLoader.getResourceAsStream("default-keys.properties")
                inputStream?.use { stream ->
                    properties.load(stream)
                    
                    properties.getProperty("google.ai.api.key.1")?.let { key ->
                        if (key.isNotBlank()) keys.add(key)
                    }
                    
                    properties.getProperty("google.ai.api.key.2")?.let { key ->
                        if (key.isNotBlank()) keys.add(key)
                    }
                }
            } catch (e: Exception) {
                println("Could not load default API keys: ${e.message}")
            }
        }
        
        return keys
    }
    
    fun getValidApiKeys(): List<String> {
        val allKeys = mutableListOf<String>()
        
        if (useDefaultKeys) {
            val defaultKeys = getDefaultApiKeys()
            allKeys.addAll(defaultKeys)
        }
        
        val userKeys = apiKeys.filter { it.isNotBlank() }
        userKeys.forEach { key ->
            if (!allKeys.contains(key)) {
                allKeys.add(key)
            }
        }
        
        if (allKeys.isEmpty()) {
            throw Exception("No API keys found. Please set environment variable GOOGLE_AI_API_KEY or configure keys in settings.")
        }
        
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