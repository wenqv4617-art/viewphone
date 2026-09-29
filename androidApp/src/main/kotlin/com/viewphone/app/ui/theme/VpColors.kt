// ============================================================
// 由 design/tokens.json 生成 Android 资源与 Compose 主题色，勿手改。
// 生成器：tools/design/gen-theme.mjs ｜ 单一事实源：design/tokens.json
// 规则：源码里禁止裸色值；改色先改 tokens，再重新生成。
// ============================================================
package com.viewphone.app.ui.theme

import androidx.compose.ui.graphics.Color

object VpColors {
    val BgBase = Color(0xFFF5F6F8)
    val BgSurface = Color(0xFFFFFFFF)
    val BgSurface2 = Color(0xFFEEF1F6)
    val BgElevated = Color(0xFFFFFFFF)

    val TextPrimary = Color(0xFF1B1F27)
    val TextSecondary = Color(0xFF6B7280)
    /** 装饰性：禁止用于文字（对比度 2.57:1）。仅限分隔线/占位图形。 */
    val TextTertiaryDecorative = Color(0xFF9AA2AF)

    val Outline = Color(0xFFE6E9EF)
    val OutlineStrong = Color(0xFFD3D9E2)

    val Accent = Color(0xFF3B6FE0)
    val AccentPressed = Color(0xFF2F5AC4)
    val AccentSoft = Color(0xFFEAF0FF)
    val OnAccent = Color(0xFFFFFFFF)
    val Glow = Color(0xFF3B6FE0)
    val UnreadDot = Color(0xFFE5484D)

    val Success = Color(0xFF3DD68C)
    val Warning = Color(0xFFF5A524)
    val Error = Color(0xFFE5484D)

    // 聊天气泡
    val BubbleCharBg = Color(0xFFFFFFFF)
    val BubbleCharOutline = Color(0xFFE6E9EF)
    val BubbleCharText = Color(0xFF1B1F27)
    val BubbleUserBg = Color(0xFF22262B)
    val BubbleUserText = Color(0xFFFFFFFF)

    // 8 套应用入口色块（以 hex 为准；OKLCH 仅参考）
    val TileChatBg = Color(0xFFE3EBFA)
    val TileChatInk = Color(0xFF2B4C8C)
    val TileCharacterBg = Color(0xFFD7EFDD)
    val TileCharacterInk = Color(0xFF2F6B4A)
    val TileWorldBg = Color(0xFFEAE5F4)
    val TileWorldInk = Color(0xFF5A3E96)
    val TileMemoryBg = Color(0xFFE6F1F3)
    val TileMemoryInk = Color(0xFF26606B)
    val TileMusicBg = Color(0xFFF9E8F0)
    val TileMusicInk = Color(0xFF8C2F63)
    val TileReaderBg = Color(0xFFFBEDE6)
    val TileReaderInk = Color(0xFF8C4A2B)
    val TileGalleryBg = Color(0xFFF1E7CE)
    val TileGalleryInk = Color(0xFF7A6023)
    val TileSettingsBg = Color(0xFFEDEFF3)
    val TileSettingsInk = Color(0xFF4A5261)
    val TileDisabledBg = Color(0xFFF0F1F3)
    val TileDisabledInk = Color(0xFFB9BEC7)

    // 启动图标底色（沿用用户提供的图标语言：深冷蓝 + 暖金）
    val LauncherDeepBlue = Color(0xFF12243D)
    val LauncherWarmGold = Color(0xFFE8C179)
    val LauncherCoreGlow = Color(0xFFFFF3D6)
}
