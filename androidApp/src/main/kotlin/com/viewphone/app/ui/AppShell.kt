package com.viewphone.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.viewphone.app.ui.desktop.DesktopScreen
import com.viewphone.app.ui.settings.SettingsScreen

/**
 * 应用外壳与路由。
 *
 * 设计取舍（第 1 轮）：
 *  - **不引入 navigation-compose**：当前只有 2 个目的地，用一个状态变量即可，
 *    避免过早引入导航库（"用到哪个建哪个"）。等目的地超过 4~5 个再换。
 *  - 返回键处理放在 [DesktopScreen] 之外，由这里统一决定。
 */
sealed interface Destination {
    data object Desktop : Destination
    data object Settings : Destination
    /** 尚未实现的应用（灰显入口）点击后的提示，不进入真正页面。 */
    data class NotImplemented(val label: String) : Destination
}

@Composable
fun AppShell() {
    var destination: Destination by remember { mutableStateOf(Destination.Desktop) }

    when (val d = destination) {
        Destination.Desktop -> DesktopScreen(
            onOpenSettings = { destination = Destination.Settings },
            onOpenApp = { app ->
                if (app.implemented) {
                    // 第 2 轮接聊天；本轮只有设置已实现
                    when (app.id) {
                        "settings" -> destination = Destination.Settings
                        else -> destination = Destination.NotImplemented(app.label)
                    }
                } else {
                    destination = Destination.NotImplemented(app.label)
                }
            },
        )

        Destination.Settings -> SettingsScreen(
            onBack = { destination = Destination.Desktop },
        )

        is Destination.NotImplemented -> NotImplementedScreen(
            label = d.label,
            onBack = { destination = Destination.Desktop },
        )
    }
}
