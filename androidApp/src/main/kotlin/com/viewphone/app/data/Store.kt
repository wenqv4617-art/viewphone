package com.viewphone.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 持久化：JSON + SharedPreferences。
 *
 * 为什么不直接上 Room：本阶段数据规模是"几十条角色 / 几百条消息"，
 * Room 的 KSP + 迁移成本超过收益（"用到哪个建哪个"）。
 * 消息量上万、需要游标分页时再换，字段名不变，迁移路径清晰。
 *
 * 并发：写操作串行化；内部持有内存态并以 StateFlow 广播，UI 直接 collect，无需手动刷新。
 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("viewphone_store", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()

    private val _presets = MutableStateFlow(load<ApiPreset>(KEY_PRESETS))
    val presets: StateFlow<List<ApiPreset>> = _presets.asStateFlow()

    private val _users = MutableStateFlow(load<UserProfile>(KEY_USERS))
    val users: StateFlow<List<UserProfile>> = _users.asStateFlow()

    private val _characters = MutableStateFlow(load<Character>(KEY_CHARACTERS))
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    private val _conversations = MutableStateFlow(load<Conversation>(KEY_CONVERSATIONS))
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    // ---------------- API 预设 ----------------

    fun presetsOf(kind: ServiceKind): List<ApiPreset> = _presets.value.filter { it.kindEnum == kind }

    /** 只保留该用途可用的协议（UI 用它过滤下拉项，避免"看起来支持"的假选项）。 */
    fun protocolsFor(kind: ServiceKind): List<ApiProtocol> =
        ApiProtocol.entries.filter { kind in it.kinds }

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
            // 解绑引用，避免悬空
            val cs = _characters.value.map {
                it.copy(
                    apiPresetId = it.apiPresetId.takeIf { p -> p != id },
                    ttsPresetId = it.ttsPresetId.takeIf { p -> p != id },
                    imagePresetId = it.imagePresetId.takeIf { p -> p != id },
                )
            }
            _characters.value = cs
            write(KEY_CHARACTERS, cs)
        }
    }

    fun presetById(id: String?): ApiPreset? = id?.let { pid -> _presets.value.firstOrNull { it.id == pid } }

    /** 文本类预设未显式绑定时，退回到第一套文本预设。 */
    fun defaultTextPreset(): ApiPreset? = presetsOf(ServiceKind.TEXT).firstOrNull()

    // ---------------- 用户（面具） ----------------

    fun upsertUser(user: UserProfile) {
        synchronized(lock) {
            val list = _users.value.toMutableList()
            val idx = list.indexOfFirst { it.id == user.id }
            if (idx >= 0) list[idx] = user else list.add(user)
            // 单选：当前生效的用户唯一
            val normalized = if (user.isActive) {
                list.map { if (it.id == user.id) it else it.copy(isActive = false) }
            } else list
            _users.value = normalized
            write(KEY_USERS, normalized)
        }
    }

    fun deleteUser(id: String) {
        synchronized(lock) {
            _users.value = _users.value.filterNot { it.id == id }
            write(KEY_USERS, _users.value)
        }
    }

    /** 设为当前生效用户（单选）。 */
    fun activateUser(id: String) {
        synchronized(lock) {
            val list = _users.value.map { it.copy(isActive = it.id == id) }
            _users.value = list
            write(KEY_USERS, list)
        }
    }

    val activeUser: UserProfile?
        get() = _users.value.firstOrNull { it.isActive } ?: _users.value.firstOrNull()

    fun userById(id: String?): UserProfile? = id?.let { uid -> _users.value.firstOrNull { it.id == uid } }

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
            // 会话里也移除该角色；会话空掉则删除会话
            val convs = _conversations.value.mapNotNull { c ->
                val rest = c.characterIds.filterNot { it == id }
                when {
                    rest.isEmpty() -> null
                    else -> c.copy(characterIds = rest, isGroup = rest.size > 1)
                }
            }
            _conversations.value = convs
            write(KEY_CONVERSATIONS, convs)
        }
    }

    fun characterById(id: String?): Character? = id?.let { cid -> _characters.value.firstOrNull { it.id == cid } }

    // ---------------- 会话与消息 ----------------

    fun createConversation(userId: String, characterIds: List<String>): Conversation? {
        if (userId.isBlank() || characterIds.isEmpty()) return null
        val title = if (characterIds.size == 1) {
            characterById(characterIds.first())?.name.orEmpty()
        } else {
            characterIds.mapNotNull { characterById(it)?.name }.joinToString("、").take(20)
        }
        val conv = Conversation(
            id = UUID.randomUUID().toString(),
            userId = userId,
            characterIds = characterIds,
            isGroup = characterIds.size > 1,
            title = title.ifBlank { "新会话" },
            lastMessageAt = System.currentTimeMillis(),
        )
        synchronized(lock) {
            _conversations.value = _conversations.value + conv
            write(KEY_CONVERSATIONS, _conversations.value)
        }
        return conv
    }

    fun deleteConversation(id: String) {
        synchronized(lock) {
            _conversations.value = _conversations.value.filterNot { it.id == id }
            write(KEY_CONVERSATIONS, _conversations.value)
            prefs.edit().remove(KEY_MESSAGES_PREFIX + id).apply()
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

    fun messages(conversationId: String): List<Message> = load(KEY_MESSAGES_PREFIX + conversationId)

    fun appendMessage(message: Message) {
        synchronized(lock) {
            val list = load<Message>(KEY_MESSAGES_PREFIX + message.conversationId).toMutableList()
            list.add(message)
            write(KEY_MESSAGES_PREFIX + message.conversationId, list)

            val convList = _conversations.value.toMutableList()
            val idx = convList.indexOfFirst { it.id == message.conversationId }
            if (idx >= 0) {
                convList[idx] = convList[idx].copy(
                    lastMessageAt = message.timestamp,
                    lastMessagePreview = message.text.replace('\n', ' ').take(40),
                )
                _conversations.value = convList
                write(KEY_CONVERSATIONS, convList)
            }
        }
    }

    fun dropLastCharMessage(conversationId: String) {
        synchronized(lock) {
            val list = load<Message>(KEY_MESSAGES_PREFIX + conversationId).toMutableList()
            val idx = list.indexOfLast { it.sender == Sender.CHAR }
            if (idx >= 0) {
                list.removeAt(idx)
                write(KEY_MESSAGES_PREFIX + conversationId, list)
            }
        }
    }

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

    private inline fun <reified T> load(key: String): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<T>>(raw) }.getOrElse { emptyList() }
    }

    private inline fun <reified T> write(key: String, value: List<T>) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    private companion object {
        const val KEY_PRESETS = "api_presets_v2"
        const val KEY_USERS = "users_v1"
        const val KEY_CHARACTERS = "characters_v2"
        const val KEY_CONVERSATIONS = "conversations_v2"
        const val KEY_MESSAGES_PREFIX = "messages_v2_"
    }
}
