package com.raasta.app.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "demo_session")

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val phone: String) : SessionState
}

/** Keeps the demo login on the device so the user stays signed in between launches. */
class DemoSessionStore(private val context: Context) {
    private val phoneKey = stringPreferencesKey("phone")

    val session: Flow<SessionState> = context.sessionDataStore.data.map { prefs ->
        prefs[phoneKey]?.let { SessionState.SignedIn(it) } ?: SessionState.SignedOut
    }

    suspend fun signIn(phone: String) {
        context.sessionDataStore.edit { it[phoneKey] = phone }
    }

    suspend fun signOut() {
        context.sessionDataStore.edit { it.remove(phoneKey) }
    }
}
