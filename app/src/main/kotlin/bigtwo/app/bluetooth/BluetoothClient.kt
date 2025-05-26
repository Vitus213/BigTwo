package bigtwo.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.channels.ClosedSendChannelException // <<<< 添加这一行
private const val SERVICE_UUID = "00001101-0000-1000-8000-00805f9b34fb" // <-- 确保这里和服务端一致，如果之前更换过自定义UUID，请用您自定义的

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

    private var clientScope = CoroutineScope(Dispatchers.IO) // 初始作用域

    @SuppressLint("MissingPermission")
    fun connectToServer(
        device: BluetoothDevice,
        //onMessageReceived: (String) -> Unit,
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

        Log.d("BluetoothClient", "连接：旧资源已关闭，准备尝试新连接。目标设备: ${device.name ?: device.address}")

        // 重新初始化作用域以取消之前的任务，并为新连接准备
        clientScope = CoroutineScope(Dispatchers.IO)

        // !!! 在这里重新创建 sendChannel !!!
        sendChannel = Channel(Channel.UNLIMITED) // <<<<<<< 添加或修改这一行

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
                    val sendJob = sendMessages() // 此时 sendChannel 已经是新的、开放状态

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
        val buffer = ByteArray(1024)
        var bytes: Int
        try {
            while (mmInputStream != null && clientScope.isActive) {
                bytes = mmInputStream!!.read(buffer)
                if (bytes > 0) {
                    val receivedMessage = String(buffer, 0, bytes)
                    Log.d("BluetoothClient", "收到消息: $receivedMessage")
                    withContext(Dispatchers.Main) {
                        _messageReceived.emit(receivedMessage) // 使用SharedFlow发送消息
                        Log.d("BluetoothClient_Flow", "消息已通过SharedFlow发出: $receivedMessage")
                    }
                } else if (bytes == -1) {
                    // Stream closed/end of stream
                    Log.d("BluetoothClient", "输入流已结束，连接可能已断开。")
                    break
                }
            }
        } catch (e: IOException) {
            Log.e("BluetoothClient", "读取消息时断开连接: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("BluetoothClient", "读取消息时发生未知错误: ${e.message}", e)
        }
        // finally 块不再调用 close()，由主连接协程或外部管理
        Log.d("BluetoothClient", "listenForMessages 协程结束。")
    }

    /**
     * 用于通过通道发送数据。
     */
    fun sendData(message: String) {
        // 只有当通道开放且作用域活跃时才发送
        if (clientScope.isActive && !sendChannel.isClosedForSend) {
            clientScope.launch {
                try {
                    // --- 在这里进行修改 ---
                    sendChannel.send((message + "\n").toByteArray()) // <<< 在这里添加换行符
                    Log.d("BluetoothClient", "数据已放入发送通道: ${message}")
                } catch (e: ClosedSendChannelException) {
                    Log.e("BluetoothClient", "发送数据失败: 通道已关闭", e)
                } catch (e: Exception) {
                    Log.e("BluetoothClient", "发送数据到通道失败: ${e.message}", e)
                }
            }
        } else {
            Log.w("BluetoothClient", "无法发送数据：连接未激活或通道已关闭。")
        }
    }

    /**
     * 用于从发送通道读取数据并写入输出流。
     */
    private fun sendMessages() = clientScope.launch {
        try {
            // 这个循环会在 sendChannel 开启时正常运行
            for (bytes in sendChannel) {
                if (mmOutputStream != null && clientScope.isActive) {
                    mmOutputStream!!.write(bytes)
                    mmOutputStream!!.flush()
                    Log.d("BluetoothClient", "已发送数据: ${String(bytes)}")
                } else {
                    Log.w("BluetoothClient", "输出流为空或协程不活跃，无法发送数据。")
                    break
                }
            }
        } catch (e: IOException) {
            Log.e("BluetoothClient", "发送数据时断开连接: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("BluetoothClient", "发送数据时发生未知错误: ${e.message}", e)
        }
        Log.d("BluetoothClient", "sendMessages 协程结束。")
    }


    /**
     * 关闭客户端的蓝牙连接和资源。
     * 这是外部调用的方法，用于彻底终止连接。
     */
    fun close() {
        Log.d("BluetoothClient", "External close() called, cancelling client scope...")
        clientScope.cancel() // 取消所有子协程
        closeResources() // 清理资源
    }

    /**
     * 内部方法，用于安全地关闭输入输出流和 Socket。
     * 不会取消 CoroutineScope。
     */
    private fun closeResources() {
        Log.d("BluetoothClient", "Closing BluetoothClient resources...")
        try {
            // 确保 sendChannel 在这里被关闭，以便 sendMessages 协程可以完成其循环
            if (!sendChannel.isClosedForSend) {
                sendChannel.close()
            }
            mmOutputStream?.close()
            mmInputStream?.close()
            mmSocket?.close()
            Log.d("BluetoothClient", "BluetoothClient 资源已成功关闭。")
        } catch (e: IOException) {
            Log.e("BluetoothClient", "关闭客户端 Socket 时发生 IOException: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("BluetoothClient", "关闭客户端资源时发生未知错误: ${e.message}", e)
        } finally {
            mmSocket = null
            mmInputStream = null
            mmOutputStream = null
        }
    }
}