package com.family.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import android.content.Context
import android.content.ContextWrapper
import coil.compose.AsyncImage
import com.family.inventory.data.Location

/**
 * 取当前 Activity 的 LifecycleOwner。
 *
 * 不走 CompositionLocal：`androidx.lifecycle.compose.LocalLifecycleOwner` 在
 * 当前 Compose 版本里没有提供方，读取会直接抛
 * "CompositionLocal LocalLifecycleOwner not present"（1.0.1 扫码闪退即此原因）。
 * 这里直接沿 Context 链找到承载它的 ComponentActivity（LocalContext 在页面组合里就是该 Activity）。
 */
@Composable
fun currentLifecycleOwner(): LifecycleOwner {
    val ctx = LocalContext.current
    return ctx.findLifecycleOwner()
        ?: throw IllegalStateException("当前上下文没有可用的 LifecycleOwner")
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findLifecycleOwner()
    else -> null
}

/** 缩略图：有图片时异步加载，无图时显示占位符。 */
@Composable
fun ThumbImage(url: String?, modifier: Modifier = Modifier, placeholder: String = "📦") {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Text(placeholder, fontSize = 22.sp)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 标签胶囊。 */
@Composable
fun TagPill(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** 详情里的「标签 + 值」两列。 */
@Composable
fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.width(84.dp)
        )
        Text(
            value.ifBlank { "—" },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 空状态占位。 */
@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📦", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

/** 全屏半透明加载层。 */
@Composable
fun LoadingOverlay(visible: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("处理中…")
            }
        }
    }
}

/** 位置树选择弹窗：用于「按位置筛选」和物品表单里的位置选择。 */
@Composable
fun LocationPickerDialog(
    locations: List<Location>,
    pathOf: (String) -> String,
    title: String,
    allowClear: Boolean,
    onDismiss: () -> Unit,
    onPick: (Location?) -> Unit
) {
    val rows = remember(locations) { flattenTree(locations) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (rows.isEmpty()) {
                Text("还没有位置，可先在「位置管理」里添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(rows, key = { it.first.id }) { (loc, depth) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            TextButton(
                                onClick = { onPick(loc); onDismiss() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        loc.name,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val path = pathOf(loc.id)
                                    if (path.isNotBlank() && depth > 0) {
                                        Text(
                                            path,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (allowClear) {
                TextButton(onClick = { onPick(null); onDismiss() }) { Text("清除筛选") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 服务器地址设置弹窗。 */
@Composable
fun ServerDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var input by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务器地址") },
        text = {
            Column {
                Text(
                    "填写部署 family-inventory 的地址，例如 http://192.168.1.10:8080",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("http://主机:端口") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "修改地址会退出当前登录，需要重新登录。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(input); onDismiss() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 按父子关系展开为 (位置, 层级) 列表，保持树的顺序。 */
fun flattenTree(locations: List<Location>): List<Pair<Location, Int>> {
    val byParent = locations.groupBy { it.parentId }.mapValues { it.value.sortedBy { l -> l.name } }
    val out = mutableListOf<Pair<Location, Int>>()
    fun walk(parent: String, depth: Int) {
        byParent[parent]?.forEach { loc ->
            out.add(loc to depth)
            if (depth < 8) walk(loc.id, depth + 1)
        }
    }
    walk("", 0)
    // 兜底：父级缺失的孤立节点也要显示
    val visited = out.map { it.first.id }.toSet()
    locations.filter { it.id !in visited }.forEach { out.add(it to 0) }
    return out
}

/** 深色小标题。 */
@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/** 页面通用列间距。 */
@Composable
fun VSpace(dp: Int) { Spacer(Modifier.height(dp.dp)) }

@Composable
fun HSpace(dp: Int) { Spacer(Modifier.width(dp.dp)) }
