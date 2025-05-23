package bigtwo.app.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * 负责管理蓝牙权限的类。
 * 它处理权限的检查、请求和状态更新，并向外部提供权限状态。
 *
 * @param context 用于检查权限和显示 Toast 的上下文（通常是 Activity）。
 * @param activityResultRegistry 用于注册 ActivityResultLauncher 的注册器。
 * @param lifecycleOwner 用于生命周期感知的 Launcher 注册。
 */
class BluetoothPermissionManager(
    private val context: Context,
    private val activityResultRegistry: ActivityResultRegistry,
    private val lifecycleOwner: LifecycleOwner
) {
    // 蓝牙扫描权限的 Compose 状态
    private val _hasBluetoothScanPermission = mutableStateOf(false)
    // 蓝牙连接权限的 Compose 状态
    private val _hasBluetoothConnectPermission = mutableStateOf(false)
    // 精确位置权限的 Compose 状态 (对于旧版本蓝牙扫描很重要)
    private val _hasFineLocationPermission = mutableStateOf(false)

    // 提供只读属性，供 UI 观察权限状态
    val hasBluetoothScanPermission: State<Boolean> = _hasBluetoothScanPermission
    val hasBluetoothConnectPermission: State<Boolean> = _hasBluetoothConnectPermission
    val hasFineLocationPermission: State<Boolean> = _hasFineLocationPermission

    // 注册权限请求的 Launcher
    private val requestBluetoothPermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activityResultRegistry.register(
            "bluetooth_permissions_request", // 唯一的 key
            lifecycleOwner,
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handleBluetoothPermissionResults(permissions)
        }

    /**
     * 核心权限管理函数：检查当前蓝牙权限状态，并根据需要发起权限请求。
     * 如果所有权限已授予，则更新状态并显示提示；否则，启动权限请求。
     */
    fun manageBluetoothPermissions() {
        val permissionsToRequest = getMissingBluetoothPermissions()

        if (permissionsToRequest.isNotEmpty()) {
            requestBluetoothPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // 所有权限都已存在，更新状态为 true
            updateBluetoothPermissionStates(
                scanGranted = true,
                connectGranted = true,
                // 对于 API 31+，ACCESS_FINE_LOCATION 默认为 true (因为它不强制要求)
                // 对于旧版本，需要根据实际检查结果来更新
                fineLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    true
                } else {
                    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            )
            showToast("蓝牙权限已存在")
        }
    }

    /**
     * 获取当前设备缺失的蓝牙相关权限列表。
     * @return 缺失的权限字符串列表。
     */
    private fun getMissingBluetoothPermissions(): List<String> {
        val permissionsNeeded = mutableListOf<String>()

        // 检查蓝牙连接和扫描权限 (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // 如果您的应用需要蓝牙广告功能，请取消注释以下行
            // if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
            //     permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            // }
        } else {
            // 对于旧版本 (API 30 及以下)，蓝牙扫描通常需要位置权限
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            // 注意：BLUETOOTH 和 BLUETOOTH_ADMIN 是普通权限，通常安装时授予，不需要运行时请求
        }

        return permissionsNeeded
    }

    /**
     * 处理蓝牙权限请求的结果。
     * 根据用户授予或拒绝的权限，更新内部状态并显示相应的 Toast 消息。
     * @param permissions 包含权限名称及其授予状态的 Map。
     */
    private fun handleBluetoothPermissionResults(permissions: Map<String, Boolean>) {
        val scanGranted = permissions.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false)
        val connectGranted = permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)
        // 位置权限的授予状态，如果未请求（如 API 31+ 且不需要位置），则默认为 true
        val fineLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, true)

        updateBluetoothPermissionStates(scanGranted, connectGranted, fineLocationGranted)

        if (scanGranted && connectGranted && fineLocationGranted) { // 检查所有必要权限
            showToast("蓝牙权限已授予")
        } else {
            showToast("蓝牙权限未完全授予，部分功能可能无法使用", Toast.LENGTH_LONG)
        }
    }

    /**
     * 更新蓝牙权限的内部状态。
     * @param scanGranted 蓝牙扫描权限是否已授予。
     * @param connectGranted 蓝牙连接权限是否已授予。
     * @param fineLocationGranted 精确位置权限是否已授予。
     */
    private fun updateBluetoothPermissionStates(scanGranted: Boolean, connectGranted: Boolean, fineLocationGranted: Boolean) {
        _hasBluetoothScanPermission.value = scanGranted
        _hasBluetoothConnectPermission.value = connectGranted
        _hasFineLocationPermission.value = fineLocationGranted
    }

    /**
     * 检查单个特定权限是否已授予。
     * @param permission 要检查的权限字符串。
     * @return 如果权限已授予则返回 `true`，否则返回 `false`。
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 显示一个 Toast 消息。
     * @param message 要显示的消息字符串。
     * @param duration Toast 的持续时间（默认为 `Toast.LENGTH_SHORT`）。
     */
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
}