package com.omniread.app.data.repo

import com.omniread.app.data.api.ApiException
import com.omniread.app.data.api.Envelope
import com.omniread.app.data.api.OmniReadApi
import com.omniread.app.data.local.TokenStore
import com.omniread.app.data.model.AuthResponse
import com.omniread.app.data.model.UserProfile
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: OmniReadApi,
    private val tokenStore: TokenStore,
    private val json: Json,
) {
    suspend fun register(email: String, username: String, password: String, birthYear: Int? = null): AuthResponse {
        val body = buildJsonObject {
            put("email", email)
            put("username", username)
            put("password", password)
            if (birthYear != null) put("birth_year", birthYear)
        }
        return api.register(body).unwrap().also { persist(it) }
    }

    suspend fun login(email: String, password: String): AuthResponse {
        val body = buildJsonObject { put("email", email); put("password", password) }
        return api.login(body).unwrap().also { persist(it) }
    }

    suspend fun guest(deviceFingerprint: String): AuthResponse {
        val body = buildJsonObject { put("device_fingerprint", deviceFingerprint) }
        return api.guest(body).unwrap().also { persist(it) }
    }

    suspend fun signInWithGoogle(idToken: String, deviceFingerprint: String?): AuthResponse {
        val body = buildJsonObject {
            put("id_token", idToken)
            if (deviceFingerprint != null) put("device_fingerprint", deviceFingerprint)
        }
        return api.google(body).unwrap().also { persist(it) }
    }

    suspend fun refresh(refreshToken: String): AuthResponse {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        return api.refresh(body).unwrap().also { persist(it) }
    }

    suspend fun forgotPassword(email: String): JsonElement {
        val body = buildJsonObject { put("email", email) }
        return api.forgotPassword(body).unwrap()
    }

    suspend fun resetPassword(token: String, newPassword: String): JsonElement {
        val body = buildJsonObject {
            put("token", token)
            put("new_password", newPassword)
        }
        return api.resetPassword(body).unwrap()
    }

    suspend fun logout() {
        val (_, refresh, _) = tokenStore.read()
        if (refresh != null) {
            runCatching { api.logout(buildJsonObject { put("refresh_token", refresh) }).unwrap() }
        }
        tokenStore.clear()
    }

    suspend fun deleteAccount() {
        api.deleteAccount().unwrap()
        tokenStore.clear()
    }

    suspend fun me(): UserProfile = api.profile().unwrap()

    private suspend fun persist(auth: AuthResponse) {
        tokenStore.save(
            access = auth.tokens.accessToken,
            refresh = auth.tokens.refreshToken,
            expiresAt = auth.tokens.expiresAt,
            userId = auth.userId,
        )
    }
}

fun <T> Envelope<T>.unwrap(): T {
    val err = error
    if (!success || err != null) throw ApiException(err?.code ?: "unknown", err?.message ?: "Request failed")
    return data ?: throw ApiException("empty", "Empty response")
}
