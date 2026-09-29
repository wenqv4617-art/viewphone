package com.viewphone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.viewphone.app.ui.AppShell
import com.viewphone.app.ui.theme.VpTheme

/**
 * 应用唯一 Activity。
 *
 * 职责：承载 Compose 树；所有界面都在 [AppShell] 里，Activity 本身不含业务逻辑。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 内容延伸到状态栏/导航栏，由各屏自己处理安全区
        setContent {
            VpTheme {
                AppShell()
            }
        }
    }
}
