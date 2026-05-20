package com.omniread.app.data.repo

import com.omniread.app.data.api.OmniReadApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GenreTab(val key: String, val label: String, val emoji: String? = null)

@Serializable
data class HomeSection(val key: String, val title: String, val emoji: String? = null, val query: String, val card_size: String = "large")

@Serializable
data class VipPlan(val id: String, val name: String, val price_usd: Double, val old_price_usd: Double, val badge: String, val duration_days: Int)

@Serializable
data class ReadingMilestone(val minutes: Int = 0, val chapters: Int = 0, val points: Int)

@Serializable
data class NewReaderTask(val key: String, val label: String, val bonus: Int)

@Serializable
data class ProfileMenuItem(val key: String, val label: String, val visible: Boolean = true, val color: String = "#FF2D78")

data class AppRemoteConfig(
    val genres: List<GenreTab> = emptyList(),
    val homeSections: List<HomeSection> = emptyList(),
    val vipPlans: List<VipPlan> = emptyList(),
    val vipBenefits: List<String> = emptyList(),
    val vipIntroPrice: Double = 2.99,
    val vipIntroThen: String = "\$19.99/Week",
    val adCoinsPerAd: Int = 10,
    val bannerAdsEnabled: Boolean = true,
    val interstitialAdsEnabled: Boolean = true,
    val rewardedAdsEnabled: Boolean = true,
    val adDailyCap: Int = 5,
    val adCooldownMin: Int = 30,
    val adMaxPoints: Int = 684,
    val adMaxAdsForPoints: Int = 25,
    val adPointsPerAd: Int = 14,
    val adMaxBonus: Int = 132,
    val adMaxAdsForBonus: Int = 15,
    val adBonusPerAd: Int = 4,
    val readingMilestones: List<ReadingMilestone> = emptyList(),
    val unlockMilestones: List<ReadingMilestone> = emptyList(),
    val newReaderTasks: List<NewReaderTask> = emptyList(),
    val dailyLoginCoins: Int = 5,
    val chapterBaseCost: Int = 5,
    val trendingTags: List<String> = emptyList(),
    val trendingTitles: List<String> = emptyList(),
    val profileMenuItems: List<ProfileMenuItem> = emptyList(),
    val feedbackEmail: String = "support@omniread.app",
    val rateUsUrl: String = "https://play.google.com/store/apps/details?id=com.omniread.app",
)

@Singleton
class ConfigRepository @Inject constructor(
    private val api: OmniReadApi,
    private val json: Json,
) {
    private val _config = MutableStateFlow(AppRemoteConfig())
    val config: StateFlow<AppRemoteConfig> = _config.asStateFlow()

    suspend fun refresh() {
        val raw = runCatching { api.appConfig().unwrap() as JsonObject }.getOrNull() ?: return
        val tags = runCatching { api.trendingTags().unwrap() }.getOrDefault(emptyList())
        val titles = runCatching { api.trendingStoryTitles().unwrap() }.getOrDefault(emptyList())

        val genres = raw["genres"]?.jsonObject?.get("tabs")?.jsonArray?.map {
            json.decodeFromJsonElement<GenreTab>(it)
        } ?: emptyList()

        val sections = raw["home_sections"]?.jsonObject?.get("sections")?.jsonArray?.map {
            json.decodeFromJsonElement<HomeSection>(it)
        } ?: emptyList()

        val vipObj = raw["vip_plans"]?.jsonObject
        val plans = vipObj?.get("plans")?.jsonArray?.map { json.decodeFromJsonElement<VipPlan>(it) } ?: emptyList()
        val benefits = vipObj?.get("benefits")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val introOffer = vipObj?.get("intro_offer")?.jsonObject
        val introPrice = introOffer?.get("price_usd")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 2.99
        val introThen = introOffer?.get("then_price")?.jsonPrimitive?.contentOrNull ?: "\$19.99/Week"

        val adObj = raw["ad_config"]?.jsonObject
        val rewardsObj = raw["rewards"]?.jsonObject
        val adRewards = rewardsObj?.get("ad_rewards")?.jsonObject
        val coinObj = raw["coin_config"]?.jsonObject

        val readingMilestones = rewardsObj?.get("reading_challenge")?.jsonObject?.get("milestones")?.jsonArray?.map {
            json.decodeFromJsonElement<ReadingMilestone>(it)
        } ?: emptyList()

        val unlockMilestones = rewardsObj?.get("unlock_reward")?.jsonObject?.get("milestones")?.jsonArray?.map {
            json.decodeFromJsonElement<ReadingMilestone>(it)
        } ?: emptyList()

        val tasks = rewardsObj?.get("new_reader_tasks")?.jsonArray?.map {
            json.decodeFromJsonElement<NewReaderTask>(it)
        } ?: emptyList()

        _config.value = AppRemoteConfig(
            genres = genres,
            homeSections = sections,
            vipPlans = plans,
            vipBenefits = benefits,
            vipIntroPrice = introPrice,
            vipIntroThen = introThen,
            adCoinsPerAd = adObj?.get("coins_per_rewarded_ad")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10,
            bannerAdsEnabled = adObj?.get("banner_enabled")?.jsonPrimitive?.booleanOrNull ?: true,
            interstitialAdsEnabled = adObj?.get("interstitial_enabled")?.jsonPrimitive?.booleanOrNull ?: true,
            rewardedAdsEnabled = adObj?.get("rewarded_ads_enabled")?.jsonPrimitive?.booleanOrNull ?: true,
            adDailyCap = adRewards?.get("daily_cap")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5,
            adCooldownMin = adObj?.get("rewarded_cooldown_minutes")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 30,
            adMaxPoints = adRewards?.get("max_points_total")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 684,
            adMaxAdsForPoints = adRewards?.get("max_ads_for_points")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 25,
            adPointsPerAd = adRewards?.get("points_per_ad")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 14,
            adMaxBonus = adRewards?.get("max_bonus_total")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 132,
            adMaxAdsForBonus = adRewards?.get("max_ads_for_bonus")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 15,
            adBonusPerAd = adRewards?.get("bonus_per_ad")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 4,
            readingMilestones = readingMilestones,
            unlockMilestones = unlockMilestones,
            newReaderTasks = tasks,
            dailyLoginCoins = coinObj?.get("daily_login_coins")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5,
            chapterBaseCost = coinObj?.get("chapter_base_cost")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5,
            trendingTags = tags,
            trendingTitles = titles,
            profileMenuItems = raw["profile_menu"]?.jsonObject?.get("items")?.jsonArray?.map {
                json.decodeFromJsonElement<ProfileMenuItem>(it)
            } ?: emptyList(),
            feedbackEmail = raw["profile_menu"]?.jsonObject?.get("feedback_email")?.jsonPrimitive?.contentOrNull ?: "support@omniread.app",
            rateUsUrl = raw["profile_menu"]?.jsonObject?.get("rate_us_url")?.jsonPrimitive?.contentOrNull ?: "https://play.google.com/store/apps/details?id=com.omniread.app",
        )
    }
}
