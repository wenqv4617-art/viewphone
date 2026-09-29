package com.viewphone.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 私有目录文件存储。
 *
 * 项目第一目标（宪法 §一.1）：**图片/语音/书籍一律落文件，数据库只存路径**。
 * 头像、壁纸、聊天图片都必须走这里；任何地方都不允许把图片转成 base64 存库。
 */
class FileStore(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    /** 把系统相册选到的图片复制进私有目录，返回绝对路径。 */
    fun importImage(uri: Uri, kind: String): String {
        val dir = File(root, kind).apply { if (!exists()) mkdirs() }
        val ext = guessExt(uri)
        val dst = File(dir, UUID.randomUUID().toString() + ext)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选图片" }
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        return dst.absolutePath
    }

    fun resolve(relativePath: String): File? {
        val f = if (relativePath.startsWith("/")) File(relativePath) else File(context.filesDir, relativePath)
        return if (f.exists()) f else null
    }

    /** 存库时用相对 filesDir 的路径，避免换机后绝对路径失效。 */
    fun relativePath(absolutePath: String): String =
        absolutePath.removePrefix(context.filesDir.absolutePath).trimStart('/')

    fun delete(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        runCatching { resolve(relativePath)?.delete() }
    }

    private fun guessExt(uri: Uri): String {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        return when {
            mime.contains("png") -> ".png"
            mime.contains("webp") -> ".webp"
            mime.contains("gif") -> ".gif"
            mime.contains("heic") || mime.contains("heif") -> ".heic"
            else -> ".jpg"
        }
    }
}
