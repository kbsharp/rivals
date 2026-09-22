package com.kevinbevan.rivals.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.RivalryRepository
import com.kevinbevan.rivals.model.Invite
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.InviteRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteUiState(
    val loading: Boolean = true,
    /** `null` once loaded: used, withdrawn or mistyped. */
    val invite: Invite? = null,
    val signedIn: Boolean = false,
    /** Opened by the person who made it. */
    val own: Boolean = false,
    val accepting: Boolean = false,
    val error: String? = null,
    /** Accepted; open this rivalry. */
    val acceptedRivalryId: String? = null,
)

/** A share link's invite, opened from the link or by typing its code. */
class InviteViewModel(
    private val code: String,
    private val authRepository: AuthRepository,
    private val rivalryRepository: RivalryRepository,
) : ViewModel() {

    private data class Local(
        val loading: Boolean = true,
        val invite: Invite? = null,
        val accepting: Boolean = false,
        val error: String? = null,
        val acceptedRivalryId: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<InviteUiState> = combine(authRepository.authState, local) { user, local ->
        InviteUiState(
            loading = local.loading,
            invite = local.invite,
            signedIn = user != null,
            own = user != null && local.invite?.from == user.uid,
            accepting = local.accepting,
            error = local.error,
            acceptedRivalryId = local.acceptedRivalryId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InviteUiState())

    init {
        load()
    }

    fun load() {
        local.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val invite = rivalryRepository.loadInvite(code)
                local.update { it.copy(loading = false, invite = invite) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(loading = false, error = "Couldn't load the invite: ${messageFor(e)}") }
            }
        }
    }

    fun accept() {
        val me = authRepository.currentUser?.uid ?: return
        val invite = local.value.invite ?: return
        if (local.value.accepting) return
        local.update { it.copy(accepting = true, error = null) }
        viewModelScope.launch {
            try {
                val id = rivalryRepository.acceptInvite(me, invite)
                local.update { it.copy(accepting = false, acceptedRivalryId = id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(accepting = false, error = "Couldn't accept: ${messageFor(e)}") }
            }
        }
    }

    fun onAcceptedHandled() = local.update { it.copy(acceptedRivalryId = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                InviteViewModel(
                    code = createSavedStateHandle().toRoute<InviteRoute>().code,
                    authRepository = container.authRepository,
                    rivalryRepository = container.rivalryRepository,
                )
            }
        }
    }
}
