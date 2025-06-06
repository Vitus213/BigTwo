package com.example.myapplication

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

class RuleSelectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置为横屏显示
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RuleSelectionScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun RuleSelectionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "请选择游戏规则", modifier = Modifier.padding(bottom = 32.dp))

        Button(
            onClick = {
                // 跳转到 LobbyActivity，并传递南方规则
                val intent = Intent(context, LobbyActivity::class.java).apply {
                    putExtra("RULE_TYPE", "SOUTHERN")
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .height(80.dp)
                .width(200.dp)
        ) {
            Text(text = "南方规则")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // 跳转到 LobbyActivity，并传递北方规则
                val intent = Intent(context, LobbyActivity::class.java).apply {
                    putExtra("RULE_TYPE", "NORTHERN")
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .height(80.dp)
                .width(200.dp)
        ) {
            Text(text = "北方规则")
        }
    }
}