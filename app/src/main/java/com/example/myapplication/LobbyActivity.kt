package com.example.myapplication

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.foundation.layout.height

class LobbyActivity : ComponentActivity() {

    private var selectedRuleType: String? = null // 用于存储选择的规则类型

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()

        // 获取从 RuleSelectionActivity 传递过来的规则类型
        selectedRuleType = intent.getStringExtra("RULE_TYPE")
        println("LobbyActivity 接收到的规则类型: $selectedRuleType")

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LobbyScreenContent(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        // 将点击事件和规则类型传递给 Composable
                        onHumanAIClick = {
                            val intent = Intent(this, GameActivity::class.java).apply { // 跳转到 GameActivity
                                putExtra("RULE_TYPE", selectedRuleType) // 传递规则类型
                                putExtra("BATTLE_MODE", "HUMAN_AI") // 传递对战模式
                            }
                            startActivity(intent)
                        },
                        onOnlineBattleClick = {
                            val intent = Intent(this, OnlineRoomActivity::class.java).apply {
                                putExtra("RULE_TYPE", selectedRuleType) // 传递规则类型
                                putExtra("BATTLE_MODE", "ONLINE") // 传递对战模式
                            }
                            startActivity(intent)
                        },
                        currentRule = selectedRuleType // 显示当前选择的规则
                    )
                }
            }
        }
    }
}

@Composable
fun LobbyScreenContent(
    modifier: Modifier = Modifier,
    onHumanAIClick: () -> Unit,
    onOnlineBattleClick: () -> Unit,
    currentRule: String? // 接收当前选择的规则
) {
    println("大厅界面")
    val context = LocalContext.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 显示当前选择的规则
        Text(text = "当前规则: ${currentRule ?: "未选择"}", modifier = Modifier.padding(bottom = 32.dp))

        // 移除原有的南方规则和北方规则按钮，它们现在在 RuleSelectionActivity 中
        /*
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { /* 后续添加南方规则逻辑 */ },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "南方规则")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { /* 后续添加北方规则逻辑 */ },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "北方规则")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        */

        // 第二行按钮：人机对局和联网对局
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onHumanAIClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "人机对局")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onOnlineBattleClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "联网对局")
            }
        }
    }
}