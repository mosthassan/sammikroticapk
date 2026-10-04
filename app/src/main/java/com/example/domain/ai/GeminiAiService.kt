package com.example.domain.ai

import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiAiService(
    private val apiKeyProvider: () -> String = { "" }
) {
    var modelName: String = "gemini-3.5-flash"

    suspend fun generateContent(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Graceful fallback to deterministic local logic
            return@withContext Result.failure(IllegalStateException("مفتاح Gemini API غير مهيأ، سيتم استخدام المعالجة المحلية."))
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doOutput = true

            val requestJson = JSONObject()
            val contents = org.json.JSONArray()
            val contentObj = JSONObject()
            val parts = org.json.JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            parts.put(partObj)
            contentObj.put("parts", parts)
            contents.put(contentObj)
            requestJson.put("contents", contents)

            connection.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val candidates = root.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val responseContent = firstCandidate?.optJSONObject("content")
                val responseParts = responseContent?.optJSONArray("parts")
                val text = responseParts?.optJSONObject(0)?.optString("text") ?: ""
                Result.success(text)
            } else {
                val errText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(RuntimeException("Gemini API Error ($responseCode): $errText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
