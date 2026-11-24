package com.family.inventory.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.family.inventory.data.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocRow(val loc: Location, val depth: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootName by remember { mutableStateOf("") }
    var addingChildOf by remember { mutableStateOf<Location?>(null) }
    var childName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Location?>(null) }
    var viewingImage by remember { mutableStateOf<Location?>(null) }
    var uploadTarget by remember { mutableStateOf<Location?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = uploadTarget
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                vm.messages.post("读取图片失败")
                return@launch
            }
            if (bytes.size > 10 * 1024 * 1024) {
                vm.messages.post("图片超过 10MB，请先压缩")
                return@launch
            }
            vm.uploadLocationImage(target, bytes, ctx.contentResolver.getType(uri), uri.lastPathSegment)
            uploadTarget = null
        }
    }

    val rows = remember(vm.locations, vm.expanded) {
        val out = mutableListOf<LocRow>()
        fun walk(parent: String, depth: Int) {
            vm.childrenOf(parent).forEach { loc ->
                out.add(LocRow(loc, depth))
                if (loc.id in vm.expanded && depth < 8) walk(loc.id, depth + 1)
            }
        }
        walk("", 0)
        val visited = out.map { it.loc.id }.toSet()
        vm.locations.filter { it.id !in visited }.forEach { out.add(LocRow(it, 0)) }
        out
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("位置管理", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            "维护层级位置、上传布局图、生成位置二维码",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.goToItems() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = rootName,
                    onValueChange = { rootName = it },
                    singleLine = true,
                    placeholder = { Text("根位置名称，如：主卧", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f)
                )
                HSpace(8)
                Button(onClick = {
                    vm.addLocation(rootName)
                    rootName = ""
                }, enabled = rootName.isNotBlank()) { Text("添加") }
            }

            if (rows.isEmpty()) {
                EmptyState("还没有位置", "先添加「主卧」「厨房」「储物间」等根位置，再往里挂子位置")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rows, key = { it.loc.id }) { row ->
                        LocationRow(
                            vm = vm,
                            row = row,
                            hasChildren = vm.childrenOf(row.loc.id).isNotEmpty(),
                            onToggle = { vm.toggleExpanded(row.loc.id) },
                            onAddChild = { addingChildOf = row.loc; childName = "" },
                            onUploadImage = {
                                uploadTarget = row.loc
                                pickImage.launch("image/*")
                            },
                            onViewImage = { viewingImage = row.loc },
                            onQr = { vm.showLocationQr(row.loc) },
                            onDelete = { deleting = row.loc }
                        )
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }

    addingChildOf?.let { parent ->
        AlertDialog(
            onDismissRequest = { addingChildOf = null },
            title = { Text("在「${parent.name}」下添加子位置") },
            text = {
                OutlinedTextField(
                    value = childName,
                    onValueChange = { childName = it },
                    singleLine = true,
                    placeholder = { Text("如：衣柜 / 第一层") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addLocation(childName, parent.id)
                    addingChildOf = null
                }, enabled = childName.isNotBlank()) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { addingChildOf = null }) { Text("取消") } }
        )
    }

    deleting?.let { loc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除位置") },
            text = {
                Text("确定删除「${vm.pathOf(loc.id).ifBlank { loc.name }}」？该位置下还有物品或子位置时会被服务器拒绝。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLocation(loc)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }

    viewingImage?.let { loc ->
        Dialog(onDismissRequest = { viewingImage = null }) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${vm.pathOf(loc.id).ifBlank { loc.name }} · 布局图",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (loc.imageFile.isBlank()) {
                        Text("该位置还没有布局图", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        AsyncImage(
                            model = vm.uploadUrl(loc.imageFile),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(360.dp)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewingImage = null }) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationRow(
    vm: MainViewModel,
    row: LocRow,
    hasChildren: Boolean,
    onToggle: () -> Unit,
    onAddChild: () -> Unit,
    onUploadImage: () -> Unit,
    onViewImage: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit
) {
    val loc = row.loc
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (hasChildren) onToggle() }
                .padding(start = (8 + row.depth * 16).dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                Icon(
                    if (loc.id in vm.expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.width(20.dp))
            }
            ThumbImage(
                url = loc.imageFile.takeIf { it.isNotBlank() }?.let { vm.uploadUrl(it) },
                modifier = Modifier.size(38.dp),
                placeholder = "📍"
            )
            HSpace(10)
            Column(modifier = Modifier.weight(1f)) {
                Text(loc.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "当前列表 ${vm.itemCountInSubtree(loc.id)} 件" + if (loc.imageFile.isNotBlank()) " · 有布局图" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onAddChild) {
                Icon(Icons.Default.Add, contentDescription = "添加子位置", modifier = Modifier.size(18.dp))
            }
            TextButton(onClick = if (loc.imageFile.isNotBlank()) onViewImage else onUploadImage) {
                Text(if (loc.imageFile.isNotBlank()) "查看图" else "上传图", fontSize = 12.sp)
            }
            TextButton(onClick = onQr) { Text("二维码", fontSize = 12.sp) }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除位置",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // 上传过布局图时，顺手提供替换入口
        if (loc.imageFile.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = (44 + row.depth * 16).dp, bottom = 4.dp)) {
                TextButton(onClick = onUploadImage, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("替换布局图", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
