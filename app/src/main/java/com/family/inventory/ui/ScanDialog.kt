package com.family.inventory.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 扫码弹窗：CameraX 预览 + ZXing 解析（一维码与二维码）。
 *
 * 注意：相机权限与 LifecycleOwner 都由调用页（Activity 层级）传入，
 * 不再在弹窗内部申请权限——弹窗内注册 ActivityResult / 查找 ViewTreeLifecycleOwner
 * 在部分机型上会抛异常导致闪退。
 *
 * @param lifecycleOwner Activity 级生命周期（LocalLifecycleOwner.current），
 *                       相机绑定到 Activity 而不是弹窗，避免弹窗销毁时解绑异常。
 * @param onError 相机初始化失败时回调，页面用 Snackbar 提示，而不是直接崩溃。
 */
@Composable
fun ScanDialog(
    lifecycleOwner: LifecycleOwner,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit = {}
) {
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("扫码录入", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 4.dp)
                ) {
                    val err = errorText
                    if (err != null) {
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp)
                        )
                    } else {
                        CameraPreview(
                            lifecycleOwner = lifecycleOwner,
                            onCode = onResult,
                            onError = {
                                errorText = it
                                onError(it)
                            }
                        )
                    }
                }
                Text(
                    "将条码 / 二维码对准取景框，识别成功后会自动填入。若长时间无画面，请确认已授予相机权限。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: LifecycleOwner,
    onCode: (String) -> Unit,
    onError: (String) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val done = remember { AtomicBoolean(false) }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.POSSIBLE_FORMATS to listOf(
                        BarcodeFormat.QR_CODE,
                        BarcodeFormat.CODE_128,
                        BarcodeFormat.CODE_39,
                        BarcodeFormat.EAN_13,
                        BarcodeFormat.EAN_8,
                        BarcodeFormat.UPC_A,
                        BarcodeFormat.UPC_E,
                        BarcodeFormat.ITF,
                        BarcodeFormat.DATA_MATRIX
                    )
                )
            )
        }
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var executor by remember { mutableStateOf<ExecutorService?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { provider?.unbindAll() }
            provider = null
            runCatching { executor?.shutdown() }
            executor = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            val previewView = try {
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            } catch (t: Throwable) {
                onError("相机预览控件创建失败：${t.message ?: t.javaClass.simpleName}")
                return@AndroidView android.view.View(viewContext)
            }
            val providerFuture = try {
                ProcessCameraProvider.getInstance(ctx.applicationContext)
            } catch (t: Throwable) {
                onError("相机服务不可用：${t.message ?: t.javaClass.simpleName}")
                return@AndroidView android.view.View(viewContext)
            }
            providerFuture.addListener({
                runCatching {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysisExecutor = Executors.newSingleThreadExecutor()
                    executor = analysisExecutor

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        try {
                            if (!done.get()) {
                                val text = runCatching { decodeImage(image, reader) }.getOrNull()
                                if (!text.isNullOrBlank() && done.compareAndSet(false, true)) {
                                    mainHandler.post { onCode(text) }
                                }
                            }
                        } finally {
                            image.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }.onFailure { e ->
                    onError("相机启动失败：${e.message ?: e.javaClass.simpleName}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

/** 把 CameraX 的 YUV_420_888 首平面转成 ZXing 亮度源并解码；竖屏时旋转 90° 再试一次。 */
private fun decodeImage(image: ImageProxy, reader: MultiFormatReader): String? {
    val plane = image.planes[0]
    val width = image.width
    val height = image.height
    val buffer = plane.buffer.duplicate()
    val data = ByteArray(width * height)
    val rowStride = plane.rowStride
    if (rowStride == width) {
        buffer.get(data, 0, minOf(buffer.remaining(), data.size))
    } else {
        var offset = 0
        for (row in 0 until height) {
            val start = row * rowStride
            if (start + width > buffer.limit()) break
            buffer.position(start)
            buffer.get(data, offset, width)
            offset += width
        }
    }

    fun tryDecode(bytes: ByteArray, w: Int, h: Int): String? {
        val source = PlanarYUVLuminanceSource(bytes, w, h, 0, 0, w, h, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return runCatching { reader.decodeWithState(bitmap)?.text }.getOrNull()
    }

    reader.reset()
    tryDecode(data, width, height)?.let { return it }

    // 竖屏扫码时画面是横的，旋转 90° 再解析
    val rotated = rotate90(data, width, height)
    reader.reset()
    return tryDecode(rotated, height, width)
}

private fun rotate90(src: ByteArray, w: Int, h: Int): ByteArray {
    val out = ByteArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            out[x * h + (h - 1 - y)] = src[y * w + x]
        }
    }
    return out
}

/** 相机权限门控：在页面层申请权限，授权后再打开扫码弹窗。 */
@Composable
fun rememberCameraGate(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (ok) onGranted() else onDenied()
    }
    return {
        if (granted) onGranted()
        else launcher.launch(android.Manifest.permission.CAMERA)
    }
}
