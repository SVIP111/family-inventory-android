package com.family.inventory.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.family.inventory.BuildConfig
import com.family.inventory.data.Item

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemsScreen(vm: MainViewModel) {
    var showServerDialog by remember { mutableStateOf(false) }
    var showLocationFilter by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Item?>(null) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var showSelectedQr by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showVersion by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val startScan = rememberCameraGate(
        onGranted = { showScan = true },
        onDenied = { vm.messages.post("需要相机权限才能扫码，请在系统设置中允许后重试") }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("家庭物资管理", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${vm.stats.items} 件物品 · ${vm.stats.locations} 个位置 · ${vm.stats.withImage} 张图片",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {},
                actions = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (vm.selectMode) {
                BulkActionBar(
                    allSelected = vm.selected.size == vm.items.size && vm.items.isNotEmpty(),
                    selectedCount = vm.selected.size,
                    onSelectAll = { vm.toggleSelectAll() },
                    onQr = { showSelectedQr = true },
                    onDelete = { showBatchDelete = true },
                    onExit = { vm.toggleSelectMode() }
                )
            } else {
                HomeBottomBar(
                    onHome = {
                        vm.goToItems()
                        vm.reloadItems()
                    },
                    onLocations = { vm.goToLocations() },
                    onDashboard = { vm.goToDashboard() },
                    onTags = { vm.goToTags() },
                    onBatch = { vm.toggleSelectMode() },
                    onMore = { showMore = true }
                )
            }
        },
        floatingActionButton = {
            if (!vm.selectMode) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openEdit(null) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("新增物品") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索 + 扫码
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.applyQuery(it) },
                    singleLine = true,
                    placeholder = { Text("搜索名称、位置、条码…", fontSize = 13.sp, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.applyQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = startScan) { Text("扫码") }
            }

            // 筛选行：位置 + 标签
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = vm.filterLocation != null || vm.onlyUnassigned,
                        onClick = { showLocationFilter = true },
                        label = {
                            Text(
                                when {
                                    vm.onlyUnassigned -> MainViewModel.UNASSIGNED_LABEL
                                    vm.filterLocation != null ->
                                        vm.pathOf(vm.filterLocation!!.id).ifBlank { vm.filterLocation!!.name }
                                    else -> "全部位置"
                                },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
                items(vm.tags) { tag ->
                    FilterChip(
                        selected = vm.filterTag.equals(tag, ignoreCase = true),
                        onClick = { vm.toggleTagFilter(tag) },
                        label = { Text(tag, fontSize = 12.sp) }
                    )
                }
                if (vm.onlyUntagged) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { vm.applyDashboardTag(MainViewModel.NO_TAG_LABEL) },
                            label = { Text(MainViewModel.NO_TAG_LABEL, fontSize = 12.sp) }
                        )
                    }
                }
                if (vm.filterTag.isNotEmpty() || vm.filterLocation != null || vm.query.isNotEmpty() ||
                    vm.onlyUnassigned || vm.onlyUntagged
                ) {
                    item {
                        TextButton(onClick = { vm.clearFilters() }) { Text("清除筛选", fontSize = 12.sp) }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            if (vm.items.isEmpty()) {
                EmptyState(
                    title = if (vm.query.isNotBlank() || vm.filterTag.isNotBlank() ||
                        vm.filterLocation != null || vm.onlyUnassigned || vm.onlyUntagged
                    ) "没有匹配的物品" else "还没有物品",
                    subtitle = "点右下角「新增物品」开始登记，或在「更多」里批量导入 CSV"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(vm.items, key = { it.id }) { item ->
                        ItemCard(
                            vm = vm,
                            item = item,
                            selected = item.id in vm.selected,
                            selectMode = vm.selectMode,
                            onClick = {
                                if (vm.selectMode) vm.toggleSelected(item.id) else vm.detailItem = item
                            },
                            onLongClick = {
                                if (!vm.selectMode) {
                                    vm.toggleSelectMode()
                                    vm.toggleSelected(item.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ---------- 物品详情 ----------
    vm.detailItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { vm.detailItem = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ItemDetailSheet(
                vm = vm,
                item = item,
                onClose = { vm.detailItem = null },
                onEdit = {
                    vm.detailItem = null
                    vm.openEdit(item.id)
                },
                onDelete = { confirmDelete = item },
                onQr = { vm.showItemQr(item) }
            )
        }
    }

    if (showLocationFilter) {
        LocationPickerDialog(
            locations = vm.locations,
            pathOf = vm::pathOf,
            title = "按位置筛选",
            allowClear = true,
            onDismiss = { showLocationFilter = false },
            onPick = { vm.applyLocationFilter(it) }
        )
    }

    if (showServerDialog) {
        ServerDialog(
            current = vm.serverInput,
            onDismiss = { showServerDialog = false },
            onSave = { vm.changeServer(it) }
        )
    }

    if (showMore) {
        ModalBottomSheet(
            onDismissRequest = { showMore = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
            ) {
                Text(
                    "更多",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp)
                )
                MoreRow(
                    Icons.Default.AddCircle,
                    "批量导入 CSV",
                    "从 CSV 文件一次导入多条物品",
                    onClick = {
                        showMore = false
                        vm.goToImport()
                    }
                )
                MoreRow(
                    Icons.Default.Settings,
                    "服务器设置",
                    "当前：${vm.baseUrl()}",
                    onClick = {
                        showMore = false
                        showServerDialog = true
                    }
                )
                MoreRow(
                    Icons.Default.Info,
                    "版本信息",
                    "客户端 v${BuildConfig.VERSION_NAME} · 服务器 " +
                        (vm.serverVersion?.let { "v$it" } ?: "未获取"),
                    onClick = {
                        showMore = false
                        showVersion = true
                        vm.loadServerVersion()
                    }
                )
                MoreRow(
                    Icons.Default.ExitToApp,
                    "退出登录",
                    "账号：${vm.username}",
                    onClick = {
                        showMore = false
                        vm.logout()
                    }
                )
            }
        }
    }

    if (showVersion) {
        AlertDialog(
            onDismissRequest = { showVersion = false },
            title = { Text("版本信息") },
            text = {
                Column {
                    KeyValueRow("客户端版本", "v${BuildConfig.VERSION_NAME}")
                    KeyValueRow("versionCode", BuildConfig.VERSION_CODE.toString())
                    KeyValueRow("构建类型", BuildConfig.BUILD_TYPE)
                    KeyValueRow("应用包名", BuildConfig.APPLICATION_ID)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    KeyValueRow("服务器地址", vm.baseUrl())
                    KeyValueRow(
                        "服务器版本",
                        vm.serverVersion?.let { "v$it" } ?: "未获取（服务器未连接）"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.loadServerVersion() }) { Text("刷新") }
            },
            dismissButton = {
                TextButton(onClick = { showVersion = false }) { Text("关闭") }
            }
        )
    }

    if (showScan) {
        ScanDialog(
            lifecycleOwner = currentLifecycleOwner(),
            onDismiss = { showScan = false },
            onResult = { text ->
                showScan = false
                vm.handleScanResult(text)
            },
            onError = { vm.messages.post(it) }
        )
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除物品") },
            text = { Text("确定删除「${item.name}」？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteItem(item.id)
                    confirmDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }

    if (showSelectedQr) {
        val selectedItems = vm.items.filter { it.id in vm.selected }
        Dialog(
            onDismissRequest = { showSelectedQr = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.94f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "所选物品二维码（${selectedItems.size}）",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showSelectedQr = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                    Text(
                        "可截图保存，或逐个扫码打开网页详情。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VSpace(8)
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        items(selectedItems, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(
                                        vm.qrImageUrl(vm.itemQrText(item.id))
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(92.dp)
                                )
                                HSpace(12)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        vm.pathOf(item.locationId).ifBlank { item.locationName },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("×${item.quantity}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }

    if (showBatchDelete) {
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text("批量删除") },
            text = { Text("将删除已选的 ${vm.selected.size} 件物品，删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSelected()
                    showBatchDelete = false
                }) { Text("全部删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBatchDelete = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(
    vm: MainViewModel,
    item: Item,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box {
            Column {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.25f)) {
                    ThumbImage(
                        url = item.imageFile.takeIf { it.isNotBlank() }?.let { vm.uploadUrl(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            "×${item.quantity}",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        item.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    val path = vm.pathOf(item.locationId).ifBlank { item.locationName.ifBlank { "未分配位置" } }
                    Text(
                        path,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.barcode.isNotBlank()) {
                        Text(
                            "⌗ ${item.barcode}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            item.tags.take(2).forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        tag,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (selectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                ) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun ItemDetailSheet(
    vm: MainViewModel,
    item: Item,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQr: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "关闭") }
        }
        if (item.imageFile.isNotBlank()) {
            ThumbImage(
                url = vm.uploadUrl(item.imageFile),
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp),
                placeholder = ""
            )
        }
        VSpace(8)
        KeyValueRow("所在位置", vm.pathOf(item.locationId).ifBlank { item.locationName.ifBlank { "未分配位置" } })
        KeyValueRow("数量", "×${item.quantity}")
        KeyValueRow("条码 / 编号", item.barcode)
        KeyValueRow("标签", item.tags.joinToString("，"))
        KeyValueRow("备注描述", item.description)
        KeyValueRow("创建时间", formatTime(item.createdAt))
        KeyValueRow("更新时间", formatTime(item.updatedAt))

        VSpace(14)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onQr) { Text("二维码") }
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                HSpace(4)
                Text("编辑")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                HSpace(4)
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 后端返回 RFC3339 时间（如 2026-11-24T00:12:39+08:00），这里裁剪为 年-月-日 时:分。 */
fun formatTime(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val t = raw.replace("T", " ")
        if (t.length >= 16) t.substring(0, 16) else t
    }.getOrDefault(raw)
}

/** 底部导航：中间是放大的「主页」按钮，其余菜单均匀分布。 */
@Composable
private fun HomeBottomBar(
    onHome: () -> Unit,
    onLocations: () -> Unit,
    onDashboard: () -> Unit,
    onTags: () -> Unit,
    onBatch: () -> Unit,
    onMore: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(74.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Icons.Default.Place, "位置", onLocations, Modifier.weight(1f))
            BottomNavItem(
                icon = null,
                drawableId = com.family.inventory.R.drawable.ic_dashboard,
                label = "仪表盘",
                onClick = onDashboard,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.weight(1.1f), contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable(onClick = onHome),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "主页",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
            BottomNavItem(
                icon = null,
                drawableId = com.family.inventory.R.drawable.ic_tag,
                label = "标签",
                onClick = onTags,
                modifier = Modifier.weight(1f)
            )
            BottomNavItem(Icons.Default.List, "批量", onBatch, Modifier.weight(1f))
            BottomNavItem(Icons.Default.MoreVert, "更多", onMore, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector? = null,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    drawableId: Int? = null
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (drawableId != null) {
            Icon(
                painter = painterResource(drawableId),
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (icon != null) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 批量模式下的底部操作栏，替代底部导航。 */
@Composable
private fun BulkActionBar(
    allSelected: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit,
    onExit: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSelectAll) { Text(if (allSelected) "取消全选" else "全选") }
                Text("已选 $selectedCount 件", fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onExit) { Text("退出批量") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onQr,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("导出二维码") }
                Button(
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除所选") }
            }
        }
    }
}
