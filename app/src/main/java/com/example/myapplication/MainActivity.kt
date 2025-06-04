package com.example.myapplication

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.bluetooth.BluetoothPermissionManager
class MainActivity : ComponentActivity() {

    private lateinit var bluetoothPermissionManager: BluetoothPermissionManager
    override fun onCreate(savedInstanceState: Bundle?) {

        bluetoothPermissionManager = BluetoothPermissionManager(
            this,                       // Activity 上下文
            activityResultRegistry,     // 用于权限请求回调
            this                        // LifecycleOwner
        )
        bluetoothPermissionManager.manageBluetoothPermissions()
        // 设置为横屏显示
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 居中显示按钮的布局
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        val context = LocalContext.current
                        Button(onClick = {
                            val intent = Intent(context, LobbyActivity::class.java)
                            context.startActivity(intent)}) {
                            Text("开始游戏")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = {}) {
                Text("开始游戏")
            }
        }
    }
}

