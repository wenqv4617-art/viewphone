package com.viewphone.app.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 应用级容器（极简 DI）。
 *
 * 不引入 Hilt：第 1 轮只有 3 个依赖，手工装配更透明（"用到哪个建哪个"）。
 */
class AppContainer(context: Context) {
    val store = Store(context)
    val keyStore = KeyStore(context)
    val llm = LlmHttpClient()
    val files = FileStore(context)
}

class AppViewModel(private val container: AppContainer) : ViewModel() {

    val store: Store = container.store

    // ---------------- 测试连接 ----------------

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _testResult = MutableStateFlow<ConnectionTestResult?>(null)
    val testResult: StateFlow<ConnectionTestResult?> = _testResult.asStateFlow()

    fun clearTestResult() {
        _testResult.value = null
    }

    /** 用当前编辑中的预设（未落库也能测）真实发起一次请求。 */
    fun testConnection(draft: ApiPreset) {
        viewModelScope.launch {
            _testing.value = true
            _testResult.value = null
            // 未填 Key 时，尝试用已保存的（编辑既有预设的场景）
            val key = container.keyStore.get(draft.id)
            _testResult.value = container.llm.listModels(draft, key)
            _testing.value = false
        }
    }

    // ---------------- 预设 ----------------

    fun savePreset(preset: ApiPreset, apiKey: String?) {
        container.store.upsertPreset(preset)
        if (apiKey != null) container.keyStore.save(preset.id, apiKey)
    }

    fun deletePreset(id: String) {
        container.store.deletePreset(id)
        container.keyStore.delete(id)
    }

    fun maskedKey(presetId: String): String = container.keyStore.masked(presetId)

    fun hasKey(presetId: String): Boolean = container.keyStore.has(presetId)

    // ---------------- 角色 ----------------

    fun saveCharacter(character: Character) {
        container.store.upsertCharacter(character)
    }

    fun deleteCharacter(id: String) {
        container.store.deleteCharacter(id)
    }

    /**
     * 从系统相册选到的图片复制进私有目录。
     * **只落文件、只存路径**——绝不把图片数据写进数据库（宪法 §一.1）。
     */
    fun importAvatar(uri: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching { container.files.importImage(uri, "avatar") }.getOrNull()
            }
            onDone(path?.let { container.files.relativePath(it) })
        }
    }

    fun avatarFile(relativePath: String?): File? =
        relativePath?.let { container.files.resolve(it) }

    // ---------------- 会话与消息 ----------------

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    /** 流式输出中的草稿（尚未落库），UI 用它做打字机效果。 */
    private val _streaming = MutableStateFlow("")
    val streaming: StateFlow<String> = _streaming.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private var generateJob: kotlinx.coroutines.Job? = null

    fun openConversation(characterId: String) {
        val conv = container.store.conversationFor(characterId)
        _activeConversationId.value = conv.id
        _messages.value = container.store.messages(conv.id)
        _streaming.value = ""
    }

    fun closeConversation() {
        generateJob?.cancel()
        _activeConversationId.value = null
        _messages.value = emptyList()
        _streaming.value = ""
        _generating.value = false
    }

    fun clearHistory(conversationId: String) {
        container.store.clearMessages(conversationId)
        _messages.value = container.store.messages(conversationId)
    }

    /**
     * 发一条用户消息并请求回复。
     *
     * 流程：落库用户消息 → 组装 system+history → 流式请求 → 逐块更新草稿 → 完成后落库角色消息。
     * 任何失败都以一条 isError 的系统消息呈现，**不吞异常**。
     */
    fun send(characterId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _generating.value) return

        val conv = container.store.conversationFor(characterId)
        _activeConversationId.value = conv.id

        val userMsg = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conv.id,
            sender = Sender.USER,
            text = trimmed,
            timestamp = System.currentTimeMillis(),
        )
        container.store.appendMessage(userMsg)
        _messages.value = container.store.messages(conv.id)

        requestReply(characterId, conv.id)
    }

    /** 重新生成：丢掉上一条角色回复再请求一次。 */
    fun regenerate(characterId: String) {
        val convId = _activeConversationId.value ?: return
        if (_generating.value) return
        container.store.dropLastCharMessage(convId)
        _messages.value = container.store.messages(convId)
        requestReply(characterId, convId)
    }

    fun stopGenerating() {
        generateJob?.cancel()
        generateJob = null
        _generating.value = false
        // 已生成的部分仍然落库，避免用户白等
        flushStreamingToStore()
    }

    private fun requestReply(characterId: String, conversationId: String) {
        val character = container.store.characterById(characterId)
        if (character == null) {
            appendError(conversationId, "角色不存在")
            return
        }
        val preset = container.store.presetById(character.apiPresetId)
        if (preset == null) {
            appendError(conversationId, "还没有配置 API：请到 设置 → API 配置 里新建一套并绑定到这个角色")
            return
        }
        val key = container.keyStore.get(preset.id)
        if (key.isBlank() && preset.protocolEnum == ApiProtocol.OPENAI_COMPAT) {
            appendError(conversationId, "这套 API 还没有填 Key：请到 设置 → API 配置 → ${preset.name} 里填写")
            return
        }

        _generating.value = true
        _streaming.value = ""

        generateJob = viewModelScope.launch {
            val systemPrompt = buildSystemPrompt(character)
            // 只带最近 40 条，避免第 1 轮就把上下文撑爆（token 预算在后续版本做）
            val history = container.store.messages(conversationId)
                .filter { !it.isError }
                .takeLast(40)

            val sb = StringBuilder()
            try {
                container.llm.stream(preset, key, systemPrompt, history).collect { chunk ->
                    sb.append(chunk)
                    _streaming.value = sb.toString()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (sb.isEmpty()) {
                    appendError(conversationId, e.message ?: "请求失败")
                }
            } finally {
                if (sb.isNotEmpty()) {
                    container.store.appendMessage(
                        Message(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversationId,
                            sender = Sender.CHAR,
                            text = sb.toString(),
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                    _messages.value = container.store.messages(conversationId)
                }
                _streaming.value = ""
                _generating.value = false
            }
        }
    }

    private fun flushStreamingToStore() {
        val text = _streaming.value
        val convId = _activeConversationId.value ?: return
        if (text.isNotBlank()) {
            container.store.appendMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    sender = Sender.CHAR,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                )
            )
            _messages.value = container.store.messages(convId)
        }
        _streaming.value = ""
    }

    private fun appendError(conversationId: String, message: String) {
        container.store.appendMessage(
            Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                sender = Sender.SYSTEM,
                text = message,
                timestamp = System.currentTimeMillis(),
                isError = true,
            )
        )
        _messages.value = container.store.messages(conversationId)
    }

    /**
     * 组装发给模型的 system。第 1 轮只含角色人设 + 基础行为约束。
     * 世界书 / 记忆 / 时间感知在后续版本接（此处预留位置，不写死结构）。
     */
    private fun buildSystemPrompt(character: Character): String = buildString {
        if (character.persona.isNotBlank()) {
            append(character.persona.trim())
            append("\n\n")
        }
        append("你正在用手机和用户聊天。请始终以「")
        append(character.name.ifBlank { "角色" })
        append("」的身份说话，保持人设一致。")
        append("直接输出聊天气泡里的内容，不要输出旁白、括号动作或格式标记。")
    }

    /** 便捷取用：给 ViewModel 造一个工厂。 */
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(container) as T
            }
    }
}
