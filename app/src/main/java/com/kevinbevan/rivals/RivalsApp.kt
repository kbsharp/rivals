package com.kevinbevan.rivals

import android.app.Application
import com.kevinbevan.rivals.auth.toPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RivalsApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        refreshProfile()
    }

    /**
     * Sign-in writes the profile and email index, but someone can stay signed in for months:
     * refresh both on each launch so a new name or photo shows, and so a player who signed in
     * before the email index existed can still be found. Best effort: it needs the network,
     * and the next launch tries again.
     */
    private fun refreshProfile() {
        val user = container.authRepository.currentUser ?: return
        appScope.launch {
            runCatching { container.playerRepository.upsert(user.toPlayer()) }
        }
    }
}
