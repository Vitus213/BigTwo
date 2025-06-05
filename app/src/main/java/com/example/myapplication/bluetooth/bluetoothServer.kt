// com.example.myapplication.bluetooth.BluetoothServer.kt
package com.example.myapplication.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.util.Collections
import java.util.UUID
import java.io.BufferedReader
import java.io.InputStreamReader
// Add ConnectedClient data class
data class ConnectedClient(
    val id: String,
    val name: String,
    val avatar: String
)
class BluetoothServer(private val context: Context) : Closeable {

    companion object {
        private const val TAG = "BluetoothServer"
        private const val SERVER_NAME = "BigTwoServer"
        private val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var serverSocket: BluetoothServerSocket? = null
    private val clientConnections = mutableMapOf<String, ClientConnection>()

    // 使用 SupervisorJob 和 CoroutineExceptionHandler 来处理子协程的异常
    private val serverScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Server Coroutine failed: ${throwable.message}", throwable)
            // 这里不直接调用 close()，因为 close() 会取消整个作用域，可能导致循环关闭。
            // 而是依赖 SupervisorJob() 来隔离子协程的失败。
            // 对于致命错误，可以在这里触发一个全局错误状态或通知UI。
        }
    )

    private var acceptJob: Job? = null

    // --- 蓝牙设备发现相关状态和流 ---
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableSharedFlow<BluetoothDevice>()
    val discoveredDevices: SharedFlow<BluetoothDevice> = _discoveredDevices.asSharedFlow()

    private val foundDeviceReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission") // 整个方法需要权限，在外部已检查
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // <--- 修正：使用 TIRAMISU (API 33)
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION") // <--- 修正：抑制弃用警告
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    device?.let {
                        Log.d(TAG, "发现设备: ${it.name ?: "未知名称"} - ${it.address}")
                        serverScope.launch {
                            _discoveredDevices.emit(it) // 发射发现的设备
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isDiscovering.value = true
                    Log.d(TAG, "蓝牙设备发现已开始。")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                    Log.d(TAG, "蓝牙设备发现已结束。")
                }
            }
        }
    }

    init {
        // 在实例初始化时注册广播接收器
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(foundDeviceReceiver, filter)
        Log.d(TAG, "BroadcastReceiver 已注册。")
    }
    // --- 蓝牙设备发现相关状态和流 结束 ---


    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun startServer(
        onMessageReceived: (String, String) -> Unit,
        onClientConnected: (String, String) -> Unit,
        onClientDisconnected: (String) -> Unit
    ) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "启动服务器失败：BLUETOOTH_CONNECT 权限未授予。")
            withContext(Dispatchers.Main) { // 确保在协程中调用
                Toast.makeText(context, "启动服务器失败：蓝牙连接权限未授予", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Log.e(TAG, "启动服务器失败：蓝牙未启用。")
            withContext(Dispatchers.Main) { // 确保在协程中调用
                Toast.makeText(context, "启动服务器失败：蓝牙未启用", Toast.LENGTH_SHORT).show()
            }
            return
        }

        close() // 先关闭旧资源
        Log.d(TAG, "服务器：旧资源已关闭，准备重新启动监听。")

        val delayMillis = 500L
        Log.d(TAG, "服务器：等待 $delayMillis ms 以确保资源释放。")
        delay(delayMillis)

        try {
            serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(SERVER_NAME, APP_UUID)
            Log.i(TAG, "服务器启动，等待客户端连接...")
            Log.d(TAG, "服务器：新 serverSocket 实例已创建。UUID: $APP_UUID")

            acceptJob = serverScope.launch {
                while (isActive) {
                    Log.d(TAG, "服务器：Accept协程循环，即将调用 serverSocket.accept()...")
                    val socket: BluetoothSocket? = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        Log.e(TAG, "服务器：serverSocket.accept() 失败（IO异常）：${e.message}", e)
                        if (e.message == "bt socket closed") {
                            Log.i(TAG, "服务器 Socket 已被关闭，退出 accept 循环。")
                            break
                        }
                        null
                    } catch (se: SecurityException) {
                        Log.e(TAG, "服务器：SecurityException：未授予 BLUETOOTH_CONNECT 权限，无法 accept()。", se)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "权限不足，服务器无法接受连接", Toast.LENGTH_SHORT).show()
                        }
                        null
                    } catch (ce: Exception) {
                        Log.e(TAG, "服务器：serverSocket.accept() 发生未知异常：${ce.message}", ce)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "服务器接受连接发生未知错误: ${ce.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                        null
                    }

                    if (socket != null) {
                        val clientId = socket.remoteDevice.address
                        
                        // 检查是否已存在该客户端的连接
                        if (clientConnections.containsKey(clientId)) {
                            Log.w(TAG, "客户端 [$clientId] 已存在连接，关闭新连接。")
                            try {
                                socket.close()
                            } catch (e: IOException) {
                                Log.e(TAG, "关闭重复连接时出错: ${e.message}", e)
                            }
                            continue
                        }

                        Log.i(TAG, "客户端 [$clientId] 已连接。当前连接数: ${clientConnections.size + 1}")

                        // 创建新的客户端连接
                        val clientConnection = ClientConnection(
                            socket,
                            onMessageReceived = { message, senderId ->
                                Log.d(TAG, "BluetoothServer: 收到客户端[$senderId]消息: $message，准备分发到外部回调")
                                serverScope.launch(Dispatchers.Main) {
                                    Log.d(TAG, "BluetoothServer: 分发到 startServer 的 onMessageReceived 回调: message=$message, senderId=$senderId")
                                    onMessageReceived(message, senderId)
                                }
                            },
                            onClientDisconnected = { disconnectedClientId ->
                                serverScope.launch(Dispatchers.Main) {
                                    Log.d(TAG, "BluetoothServer: 处理客户端 [$disconnectedClientId] 断开连接事件")
                                    if (clientConnections.remove(disconnectedClientId) != null) {
                                        Log.i(TAG, "客户端 [$disconnectedClientId] 已从连接列表中移除。当前连接数: ${clientConnections.size}")
                                        onClientDisconnected(disconnectedClientId)
                                    } else {
                                        Log.w(TAG, "客户端 [$disconnectedClientId] 已在处理断开连接前从列表中移除，忽略重复处理。")
                                    }
                                }
                            }
                        )

                        // 先添加到连接列表
                        clientConnections[clientId] = clientConnection
                        
                        // 通知连接成功
                        withContext(Dispatchers.Main) {
                            onClientConnected(clientId, socket.remoteDevice.name ?: "Unknown Device")
                        }

                        // 开始监听客户端消息
                        clientConnection.receiveData { message ->
                            Log.d(TAG, "BluetoothServer: receiveData 收到消息: $message, clientId=$clientId")
                        }
                    }
                }
                Log.d(TAG, "服务器：Accept协程循环已结束。")
            }
        } catch (e: IOException) {
            Log.e(TAG, "服务器启动失败（listenUsingInsecureRfcommWithServiceRecord）：${e.message}", e)
            serverScope.launch { close() }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "服务器启动失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleMessage(message: String, clientId: String) {
        Log.d(TAG, "服务器处理来自 [$clientId] 的消息: $message")
        when {
            message.startsWith("PLAYER_JOIN:") -> {
                val clientNickname = message.substringAfter("PLAYER_JOIN:")
                Log.d(TAG, "服务器收到 PLAYER_JOIN: $clientNickname from $clientId")
                // 这里只做消息转发，不维护 UI 列表
                // 你可以在这里通过 sendDataToClient/sendDataToAllClients 通知其他客户端
            }
            message.startsWith("PLAYER_ACTION:") -> {
                Log.d(TAG, "服务器处理玩家操作: $message")
                sendDataToAllClients(message)
            }
            else -> {
                Log.d(TAG, "服务器收到未知消息: $message")
            }
        }
    }

    fun sendDataToClient(clientId: String, data: String) {
        Log.d(TAG, "服务器尝试发送数据给客户端 [$clientId]: $data")
        val client = clientConnections[clientId]
        if (client != null) {
            try {
                client.sendData(data)
                Log.d(TAG, "服务器成功发送数据给客户端 [$clientId]")
            } catch (e: Exception) {
                Log.e(TAG, "服务器发送数据给客户端 [$clientId] 失败: ${e.message}")
            }
        } else {
            Log.e(TAG, "服务器发送数据失败：客户端 [$clientId] 不存在")
        }
    }

    fun sendDataToAllClients(data: String) {
        Log.d(TAG, "服务器广播数据给所有客户端: $data")
        clientConnections.forEach { (clientId, client) ->
            try {
                client.sendData(data)
                Log.d(TAG, "服务器成功发送数据给客户端 [$clientId]")
            } catch (e: Exception) {
                Log.e(TAG, "服务器发送数据给客户端 [$clientId] 失败: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission") // 方法级别权限处理
    suspend fun startDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            Log.e(TAG, "开始发现失败：BLUETOOTH_SCAN 权限未授予。")
            withContext(Dispatchers.Main) { // 确保在协程中调用
                Toast.makeText(context, "开始发现失败：蓝牙扫描权限未授予。", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Log.e(TAG, "开始发现失败：蓝牙未启用。")
            withContext(Dispatchers.Main) { // 确保在协程中调用
                Toast.makeText(context, "开始发现失败：蓝牙未启用。", Toast.LENGTH_SHORT).show()
            }
            return
        }
        // Lint 警告：Call requires permission... 可以通过 @SuppressLint("MissingPermission") 解决
        if (bluetoothAdapter?.isDiscovering == true) { // <--- 这里也会有 Lint 警告，方法级别的SuppressLint已处理
            Log.d(TAG, "已经在发现中，无需重复。")
            return
        }
        Log.d(TAG, "开始蓝牙设备发现...")
        _isDiscovering.value = true // 在实际开始发现前更新状态
        bluetoothAdapter?.startDiscovery() // <--- 这里也会有 Lint 警告，方法级别的SuppressLint已处理
    }

    @SuppressLint("MissingPermission") // 方法级别权限处理
    suspend fun stopDiscovery() {
        // Lint 警告：Call requires permission...
        if (bluetoothAdapter?.isDiscovering == false) { // <--- 这里也会有 Lint 警告，方法级别的SuppressLint已处理
            Log.d(TAG, "未在发现中，无需停止。")
            return
        }
        Log.d(TAG, "停止蓝牙设备发现。")
        bluetoothAdapter?.cancelDiscovery() // <--- 这里也会有 Lint 警告，方法级别的SuppressLint已处理
        _isDiscovering.value = false // 在实际停止后更新状态
    }

    @SuppressLint("MissingPermission") // 方法级别权限处理
    override fun close() { // 这是一个非 suspend 函数
        Log.d(TAG, "External close() called, cancelling server resources...")

        // 先取消所有正在进行的作业
        acceptJob?.cancel()
        acceptJob = null

        // 停止蓝牙发现
        bluetoothAdapter?.cancelDiscovery()
        _isDiscovering.value = false

        // 启动一个独立的协程来关闭所有客户端，因为 ClientConnection.close() 是 suspend 函数
        // 使用一个新的 CoroutineScope，以确保此关闭任务能够在 serverScope 被取消后完成
        // 或者简单地在 serverScope 中 launch，但要确保 serverScope 不被取消得太早
        serverScope.launch { // 在 serverScope 中启动，因为 serverScope 是 SupervisorJob()，子Job失败不会影响其他Job
            val clientsToClose = clientConnections.values.toList() // 创建副本避免并发修改
            clientsToClose.forEach { client ->
                try {
                    client.close() // 调用 suspend 函数
                } catch (e: Exception) {
                    Log.e(TAG, "关闭客户端 ${client.clientId} 时出错: ${e.message}", e)
                }
            }
            clientConnections.clear() // 清空列表
            Log.d(TAG, "所有客户端连接已关闭并从列表中移除。")
        }


        try {
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "蓝牙服务器 Socket 已关闭。")
        } catch (e: IOException) {
            Log.e(TAG, "关闭服务器 Socket 时发生错误：${e.message}", e)
        }

        // 取消注册广播接收器
        try {
            context.unregisterReceiver(foundDeviceReceiver)
            Log.d(TAG, "BroadcastReceiver 已注销。")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "注销 BroadcastReceiver 时出错 (可能未注册): ${e.message}", e)
        }

        // serverScope.cancel() // 这个通常在整个 BluetoothServer 实例不再需要时才调用 (例如 Activity 的 onDestroy)
        Log.i(TAG, "蓝牙服务器已完全关闭。")
    }

    fun getConnectedClientCount(): Int = clientConnections.size
    fun getConnectedClientIds(): Set<String> = clientConnections.keys
    fun getClientNameById(id: String): String? = clientConnections[id]?.clientName

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "获取已配对设备失败：BLUETOOTH_CONNECT 权限未授予。")
            return emptyList()
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Log.e(TAG, "获取已配对设备失败：蓝牙未启用。")
            return emptyList()
        }
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }
}