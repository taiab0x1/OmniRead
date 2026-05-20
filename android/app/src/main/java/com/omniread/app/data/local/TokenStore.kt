package com.omniread.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authStore by preferencesDataStore(name = "omniread_auth")

@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val expiresKey = stringPreferencesKey("expires_at")
    private val userIdKey = stringPreferencesKey("user_id")

    val accessTokenFlow: Flow<String?> = ctx.authStore.data.map { it[accessKey] }
    val userIdFlow: Flow<String?> = ctx.authStore.data.map { it[userIdKey] }

    suspend fun read(): Triple<String?, String?, String?> {
        val prefs = ctx.authStore.data.first()
        return Triple(prefs[accessKey], prefs[refreshKey], prefs[expiresKey])
    }

    suspend fun save(access: String, refresh: String, expiresAt: String, userId: String) {
        ctx.authStore.edit {
            it[accessKey] = access
            it[refreshKey] = refresh
            it[expiresKey] = expiresAt
            it[userIdKey] = userId
        }
    }

    suspend fun updateAccess(access: String, expiresAt: String) {
        ctx.authStore.edit {
            it[accessKey] = access
            it[expiresKey] = expiresAt
        }
    }

    suspend fun clear() {
        ctx.authStore.edit { it.clear() }
    }

    suspend fun userId(): String? = ctx.authStore.data.first()[userIdKey]
}
