// bigtwo.app.network.BluetoothServer.kt
package bigtwo.app.network

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
    private val connectedClients: MutableMap<String, ClientConnection> =
        Collections.synchronizedMap(mutableMapOf())

    private val serverScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Server Coroutine failed: ${throwable.message}", throwable)
            // 这里调用普通的 close() 方法，它内部会启动协程来处理 suspend 调用
            close()
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

    @SuppressLint("MissingPermission") // 方法级别权限处理
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

        close() // 先关闭旧资源 (这是一个普通函数，但内部会启动协程处理 suspend 关闭)
        Log.d(TAG, "服务器：旧资源已关闭，准备重新启动监听。")

        val delayMillis = 500L
        Log.d(TAG, "服务器：等待 $delayMillis ms 以确保资源释放。")
        delay(delayMillis) // 确保资源被释放，避免端口冲突

        try {
            serverSocket =
                bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVER_NAME, APP_UUID)
            Log.i(TAG, "服务器启动，等待客户端连接...")
            Log.d(TAG, "服务器：新 serverSocket 实例已创建。UUID: $APP_UUID")

            acceptJob = serverScope.launch {
                while (isActive) { // 确保协程活跃
                    Log.d(TAG, "服务器：Accept协程循环，即将调用 serverSocket.accept()...")
                    val socket: BluetoothSocket? = try {
                        // accept() 阻塞调用
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        Log.e(TAG, "服务器：serverSocket.accept() 失败（IO异常）：${e.message}", e)
                        if (e.message == "bt socket closed") {
                            Log.i(TAG, "服务器 Socket 已被关闭，退出 accept 循环。")
                            break // Socket 关闭时退出循环
                        }
                        null
                    } catch (se: SecurityException) {
                        Log.e(
                            TAG,
                            "服务器：SecurityException：未授予 BLUETOOTH_CONNECT 权限，无法 accept()。",
                            se
                        )
                        withContext(Dispatchers.Main) { // 确保在协程中调用
                            Toast.makeText(
                                context,
                                "权限不足，服务器无法接受连接",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        null
                    } catch (ce: Exception) {
                        Log.e(TAG, "服务器：serverSocket.accept() 发生未知异常：${ce.message}", ce)
                        withContext(Dispatchers.Main) { // 确保在协程中调用
                            Toast.makeText(
                                context,
                                "服务器接受连接发生未知错误: ${ce.localizedMessage}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        null
                    }

                    if (socket != null) {
                        val clientConnection = ClientConnection(
                            socket,
                            onMessageReceived = { message, senderId ->
                                // >>>>>>> 在这里添加日志打印 <<<<<<<
                                Log.i(TAG, "服务器收到来自 [$senderId] 的消息：$message")
                                // 然后再调用外部传入的 onMessageReceived 回调
                                onMessageReceived(message, senderId)
                            },
                            onClientDisconnected = { disconnectedClientId ->
                                connectedClients.remove(disconnectedClientId)
                                onClientDisconnected(disconnectedClientId)
                                Log.i(
                                    TAG,
                                    "客户端 [$disconnectedClientId] 已从连接列表中移除。当前连接数: ${connectedClients.size}"
                                )
                            }
                        )
                        connectedClients[clientConnection.clientId] = clientConnection
                        withContext(Dispatchers.Main) {
                            onClientConnected(
                                clientConnection.clientId,
                                clientConnection.clientName
                            )
                        }
                        Log.i(
                            TAG,
                            "客户端 [${clientConnection.clientId}] 已连接。当前连接数: ${connectedClients.size}"
                        )
                    } else {
                        Log.d(TAG, "服务器：accept() 返回 null。")
                    }
                }
                Log.d(TAG, "服务器：Accept协程循环已结束。")
            }
        } catch (e: IOException) {
            Log.e(TAG, "服务器启动失败（listenUsingRfcommWithServiceRecord）：${e.message}", e)
            serverScope.launch { close() } // 启动一个协程来清理
            withContext(Dispatchers.Main) { // 确保在协程中调用
                Toast.makeText(context, "服务器启动失败: ${e.localizedMessage}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    // <--- 修正：在非 suspend 函数中显示 Toast，需要启动一个新的协程 --->
    fun sendDataToAllClients(data: String) {
        if (connectedClients.isEmpty()) {
            Log.w(TAG, "没有客户端连接，无法发送数据。")
            serverScope.launch(Dispatchers.Main) { // <--- 修正
                Toast.makeText(context, "没有客户端连接，无法发送数据。", Toast.LENGTH_SHORT).show()
            }
            return
        }
        connectedClients.values.forEach { client ->
            client.sendData(data)
        }
    }

    // <--- 修正：在非 suspend 函数中显示 Toast，需要启动一个新的协程 --->
    fun sendDataToClient(clientId: String, data: String) {
        connectedClients[clientId]?.sendData(data) ?: run {
            Log.w(TAG, "未找到客户端 [$clientId] 或已断开连接，无法发送数据。")
            serverScope.launch(Dispatchers.Main) { // <--- 修正
                Toast.makeText(
                    context,
                    "未找到客户端或已断开连接，无法发送数据。",
                    Toast.LENGTH_SHORT
                ).show()
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
    override fun close() {
        Log.d(TAG, "Closing BluetoothServer resources...")
        // 先取消所有正在进行的作业
        acceptJob?.cancel()
        acceptJob = null
        bluetoothAdapter?.cancelDiscovery() // <--- 这里也会有 Lint 警告，方法级别的SuppressLint已处理
        _isDiscovering.value = false

        // 启动一个协程来关闭所有客户端，因为 ClientConnection.close() 是 suspend 函数
        serverScope.launch {
            val clientsToClose = connectedClients.values.toList() // 创建副本避免并发修改
            clientsToClose.forEach { client ->
                try {
                    client.close() // 调用 suspend 函数
                } catch (e: Exception) {
                    Log.e(TAG, "关闭客户端 ${client.clientId} 时出错: ${e.message}", e)
                }
            }
            connectedClients.clear() // 清空列表
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

        // serverScope.cancel() // 这个通常在整个 BluetoothServer 实例不再需要时才调用
        Log.i(TAG, "蓝牙服务器已完全关闭。")
    }

    fun getConnectedClientCount(): Int = connectedClients.size
    fun getConnectedClientIds(): Set<String> = connectedClients.keys
    fun getClientNameById(id: String): String? = connectedClients[id]?.clientName
}