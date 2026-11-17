package art.kaogong.xiaonian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import art.kaogong.xiaonian.data.Settings
import art.kaogong.xiaonian.ui.XiaonianApp
import art.kaogong.xiaonian.ui.theme.AccentTheme
import art.kaogong.xiaonian.ui.theme.LocalAccent
import art.kaogong.xiaonian.ui.theme.LocalPalette
import art.kaogong.xiaonian.ui.theme.Palette
import art.kaogong.xiaonian.ui.theme.XiaonianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // 主题放在 XiaonianTheme 外层：它自己要从 CompositionLocal 读配色
            var accent by mutableStateOf(AccentTheme.from(Settings.accentTheme(this)))
            var darkSetting by mutableStateOf(Settings.darkMode(this))

            val systemDark = isSystemInDarkTheme()
            val dark = when (darkSetting) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            CompositionLocalProvider(
                LocalAccent provides accent,
                LocalPalette provides if (dark) Palette.Dark else Palette.Light,
            ) {
                XiaonianTheme {
                    XiaonianApp(
                        onAccentChange = { picked ->
                            Settings.setAccentTheme(this, picked.id)
                            accent = picked
                        },
                        onDarkModeChange = { mode ->
                            Settings.setDarkMode(this, mode)
                            darkSetting = mode
                        },
                    )
                }
            }
        }
    }
}
