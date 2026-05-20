package com.omniread.app.util

import android.app.Activity
import android.content.Context
import com.omniread.app.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdManager {
    private val rewardedAdUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID
    private val interstitialAdUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID
    private val bannerAdUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L

    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var lastInterstitialShownAt = 0L
    private val _adReady = MutableStateFlow(false)
    val adReady: StateFlow<Boolean> = _adReady.asStateFlow()

    private val _interstitialReady = MutableStateFlow(false)
    val interstitialReady: StateFlow<Boolean> = _interstitialReady.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun initialize(context: Context) {
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                .build()
        )
        MobileAds.initialize(context) {}
        loadRewardedAd(context)
        loadInterstitialAd(context)
    }

    fun createBannerAdView(context: Context): AdView {
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = bannerAdUnitId
            loadAd(AdRequest.Builder().build())
        }
    }

    fun loadRewardedAd(context: Context) {
        if (_loading.value || rewardedAd != null) return
        _loading.value = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, rewardedAdUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                _adReady.value = true
                _loading.value = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedAd = null
                _adReady.value = false
                _loading.value = false
            }
        })
    }

    fun loadInterstitialAd(context: Context) {
        if (interstitialAd != null) return
        InterstitialAd.load(context, interstitialAdUnitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                _interstitialReady.value = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialAd = null
                _interstitialReady.value = false
            }
        })
    }

    fun maybeShowInterstitial(
        activity: Activity,
        onDismissed: () -> Unit,
        onSkipped: () -> Unit,
    ) {
        val now = System.currentTimeMillis()
        val ad = interstitialAd
        if (ad == null || now - lastInterstitialShownAt < MIN_INTERSTITIAL_INTERVAL_MS) {
            if (ad == null) loadInterstitialAd(activity)
            onSkipped()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                _interstitialReady.value = false
                loadInterstitialAd(activity)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                _interstitialReady.value = false
                loadInterstitialAd(activity)
                onSkipped()
            }
        }
        lastInterstitialShownAt = now
        ad.show(activity)
    }

    /**
     * Shows the rewarded ad and configures Server-Side Verification so the
     * AdMob callback to our backend can credit the right user.
     *
     * @param userId the signed-in user's UUID (forwarded as `user_id` in the SSV callback)
     * @param customData optional payload echoed in SSV `custom_data`; e.g. "chapter:<uuid>"
     *                   to credit a chapter unlock instead of a coin reward.
     */
    fun showRewardedAd(
        activity: Activity,
        userId: String?,
        customData: String? = null,
        onRewarded: (amount: Int, type: String) -> Unit,
        onDismissed: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val ad = rewardedAd
        if (ad == null) {
            onFailed("Ad not ready. Try again in a moment.")
            loadRewardedAd(activity)
            return
        }

        // Set SSV options BEFORE show() so they're included in the callback Google fires.
        val ssvBuilder = ServerSideVerificationOptions.Builder()
        if (!userId.isNullOrBlank()) ssvBuilder.setUserId(userId)
        if (!customData.isNullOrBlank()) ssvBuilder.setCustomData(customData)
        ad.setServerSideVerificationOptions(ssvBuilder.build())

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                _adReady.value = false
                onDismissed()
                loadRewardedAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                _adReady.value = false
                onFailed(error.message)
                loadRewardedAd(activity)
            }
        }

        ad.show(activity) { reward ->
            onRewarded(reward.amount, reward.type)
        }
    }
}
