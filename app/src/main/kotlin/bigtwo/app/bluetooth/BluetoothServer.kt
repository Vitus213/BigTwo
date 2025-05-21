package bigtwo.app.network

import android.Manifest
import android.annotation.SuppressLint // 引入此注解
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager // 引入 BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build // 引入 Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler // 引入 CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job // 引入 Job
import kotlinx.coroutines.SupervisorJob // 引入 SupervisorJob
import kotlinx.coroutines.cancel // 引入 cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.Closeable // 引入 Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

/**
 * 蓝牙服务端，用于监听客户端连接、接收和发送数据。
 * 遵循 Coroutines 和 Flow 模式进行异步操作和状态管理。
 *
 * @param context 应用程序上下文，用于权限检查和获取蓝牙适配器。
 */
class BluetoothServer(private val context: Context) : Closeable { // 实现 Closeable 接口

    companion object {
        private const val TAG = "BluetoothServer"
        private const val SERVER_NAME = "BigTwoServer"
        private val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    // 通过 lazy 初始化 BluetoothAdapter，兼容不同 Android 版本获取方式
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+ 使用 BluetoothManager 获取
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } else { // 兼容旧版本，使用 getDefaultAdapter()
            @Suppress("DEPRECATION") // 抑制对已弃用方法的警告
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // 用于管理服务器内部所有协程的范围，当服务器关闭时，此范围内的所有协程都将被取消。
    private val serverScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Server Coroutine failed: ${throwable.message}", throwable)
            close() // 协程发生异常时尝试关闭服务器
        }
    )

    private var acceptJob: Job? = null // 用于持有 accept 循环的 Job
    private var receiveJob: Job? = null // 用于持有数据接收的 Job

    /**
     * 权限检查辅助函数。
     * @param permission 要检查的权限字符串。
     * @return 如果权限已授予则返回 `true`，否则返回 `false`。
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 开启蓝牙服务端，监听客户端连接。
     * 这是一个挂起函数，因为它内部会进行阻塞的 `accept()` 调用。
     * @param onMessageReceived 当接收到客户端消息时调用的回调。
     * @param onClientConnected 当有客户端成功连接时调用的回调。
     * @param onClientDisconnected 当客户端断开连接时调用的回调（可选）。
     */
    @SuppressLint("MissingPermission") // 假设调用此方法前已检查 BLUETOOTH_CONNECT 权限
    suspend fun startServer(
        onMessageReceived: (String) -> Unit,
        onClientConnected: () -> Unit,
        onClientDisconnected: (() -> Unit)? = null // 添加 onClientDisconnected 回调并设为可空
    ) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "启动服务器失败：BLUETOOTH_CONNECT 权限未授予。")
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Log.e(TAG, "启动服务器失败：蓝牙未启用。")
            return
        }

        // 确保在启动新服务器前关闭旧的连接
        close()

        try {
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVER_NAME, APP_UUID)
            Log.i(TAG, "服务器启动，等待客户端连接...")

            // 将 accept 循环放在服务器作用域内
            acceptJob = serverScope.launch {
                while (true) {
                    val socket: BluetoothSocket? = try {
                        serverSocket?.accept() // 阻塞调用，直到客户端连接或发生异常
                    } catch (e: IOException) {
                        Log.e(TAG, "serverSocket.accept() 失败：${e.message}")
                        break // 退出循环，服务器停止监听
                    } catch (se: SecurityException) {
                        Log.e(TAG, "SecurityException：未授予 BLUETOOTH_CONNECT 权限，无法 accept()。")
                        break // 退出循环
                    }

                    if (socket != null) {
                        // 确保只处理一个客户端连接，如果已有连接，则关闭旧的或新接受的
                        if (clientSocket != null && clientSocket?.isConnected == true) {
                            Log.d(TAG, "已有客户端连接，关闭新接受的连接。")
                            socket.close()
                            continue
                        }

                        try {
                            Log.d(TAG, "客户端已连接：${socket.remoteDevice?.name}")
                        } catch (se: SecurityException) {
                            Log.e(TAG, "SecurityException：无法获取客户端设备名。")
                        }

                        clientSocket = socket
                        onClientConnected() // 调用连接成功的回调

                        try {
                            inputStream = socket.inputStream
                            outputStream = socket.outputStream
                        } catch (e: IOException) {
                            Log.e(TAG, "获取输入输出流失败：${e.message}")
                            closeClientConnection() // 关闭当前的客户端连接
                            continue // 继续等待下一个客户端
                        }

                        // 为当前连接的客户端启动数据接收协程
                        receiveJob = serverScope.launch {
                            receiveData(onMessageReceived, onClientDisconnected)
                        }
                    }
                }
            }
            acceptJob?.join() // 等待 accept 循环结束
        } catch (e: IOException) {
            Log.e(TAG, "服务器启动失败：${e.message}")
            close() // 启动失败时关闭所有资源
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
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException：无权限发送数据。")
                closeClientConnection()
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
        // 使用 use 确保 BufferedReader 和 InputStreamReader 在退出时自动关闭
        clientSocket?.inputStream?.bufferedReader()?.use { reader ->
            try {
                while (true) {
                    val receivedMessage = reader.readLine()
                    if (receivedMessage != null) {
                        Log.d(TAG, "接收到数据：$receivedMessage")
                        withContext(Dispatchers.Main) { // 切换到主线程调用回调
                            onMessageReceived(receivedMessage)
                        }
                    } else {
                        // readLine() 返回 null 通常表示流结束或客户端已断开连接
                        Log.d(TAG, "InputStream 为空，客户端可能已断开连接。")
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "数据接收错误：${e.message}", e)
            } finally {
                // 客户端断开或出错，关闭当前客户端连接
                closeClientConnection()
                withContext(Dispatchers.Main) { // 在主线程调用断开回调
                    onClientDisconnected?.invoke()
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
        acceptJob?.cancel() // 取消 accept 循环协程
        acceptJob = null

        closeClientConnection() // 先关闭任何活动的客户端连接

        try {
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "蓝牙服务器 Socket 已关闭。")
        } catch (e: IOException) {
            Log.e(TAG, "关闭服务器 Socket 时发生错误：${e.message}", e)
        }
        serverScope.cancel() // 取消服务器的整个协程作用域
        Log.i(TAG, "蓝牙服务器已完全关闭。")
    }
}