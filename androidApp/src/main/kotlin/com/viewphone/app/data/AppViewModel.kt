package com.viewphone.app.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 极简 DI 容器：本阶段只有 4 个依赖，手工装配比引 Hilt 更透明。 */
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

    /** 拉取到的模型列表缓存：key = presetId，用于编辑器里的"从列表选模型"。 */
    private val _modelLists = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val modelLists: StateFlow<Map<String, List<String>>> = _modelLists.asStateFlow()

    fun clearTestResult() {
        _testResult.value = null
    }

    fun testConnection(draft: ApiPreset) {
        viewModelScope.launch {
            _testing.value = true
            _testResult.value = null
            val key = container.keyStore.get(draft.id)
            val result = container.llm.listModels(draft, key)
            _testResult.value = result
            if (result is ConnectionTestResult.Success) {
                _modelLists.value = _modelLists.value + (draft.id to result.models)
            }
            _testing.value = false
        }
    }

    fun modelsFor(presetId: String): List<String> = _modelLists.value[presetId].orEmpty()

    /** 手填模型名（有些中转站不提供 /models）。 */
    fun rememberModels(presetId: String, models: List<String>) {
        _modelLists.value = _modelLists.value + (presetId to models)
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

    // ---------------- 用户与角色 ----------------

    fun saveUser(user: UserProfile) = container.store.upsertUser(user)
    fun deleteUser(id: String) = container.store.deleteUser(id)
    fun activateUser(id: String) = container.store.activateUser(id)

    fun saveCharacter(character: Character) = container.store.upsertCharacter(character)
    fun deleteCharacter(id: String) = container.store.deleteCharacter(id)

    /** 把系统相册选到的图片复制进私有目录；**只落文件、只存相对路径**（宪法 §一.1）。 */
    fun importImage(uri: Uri, kind: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val rel = withContext(Dispatchers.IO) {
                runCatching {
                    container.files.relativePath(container.files.importImage(uri, kind))
                }.getOrNull()
            }
            onDone(rel)
        }
    }

    fun imageFile(relativePath: String?): File? = relativePath?.let { container.files.resolve(it) }

    // ---------------- 会话与消息 ----------------

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _streaming = MutableStateFlow("")
    val streaming: StateFlow<String> = _streaming.asStateFlow()

    private val _streamingCharacterId = MutableStateFlow<String?>(null)
    val streamingCharacterId: StateFlow<String?> = _streamingCharacterId.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private var generateJob: Job? = null

    fun openConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        _messages.value = container.store.messages(conversationId)
        _streaming.value = ""
    }

    fun closeConversation() {
        generateJob?.cancel()
        generateJob = null
        _activeConversationId.value = null
        _messages.value = emptyList()
        _streaming.value = ""
        _generating.value = false
    }

    fun clearHistory(conversationId: String) {
        container.store.clearMessages(conversationId)
        _messages.value = container.store.messages(conversationId)
    }

    fun createConversation(userId: String, characterIds: List<String>): String? =
        container.store.createConversation(userId, characterIds)?.id

    /**
     * 发送一条消息并请求回复。
     * 群聊：本轮先让**第一个角色**回复（多角色调度在后续版本做，避免半成品）。
     */
    fun send(conversationId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _generating.value) return

        _activeConversationId.value = conversationId
        val conv = container.store.conversations.value.firstOrNull { it.id == conversationId } ?: return
        val responderId = conv.characterIds.firstOrNull() ?: return

        container.store.appendMessage(
            Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                sender = Sender.USER,
                text = trimmed,
                timestamp = System.currentTimeMillis(),
            )
        )
        _messages.value = container.store.messages(conversationId)
        requestReply(conversationId, responderId, conv.userId)
    }

    fun regenerate(conversationId: String) {
        if (_generating.value) return
        val conv = container.store.conversations.value.firstOrNull { it.id == conversationId } ?: return
        val responderId = conv.characterIds.firstOrNull() ?: return
        container.store.dropLastCharMessage(conversationId)
        _messages.value = container.store.messages(conversationId)
        requestReply(conversationId, responderId, conv.userId)
    }

    fun stopGenerating() {
        generateJob?.cancel()
        generateJob = null
        _generating.value = false
        flushStreaming()
    }

    private fun requestReply(conversationId: String, characterId: String, userId: String) {
        val character = container.store.characterById(characterId)
        if (character == null) {
            appendError(conversationId, "角色不存在")
            return
        }
        val preset = container.store.presetById(character.apiPresetId)
            ?: container.store.defaultTextPreset()
        if (preset == null) {
            appendError(conversationId, "还没有配置「文本」类 API：请到 设置 → API 配置 → 文本 里新建一套")
            return
        }
        val key = container.keyStore.get(preset.id)

        _generating.value = true
        _streaming.value = ""
        _streamingCharacterId.value = characterId

        generateJob = viewModelScope.launch {
            val user = container.store.userById(userId)
            val systemPrompt = buildSystemPrompt(character, user)
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
                if (sb.isEmpty()) appendError(conversationId, e.message ?: "请求失败")
            } finally {
                if (sb.isNotEmpty()) {
                    container.store.appendMessage(
                        Message(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversationId,
                            sender = Sender.CHAR,
                            senderCharacterId = characterId,
                            text = sb.toString(),
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                    _messages.value = container.store.messages(conversationId)
                }
                _streaming.value = ""
                _streamingCharacterId.value = null
                _generating.value = false
            }
        }
    }

    private fun flushStreaming() {
        val text = _streaming.value
        val convId = _activeConversationId.value
        val charId = _streamingCharacterId.value
        if (!text.isNullOrBlank() && convId != null) {
            container.store.appendMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    sender = Sender.CHAR,
                    senderCharacterId = charId,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                )
            )
            _messages.value = container.store.messages(convId)
        }
        _streaming.value = ""
        _streamingCharacterId.value = null
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
     * 组装 system。本版包含：用户人设（你是谁）+ 角色人设 + 基础约束。
     * 世界书 / 记忆 / 时间感知在后续版本接入（此处不写死结构，预留位置）。
     */
    private fun buildSystemPrompt(character: Character, user: UserProfile?): String = buildString {
        append("【角色】\n")
        append(character.persona.trim().ifBlank { "（未设置人设，请自然地扮演这个名字的角色）" })
        append("\n\n")
        if (user != null && user.persona.isNotBlank()) {
            append("【用户】\n")
            append(user.persona.trim())
            append("\n\n")
        }
        append("你现在通过手机和对方聊天。请始终以「")
        append(character.name.ifBlank { "角色" })
        append("」的身份说话，保持人设一致。")
        append("直接输出聊天气泡里的文字，不要输出旁白、括号动作、格式标记或任何解释。")
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(container) as T
            }
    }
}
