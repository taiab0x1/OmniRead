package com.omniread.app.data.api

import com.omniread.app.data.local.TokenStore
import com.omniread.app.data.repo.AuthRepository
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Recovers from 401s on app-side endpoints by rotating the refresh token and
 * retrying the original request once. If refresh fails, the stale session is
 * cleared and the caller can route the user back through auth.
 *
 * Skipped for auth endpoints themselves to avoid retry loops.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    // Lazy provider — AuthRepository depends on the same OkHttpClient that
    // installs this authenticator, so eager injection would deadlock the graph.
    private val authRepoProvider: Provider<AuthRepository>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val req = response.request
        val path = req.url.encodedPath
        // Don't try to refresh auth on the auth endpoints themselves
        if (path.contains("/v1/auth/")) return null
        // Already retried once? Give up.
        if (response.priorResponse != null) return null

        return runBlocking {
            val (_, refresh, _) = tokenStore.read()
            if (refresh.isNullOrBlank()) return@runBlocking null
            val authRepo = authRepoProvider.get()
            val newAuth = runCatching { authRepo.refresh(refresh) }
                .onFailure { tokenStore.clear() }
                .getOrNull() ?: return@runBlocking null
            req.newBuilder()
                .header("Authorization", "Bearer ${newAuth.tokens.accessToken}")
                .build()
        }
    }
}
