package com.example.myapplication.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private const val SERVICE_UUID =
    "00001101-0000-1000-8000-00805f9b34fb" // <-- 确保这里和服务端一致，如果之前更换过自定义UUID，请用您自定义的

class BluetoothClient(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var mmSocket: BluetoothSocket? = null
    private var mmInputStream: InputStream? = null
    private var mmOutputStream: OutputStream? = null

    // sendChannel 现在只声明而不初始化，因为它将在 connectToServer 内部每次重新创建
    private lateinit var sendChannel: Channel<ByteArray>

    private val _messageReceived = MutableSharedFlow<String>()
    val messageReceived = _messageReceived.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState = _connectionState.asStateFlow()

    private var clientScope = CoroutineScope(Dispatchers.IO) // 初始作用域

    @SuppressLint("MissingPermission")
    fun connectToServer(
        device: BluetoothDevice,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val adapter = bluetoothAdapter

        if (adapter == null || !adapter.isEnabled) {
            val msg = "蓝牙不可用或未启用。"
            Log.e("BluetoothClient", msg)
            onFailed(msg)
            return
        }

        Log.d("BluetoothClient", "Closing BluetoothClient resources...")
        // 在尝试新连接前，先关闭旧连接，确保资源干净
        close() // 确保旧资源已关闭，这会关闭旧的 sendChannel

        Log.d(
            "BluetoothClient",
            "连接：旧资源已关闭，准备尝试新连接。目标设备: ${device.name ?: device.address}"
        )

        // 重新初始化作用域以取消之前的任务，并为新连接准备
        clientScope = CoroutineScope(Dispatchers.IO)

        // 重新创建 sendChannel
        sendChannel = Channel(Channel.UNLIMITED)

        clientScope.launch {
            try {
                val uuid = UUID.fromString(SERVICE_UUID)
                Log.d("BluetoothClient", "尝试创建RFCOMM socket，UUID: $uuid")
                mmSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)

                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                    Log.d("BluetoothClient", "已取消正在进行的蓝牙发现。")
                }

                Log.d("BluetoothClient", "尝试连接到服务端...")
                mmSocket?.connect()
                Log.d("BluetoothClient", "成功连接到服务端！")

                // 更新连接状态
                _connectionState.value = true

                withContext(Dispatchers.Main) {
                    onConnected()
                }

                mmInputStream = mmSocket?.inputStream
                mmOutputStream = mmSocket?.outputStream

                if (mmInputStream != null && mmOutputStream != null) {
                    Log.d("BluetoothClient", "已获取输入输出流，开始监听消息。")
                    // 启动接收消息的协程
                    val listenJob = listenForMessages()
                    // 启动发送消息的协程
                    val sendJob = sendMessages()

                    // 等待其中一个任务完成（通常是监听任务因断开连接而结束）
                    listenJob.join()
                    Log.d("BluetoothClient", "监听协程已结束，假定连接已断开。")

                } else {
                    val msg = "连接成功但无法获取输入输出流。"
                    Log.e("BluetoothClient", msg)
                    withContext(Dispatchers.Main) { onFailed(msg) }
                }

            } catch (e: IOException) {
                val errorMessage = "连接失败或连接断开: IOException: ${e.message}"
                Log.e("BluetoothClient", errorMessage, e)
                withContext(Dispatchers.Main) { onFailed(errorMessage) }
            } catch (e: SecurityException) {
                val errorMessage = "连接失败: 缺少蓝牙权限: ${e.message}"
                Log.e("BluetoothClient", errorMessage, e)
                withContext(Dispatchers.Main) { onFailed(errorMessage) }
            } catch (e: Exception) {
                val errorMessage = "连接失败: 未知错误: ${e.message}"
                Log.e("BluetoothClient", errorMessage, e)
                withContext(Dispatchers.Main) { onFailed(errorMessage) }
            } finally {
                Log.d("BluetoothClient", "主连接协程结束，调用onDisconnected并清理资源。")
                // 更新连接状态
                _connectionState.value = false
                withContext(Dispatchers.Main) { onDisconnected() }
                closeResources()
            }
        }
    }

    /**
     * 用于持续监听来自服务器的消息。
     * 当连接断开或读取失败时，会抛出IOException，导致此协程结束。
     */
    private fun listenForMessages() = clientScope.launch {
        Log.d("BluetoothClient", "listenForMessages 协程已启动。")
        val buffer = ByteArray(1024)
        var bytes: Int
        try {
            while (mmInputStream != null && clientScope.isActive) {
                Log.d("BluetoothClient", "listenForMessages: 尝试读取数据...")
                bytes = mmInputStream!!.read(buffer)
                if (bytes > 0) {
                    val receivedMessage = String(buffer, 0, bytes)
                    Log.d("BluetoothClient", "收到消息: $receivedMessage")
                    withContext(Dispatchers.Main) {
                        _messageReceived.emit(receivedMessage)
                        Log.d("BluetoothClient_Flow", "消息已通过SharedFlow发出: $receivedMessage")
                    }
                } else if (bytes == -1) {
                    Log.d("BluetoothClient", "输入流已结束（readLine returned null），连接可能已断开。")
                    break
                }
            }
        } catch (e: IOException) {
            Log.e("BluetoothClient", "读取消息时发生IOException: ${e.message}", e)
            // 更新连接状态
            _connectionState.value = false
        } catch (e: Exception) {
            Log.e("BluetoothClient", "读取消息时发生未知错误: ${e.message}", e)
            // 更新连接状态
            _connectionState.value = false
        } finally {
            Log.d("BluetoothClient", "listenForMessages 协程结束。isConnected=${_connectionState.value}")
        }
    }

    /**
     * 用于通过通道发送数据。
     */
    fun sendData(message: String) {
        if (!_connectionState.value) {
            Log.w("BluetoothClient", "无法发送数据：未连接到服务器")
            return
        }

        if (clientScope.isActive && !sendChannel.isClosedForSend) {
            clientScope.launch {
                try {
                    sendChannel.send((message + "\n").toByteArray())
                    Log.d("BluetoothClient", "数据已放入发送通道: $message")
                } catch (e: ClosedSendChannelException) {
                    Log.e("BluetoothClient", "发送数据失败: 通道已关闭", e)
                } catch (e: Exception) {
                    Log.e("BluetoothClient", "发送数据到通道失败: ${e.message}", e)
                }
            }
        } else {
            Log.w("BluetoothClient", "无法发送数据：客户端作用域未激活或发送通道已关闭")
        }
    }

    /**
     * 用于持续从通道读取数据并发送到服务器。
     */
    private fun sendMessages() = clientScope.launch {
        try {
            while (mmOutputStream != null && clientScope.isActive) {
                val data = sendChannel.receive()
                mmOutputStream?.write(data)
                mmOutputStream?.flush()
                Log.d("BluetoothClient", "数据已写入输出流: ${String(data)}")
            }
        } catch (e: Exception) {
            Log.e("BluetoothClient", "发送消息时发生错误: ${e.message}", e)
        }
    }

    /**
     * 关闭客户端的蓝牙连接和资源。
     * 这是外部调用的方法，用于彻底终止连接。
     */
    fun close() {
        Log.d("BluetoothClient", "关闭客户端资源...")
        clientScope.cancel()
        closeResources()
    }

    /**
     * 内部方法，用于安全地关闭输入输出流和 Socket。
     * 不会取消 CoroutineScope。
     */
    private fun closeResources() {
        try {
            mmInputStream?.close()
            mmOutputStream?.close()
            mmSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothClient", "关闭资源时发生错误: ${e.message}", e)
        } finally {
            mmInputStream = null
            mmOutputStream = null
            mmSocket = null
        }
    }
}