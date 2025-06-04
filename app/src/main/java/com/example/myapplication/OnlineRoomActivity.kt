package com.example.myapplication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.bluetooth.BluetoothClient
import com.example.myapplication.bluetooth.BluetoothPermissionManager
import com.example.myapplication.bluetooth.BluetoothServer
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "OnlineRoomActivity"

data class ConnectedClient(
    val id: String,
    val name: String,
    val avatar: String
)

class OnlineRoomActivity : ComponentActivity() {

    private lateinit var permissionManager: BluetoothPermissionManager
    private lateinit var bluetoothClient: BluetoothClient
    private lateinit var bluetoothServer: BluetoothServer

    // 使用 StateFlow 管理连接的客户端列表
    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionManager = BluetoothPermissionManager(this, activityResultRegistry, this)
        bluetoothClient = BluetoothClient(this)
        bluetoothServer = BluetoothServer(this)

        setContent {
            MyApplicationTheme {
                OnlineRoomScreen(
                    permissionManager = permissionManager,
                    bluetoothClient = bluetoothClient,
                    bluetoothServer = bluetoothServer,
                    activityContext = this,
                    isHostFlow = isHost,
                    connectedClientsFlow = connectedClients,
                    onClientListUpdated = { newList -> _connectedClients.value = newList },
                    onSetIsHost = { host -> _isHost.value = host }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保在 Activity 销毁时关闭蓝牙连接
        bluetoothClient.close()
        bluetoothServer.close()
    }

    // 处理消息 (这个方法是在 Activity 中定义的，现在不需要了，因为处理逻辑移到了 Composable 中)
    // private fun handleMessage(message: String) { ... }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineRoomScreen(
    permissionManager: BluetoothPermissionManager,
    bluetoothClient: BluetoothClient,
    bluetoothServer: BluetoothServer,
    activityContext: Context,
    isHostFlow: StateFlow<Boolean>,
    connectedClientsFlow: StateFlow<List<ConnectedClient>>,
    onClientListUpdated: (List<ConnectedClient>) -> Unit,
    onSetIsHost: (Boolean) -> Unit
) {
    val playerNickname = remember { mutableStateOf("") }
    val showNicknameDialog = remember { mutableStateOf(true) }

    // Collect StateFlows as Compose States
    val isHost by isHostFlow.collectAsState()
    val connectedClients by connectedClientsFlow.collectAsState()

    var isBluetooth by remember { mutableStateOf(true) } // 目前只支持蓝牙，所以是固定值
    var isConnected by remember { mutableStateOf(false) }

    val showSearchDialog = remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val hasBluetoothScanPermission by permissionManager.hasBluetoothScanPermission
    val hasBluetoothConnectPermission by permissionManager.hasBluetoothConnectPermission

    // 用于控制客户端PLAYER_JOIN消息只发送一次
    var hasJoinedRoom by remember { mutableStateOf(false) }

    // --- 蓝牙服务器逻辑 (主机端) ---
    // Listen to Bluetooth server messages and connection events
    LaunchedEffect(bluetoothServer) {
        bluetoothServer.startServer(
            onMessageReceived = { message, senderId -> // senderId 在这里是可用的
                Log.d("OnlineRoomScreen", "主机收到来自 [$senderId] 的消息: $message")
                when {
                    message.startsWith("PLAYER_JOIN:") -> {
                        val clientNickname = message.substringAfter("PLAYER_JOIN:")
                        val newClient = ConnectedClient(
                            id = senderId,
                            name = clientNickname,
                            avatar = "" // 可以在这里设置默认头像或从消息中解析
                        )
                        val existingClientIndex = connectedClients.indexOfFirst { it.id == senderId }
                        if (existingClientIndex != -1) {
                            // 更新现有客户端的昵称，如果他们重新连接或更改了昵称
                            val updatedClients = connectedClientsFlow.value.toMutableList()
                            updatedClients[existingClientIndex] = newClient
                            onClientListUpdated(updatedClients) // 更新底层的 StateFlow
                            Toast.makeText(activityContext, "客户端 ${clientNickname} (已更新) 已加入房间", Toast.LENGTH_SHORT).show()

                            // *** 将构建 latestClients 并发送消息的逻辑复制到这里 ***
                            val latestClients = updatedClients // 在更新分支中使用 updatedClients
                            Log.d("OnlineRoomScreen", "主机收到PLAYER_JOIN，准备发送ROOM_UPDATE和PLAYER_JOINED (更新现有)") // Update log
                            Log.d("OnlineRoomScreen", "主机构建ROOM_UPDATE前 latestClients 大小: ${latestClients.size}") // Add log here
                            latestClients.forEachIndexed { index, client -> // Add log here
                                Log.d("OnlineRoomScreen", "主机构建ROOM_UPDATE前 latestClients[${index}]: id=${client.id}, name=${client.name}") // Add log here
                            }
                            val currentPlayersJsonArray = JSONArray().apply {
                                latestClients.forEach { client -> // Use the latest list
                                    put(JSONObject().apply {
                                        put("id", client.id)
                                        put("name", client.name)
                                        put("avatar", client.avatar)
                                    })
                                }
                            }
                            val roomUpdateMessage = JSONObject().apply {
                                put("type", "ROOM_UPDATE")
                                put("clients", currentPlayersJsonArray)
                            }.toString()

                            Log.d("OnlineRoomScreen", "主机发送 ROOM_UPDATE 给客户端 [${senderId}]: $roomUpdateMessage") // Update log
                            bluetoothServer.sendDataToClient(senderId, roomUpdateMessage) // Send only to the joining client

                            // 通知其他客户端有新玩家加入 (使用最新列表)
                            latestClients.filter { it.id != senderId && it.id != "LOCAL_HOST_ID" }
                                .forEach { client ->
                                    Log.d("OnlineRoomScreen", "主机通知其他客户端 PLAYER_JOINED 给 [${client.id}]: $senderId,$clientNickname") // Update log
                                    bluetoothServer.sendDataToClient(client.id, "PLAYER_JOINED:$senderId,$clientNickname")
                                }
                            // *** 结束复制的部分 ***

                        } else {
                            // 添加新客户端
                            val latestClients = connectedClientsFlow.value + newClient // 在新增分支中使用当前 StateFlow 值 + 新客户端
                            onClientListUpdated(latestClients) // 更新底层的 StateFlow
                            Toast.makeText(activityContext, "客户端 ${clientNickname} 已加入房间", Toast.LENGTH_SHORT).show()

                            // *** 将构建 latestClients 并发送消息的逻辑复制到这里 ***
                            // 注意：这里的 latestClients 变量已经定义了，直接使用即可
                            Log.d("OnlineRoomScreen", "主机收到PLAYER_JOIN，准备发送ROOM_UPDATE和PLAYER_JOINED (新加入)") // Update log
                            Log.d("OnlineRoomScreen", "主机构建ROOM_UPDATE前 latestClients 大小: ${latestClients.size}") // Add log here
                            latestClients.forEachIndexed { index, client -> // Add log here
                                Log.d("OnlineRoomScreen", "主机构建ROOM_UPDATE前 latestClients[${index}]: id=${client.id}, name=${client.name}") // Add log here
                            }
                            val currentPlayersJsonArray = JSONArray().apply {
                                latestClients.forEach { client -> // Use the latest list
                                    put(JSONObject().apply {
                                        put("id", client.id)
                                        put("name", client.name)
                                        put("avatar", client.avatar)
                                    })
                                }
                            }
                            val roomUpdateMessage = JSONObject().apply {
                                put("type", "ROOM_UPDATE")
                                put("clients", currentPlayersJsonArray)
                            }.toString()

                            Log.d("OnlineRoomScreen", "主机发送 ROOM_UPDATE 给客户端 [${senderId}]: $roomUpdateMessage") // Update log
                            bluetoothServer.sendDataToClient(senderId, roomUpdateMessage) // Send only to the joining client

                            // 通知其他客户端有新玩家加入 (使用最新列表)
                            latestClients.filter { it.id != senderId && it.id != "LOCAL_HOST_ID" }
                                .forEach { client ->
                                    Log.d("OnlineRoomScreen", "主机通知其他客户端 PLAYER_JOINED 给 [${client.id}]: $senderId,$clientNickname") // Update log
                                    bluetoothServer.sendDataToClient(client.id, "PLAYER_JOINED:$senderId,$clientNickname")
                                }
                        }
                    }
                    message.startsWith("PLAYER_ACTION:") -> {
                        Toast.makeText(activityContext, "主机处理玩家操作: $message", Toast.LENGTH_SHORT).show()
                        bluetoothServer.sendDataToAllClients(message)
                    }
                    else -> {
                        Toast.makeText(activityContext, "主机收到未知消息: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onClientConnected = { clientId, clientName ->
                Log.d("OnlineRoomScreen", "新客户端连接 (未加入房间): $clientId, $clientName")
                // 确保 Toast 在主线程显示
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(activityContext, "新客户端 ${clientName} 已连接，等待加入信息...", Toast.LENGTH_SHORT).show()
                }
            },
            onClientDisconnected = { clientId ->
                Log.d("OnlineRoomScreen", "客户端断开连接: $clientId")
                val disconnectedClient = connectedClients.find { it.id == clientId }
                onClientListUpdated(connectedClients.filter { it.id != clientId })
                // 确保 Toast 在主线程显示
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(activityContext, "${disconnectedClient?.name ?: clientId} 已断开连接", Toast.LENGTH_SHORT).show()
                }
                bluetoothServer.sendDataToAllClients("PLAYER_LEFT:$clientId")
            }
        )
    }

    // --- 蓝牙客户端逻辑 ---
    LaunchedEffect(bluetoothClient) {
        bluetoothClient.messageReceived.collectLatest { message ->
            Log.d("OnlineRoomScreen", "客户端 collectLatest 收到原始消息: $message")
            Log.d("OnlineRoomScreen", "客户端收到消息: $message")
            when {
                message.startsWith("ROOM_UPDATE:") -> {
                    // 客户端收到 ROOM_UPDATE 消息，更新连接列表
                    val jsonString = message.substringAfter("ROOM_UPDATE:")
                    Log.d("OnlineRoomScreen", "客户端收到 ROOM_UPDATE 原始 JSON 字符串: $jsonString")
                    try {
                        val jsonObject = JSONObject(jsonString)
                        val clientsArray = jsonObject.getJSONArray("clients")
                        Log.d("OnlineRoomScreen", "客户端 ROOM_UPDATE 解析到 clients 数组长度: ${clientsArray.length()}")
                        val newClients = mutableListOf<ConnectedClient>()
                        for (i in 0 until clientsArray.length()) {
                            val clientObj = clientsArray.getJSONObject(i)
                            newClients.add(
                                ConnectedClient(
                                    id = clientObj.getString("id"),
                                    name = clientObj.getString("name"),
                                    avatar = clientObj.getString("avatar")
                                )
                            )
                        }
                        Log.d("OnlineRoomScreen", "客户端 ROOM_UPDATE 解析到新客户端列表: ${newClients.joinToString()}")
                        // 强制更新列表
                        onClientListUpdated(newClients)

                        Log.d("OnlineRoomScreen", "客户端更新后的连接列表（addAll后）: ${connectedClients.joinToString()}")
                        Log.d("OnlineRoomScreen", "客户端更新后列表大小: ${connectedClients.size}")
                        connectedClients.forEachIndexed { index, client ->
                            Log.d("OnlineRoomScreen", "客户端更新后列表[${index}]: id=${client.id}, name=${client.name}")
                        }
                        Toast.makeText(activityContext, "房间状态已更新", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("OnlineRoomScreen", "解析 ROOM_UPDATE 消息失败: ${e.message}", e)
                    }
                }
                message.startsWith("PLAYER_LEFT:") -> {
                    val clientId = message.substringAfter("PLAYER_LEFT:")
                    val disconnectedClient = connectedClients.find { it.id == clientId }
                    onClientListUpdated(connectedClients.filter { it.id != clientId })
                    Toast.makeText(activityContext, "${disconnectedClient?.name ?: clientId} 已离开房间", Toast.LENGTH_SHORT).show()
                }
                message.startsWith("START_GAME:") -> {
                    Toast.makeText(activityContext, "主机已开始游戏！", Toast.LENGTH_LONG).show()
                    // TODO: Navigate to game screen
                }
                else -> {
                    Toast.makeText(activityContext, "收到未知消息: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 监听连接状态变化
    LaunchedEffect(Unit) {
        bluetoothClient.connectionState.collectLatest { connected ->
            isConnected = connected
            if (!connected && !isHost) { // 如果是客户端且断开连接，清空列表
                onClientListUpdated(emptyList())
            }
        }
    }

    // 在连接成功后发送玩家加入消息
    LaunchedEffect(isConnected) {
        Log.d("OnlineRoomScreen", "LaunchedEffect(isConnected) 触发, isConnected: $isConnected") // Add log here
        if (isConnected && !isHost && !hasJoinedRoom) {
            Log.d("OnlineRoomScreen", "条件 isConnected && !isHost && !hasJoinedRoom 成立") // Add log here
            Log.d("OnlineRoomScreen", "客户端连接成功，发送 PLAYER_JOIN 消息")
            bluetoothClient.sendData("PLAYER_JOIN:${playerNickname.value}")
            hasJoinedRoom = true
        }
    }

    // --- 昵称输入对话框 ---
    if (showNicknameDialog.value) {
        AlertDialog(
            onDismissRequest = { /* Cannot be dismissed, nickname is mandatory */ },
            title = { Text("输入游戏昵称") },
            text = {
                TextField(
                    value = playerNickname.value,
                    onValueChange = { playerNickname.value = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playerNickname.value.isNotBlank()) {
                            showNicknameDialog.value = false
                        } else {
                            Toast.makeText(activityContext, "昵称不能为空", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = playerNickname.value.isNotBlank()
                ) {
                    Text("确认")
                }
            }
        )
    }

    // --- 屏幕 UI 布局 ---
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "联机房间",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 24.dp)
                )

                // 房间状态显示
                Text(
                    text = when {
                        isHost -> "状态: 已开启房间 (${connectedClients.size}/4人)"
                        isConnected -> "状态: 已连接到房间 (${connectedClients.size}/4人)"
                        else -> "状态: 未连接"
                    },
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 玩家头像显示区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Add logging here to check the list state at drawing time
                    Log.d("OnlineRoomScreen_UI", "UI绘制时 - connectedClients大小: ${connectedClients.size}")
                    connectedClients.forEachIndexed { index, client ->
                        Log.d("OnlineRoomScreen_UI", "UI绘制时 - connectedClients[${index}]: id=${client.id}, name=${client.name}")
                    }

                    // 获取主机和当前客户端的信息
                    val hostPlayer = connectedClients.firstOrNull { it.id == "LOCAL_HOST_ID" }
                    val selfPlayer = connectedClients.firstOrNull { it.name == playerNickname.value }

                    // 收集其他玩家 (排除主机和自身)
                    val otherPlayers = connectedClients.filter { it.id != "LOCAL_HOST_ID" && it.name != playerNickname.value }

                    // 按照固定顺序绘制：自身(索引0)，主机(索引1)，其他玩家(索引2 onwards)
                    var currentAvatarIndex = 0

                    // 绘制客户端自身 (如果存在)
                    AvatarBox(
                        index = currentAvatarIndex,
                        nickname = selfPlayer?.name ?: playerNickname.value.ifBlank { "你" }
                    )
                    currentAvatarIndex++

                    // 绘制主机 (如果存在且不是客户端自身)
                    if (hostPlayer != null && hostPlayer.id != (selfPlayer?.id ?: "")) {
                        AvatarBox(
                            index = currentAvatarIndex,
                            nickname = hostPlayer.name
                        )
                        currentAvatarIndex++
                    }

                    // 绘制其他玩家
                    otherPlayers.forEach { client ->
                        AvatarBox(
                            index = currentAvatarIndex,
                            nickname = client.name
                        )
                        currentAvatarIndex++
                    }

                    // 填充剩余的空位，最多4人
                    while (currentAvatarIndex < 4) {
                        AvatarBox(index = currentAvatarIndex)
                        currentAvatarIndex++
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // --- 连接选择器 (开启/搜索房间按钮) ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                ConnectionSelector(
                    isBluetooth = isBluetooth,
                    onCheckedChange = { /* 目前禁用，因为只支持蓝牙 */ },
                    onHostRoom = {
                        @SuppressLint("MissingPermission")
                        if (!hasBluetoothConnectPermission) {
                            Toast.makeText(activityContext, "需要蓝牙连接权限才能开启房间，请在应用设置中授予。", Toast.LENGTH_LONG).show()
                            permissionManager.manageBluetoothPermissions()
                            return@ConnectionSelector
                        }
                        if (isHost) {
                            Toast.makeText(activityContext, "房间已开启", Toast.LENGTH_SHORT).show()
                            return@ConnectionSelector
                        }
                        if (isConnected) {
                            Toast.makeText(activityContext, "你已连接到其他房间，请先断开", Toast.LENGTH_SHORT).show()
                            return@ConnectionSelector
                        }
                        bluetoothClient.close() // 确保客户端连接已关闭
                        coroutineScope.launch {
                            bluetoothServer.startServer(
                                onMessageReceived = { message, senderId -> // senderId 在这里是可用的
                                    Log.d("OnlineRoomScreen", "主机收到来自 [$senderId] 的消息: $message")
                                    when {
                                        message.startsWith("PLAYER_JOIN:") -> {
                                            val clientNickname = message.substringAfter("PLAYER_JOIN:")
                                            val newClient = ConnectedClient(
                                                id = senderId,
                                                name = clientNickname,
                                                avatar = ""
                                            )
                                            val existingClientIndex = connectedClients.indexOfFirst { it.id == senderId }
                                            if (existingClientIndex != -1) {
                                                val updatedClients = connectedClients.toMutableList()
                                                updatedClients[existingClientIndex] = newClient
                                                onClientListUpdated(updatedClients)
                                                Toast.makeText(activityContext, "客户端 ${clientNickname} (已更新) 已加入房间", Toast.LENGTH_SHORT).show()
                                            } else {
                                                onClientListUpdated(connectedClients + newClient)
                                                Toast.makeText(activityContext, "客户端 ${clientNickname} 已加入房间", Toast.LENGTH_SHORT).show()
                                            }

                                            val currentPlayersJsonArray = JSONArray().apply {
                                                connectedClients.forEach { client ->
                                                    put(JSONObject().apply {
                                                        put("id", client.id)
                                                        put("name", client.name)
                                                        put("avatar", client.avatar)
                                                    })
                                                }
                                            }
                                            val roomUpdateMessage = JSONObject().apply {
                                                put("type", "ROOM_UPDATE")
                                                put("clients", currentPlayersJsonArray)
                                            }.toString()

                                            Log.d("OnlineRoomScreen", "主机发送 ROOM_UPDATE 给客户端 [${senderId}]: $roomUpdateMessage") // Update log
                                            bluetoothServer.sendDataToClient(senderId, roomUpdateMessage) // Send only to the joining client

                                            // Notify other clients about the new player joining
                                            connectedClients.filter { it.id != senderId && it.id != "LOCAL_HOST_ID" }
                                                .forEach { client ->
                                                    Log.d("OnlineRoomScreen", "主机通知其他客户端 PLAYER_JOINED 给 [${client.id}]: $senderId,$clientNickname") // Update log
                                                    bluetoothServer.sendDataToClient(client.id, "PLAYER_JOINED:$senderId,$clientNickname")
                                                }
                                        }
                                        message.startsWith("PLAYER_ACTION:") -> {
                                            Toast.makeText(activityContext, "主机处理玩家操作: $message", Toast.LENGTH_SHORT).show()
                                            bluetoothServer.sendDataToAllClients(message)
                                        }
                                        else -> {
                                            Toast.makeText(activityContext, "主机收到未知消息: $message", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onClientConnected = { clientId, clientName ->
                                    Log.d("OnlineRoomScreen", "新客户端连接 (未加入房间): $clientId, $clientName")
                                    // 确保 Toast 在主线程显示
                                    coroutineScope.launch(Dispatchers.Main) {
                                        Toast.makeText(activityContext, "新客户端 ${clientName} 已连接，等待加入信息...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onClientDisconnected = { clientId ->
                                    Log.d("OnlineRoomScreen", "客户端断开连接: $clientId")
                                    val disconnectedClient = connectedClients.find { it.id == clientId }
                                    onClientListUpdated(connectedClients.filter { it.id != clientId })
                                    // 确保 Toast 在主线程显示
                                    coroutineScope.launch(Dispatchers.Main) {
                                        Toast.makeText(activityContext, "${disconnectedClient?.name ?: clientId} 已断开连接", Toast.LENGTH_SHORT).show()
                                    }
                                    bluetoothServer.sendDataToAllClients("PLAYER_LEFT:$clientId")
                                }
                            )
                            onSetIsHost(true)
                            isConnected = true
                            hasJoinedRoom = true // 主机也视为已加入房间
                            Toast.makeText(activityContext, "房间已开启！", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSearchRoom = {
                        @SuppressLint("MissingPermission")
                        if (!hasBluetoothScanPermission) {
                            Toast.makeText(activityContext, "需要蓝牙扫描权限才能搜索房间，请在应用设置中授予。", Toast.LENGTH_LONG).show()
                            permissionManager.manageBluetoothPermissions()
                            return@ConnectionSelector
                        }
                        if (isHost) {
                            Toast.makeText(activityContext, "你已开启房间，无法搜索", Toast.LENGTH_SHORT).show()
                            return@ConnectionSelector
                        }
                        if (isConnected) {
                            Toast.makeText(activityContext, "你已连接到其他房间，请先断开", Toast.LENGTH_SHORT).show()
                            return@ConnectionSelector
                        }

                        bluetoothServer.close() // 确保服务器已关闭
                        showSearchDialog.value = true
                    }
                )
            }

            // --- 开始游戏按钮 ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                StartGameButton(
                    onStartGame = {
                        if (isHost) {
                            if (connectedClients.size > 1) { // 至少需要两名玩家（主机+1个客户端）
                                Toast.makeText(activityContext, "主机开始游戏！", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    bluetoothServer.sendDataToAllClients("START_GAME:")
                                    // TODO: Navigate to game screen
                                }
                            } else {
                                Toast.makeText(activityContext, "至少需要一名玩家才能开始游戏", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(activityContext, "只有主机才能开始游戏", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // --- 搜索房间对话框 ---
    @SuppressLint("MissingPermission")
    if (showSearchDialog.value) {
        val pairedDevices = remember { mutableStateListOf<BluetoothDevice>() }

        // 获取已配对设备列表
        LaunchedEffect(Unit) {
            val devices = bluetoothServer.getPairedDevices()
            pairedDevices.clear()
            pairedDevices.addAll(devices)
        }

        AlertDialog(
            onDismissRequest = {
                showSearchDialog.value = false
            },
            title = { Text("选择房间 (已配对设备)") },
            text = {
                Column {
                    Text("请选择要连接的房间：")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (pairedDevices.isEmpty()) {
                        Text("没有已配对的设备。请先在系统设置中配对设备。", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        LazyColumn {
                            items(pairedDevices) { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSearchDialog.value = false // 关闭对话框
                                            bluetoothServer.close() // 确保服务器已关闭

                                            bluetoothClient.connectToServer(
                                                device,
                                                onConnected = {
                                                    isConnected = true
                                                    onSetIsHost(false)
                                                    hasJoinedRoom = false // 重置，确保下次连接能发送PLAYER_JOIN
                                                    Toast.makeText(activityContext, "已连接到 ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                                                },
                                                onDisconnected = {
                                                    isConnected = false
                                                    onClientListUpdated(emptyList()) // 断开连接后清空列表
                                                    Toast.makeText(activityContext, "已断开连接", Toast.LENGTH_SHORT).show()
                                                    hasJoinedRoom = false // 断开连接后重置
                                                },
                                                onFailed = { errorMessage ->
                                                    isConnected = false
                                                    onClientListUpdated(emptyList()) // 连接失败也清空列表
                                                    Toast.makeText(activityContext, "连接失败: $errorMessage", Toast.LENGTH_LONG).show()
                                                    hasJoinedRoom = false // 连接失败后重置
                                                }
                                            )
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.LightGray)
                                            .border(1.dp, Color.DarkGray, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "B", color = Color.DarkGray) // 可以替换为实际的头像图标
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = device.name ?: "未知设备",
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = device.address,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSearchDialog.value = false
                }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun AvatarBox(index: Int, nickname: String = "等待玩家") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (index == 0) Color(0xFFADD8E6) else Color.LightGray)
                .border(2.dp, Color.DarkGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (nickname == "等待玩家" || nickname.isBlank()) {
                Text(text = "?", color = Color.DarkGray, fontSize = 36.sp)
            } else {
                // 如果是自己的昵称，显示"你"
                if (nickname == "你") {
                    Text(text = "你", color = Color.DarkGray, fontSize = 36.sp)
                } else {
                    Text(text = nickname.first().uppercase(), color = Color.DarkGray, fontSize = 36.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = nickname, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ConnectionSelector(
    isBluetooth: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onHostRoom: () -> Unit,
    onSearchRoom: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "蓝牙", color = if (isBluetooth) Color.Blue else Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isBluetooth,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.Blue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    ),
                    enabled = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "WiFi", color = if (!isBluetooth) Color.Red else Color.Gray)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Button(
                onClick = onHostRoom,
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    contentColor = Color.White
                )
            ) {
                Text(text = "开启房间", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSearchRoom,
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800),
                    contentColor = Color.White
                )
            ) {
                Text(text = "搜索房间", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun StartGameButton(onStartGame: () -> Unit) {
    Button(
        onClick = onStartGame,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Text(text = "开始游戏", fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}