package com.omniread.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniread.app.data.api.ApiException
import com.omniread.app.data.repo.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Success(val username: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

data class PasswordResetState(
    val loading: Boolean = false,
    val requested: Boolean = false,
    val reset: Boolean = false,
    val devToken: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private val _passwordResetState = MutableStateFlow(PasswordResetState())
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState.asStateFlow()

    fun signInAsGuest(deviceFingerprint: String) = launch {
        runCatching { auth.guest(deviceFingerprint) }
    }

    fun login(email: String, password: String) = launch {
        runCatching { auth.login(email.trim(), password) }
    }

    fun register(email: String, username: String, password: String, birthYear: Int?) = launch {
        runCatching { auth.register(email.trim(), username.trim(), password, birthYear) }
    }

    fun signInWithGoogle(idToken: String, deviceFingerprint: String?) = launch {
        runCatching { auth.signInWithGoogle(idToken, deviceFingerprint) }
    }

    fun requestPasswordReset(email: String) {
        _passwordResetState.value = PasswordResetState(loading = true)
        viewModelScope.launch {
            runCatching { auth.forgotPassword(email.trim()) }
                .onSuccess { payload ->
                    val devToken = payload.jsonObject["reset_token"]?.jsonPrimitive?.content
                    _passwordResetState.value = PasswordResetState(requested = true, devToken = devToken)
                }
                .onFailure { e ->
                    _passwordResetState.value = PasswordResetState(error = errorMessage(e))
                }
        }
    }

    fun submitPasswordReset(token: String, newPassword: String) {
        _passwordResetState.value = _passwordResetState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { auth.resetPassword(token.trim(), newPassword) }
                .onSuccess { _passwordResetState.value = PasswordResetState(requested = true, reset = true) }
                .onFailure { e ->
                    _passwordResetState.value = _passwordResetState.value.copy(loading = false, error = errorMessage(e))
                }
        }
    }

    fun reset() {
        _state.value = AuthUiState.Idle
    }

    private inline fun launch(crossinline block: suspend () -> Result<com.omniread.app.data.model.AuthResponse>) {
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            block().fold(
                onSuccess = { _state.value = AuthUiState.Success(it.username) },
                onFailure = { e ->
                    val msg = when (e) {
                        is ApiException -> e.message ?: "Request failed"
                        is retrofit2.HttpException -> httpErrorMessage(e)
                        else -> e.message ?: "Something went wrong"
                    }
                    _state.value = AuthUiState.Error(msg)
                },
            )
        }
    }

    private fun errorMessage(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "Request failed"
        is retrofit2.HttpException -> httpErrorMessage(e)
        else -> e.message ?: "Something went wrong"
    }

    private fun httpErrorMessage(e: retrofit2.HttpException): String {
        val body = e.response()?.errorBody()?.string()
        return if (body != null) {
            try {
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body)
                json.jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Error ${e.code()}"
            } catch (_: Exception) {
                "Error ${e.code()}"
            }
        } else {
            "Error ${e.code()}"
        }
    }
}
