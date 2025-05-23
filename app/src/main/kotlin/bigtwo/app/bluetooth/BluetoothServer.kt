package bigtwo.app.network

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay // 导入 delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

class BluetoothServer(private val context: Context) : Closeable {

    companion object {
        private const val TAG = "BluetoothServer"
        private const val SERVER_NAME = "BigTwoServer"
        private val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val serverScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Server Coroutine failed: ${throwable.message}", throwable)
            // 在这里调用 close() 可能会导致延迟问题，如果这是由非IO异常引起，
            // 且需要在MainThread中操作，可以考虑更精细的控制
            // 目前保持不变，因为其主要目的是清理资源
            close()
        }
    )

    private var acceptJob: Job? = null
    private var receiveJob: Job? = null

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun startServer(
        onMessageReceived: (String) -> Unit,
        onClientConnected: () -> Unit,
        onClientDisconnected: (() -> Unit)? = null
    ) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "启动服务器失败：BLUETOOTH_CONNECT 权限未授予。")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "启动服务器失败：蓝牙连接权限未授予", Toast.LENGTH_SHORT).show()
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

        // 步骤1: 确保在启动新服务器前关闭旧的连接和 Socket。
        // 这会关闭旧的 serverSocket 并将其置为 null，为新的监听做准备。
        close()
        Log.d(TAG, "服务器：旧资源已关闭，准备重新启动监听。")

        // 步骤2: 引入短暂延迟，给蓝牙底层服务时间来清理端口和资源
        val delayMillis = 500L // 建议 200ms 到 500ms，可以尝试调整
        Log.d(TAG, "服务器：等待 $delayMillis ms 以确保资源释放。")
        delay(delayMillis) // 挂起协程，等待一段时间

        try {
            // 步骤3: 重新创建新的 BluetoothServerSocket 实例
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVER_NAME, APP_UUID)
            Log.i(TAG, "服务器启动，等待客户端连接...")
            Log.d(TAG, "服务器：新 serverSocket 实例已创建。UUID: $APP_UUID")

            acceptJob = serverScope.launch {
                Log.d(TAG, "服务器：Accept协程启动，即将调用 serverSocket.accept()...")
                val socket: BluetoothSocket? = try {
                    serverSocket?.accept() // 阻塞调用
                } catch (e: IOException) {
                    Log.e(TAG, "服务器：serverSocket.accept() 失败：${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "服务器接受连接失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                    null
                } catch (se: SecurityException) {
                    Log.e(TAG, "服务器：SecurityException：未授予 BLUETOOTH_CONNECT 权限，无法 accept()。", se)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "权限不足，服务器无法接受连接", Toast.LENGTH_SHORT).show()
                    }
                    null
                }

                if (socket != null) {
                    Log.d(TAG, "服务器：accept() 成功获取到 Socket。")
                    clientSocket = socket
                    try {
                        Log.d(TAG, "服务器：客户端已连接：${socket.remoteDevice?.name} - ${socket.remoteDevice?.address}")
                    } catch (se: SecurityException) {
                        Log.e(TAG, "SecurityException：无法获取客户端设备名或地址。")
                    }

                    withContext(Dispatchers.Main) {
                        onClientConnected()
                    }

                    try {
                        inputStream = clientSocket?.inputStream
                        outputStream = clientSocket?.outputStream
                    } catch (e: IOException) {
                        Log.e(TAG, "服务器：获取输入输出流失败：${e.message}")
                        closeClientConnection()
                        return@launch
                    }

                    Log.d(TAG, "服务器：客户端已连接，尝试启动数据接收协程。")
                    receiveJob = serverScope.launch {
                        receiveData(onMessageReceived, onClientDisconnected)
                    }
                    Log.d(TAG, "服务器：数据接收协程已启动。")

                } else {
                    Log.d(TAG, "服务器：未成功接受客户端连接，accept() 返回 null。") // 修改日志，更准确
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "服务器启动失败（listenUsingRfcommWithServiceRecord）：${e.message}", e)
            close() // 启动失败时也需要调用 close，确保清理不完整的状态
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "服务器启动失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 发送数据给客户端。
     * @param data 要发送的字符串数据。
     */
    @SuppressLint("MissingPermission") // 假设调用此方法前已检查 BLUETOOTH_CONNECT 权限
    fun sendData(data: String) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "发送数据失败：BLUETOOTH_CONNECT 权限未授予。")
            return
        }
        if (outputStream == null || clientSocket?.isConnected == false) {
            Log.e(TAG, "发送数据失败：输出流未初始化或客户端未连接。")
            return
        }

        serverScope.launch { // 在服务器协程作用域内发送数据
            try {
                withContext(Dispatchers.IO) { // 切换到 IO 线程执行阻塞操作
                    outputStream?.write((data + "\n").toByteArray())
                    outputStream?.flush()
                    Log.i(TAG, "发送数据：$data")
                }
            } catch (e: IOException) {
                Log.e(TAG, "发送失败：${e.message}", e)
                closeClientConnection() // 发送失败时关闭客户端连接
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "发送数据失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException：无权限发送数据。")
                closeClientConnection()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "发送数据失败：权限不足", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 持续接收客户端数据。
     * 当客户端断开连接或发生 IO 错误时，会调用 `onClientDisconnected` 回调。
     * @param onMessageReceived 接收到消息时调用的回调。
     * @param onClientDisconnected 客户端断开连接时调用的回调（可选）。
     */
    private suspend fun receiveData(
        onMessageReceived: (String) -> Unit,
        onClientDisconnected: (() -> Unit)?
    ) {
        Log.d(TAG, "服务器：进入 receiveData 方法，开始监听客户端消息。") // **新增日志**
        withContext(Dispatchers.IO) { // 确保在 IO 调度器进行阻塞的 IO 操作
            clientSocket?.inputStream?.bufferedReader()?.use { reader ->
                try {
                    var receivedMessage: String?
                    while (true) {
                        receivedMessage = reader.readLine() // 阻塞直到有数据或流关闭
                        if (receivedMessage != null) {
                            Log.d(TAG, "服务器：成功接收到数据：$receivedMessage") // **新增日志**
                            withContext(Dispatchers.Main) { // 切换到主线程调用回调
                                onMessageReceived(receivedMessage)
                            }
                        } else {
                            // readLine() 返回 null，表示流结束或客户端已断开连接
                            Log.d(TAG, "服务器：InputStream readLine() 返回 null，客户端可能已断开连接。") // **新增日志**
                            break // 退出循环
                        }
                    }
                } catch (e: IOException) {
                    // IO 异常，通常意味着连接断开或发生其他通信错误
                    Log.e(TAG, "服务器：数据接收错误：${e.message}", e) // **修改日志前缀**
                } finally {
                    // 无论循环如何退出 (正常结束、break、异常)，都应该关闭连接
                    Log.d(TAG, "服务器：receiveData finally 块，准备关闭客户端连接。") // **新增日志**
                    closeClientConnection()
                    withContext(Dispatchers.Main) { // 在主线程调用断开回调
                        onClientDisconnected?.invoke()
                    }
                }
            }
        }
    }

    /**
     * 关闭当前已连接的客户端的 Socket 和流。
     */
    private fun closeClientConnection() {
        receiveJob?.cancel() // 取消数据接收协程
        receiveJob = null

        try {
            inputStream?.close()
            inputStream = null
        } catch (e: IOException) {
            Log.e(TAG, "关闭输入流时出错：${e.message}", e)
        }
        try {
            outputStream?.close()
            outputStream = null
        } catch (e: IOException) {
            Log.e(TAG, "关闭输出流时出错：${e.message}", e)
        }
        try {
            clientSocket?.close()
            clientSocket = null
            Log.i(TAG, "客户端连接已关闭。")
        } catch (e: IOException) {
            Log.e(TAG, "关闭客户端 Socket 时出错：${e.message}", e)
        }
    }

    /**
     * 实现 Closeable 接口的 close 方法。
     * 关闭服务器和所有连接相关的资源。
     */
    override fun close() {
        Log.d(TAG, "Closing BluetoothServer resources...")
        acceptJob?.cancel()
        acceptJob = null

        closeClientConnection() // 先关闭任何活动的客户端连接

        try {
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "蓝牙服务器 Socket 已关闭。")
        } catch (e: IOException) {
            Log.e(TAG, "关闭服务器 Socket 时发生错误：${e.message}", e)
        }
        // 不需要在这里取消 serverScope，因为它是整个服务器实例的范围，
        // 只有当服务器实例不再使用时才取消。
        // 如果每次 close 都取消 serverScope，那么下次 launch 协程就会失败。
        // serverScope.cancel() // <-- 移除或注释掉这行
        Log.i(TAG, "蓝牙服务器已完全关闭。")
    }
}