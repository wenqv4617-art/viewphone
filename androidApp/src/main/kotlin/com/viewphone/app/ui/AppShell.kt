package com.viewphone.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.viewphone.app.data.AppContainer
import com.viewphone.app.data.AppViewModel
import com.viewphone.app.ui.chat.ChatScreen
import com.viewphone.app.ui.chat.ConversationListScreen
import com.viewphone.app.ui.character.CharacterListScreen
import com.viewphone.app.ui.desktop.DesktopScreen
import com.viewphone.app.ui.settings.ApiConfigScreen
import com.viewphone.app.ui.settings.SettingsScreen
import androidx.compose.ui.platform.LocalContext

/**
 * 应用外壳与路由。
 *
 * 设计取舍：**仍不引入 navigation-compose**——目的地增至 8 个但都是"一层栈"结构，
 * 用一个 sealed interface + 一个状态变量足够清晰；等出现深层嵌套或深链接需求再换。
 * 返回键：每个页面自己处理 onBack（统一回到它的上一层）。
 */
sealed interface Destination {
    data object Desktop : Destination
    data object Settings : Destination
    data object ApiConfig : Destination
    data object Characters : Destination
    data object Conversations : Destination
    data class Chat(val characterId: String) : Destination
    data class NotImplemented(val label: String) : Destination
}

@Composable
fun AppShell() {
    val context = LocalContext.current
    val container = remember { AppContainer(context.applicationContext) }
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))

    var destination: Destination by remember { mutableStateOf(Destination.Desktop) }
    // 记住"从哪来"，让聊天页返回时回到正确的上一层
    var chatReturnTo: Destination by remember { mutableStateOf(Destination.Conversations) }

    when (val d = destination) {
        Destination.Desktop -> DesktopScreen(
            onOpenSettings = { destination = Destination.Settings },
            onOpenApp = { app ->
                destination = when {
                    !app.implemented -> Destination.NotImplemented(app.label)
                    else -> when (app.id) {
                        "settings" -> Destination.Settings
                        "chat" -> Destination.Conversations
                        "character" -> Destination.Characters
                        else -> Destination.NotImplemented(app.label)
                    }
                }
            },
        )

        Destination.Settings -> SettingsScreen(
            onBack = { destination = Destination.Desktop },
            onOpenApiConfig = { destination = Destination.ApiConfig },
            onOpenCharacters = { destination = Destination.Characters },
            onOpenChats = { destination = Destination.Conversations },
        )

        Destination.ApiConfig -> ApiConfigScreen(
            vm = vm,
            onBack = { destination = Destination.Settings },
        )

        Destination.Characters -> CharacterListScreen(
            vm = vm,
            onBack = { destination = Destination.Desktop },
            onOpenChat = { characterId ->
                chatReturnTo = Destination.Characters
                destination = Destination.Chat(characterId)
            },
        )

        Destination.Conversations -> ConversationListScreen(
            vm = vm,
            onBack = { destination = Destination.Desktop },
            onOpenCharacter = { characterId ->
                chatReturnTo = Destination.Conversations
                destination = Destination.Chat(characterId)
            },
        )

        is Destination.Chat -> ChatScreen(
            vm = vm,
            characterId = d.characterId,
            onBack = { destination = chatReturnTo },
        )

        is Destination.NotImplemented -> NotImplementedScreen(
            label = d.label,
            onBack = { destination = Destination.Desktop },
        )
    }
}
