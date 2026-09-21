package com.kevinbevan.rivals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kevinbevan.rivals.ui.navigation.RivalsNavHost
import com.kevinbevan.rivals.ui.theme.RivalsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RivalsTheme {
                RivalsNavHost()
            }
        }
    }
}
