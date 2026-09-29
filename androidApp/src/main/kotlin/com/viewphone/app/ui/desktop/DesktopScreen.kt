package com.viewphone.app.ui.desktop

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewphone.app.ui.theme.VpColors

/**
 * 桌面（外壳与入口）。
 *
 * 第 1 轮范围：状态栏 + 大时钟/日期/天气 + 图标网格 + Dock + 未读红点 + 未实现应用灰显。
 * **本轮不做**：编辑视图、拖动排序、大文件夹、壁纸更换、加组件、自定义 CSS（已定稿后移）。
 *
 * 布局基准（取自 design/tokens.json）：屏边 20 · 图标单元 82 · 图标 66 · 行距 118 · Dock 高 86 圆角 30。
 */
@Composable
fun DesktopScreen(
    onOpenSettings: () -> Unit,
    onOpenApp: (DesktopApp) -> Unit,
) {
    // 「开发中」提示：点击灰显应用后短暂显示，不用 Toast（避免平台原生弹窗）
    var devHint by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            StatusBar()

            // 大时钟 + 日期 + 天气
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "9:41",
                        fontSize = 68.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = VpColors.TextPrimary,
                    )
                    Text(
                        text = "9月29日 星期一",
                        style = MaterialTheme.typography.labelMedium,
                        color = VpColors.TextSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "21°",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        color = VpColors.TextPrimary,
                    )
                    Text(
                        text = "多云",
                        style = MaterialTheme.typography.labelMedium,
                        color = VpColors.TextSecondary,
                    )
                }
            }

            // 微光：状态点（全屏第 1 处，也是唯一一处）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 24.dp, top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(VpColors.Accent),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            IconGrid(
                onOpenApp = { app ->
                    if (app.implemented) onOpenApp(app) else devHint = app.label
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            devHint?.let { label ->
                Text(
                    text = "$label · 开发中",
                    style = MaterialTheme.typography.labelMedium,
                    color = VpColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            Dock(onOpenApp = onOpenApp)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "9:41",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = VpColors.TextPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        // 矢量小图标：Wi-Fi 圆点 + 电量文字（本轮不画满三件套，避免噪点）
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(VpColors.TextSecondary),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "100%",
            style = MaterialTheme.typography.labelSmall,
            color = VpColors.TextSecondary,
        )
    }
}

/** 4 列图标网格。未实现的应用灰显（tokens: TileDisabled*）。 */
@Composable
private fun IconGrid(onOpenApp: (DesktopApp) -> Unit) {
    val apps = DesktopApp.entries.toList()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        apps.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { app ->
                    AppEntry(
                        app = app,
                        unread = if (app == DesktopApp.CHAT) 3 else 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenApp(app) },
                    )
                }
                // 补齐空位，保持 4 列对齐
                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** 单个应用入口：图标块 + 名称；未读红点贴右上角外侧（槽位 72 vs 图标 66）。 */
@Composable
private fun AppEntry(
    app: DesktopApp,
    unread: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tileBg = if (app.implemented) app.tileBg() else VpColors.TileDisabledBg
    val iconTint = if (app.implemented) null else VpColors.TileDisabledInk

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(app.icon),
                    contentDescription = app.label,
                    modifier = Modifier.size(30.dp),
                    tint = iconTint ?: Color.Unspecified,
                    colorFilter = iconTint?.let { ColorFilter.tint(it) },
                )
            }
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(VpColors.UnreadDot),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = unread.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (app.implemented) VpColors.TextSecondary else VpColors.TileDisabledInk,
        )
    }
}

/** 底部 Dock：4 个入口，不显示文字。 */
@Composable
private fun Dock(onOpenApp: (DesktopApp) -> Unit) {
    val dockApps = listOf(
        DesktopApp.CHAT,
        DesktopApp.SETTINGS,
        DesktopApp.MUSIC,
        DesktopApp.GALLERY,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(VpColors.BgSurface)
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            dockApps.forEach { app ->
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (app.implemented) app.tileBg() else VpColors.TileDisabledBg)
                        .clickable { if (app.implemented) onOpenApp(app) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(app.icon),
                        contentDescription = app.label,
                        modifier = Modifier.size(28.dp),
                        tint = if (app.implemented) Color.Unspecified else VpColors.TileDisabledInk,
                    )
                }
            }
        }
    }
}

/** 8 套应用入口色块（取自 tokens.color.app-tiles，以 hex 为准）。 */
private fun DesktopApp.tileBg(): Color = when (this) {
    DesktopApp.CHAT -> VpColors.TileChatBg
    DesktopApp.CHARACTER -> VpColors.TileCharacterBg
    DesktopApp.WORLD -> VpColors.TileWorldBg
    DesktopApp.MEMORY -> VpColors.TileMemoryBg
    DesktopApp.MUSIC -> VpColors.TileMusicBg
    DesktopApp.READER -> VpColors.TileReaderBg
    DesktopApp.GALLERY -> VpColors.TileGalleryBg
    DesktopApp.SETTINGS -> VpColors.TileSettingsBg
}
