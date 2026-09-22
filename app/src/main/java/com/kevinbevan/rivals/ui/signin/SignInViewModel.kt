package com.kevinbevan.rivals.ui.signin

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.ui.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val inProgress: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

class SignInViewModel(
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun signIn(activityContext: Context) {
        if (_uiState.value.inProgress) return
        _uiState.update { it.copy(inProgress = true, error = null) }
        viewModelScope.launch {
            val error = try {
                val player = authRepository.signInWithGoogle(activityContext)
                // The profile and email index are what let a rival find you.
                playerRepository.upsert(player)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: GetCredentialCancellationException) {
                // User backed out of the account picker; not an error.
                _uiState.update { it.copy(inProgress = false) }
                return@launch
            } catch (e: Exception) {
                // Only a Firebase session needs undoing; a failed account picker leaves none.
                if (authRepository.currentUser != null) authRepository.signOut()
                signInErrorMessage(e)
            }
            _uiState.update { SignInUiState(error = error, signedIn = error == null) }
        }
    }

    fun onSignInHandled() = _uiState.update { it.copy(signedIn = false) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SignInViewModel(container.authRepository, container.playerRepository)
            }
        }
    }
}
