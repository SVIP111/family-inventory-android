package com.family.inventory.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CSV_HEADER = "名称,位置,标签,条码,数量,备注"

private val ResultOkBg = Color(0xFFE8F5E9)
private val ResultWarnBg = Color(0xFFFFF3E0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedName by remember { mutableStateOf<String?>(null) }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                vm.messages.post("读取 CSV 失败")
                return@launch
            }
            pickedBytes = bytes
            pickedName = queryName(ctx, uri) ?: uri.lastPathSegment ?: "import.csv"
            vm.clearImportResult()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("批量导入物品", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("CSV 列顺序：名称、位置、标签、条码、数量、备注",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        HSpace(6)
                        Text("填写说明", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    VSpace(8)
                    Text(
                        "1. 第一行建议写表头：" + CSV_HEADER + "（中英文列名都支持）\n" +
                            "2. 位置可填完整路径，如「主卧 / 衣柜」；路径不存在时后端会自动逐级创建\n" +
                            "3. 标签多个用逗号分隔；数量留空按 1 件处理；文件需为 UTF-8 编码",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VSpace(10)
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("csv-header", "$CSV_HEADER\n客厅路由器,客厅,数码,6901234567890,1,备用"))
                        vm.messages.post("已复制表头示例到剪贴板")
                    }) { Text("复制表头示例", fontSize = 13.sp) }
                }
            }

            VSpace(14)
            Text("CSV 文件（UTF-8）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            VSpace(6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickCsv.launch(arrayOf("*/*")) }) { Text("选择 CSV 文件") }
                HSpace(10)
                Text(
                    pickedName ?: "未选择文件",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            VSpace(14)
            Button(
                onClick = {
                    val bytes = pickedBytes
                    if (bytes == null) {
                        vm.messages.post("请先选择 CSV 文件")
                        return@Button
                    }
                    vm.importCsv(bytes, pickedName ?: "import.csv")
                },
                enabled = pickedBytes != null && !vm.importBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (vm.importBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    HSpace(10)
                }
                Text("开始导入")
            }

            vm.importResult?.let { res ->
                VSpace(18)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (res.failed == 0) ResultOkBg else ResultWarnBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (res.failed == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            HSpace(8)
                            Text(
                                "导入完成：成功 ${res.added} 条，失败 ${res.failed} 条",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        if (vm.importErrors.isNotEmpty()) {
                            VSpace(10)
                            vm.importErrors.take(20).forEach { err ->
                                Text(
                                    "第 ${err.row} 行 ${err.name}：${err.reason}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            if (vm.importErrors.size > 20) {
                                Text("…共 ${vm.importErrors.size} 条错误，仅显示前 20 条", fontSize = 11.sp)
                            }
                        }
                    }
                }
                VSpace(12)
                TextButton(onClick = { vm.clearImportResult() }) { Text("清理结果") }
            }
            VSpace(30)
        }
    }
}

private fun queryName(ctx: android.content.Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
