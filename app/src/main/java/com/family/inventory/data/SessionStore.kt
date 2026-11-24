package com.family.inventory.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地配置：服务器地址 + 登录令牌。
 * 与网页端的 localStorage（fi_token / fi_user）等价，但令牌只保存在应用私有目录。
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("family_inventory", Context.MODE_PRIVATE)

    /** 服务器地址，例如 http://192.168.1.10:8080（末尾不带斜杠）。 */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE, normalizeBaseUrl(value)).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    /** 记住上次使用的服务器地址输入框内容，便于修改时回填。 */
    var lastServerInput: String
        get() = prefs.getString(KEY_SERVER_INPUT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER_INPUT, value).apply()

    val hasServer: Boolean get() = baseUrl.isNotBlank()
    val isLoggedIn: Boolean get() = token.isNotBlank()

    fun saveAuth(token: String, username: String) {
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USER, username).apply()
    }

    fun clearAuth() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
    }

    companion object {
        private const val KEY_BASE = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "username"
        private const val KEY_SERVER_INPUT = "server_input"

        /** 补全协议、去掉末尾斜杠，用户只输 192.168.1.10:8080 也能用。 */
        fun normalizeBaseUrl(raw: String): String {
            var v = raw.trim()
            if (v.isEmpty()) return ""
            if (!v.startsWith("http://") && !v.startsWith("https://")) {
                v = "http://$v"
            }
            return v.trimEnd('/')
        }
    }
}
