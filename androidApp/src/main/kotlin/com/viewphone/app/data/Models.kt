package com.viewphone.app.data

import kotlinx.serialization.Serializable

/**
 * 微光机的业务实体。
 *
 * 本版相对上一版的**结构性变化**（用户明确要求）：
 *  1. API 按用途分成四类：文本 / 语音合成 / 生图 / 向量。不再只有"一套 API"。
 *  2. 角色库分「用户」（用户扮演的身份）与「角色」（AI），两者都可分组。
 *  3. 会话（对话）独立于角色：先选用户，再在联系人里导入角色，然后建会话（单聊/群聊）。
 *
 * 存储：JSON + SharedPreferences（数据量小）；API Key 一律走 [KeyStore] 加密，绝不入库。
 */

// ============================================================
// API：四类服务
// ============================================================

/** API 用途分类。用户要求这四类分开配置、各自可拉模型。 */
enum class ServiceKind(val label: String, val hint: String) {
    TEXT("文本", "对话用的语言模型"),
    TTS("语音合成", "让角色发语音"),
    IMAGE("生图", "让角色发图片"),
    VECTOR("向量", "记忆检索用的 embedding"),
}

/** 协议。仅列出真实实现或明确预留的项，不做"看起来支持"的假选项。 */
enum class ApiProtocol(val label: String, val kinds: Set<ServiceKind>) {
    OPENAI_COMPAT("OpenAI 兼容", setOf(ServiceKind.TEXT, ServiceKind.TTS, ServiceKind.IMAGE, ServiceKind.VECTOR)),
    ANTHROPIC("Anthropic", setOf(ServiceKind.TEXT)),
    GEMINI("Gemini", setOf(ServiceKind.TEXT)),
    MINIMAX_CN("MiniMax 国内版", setOf(ServiceKind.TTS)),
    MINIMAX_INTL("MiniMax 国际版", setOf(ServiceKind.TTS)),
    NOVA_AI("NovaAI 生图", setOf(ServiceKind.IMAGE)),
    CUSTOM("自定义", ServiceKind.entries.toSet()),
}

@Serializable
data class ApiPreset(
    val id: String,
    val name: String,
    /** [ServiceKind] 的名字；缺失时按 TEXT 处理（兼容上一版数据） */
    val kind: String = ServiceKind.TEXT.name,
    val protocol: String = ApiProtocol.OPENAI_COMPAT.name,
    val baseUrl: String = "",
    /** 文本模型名 / TTS 音色或模型 / 生图模型 / embedding 模型 */
    val model: String = "",
    val temperature: Float = 0.8f,
    val maxTokens: Int = 2048,
    val stream: Boolean = true,
    /** 分组（用户要求 API 也能分组管理） */
    val group: String = "",
) {
    val kindEnum: ServiceKind
        get() = runCatching { ServiceKind.valueOf(kind) }.getOrDefault(ServiceKind.TEXT)

    val protocolEnum: ApiProtocol
        get() = runCatching { ApiProtocol.valueOf(protocol) }.getOrDefault(ApiProtocol.OPENAI_COMPAT)

    val baseUrlShort: String
        get() = baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

// ============================================================
// 角色库：用户 与 角色
// ============================================================

/**
 * 「用户」= 用户在小手机里扮演的身份（旧项目的"面具"）。
 * 聊天·主页选中某个用户后，后续所有会话都以这个身份进行。
 */
@Serializable
data class UserProfile(
    val id: String,
    val name: String = "",
    val avatarPath: String? = null,
    /** 用户人设（会被注入提示词的"用户是谁"部分） */
    val persona: String = "",
    val group: String = "",
    val isActive: Boolean = false,
    val createdAt: Long = 0L,
)

@Serializable
data class Character(
    val id: String,
    val name: String = "",
    val avatarPath: String? = null,
    val persona: String = "",
    /** 绑定的**文本**类 API 预设 id */
    val apiPresetId: String? = null,
    /** 可选的语音/生图预设绑定（留空表示不启用） */
    val ttsPresetId: String? = null,
    val imagePresetId: String? = null,
    val group: String = "",
    val note: String = "",
    val createdAt: Long = 0L,
)

// ============================================================
// 会话：单聊 / 群聊
// ============================================================

@Serializable
data class Conversation(
    val id: String,
    /** 以哪个用户的身份进行 */
    val userId: String,
    /** 单聊时为 1 个角色；群聊时多个 */
    val characterIds: List<String> = emptyList(),
    val isGroup: Boolean = false,
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
    /** 群聊时标识是哪个角色发的 */
    val senderCharacterId: String? = null,
    val text: String,
    val timestamp: Long = 0L,
    val isError: Boolean = false,
)

object Sender {
    const val USER = "user"
    const val CHAR = "char"
    const val SYSTEM = "system"
}

/** 测试连接的结果。 */
sealed interface ConnectionTestResult {
    data class Success(val models: List<String>) : ConnectionTestResult
    data class Failure(val message: String) : ConnectionTestResult
}
