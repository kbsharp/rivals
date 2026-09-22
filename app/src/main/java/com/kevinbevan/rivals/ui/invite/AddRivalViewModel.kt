package com.kevinbevan.rivals.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.auth.toPlayer
import com.kevinbevan.rivals.data.InviteResult
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.RivalryRepository
import com.kevinbevan.rivals.model.Player
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.InviteLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Looking someone up by email, or making a link for someone who isn't on Rivals yet. */
data class AddRivalUiState(
    val busy: Boolean = false,
    /** The last address searched for, and who has it (`null`: nobody on Rivals). */
    val searched: String? = null,
    val found: Player? = null,
    /** An invite was sent to [found]. */
    val invited: Boolean = false,
    val message: String? = null,
    /** Text for the share sheet, once a link's been made; cleared by [AddRivalViewModel.onShared]. */
    val shareText: String? = null,
)

class AddRivalViewModel(
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
    private val rivalryRepository: RivalryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRivalUiState())
    val uiState: StateFlow<AddRivalUiState> = _uiState.asStateFlow()

    fun search(email: String) = work {
        val address = email.trim()
        if ('@' !in address) {
            _uiState.update { it.copy(message = "Enter their full email address") }
            return@work
        }
        val found = playerRepository.findByEmail(address)
        if (found?.uid == authRepository.currentUser?.uid) {
            _uiState.update { it.copy(searched = null, found = null, message = "That's your own address") }
            return@work
        }
        _uiState.update { it.copy(searched = address, found = found, invited = false) }
    }

    fun invite() = work {
        val me = authRepository.currentUser?.uid ?: return@work
        val rival = _uiState.value.found ?: return@work
        val message = when (rivalryRepository.invite(me, rival.uid)) {
            InviteResult.SENT -> "Invite sent. ${rival.shortName} will see it next time they open Rivals."
            InviteResult.ACCEPTED -> "${rival.shortName} had already invited you, so you're rivals now."
            InviteResult.ALREADY -> "You've already invited ${rival.shortName}, or you're rivals already."
        }
        _uiState.update { it.copy(invited = true, message = message) }
    }

    /** Makes a one-off link for the share sheet. */
    fun shareLink() = work {
        val me = authRepository.currentUser?.toPlayer() ?: return@work
        val code = rivalryRepository.createInvite(me)
        val text = "${me.shortName} wants a rivalry with you on Rivals, the pool score tracker. " +
            "Open this on your phone to accept: ${InviteLinks.forCode(code)}\n\n" +
            "Or enter invite code $code in the app."
        _uiState.update { it.copy(shareText = text) }
    }

    fun onShared() = _uiState.update { it.copy(shareText = null) }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private fun work(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(message = messageFor(e)) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AddRivalViewModel(container.authRepository, container.playerRepository, container.rivalryRepository)
            }
        }
    }
}
