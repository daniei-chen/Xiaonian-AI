package art.kaogong.xiaonian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import art.kaogong.xiaonian.data.Settings
import art.kaogong.xiaonian.ui.XiaonianApp
import art.kaogong.xiaonian.ui.theme.AccentTheme
import art.kaogong.xiaonian.ui.theme.LocalAccent
import art.kaogong.xiaonian.ui.theme.XiaonianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // 主题色放在 Theme 外层：XiaonianTheme 自己要从 CompositionLocal 读强调色
            var accent by mutableStateOf(AccentTheme.from(Settings.accentTheme(this)))

            CompositionLocalProvider(LocalAccent provides accent) {
                XiaonianTheme {
                    XiaonianApp(
                        onAccentChange = { picked ->
                            Settings.setAccentTheme(this, picked.id)
                            accent = picked
                        },
                    )
                }
            }
        }
    }
}
