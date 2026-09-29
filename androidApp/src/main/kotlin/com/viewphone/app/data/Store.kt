package com.viewphone.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 第 1 轮的持久化：JSON + SharedPreferences。
 *
 * 为什么不直接上 Room：本轮数据规模是"几十条角色 / 几百条消息"，
 * Room 带来的 KSP + 迁移成本超过收益（"用到哪个建哪个"）。
 * 消息量上万需要游标分页时再换，字段名不变，迁移路径清晰（见 Models.kt 注释）。
 *
 * 并发：所有写操作串行化在同一把锁内，内部持有内存态并以 StateFlow 广播，
 * 调用方（ViewModel）直接 collect 即可，不需要手动刷新。
 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("viewphone_store", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val lock = Any()

    private val _presets = MutableStateFlow(loadPresets())
    val presets: StateFlow<List<ApiPreset>> = _presets.asStateFlow()

    private val _characters = MutableStateFlow(loadCharacters())
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    private val _conversations = MutableStateFlow(loadConversations())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    // ---------------- API 预设 ----------------

    fun upsertPreset(preset: ApiPreset) {
        synchronized(lock) {
            val list = _presets.value.toMutableList()
            val idx = list.indexOfFirst { it.id == preset.id }
            if (idx >= 0) list[idx] = preset else list.add(preset)
            _presets.value = list
            write(KEY_PRESETS, list)
        }
    }

    fun deletePreset(id: String) {
        synchronized(lock) {
            _presets.value = _presets.value.filterNot { it.id == id }
            write(KEY_PRESETS, _presets.value)
            // 解绑引用了该预设的角色，避免悬空引用
            val updated = _characters.value.map { if (it.apiPresetId == id) it.copy(apiPresetId = null) else it }
            _characters.value = updated
            write(KEY_CHARACTERS, updated)
        }
    }

    fun presetById(id: String?): ApiPreset? =
        if (id == null) _presets.value.firstOrNull() else _presets.value.firstOrNull { it.id == id }

    // ---------------- 角色 ----------------

    fun upsertCharacter(character: Character) {
        synchronized(lock) {
            val list = _characters.value.toMutableList()
            val idx = list.indexOfFirst { it.id == character.id }
            if (idx >= 0) list[idx] = character else list.add(character)
            _characters.value = list
            write(KEY_CHARACTERS, list)
        }
    }

    fun deleteCharacter(id: String) {
        synchronized(lock) {
            _characters.value = _characters.value.filterNot { it.id == id }
            write(KEY_CHARACTERS, _characters.value)
        }
    }

    fun characterById(id: String): Character? = _characters.value.firstOrNull { it.id == id }

    // ---------------- 会话与消息 ----------------

    fun conversationFor(characterId: String): Conversation {
        synchronized(lock) {
            _conversations.value.firstOrNull { it.characterId == characterId }?.let { return it }
            val conv = Conversation(
                id = UUID.randomUUID().toString(),
                characterId = characterId,
                title = characterById(characterId)?.name.orEmpty(),
                lastMessageAt = System.currentTimeMillis(),
            )
            _conversations.value = _conversations.value + conv
            write(KEY_CONVERSATIONS, _conversations.value)
            return conv
        }
    }

    fun updateConversation(conv: Conversation) {
        synchronized(lock) {
            val list = _conversations.value.toMutableList()
            val idx = list.indexOfFirst { it.id == conv.id }
            if (idx >= 0) list[idx] = conv else list.add(conv)
            _conversations.value = list
            write(KEY_CONVERSATIONS, list)
        }
    }

    fun messages(conversationId: String): List<Message> = loadMessages(conversationId)

    fun appendMessage(message: Message) {
        synchronized(lock) {
            val list = loadMessages(message.conversationId).toMutableList()
            list.add(message)
            write(KEY_MESSAGES_PREFIX + message.conversationId, list)

            // 同步会话摘要
            val convList = _conversations.value.toMutableList()
            val idx = convList.indexOfFirst { it.id == message.conversationId }
            if (idx >= 0) {
                val preview = message.text.replace('\n', ' ').take(40)
                convList[idx] = convList[idx].copy(
                    lastMessageAt = message.timestamp,
                    lastMessagePreview = preview,
                )
                _conversations.value = convList
                write(KEY_CONVERSATIONS, convList)
            }
        }
    }

    /** 重新生成用：删除某会话最后一条角色消息。 */
    fun dropLastCharMessage(conversationId: String) {
        synchronized(lock) {
            val list = loadMessages(conversationId).toMutableList()
            val lastCharIdx = list.indexOfLast { it.sender == Sender.CHAR }
            if (lastCharIdx >= 0) {
                list.removeAt(lastCharIdx)
                write(KEY_MESSAGES_PREFIX + conversationId, list)
            }
        }
    }

    /** 清空某个会话的历史。 */
    fun clearMessages(conversationId: String) {
        synchronized(lock) {
            write(KEY_MESSAGES_PREFIX + conversationId, emptyList<Message>())
            val convList = _conversations.value.toMutableList()
            val idx = convList.indexOfFirst { it.id == conversationId }
            if (idx >= 0) {
                convList[idx] = convList[idx].copy(lastMessagePreview = "")
                _conversations.value = convList
                write(KEY_CONVERSATIONS, convList)
            }
        }
    }

    // ---------------- 内部 ----------------

    private fun loadPresets(): List<ApiPreset> = read(KEY_PRESETS)
    private fun loadCharacters(): List<Character> = read(KEY_CHARACTERS)
    private fun loadConversations(): List<Conversation> = read(KEY_CONVERSATIONS)
    private fun loadMessages(conversationId: String): List<Message> = read(KEY_MESSAGES_PREFIX + conversationId)

    private inline fun <reified T> read(key: String): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<T>>(raw) }.getOrElse { emptyList() }
    }

    private inline fun <reified T> write(key: String, value: List<T>) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    private companion object {
        const val KEY_PRESETS = "api_presets"
        const val KEY_CHARACTERS = "characters"
        const val KEY_CONVERSATIONS = "conversations"
        const val KEY_MESSAGES_PREFIX = "messages_"
    }
}
