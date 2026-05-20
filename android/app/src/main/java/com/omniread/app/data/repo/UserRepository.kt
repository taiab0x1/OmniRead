package com.omniread.app.data.repo

import com.omniread.app.data.api.OmniReadApi
import com.omniread.app.data.model.BookmarkItem
import com.omniread.app.data.model.CoinPackage
import com.omniread.app.data.model.CoinTransaction
import com.omniread.app.data.model.HistoryItem
import com.omniread.app.data.model.NotificationItem
import com.omniread.app.data.model.UserProfile
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(private val api: OmniReadApi) {
    suspend fun profile(): UserProfile = api.profile().unwrap()
    suspend fun updatePreferences(preferredGenres: List<String>?, locale: String? = null): UserProfile {
        val body = buildJsonObject {
            if (preferredGenres != null) {
                put("preferred_genres", kotlinx.serialization.json.JsonArray(preferredGenres.map {
                    kotlinx.serialization.json.JsonPrimitive(it)
                }))
            }
            if (locale != null) put("locale", locale)
        }
        return api.updateProfile(body).unwrap()
    }

    suspend fun coinTransactions(limit: Int = 50): List<CoinTransaction> = api.coinTransactions(limit).unwrap()
    suspend fun claimDaily(): JsonElement = api.claimDaily().unwrap()
    suspend fun bookmarks(): List<BookmarkItem> = api.bookmarks().unwrap()
    suspend fun history(): List<HistoryItem> = api.history().unwrap()
    suspend fun notifications(limit: Int = 50): List<NotificationItem> = api.notifications(limit).unwrap()
    suspend fun markNotificationsRead(): JsonElement = api.markNotificationsRead().unwrap()
    suspend fun registerFcm(token: String, appVersion: String?) {
        val body = buildJsonObject {
            put("token", token)
            put("platform", "android")
            if (appVersion != null) put("app_version", appVersion)
        }
        api.registerFcm(body).unwrap()
    }
}

@Singleton
class PaymentRepository @Inject constructor(private val api: OmniReadApi) {
    suspend fun packages(): List<CoinPackage> = api.coinPackages().unwrap()

    suspend fun verifyCoinPurchase(sku: String, purchaseToken: String, idempotency: String?): JsonElement {
        val body = buildJsonObject {
            put("sku", sku)
            put("purchase_token", purchaseToken)
            put("is_subscription", false)
        }
        return api.verifyCoinPurchase(body, idempotency).unwrap()
    }

    suspend fun verifySubscription(sku: String, purchaseToken: String): JsonElement {
        val body = buildJsonObject {
            put("sku", sku)
            put("purchase_token", purchaseToken)
            put("is_subscription", true)
        }
        return api.verifySubscription(body).unwrap()
    }

    suspend fun validateAdReward(
        placement: String,
        chapterId: String?,
        deviceFingerprint: String,
        ssvTransactionId: String?,
    ): JsonElement {
        val body = buildJsonObject {
            put("placement", placement)
            if (chapterId != null) put("chapter_id", chapterId)
            put("device_fingerprint", deviceFingerprint)
            if (ssvTransactionId != null) put("ssv_transaction_id", ssvTransactionId)
        }
        return api.validateAdReward(body).unwrap()
    }
}
