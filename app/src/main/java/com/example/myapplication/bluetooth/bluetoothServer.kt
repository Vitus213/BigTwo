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
import com.example.myapplication.domain.Card
import com.example.myapplication.domain.Rank
import com.example.myapplication.domain.Suit
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
import org.json.JSONArray
import org.json.JSONObject

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

    private fun checkBluetoothPermissions(): Boolean {
        val hasConnectPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        if (!hasConnectPermission) {
            Log.e(TAG, "缺少 BLUETOOTH_CONNECT 权限")
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    suspend fun startServer(
        onMessageReceived: (String, String) -> Unit,
        onClientConnected: (String, String) -> Unit,
        onClientDisconnected: (String) -> Unit
    ) {
        if (!checkBluetoothPermissions()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "启动服务器失败：蓝牙权限未授予", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Log.e(TAG, "启动服务器失败：蓝牙未启用。")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "启动服务器失败：蓝牙未启用", Toast.LENGTH_SHORT).show()
            }
            return
        }

        close()
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
                        if (!checkBluetoothPermissions()) {
                            Log.e(TAG, "服务器：缺少蓝牙权限，无法接受连接")
                            break
                        }
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

                        clientConnections[clientId] = clientConnection

                        withContext(Dispatchers.Main) {
                            onClientConnected(clientId, socket.remoteDevice.name ?: "Unknown Device")
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
            message.startsWith("PLAY_CARDS:") -> {
                val cardsJson = message.substringAfter("PLAY_CARDS:")
                try {
                    val clientName = getClientNameById(clientId) ?: "未知玩家"
                    Log.d(TAG, "服务器收到玩家 [$clientName] 出牌: $cardsJson")

                    // 解析出牌数据
                    val cards = JSONArray(cardsJson)
                    val playedCards = mutableListOf<Card>()
                    for (i in 0 until cards.length()) {
                        val cardObj = cards.getJSONObject(i)
                        val suit = when (cardObj.getString("suit").lowercase()) {
                            "hearts" -> Suit.HEARTS
                            "spades" -> Suit.SPADES
                            "diamonds" -> Suit.DIAMONDS
                            "clubs" -> Suit.CLUBS
                            else -> throw IllegalArgumentException("Invalid suit")
                        }
                        val rank = when (cardObj.getString("value")) {
                            "A" -> Rank.ACE
                            "J" -> Rank.JACK
                            "Q" -> Rank.QUEEN
                            "K" -> Rank.KING
                            else -> {
                                val value = cardObj.getString("value").toInt()
                                Rank.values().find { it.value == value }
                                    ?: throw IllegalArgumentException("Invalid rank value: $value")
                            }
                        }
                        playedCards.add(Card(suit, rank))
                    }

                    // 更新客户端手牌
                    val client = clientConnections[clientId]
                    if (client != null) {
                        val currentHand = client.currentHand.toMutableList()
                        Log.d(TAG, "更新客户端 [$clientName] 手牌: 原有 ${currentHand.size} 张，移除 ${playedCards.size} 张")
                        Log.d(TAG, "客户端 [$clientName] 当前手牌: ${currentHand.joinToString { "${it.suit}${it.rank}" }}")

                        // 从手牌中移除已出的牌
                        playedCards.forEach { playedCard ->
                            val index = currentHand.indexOfFirst { it.suit == playedCard.suit && it.rank == playedCard.rank }
                            if (index != -1) {
                                currentHand.removeAt(index)
                                Log.d(TAG, "移除牌: ${playedCard.suit}${playedCard.rank}")
                            } else {
                                Log.w(TAG, "警告：尝试移除不存在的牌 ${playedCard.suit}${playedCard.rank}")
                            }
                        }

                        client.currentHand = currentHand
                        Log.d(TAG, "客户端 [$clientName] 剩余手牌: ${currentHand.size} 张")
                        Log.d(TAG, "客户端 [$clientName] 剩余手牌: ${currentHand.joinToString { "${it.suit}${it.rank}" }}")

                        // 发送更新后的手牌给客户端
                        val remainingCardsJson = JSONArray().apply {
                            currentHand.forEach { card: Card ->
                                put(JSONObject().apply {
                                    put("suit", card.suit.toString())
                                    put("value", when (card.rank) {
                                        Rank.ACE -> "A"
                                        Rank.JACK -> "J"
                                        Rank.QUEEN -> "Q"
                                        Rank.KING -> "K"
                                        else -> card.rank.value.toString()
                                    })
                                })
                            }
                        }.toString()

                        Log.d(TAG, "发送更新后的手牌给客户端 [$clientName]: $remainingCardsJson")
                        sendDataToClient(clientId, "UPDATE_HAND:$remainingCardsJson")
                    } else {
                        Log.e(TAG, "错误：找不到客户端 [$clientId] 的连接信息")
                    }

                    // 广播出牌信息给所有客户端
                    broadcastCardPlay(clientId, clientName, cardsJson)
                } catch (e: Exception) {
                    Log.e(TAG, "处理出牌信息失败: ${e.message}", e)
                }
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

    @SuppressLint("MissingPermission")
    fun sendDataToClient(clientId: String, data: String) {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "发送数据失败：缺少蓝牙权限")
            return
        }
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

    @SuppressLint("MissingPermission")
    fun sendDataToAllClients(data: String) {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "广播数据失败：缺少蓝牙权限")
            return
        }
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

    /**
     * 向指定客户端发送其手牌信息
     * @param clientId 目标客户端ID
     * @param cards 手牌数据，格式为JSON字符串
     */
    fun sendPlayerCards(clientId: String, cards: String) {
        Log.d(TAG, "服务器向客户端 [$clientId] 发送手牌信息")
        val message = "PLAYER_CARDS:$cards"

        // 解析并更新客户端手牌
        try {
            val cardsArray = JSONArray(cards)
            val handCards = mutableListOf<Card>()
            for (i in 0 until cardsArray.length()) {
                val cardObj = cardsArray.getJSONObject(i)
                val suit = when (cardObj.getString("suit").lowercase()) {
                    "hearts" -> Suit.HEARTS
                    "spades" -> Suit.SPADES
                    "diamonds" -> Suit.DIAMONDS
                    "clubs" -> Suit.CLUBS
                    else -> throw IllegalArgumentException("Invalid suit")
                }
                val rank = when (cardObj.getString("value")) {
                    "A" -> Rank.ACE
                    "J" -> Rank.JACK
                    "Q" -> Rank.QUEEN
                    "K" -> Rank.KING
                    else -> Rank.values().find { it.value == cardObj.getString("value").toInt() }
                        ?: throw IllegalArgumentException("Invalid rank")
                }
                handCards.add(Card(suit, rank))
            }

            // 更新客户端手牌
            val client = clientConnections[clientId]
            if (client != null) {
                client.currentHand = handCards.toMutableList()
                Log.d(TAG, "更新客户端 [$clientId] 手牌: ${handCards.size} 张")
                Log.d(TAG, "客户端 [$clientId] 当前手牌: ${handCards.joinToString { "${it.suit}${it.rank}" }}")
            } else {
                Log.e(TAG, "错误：找不到客户端 [$clientId] 的连接信息")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析手牌数据失败: ${e.message}", e)
        }

        sendDataToClient(clientId, message)
    }

    /**
     * 广播玩家出牌信息给所有客户端
     * @param playerId 出牌的玩家ID
     * @param playerName 出牌的玩家名称
     * @param cards 出的牌，格式为JSON字符串
     */
    fun broadcastCardPlay(playerId: String, playerName: String, cards: String) {
        Log.d(TAG, "服务器广播玩家 [$playerName] 出牌信息")
        val message = "CARD_PLAYED:$playerId:$playerName:$cards"
        sendDataToAllClients(message)
    }

    @SuppressLint("MissingPermission")
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

    @SuppressLint("MissingPermission")
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

    @SuppressLint("MissingPermission")
    override fun close() { // 这是一个非 suspend 函数
        Log.d(TAG, "External close() called, cancelling server resources...")

        // 先取消所有正在进行的作业
        acceptJob?.cancel()
        acceptJob = null

        // 停止蓝牙发现
        try {
            if (checkBluetoothPermissions()) {
                bluetoothAdapter?.cancelDiscovery()
            } else {
                Log.w(TAG, "无法停止蓝牙发现：缺少权限")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "停止蓝牙发现时发生权限错误：${e.message}", e)
        }
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
    @SuppressLint("MissingPermission")
    fun getClientNameById(id: String): String? {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "获取客户端名称失败：缺少蓝牙权限")
            return null
        }
        return clientConnections[id]?.clientName
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "获取已配对设备失败：缺少蓝牙权限")
            return emptyList()
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Log.e(TAG, "获取已配对设备失败：蓝牙未启用")
            return emptyList()
        }
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    private inner class ClientConnection(
        private val socket: BluetoothSocket,
        private val onMessageReceived: (String, String) -> Unit,
        private val onClientDisconnected: (String) -> Unit
    ) : Closeable {
        val clientId: String = socket.remoteDevice.address
        val clientName: String = try {
            if (checkBluetoothPermissions()) {
                socket.remoteDevice.name ?: "Unknown Device"
            } else {
                Log.w(TAG, "无法获取设备名称：缺少蓝牙权限")
                "Unknown Device"
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "获取设备名称时发生权限错误：${e.message}", e)
            "Unknown Device"
        }
        var currentHand: MutableList<Card> = mutableListOf()

        private val inputStream = socket.inputStream
        private val outputStream = socket.outputStream
        private val reader = BufferedReader(InputStreamReader(inputStream))
        private var isRunning = true

        init {
            Log.d(TAG, "初始化客户端 [$clientId] 连接，名称: $clientName")
            serverScope.launch {
                try {
                    while (isRunning) {
                        val message = reader.readLine()
                        if (message != null) {
                            Log.d(TAG, "收到客户端 [$clientId] 消息: $message")
                            onMessageReceived(message, clientId)
                        } else {
                            Log.d(TAG, "客户端 [$clientId] 连接关闭")
                            break
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "读取客户端 [$clientId] 数据失败: ${e.message}", e)
                } finally {
                    close()
                }
            }
        }

        fun sendData(data: String) {
            try {
                outputStream.write("$data\n".toByteArray())
                outputStream.flush()
            } catch (e: IOException) {
                Log.e(TAG, "发送数据给客户端 [$clientId] 失败: ${e.message}", e)
                throw e
            }
        }

        override fun close() {
            isRunning = false
            try {
                reader.close()
                outputStream.close()
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "关闭客户端 [$clientId] 连接失败: ${e.message}", e)
            }
            onClientDisconnected(clientId)
        }
    }
}