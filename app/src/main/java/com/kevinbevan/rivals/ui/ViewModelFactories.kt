package com.kevinbevan.rivals.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.kevinbevan.rivals.AppContainer
import com.kevinbevan.rivals.RivalsApp
import com.kevinbevan.rivals.data.MatchDefaults

/** Use inside `viewModelFactory { initializer { ... } }` to reach the app's dependencies. */
fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as RivalsApp).container

/**
 * The phone's remembered match settings. `MainActivity` provides the app's; previews and
 * Compose tests fall back to an in-memory copy, so a render never writes to the phone.
 */
val LocalMatchDefaults = staticCompositionLocalOf { MatchDefaults() }

/** The remembered settings a dialog opens on. */
@Composable
fun rememberMatchDefaults(): MatchDefaults = LocalMatchDefaults.current
