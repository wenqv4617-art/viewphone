package com.viewphone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.viewphone.app.ui.theme.VpColors

/**
 * 「开发中」占位屏。
 *
 * 诚实边界原则：未实现的应用点击后**明确告知**，不做假界面、不假装能用。
 */
@Composable
fun NotImplementedScreen(label: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "$label · 开发中",
                color = VpColors.TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "这个应用还没做。第 1 轮只交付：桌面外壳 · 设置(API 配置) · 角色库。",
                color = VpColors.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            TextButton(onClick = onBack) {
                Text("返回桌面", color = VpColors.Accent)
            }
        }
    }
}
