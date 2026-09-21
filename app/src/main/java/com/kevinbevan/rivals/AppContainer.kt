package com.kevinbevan.rivals

import android.content.Context

/**
 * Manual dependency injection. Repositories are created here and handed to ViewModels
 * through their factories. Firebase-backed repositories arrive in milestone 2.
 */
class AppContainer(@Suppress("unused") private val context: Context)
