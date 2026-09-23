package com.kevinbevan.rivals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.kevinbevan.rivals.ui.LocalMatchDefaults
import com.kevinbevan.rivals.ui.navigation.RivalsNavHost
import com.kevinbevan.rivals.ui.theme.RivalsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
}
