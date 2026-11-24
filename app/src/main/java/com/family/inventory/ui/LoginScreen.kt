package com.family.inventory.ui

import com.family.inventory.BuildConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(vm: MainViewModel) {
    var server by remember { mutableStateOf(vm.serverInput) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val needRegister = vm.registerMode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("📦", fontSize = 44.sp)
        Spacer(Modifier.height(6.dp))
        Text("家庭物资管理", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                vm.authBusy -> "正在连接服务器…"
                needRegister -> "首次使用，请先创建一个账号"
                else -> "请登录后使用"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(22.dp))

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FieldLabel("服务器地址")
                OutlinedTextField(
                    value = server,
                    onValueChange = {
                        server = it
                        vm.onServerInputChange(it)
                    },
                    singleLine = true,
                    placeholder = { Text("http://192.168.1.10:8080") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                VSpace(10)
                Text(
                    "填部署了 family-inventory 的主机地址；手机与服务器需在同一局域网。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VSpace(14)

                FieldLabel("用户名")
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                VSpace(12)

                FieldLabel("密码")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (needRegister) {
                    VSpace(12)
                    FieldLabel("确认密码")
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                vm.authError?.let { err ->
                    VSpace(12)
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                VSpace(18)
                Button(
                    onClick = {
                        if (needRegister) vm.register(username, password, confirm)
                        else vm.login(username, password)
                    },
                    enabled = !vm.authBusy &&
                        server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (vm.authBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (needRegister) "创建账号并登录" else "登录")
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { vm.applyRegisterMode(!needRegister) }) {
                        Text(if (needRegister) "已有账号，去登录" else "首次使用？创建账号")
                    }
                    TextButton(onClick = {
                        vm.applyServer(server)
                        vm.refreshAuthStatus()
                    }) {
                        Text("检测服务器")
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            vm.authStatus?.let {
                if (it.registered) "服务器已就绪，已有账号" else "服务器已就绪，尚未创建账号"
            } ?: "尚未连接服务器",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "客户端 v${BuildConfig.VERSION_NAME}" +
                (vm.serverVersion?.let { " · 服务器 v$it" } ?: " · 服务器版本未获取"),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
