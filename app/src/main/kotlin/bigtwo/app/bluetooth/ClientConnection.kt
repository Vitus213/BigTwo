// bigtwo.app.network.ClientConnection.kt
package bigtwo.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 封装单个蓝牙客户端连接的通信逻辑。
 * 每个 ClientConnection 实例代表一个已连接的客户端。
 */
class ClientConnection(
    private val clientSocket: BluetoothSocket,
    private val onMessageReceived: (String, String) -> Unit,
    private val onClientDisconnected: (String) -> Unit
) {
    private val TAG = "ClientConnection"

    @SuppressLint("MissingPermission")
    val clientId: String = try {
        clientSocket.remoteDevice?.address ?: "未知地址"
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException: 无法获取客户端MAC地址，可能是权限不足。")
        "未知地址_error"
    }

    @SuppressLint("MissingPermission")
    val clientName: String = try {
        clientSocket.remoteDevice?.name ?: "未知设备"
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException: 无法获取客户端名称，可能是权限不足。")
        clientId
    }

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // 使用 lazy 延迟初始化 connectionScope
    private val connectionScope: CoroutineScope by lazy {
        CoroutineScope(
            Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
                // <--- 修正这里：CoroutineExceptionHandler 的 lambda 参数是 CoroutineContext 和 Throwable
                Log.e(
                    TAG,
                    "ClientConnection[$clientId] Coroutine failed: ${throwable.message}",
                    throwable
                )
                // 在 CoroutineExceptionHandler 中，我们需要明确引用 connectionScope
                // 如果 connectionScope 仍然活跃，则尝试启动一个协程来关闭连接
                if (connectionScope.isActive) { // <--- 修正这里：使用 connectionScope.isActive
                    connectionScope.launch { // 在 connectionScope 内部启动一个协程
                        try {
                            close() // 调用 suspend 函数
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Error during closing ClientConnection from exception handler: ${e.message}"
                            )
                        }
                    }
                }
            }
        )
    }

    private var receiveJob: Job? = null

    init {
        // 在 init 块中启动一个协程来执行所有初始化和阻塞操作
        // 确保 connectionScope 已经被初始化 (因为是 lazy 的，第一次访问时会初始化)
        connectionScope.launch {
            try {
                inputStream = withContext(Dispatchers.IO) { clientSocket.inputStream }
                outputStream = withContext(Dispatchers.IO) { clientSocket.outputStream }
                Log.d(TAG, "ClientConnection[$clientId]：输入输出流已获取。")
                startReceivingData()
            } catch (e: IOException) {
                Log.e(TAG, "ClientConnection[$clientId]：获取输入输出流失败：${e.message}", e)
                close()
            } catch (e: SecurityException) {
                Log.e(TAG, "ClientConnection[$clientId]：初始化SecurityException：${e.message}", e)
                close()
            } catch (e: Exception) {
                Log.e(TAG, "ClientConnection[$clientId]：初始化发生未知错误：${e.message}", e)
                close()
            }
        }
    }

    /**
     * 启动一个协程持续接收来自此客户端的数据。
     */
    private fun startReceivingData() {
        if (receiveJob != null && receiveJob?.isActive == true) {
            Log.d(TAG, "ClientConnection[$clientId]：接收协程已在运行。")
            return
        }

        receiveJob = connectionScope.launch {
            Log.d(TAG, "ClientConnection[$clientId]：进入 receiveData 方法，开始监听客户端消息。")
            inputStream?.bufferedReader()?.use { reader ->
                try {
                    var receivedMessage: String?
                    while (isActive) { // 这里使用当前协程的 isActive
                        receivedMessage = withContext(Dispatchers.IO) { reader.readLine() }
                        if (receivedMessage != null) {
                            Log.d(
                                TAG,
                                "ClientConnection[$clientId]：成功接收到数据：$receivedMessage"
                            )
                            withContext(Dispatchers.Main) {
                                onMessageReceived(receivedMessage, clientId)
                            }
                        } else {
                            Log.d(
                                TAG,
                                "ClientConnection[$clientId]：InputStream readLine() 返回 null，客户端可能已断开连接。"
                            )
                            break
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "ClientConnection[$clientId]：数据接收错误：${e.message}", e)
                } finally {
                    Log.d(TAG, "ClientConnection[$clientId]：receiveData finally 块，准备关闭连接。")
                    // 在 finally 块中调用 close()，确保它总能执行
                    // close() 方法本身会处理重复关闭和协程取消逻辑
                    if (connectionScope.isActive) { // 再次检查 scope 是否活跃再尝试关闭
                        close()
                    }
                }
            } ?: run {
                Log.e(TAG, "ClientConnection[$clientId]：输入流为 null，无法开始接收数据。")
                close()
            }
        }
    }

    /**
     * 发送数据给此客户端。
     * @param data 要发送的字符串数据。
     */
    @SuppressLint("MissingPermission")
    fun sendData(data: String) {
        if (outputStream == null || !clientSocket.isConnected) {
            Log.e(TAG, "ClientConnection[$clientId]：发送数据失败：输出流未初始化或客户端未连接。")
            return
        }

        connectionScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    outputStream?.write((data + "\n").toByteArray())
                    outputStream?.flush()
                    Log.i(TAG, "ClientConnection[$clientId]：发送数据：$data")
                }
            } catch (e: IOException) {
                Log.e(TAG, "ClientConnection[$clientId]：发送失败：${e.message}", e)
                close()
            } catch (se: SecurityException) {
                Log.e(TAG, "ClientConnection[$clientId]：SecurityException：无权限发送数据。")
                close()
            }
        }
    }

    /**
     * 关闭此客户端连接的所有资源。
     * 标记为 suspend，因为其中包含 withContext(Dispatchers.Main) 调用。
     */
    @SuppressLint("MissingPermission")
    suspend fun close() {
        Log.d(TAG, "ClientConnection[$clientId]：正在关闭连接资源...")

        // 直接取消整个作用域，它会取消所有子 Job
        connectionScope.cancel() // 立即取消所有在此作用域内运行的协程

        // 确保 receiveJob 也被清空，避免 dangling reference
        receiveJob = null // 移除对 Job 的引用

        // 在 IO 线程执行关闭操作
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
                inputStream = null // 清空引用
            } catch (e: IOException) {
                Log.e(TAG, "ClientConnection[$clientId]：关闭输入流时出错：${e.message}", e)
            }
            try {
                outputStream?.close()
                outputStream = null // 清空引用
            } catch (e: IOException) {
                Log.e(TAG, "ClientConnection[$clientId]：关闭输出流时出错：${e.message}", e)
            }
            try {
                clientSocket.close() // Socket 必须是非空，因为它是通过构造函数传入的
                Log.i(TAG, "ClientConnection[$clientId]：客户端 Socket 已关闭。")
            } catch (e: IOException) {
                Log.e(TAG, "ClientConnection[$clientId]：关闭客户端 Socket 时出错：${e.message}", e)
            }
        }

        // 通知外部此客户端已断开，确保在主线程调用
        withContext(Dispatchers.Main) {
            onClientDisconnected(clientId)
        }

        Log.i(TAG, "ClientConnection[$clientId]：连接已完全清理。")
    }
}