package bigtwo.app.network

import android.Manifest
import android.annotation.SuppressLint // 引入此注解
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
/**
 * 定义蓝牙连接的当前状态。
 */
enum class ConnectionState {
    Disconnected, // 已断开连接
    Connecting,   // 正在连接中
    Connected,    // 已成功连接
    Failed        // 连接失败
}
/**
 * 蓝牙客户端，用于连接到蓝牙服务器并进行数据通信。
 * 遵循 Coroutines 和 Flow 模式进行异步操作和状态管理。
 *
 * @param context 应用程序上下文，用于权限检查和获取蓝牙适配器。
 */
class BluetoothClient(private val context: Context) : Closeable {

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

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // 用于管理客户端内部所有协程的范围，当客户端关闭时，此范围内的所有协程都将被取消。
    private val clientScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Coroutine failed: ${throwable.message}", throwable)
            updateConnectionState(ConnectionState.Failed)
            closeConnection()
        }
    )

    // 用于发送连接状态更新的 StateFlow
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 用于发送接收到消息的 SharedFlow
    private val _receivedMessages = MutableSharedFlow<String>()
    val receivedMessages: SharedFlow<String> = _receivedMessages.asSharedFlow()

    private var receiveJob: Job? = null // 用于持有数据接收协程的引用

    companion object {
        private const val TAG = "BluetoothClient"
        // 标准的蓝牙串行端口配置文件 (SPP) UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    /**
     * 检查是否已授予 BLUETOOTH_CONNECT 权限。
     * @return 如果权限已授予则返回 true，否则返回 false。
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取已配对设备的列表。
     * 需要 BLUETOOTH_CONNECT 权限。
     * @return 已配对的 BluetoothDevice 列表，如果蓝牙不可用或权限不足则返回空列表。
     */
    @SuppressLint("MissingPermission") // 该方法内部涉及 BLUETOOTH_CONNECT 权限操作，已在方法内部逻辑中处理权限检查
    fun getPairedDevices(): List<BluetoothDevice> {
        // 使用 let 块安全地处理可空 BluetoothAdapter
        val adapter = bluetoothAdapter ?: run {
            Log.e(TAG, "设备不支持蓝牙。")
            return emptyList()
        }

        if (!hasBluetoothConnectPermission()) {
            Log.e(TAG, "获取配对设备失败：缺少 BLUETOOTH_CONNECT 权限。")
            return emptyList()
        }
        return adapter.bondedDevices.toList()
    }

    /**
     * 连接到指定的蓝牙设备。
     * 这是一个挂起函数，因为它执行耗时的网络操作。
     * 需要 BLUETOOTH_CONNECT 权限。
     * @param device 要连接的 BluetoothDevice 对象。
     * @return 连接成功则返回 true，否则返回 false。
     */
    @SuppressLint("MissingPermission") // 该方法内部涉及 BLUETOOTH_CONNECT 权限操作，已在方法内部逻辑中处理权限检查
    suspend fun connectToServer(device: BluetoothDevice): Boolean {
        if (!hasBluetoothConnectPermission()) {
            Log.e(TAG, "连接失败：缺少 BLUETOOTH_CONNECT 权限。")
            return false
        }
        // 使用 let 块安全地处理可空 BluetoothAdapter
        if (bluetoothAdapter?.isEnabled == false) {
            Log.e(TAG, "连接失败：蓝牙未启用。")
            return false
        }

        updateConnectionState(ConnectionState.Connecting)
        closeConnection() // 确保在尝试新连接前关闭旧连接

        return withContext(Dispatchers.IO) {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect() // 阻塞调用，直到连接成功或失败

                outputStream = bluetoothSocket?.outputStream
                startReceivingData() // 连接成功后启动数据接收

                // Log.d(TAG, ...) 中的 device.name 访问也需要 BLUETOOTH_CONNECT 权限，
                // 但已通过方法声明上的 @SuppressLint 处理。
                Log.d(TAG, "成功连接到服务器：${device.name}")
                updateConnectionState(ConnectionState.Connected)
                true
            } catch (e: IOException) {
                Log.e(TAG, "连接失败：${e.message}", e)
                updateConnectionState(ConnectionState.Failed)
                closeConnection()
                false
            }
        }
    }

    /**
     * 启动一个协程以持续接收来自蓝牙服务器的数据。
     * 接收到的数据会通过 `_receivedMessages` Flow 发送。
     */
    private fun startReceivingData() {
        receiveJob?.cancel() // 如果接收协程已在运行，先取消它以避免重复

        receiveJob = clientScope.launch {
            bluetoothSocket?.inputStream?.bufferedReader()?.use { reader ->
                try {
                    while (true) {
                        val receivedMessage = reader.readLine()
                        if (receivedMessage != null) {
                            Log.d(TAG, "接收到数据：$receivedMessage")
                            _receivedMessages.emit(receivedMessage) // 发送消息到 Flow
                        } else {
                            // 服务器断开连接或发送结束
                            Log.d(TAG, "服务器断开连接或发送结束。")
                            break
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "接收数据时发生错误：${e.message}", e)
                } finally {
                    // 接收结束或出错，关闭连接
                    closeConnection()
                }
            }
        }
    }

    /**
     * 发送数据到已连接的蓝牙设备。
     * 会在消息末尾添加换行符以方便读取。
     * @param message 要发送的字符串数据。
     */
    fun sendData(message: String) {
        if (outputStream == null) {
            Log.e(TAG, "发送数据失败：输出流未初始化或连接未建立。")
            return
        }
        clientScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    outputStream?.write((message + "\n").toByteArray()) // 添加换行符
                    outputStream?.flush()
                    Log.d(TAG, "发送数据：$message")
                }
            } catch (e: IOException) {
                Log.e(TAG, "数据发送失败：${e.message}", e)
                updateConnectionState(ConnectionState.Failed)
                closeConnection()
            }
        }
    }

    /**
     * 更新连接状态的内部函数。
     * @param state 要设置的新连接状态。
     */
    private fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
        Log.d(TAG, "连接状态更新: $state")
    }

    /**
     * 关闭所有蓝牙连接相关的资源。
     * 实现了 [Closeable] 接口，可以在 `use` 块中调用。
     */
    override fun close() {
        closeConnection()
    }

    /**
     * 安全地关闭所有蓝牙连接相关的资源。
     * 关闭输入/输出流、蓝牙 Socket，并取消相关的协程。
     */
    fun closeConnection() {
        receiveJob?.cancel() // 取消数据接收协程
        receiveJob = null

        try {
            outputStream?.close()
            outputStream = null
        } catch (e: IOException) {
            Log.e(TAG, "关闭输出流时出错：${e.message}", e)
        }

        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
        } catch (e: IOException) {
            Log.e(TAG, "关闭蓝牙 Socket 时出错：${e.message}", e)
        }
        updateConnectionState(ConnectionState.Disconnected)
        Log.d(TAG, "连接已关闭。")
    }
}