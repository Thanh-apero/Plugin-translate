package com.xmltranslator.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Service for scanning project to find modules with values directories
 * 
 * @author Thanh Nguyen <thanhnguyen6702@gmail.com>
 */
@Service 
class ProjectScanner {
    
    enum class ModuleType(val displayName: String, val icon: String) {
        ANDROID("Android", ""),
        KMM_SHARED("KMM Shared", ""),
        KMM_ANDROID("KMM Android", ""),
        KMM_COMMON("KMM Common", ""),
        ANDROID_APP("Android App", "")
    }
    
    private data class ResourcePath(val path: String, val moduleType: ModuleType)
    
    data class AndroidModule(
        val name: String,
        val path: String,
        val resDir: File,
        val valuesDir: File,
        val stringsFile: File?,
        val availableValuesFolders: List<String>,
        val moduleType: ModuleType = ModuleType.ANDROID
    ) {
        val isValid: Boolean
            get() = resDir.exists() && valuesDir.exists()
        
        val hasStringsFile: Boolean
            get() = stringsFile?.exists() == true
        
        override fun toString(): String {
            val status = when {
                !isValid -> "❌"
                !hasStringsFile -> "⚠️"
                else -> "✅"
            }
            val stringCount = if (hasStringsFile) {
                try {
                    val content = stringsFile!!.readText()
                    val stringTags = Regex("<string[^>]*>").findAll(content).count()
                    " (${stringTags} strings)"
                } catch (e: Exception) {
                    " (error reading)"
                }
            } else {
                " (no strings.xml)"
            }
            
            return "$status ${moduleType.icon} $name${stringCount}"
        }
    }
    
    /**
     * Scan project để tìm tất cả modules có thư mục values
     */
    fun scanProjectModules(project: Project): List<AndroidModule> {
        val projectPath = project.basePath ?: return emptyList()
        val projectRoot = File(projectPath)
        
        val modules = mutableListOf<AndroidModule>()
        
        // Scan các common Android project structures
        scanDirectory(projectRoot, modules, "")
        
        return modules.sortedBy { it.name }
    }
    
    private fun scanDirectory(dir: File, modules: MutableList<AndroidModule>, parentPath: String) {
        if (!dir.exists() || !dir.isDirectory) return
        
        val currentPath = if (parentPath.isEmpty()) dir.name else "$parentPath/${dir.name}"
        
        // Kiểm tra các cấu trúc module khác nhau
        val resourcePaths = listOf(
            // Traditional Android paths
            ResourcePath("src/main/res", ModuleType.ANDROID),
            ResourcePath("main/res", ModuleType.ANDROID),
            ResourcePath("res", ModuleType.ANDROID),
            
            // KMM paths
            ResourcePath("src/commonMain/resources", ModuleType.KMM_COMMON),
            ResourcePath("src/androidMain/res", ModuleType.KMM_ANDROID),
            ResourcePath("shared/src/commonMain/resources", ModuleType.KMM_COMMON),
            ResourcePath("shared/src/androidMain/res", ModuleType.KMM_ANDROID),
            
            // Android App in KMM project  
            ResourcePath("androidApp/src/main/res", ModuleType.ANDROID_APP)
        )
        
        for (resourcePath in resourcePaths) {
            val resDir = File(dir, resourcePath.path)
            if (resDir.exists() && resDir.isDirectory) {
                // Đối với KMM common resources, cần tìm xml files thay vì values folder
                val valuesDir = if (resourcePath.moduleType == ModuleType.KMM_COMMON) {
                    // KMM common có thể có strings trong root resources hoặc values subfolder
                    val possibleValuesDirs = listOf(
                        File(resDir, "values"),
                        resDir  // Có thể strings.xml nằm trực tiếp trong resources
                    )
                    possibleValuesDirs.firstOrNull { it.exists() && it.isDirectory }
                } else {
                    File(resDir, "values")
                }
                
                if (valuesDir != null && valuesDir.exists() && valuesDir.isDirectory) {
                    // Tìm thấy module hợp lệ
                    val stringsFile = File(valuesDir, "strings.xml")
                    val availableValuesFolders = getValuesFolders(resDir)
                    
                    val module = AndroidModule(
                        name = currentPath,
                        path = dir.absolutePath,
                        resDir = resDir,
                        valuesDir = valuesDir,
                        stringsFile = if (stringsFile.exists()) stringsFile else null,
                        availableValuesFolders = availableValuesFolders,
                        moduleType = resourcePath.moduleType
                    )
                    
                    modules.add(module)
                    break // Đã tìm thấy res dir, không cần check các path khác
                }
            }
        }
        
        // Recursively scan subdirectories (nhưng bỏ qua một số thư mục không cần thiết)
        val skipDirs = setOf(
            "build", "out", ".gradle", ".idea", "node_modules", 
            "target", "bin", "gen", ".git", "src", "main"
        )
        
        dir.listFiles { file ->
            file.isDirectory && !file.name.startsWith(".") && !skipDirs.contains(file.name)
        }?.forEach { subDir ->
            // Limit depth để tránh scan quá sâu
            if (parentPath.count { it == '/' } < 3) {
                scanDirectory(subDir, modules, currentPath)
            }
        }
    }
    
    private fun getValuesFolders(resDir: File): List<String> {
        // Kiểm tra xem có values folders không
        val valuesFolders = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values")
        }?.map { it.name }?.sorted() ?: emptyList()
        
        // Nếu không có values folders, có thể là KMM common structure
        // Trả về "values" như default nếu thư mục chính chứa strings.xml
        if (valuesFolders.isEmpty() && File(resDir, "strings.xml").exists()) {
            return listOf("values")
        }
        
        return valuesFolders
    }
    
    /**
     * Get filtered values folders for a specific module
     */
    fun getFilteredValuesFolders(module: AndroidModule): List<String> {
        val stringFilter = StringFilter()
        return stringFilter.getFilteredValuesFolders(module.resDir)
    }
    
    /**
     * Get project structure info for debugging
     */
    fun getProjectInfo(project: Project): String {
        val modules = scanProjectModules(project)
        val modulesByType = modules.groupBy { it.moduleType }
        
        return buildString {
            appendLine("📦 Project: ${project.name}")
            appendLine("📍 Path: ${project.basePath}")
            appendLine("🔍 Found ${modules.size} modules with resource directories:")
            appendLine()
            
            if (modules.isEmpty()) {
                appendLine("❌ No modules found")
                appendLine("💡 Supported structures:")
                appendLine("    Android: module/src/main/res/values/")
                appendLine("    KMM Common: shared/src/commonMain/resources/")
                appendLine("    KMM Android: shared/src/androidMain/res/values/")
                appendLine("    KMM App: androidApp/src/main/res/values/")
            } else {
                modulesByType.forEach { (type, moduleList) ->
                    appendLine("${type.icon} ${type.displayName} Modules (${moduleList.size}):")
                    moduleList.forEach { module ->
                        appendLine("  ${module}")
                        appendLine("     📁 Path: ${module.path}")
                        appendLine("     📂 Res: ${module.resDir.path}")
                        appendLine("     🌍 Values folders: ${module.availableValuesFolders.joinToString(", ")}")
                        if (module.hasStringsFile) {
                            appendLine("     📄 strings.xml: ✅")
                        } else {
                            appendLine("     📄 strings.xml: ❌ (will be created)")
                        }
                        appendLine()
                    }
                }
            }
        }
    }
} 