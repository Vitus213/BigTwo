package bigtwo.app                       // 应用根包
import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import bigtwo.app.ui.GameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GameScreen() }
    }
}
