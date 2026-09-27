package com.kevinbevan.rivals.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.kevinbevan.rivals.data.MatchDefaults
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import java.io.File

/**
 * A screen under test, in the app's theme with its own remembered match settings: a test
 * never reads or writes the phone's, and one test's choices can't leak into the next.
 */
@Composable
fun Fixture(content: @Composable () -> Unit) {
    val defaults = remember { MatchDefaults() }
    CompositionLocalProvider(LocalMatchDefaults provides defaults) {
        RivalsTheme { content() }
    }
}

/** Where [Screenshots] and [FlowScreenshots] write their PNGs: `app/build/screenshots`. */
val screenshotDir: File get() = File("build/screenshots").apply { mkdirs() }
