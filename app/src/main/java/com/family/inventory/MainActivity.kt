package com.family.inventory

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.inventory.data.ApiClient
import com.family.inventory.data.SessionStore
import com.family.inventory.ui.DashboardScreen
import com.family.inventory.ui.EditScreen
import com.family.inventory.ui.FamilyInventoryTheme
import com.family.inventory.ui.ImportScreen
import com.family.inventory.ui.ItemsScreen
import com.family.inventory.ui.LoadingOverlay
import com.family.inventory.ui.LocationScreen
import com.family.inventory.ui.LoginScreen
import com.family.inventory.ui.MainViewModel
import com.family.inventory.ui.QrScreen
import com.family.inventory.ui.Screen
import com.family.inventory.ui.TagsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val session = SessionStore(this)
        val api = ApiClient(session)

        setContent {
            FamilyInventoryTheme {
                val vm: MainViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            MainViewModel(session, api) as T
                    }
                )
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.messages.events.collect { text ->
            snackbarHostState.showSnackbar(text)
        }
    }

    // 上次若发生过未捕获异常，启动时直接把堆栈摆出来，方便真机排查
    val context = LocalContext.current
    var crashText by remember { mutableStateOf(CrashReporter.consume(context)) }

    // 返回键：批量模式下退出批量，子页面回到物品列表，登录页不拦截
    BackHandler(
        enabled = vm.screen !is Screen.Login &&
            (vm.screen !is Screen.Items || vm.selectMode)
    ) {
        if (vm.screen is Screen.Items) vm.toggleSelectMode() else vm.goToItems()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (val screen = vm.screen) {
                        is Screen.Login -> LoginScreen(vm)
                        is Screen.Items -> ItemsScreen(vm)
                        is Screen.Locations -> LocationScreen(vm)
                        is Screen.Import -> ImportScreen(vm)
                        is Screen.Dashboard -> DashboardScreen(vm)
                        is Screen.Tags -> TagsScreen(vm)
                        is Screen.Qr -> QrScreen(vm, screen)
                        is Screen.Edit -> EditScreen(vm, screen.editingId)
                    }
                }
            }
            LoadingOverlay(vm.loading)
        }
    }

    crashText?.let { text ->
        AlertDialog(
            onDismissRequest = { crashText = null },
            title = { Text("上次运行发生了崩溃") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("crash", text))
                    crashText = null
                }) { Text("复制并关闭") }
            },
            dismissButton = {
                TextButton(onClick = { crashText = null }) { Text("关闭") }
            }
        )
    }
}
