package com.projectsuperhuman.next

import com.projectsuperhuman.next.trudy.TrudyFormattedModelInput
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Direct Gemini Developer API transport for personal builds.
 *
 * No Project Superhuman server is required. The API key is supplied by the runtime credential
 * provider and is never written to logs. Health/tool evidence remains bounded by the Trudy prompt
 * formatter and all personal calculations still happen in deterministic Trudy tools.
 */
class GeminiHostedTrudyTransport : HostedTrudyTransport {
    override suspend fun complete(
        settings: HostedTrudyModelSettings,
        credential: String,
        input: TrudyFormattedModelInput
    ): HostedTrudyModelResponse = withContext(Dispatchers.IO) {
        require(settings.providerId.equals(PROVIDER_ID, ignoreCase = true)) {
            "Gemini transport cannot serve provider ${settings.providerId}"
        }
        require(settings.endpoint.startsWith("https://generativelanguage.googleapis.com/")) {
            "Gemini endpoint must use the official Google Generative Language API host"
        }
        require(settings.modelId.isNotBlank()) { "Gemini model ID is required" }
        require(credential.isNotBlank()) { "Gemini API key is required" }

        val endpoint = settings.endpoint.trimEnd('/') + "/" + settings.modelId + ":generateContent"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", credential)
        }

        try {
            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", input.systemInstruction))
                ))
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", input.asPrompt())))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.25)
                    put("topP", 0.9)
                    put("maxOutputTokens", 1200)
                })
            }

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                throw HostedTrudyConfigurationException(
                    "Gemini request failed with HTTP $code${safeErrorSuffix(body)}"
                )
            }

            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates")
                ?: throw IllegalStateException("Gemini response contained no candidates")
            if (candidates.length() == 0) throw IllegalStateException("Gemini response contained no candidates")
            val content = candidates.getJSONObject(0).optJSONObject("content")
                ?: throw IllegalStateException("Gemini response contained no content")
            val parts = content.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini response contained no text parts")
            val text = buildString {
                for (index in 0 until parts.length()) {
                    val part = parts.optJSONObject(index) ?: continue
                    val piece = part.optString("text").trim()
                    if (piece.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(piece)
                    }
                }
            }.trim()
            if (text.isBlank()) throw IllegalStateException("Gemini returned an empty response")

            HostedTrudyModelResponse(
                responseText = text,
                attributes = mapOf("transport" to "gemini-generate-content")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun safeErrorSuffix(body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return ""
        return ": " + message.replace(Regex("[\\r\\n]+"), " ").take(240)
    }

    private companion object {
        const val PROVIDER_ID = "gemini"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
