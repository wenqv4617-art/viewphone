// ============================================================
// 微光机 ViewPhone · Compose 主题
// 色值一律来自 ui/theme/VpColors.kt（由 design/tokens.json 生成）。
// 浅色为主 + 单一强调色；微光 = 高亮度小面积。
// ============================================================
package com.viewphone.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val VpColorScheme = lightColorScheme(
    primary = VpColors.Accent,
    onPrimary = VpColors.OnAccent,
    primaryContainer = VpColors.AccentSoft,
    onPrimaryContainer = VpColors.AccentPressed,
    background = VpColors.BgBase,
    onBackground = VpColors.TextPrimary,
    surface = VpColors.BgSurface,
    onSurface = VpColors.TextPrimary,
    surfaceVariant = VpColors.BgSurface2,
    onSurfaceVariant = VpColors.TextSecondary,
    outline = VpColors.Outline,
    outlineVariant = VpColors.OutlineStrong,
    error = VpColors.Error,
)

/** 字阶（取自 tokens.typography.scale）。 */
val VpTypography = Typography(
    displayLarge = TextStyle(fontSize = 68.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 68.sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 15.sp),
)

/**
 * 应用主题。
 *
 * 注意：本项目**只做浅色**（用户明确否掉暗色）。因此这里忽略系统暗色偏好，
 * 始终使用上面这套浅色方案，避免"系统切暗色后界面崩坏"。
 */
@Composable
fun VpTheme(content: @Composable () -> Unit) {
    // 保留参数以获得重组的正确性，但**不使用**它（浅色为唯一方案）
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()

    MaterialTheme(
        colorScheme = VpColorScheme,
        typography = VpTypography,
        content = content,
    )
}
