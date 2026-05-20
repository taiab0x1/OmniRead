package com.omniread.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TokenPair(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
)

@Serializable
data class AuthResponse(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("is_guest") val isGuest: Boolean,
    @SerialName("coin_balance") val coinBalance: Int,
    @SerialName("subscription_tier") val subscriptionTier: String,
    val tokens: TokenPair,
)

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_guest") val isGuest: Boolean,
    @SerialName("is_verified") val isVerified: Boolean,
    @SerialName("coin_balance") val coinBalance: Int,
    @SerialName("subscription_tier") val subscriptionTier: String,
    @SerialName("subscription_expires_at") val subscriptionExpiresAt: String? = null,
    @SerialName("reading_streak") val readingStreak: Int,
    @SerialName("preferred_genres") val preferredGenres: List<String>? = null,
    val locale: String = "en",
)

@Serializable
data class CoinPackage(
    val id: String,
    val sku: String,
    val name: String,
    val coins: Int,
    @SerialName("bonus_coins") val bonusCoins: Int,
    @SerialName("price_usd") val priceUsd: Double,
    @SerialName("is_best_value") val isBestValue: Boolean,
)

@Serializable
data class CoinTransaction(
    val id: String,
    val amount: Int,
    val type: String,
    val description: String? = null,
    @SerialName("balance_after") val balanceAfter: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NotificationItem(
    val id: String,
    val type: String,
    val title: String? = null,
    val body: String? = null,
    val data: JsonElement? = null,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class UnlockResponse(
    @SerialName("chapter_id") val chapterId: String,
    @SerialName("unlock_method") val unlockMethod: String,
    @SerialName("coins_spent") val coinsSpent: Int,
    @SerialName("new_balance") val newBalance: Int,
)

@Serializable
data class HistoryItem(
    @SerialName("story_id") val storyId: String,
    val title: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("chapter_id") val chapterId: String,
    @SerialName("scroll_position") val scrollPosition: Int = 0,
    val completed: Boolean = false,
    @SerialName("last_read_at") val lastReadAt: String,
)

@Serializable
data class BookmarkItem(
    val id: String,
    val title: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val genre: String,
)
