package app.ownplay.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.ownplay.player.ui.OwnPlayApp
import app.ownplay.player.ui.theme.OwnPlayTheme

class MainActivity : ComponentActivity() {
    private lateinit var runtime: OwnPlayAppRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = OwnPlayAppRuntime(applicationContext)
        enableEdgeToEdge()
        setContent {
            OwnPlayTheme {
                OwnPlayApp(runtime)
            }
        }
    }

    override fun onDestroy() {
        runtime.close()
        super.onDestroy()
    }
}
