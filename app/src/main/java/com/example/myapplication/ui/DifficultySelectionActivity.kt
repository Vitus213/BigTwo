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

class DifficultySelectionActivity : ComponentActivity() {

    private var selectedRuleType: String? = null
    private var battleMode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()

        // 获取传递过来的规则类型和对战模式
        selectedRuleType = intent.getStringExtra("RULE_TYPE")
        battleMode = intent.getStringExtra("BATTLE_MODE")

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DifficultySelectionScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        selectedRuleType = selectedRuleType,
                        battleMode = battleMode
                    )
                }
            }
        }
    }
}

@Composable
fun DifficultySelectionScreen(
    modifier: Modifier = Modifier,
    selectedRuleType: String?,
    battleMode: String?
) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "请选择人机难度", modifier = Modifier.padding(bottom = 32.dp))

        Button(
            onClick = {
                // 跳转到 GameActivity，传递规则类型、对战模式和难度
                val intent = Intent(context, GameActivity::class.java).apply {
                    putExtra("RULE_TYPE", selectedRuleType)
                    putExtra("BATTLE_MODE", battleMode) // 仍为 HUMAN_AI
                    putExtra("DIFFICULTY", "NORMAL") // 新增难度参数
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .height(80.dp)
                .width(200.dp)
        ) {
            Text(text = "普通人机")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // 跳转到 GameActivity，传递规则类型、对战模式和难度
                val intent = Intent(context, GameActivity::class.java).apply {
                    putExtra("RULE_TYPE", selectedRuleType)
                    putExtra("BATTLE_MODE", battleMode) // 仍为 HUMAN_AI
                    putExtra("DIFFICULTY", "HARD") // 新增难度参数
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .height(80.dp)
                .width(200.dp)
        ) {
            Text(text = "困难人机")
        }
    }
}