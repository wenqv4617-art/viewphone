package com.viewphone.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * LLM HTTP 客户端。第 1 轮覆盖 OpenAI 兼容与 Anthropic 两种协议。
 *
 * 为什么用 OkHttp 而不是 Ktor：OkHttp 在 catalog 里已列、体积小、SSE 逐行读取直接可用。
 *
 * 安全：任何错误信息在返回前都会**抹掉 API Key**（见 [sanitize]），避免 Key 经由报错泄漏到界面或日志。
 */
class LlmHttpClient {

    private val client = OkHttpClient.Builder()
        // 连接/写入要快失败；读取要长（流式回复可能很久不说话）
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------------
    // 模型列表（测试连接）
    // ------------------------------------------------------------------

    suspend fun listModels(preset: ApiPreset, apiKey: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            if (preset.baseUrl.isBlank()) {
                return@withContext ConnectionTestResult.Failure("地址为空：请先填写 Base URL（例如 https://api.openai.com/v1）")
            }
            val url = when (preset.protocolEnum) {
                ApiProtocol.OPENAI_COMPAT, ApiProtocol.CUSTOM -> joinUrl(preset.baseUrl, "models")
                ApiProtocol.ANTHROPIC -> joinUrl(preset.baseUrl, "models")
                ApiProtocol.GEMINI -> joinUrl(preset.baseUrl, "models")
            }
            val req = Request.Builder()
                .url(url)
                .apply {
                    when (preset.protocolEnum) {
                        ApiProtocol.ANTHROPIC -> {
                            header("x-api-key", apiKey)
                            header("anthropic-version", "2023-06-01")
                        }
                        else -> header("Authorization", "Bearer $apiKey")
                    }
                }
                .get()
                .build()

            runCatching {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        ConnectionTestResult.Failure(httpError(resp.code, body, apiKey))
                    } else {
                        val ids = parseModelIds(body)
                        if (ids.isEmpty()) {
                            ConnectionTestResult.Failure("连接成功（HTTP ${resp.code}），但返回里没有解析到模型名。原始响应片段：${sanitize(body, apiKey).take(160)}")
                        } else {
                            ConnectionTestResult.Success(ids)
                        }
                    }
                }
            }.getOrElse { e ->
                ConnectionTestResult.Failure(networkError(e, url, apiKey))
            }
        }

    // ------------------------------------------------------------------
    // 对话（流式与非流式）
    // ------------------------------------------------------------------

    /**
     * 发起一次对话，**逐块**产出模型输出。
     * 调用方负责累积与落库；本函数不做任何持久化。
     */
    fun stream(
        preset: ApiPreset,
        apiKey: String,
        systemPrompt: String,
        history: List<Message>,
    ): Flow<String> = flow {
        if (preset.baseUrl.isBlank()) error("地址为空：请先在设置里填写 Base URL")
        if (preset.model.isBlank()) error("模型名为空：请先在设置里填写模型名")

        val useStream = preset.stream
        val url: String
        val payload: String
        val reqBuilder: Request.Builder

        when (preset.protocolEnum) {
            ApiProtocol.ANTHROPIC -> {
                url = joinUrl(preset.baseUrl, "messages")
                payload = buildAnthropicPayload(preset, systemPrompt, history, useStream)
                reqBuilder = Request.Builder()
                    .url(url)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
            }
            ApiProtocol.GEMINI -> error("Gemini 协议本轮暂未支持，请改用 OpenAI 兼容（多数中转站都兼容）")
            else -> {
                url = joinUrl(preset.baseUrl, "chat/completions")
                payload = buildOpenAiPayload(preset, systemPrompt, history, useStream)
                reqBuilder = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
            }
        }

        val req = reqBuilder
            .post(payload.toRequestBody(jsonMedia))
            .apply { if (useStream) header("Accept", "text/event-stream") }
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                error(httpError(resp.code, errBody, apiKey))
            }
            val body = resp.body ?: error("响应为空（服务端没有返回内容）")

            if (!useStream) {
                val text = parseNonStreamText(body.string(), preset.protocolEnum)
                if (text.isBlank()) error("模型没有返回正文")
                emit(text)
                return@use
            }

            // 流式：逐行读 SSE
            body.source().use { source ->
                while (true) {
                    coroutineContext.ensureActive() // 支持取消
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                    if (!trimmed.startsWith("data:")) continue
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching {
                        parseStreamChunk(data, preset.protocolEnum)
                    }.getOrNull()
                    if (!chunk.isNullOrEmpty()) emit(chunk)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    private fun parseModelIds(raw: String): List<String> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val arr = (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            obj["id"]?.jsonPrimitive?.contentOrNullSafe()
                ?: obj["name"]?.jsonPrimitive?.contentOrNullSafe()
        }.filter { it.isNotBlank() }.sorted()
    }

    private fun parseNonStreamText(raw: String, protocol: ApiProtocol): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return ""
        return when (protocol) {
            ApiProtocol.ANTHROPIC -> {
                val content = root["content"] as? JsonArray ?: return ""
                content.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNullSafe() }
                    .joinToString("")
            }
            else -> {
                val choices = root["choices"] as? JsonArray ?: return ""
                val first = choices.firstOrNull() as? JsonObject ?: return ""
                val msg = first["message"] as? JsonObject ?: return ""
                msg["content"]?.jsonPrimitive?.contentOrNullSafe()
                    ?: msg["reasoning_content"]?.jsonPrimitive?.contentOrNullSafe()
                    ?: ""
            }
        }
    }

    private fun parseStreamChunk(data: String, protocol: ApiProtocol): String {
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return ""
        return when (protocol) {
            ApiProtocol.ANTHROPIC -> {
                val type = root["type"]?.jsonPrimitive?.contentOrNullSafe()
                if (type == "content_block_delta") {
                    val delta = root["delta"] as? JsonObject ?: return ""
                    delta["text"]?.jsonPrimitive?.contentOrNullSafe() ?: ""
                } else ""
            }
            else -> {
                val choices = root["choices"] as? JsonArray ?: return ""
                val first = choices.firstOrNull() as? JsonObject ?: return ""
                val delta = first["delta"] as? JsonObject ?: return ""
                delta["content"]?.jsonPrimitive?.contentOrNullSafe()
                    ?: delta["reasoning_content"]?.jsonPrimitive?.contentOrNullSafe()
                    ?: ""
            }
        }
    }

    private fun buildOpenAiPayload(
        preset: ApiPreset,
        systemPrompt: String,
        history: List<Message>,
        stream: Boolean,
    ): String = buildJsonObject {
        put("model", preset.model)
        put("temperature", preset.temperature)
        put("max_tokens", preset.maxTokens)
        put("stream", stream)
        putJsonArray("messages") {
            if (systemPrompt.isNotBlank()) {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            history.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.sender == Sender.USER) "user" else "assistant")
                    put("content", m.text)
                })
            }
        }
    }.toString()

    private fun buildAnthropicPayload(
        preset: ApiPreset,
        systemPrompt: String,
        history: List<Message>,
        stream: Boolean,
    ): String = buildJsonObject {
        put("model", preset.model)
        put("max_tokens", preset.maxTokens)
        put("temperature", preset.temperature)
        put("stream", stream)
        if (systemPrompt.isNotBlank()) put("system", systemPrompt)
        putJsonArray("messages") {
            history.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.sender == Sender.USER) "user" else "assistant")
                    put("content", m.text)
                })
            }
        }
    }.toString()

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 拼接 URL：base 已在 settings 里说明，本项目统一按"base + 标准路径"处理。 */
    private fun joinUrl(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        return when {
            b.endsWith("/$path") -> b
            path == "chat/completions" && b.endsWith("/chat/completions") -> b
            else -> "$b/$path"
        }
    }

    private fun httpError(code: Int, body: String, apiKey: String): String {
        val hint = when (code) {
            401, 403 -> "鉴权失败：API Key 可能无效或没有该模型的权限"
            404 -> "地址或路径不对：确认 Base URL 是否需要以 /v1 结尾"
            429 -> "被限流：稍后重试，或检查额度"
            in 500..599 -> "服务端错误"
            else -> "请求被拒绝"
        }
        val bodyPart = sanitize(body, apiKey).take(300)
        return "HTTP $code · $hint\n$bodyPart"
    }

    private fun networkError(e: Throwable, url: String, apiKey: String): String {
        val reason = when (e) {
            is java.net.UnknownHostException -> "域名解析失败（检查 Base URL 拼写与网络）"
            is java.net.SocketTimeoutException -> "连接超时（检查网络或代理）"
            is javax.net.ssl.SSLException -> "TLS 握手失败（地址可能不是 https 或证书不受信）"
            else -> e.javaClass.simpleName + ": " + (e.message ?: "")
        }
        return "请求失败 · $reason\n目标：${sanitize(url, apiKey)}"
    }

    /** 错误信息里绝不能出现 Key。 */
    private fun sanitize(text: String, apiKey: String): String =
        if (apiKey.isBlank()) text else text.replace(apiKey, "***")
}

/** JsonPrimitive 的安全取值：非字符串类型也尽量取到内容。 */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()
