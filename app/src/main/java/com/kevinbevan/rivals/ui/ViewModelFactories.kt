package com.kevinbevan.rivals.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.kevinbevan.rivals.AppContainer
import com.kevinbevan.rivals.RivalsApp

/** Use inside `viewModelFactory { initializer { ... } }` to reach the app's dependencies. */
fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as RivalsApp).container
