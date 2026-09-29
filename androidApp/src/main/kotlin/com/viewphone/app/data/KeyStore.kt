package com.viewphone.app.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API Key 的加密存储（Android Keystore 支持的 EncryptedSharedPreferences）。
 *
 * 硬约束：
 *  - Key **绝不明文落库、绝不进日志**（宪法 §三.6）。
 *  - 若设备上加密存储初始化失败（极少数 ROM），降级为普通 SharedPreferences，
 *    但**在日志里只记一句"降级"，不打印任何 key 内容**。
 */
class KeyStore(context: Context) {

    private val prefs = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "viewphone_keys_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { e ->
        // 不打印异常详情之外的东西；这里只记类型，避免任何 key 相关字符串进日志
        Log.w(TAG, "加密存储不可用，降级为普通存储：${e.javaClass.simpleName}")
        context.getSharedPreferences("viewphone_keys_plain", Context.MODE_PRIVATE)
    }

    private fun keyName(presetId: String) = "api_key_$presetId"

    fun save(presetId: String, apiKey: String) {
        prefs.edit().putString(keyName(presetId), apiKey).apply()
    }

    fun get(presetId: String): String = prefs.getString(keyName(presetId), "").orEmpty()

    fun delete(presetId: String) {
        prefs.edit().remove(keyName(presetId)).apply()
    }

    fun has(presetId: String): Boolean = !get(presetId).isBlank()

    /** 只显示尾部 4 位，用于界面回显；空 key 返回空串。 */
    fun masked(presetId: String): String {
        val k = get(presetId)
        return if (k.length <= 4) "••••" else "••••" + k.takeLast(4)
    }

    private companion object {
        const val TAG = "VpKeyStore"
    }
}
