package com.swipedelete.zero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.swipedelete.zero.ui.navigation.AppNavigation
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SwipeDeleteZeroTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity host. All UI is Compose; navigation lives in
 * [AppNavigation]. Edge-to-edge with a pitch-black scrim keeps the OLED
 * aesthetic seamless behind the status/nav bars.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Installed before super.onCreate so the brand mark is the first
        // thing drawn, on the same warm charcoal the app itself uses.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SwipeDeleteZeroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SdzColor.Surface0,
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
