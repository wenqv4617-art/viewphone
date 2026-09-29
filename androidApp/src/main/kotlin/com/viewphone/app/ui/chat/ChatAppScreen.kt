package com.viewphone.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewphone.app.data.AppViewModel
import com.viewphone.app.data.Character
import com.viewphone.app.data.Conversation
import com.viewphone.app.data.Message
import com.viewphone.app.data.Sender
import com.viewphone.app.ui.role.AvatarBox
import com.viewphone.app.ui.settings.Hint
import com.viewphone.app.ui.settings.NavBar
import com.viewphone.app.ui.theme.VpColors

/**
 * 聊天应用：底部四页签（对话 / 联系人 / 发现 / 主页）。
 *
 * 用户要求的流程：
 *   1. 在聊天·主页选中「用户」（你扮演的身份）
 *   2. 在「联系人」里添加/导入角色
 *   3. 在「对话」右上角新建会话 → 选单聊或群聊 → 选角色
 */
@Composable
fun ChatAppScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var showCreator by remember { mutableStateOf(false) }
    val users by vm.store.users.collectAsState()
    val characters by vm.store.characters.collectAsState()
    val conversations by vm.store.conversations.collectAsState()
    val activeUser = users.firstOrNull { it.isActive } ?: users.firstOrNull()

    if (showCreator) {
        ConversationCreator(
            vm = vm,
            onClose = { showCreator = false },
            onCreated = { convId ->
                showCreator = false
                onOpenConversation(convId)
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        when (tab) {
            0 -> NavBar(
                title = "对话",
                onBack = onBack,
                action = {
                    Text(
                        "＋新建",
                        color = VpColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { showCreator = true },
                    )
                },
            )
            1 -> NavBar(title = "联系人", onBack = onBack)
            2 -> NavBar(title = "发现", onBack = onBack)
            else -> NavBar(title = "主页", onBack = onBack)
        }

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> ConversationsTab(vm, conversations, onOpenConversation, activeUser?.name)
                1 -> ContactsTab(vm, characters, onOpenConversation)
                2 -> DiscoverTab()
                else -> HomeTab(vm, users)
            }
        }

        // 底部四页签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(VpColors.BgSurface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("对话", "联系人", "发现", "主页").forEachIndexed { i, label ->
                val selected = tab == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { tab = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) VpColors.Accent else VpColors.TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ---------------- 对话页签 ----------------

@Composable
private fun ConversationsTab(
    vm: AppViewModel,
    conversations: List<Conversation>,
    onOpen: (String) -> Unit,
    activeUserName: String?,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (conversations.isEmpty()) {
            Hint(
                "还没有会话。\n" +
                    "流程：主页选好「用户」→ 联系人里确认有「角色」→ 右上角「＋新建」选单聊或群聊。"
            )
        }
        conversations.sortedByDescending { it.lastMessageAt }.forEach { conv ->
            val names = conv.characterIds.mapNotNull { vm.store.characterById(it)?.name }
                .joinToString("、").ifBlank { "（角色已删除）" }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VpColors.BgSurface)
                    .clickable { onOpen(conv.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBox(vm, vm.store.characterById(conv.characterIds.firstOrNull())?.avatarPath, 44, names)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = (if (conv.isGroup) "[群] " else "") + conv.title.ifBlank { names },
                        fontWeight = FontWeight.Medium,
                        color = VpColors.TextPrimary,
                    )
                    Text(
                        conv.lastMessagePreview.ifBlank { "还没有聊天" },
                        style = MaterialTheme.typography.labelSmall,
                        color = VpColors.TextSecondary,
                    )
                }
                Text(activeUserName?.let { "·" } ?: "", color = VpColors.TextTertiaryDecorative)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------- 联系人页签 ----------------

@Composable
private fun ContactsTab(
    vm: AppViewModel,
    characters: List<Character>,
    onOpenConversation: (String) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (characters.isEmpty()) {
            Hint("联系人里还没有角色。去「角色库」新建角色，或在下面的按钮里快速添加。")
        }
        characters.groupBy { it.group.ifBlank { "未分组" } }.forEach { (group, list) ->
            Text(
                text = group,
                style = MaterialTheme.typography.labelMedium,
                color = VpColors.TextSecondary,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
            )
            list.forEach { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VpColors.BgSurface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarBox(vm, c.avatarPath, 40, c.name)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        c.name.ifBlank { "未命名角色" },
                        color = VpColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "发消息",
                        color = VpColors.Accent,
                        modifier = Modifier.clickable {
                            val userId = vm.store.activeUser?.id
                            if (userId != null) {
                                vm.createConversation(userId, listOf(c.id))?.let(onOpenConversation)
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Hint("「导入角色」是接外部角色卡的能力，涉及格式解析，放在后续版本（本项目不做酒馆格式兼容）。")
    }
}

// ---------------- 发现 / 主页 ----------------

@Composable
private fun DiscoverTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("发现", color = VpColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "世界书 / 记忆 / 广场等扩展内容后续版本接入。",
            style = MaterialTheme.typography.labelSmall,
            color = VpColors.TextSecondary,
        )
    }
}

@Composable
private fun HomeTab(vm: AppViewModel, users: List<com.viewphone.app.data.UserProfile>) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Hint("选择你这次要扮演的「用户」。选好后，所有会话都会以这个人设进行。")
        if (users.isEmpty()) {
            Hint("还没有用户。请到「角色库 → 用户」新建一个。")
        }
        users.forEach { u ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (u.isActive) VpColors.AccentSoft else VpColors.BgSurface)
                    .clickable { vm.activateUser(u.id) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBox(vm, u.avatarPath, 48, u.name)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        u.name.ifBlank { "未命名用户" },
                        fontWeight = FontWeight.Medium,
                        color = if (u.isActive) VpColors.Accent else VpColors.TextPrimary,
                    )
                    Text(
                        if (u.isActive) "当前使用中" else "点击切换为当前用户",
                        style = MaterialTheme.typography.labelSmall,
                        color = VpColors.TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------- 建会话 ----------------

@Composable
private fun ConversationCreator(
    vm: AppViewModel,
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val users by vm.store.users.collectAsState()
    val characters by vm.store.characters.collectAsState()
    var isGroup by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf(vm.store.activeUser?.id) }
    var picked by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        NavBar(
            title = "新建会话",
            onBack = onClose,
            action = {
                Text(
                    "创建",
                    color = if (userId != null && picked.isNotEmpty()) VpColors.Accent else VpColors.TextTertiaryDecorative,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        val uid = userId
                        if (uid != null && picked.isNotEmpty()) {
                            vm.createConversation(uid, picked.toList())?.let(onCreated)
                        }
                    },
                )
            },
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("类型", style = MaterialTheme.typography.labelMedium, color = VpColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "单聊", true to "群聊").forEach { (g, label) ->
                    val selected = isGroup == g
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) VpColors.Accent else VpColors.BgSurface)
                            .clickable {
                                isGroup = g
                                if (!g && picked.size > 1) picked = picked.take(1).toSet()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (selected) VpColors.OnAccent else VpColors.TextSecondary)
                    }
                }
            }

            Text("以哪个用户身份", style = MaterialTheme.typography.labelMedium, color = VpColors.TextSecondary)
            if (users.isEmpty()) {
                Hint("还没有用户，先去「角色库 → 用户」建一个。")
            }
            users.forEach { u ->
                val selected = userId == u.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) VpColors.AccentSoft else VpColors.BgSurface)
                        .clickable { userId = u.id }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarBox(vm, u.avatarPath, 36, u.name)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        u.name.ifBlank { "未命名用户" },
                        color = if (selected) VpColors.Accent else VpColors.TextPrimary,
                    )
                }
            }

            Text(
                if (isGroup) "选择角色（可多选）" else "选择角色（单选）",
                style = MaterialTheme.typography.labelMedium,
                color = VpColors.TextSecondary,
            )
            if (characters.isEmpty()) {
                Hint("还没有角色，先去「角色库 → 角色」建一个。")
            }
            characters.forEach { c ->
                val selected = c.id in picked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) VpColors.AccentSoft else VpColors.BgSurface)
                        .clickable {
                            picked = when {
                                !selected && !isGroup -> setOf(c.id)
                                selected -> picked - c.id
                                else -> picked + c.id
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarBox(vm, c.avatarPath, 36, c.name)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        c.name.ifBlank { "未命名角色" },
                        color = if (selected) VpColors.Accent else VpColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Text("✓", color = VpColors.Accent)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ---------------- 单聊对话页 ----------------

@Composable
fun ConversationScreen(vm: AppViewModel, conversationId: String, onBack: () -> Unit) {
    val conversations by vm.store.conversations.collectAsState()
    val conv = conversations.firstOrNull { it.id == conversationId }
    val messages by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val generating by vm.generating.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(conversationId) { vm.openConversation(conversationId) }
    LaunchedEffect(messages.size, streaming.length) {
        val total = messages.size + if (streaming.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem((total - 1).coerceAtLeast(0))
    }

    val title = remember(conv, conversations) {
        conv?.let { c ->
            (if (c.isGroup) "[群] " else "") +
                c.title.ifBlank {
                    c.characterIds.mapNotNull { vm.store.characterById(it)?.name }.joinToString("、")
                }
        } ?: "会话"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding()
            .imePadding(),
    ) {
        NavBar(
            title = title,
            onBack = {
                vm.closeConversation()
                onBack()
            },
            action = {
                Text(
                    "清空",
                    color = VpColors.TextSecondary,
                    modifier = Modifier.clickable { vm.clearHistory(conversationId) },
                )
            },
        )

        if (messages.isEmpty() && streaming.isEmpty()) {
            Hint(
                "还没有消息。\n" +
                    "如果发出去没反应：到 设置 → API 配置 → 文本 检查地址 / Key / 模型，并点一次「测试连接」。"
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { m ->
                val speaker = vm.store.characterById(m.senderCharacterId)
                    ?: vm.store.characterById(conv?.characterIds?.firstOrNull())
                Bubble(m, vm.imageFile(speaker?.avatarPath), speaker?.name)
            }
            if (streaming.isNotEmpty()) {
                // 注意：LazyListScope 里必须用 item { } 包住，否则 @Composable 不能在非组合作用域调用
                item(key = "streaming") {
                    val speaker = vm.store.characterById(conv?.characterIds?.firstOrNull())
                    Bubble(
                        Message(
                            id = "streaming",
                            conversationId = conversationId,
                            sender = Sender.CHAR,
                            text = streaming,
                        ),
                        vm.imageFile(speaker?.avatarPath),
                        speaker?.name,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("说点什么…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.size(8.dp))
            if (generating) {
                TextButton(onClick = { vm.stopGenerating() }) { Text("停止") }
            } else {
                TextButton(
                    onClick = {
                        val t = input
                        input = ""
                        vm.send(conversationId, t)
                    },
                    enabled = input.isNotBlank(),
                ) { Text("发送", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Bubble(message: Message, avatarFile: java.io.File?, speakerName: String?) {
    val isUser = message.sender == Sender.USER
    if (message.sender == Sender.SYSTEM) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.labelSmall,
                color = if (message.isError) VpColors.Error else VpColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(VpColors.BgSurface2)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AvatarImage(avatarFile, speakerName)
            Spacer(Modifier.size(8.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 18.dp,
                    )
                )
                .background(if (isUser) VpColors.BubbleUserBg else VpColors.BubbleCharBg)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = message.text,
                fontSize = 15.sp,
                color = if (isUser) VpColors.BubbleUserText else VpColors.BubbleCharText,
            )
        }
    }
}

@Composable
private fun AvatarImage(file: java.io.File?, name: String?) {
    val bitmap = remember(file?.absolutePath) {
        file?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VpColors.BgSurface2),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!name.isNullOrBlank()) {
            Text(name.trim().take(1), color = VpColors.TextSecondary, fontSize = 13.sp)
        }
    }
}
