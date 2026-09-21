package com.kevinbevan.rivals.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(val playerName: String = "")

class HomeViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = authRepository.authState
        .map { HomeUiState(playerName = it?.displayName.orEmpty()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(playerName = authRepository.currentUser?.displayName.orEmpty()),
        )

    /** Navigation back to sign-in follows from the auth state change. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer().authRepository) }
        }
    }
}
