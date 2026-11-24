package com.family.inventory.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.inventory.data.ApiClient
import com.family.inventory.data.ApiException
import com.family.inventory.data.AuthStatus
import com.family.inventory.data.ImportError
import com.family.inventory.data.Item
import com.family.inventory.data.ItemForm
import com.family.inventory.data.Location
import com.family.inventory.data.SessionStore
import com.family.inventory.data.Stats
import com.family.inventory.data.Summary
import com.family.inventory.data.UnauthorizedException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** 应用内页面栈（单 Activity，用状态切换代替 Navigation 组件，减少依赖）。 */
sealed interface Screen {
    data object Login : Screen
    data object Items : Screen
    data object Locations : Screen
    data object Import : Screen
    data object Dashboard : Screen
    data object Tags : Screen
    data class Edit(val editingId: String? = null) : Screen
    data class Qr(val title: String, val text: String, val meta: String) : Screen
}

/** 一次性提示消息（Snackbar）。 */
class Messages {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = flow
    fun post(text: String) { flow.tryEmit(text) }
}

class MainViewModel(
    private val session: SessionStore,
    private val api: ApiClient
) : ViewModel() {

    val messages = Messages()

    // ---------- 全局状态 ----------
    var screen by mutableStateOf<Screen>(Screen.Login)
        private set
    var loading by mutableStateOf(false)
        private set

    var serverInput by mutableStateOf(session.lastServerInput.ifBlank { session.baseUrl })
        private set
    var authStatus by mutableStateOf<AuthStatus?>(null)
        private set
    var registerMode by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    var authBusy by mutableStateOf(false)
        private set
    var username by mutableStateOf(session.username)

    // ---------- 数据 ----------
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var locations by mutableStateOf<List<Location>>(emptyList())
        private set
    var tags by mutableStateOf<List<String>>(emptyList())
        private set
    var stats by mutableStateOf(Stats())
        private set

    /** 仪表盘汇总（GET /api/summary），进入仪表盘或标签管理时刷新。 */
    var summary by mutableStateOf<Summary?>(null)
        private set
    var summaryLoading by mutableStateOf(false)
        private set

    /** 服务端版本号（GET /api/version，免鉴权），未连上时为 null。 */
    var serverVersion by mutableStateOf<String?>(null)
        private set

    // ---------- 筛选 ----------
    var query by mutableStateOf("")
        private set
    var filterLocation by mutableStateOf<Location?>(null)
        private set
    var filterTag by mutableStateOf("")
        private set

    /** 仪表盘图例「未分配位置」/「无标签」在服务端没有对应查询参数，改为拉全量后本地过滤。 */
    var onlyUnassigned by mutableStateOf(false)
        private set
    var onlyUntagged by mutableStateOf(false)
        private set

    // ---------- 批量选择 ----------
    var selectMode by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    // ---------- 详情 ----------
    var detailItem by mutableStateOf<Item?>(null)

    /** 编辑页正在处理的物品（新增时为 null）。 */
    var editingItem by mutableStateOf<Item?>(null)
        private set

    // ---------- 位置管理 ----------
    var expanded by mutableStateOf<Set<String>>(emptySet())
        private set

    // ---------- 导入 ----------
    var importResult by mutableStateOf<com.family.inventory.data.ImportResult?>(null)
        private set
    var importErrors by mutableStateOf<List<ImportError>>(emptyList())
        private set
    var importBusy by mutableStateOf(false)
        private set

    private var searchJob: Job? = null

    init {
        bootstrap()
    }

    // ---------- 启动 / 鉴权 ----------

    private fun bootstrap() {
        if (!session.hasServer || !session.isLoggedIn) {
            screen = Screen.Login
            if (session.hasServer) refreshAuthStatus()
            return
        }
        loadAll(goToItems = true)
    }

    fun onServerInputChange(v: String) {
        serverInput = v
    }

    /** 保存服务器地址（登录页与设置弹窗共用）。 */
    fun applyServer(raw: String): Boolean {
        val normalized = SessionStore.normalizeBaseUrl(raw)
        if (normalized.isBlank()) {
            authError = "请填写服务器地址，例如 http://192.168.1.10:8080"
            return false
        }
        session.baseUrl = normalized
        session.lastServerInput = raw
        serverInput = raw
        authError = null
        return true
    }

    fun refreshAuthStatus() {
        viewModelScope.launch {
            try {
                loadServerVersion()
                val status = api.authStatus()
                authStatus = status
                // 服务器上还没有任何账号时，直接切到首次注册
                if (!status.registered) registerMode = true
            } catch (e: Exception) {
                // 服务器不可达时在登录页给出提示，不阻断输入
                authError = e.message
            }
        }
    }

    fun applyRegisterMode(value: Boolean) {
        registerMode = value
        authError = null
    }

    fun login(user: String, pass: String) {
        if (authBusy) return
        if (!applyServer(serverInput)) return
        authBusy = true
        authError = null
        viewModelScope.launch {
            try {
                val res = api.login(user.trim(), pass)
                session.saveAuth(res.token, res.username)
                username = res.username
                loadAll(goToItems = true)
            } catch (e: Exception) {
                authError = e.message ?: "登录失败"
                if (e is ApiException && e.code == 401) refreshAuthStatus()
            } finally {
                authBusy = false
            }
        }
    }

    fun register(user: String, pass: String, confirm: String) {
        if (authBusy) return
        if (!applyServer(serverInput)) return
        authBusy = true
        authError = null
        viewModelScope.launch {
            try {
                val res = api.register(user.trim(), pass, confirm)
                session.saveAuth(res.token, res.username)
                username = res.username
                loadAll(goToItems = true)
            } catch (e: Exception) {
                authError = e.message ?: "注册失败"
            } finally {
                authBusy = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() }
            session.clearAuth()
            username = ""
            items = emptyList()
            locations = emptyList()
            tags = emptyList()
            stats = Stats()
            selectMode = false
            selected = emptySet()
            screen = Screen.Login
            refreshAuthStatus()
        }
    }

    // ---------- 数据加载 ----------

    /** 关闭设置弹窗后切换服务器：清空登录态并要求重新登录。 */
    fun changeServer(raw: String) {
        if (!applyServer(raw)) return
        session.clearAuth()
        username = ""
        screen = Screen.Login
        refreshAuthStatus()
    }

    fun loadAll(goToItems: Boolean = false) {
        viewModelScope.launch {
            loading = true
            try {
                val locs = api.locations()
                locations = locs
                runCatching { api.version() }.onSuccess { serverVersion = it }
                tags = api.tags()
                stats = api.stats()
                items = fetchFilteredItems()
                if (goToItems) screen = Screen.Items
            } catch (e: Exception) {
                handleError(e)
            } finally {
                loading = false
            }
        }
    }

    fun reloadItems() {
        viewModelScope.launch {
            try {
                items = fetchFilteredItems()
                stats = api.stats()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /**
     * 列表数据：正常情况直接用服务端筛选；「未分配位置」「无标签」这两个虚拟分片
     * 服务端没有查询参数，改为按对应维度拉全量后本地过滤。
     */
    private suspend fun fetchFilteredItems(): List<Item> {
        if (onlyUnassigned) {
            return api.items("", if (onlyUntagged) "" else filterTag, query)
                .filter { it.locationId.isBlank() }
                .let { list -> if (onlyUntagged) list.filter { it.tags.isEmpty() } else list }
        }
        if (onlyUntagged) {
            return api.items(filterLocation?.id ?: "", "", query)
                .filter { it.tags.isEmpty() }
        }
        return api.items(filterLocation?.id ?: "", filterTag, query)
    }

    fun applyQuery(v: String) {
        query = v
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350) // 输入防抖
            reloadItems()
        }
    }

    fun clearFilters() {
        query = ""
        filterTag = ""
        filterLocation = null
        onlyUnassigned = false
        onlyUntagged = false
        reloadItems()
    }

    fun toggleTagFilter(tag: String) {
        filterTag = if (filterTag.equals(tag, ignoreCase = true)) "" else tag
        onlyUntagged = false
        reloadItems()
    }

    fun applyLocationFilter(loc: Location?) {
        filterLocation = loc
        onlyUnassigned = false
        reloadItems()
    }

    /** 只想看未分配位置的物品（仪表盘图例「未分配位置」）。 */
    fun filterUnassigned() {
        query = ""
        filterTag = ""
        filterLocation = null
        onlyUntagged = false
        onlyUnassigned = true
        screen = Screen.Items
        reloadItems()
    }

    // ---------- 批量 ----------

    fun toggleSelectMode() {
        selectMode = !selectMode
        if (!selectMode) selected = emptySet()
    }

    fun toggleSelected(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun toggleSelectAll() {
        selected = if (selected.size == items.size) emptySet() else items.map { it.id }.toSet()
    }

    fun deleteSelected() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            loading = true
            var failed = 0
            for (id in ids) {
                try {
                    api.deleteItem(id)
                } catch (e: UnauthorizedException) {
                    handleError(e); loading = false; return@launch
                } catch (e: Exception) {
                    failed++
                }
            }
            loading = false
            selected = emptySet()
            selectMode = false
            reloadItems()
            messages.post(if (failed == 0) "已删除 ${ids.size} 件物品" else "成功 ${ids.size - failed} 件，失败 $failed 件")
        }
    }

    // ---------- 物品增删改 ----------

    fun saveItem(
        editingId: String?,
        form: ItemForm,
        image: ByteArray?,
        mime: String?,
        fileName: String?,
        onDone: () -> Unit
    ) {
        if (form.name.isBlank()) {
            messages.post("物品名称不能为空")
            return
        }
        viewModelScope.launch {
            loading = true
            try {
                if (editingId == null) {
                    api.createItem(form, image, mime, fileName)
                    messages.post("已添加：${form.name}")
                } else {
                    api.updateItem(editingId, form, image, mime, fileName)
                    messages.post("已保存：${form.name}")
                }
                detailItem = null
                screen = Screen.Items
                // 刷新统计与列表
                reloadItems()
                stats = api.stats()
                locations = api.locations()
                tags = api.tags()
                onDone()
            } catch (e: Exception) {
                handleError(e)
            } finally {
                loading = false
            }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            try {
                api.deleteItem(id)
                detailItem = null
                reloadItems()
                messages.post("已删除物品")
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    // ---------- 位置 ----------

    fun addLocation(name: String, parentId: String = "") {
        if (name.isBlank()) {
            messages.post("位置名称不能为空")
            return
        }
        viewModelScope.launch {
            try {
                val loc = api.createLocation(name.trim(), parentId)
                locations = api.locations()
                stats = api.stats()
                if (parentId.isNotBlank()) expanded = expanded + parentId
                messages.post("已添加位置：${loc.name}")
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun deleteLocation(loc: Location) {
        viewModelScope.launch {
            try {
                api.deleteLocation(loc.id)
                locations = api.locations()
                stats = api.stats()
                if (filterLocation?.id == loc.id) filterLocation = null
                messages.post("已删除位置：${loc.name}")
                reloadItems()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun uploadLocationImage(loc: Location, bytes: ByteArray, mime: String?, name: String?) {
        viewModelScope.launch {
            loading = true
            try {
                api.uploadLocationImage(loc.id, bytes, mime, name)
                locations = api.locations()
                messages.post("已更新布局图：${loc.name}")
            } catch (e: Exception) {
                handleError(e)
            } finally {
                loading = false
            }
        }
    }

    fun toggleExpanded(id: String) {
        expanded = if (id in expanded) expanded - id else expanded + id
    }

    // ---------- 导入 ----------

    fun importCsv(bytes: ByteArray, fileName: String) {
        if (importBusy) return
        importBusy = true
        importResult = null
        importErrors = emptyList()
        viewModelScope.launch {
            try {
                val res = api.importCsv(bytes, fileName)
                importResult = res
                importErrors = res.errors
                locations = api.locations()
                stats = api.stats()
                reloadItems()
                messages.post("导入完成：成功 ${res.added} 条，失败 ${res.failed} 条")
            } catch (e: Exception) {
                handleError(e)
            } finally {
                importBusy = false
            }
        }
    }

    fun clearImportResult() {
        importResult = null
        importErrors = emptyList()
    }

    // ---------- 页面跳转 ----------

    fun goToItems() {
        detailItem = null
        screen = Screen.Items
    }

    fun goToLocations() {
        screen = Screen.Locations
    }

    fun goToImport() {
        screen = Screen.Import
    }

    fun goToDashboard() {
        screen = Screen.Dashboard
        loadSummary()
    }

    fun goToTags() {
        screen = Screen.Tags
        loadSummary()
    }

    // ---------- 仪表盘 / 标签 ----------

    /** 读取服务端版本号；失败静默（不弹错误，页面显示「未获取」即可）。 */
    fun loadServerVersion() {
        viewModelScope.launch {
            serverVersion = runCatching { api.version() }.getOrNull()
        }
    }

    fun loadSummary() {
        viewModelScope.launch {
            summaryLoading = true
            try {
                summary = api.summary()
            } catch (e: Exception) {
                handleError(e)
            } finally {
                summaryLoading = false
            }
        }
    }

    /**
     * 仪表盘点位置图例：只按位置这一维度筛选（清掉标签与关键字，与网页端一致）。
     * 「未分配位置」在服务端没有对应参数，改为本地过滤。
     */
    fun applyDashboardLocation(name: String) {
        query = ""
        filterTag = ""
        onlyUntagged = false
        if (name == UNASSIGNED_LABEL) {
            filterLocation = null
            onlyUnassigned = !onlyUnassigned
        } else {
            onlyUnassigned = false
            val loc = locations.firstOrNull { pathOf(it.id) == name }
                ?: locations.firstOrNull { it.name == name }
            filterLocation = loc
            if (loc == null) messages.post("没有找到位置「$name」，已显示全部物品")
        }
        screen = Screen.Items
        reloadItems()
    }

    /** 仪表盘点标签图例：只按标签这一维度筛选；「无标签」本地过滤。 */
    fun applyDashboardTag(name: String) {
        query = ""
        filterLocation = null
        onlyUnassigned = false
        if (name == NO_TAG_LABEL) {
            // 再点一次同名分片＝取消该筛选（与网页端「再次点击取消维度」一致）
            filterTag = ""
            onlyUntagged = !onlyUntagged
        } else {
            onlyUntagged = false
            filterTag = name
        }
        screen = Screen.Items
        reloadItems()
    }

    /** 仪表盘 Top 榜点击：回到列表并打开该物品详情。 */
    fun openItemDetail(id: String) {
        screen = Screen.Items
        val cached = items.firstOrNull { it.id == id }
        if (cached != null) {
            detailItem = cached
            return
        }
        viewModelScope.launch {
            try {
                detailItem = api.item(id)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /** PUT /api/tags/rename：跨物品改名并同名合并，成功后刷新标签、列表与汇总。 */
    fun renameTag(oldName: String, newName: String) {
        val old = oldName.trim()
        val new = newName.trim()
        if (new.isEmpty()) {
            messages.post("新标签名称不能为空")
            return
        }
        if (old.equals(new, ignoreCase = true)) return
        viewModelScope.launch {
            try {
                tags = api.renameTag(old, new)
                if (filterTag.equals(old, ignoreCase = true)) filterTag = new
                messages.post("标签「$old」已改名")
                reloadItems()
                loadSummary()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun openEdit(editingId: String?) {
        screen = Screen.Edit(editingId)
        editingItem = editingId?.let { id -> items.firstOrNull { it.id == id } }
        if (editingId != null && editingItem == null) {
            viewModelScope.launch {
                try {
                    editingItem = api.item(editingId)
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        }
    }

    // ---------- 二维码 ----------

    fun showItemQr(item: Item) {
        val text = api.itemQrText(item.id)
        screen = Screen.Qr(
            title = "物品二维码",
            text = text,
            meta = "${item.name} · ${pathOf(item.locationId).ifBlank { item.locationName.ifBlank { "未分配位置" } }} · 数量 ×${item.quantity}"
        )
    }

    fun showLocationQr(loc: Location) {
        val text = api.locationQrText(loc.id)
        screen = Screen.Qr(title = "位置二维码", text = text, meta = pathOf(loc.id).ifBlank { loc.name })
    }

    fun showItemQrById(id: String) {
        viewModelScope.launch {
            try {
                val it = items.find { x -> x.id == id } ?: api.item(id)
                showItemQr(it)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    // ---------- 扫码结果 ----------

    /**
     * 扫码结果分发（纯客户端解析，服务端无需改动）：
     * - 扫到本应用的物品二维码 `${base}/?item=<id>` → 直接打开该物品详情
     * - 扫到位置二维码 `${base}/?loc=<id>` → 直接按该位置筛选列表
     * - 其他内容（普通商品条码、纯数字、任意文本）→ 作为关键词搜索
     */
    fun handleScanResult(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return

        val params = runCatching { java.net.URI(text) }.getOrNull()?.rawQuery
            ?.split("&")
            ?.mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else {
                    val k = pair.substring(0, idx).lowercase()
                    val v = runCatching {
                        java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    }.getOrDefault(pair.substring(idx + 1)).trim()
                    if (v.isEmpty()) null else k to v
                }
            }
            .orEmpty()

        fun param(name: String): String? = params.firstOrNull { it.first == name }?.second

        val itemId = param("item")
        val locId = param("loc")

        when {
            itemId != null -> {
                if (selectMode) toggleSelectMode()
                val cached = items.firstOrNull { it.id == itemId }
                if (cached != null) {
                    detailItem = cached
                    messages.post("已定位物品：${cached.name}")
                } else {
                    viewModelScope.launch {
                        try {
                            val target = api.item(itemId)
                            detailItem = target
                            messages.post("已定位物品：${target.name}")
                        } catch (e: Exception) {
                            handleError(e)
                        }
                    }
                }
            }

            locId != null -> {
                val loc = locations.firstOrNull { it.id == locId }
                if (loc != null) {
                    applyLocationFilter(loc)
                    messages.post("已筛选位置：${pathOf(loc.id).ifBlank { loc.name }}")
                } else {
                    messages.post("未找到该位置，可能已被删除")
                }
            }

            else -> {
                applyQuery(text)
                messages.post("已按「$text」搜索")
            }
        }
    }

    // ---------- 辅助 ----------

    /** 位置完整路径（如 主卧 / 衣柜），与后端 locationPathLocked 一致。 */
    fun pathOf(locationId: String): String {
        if (locationId.isBlank()) return ""
        val byId = locations.associateBy { it.id }
        var cur = byId[locationId] ?: return ""
        val parts = mutableListOf<String>()
        var guard = 0
        while (guard++ < 32) {
            parts.add(0, cur.name)
            val parent = cur.parentId
            if (parent.isBlank()) break
            cur = byId[parent] ?: break
        }
        return parts.joinToString(" / ")
    }

    fun childrenOf(parentId: String): List<Location> =
        locations.filter { it.parentId == parentId }.sortedBy { it.name }

    fun itemCountInSubtree(locationId: String): Int {
        val ids = subtreeIds(locationId)
        return items.count { it.locationId in ids }
    }

    private fun subtreeIds(root: String): Set<String> {
        val out = mutableSetOf(root)
        var guard = 0
        var changed = true
        while (changed && guard++ < 32) {
            changed = false
            locations.forEach { l ->
                if (l.parentId in out && l.id !in out) {
                    out.add(l.id); changed = true
                }
            }
        }
        return out
    }

    fun qrImageUrl(text: String): String = api.qrImageUrl(text)

    /** 物品二维码内容（批量导出二维码列表使用）。 */
    fun itemQrText(id: String): String = api.itemQrText(id)

    fun uploadUrl(file: String): String = api.uploadUrl(file)

    fun baseUrl(): String = api.base

    companion object {
        /** 后端 Summary 里无位置物品的分片名。 */
        const val UNASSIGNED_LABEL = "未分配位置"

        /** 后端 Summary 里无标签物品的分片名。 */
        const val NO_TAG_LABEL = "无标签"
    }

    private fun handleError(e: Exception) {
        when (e) {
            is UnauthorizedException -> {
                session.clearAuth()
                username = ""
                authError = e.message ?: "登录已过期，请重新登录"
                screen = Screen.Login
            }
            else -> messages.post(e.message ?: "操作失败")
        }
    }
}
