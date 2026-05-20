package com.omniread.app.data.api

import com.omniread.app.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.header("Authorization") != null) return chain.proceed(req)
        val token = runBlocking { tokenStore.read().first }
        val out = if (token.isNullOrBlank()) req else req.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(out)
    }
}
