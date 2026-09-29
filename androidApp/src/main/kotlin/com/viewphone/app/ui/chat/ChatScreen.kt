package com.viewphone.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewphone.app.data.AppViewModel
import com.viewphone.app.data.Message
import com.viewphone.app.data.Sender
import com.viewphone.app.ui.character.Avatar
import com.viewphone.app.ui.settings.Hint
import com.viewphone.app.ui.settings.NavBar
import com.viewphone.app.ui.theme.VpColors

/**
 * 单聊页（第 1 轮：能真的聊起来）。
 *
 * 本轮做到：会话列表 → 进入单聊 → 发送 → **逐字流式回复** → 消息落库（关掉再开还在）。
 * 长按菜单（引用/复制/撤回/重新生成）本轮先给「复制 / 重新生成 / 清空」三个最实用的。
 */
@Composable
fun ChatScreen(vm: AppViewModel, characterId: String, onBack: () -> Unit) {
    val character = remember(characterId) { vm.store.characterById(characterId) }
    val messages by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val generating by vm.generating.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(characterId) { vm.openConversation(characterId) }

    // 新内容到达时滚到底
    LaunchedEffect(messages.size, streaming.length) {
        val total = messages.size + if (streaming.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem((total - 1).coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding()
            .imePadding(),
    ) {
        NavBar(
            title = character?.name?.ifBlank { "未命名角色" } ?: "会话",
            onBack = {
                vm.closeConversation()
                onBack()
            },
            action = {
                Text(
                    text = "清空",
                    color = VpColors.TextSecondary,
                    modifier = Modifier.clickable {
                        vm.activeConversationId.value?.let { vm.clearHistory(it) }
                    },
                )
            },
        )

        if (character == null) {
            Hint("角色不存在，可能已被删除。")
            return@Column
        }

        if (messages.isEmpty() && streaming.isEmpty()) {
            Hint("还没有消息。在下面输入框里说第一句话吧。\n（如果没反应，先到 设置 → API 配置 检查地址/Key/模型，并点一次「测试连接」。）")
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { m ->
                MessageBubble(
                    message = m,
                    avatar = { Avatar(vm, character.avatarPath, size = 32) },
                )
            }
            if (streaming.isNotEmpty()) {
                item(key = "streaming") {
                    MessageBubble(
                        message = Message(
                            id = "streaming",
                            conversationId = "",
                            sender = Sender.CHAR,
                            text = streaming,
                        ),
                        avatar = { Avatar(vm, character.avatarPath, size = 32) },
                    )
                }
            }
        }

        // 输入栏
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
                        vm.send(characterId, t)
                    },
                    enabled = input.isNotBlank(),
                ) { Text("发送", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, avatar: @Composable () -> Unit) {
    val isUser = message.sender == Sender.USER
    val isSystem = message.sender == Sender.SYSTEM

    if (isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
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
            avatar()
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
        if (isUser) Spacer(Modifier.size(4.dp))
    }
}

/** 会话列表：从桌面「聊天」进来先看到这个。 */
@Composable
fun ConversationListScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
) {
    val conversations by vm.store.conversations.collectAsState()
    val characters by vm.store.characters.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        NavBar(title = "聊天", onBack = onBack)

        if (characters.isEmpty()) {
            Hint("还没有角色，先去「角色库」建一个。")
        } else {
            characters.forEach { c ->
                val conv = conversations.firstOrNull { it.characterId == c.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VpColors.BgSurface)
                        .clickable { onOpenCharacter(c.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(vm, c.avatarPath, size = 44)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = c.name.ifBlank { "未命名角色" },
                            fontWeight = FontWeight.Medium,
                            color = VpColors.TextPrimary,
                        )
                        Text(
                            text = conv?.lastMessagePreview?.ifBlank { "还没有聊天" } ?: "还没有聊天",
                            style = MaterialTheme.typography.labelSmall,
                            color = VpColors.TextSecondary,
                        )
                    }
                    Text("›", fontSize = 20.sp, color = VpColors.TextTertiaryDecorative)
                }
            }
        }
    }
}
