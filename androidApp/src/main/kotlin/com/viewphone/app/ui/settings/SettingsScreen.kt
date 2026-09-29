package com.viewphone.app.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewphone.app.ui.theme.VpColors

/**
 * 设置页（分组列表）。
 *
 * 本轮已接：API 配置（可用的预设 CRUD + 测试连接）、角色库、聊天入口。
 * 未接的项保持占位，点击暂无动作（不做假跳转）。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenApiConfig: () -> Unit = {},
    onOpenRoles: () -> Unit = {},
    onOpenChats: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 导航条（高 52，取自 tokens.space.semantic.navBarHeight）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                fontSize = 26.sp,
                color = VpColors.Accent,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Spacer(modifier = Modifier.padding(start = 8.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                color = VpColors.TextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        GroupTitle("模型与接口")
        GroupCard {
            SettingRow(
                title = "API 配置",
                subtitle = "多套预设 · 按角色绑定 · 测试连接",
                onClick = onOpenApiConfig,
            )
            Divider()
            SettingRow(
                title = "角色库",
                subtitle = "用户 / 角色，可分组",
                onClick = onOpenRoles,
            )
            Divider()
            SettingRow(
                title = "聊天",
                subtitle = "对话 / 联系人 / 发现 / 主页",
                onClick = onOpenChats,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        GroupTitle("数据")
        GroupCard {
            SettingRow(title = "容量与存储", subtitle = "统计与清理", onClick = {})
            Divider()
            SettingRow(title = "导出 / 导入", subtitle = "备份为 ZIP（含校验和）", onClick = {})
            Divider()
            SettingRow(title = "清空数据", subtitle = "危险操作，需二次确认", onClick = {})
        }

        Spacer(modifier = Modifier.height(24.dp))

        GroupTitle("调试")
        GroupCard {
            SettingRow(title = "提示词调试面板", subtitle = "查看本次发给模型的完整提示词", onClick = {})
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = VpColors.TextSecondary,
        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VpColors.BgSurface),
    ) {
        content()
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = VpColors.TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = VpColors.TextSecondary,
            )
        }
        Text(text = "›", fontSize = 20.sp, color = VpColors.TextTertiaryDecorative)
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(VpColors.Outline),
    )
}

/** 空态占位（本轮未接数据时使用）。 */
@Composable
fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = VpColors.TextSecondary)
        }
    }
}
