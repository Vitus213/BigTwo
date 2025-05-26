package bigtwo.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import bigtwo.app.bluetooth.BluetoothPermissionManager
import bigtwo.app.network.BluetoothClient
import bigtwo.app.network.BluetoothServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import android.location.LocationManager // <--- 新增这行导入！
import androidx.compose.foundation.lazy.rememberLazyListState

@SuppressLint("MissingPermission")
@Composable
fun BluetoothTestScreen(bluetoothPermissionManager: BluetoothPermissionManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 观察权限管理器中的权限状态
    val hasBluetoothConnectPermission by bluetoothPermissionManager.hasBluetoothConnectPermission
    val hasBluetoothScanPermission by bluetoothPermissionManager.hasBluetoothScanPermission // 对于 API < 31，此表示位置权限
    val hasFineLocationPermission by bluetoothPermissionManager.hasFineLocationPermission

    // 蓝牙状态 (仍然是本地状态，不属于权限管理器管理)
    var isBluetoothEnabled by remember { mutableStateOf(false) }
    var isDiscoverable by remember { mutableStateOf(false) } // 服务端是否可被发现

    // <--- 新增：位置信息服务开启状态 --->
    var isLocationServiceEnabled by remember { mutableStateOf(false) }

    // 客户端/服务端实例
    val client = remember { BluetoothClient(context) }
    val server = remember { BluetoothServer(context) }

    // UI 状态
    var serverMessage by remember { mutableStateOf("") }
    var clientMessage by remember { mutableStateOf("") }
    val receivedMessages = remember { mutableStateListOf<String>() }

    // 服务端状态
    var isServerStarted by remember { mutableStateOf(false) }
    // 客户端连接状态：键是客户端ID (MAC地址)，值是客户端名称
    val connectedClients = remember { mutableStateMapOf<String, String>() }

    // 客户端状态
    var isClientConnectedToServer by remember { mutableStateOf(false) } // 客户端是否连接到服务端
    var selectedBluetoothDevice by remember { mutableStateOf<BluetoothDevice?>(null) } // 新增：选中的设备

    // 蓝牙设备发现相关
    val bluetoothAdapter: BluetoothAdapter? by remember {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mutableStateOf(bluetoothManager.adapter)
    }
    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
    var isScanning by remember { mutableStateOf(false) }

    // 蓝牙启用请求启动器 (仍然在此，不属于权限管理)
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
        if (!isBluetoothEnabled) {
            Toast.makeText(context, "蓝牙未启用，无法进行通信。", Toast.LENGTH_SHORT).show()
        }
    }

    // 应用程序启动时更新蓝牙启用状态
    LaunchedEffect(Unit) { // 使用 Unit 以便在 Composable 首次进入 Composition 时运行
        isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
    }

    // <--- 新增：用于更新位置信息服务状态的 LaunchedEffect --->
    LaunchedEffect(Unit) { // 每次 Composable 进入组合时检查一次
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        isLocationServiceEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }



    // 确保蓝牙已启用 (现在使用权限管理器的连接权限状态)
    LaunchedEffect(isBluetoothEnabled, hasBluetoothConnectPermission) {
        if (!isBluetoothEnabled && hasBluetoothConnectPermission) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }

    // 确保服务端可被发现
    LaunchedEffect(isDiscoverable, isServerStarted, hasBluetoothConnectPermission) { // 使用连接权限作为广告权限的代理
        if (!isDiscoverable && isServerStarted && bluetoothAdapter?.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) // 可发现 5 分钟
            }
            try {
                // 如果 API 31+ 且没有 BLUETOOTH_ADVERTISE 权限（此处用连接权限作为通用检查），可能会失败
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission) {
                    Log.w("BluetoothTestScreen", "BLUETOOTH_ADVERTISE (implied) 权限未授予，无法请求可发现性。")
                } else {
                    context.startActivity(discoverableIntent)
                    isDiscoverable = true
                    Log.d("BluetoothTestScreen", "请求蓝牙可发现性。")
                }
            } catch (e: Exception) {
                Log.e("BluetoothTestScreen", "无法请求蓝牙可发现性: ${e.message}", e)
                Toast.makeText(context, "无法请求蓝牙可发现性，请手动打开。", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 蓝牙设备发现的监听 (从 BluetoothServer 复制过来，因为客户端也需要发现)
    LaunchedEffect(server) { // 使用 server 实例来收集发现的设备
        server.discoveredDevices.collect { device ->
            // 避免重复添加，并保持设备最新信息
            val existingDevice = discoveredDevices.find { it.address == device.address }
            if (existingDevice == null) {
                discoveredDevices.add(device)
            } else {
                val index = discoveredDevices.indexOf(existingDevice)
                if (index != -1) {
                    discoveredDevices[index] = device // 更新设备信息
                }
            }
        }
    }
    LaunchedEffect(server.isDiscovering) {
        isScanning = server.isDiscovering.value
    }

    // !!! 新增：订阅客户端的 messageReceived SharedFlow !!!
    LaunchedEffect(client) {
        client.messageReceived.collect { receivedMessage ->
            val cleanedMessage = receivedMessage.trimEnd() // !!! 新增：移除末尾的换行符或空格 !!!
            receivedMessages.add("客户端收到: $cleanedMessage") // 使用清理后的消息
            Log.d("UI_UPDATE_CLIENT", "客户端UI已添加消息: $cleanedMessage, 当前列表大小: ${receivedMessages.size}")
        }
    }

    // 创建一个 ScrollState 实例
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // --- 权限和蓝牙状态显示 ---
            Text("权限状态:")
            Text("CONNECT: $hasBluetoothConnectPermission")
            // 根据API版本显示不同的扫描权限状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text("SCAN (BLUETOOTH_SCAN): $hasBluetoothScanPermission")
                // 显示广告权限（通过连接权限代理）
                Text("ADVERTISE: ${bluetoothPermissionManager.hasBluetoothConnectPermission.value}")
            } else {
                Text("SCAN (LOCATION - Fine): $hasFineLocationPermission")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("蓝牙已启用: $isBluetoothEnabled")
            // <--- 新增：显示位置信息服务状态 --->
            Text("位置信息服务已启用: $isLocationServiceEnabled")
            Spacer(modifier = Modifier.height(16.dp))

            // --- 启动服务端按钮 ---
            Button(
                onClick = {
                    if (hasBluetoothConnectPermission && isBluetoothEnabled) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                server.startServer(
                                    onMessageReceived = { received, senderId ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            val clientName = connectedClients[senderId] ?: senderId
                                            val cleanedReceived = received.trimEnd() // !!! 新增：移除末尾的换行符或空格 !!!
                                            receivedMessages.add("收到来自 $clientName: $cleanedReceived") // 使用清理后的消息
                                            Log.d("UI_UPDATE_SERVER", "服务端UI已添加消息: 收到来自 $clientName: $cleanedReceived, 当前列表大小: ${receivedMessages.size}")
                                        }
                                    },
                                    onClientConnected = { clientId, clientName ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            connectedClients[clientId] = clientName
                                            Toast.makeText(context, "客户端 $clientName 已连接", Toast.LENGTH_SHORT).show()
                                            Log.d("BluetoothTestScreen", "服务端收到客户端 $clientName 连接通知 (UI)")
                                        }
                                    },
                                    onClientDisconnected = { disconnectedClientId ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            val disconnectedClientName = connectedClients.remove(disconnectedClientId)
                                            Toast.makeText(context, "客户端 ${disconnectedClientName ?: disconnectedClientId} 已断开", Toast.LENGTH_SHORT).show()
                                            Log.d("BluetoothTestScreen", "服务端收到客户端 ${disconnectedClientName ?: disconnectedClientId} 断开连接通知 (UI)")
                                        }
                                    }
                                )
                                coroutineScope.launch(Dispatchers.Main) {
                                    isServerStarted = true
                                    Toast.makeText(context, "服务端正在尝试启动...", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("BluetoothTestScreen", "启动服务端协程失败: ${e.message}", e)
                                coroutineScope.launch(Dispatchers.Main) {
                                    isServerStarted = false
                                    Toast.makeText(context, "服务端启动失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "启动服务端需要蓝牙连接权限且蓝牙已启用。", Toast.LENGTH_LONG).show()
                        // 引导用户打开权限设置
                        val activity = context as? Activity
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED &&
                            !activity.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
                        ) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                            Toast.makeText(context, "请在应用设置中手动授予蓝牙连接权限。", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isServerStarted
            ) {
                Text(text = if (isServerStarted) "服务端已启动" else "启动服务端")
            }

            // --- 停止服务端按钮 ---
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        server.close()
                        coroutineScope.launch(Dispatchers.Main) {
                            isServerStarted = false
                            connectedClients.clear()
                            Toast.makeText(context, "服务器已停止", Toast.LENGTH_SHORT).show()
                            Log.d("BluetoothTestScreen", "服务端已手动停止")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isServerStarted
            ) {
                Text(text = "停止服务端")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- 客户端连接到服务端 ---
            Text("客户端状态:")

            // 蓝牙设备扫描和列表
            Button(
                onClick = {
                    // 更新一下位置服务状态，确保在点击时是最新值
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    isLocationServiceEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                    val requiredScanPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        hasBluetoothScanPermission // API 31+ needs BLUETOOTH_SCAN
                    } else {
                        hasFineLocationPermission // API < 31 needs ACCESS_FINE_LOCATION (or Coarse)
                    }

                    // --- 检查蓝牙扫描所需的三个条件：权限、蓝牙启用、位置信息服务启用 ---
                    if (requiredScanPermissionGranted && isBluetoothEnabled && isLocationServiceEnabled) {
                        discoveredDevices.clear()
                        coroutineScope.launch(Dispatchers.IO) {
                            server.startDiscovery()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "开始扫描蓝牙设备...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // 确定具体是哪个条件不满足，并给出相应提示
                        var message = ""
                        var shouldGuideToAppSettingsForPermission = false // 引导到应用权限设置
                        var shouldGuideToLocationServiceSettings = false // 引导到系统位置信息服务设置

                        if (!isBluetoothEnabled) {
                            message = "蓝牙未启用，无法进行扫描。"
                        } else if (!requiredScanPermissionGranted) {
                            message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                "开始扫描需要 BLUETOOTH_SCAN 权限。"
                            } else {
                                "开始扫描需要位置权限（精确位置）。"
                            }
                            // 检查是否是被“永久拒绝”了某个扫描权限
                            val permissionsToCheckForPermanentDenial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                listOf(Manifest.permission.BLUETOOTH_SCAN)
                            } else {
                                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                            }

                            val activity = context as? Activity
                            if (activity != null) {
                                for (permission in permissionsToCheckForPermanentDenial) {
                                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_DENIED &&
                                        !activity.shouldShowRequestPermissionRationale(permission)) {
                                        shouldGuideToAppSettingsForPermission = true // 标记需要引导到应用设置
                                        break
                                    }
                                }
                            }
                        } else if (!isLocationServiceEnabled) { // <--- 检查位置信息服务是否开启
                            message = "请开启设备的位置信息服务（GPS）以进行蓝牙扫描。"
                            shouldGuideToLocationServiceSettings = true // 标记需要引导到系统位置设置
                        } else {
                            message = "未知错误，无法开始扫描。" // 兜底
                        }

                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        Log.d("BluetoothTestScreen", "缺失蓝牙扫描所需条件。消息: $message")

                        if (shouldGuideToAppSettingsForPermission) {
                            // 引导到应用自身的权限设置页面
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                            Toast.makeText(context, "请在应用设置中手动授予蓝牙扫描所需权限。", Toast.LENGTH_LONG).show()
                            Log.d("BluetoothTestScreen", "已尝试引导用户到应用设置界面。")
                        } else if (shouldGuideToLocationServiceSettings) { // <--- 处理位置信息服务未开启的情况
                            // 引导到系统位置信息设置页面
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            context.startActivity(intent)
                            Toast.makeText(context, "请手动开启设备的位置信息服务（GPS）。", Toast.LENGTH_LONG).show()
                            Log.d("BluetoothTestScreen", "已尝试引导用户到系统位置设置界面。")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning && !isClientConnectedToServer
            ) {
                Text(text = if (isScanning) "正在扫描..." else "扫描设备")
            }

            // 停止扫描按钮
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        server.stopDiscovery()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "停止扫描设备。", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isScanning
            ) {
                Text("停止扫描")
            }

            // 已配对设备
            Text("已配对设备:")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                bluetoothAdapter?.bondedDevices?.let { pairedDevices ->
                    items(pairedDevices.toList()) { device ->
                        PairedDeviceItem(device = device, onDeviceSelected = { selectedDevice ->
                            selectedBluetoothDevice = selectedDevice
                            Toast.makeText(context, "选中设备: ${selectedDevice.name ?: selectedDevice.address}", Toast.LENGTH_SHORT).show()
                        })
                    }
                } ?: item { Text("无已配对设备") }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("已发现设备:")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                items(discoveredDevices) { device ->
                    DiscoveredDeviceItem(device = device, onDeviceSelected = { selectedDevice ->
                        selectedBluetoothDevice = selectedDevice
                        coroutineScope.launch(Dispatchers.IO) {
                            server.stopDiscovery()
                            withContext(Dispatchers.Main) {
                                isScanning = false
                            }
                        }
                        Toast.makeText(context, "选中设备: ${selectedDevice.name ?: selectedDevice.address}", Toast.LENGTH_SHORT).show()
                    })
                }
                if (discoveredDevices.isEmpty() && isScanning) {
                    item { Text("正在扫描...") }
                } else if (discoveredDevices.isEmpty() && !isScanning) {
                    item { Text("无已发现设备") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text("当前选中的设备: ${selectedBluetoothDevice?.name ?: selectedBluetoothDevice?.address ?: "无"}")

            Button(
                onClick = {
                    if (selectedBluetoothDevice != null && hasBluetoothConnectPermission && isBluetoothEnabled) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                client.connectToServer(
                                    device = selectedBluetoothDevice!!,
//                                    onMessageReceived = { received ->
//                                        coroutineScope.launch(Dispatchers.Main) {
//                                            receivedMessages.add("客户端收到: $received")
//                                        }
//                                    },
                                    onConnected = {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            isClientConnectedToServer = true
                                            Toast.makeText(context, "客户端已连接到服务端", Toast.LENGTH_SHORT).show()
                                            Log.d("BluetoothTestScreen", "客户端连接成功 (UI)")
                                        }
                                    },
                                    onDisconnected = {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            isClientConnectedToServer = false
                                            Toast.makeText(context, "客户端已断开与服务端的连接", Toast.LENGTH_SHORT).show()
                                            Log.d("BluetoothTestScreen", "客户端断开连接 (UI)")
                                        }
                                    },
                                    onFailed = { message ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            isClientConnectedToServer = false
                                            Toast.makeText(context, "客户端连接失败: $message", Toast.LENGTH_LONG).show()
                                            Log.d("BluetoothTestScreen", "客户端连接失败 (UI): $message")
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("BluetoothTestScreen", "客户端连接协程失败: ${e.message}", e)
                                coroutineScope.launch(Dispatchers.Main) {
                                    isClientConnectedToServer = false
                                    Toast.makeText(context, "客户端连接发生异常: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "请确保蓝牙连接权限、蓝牙已启用且已选择设备。", Toast.LENGTH_SHORT).show()
                        val activity = context as? Activity
                        if (activity != null &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED &&
                            !activity.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
                        ) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                            Toast.makeText(context, "请在应用设置中手动授予蓝牙连接权限。", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isClientConnectedToServer && hasBluetoothConnectPermission && isBluetoothEnabled && selectedBluetoothDevice != null
            ) {
                Text(text = if (isClientConnectedToServer) "客户端已连接" else "连接选中设备")
            }

            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        client.close()
                        coroutineScope.launch(Dispatchers.Main) {
                            isClientConnectedToServer = false
                            Toast.makeText(context, "客户端已断开连接", Toast.LENGTH_SHORT).show()
                            Log.d("BluetoothTestScreen", "客户端已手动断开连接")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isClientConnectedToServer
            ) {
                Text(text = "断开客户端连接")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- 消息发送 ---
            // 移除 val currentMessageText = remember(...)
            // 直接使用 serverMessage 或 clientMessage 作为 OutlinedTextField 的值

            OutlinedTextField(
                value = if (isServerStarted) serverMessage else clientMessage, // 直接绑定到对应的状态
                onValueChange = {
                    if (isServerStarted) {
                        serverMessage = it // 更新服务端消息
                    } else {
                        clientMessage = it // 更新客户端消息
                    }
                },
                label = { Text("输入消息") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(
                    onClick = {
                        // 在这里，您已经正确地清空了 serverMessage 和 clientMessage
                        if (isServerStarted) {
                            if (connectedClients.isNotEmpty()) {
                                server.sendDataToAllClients(serverMessage)
                                serverMessage = "" // **这里会清空**
                            } else {
                                Toast.makeText(context, "没有客户端连接，无法发送数据。", Toast.LENGTH_SHORT).show()
                            }
                        } else if (isClientConnectedToServer) {
                            client.sendData(clientMessage)
                            clientMessage = "" // **这里会清空**
                        } else {
                            Toast.makeText(context, "请先启动或连接。", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = (isServerStarted && connectedClients.isNotEmpty()) || isClientConnectedToServer
                ) {
                    Text(text = if (isServerStarted) "服务端发送" else "客户端发送")
                }
            }


            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- 已连接客户端列表 (仅服务端模式显示) ---
            if (isServerStarted) {
                Text("已连接客户端 (${connectedClients.size}):")
                LazyColumn(modifier = Modifier.height(100.dp)) {
                    items(connectedClients.keys.toList()) { clientId ->
                        val clientName = connectedClients[clientId]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    Toast.makeText(context, "选中客户端: $clientName", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Text(text = "ID: $clientId, 名称: $clientName")
                        }
                        Divider()
                    }
                    if (connectedClients.isEmpty()) {
                        item {
                            Text("无客户端连接", modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(0.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- 接收到的消息显示 ---
            Text("接收到的消息:")
            val listState = rememberLazyListState() // 导入 androidx.compose.foundation.lazy.rememberLazyListState

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp), // 保持高度，但确保可以滚动
                state = listState // 将状态赋值给 LazyColumn
            ) {
                items(receivedMessages) { message ->
                    Text(message, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            // 自动滚动到底部
            LaunchedEffect(receivedMessages.size) {
                if (receivedMessages.isNotEmpty()) {
                    listState.animateScrollToItem(receivedMessages.lastIndex)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun PairedDeviceItem(device: BluetoothDevice, onDeviceSelected: (BluetoothDevice) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected(device) }
            .padding(vertical = 4.dp)
    ) {
        Text(text = "名称: ${device.name ?: "未知名称"}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "地址: ${device.address}", style = MaterialTheme.typography.bodySmall)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DiscoveredDeviceItem(device: BluetoothDevice, onDeviceSelected: (BluetoothDevice) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected(device) }
            .padding(vertical = 4.dp)
    ) {
        Text(text = "名称: ${device.name ?: "未知名称"}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "地址: ${device.address}", style = MaterialTheme.typography.bodySmall)
    }
}