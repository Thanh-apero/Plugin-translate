package com.xmltranslator.services

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.xmltranslator.settings.XmlTranslatorSettings
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for handling Google Gemini API communication
 * 
 * @author Thanh Nguyen <thanhnguyen6702@gmail.com>
 */
@Service
class ApiService {
    
    private val gson = Gson()
    private var currentKeyIndex = AtomicInteger(0)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(60))
        .build()

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

    private fun getApiKeys(): List<String> {
        val settings = XmlTranslatorSettings.getInstance()
        val keys = settings.getValidApiKeys()
        
        if (keys.isEmpty()) {
            throw Exception("Không có API key nào được cấu hình. Vui lòng thêm API key trong Settings > Tools > XML Translator")
        }
        
        return keys
    }
    
    private fun getNextApiKey(): String {
        val keys = getApiKeys()
        val index = currentKeyIndex.getAndUpdate { (it + 1) % keys.size }
        return keys[index]
    }

    /**
     * Thực hiện translation request và trả về kết quả hoặc throw exception nếu lỗi
     */
    fun translateRequest(request: TranslationRequest): TranslationResponse {
        val apiKey = getNextApiKey()
        println("DEBUG: Gửi yêu cầu translation tới Google AI API")
        println("DEBUG: Sử dụng API key: ${apiKey.substring(0, 10)}...")
        
        val prompt = buildPrompt(request)
        
        try {
            val response = callGeminiAPI(apiKey, request, prompt)
            return parseGeminiResponse(response)
        } catch (e: Exception) {
            // Retry với API key khác nếu có (nhưng không retry timeout errors)
            val isTimeoutError = e.message?.contains("timeout", ignoreCase = true) == true
            
            println("🔄 Lần thử đầu thất bại: ${e.message}")
            
            val keys = getApiKeys()
            if (keys.size > 1 && !isTimeoutError) {
                println("🔄 Thử lại với API key khác...")
                Thread.sleep(4000) // Wait before retry
                
                val retryApiKey = getNextApiKey()
                val retryResponse = callGeminiAPI(retryApiKey, request, prompt)
                return parseGeminiResponse(retryResponse)
            } else {
                if (isTimeoutError) {
                    throw Exception("⏰ Translation timeout: ${e.message}\n\n💡 Gợi ý: Thử giảm batch size xuống dưới ${request.strings.size}")
                } else {
                    throw Exception("❌ Translation API thất bại: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Tính toán timeout dựa trên số lượng strings cần dịch
     */
    private fun calculateTimeout(stringCount: Int): Long {
        // Base timeout 30s + 5s per string, minimum 30s, maximum 300s (5 phút)
        val dynamicTimeout = 30 + (stringCount * 5)
        return maxOf(30, minOf(300, dynamicTimeout.toLong()))
    }
    
    /**
     * Public method để check timeout settings cho một batch size cụ thể
     */
    fun getTimeoutInfo(stringCount: Int): String {
        val timeout = calculateTimeout(stringCount)
        return "⏰ Timeout cho $stringCount strings: ${timeout}s (${timeout/60.0} phút)"
    }
    
    private fun callGeminiAPI(apiKey: String, request: TranslationRequest, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
        val geminiRequest = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(prompt))
                )
            )
        )
        
        val requestBody = gson.toJson(geminiRequest)
        
        // Dynamic timeout dựa trên request size
        val timeoutSeconds = calculateTimeout(request.strings.size)
        println("DEBUG: Sử dụng timeout ${timeoutSeconds}s cho ${request.strings.size} strings")
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()
        
        val response = try {
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        } catch (e: java.net.http.HttpTimeoutException) {
            throw Exception("⏰ Request timeout sau ${timeoutSeconds}s. Thử giảm số lượng strings hoặc kiểm tra kết nối mạng. Chi tiết: ${e.message}")
        } catch (e: java.net.ConnectException) {
            throw Exception("🔌 Không thể kết nối tới Google API. Kiểm tra kết nối internet. Chi tiết: ${e.message}")
        } catch (e: java.io.IOException) {
            throw Exception("🌐 Lỗi mạng khi gọi API. Chi tiết: ${e.message}")
        }
        
        if (response.statusCode() != 200) {
            val errorBody = response.body().take(500) // Limit error message length
            throw Exception("🚫 API request thất bại với status ${response.statusCode()}: $errorBody")
        }
        
        val geminiResponse = gson.fromJson(response.body(), GeminiResponse::class.java)
        val generatedText = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Không có nội dung trong API response")
        
        return generatedText
    }
    
    private fun buildPrompt(request: TranslationRequest): String {
        // Sử dụng examples như bản Python - complete set
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
    {
      "id": 1,
      "text": "Enable <b>Notifications</b> for continuous using when the app is closed."
    }
  ]
}
output: {
  "translations": [
    {
      "id": 1,
      "text": "Bật <b>Thông báo</b> của ứng dụng để tiếp tục sử dụng khi ứng dụng bị đóng."
    }
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

input: {
  "source_language": "en",
  "target_language": "vi",
  "strings": [
    {
      "id": 1,
      "text": "Set app <font color=\"#FF3E3E\"><b>PDF Reader</b></font> as the default PDF reader"
    },
    {
      "id": 2,
      "text": "Click <font color=\"#007AFF\"><b>Allow</b></font> to enable permissions"
    }
  ]
}
output: {
  "translations": [
    {
      "id": 1,
      "text": "Đặt ứng dụng <font color=\"#FF3E3E\"><b>PDF Reader</b></font> làm trình đọc PDF mặc định"
    },
    {
      "id": 2,
      "text": "Nhấn <font color=\"#007AFF\"><b>Cho phép</b></font> để bật quyền"
    }
  ]
}

input: {
  "source_language": "en",
  "target_language": "ko",
  "strings": [
    {
      "id": 1,
      "text": "Download <font color=\"#34C759\"><b>Premium</b></font> version for unlimited features"
    },
    {
      "id": 2,
      "text": "Status: <font color=\"#FF9500\"><b>Processing...</b></font>"
    }
  ]
}
output: {
  "translations": [
    {
      "id": 1,
      "text": "<font color=\"#34C759\"><b>프리미엄</b></font> 버전을 다운로드하여 무제한 기능을 이용하세요"
    },
    {
      "id": 2,
      "text": "상태: <font color=\"#FF9500\"><b>처리 중...</b></font>"
    }
  ]
}
        """.trimIndent()
        
        val requestJson = gson.toJson(request)
        
        return """
$examples

QUY TẮC ĐỊNH DẠNG QUAN TRỌNG:
1. Luôn bảo toàn các HTML/XML tags chính xác như chúng xuất hiện: <b>, </b>, <font>, </font>, etc.
2. Giữ nguyên tất cả thuộc tính HTML: color="#FF3E3E", style="...", etc.
3. Chỉ dịch nội dung văn bản thực tế, không dịch cấu trúc HTML
4. Bảo toàn tất cả escape sequences: \n, \r, \t, \\, etc.
5. Giữ nguyên ký tự đặc biệt và symbols: #, @, &, etc.

input: $requestJson
output:"""
    }
    
    private fun parseGeminiResponse(responseText: String): TranslationResponse {
        try {
            // Clean response text - giống như Python version
            val cleanContent = responseText
                .replace("```json\n", "")
                .replace("\n```", "")
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            println("DEBUG: Clean response: $cleanContent")
            
            return gson.fromJson(cleanContent, TranslationResponse::class.java)
        } catch (e: Exception) {
            println("LỖI: Không thể parse response: ${e.message}")
            println("Response text: $responseText")
            throw Exception("Không thể parse translation response: ${e.message}")
        }
    }
} 