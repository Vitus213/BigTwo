package com.example.myapplication.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
    private val _hasFineLocationPermission = mutableStateOf(false) // 明确代表 ACCESS_FINE_LOCATION

    // 提供只读属性，供 UI 观察权限状态
    val hasBluetoothScanPermission: State<Boolean> = _hasBluetoothScanPermission
    val hasBluetoothConnectPermission: State<Boolean> = _hasBluetoothConnectPermission
    val hasFineLocationPermission: State<Boolean> = _hasFineLocationPermission // 明确暴露位置权限状态

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
            // 所有必要的权限都已存在，更新状态
            updateBluetoothPermissionStates(
                scanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    hasPermission(Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    // 对于旧版本，如果走到这里，说明细致位置权限可能已授予，或 BLUETOOTH/BLUETOOTH_ADMIN 已存在
                    // 蓝牙扫描功能需要细致位置权限，所以这里依赖其状态
                    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                connectGranted = hasPermission(Manifest.permission.BLUETOOTH_CONNECT), // API 31+
                fineLocationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            )
            showToast("所有蓝牙相关权限已存在")
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
            // 对于旧版本 (API 30 及以下)，蓝牙扫描强制需要位置权限
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
        // 明确获取位置权限的授予状态
        val fineLocationGranted =
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)


        updateBluetoothPermissionStates(scanGranted, connectGranted, fineLocationGranted)

        // 根据实际授予的权限给出提示
        val grantedPermissions = permissions.filterValues { it }.keys
        if (grantedPermissions.isEmpty()) {
            showToast("所有蓝牙相关权限均被拒绝", Toast.LENGTH_LONG)
        } else if (getMissingBluetoothPermissions().isEmpty()) { // 如果现在所有必需权限都已满足
            showToast("所有蓝牙相关权限已授予", Toast.LENGTH_SHORT)
        } else {
            showToast("部分蓝牙相关权限未授予，部分功能可能无法使用", Toast.LENGTH_LONG)
        }
    }

    /**
     * 更新蓝牙权限的内部状态。
     * @param scanGranted 蓝牙扫描权限（API 31+ 为 BLUETOOTH_SCAN，否则为旧版本中的扫描相关权限概念）是否已授予。
     * @param connectGranted 蓝牙连接权限是否已授予。
     * @param fineLocationGranted 精确位置权限（ACCESS_FINE_LOCATION）是否已授予。
     */
    private fun updateBluetoothPermissionStates(
        scanGranted: Boolean,
        connectGranted: Boolean,
        fineLocationGranted: Boolean
    ) {
        // 对于 API 31+ 设备，蓝牙扫描权限就是 BLUETOOTH_SCAN
        // 对于旧版本设备，蓝牙扫描功能依赖 ACCESS_FINE_LOCATION，所以这里将 _hasBluetoothScanPermission
        // 关联到 _hasFineLocationPermission 以便 BluetoothTestScreen 正确观察扫描条件
        _hasBluetoothScanPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scanGranted
        } else {
            fineLocationGranted // 旧版本扫描依赖精确位置
        }
        _hasBluetoothConnectPermission.value = connectGranted
        _hasFineLocationPermission.value = fineLocationGranted
    }

    /**
     * 检查单个特定权限是否已授予。
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
     * 显示一个 Toast 消息。
     * @param message 要显示的消息字符串。
     * @param duration Toast 的持续时间（默认为 `Toast.LENGTH_SHORT`）。
     */
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
}