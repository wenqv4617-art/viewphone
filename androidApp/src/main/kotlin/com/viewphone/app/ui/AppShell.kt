package com.viewphone.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.viewphone.app.data.AppContainer
import com.viewphone.app.data.AppViewModel
import com.viewphone.app.ui.chat.ChatAppScreen
import com.viewphone.app.ui.chat.ConversationScreen
import com.viewphone.app.ui.desktop.DesktopScreen
import com.viewphone.app.ui.role.RoleLibraryScreen
import com.viewphone.app.ui.settings.ApiConfigScreen
import com.viewphone.app.ui.settings.SettingsScreen

/**
 * 应用外壳与路由。
 *
 * 结构（用户要求）：
 *   桌面 →「角色库」= 用户 / 角色（均可分组）
 *   桌面 →「聊天」  = 对话 / 联系人 / 发现 / 主页 四页签
 *   流程：角色库里建「用户」→ 聊天·主页选用户 → 联系人里确认角色 →
 *        对话右上角「＋新建」选单聊/群聊 → 进入会话
 *
 * 仍然不引入 navigation-compose：目的地都是一层栈，一个 sealed interface 足够清晰。
 */
sealed interface Destination {
    data object Desktop : Destination
    data object Settings : Destination
    data object ApiConfig : Destination
    data object RoleLibrary : Destination
    data object ChatApp : Destination
    data class Conversation(val conversationId: String) : Destination
    data class NotImplemented(val label: String) : Destination
}

@Composable
fun AppShell() {
    val context = LocalContext.current
    val container = remember { AppContainer(context.applicationContext) }
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))

    var destination: Destination by remember { mutableStateOf(Destination.Desktop) }

    when (val d = destination) {
        Destination.Desktop -> DesktopScreen(
            onOpenSettings = { destination = Destination.Settings },
            onOpenApp = { app ->
                destination = when {
                    !app.implemented -> Destination.NotImplemented(app.label)
                    else -> when (app.id) {
                        "settings" -> Destination.Settings
                        "chat" -> Destination.ChatApp
                        "character" -> Destination.RoleLibrary
                        else -> Destination.NotImplemented(app.label)
                    }
                }
            },
        )

        Destination.Settings -> SettingsScreen(
            onBack = { destination = Destination.Desktop },
            onOpenApiConfig = { destination = Destination.ApiConfig },
            onOpenRoles = { destination = Destination.RoleLibrary },
            onOpenChats = { destination = Destination.ChatApp },
        )

        Destination.ApiConfig -> ApiConfigScreen(
            vm = vm,
            onBack = { destination = Destination.Settings },
        )

        Destination.RoleLibrary -> RoleLibraryScreen(
            vm = vm,
            onBack = { destination = Destination.Desktop },
        )

        Destination.ChatApp -> ChatAppScreen(
            vm = vm,
            onBack = { destination = Destination.Desktop },
            onOpenConversation = { convId -> destination = Destination.Conversation(convId) },
        )

        is Destination.Conversation -> ConversationScreen(
            vm = vm,
            conversationId = d.conversationId,
            onBack = { destination = Destination.ChatApp },
        )

        is Destination.NotImplemented -> NotImplementedScreen(
            label = d.label,
            onBack = { destination = Destination.Desktop },
        )
    }
}
