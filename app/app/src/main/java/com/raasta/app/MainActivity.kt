package com.raasta.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.raasta.app.auth.DemoSessionStore
import com.raasta.app.map.configureMaps
import com.raasta.app.ui.theme.RaastaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark-first, so system bar icons stay light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        configureMaps(applicationContext)
        val sessionStore = DemoSessionStore(applicationContext)
        setContent {
            RaastaTheme {
                RaastaApp(sessionStore)
            }
        }
    }
}
