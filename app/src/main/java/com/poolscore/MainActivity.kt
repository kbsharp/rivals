package com.poolscore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.poolscore.ui.navigation.PoolScoreNavHost
import com.poolscore.ui.theme.PoolScoreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoolScoreTheme {
                PoolScoreNavHost()
            }
        }
    }
}
