package com.quntodo.app.ai

import com.quntodo.app.rules.RuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class AiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(
        apiKey: String,
        baseUrl: String,
        model: String,
        groupName: String,
        messages: List<RuleEngine.SourceMessage>,
        now: Long = System.currentTimeMillis()
    ): Result<AiSummary> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("还没有填写 API Key"))
        if (messages.isEmpty()) return@withContext Result.failure(IllegalStateException("没有可整理的消息"))

        val validIds = messages.map { it.id }.toSet()
        val userContent = AiPrompt.userContent(groupName, now, messages)
        val url = endpoint(baseUrl)

        val requestBody = buildJsonObject {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", 2048)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", AiPrompt.SYSTEM)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userContent)
                })
            })
        }.toString()

        val result = runCatching {
            val response = execute(url, apiKey, requestBody)
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IllegalStateException(mapHttpError(response.code, errorBody))
            }
            val bodyText = response.body?.string().orEmpty()
            val content = extractContent(bodyText)
            if (content.isBlank()) throw IllegalStateException("AI 返回内容为空，请稍后重试")
            AiResponseParser.parse(content, validIds, now = now)
        }

        result.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalStateException(friendlyError(it))) }
        )
    }

    suspend fun testConnection(
        apiKey: String,
        baseUrl: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("请先填写 API Key"))
        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 8)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "你好")
                })
            })
        }.toString()
        runCatching {
            val response = execute(endpoint(baseUrl), apiKey, requestBody)
            response.use {
                if (!it.isSuccessful) {
                    throw IllegalStateException(mapHttpError(it.code, it.body?.string().orEmpty()))
                }
            }
            "连接成功，模型可用"
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalStateException(friendlyError(it))) }
        )
    }

    private fun execute(url: String, apiKey: String, body: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return client.newCall(request).execute()
    }

    private fun extractContent(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return ""
        val message = choice["message"]?.jsonObject ?: return ""
        return message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun endpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.contains("/chat/completions")) return trimmed
        if (trimmed.endsWith("/v1")) return "$trimmed/chat/completions"
        return "$trimmed/chat/completions"
    }

    private fun mapHttpError(code: Int, body: String): String = when (code) {
        400 -> "请求被拒绝（400），请检查模型名或接口地址"
        401, 403 -> "API Key 无效或没有权限（$code）"
        402 -> "账户余额不足（402）"
        404 -> "接口地址不存在（404），请检查基础地址"
        429 -> "请求太频繁，请稍后再试（429）"
        in 500..599 -> "AI 服务暂时不可用（$code）"
        else -> "请求失败（$code）：${body.take(120)}"
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is SocketTimeoutException -> "连接 AI 服务超时，请检查网络后重试"
        is IOException -> "网络连接失败，请检查手机网络后重试"
        else -> error.message ?: "AI 调用失败，请稍后重试"
    }
}
