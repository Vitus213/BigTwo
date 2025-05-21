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

class BluetoothPermissionManager(
    private val context: Context,
    private val activityResultRegistry: ActivityResultRegistry,
    private val lifecycleOwner: LifecycleOwner
) {
    private val _hasBluetoothScanPermission = mutableStateOf(false)
    private val _hasBluetoothConnectPermission = mutableStateOf(false)
    // 核心改动：添加位置权限的 Compose 状态
    private val _hasFineLocationPermission = mutableStateOf(false)

    val hasBluetoothScanPermission: State<Boolean> = _hasBluetoothScanPermission
    val hasBluetoothConnectPermission: State<Boolean> = _hasBluetoothConnectPermission
    // 核心改动：提供只读位置权限属性
    val hasFineLocationPermission: State<Boolean> = _hasFineLocationPermission

    private val requestBluetoothPermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activityResultRegistry.register(
            "bluetooth_permissions_request",
            lifecycleOwner,
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handleBluetoothPermissionResults(permissions)
        }

    fun manageBluetoothPermissions() {
        val permissionsToRequest = getMissingBluetoothPermissions()

        if (permissionsToRequest.isNotEmpty()) {
            requestBluetoothPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // 所有权限都已存在，更新状态为 true
            updateBluetoothPermissionStates(
                scanGranted = true,
                connectGranted = true,
                fineLocationGranted = true // 核心改动：更新位置权限状态
            )
            showToast("蓝牙权限已存在")
        }
    }

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
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
                // 如果需要广告功能，也检查此权限
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        } else {
            // 核心改动：对于旧版本，检查位置权限
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            // BLUETOOTH 和 BLUETOOTH_ADMIN 是普通权限，通常安装时授予，不需要运行时请求
        }

        return permissionsNeeded
    }

    private fun handleBluetoothPermissionResults(permissions: Map<String, Boolean>) {
        val scanGranted = permissions.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false)
        val connectGranted = permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)
        // 核心改动：处理位置权限的结果
        val fineLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, true) // 默认值设为 true，因为 API 31+ 可能不请求

        updateBluetoothPermissionStates(scanGranted, connectGranted, fineLocationGranted)

        if (scanGranted && connectGranted && fineLocationGranted) { // 核心改动：所有权限都需授予
            showToast("蓝牙权限已授予")
        } else {
            showToast("蓝牙权限未完全授予，部分功能可能无法使用", Toast.LENGTH_LONG)
        }
    }

    private fun updateBluetoothPermissionStates(scanGranted: Boolean, connectGranted: Boolean, fineLocationGranted: Boolean) {
        _hasBluetoothScanPermission.value = scanGranted
        _hasBluetoothConnectPermission.value = connectGranted
        _hasFineLocationPermission.value = fineLocationGranted // 核心改动：更新位置权限状态
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
}