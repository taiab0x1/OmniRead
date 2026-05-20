package com.omniread.app.data.api

import com.omniread.app.data.model.AuthResponse
import com.omniread.app.data.model.BookmarkItem
import com.omniread.app.data.model.ChapterContent
import com.omniread.app.data.model.ChapterListItem
import com.omniread.app.data.model.CoinPackage
import com.omniread.app.data.model.CoinTransaction
import com.omniread.app.data.model.HistoryItem
import com.omniread.app.data.model.NotificationItem
import com.omniread.app.data.model.Story
import com.omniread.app.data.model.UnlockResponse
import com.omniread.app.data.model.UserProfile
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface OmniReadApi {

    // Auth
    @POST("v1/auth/register")
    suspend fun register(@Body body: JsonElement): Envelope<AuthResponse>

    @POST("v1/auth/login")
    suspend fun login(@Body body: JsonElement): Envelope<AuthResponse>

    @POST("v1/auth/google")
    suspend fun google(@Body body: JsonElement): Envelope<AuthResponse>

    @POST("v1/auth/guest")
    suspend fun guest(@Body body: JsonElement): Envelope<AuthResponse>

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body body: JsonElement): Envelope<AuthResponse>

    @POST("v1/auth/forgot-password")
    suspend fun forgotPassword(@Body body: JsonElement): Envelope<JsonElement>

    @POST("v1/auth/reset-password")
    suspend fun resetPassword(@Body body: JsonElement): Envelope<JsonElement>

    @POST("v1/auth/logout")
    suspend fun logout(@Body body: JsonElement): Envelope<JsonElement>

    @DELETE("v1/auth/account")
    suspend fun deleteAccount(): Envelope<JsonElement>

    // Stories
    @GET("v1/stories")
    suspend fun feed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("genre") genre: String? = null,
    ): Envelope<List<Story>>

    @GET("v1/stories/trending")
    suspend fun trending(@Query("limit") limit: Int = 20, @Query("genre") genre: String? = null): Envelope<List<Story>>

    @GET("v1/stories/new")
    suspend fun newest(@Query("limit") limit: Int = 20, @Query("genre") genre: String? = null): Envelope<List<Story>>

    @GET("v1/stories/recommended")
    suspend fun recommended(@Query("limit") limit: Int = 20): Envelope<List<Story>>

    @GET("v1/stories/genres")
    suspend fun genres(): Envelope<JsonElement>

    // Public config
    @GET("v1/config/app")
    suspend fun appConfig(): Envelope<JsonElement>

    @GET("v1/config/trending-tags")
    suspend fun trendingTags(): Envelope<List<String>>

    @GET("v1/config/trending-stories")
    suspend fun trendingStoryTitles(): Envelope<List<String>>

    @GET("v1/stories/search")
    suspend fun search(
        @Query("q") q: String? = null,
        @Query("genre") genre: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): Envelope<List<Story>>

    @GET("v1/stories/{id}")
    suspend fun storyDetail(@Path("id") id: String): Envelope<Story>

    @GET("v1/stories/{id}/chapters")
    suspend fun chapterList(@Path("id") id: String): Envelope<List<ChapterListItem>>

    @GET("v1/stories/{id}/related")
    suspend fun related(@Path("id") id: String, @Query("limit") limit: Int = 10): Envelope<List<Story>>

    @POST("v1/stories/{id}/like")
    suspend fun likeStory(@Path("id") id: String): Envelope<JsonElement>

    @POST("v1/stories/{id}/bookmark")
    suspend fun bookmark(@Path("id") id: String): Envelope<JsonElement>

    @DELETE("v1/stories/{id}/bookmark")
    suspend fun unbookmark(@Path("id") id: String): Envelope<JsonElement>

    @POST("v1/stories/{id}/rate")
    suspend fun rateStory(@Path("id") id: String, @Body body: JsonElement): Envelope<JsonElement>

    // Chapters
    @GET("v1/chapters/{id}")
    suspend fun chapter(@Path("id") id: String): Envelope<JsonElement>

    @GET("v1/chapters/{id}/comments")
    suspend fun chapterComments(@Path("id") id: String): Envelope<List<com.omniread.app.ui.screens.detail.CommentData>>

    @POST("v1/chapters/{id}/comments")
    suspend fun postComment(@Path("id") id: String, @Body body: JsonElement): Envelope<JsonElement>

    @POST("v1/chapters/{id}/unlock")
    suspend fun unlockWithCoins(
        @Path("id") id: String,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): Envelope<UnlockResponse>

    @POST("v1/chapters/{id}/progress")
    suspend fun saveProgress(@Path("id") id: String, @Body body: JsonElement): Envelope<JsonElement>

    // User
    @GET("v1/user/profile")
    suspend fun profile(): Envelope<UserProfile>

    @PUT("v1/user/profile")
    suspend fun updateProfile(@Body body: JsonElement): Envelope<UserProfile>

    @GET("v1/user/coins")
    suspend fun coins(): Envelope<JsonElement>

    @GET("v1/user/coins/transactions")
    suspend fun coinTransactions(@Query("limit") limit: Int = 50): Envelope<List<CoinTransaction>>

    @POST("v1/user/coins/claim-daily")
    suspend fun claimDaily(): Envelope<JsonElement>

    @GET("v1/user/bookmarks")
    suspend fun bookmarks(): Envelope<List<BookmarkItem>>

    @GET("v1/user/history")
    suspend fun history(): Envelope<List<HistoryItem>>

    @GET("v1/user/notifications")
    suspend fun notifications(@Query("limit") limit: Int = 50): Envelope<List<NotificationItem>>

    @PUT("v1/user/notifications/read")
    suspend fun markNotificationsRead(): Envelope<JsonElement>

    @POST("v1/user/fcm-token")
    suspend fun registerFcm(@Body body: JsonElement): Envelope<JsonElement>

    // Payments
    @GET("v1/payments/coins/packages")
    suspend fun coinPackages(): Envelope<List<CoinPackage>>

    @POST("v1/payments/coins/purchase")
    suspend fun verifyCoinPurchase(
        @Body body: JsonElement,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): Envelope<JsonElement>

    @POST("v1/payments/subscription/subscribe")
    suspend fun verifySubscription(@Body body: JsonElement): Envelope<JsonElement>

    @GET("v1/payments/subscription/status")
    suspend fun subscriptionStatus(): Envelope<JsonElement>

    @POST("v1/payments/ad-reward/validate")
    suspend fun validateAdReward(@Body body: JsonElement): Envelope<JsonElement>
}
