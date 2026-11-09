package com.voxstream.core.providers

import com.voxstream.core.models.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Speech-to-text provider compatible with OpenAI Whisper endpoints
 * (e.g. OpenAI, Groq, local Whisper servers, vLLM).
 */
class OpenAiCompatSttProvider(
    private val baseUrl: String = "https://api.openai.com/v1",
    private val apiKey: String,
    private val model: String = "whisper-1",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) : SttProvider {

    override val name: String = "OpenAI Whisper ($model)"
    override val isOffline: Boolean = false

    override suspend fun transcribe(
        audioBytes: ByteArray,
        sampleRate: Int,
        language: String?
    ): List<Segment> = withContext(Dispatchers.IO) {
        val endpoint = "${baseUrl.trimEnd('/')}/audio/transcriptions"
        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart(
                "file",
                "audio.wav",
                audioBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("response_format", "verbose_json")

        if (!language.isNullOrBlank()) {
            requestBodyBuilder.addFormDataPart("language", language)
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string()?.take(200) ?: "HTTP ${response.code}"
                throw IOException("STT transcription failed with code ${response.code}: $errorMsg")
            }

            val body = response.body?.string() ?: throw IOException("Empty transcription response")
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(body).jsonObject

            val segments = mutableListOf<Segment>()
            val jsonSegments = root["segments"]?.jsonArray
            if (jsonSegments != null && jsonSegments.isNotEmpty()) {
                for (s in jsonSegments) {
                    val obj = s.jsonObject
                    val start = ((obj["start"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
                    val end = ((obj["end"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
                    val text = obj["text"]?.jsonPrimitive?.content?.trim() ?: ""
                    if (text.isNotBlank()) {
                        segments.add(
                            Segment(
                                id = UUID.randomUUID().toString(),
                                chunkIndex = 0,
                                startMs = start,
                                endMs = end,
                                text = text
                            )
                        )
                    }
                }
            } else {
                val singleText = root["text"]?.jsonPrimitive?.content?.trim() ?: ""
                if (singleText.isNotBlank()) {
                    segments.add(
                        Segment(
                            id = UUID.randomUUID().toString(),
                            chunkIndex = 0,
                            startMs = 0L,
                            endMs = 3000L,
                            text = singleText
                        )
                    )
                }
            }
            segments
        }
    }
}

/**
 * Translation provider using OpenAI / LLM chat completion API
 * (OpenAI, DeepSeek, Groq, Ollama, etc.).
 */
class OpenAiCompatTranslateProvider(
    private val baseUrl: String = "https://api.openai.com/v1",
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : TranslateProvider {

    override val name: String = "OpenAI Compatible ($model)"
    override val isOffline: Boolean = false

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"
        val systemPrompt = "You are an expert real-time subtitle translator. " +
                "Translate the following speech segment accurately from $sourceLanguage to $targetLanguage. " +
                "Output ONLY the translated text without explanations, greetings, quotes, or markdown formatting."

        val jsonPayload = """
            {
                "model": "$model",
                "messages": [
                    {"role": "system", "content": ${escapeJson(systemPrompt)}},
                    {"role": "user", "content": ${escapeJson(text)}}
                ],
                "temperature": 0.3
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string()?.take(200) ?: "HTTP ${response.code}"
                throw IOException("Translation API call failed with code ${response.code}: $errorMsg")
            }

            val body = response.body?.string() ?: throw IOException("Empty translation response")
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(body).jsonObject
            val choices = root["choices"]?.jsonArray
            val firstChoice = choices?.firstOrNull()?.jsonObject
            val message = firstChoice?.get("message")?.jsonObject
            val content = message?.get("content")?.jsonPrimitive?.content?.trim() ?: ""
            content
        }
    }

    private fun escapeJson(str: String): String {
        val out = StringBuilder("\"")
        for (c in str) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(c)
            }
        }
        out.append("\"")
        return out.toString()
    }
}
