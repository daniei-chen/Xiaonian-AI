package art.kaogong.xiaonian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import art.kaogong.xiaonian.ui.XiaonianApp
import art.kaogong.xiaonian.ui.theme.XiaonianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiaonianTheme {
                XiaonianApp()
            }
        }
    }
}
