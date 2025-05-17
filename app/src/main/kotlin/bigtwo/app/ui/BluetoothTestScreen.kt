package bigtwo.app.ui

import android.bluetooth.BluetoothDevice
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bigtwo.app.network.BluetoothClient
import bigtwo.app.network.BluetoothServer
import bigtwo.app.network.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BluetoothTestScreen(
    hasBluetoothScanPermission: Boolean,
    hasBluetoothConnectPermission: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val server = remember { BluetoothServer(context) }
    val client = remember { BluetoothClient(context) }

    val clientConnectionState by client.connectionState.collectAsState()
    val isClientConnected = clientConnectionState == ConnectionState.Connected

    var isServerStarted by remember { mutableStateOf(false) }
    var isClientConnectedToServer by remember { mutableStateOf(false) } // 表示是否有客户端连接到服务端

    val receivedMessages = remember { mutableStateListOf<String>() }

    var messageToSendByClient by remember { mutableStateOf("") }
    var messageToSendByServer by remember { mutableStateOf("") }

    // --- 监听来自 BluetoothClient 的消息 ---
    LaunchedEffect(client) {
        client.receivedMessages.collect { received ->
            receivedMessages.add("客户端收到: $received")
        }
    }

    // --- 资源清理：当 Composable 离开组合树时，关闭蓝牙连接 ---
    DisposableEffect(server, client) {
        onDispose {
            Log.d("BluetoothTestScreen", "Closing Bluetooth resources...")
            server.close() // 调用 BluetoothServer 的 close 方法
            client.close() // 调用 BluetoothClient 的 close 方法
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // --- 启动服务端按钮 ---
        Button(
            onClick = {
                if (hasBluetoothConnectPermission) {
                    if (!isServerStarted) {
                        isServerStarted = true
                        coroutineScope.launch(Dispatchers.IO) {
                            server.startServer(
                                onMessageReceived = { received ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        receivedMessages.add("服务端收到: $received")
                                    }
                                },
                                onClientConnected = {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        isClientConnectedToServer = true
                                        Toast.makeText(context, "客户端已连接到服务端", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                // 暂时移除 onClientDisconnected，因为它在 BluetoothServer 中未定义
                                // 如果需要此功能，请先修改 BluetoothServer 类
                            )
                        }
                        Toast.makeText(context, "服务端已启动", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "服务端已启动", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "蓝牙连接权限未授予", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasBluetoothConnectPermission && !isServerStarted
        ) {
            Text(text = if (isServerStarted) "服务端已启动" else "启动服务端")
        }

        // --- 接收消息显示区域 ---
        Text(text = "接收的消息：")
        LazyColumn(modifier = Modifier.height(150.dp)) {
            items(receivedMessages) { msg ->
                Text(text = msg)
            }
        }

        // --- 客户端连接按钮 (作为客户端连接到服务端) ---
        Button(
            onClick = {
                if (hasBluetoothConnectPermission) {
                    coroutineScope.launch {
                        val devices: List<BluetoothDevice> = client.getPairedDevices()
                        if (devices.isNotEmpty()) {
                            val connected = client.connectToServer(devices[0])
                            if (connected) {
                                Toast.makeText(context, "客户端已连接", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "连接失败", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "未找到已配对设备", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "蓝牙连接权限未授予", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasBluetoothConnectPermission && !isServerStarted && !isClientConnected
        ) {
            Text(text = if (isClientConnected) "客户端已连接" else "连接到服务端 (作为客户端)")
        }

        // --- 服务端发送消息区域 (仅在服务端启动且有客户端连接后显示) ---
        if (isServerStarted && isClientConnectedToServer && hasBluetoothConnectPermission) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "服务端发送消息：")
                OutlinedTextField(
                    value = messageToSendByServer,
                    onValueChange = { messageToSendByServer = it },
                    label = { Text("输入消息") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (messageToSendByServer.isNotBlank()) {
                            server.sendData(messageToSendByServer)
                            Log.d("BluetoothTestScreen", "服务端发送数据：$messageToSendByServer")
                            messageToSendByServer = ""
                        } else {
                            Toast.makeText(context, "消息不能为空", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "服务端发送")
                }
            }
        }

        // --- 客户端发送消息输入框 ---
        OutlinedTextField(
            value = messageToSendByClient,
            onValueChange = { messageToSendByClient = it },
            label = { Text("客户端发送消息") },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasBluetoothConnectPermission && !isServerStarted && isClientConnected
        )

        // --- 客户端发送消息按钮 ---
        Button(
            onClick = {
                if (messageToSendByClient.isNotBlank()) {
                    client.sendData(messageToSendByClient)
                    Log.d("BluetoothTestScreen", "客户端发送数据：$messageToSendByClient")
                    messageToSendByClient = ""
                } else {
                    val message = when {
                        !hasBluetoothConnectPermission -> "蓝牙连接权限未授予"
                        isServerStarted -> "当前为服务端模式，无法发送客户端消息"
                        !isClientConnected -> "客户端未连接，无法发送消息"
                        messageToSendByClient.isBlank() -> "消息不能为空"
                        else -> ""
                    }
                    if (message.isNotEmpty()) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasBluetoothConnectPermission && !isServerStarted && isClientConnected
        ) {
            Text(text = "客户端发送")
        }
    }
}