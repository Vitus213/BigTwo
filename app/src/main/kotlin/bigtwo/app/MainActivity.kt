package bigtwo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import bigtwo.app.bluetooth.BluetoothPermissionManager
import bigtwo.app.ui.BluetoothTestScreen // 确保导入您的 UI Composable

class MainActivity : ComponentActivity() {

    // 声明蓝牙权限管理器的实例
    private lateinit var bluetoothPermissionManager: BluetoothPermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化蓝牙权限管理器
        // 传递当前 Activity 的上下文、ActivityResultRegistry 和生命周期所有者
        bluetoothPermissionManager = BluetoothPermissionManager(
            this, // Activity context
            activityResultRegistry, // ComponentActivity 提供的注册器
            this // MainActivity 实现了 LifecycleOwner 接口
        )

        // 调用管理器的方法来处理蓝牙权限
        // 这将检查并请求所有必要的蓝牙和位置权限
        bluetoothPermissionManager.manageBluetoothPermissions()

        // 设置 Compose UI 内容
        setContent {
            // 从蓝牙权限管理器获取权限状态，并传递给 UI
            BluetoothTestScreen(
                hasBluetoothScanPermission = bluetoothPermissionManager.hasBluetoothScanPermission.value,
                hasBluetoothConnectPermission = bluetoothPermissionManager.hasBluetoothConnectPermission.value
                // 如果您的 BluetoothTestScreen 还需要位置权限的状态，可以也传递过去
                // hasFineLocationPermission = bluetoothPermissionManager.hasFineLocationPermission.value
            )
        }
    }
}