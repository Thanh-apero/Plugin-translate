package com.xmltranslator.services

import com.intellij.openapi.components.Service
import java.io.File

/**
 * Service for filtering strings and folders based on configuration patterns
 * 
 * @author Thanh Nguyen <thanhnguyen6702@gmail.com>
 */
@Service
class StringFilter {
    
    // Comprehensive patterns for automatic filtering
    private val excludePatterns = listOf(
        // Version-specific qualifiers
        "-v\\d+",                                    // Any version qualifier (v21, v29, v30, etc.)
        "value-v\\d+",                              // Direct value-v patterns
        
        // Screen configuration qualifiers
        "-night", "-notnight",                       // Night mode qualifiers
        "value-night", "value-notnight",            // Direct value-night patterns
        
        // Orientation qualifiers
        "-land", "-port",                           // Landscape/Portrait
        "value-land", "value-port",                 // Direct value-land patterns
        
        // Screen size qualifiers
        "-small", "-normal", "-large", "-xlarge",   // Screen sizes
        "value-small", "value-normal", "value-large", "value-xlarge",
        
        // Screen density qualifiers
        "-ldpi", "-mdpi", "-hdpi", "-xhdpi", "-xxhdpi", "-xxxhdpi", "-nodpi", "-tvdpi",
        "value-ldpi", "value-mdpi", "value-hdpi", "value-xhdpi", "value-xxhdpi", "value-xxxhdpi",
        
        // Screen width/height qualifiers
        "-sw\\d+dp", "-w\\d+dp", "-h\\d+dp",        // Smallest width, width, height
        "value-sw\\d+dp", "value-w\\d+dp", "value-h\\d+dp",
        
        // UI Mode qualifiers
        "-car", "-desk", "-television", "-appliance", "-watch", "-vrheadset",
        "value-car", "value-desk", "value-television", "value-appliance", "value-watch", "value-vrheadset",
        
        // Keyboard and input qualifiers
        "-keysexposed", "-keyshidden", "-keyssoft",
        "-notouch", "-stylus", "-finger",
        "-trackball", "-wheel", "-dpad",
        
        // Navigation qualifiers
        "-navexposed", "-navhidden",
        "-nonav", "-dpad", "-trackball", "-wheel",
        
        // Round screen qualifier (for watches)
        "-round", "-notround"
    )
    
    /**
     * Get filtered values folders from a directory
     * Automatically excludes unwanted qualifier combinations
     */
    fun getFilteredValuesFolders(resourceDir: File): List<String> {
        if (!resourceDir.exists() || !resourceDir.isDirectory) {
            return emptyList()
        }
        
        val allValuesFolders = resourceDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values")
        }?.map { it.name } ?: emptyList()
        
        val filteredFolders = allValuesFolders.filter { folderName ->
            !shouldExcludeValuesFolder(folderName)
        }.sorted()
        
        // Simple log for excluded folders
        val excludedFolders = allValuesFolders.filter { folderName ->
            shouldExcludeValuesFolder(folderName)
        }
        
        if (excludedFolders.isNotEmpty()) {
            println("🚫 Loại bỏ ${excludedFolders.size} thư mục: ${excludedFolders.joinToString(", ")}")
        }
        
        return filteredFolders
    }
    
    /**
     * Get simplified filtering information
     */
    fun getFilteringInfo(): String {
        return "🛡️ ${excludePatterns.size} patterns đã được cấu hình để loại bỏ qualifier folders"
    }
    
    /**
     * Test if a string name or folder name would be excluded
     * Useful for debugging and preview
     */
    fun testExclusion(name: String, isFolder: Boolean = false): Pair<Boolean, String> {
        val isExcluded = if (isFolder) {
            shouldExcludeValuesFolder(name)
        } else {
            shouldExcludeString(name)
        }
        
        val reason = if (isExcluded) {
            val matchingPatterns = mutableListOf<String>()
            
            // Check patterns
            excludePatterns.forEach { pattern ->
                if (name.matches(".*$pattern.*".toRegex(RegexOption.IGNORE_CASE))) {
                    matchingPatterns.add("Matched: $pattern")
                }
            }
            
            "Matched patterns: ${matchingPatterns.joinToString(", ")}"
        } else {
            "No matching exclusion patterns"
        }
        
        return isExcluded to reason
    }
    
    /**
     * Check if a string name should be excluded from processing
     * Enhanced to catch more patterns automatically
     */
    fun shouldExcludeString(stringName: String): Boolean {
        // Check against consolidated patterns
        return excludePatterns.any { pattern ->
            stringName.matches(".*$pattern.*".toRegex(RegexOption.IGNORE_CASE))
        }
    }
    
    /**
     * Check if a values folder should be excluded from processing
     * Enhanced with comprehensive pattern matching
     */
    fun shouldExcludeValuesFolder(folderName: String): Boolean {
        // Skip if folder doesn't start with "values"
        if (!folderName.startsWith("values")) {
            return false
        }
        
        // Always include base "values" folder
        if (folderName == "values") {
            return false
        }
        
        // Check against consolidated patterns for comprehensive filtering
        return excludePatterns.any { pattern ->
            folderName.matches(".*$pattern.*".toRegex(RegexOption.IGNORE_CASE))
        }
    }
} 