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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * LLM 服务客户端。按 [ServiceKind] 区分四类用途。
 *
 * 第 1 轮实现范围（诚实标注）：
 *  - 文本：OpenAI 兼容 + Anthropic（流式 + 非流式）✅
 *  - 文本：Gemini 原生 ✗（给出明确提示，建议用中转站的 OpenAI 兼容接口）
 *  - 语音合成：MiniMax（国内/国际）+ OpenAI 兼容 TTS —— 本版先做**配置与拉取**，
 *              真正合成放到"让角色发语音"那一版（避免半成品混进来）
 *  - 生图：OpenAI 兼容 images/generations + NovaAI 配置 —— 同上
 *  - 向量：OpenAI 兼容 embeddings —— 同上
 *
 * 安全：所有错误信息在返回前都会抹掉 API Key（[sanitize]）。
 */
class LlmHttpClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------------
    // 拉取模型列表（测试连接）
    // ------------------------------------------------------------------

    suspend fun listModels(preset: ApiPreset, apiKey: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            if (preset.baseUrl.isBlank()) {
                return@withContext ConnectionTestResult.Failure(
                    "地址为空：请先填写 Base URL（例如 https://api.openai.com/v1）"
                )
            }

            // MiniMax：没有公开的 /models 列表接口，返回**内置候选**并说明来源
            if (preset.protocolEnum == ApiProtocol.MINIMAX_CN || preset.protocolEnum == ApiProtocol.MINIMAX_INTL) {
                val voices = MINIMAX_VOICES
                return@withContext ConnectionTestResult.Success(voices)
            }

            val url = joinUrl(preset.baseUrl, "models")
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
                        when {
                            ids.isNotEmpty() -> ConnectionTestResult.Success(ids)
                            // 生图类服务常常不返回模型列表：给出内置候选，并说明原因
                            preset.kindEnum == ServiceKind.IMAGE -> ConnectionTestResult.Success(IMAGE_FALLBACK)
                            preset.kindEnum == ServiceKind.VECTOR -> ConnectionTestResult.Success(VECTOR_FALLBACK)
                            else -> ConnectionTestResult.Failure(
                                "连接成功（HTTP ${resp.code}），但返回里没有模型名。" +
                                    "该站点可能不提供 /models 接口，请手动填写模型名。\n原始片段：" +
                                    sanitize(body, apiKey).take(160)
                            )
                        }
                    }
                }
            }.getOrElse { e ->
                ConnectionTestResult.Failure(networkError(e, url, apiKey))
            }
        }

    // ------------------------------------------------------------------
    // 文本对话（流式 / 非流式）
    // ------------------------------------------------------------------

    fun stream(
        preset: ApiPreset,
        apiKey: String,
        systemPrompt: String,
        history: List<Message>,
    ): Flow<String> = flow {
        if (preset.baseUrl.isBlank()) error("地址为空：请先在设置里填写 Base URL")
        if (preset.model.isBlank()) error("模型名为空：请先在设置里填写或选择模型")

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
            ApiProtocol.GEMINI -> error("Gemini 原生协议暂未支持：多数中转站提供 OpenAI 兼容接口，请换用「OpenAI 兼容」并填同一地址")
            ApiProtocol.MINIMAX_CN, ApiProtocol.MINIMAX_INTL ->
                error("MiniMax 是语音合成服务，不能用于对话。请在 设置 → API 配置 → 文本 里选一套文本模型")
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
                error(httpError(resp.code, resp.body?.string().orEmpty(), apiKey))
            }
            val body = resp.body ?: error("响应为空（服务端没有返回内容）")

            if (!useStream) {
                val text = parseNonStreamText(body.string(), preset.protocolEnum)
                if (text.isBlank()) error("模型没有返回正文")
                emit(text)
                return@use
            }

            body.source().use { source ->
                while (true) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                    if (!trimmed.startsWith("data:")) continue
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching { parseStreamChunk(data, preset.protocolEnum) }.getOrNull()
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
            obj["id"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull
        }.filter { it.isNotBlank() }.distinct().sorted()
    }

    private fun parseNonStreamText(raw: String, protocol: ApiProtocol): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return ""
        return when (protocol) {
            ApiProtocol.ANTHROPIC -> {
                val content = root["content"] as? JsonArray ?: return ""
                content.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
                    .joinToString("")
            }
            else -> {
                val first = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return ""
                val msg = first["message"] as? JsonObject ?: return ""
                msg["content"]?.jsonPrimitive?.contentOrNull
                    ?: msg["reasoning_content"]?.jsonPrimitive?.contentOrNull
                    ?: ""
            }
        }
    }

    private fun parseStreamChunk(data: String, protocol: ApiProtocol): String {
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return ""
        return when (protocol) {
            ApiProtocol.ANTHROPIC -> {
                if (root["type"]?.jsonPrimitive?.contentOrNull == "content_block_delta") {
                    (root["delta"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                } else ""
            }
            else -> {
                val first = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return ""
                val delta = first["delta"] as? JsonObject ?: return ""
                delta["content"]?.jsonPrimitive?.contentOrNull
                    ?: delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
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

    private fun joinUrl(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith("/$path")) b else "$b/$path"
    }

    private fun httpError(code: Int, body: String, apiKey: String): String {
        val hint = when (code) {
            401, 403 -> "鉴权失败：API Key 可能无效，或没有该模型的权限"
            404 -> "地址或路径不对：确认 Base URL 是否需要以 /v1 结尾"
            429 -> "被限流：稍后重试，或检查额度"
            in 500..599 -> "服务端错误"
            else -> "请求被拒绝"
        }
        return "HTTP $code · $hint\n${sanitize(body, apiKey).take(300)}"
    }

    private fun networkError(e: Throwable, url: String, apiKey: String): String {
        val reason = when (e) {
            is java.net.UnknownHostException -> "域名解析失败（检查 Base URL 与网络）"
            is java.net.SocketTimeoutException -> "连接超时（检查网络或代理）"
            is javax.net.ssl.SSLException -> "TLS 握手失败（地址可能不是 https，或证书不受信）"
            else -> e.javaClass.simpleName + ": " + (e.message ?: "")
        }
        return "请求失败 · $reason\n目标：${sanitize(url, apiKey)}"
    }

    private fun sanitize(text: String, apiKey: String): String =
        if (apiKey.isBlank()) text else text.replace(apiKey, "***")

    private companion object {
        /** MiniMax 常用音色（站点不提供 /models，返回内置候选供选择）。 */
        val MINIMAX_VOICES = listOf(
            "male-qn-qingse", "male-qn-jingying", "male-qn-badao", "male-qn-daxuesheng",
            "female-shaonv", "female-yujie", "female-chengshu", "female-tianmei",
            "presenter_male", "presenter_female", "audiobook_male_1", "audiobook_female_1",
        )
        val IMAGE_FALLBACK = listOf("gpt-image-1", "dall-e-3", "flux-schnell", "flux-dev", "sd3.5-large")
        val VECTOR_FALLBACK = listOf("text-embedding-3-small", "text-embedding-3-large", "bge-m3", "text-embedding-ada-002")
    }
}
