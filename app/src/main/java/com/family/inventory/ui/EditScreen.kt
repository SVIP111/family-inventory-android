package com.family.inventory.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.family.inventory.data.Item
import com.family.inventory.data.ItemForm
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: MainViewModel, editingId: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val item: Item? = vm.editingItem

    var name by remember(editingId) { mutableStateOf(item?.name.orEmpty()) }
    var locationText by remember(editingId) {
        mutableStateOf(item?.let { vm.pathOf(it.locationId).ifBlank { it.locationName } }.orEmpty())
    }
    var locationId by remember(editingId) { mutableStateOf(item?.locationId.orEmpty()) }
    var tags by remember(editingId) { mutableStateOf(item?.tags?.joinToString("，").orEmpty()) }
    var barcode by remember(editingId) { mutableStateOf(item?.barcode.orEmpty()) }
    var quantity by remember(editingId) { mutableStateOf((item?.quantity ?: 1).coerceAtLeast(1)) }
    var description by remember(editingId) { mutableStateOf(item?.description.orEmpty()) }

    var imageBytes by remember(editingId) { mutableStateOf<ByteArray?>(null) }
    var imageMime by remember(editingId) { mutableStateOf<String?>(null) }
    var imageName by remember(editingId) { mutableStateOf<String?>(null) }
    var imageUri by remember(editingId) { mutableStateOf<Uri?>(null) }

    var showLocationPicker by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    val hasCamera = remember { ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY) }

    val takePhoto = rememberLauncherForActivityResult(TakePhotoContract()) { ok ->
        val file = pendingPhotoFile
        val uri = pendingPhotoUri
        if (!ok || file == null || uri == null) {
            vm.messages.post("已取消拍照")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { compressForUpload(file) }
            if (bytes == null) {
                vm.messages.post("照片处理失败，请改用相册选择")
                return@launch
            }
            imageBytes = bytes
            imageUri = uri
            imageMime = "image/jpeg"
            imageName = file.name
        }
    }

    // 拍照入口：先确认相机权限（应用声明了 CAMERA，调系统相机也需要已授权）
    val startTakePhoto = rememberCameraGate(
        onGranted = {
            val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "item_" + System.currentTimeMillis() + ".jpg")
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            // 兼容部分厂商相机：显式把读写权限授予能响应拍照的应用
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            ctx.packageManager.queryIntentActivities(intent, 0).forEach { info ->
                runCatching {
                    ctx.grantUriPermission(
                        info.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            pendingPhotoFile = file
            pendingPhotoUri = uri
            takePhoto.launch(uri)
        },
        onDenied = { vm.messages.post("需要相机权限才能拍照，请在系统设置中允许后重试") }
    )

    val startScan = rememberCameraGate(
        onGranted = { showScan = true },
        onDenied = { vm.messages.post("需要相机权限才能扫码，请在系统设置中允许后重试") }
    )

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                vm.messages.post("读取图片失败")
                return@launch
            }
            if (bytes.size > MAX_IMAGE_BYTES) {
                vm.messages.post("图片超过 10MB，请先压缩")
                return@launch
            }
            imageBytes = bytes
            imageUri = uri
            imageMime = ctx.contentResolver.getType(uri)
            imageName = displayName(ctx, uri)
        }
    }

    // 编辑态：等详情加载完再回填
    LaunchedEffect(item?.id) {
        val it = item ?: return@LaunchedEffect
        name = it.name
        locationId = it.locationId
        locationText = vm.pathOf(it.locationId).ifBlank { it.locationName }
        tags = it.tags.joinToString("，")
        barcode = it.barcode
        quantity = it.quantity.coerceAtLeast(1)
        description = it.description
    }

    val loadingItem = editingId != null && item == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (editingId == null) "新增物品" else "编辑物品", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { vm.goToItems() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (loadingItem) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            FieldLabel("物品名称 *")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("如：客厅路由器") },
                modifier = Modifier.fillMaxWidth()
            )
            VSpace(12)

            FieldLabel("所在位置 *")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = locationText,
                    onValueChange = {
                        locationText = it
                        locationId = "" // 手输按路径/名称提交，交给后端解析或自动建位置
                    },
                    singleLine = true,
                    placeholder = { Text("选择已有位置，或输入新路径（如 主卧 / 衣柜）") },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showLocationPicker = true }) { Text("选择") }
            }
            VSpace(12)

            FieldLabel("标签")
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                singleLine = true,
                placeholder = { Text("逗号分隔，如：数码，备用，户外") },
                modifier = Modifier.fillMaxWidth()
            )
            if (vm.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.tags) { tag ->
                        val active = splitTags(tags).any { it.equals(tag, ignoreCase = true) }
                        FilterChip(
                            selected = active,
                            onClick = { tags = toggleTagIn(tags, tag) },
                            label = { Text(tag, fontSize = 11.sp) }
                        )
                    }
                }
                Text(
                    "点选上面已有标签即可添加 / 取消",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            VSpace(12)

            FieldLabel("条码 / 编号")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    singleLine = true,
                    placeholder = { Text("扫描或手动输入商品条码 / 编号") },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = startScan) { Text("扫码") }
            }
            VSpace(12)

            FieldLabel("数量")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (quantity > 1) quantity-- }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(44.dp)) {
                    Text("−", fontSize = 18.sp)
                }
                Text(
                    "$quantity",
                    modifier = Modifier.padding(horizontal = 18.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(onClick = { quantity++ }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(44.dp)) {
                    Text("+", fontSize = 18.sp)
                }
            }
            VSpace(12)

            FieldLabel("备注描述")
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                minLines = 3,
                placeholder = { Text("品牌、型号、有效期、存放说明…") },
                modifier = Modifier.fillMaxWidth()
            )
            VSpace(14)

            FieldLabel("物品图片（≤10MB）")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasCamera) {
                    OutlinedButton(onClick = startTakePhoto) { Text("拍照") }
                    HSpace(8)
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    HSpace(4)
                    Text("从相册选择")
                }
                if (imageUri != null) {
                    HSpace(8)
                    TextButton(onClick = {
                        imageBytes = null
                        imageUri = null
                        imageMime = null
                        imageName = null
                    }) { Text("取消选择") }
                } else if (item?.imageFile?.isNotBlank() == true) {
                    HSpace(8)
                    Text("重新选择图片可替换原图", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val previewModel: Any? = imageUri ?: item?.imageFile?.takeIf { it.isNotBlank() }?.let { vm.uploadUrl(it) }
            if (previewModel != null) {
                VSpace(8)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            }

            VSpace(22)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.goToItems() },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("取消") }
                Button(
                    onClick = {
                        vm.saveItem(
                            editingId = editingId,
                            form = ItemForm(
                                name = name.trim(),
                                description = description.trim(),
                                locationId = locationId,
                                locationName = locationText.trim(),
                                barcode = barcode.trim(),
                                tags = tags.trim(),
                                quantity = quantity
                            ),
                            image = imageBytes,
                            mime = imageMime,
                            fileName = imageName,
                            onDone = {}
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("保存") }
            }
            VSpace(28)
        }
    }

    if (showLocationPicker) {
        LocationPickerDialog(
            locations = vm.locations,
            pathOf = vm::pathOf,
            title = "选择位置",
            allowClear = false,
            onDismiss = { showLocationPicker = false },
            onPick = { loc ->
                if (loc != null) {
                    locationId = loc.id
                    locationText = vm.pathOf(loc.id).ifBlank { loc.name }
                }
            }
        )
    }

    if (showScan) {
        ScanDialog(
            lifecycleOwner = currentLifecycleOwner(),
            onDismiss = { showScan = false },
            onResult = { text ->
                barcode = text
                showScan = false
            },
            onError = { vm.messages.post(it) }
        )
    }
}

/** 通过 SAF 取图片文件名，便于后端按扩展名兜底判断类型。 */
private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

/** ACTION_IMAGE_CAPTURE 契约：显式带上 URI 读写授权，兼容各家相机应用。 */
private class TakePhotoContract : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
}

/**
 * 相机原图动辄 5–15MB，超过后端 10MB 限制。这里按最长边 1600px 采样 + JPEG 质量递减压缩，
 * 保证上传体积可控，同时保留足够看清物品的清晰度。
 */
private fun compressForUpload(file: File): ByteArray? {
    if (!file.exists() || file.length() == 0L) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
        sample *= 2
    }
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return null

    var quality = 85
    var bytes: ByteArray
    while (true) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        if (bytes.size <= MAX_IMAGE_BYTES || quality <= 45) break
        quality -= 15
    }
    bitmap.recycle()
    return if (bytes.size <= MAX_IMAGE_BYTES) bytes else null
}

/** 与后端 normalizeTags 一致的分隔符集合。 */
private fun splitTags(raw: String): List<String> =
    raw.split(',', '，', '、', ';', '；', ' ', '\t', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** 在标签输入框里点选某个既有标签：已有则移除，没有则追加。 */
private fun toggleTagIn(raw: String, tag: String): String {
    val current = splitTags(raw)
    val hit = current.any { it.equals(tag, ignoreCase = true) }
    val next = if (hit) current.filterNot { it.equals(tag, ignoreCase = true) } else current + tag
    return next.joinToString("，")
}
