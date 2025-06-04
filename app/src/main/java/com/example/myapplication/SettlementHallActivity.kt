package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.domain.GameManager
import com.example.myapplication.domain.Player
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.io.Serializable

class SettlementHallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val gameResult = intent.getSerializableExtra("result_data") as? GameManager.GameResult

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    gameResult?.let {
                        SettlementHallScreen(gameResult = it)
                    } ?: run {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("未找到结算数据", fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettlementHallScreen(gameResult: GameManager.GameResult) {
    // 将玩家数据转换为列表并按最终得分排序
    val sortedPlayers = gameResult.playerFinalScores.entries
        .sortedByDescending { it.value }
        .mapIndexed { index, entry ->
            val player = entry.key
            val baseScore = gameResult.playerBaseScores[player] ?: 0
            PlayerScoreData(
                rank = index + 1,
                player = player,
                baseScore = baseScore,
                finalScore = entry.value,
                isWinner = player == gameResult.winner
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = "游戏结算",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        // 获胜者信息
        WinnerSection(winner = gameResult.winner)

        Spacer(modifier = Modifier.height(24.dp))

        // 分数排名列表
        ScoreRankingList(players = sortedPlayers)

        // 底部按钮
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { /* 返回或重新开始 */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("返回大厅", fontSize = 18.sp)
        }
    }
}

@Composable
fun WinnerSection(winner: Player) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFD700).copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🏆 获胜者 🏆",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFA000)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = winner.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ScoreRankingList(players: List<PlayerScoreData>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 表头
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "排名",
                    modifier = Modifier.weight(0.8f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "玩家",
                    modifier = Modifier.weight(2f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "基础分",
                    modifier = Modifier.weight(1.5f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "最终分",
                    modifier = Modifier.weight(1.5f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }
        }

        // 玩家分数项
        itemsIndexed(players) { _, playerData ->
            PlayerScoreItem(playerData = playerData)
        }
    }
}

@Composable
fun PlayerScoreItem(playerData: PlayerScoreData) {
    val backgroundColor = if (playerData.isWinner) {
        Brush.linearGradient(
            colors = listOf(Color(0xFFFFF176), Color(0xFFFFD54F))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(if (playerData.isWinner) 8.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (playerData.isWinner) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (playerData.isWinner) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .wrapContentSize(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                val rankColor = when (playerData.rank) {
                    1 -> Color(0xFFFFD700)
                    2 -> Color(0xFFC0C0C0)
                    3 -> Color(0xFFCD7F32)
                    else -> MaterialTheme.colorScheme.primary
                }

                Text(
                    text = when (playerData.rank) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "#${playerData.rank}"
                    },
                    fontSize = if (playerData.rank <= 3) 24.sp else 18.sp,
                    color = rankColor,
                    fontWeight = FontWeight.Bold
                )
            }

            // 玩家名称
            Text(
                text = playerData.player.name,
                modifier = Modifier.weight(2f),
                fontSize = 18.sp,
                fontWeight = if (playerData.isWinner) FontWeight.Bold else FontWeight.Normal
            )

            // 基础分
            Text(
                text = "${playerData.baseScore}",
                modifier = Modifier
                    .weight(1.5f)
                    .wrapContentSize(Alignment.CenterEnd),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 最终分
            Text(
                text = "${playerData.finalScore}",
                modifier = Modifier
                    .weight(1.5f)
                    .wrapContentSize(Alignment.CenterEnd),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (playerData.finalScore >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F)
            )
        }
    }
}

// 玩家分数数据类
data class PlayerScoreData(
    val rank: Int,
    val player: Player,
    val baseScore: Int,
    val finalScore: Int,
    val isWinner: Boolean
)

// 确保Player类是可序列化的
data class Player(
    val id: String,
    val name: String
) : Serializable

// 确保GameResult可序列化
data class GameResult(
    val winner: Player,
    val playerBaseScores: Map<Player, Int>,
    val playerFinalScores: Map<Player, Int>
) : Serializable