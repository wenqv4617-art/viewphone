package com.viewphone.app.data

import kotlinx.serialization.Serializable

/**
 * 第 1 轮的三类业务实体：API 预设 / 角色 / 会话 / 消息。
 *
 * 存储取捨（有意为之，不是偷懒）：
 *  - 第 1 轮用 JSON + SharedPreferences 持久化，**不引入 Room**。
 *    理由：本轮的数据量只有几十条角色、几百条消息，Room 会带来 KSP 与迁移成本
 *    （"用到哪个建哪个"）。等消息量上万、需要游标分页时再换 Room，
 *    届时字段名不变，迁移路径清晰。
 *  - **API Key 不在这里**：单独走 Android Keystore 加密存储（见 [KeyStore]），
 *    本模型只存 `hasKey` 之外的元数据，绝不落明文。
 */

/** 协议类型。第 1 轮实现前两种；后两种在 UI 上可见但会给出明确的"暂不支持"错误。 */
enum class ApiProtocol(val label: String) {
    OPENAI_COMPAT("OpenAI 兼容"),
    ANTHROPIC("Anthropic"),
    GEMINI("Gemini"),
    CUSTOM("自定义"),
}

@Serializable
data class ApiPreset(
    val id: String,
    val name: String,
    val protocol: String = ApiProtocol.OPENAI_COMPAT.name,
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Float = 0.8f,
    val maxTokens: Int = 2048,
    val stream: Boolean = true,
) {
    val protocolEnum: ApiProtocol
        get() = runCatching { ApiProtocol.valueOf(protocol) }.getOrDefault(ApiProtocol.OPENAI_COMPAT)

    /** 用户可读的地址摘要，用于列表展示。 */
    val baseUrlShort: String
        get() = baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

@Serializable
data class Character(
    val id: String,
    val name: String = "",
    /** 头像文件相对路径（沙盒私有目录内）。**只存路径，不存图片数据。** */
    val avatarPath: String? = null,
    /** 人设 / 系统提示词 */
    val persona: String = "",
    /** 绑定的 API 预设 id；为空则用第一套可用预设 */
    val apiPresetId: String? = null,
    val note: String = "",
    val createdAt: Long = 0L,
)

@Serializable
data class Conversation(
    val id: String,
    val characterId: String,
    val title: String = "",
    val lastMessageAt: Long = 0L,
    val lastMessagePreview: String = "",
)

@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    /** "user" | "char" | "system" */
    val sender: String,
    val text: String,
    val timestamp: Long = 0L,
    val isError: Boolean = false,
)

object Sender {
    const val USER = "user"
    const val CHAR = "char"
    const val SYSTEM = "system"
}

/** 测试连接的结果，直接用于 UI 提示（成功列出模型，失败显示真实原因）。 */
sealed interface ConnectionTestResult {
    data class Success(val models: List<String>) : ConnectionTestResult
    data class Failure(val message: String) : ConnectionTestResult
}
