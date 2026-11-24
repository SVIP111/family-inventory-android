package com.family.inventory.data

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 接口返回非 2xx 时抛出，message 直接来自后端的 {"error": "..."}。 */
class ApiException(message: String, val code: Int = 0) : Exception(message)

/** 401：令牌失效或未登录，需要重新登录。 */
class UnauthorizedException(message: String) : Exception(message)

/**
 * 与 family-inventory Go 后端（main.go 中的 /api 路由）对接的客户端。
 * 所有方法与网页端 app.js 的调用一一对应，请求/响应字段保持兼容。
 */
class ApiClient(private val session: SessionStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    val base: String get() = session.baseUrl

    fun absolute(path: String): String = base.trimEnd('/') + path

    /** 物品二维码内容：与网页端一致，指向服务器网页端的 ?item=<id>。 */
    fun itemQrText(id: String): String = "$base/?item=" + URLEncoder.encode(id, "UTF-8")

    /** 位置二维码内容：指向网页端的 ?loc=<id>。 */
    fun locationQrText(id: String): String = "$base/?loc=" + URLEncoder.encode(id, "UTF-8")

    fun qrImageUrl(text: String): String =
        absolute("/api/qr?text=" + URLEncoder.encode(text, "UTF-8"))

    fun uploadUrl(file: String): String = absolute("/uploads/" + file)

    // ---------- 内部请求封装 ----------

    private fun newRequest(path: String, method: String = "GET", body: okhttp3.RequestBody? = null): Request {
        val builder = Request.Builder().url(absolute(path))
        if (session.token.isNotBlank()) {
            builder.header("Authorization", "Bearer ${session.token}")
        }
        builder.method(method, body)
        return builder.build()
    }

    private suspend fun <T> send(path: String, method: String, body: okhttp3.RequestBody?, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val response = try {
                http.newCall(newRequest(path, method, body)).execute()
            } catch (e: IOException) {
                throw ApiException("无法连接服务器：${e.message ?: "网络异常"}")
            }
            response.use { res ->
                val text = res.body?.string().orEmpty()
                when {
                    res.code == 401 -> throw UnauthorizedException(
                        parseErrorMessage(text) ?: "未登录或登录已过期，请重新登录"
                    )
                    res.code in 200..299 -> parse(text)
                    else -> throw ApiException(
                        parseErrorMessage(text) ?: "请求失败（HTTP ${res.code}）",
                        res.code
                    )
                }
            }
        }

    private fun parseErrorMessage(text: String): String? = runCatching {
        json.decodeFromString(ApiError.serializer(), text).error.ifBlank { null }
    }.getOrNull()

    private inline fun <reified T> parseBody(text: String): T = json.decodeFromString(text)

    // ---------- 鉴权 ----------

    suspend fun authStatus(): AuthStatus =
        send("/api/auth/status", "GET", null) { parseBody(it) }

    suspend fun login(username: String, password: String): AuthResult {
        val payload = buildString {
            append("{\"username\":").append(quote(username))
            append(",\"password\":").append(quote(password)).append('}')
        }
        return send("/api/auth/login", "POST", payload.toRequestBody(jsonMedia)) { parseBody(it) }
    }

    suspend fun register(username: String, password: String, confirm: String): AuthResult {
        val payload = buildString {
            append("{\"username\":").append(quote(username))
            append(",\"password\":").append(quote(password))
            append(",\"confirm\":").append(quote(confirm)).append('}')
        }
        return send("/api/auth/register", "POST", payload.toRequestBody(jsonMedia)) { parseBody(it) }
    }

    suspend fun logout() {
        send("/api/auth/logout", "POST", "".toRequestBody(jsonMedia)) { Unit }
    }

    // ---------- 统计 / 位置 / 标签 ----------

    suspend fun stats(): Stats = send("/api/stats", "GET", null) { parseBody(it) }

    /** GET /api/version（免鉴权）：返回服务端版本号，供登录页/关于页展示。 */
    suspend fun version(): String =
        send("/api/version", "GET", null) { json.decodeFromString(ServerVersion.serializer(), it).version }

    suspend fun locations(): List<Location> = send("/api/locations", "GET", null) { parseBody(it) }

    suspend fun createLocation(name: String, parentId: String = ""): Location {
        val payload = buildString {
            append("{\"name\":").append(quote(name))
            append(",\"parentId\":").append(quote(parentId)).append('}')
        }
        return send("/api/locations", "POST", payload.toRequestBody(jsonMedia)) { parseBody(it) }
    }

    suspend fun deleteLocation(id: String) {
        send("/api/locations/$id", "DELETE", null) { Unit }
    }

    suspend fun uploadLocationImage(id: String, image: ByteArray?, mime: String?, fileName: String?): Location {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        if (image != null) {
            multipart.addFormDataPart("image", fileName ?: "location.jpg", image.toRequestBody(mime?.toMediaType()))
        }
        return send("/api/locations/$id/image", "POST", multipart.build()) { parseBody(it) }
    }

    suspend fun tags(): List<String> = send("/api/tags", "GET", null) { parseBody(it) }

    /** GET /api/summary：仪表盘汇总（位置/标签分布 + 数量 Top10）。 */
    suspend fun summary(): Summary = send("/api/summary", "GET", null) { parseBody(it) }

    /** PUT /api/tags/rename：跨全部物品把 old 改成 new（同名自动合并），返回改名后的标签列表。 */
    suspend fun renameTag(old: String, new: String): List<String> {
        val payload = "{\"old\":" + quote(old) + ",\"new\":" + quote(new) + "}"
        return send("/api/tags/rename", "PUT", payload.toRequestBody(jsonMedia)) { text ->
            json.decodeFromString(TagRenameResult.serializer(), text).tags
        }
    }

    // ---------- 物品 ----------

    suspend fun items(location: String = "", tag: String = "", query: String = ""): List<Item> {
        val params = buildList {
            if (location.isNotBlank()) add("location=" + URLEncoder.encode(location, "UTF-8"))
            if (tag.isNotBlank()) add("tag=" + URLEncoder.encode(tag, "UTF-8"))
            if (query.isNotBlank()) add("q=" + URLEncoder.encode(query, "UTF-8"))
        }
        val qs = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return send("/api/items$qs", "GET", null) { parseBody(it) }
    }

    suspend fun item(id: String): Item = send("/api/items/$id", "GET", null) { parseBody(it) }

    suspend fun createItem(form: ItemForm, image: ByteArray?, mime: String?, fileName: String?): Item {
        val body = itemMultipart(form, image, mime, fileName)
        return send("/api/items", "POST", body) { parseBody(it) }
    }

    suspend fun updateItem(id: String, form: ItemForm, image: ByteArray?, mime: String?, fileName: String?): Item {
        val body = itemMultipart(form, image, mime, fileName)
        return send("/api/items/$id", "PUT", body) { parseBody(it) }
    }

    suspend fun deleteItem(id: String) {
        send("/api/items/$id", "DELETE", null) { Unit }
    }

    suspend fun importCsv(bytes: ByteArray, fileName: String): ImportResult {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody("text/csv".toMediaType()))
            .build()
        return send("/api/items/import", "POST", body) { parseBody(it) }
    }

    private fun itemMultipart(form: ItemForm, image: ByteArray?, mime: String?, fileName: String?): MultipartBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            // 与后端 parseItemForm 读取的 FormValue 名称一致
            .addFormDataPart("name", form.name)
            .addFormDataPart("description", form.description)
            .addFormDataPart("locationId", form.locationId)
            .addFormDataPart("locationName", form.locationName)
            .addFormDataPart("barcode", form.barcode)
            .addFormDataPart("tags", form.tags)
            .addFormDataPart("quantity", form.quantity.toString())
        if (image != null) {
            builder.addFormDataPart("image", fileName ?: "item.jpg", image.toRequestBody(mime?.toMediaType()))
        }
        return builder.build()
    }

    /** 安全的 JSON 字符串字面量（自动转义引号、反斜杠、换行）。 */
    private fun quote(s: String): String = kotlinx.serialization.json.JsonPrimitive(s).toString()
}
