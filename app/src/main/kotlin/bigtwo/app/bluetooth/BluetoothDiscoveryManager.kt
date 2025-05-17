package bigtwo.app.network

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class BluetoothDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothDiscoveryManager"
        const val REQUEST_CODE_BLUETOOTH_SCAN = 101
        const val REQUEST_CODE_BLUETOOTH_CONNECT = 102
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val systemBluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            systemBluetoothManager.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private val discoveredDevices = mutableSetOf<BluetoothDevice>()
    private val _discoveredDevicesLiveData = MutableLiveData<Set<BluetoothDevice>>()
    val discoveredDevicesLiveData: LiveData<Set<BluetoothDevice>> get() = _discoveredDevicesLiveData

    private var isReceiverRegistered = false

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission(permission: String, requestCode: Int) {
        if (context is Activity) {
            ActivityCompat.requestPermissions(context, arrayOf(permission), requestCode)
        } else {
            Log.e(TAG, "Context is not an Activity, cannot request permissions.")
            Toast.makeText(context, "无法请求权限，请确保上下文是Activity", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 启动蓝牙扫描。
     * 需要 BLUETOOTH_SCAN 权限。
     */
    @SuppressLint("MissingPermission") // BLUETOOTH_SCAN 权限已在方法开始处检查
    fun startDiscovery() {
        val adapter = bluetoothAdapter // 捕获到局部不可变变量
        if (adapter == null) {
            Toast.makeText(context, "设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "设备不支持蓝牙。")
            return
        }

        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            requestPermission(Manifest.permission.BLUETOOTH_SCAN, REQUEST_CODE_BLUETOOTH_SCAN)
            Toast.makeText(context, "缺少蓝牙扫描权限，正在请求...", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "BLUETOOTH_SCAN 权限未授予，正在请求。")
            return
        }

        // 如果蓝牙适配器正在扫描，先取消
        if (adapter.isDiscovering) { // 现在可以直接使用 adapter，因为它已智能转换为非空
            adapter.cancelDiscovery()
            Log.d(TAG, "正在取消之前的蓝牙扫描。")
        }

        // 确保在启动新扫描前注销旧的接收器，以防万一
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "注销了之前注册的广播接收器。")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "广播接收器未注册或已注销：${e.message}")
            }
        }

        discoveredDevices.clear()
        updateDiscoveredDevices(discoveredDevices)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            context.registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
            adapter.startDiscovery() // 直接使用 adapter
            Log.i(TAG, "开始扫描蓝牙设备...")
            Toast.makeText(context, "开始扫描蓝牙设备...", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException：启动扫描失败：${e.message}")
            Toast.makeText(context, "启动扫描失败：缺少蓝牙扫描权限", Toast.LENGTH_SHORT).show()
            isReceiverRegistered = false
        } catch (e: Exception) {
            Log.e(TAG, "启动扫描时发生未知错误：${e.message}", e)
            Toast.makeText(context, "启动扫描时发生错误", Toast.LENGTH_SHORT).show()
            isReceiverRegistered = false
        }
    }

    /**
     * 广播接收器：处理发现设备和扫描完成事件。
     */
    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission") // 权限已在 startDiscovery() 中检查
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val deviceName = it.name ?: "未知设备"
                        Log.d(TAG, "发现蓝牙设备：$deviceName - ${it.address}")
                        discoveredDevices.add(it)
                        updateDiscoveredDevices(discoveredDevices)
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.i(TAG, "蓝牙扫描完成，共发现 ${discoveredDevices.size} 个设备。")
                    Toast.makeText(context, "蓝牙扫描完成", Toast.LENGTH_SHORT).show()
                    updateDiscoveredDevices(discoveredDevices)
                    if (isReceiverRegistered) {
                        try {
                            context.unregisterReceiver(this)
                            isReceiverRegistered = false
                            Log.d(TAG, "蓝牙广播接收器已注销 (扫描完成)。")
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "注销广播接收器时出错：${e.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * 更新设备列表并通知 LiveData 观察者。
     */
    private fun updateDiscoveredDevices(devices: Set<BluetoothDevice>) {
        _discoveredDevicesLiveData.postValue(devices)
    }

    /**
     * 取消正在进行的蓝牙扫描（如果存在）。
     * 不会注销广播接收器。
     */
    @SuppressLint("MissingPermission") // 将注解移动到方法级别
    fun cancelDiscovery() {
        val adapter = bluetoothAdapter // 捕获到局部不可变变量
        if (adapter == null) {
            Log.w(TAG, "设备不支持蓝牙，无法取消扫描。")
            return
        }

        if (adapter.isDiscovering) { // 直接使用 adapter
            adapter.cancelDiscovery()
            Log.i(TAG, "蓝牙扫描已取消。")
            Toast.makeText(context, "蓝牙扫描已取消。", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, "当前没有进行中的蓝牙扫描。")
        }
    }

    /**
     * 停止蓝牙扫描并注销广播接收器。
     * 应该在 Activity 的 onStop 或 onDestroy 等生命周期方法中调用，
     * 以避免内存泄漏和不必要的资源占用。
     */
    @SuppressLint("MissingPermission") // 将注解移动到方法级别
    fun stopDiscoveryAndUnregisterReceiver() {
        val adapter = bluetoothAdapter // 捕获到局部不可变变量
        if (adapter != null) { // 检查适配器是否可用
            if (adapter.isDiscovering) { // 直接使用 adapter，此处操作已被方法级注解覆盖
                adapter.cancelDiscovery()
                Log.i(TAG, "蓝牙扫描已停止。")
            } else {
                Log.d(TAG, "当前没有进行中的蓝牙扫描。")
            }
        } else {
            Log.w(TAG, "设备不支持蓝牙，无法停止扫描。")
        }


        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
                Log.i(TAG, "蓝牙广播接收器已注销 (手动)。")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "注销广播接收器时出错：${e.message}")
            }
        } else {
            Log.d(TAG, "广播接收器未注册，无需注销。")
        }
    }
}