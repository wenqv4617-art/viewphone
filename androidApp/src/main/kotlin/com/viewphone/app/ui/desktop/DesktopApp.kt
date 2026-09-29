// ============================================================
// 应用入口图标（矢量 VectorDrawable 资源，禁止 emoji、禁止位图）
// 24 栅格 / 线宽 1.75 / round；颜色由 Compose 的 tint 或资源内写死 tokens 值。
// 说明：本轮先用 Android VectorDrawable（原生矢量）；后续如需 Web 端复用，
//      可把同一份几何导出为 SVG（几何定义见 design/tokens.json → icons.appEntrySet）。
// ============================================================
package com.viewphone.app.ui.desktop

import androidx.annotation.DrawableRes
import com.viewphone.app.R

/**
 * 桌面 / Dock 上的应用入口定义。
 *
 * @property id 稳定标识（用于导航与持久化，不用中文名）
 * @property label 显示名
 * @property icon 矢量图标资源
 * @property implemented 是否已实现；false 时灰显并在点击时提示"开发中"
 */
enum class DesktopApp(
    val id: String,
    val label: String,
    @DrawableRes val icon: Int,
    val implemented: Boolean,
) {
    CHAT("chat", "聊天", R.drawable.ic_app_chat, true),
    CHARACTER("character", "角色", R.drawable.ic_app_character, true),
    WORLD("world", "世界", R.drawable.ic_app_world, false),
    MEMORY("memory", "记忆", R.drawable.ic_app_memory, false),
    MUSIC("music", "音乐", R.drawable.ic_app_music, false),
    READER("reader", "书城", R.drawable.ic_app_reader, false),
    GALLERY("gallery", "相册", R.drawable.ic_app_gallery, false),
    SETTINGS("settings", "设置", R.drawable.ic_app_settings, true),
}
