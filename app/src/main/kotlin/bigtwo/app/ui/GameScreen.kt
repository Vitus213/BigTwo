package bigtwo.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun GameScreen() {
    /*
    这是游戏的主界面，使用 Jetpack Compose 构建 UI。
    需要完善
     */
    var count by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween, // 顶部文本 + 底部按钮
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "You've clicked $count times")

        Button(onClick = { count++ }) {
            Text("Click me")
        }
    }
}
