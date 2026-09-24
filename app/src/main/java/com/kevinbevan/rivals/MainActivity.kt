package com.kevinbevan.rivals

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kevinbevan.rivals.ui.LocalMatchDefaults
import com.kevinbevan.rivals.ui.navigation.RivalsNavHost
import com.kevinbevan.rivals.ui.theme.RivalsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // The first frame is usually ready before the rack has finished animating in; hold the
        // splash until it has. Before Android 12 the splash is still, so there's nothing to wait for.
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val until = SystemClock.uptimeMillis() + SPLASH_MILLIS
            splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() < until }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val defaults = (application as RivalsApp).container.matchDefaults
            CompositionLocalProvider(LocalMatchDefaults provides defaults) {
                RivalsTheme {
                    RivalsNavHost()
                }
            }
        }
    }

    private companion object {
        /** The length of splash_icon_animated, plus a beat to see it whole. */
        const val SPLASH_MILLIS = 900L
    }
}
