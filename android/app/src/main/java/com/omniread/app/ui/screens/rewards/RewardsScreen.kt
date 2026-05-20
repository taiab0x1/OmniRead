package com.omniread.app.ui.screens.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniread.app.data.repo.AppRemoteConfig
import com.omniread.app.data.repo.ConfigRepository
import com.omniread.app.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val configRepo: ConfigRepository,
    private val userRepo: com.omniread.app.data.repo.UserRepository,
    private val tokenStore: com.omniread.app.data.local.TokenStore,
) : ViewModel() {
    val config = configRepo.config
    private val _coinBalance = MutableStateFlow(0)
    val coinBalance: StateFlow<Int> = _coinBalance.asStateFlow()
    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    init {
        viewModelScope.launch {
            _userId.value = tokenStore.userId()
            runCatching { userRepo.profile() }.onSuccess {
                _coinBalance.value = it.coinBalance
                _streak.value = it.readingStreak
                _userId.value = it.id
            }
        }
    }

    fun checkIn() {
        viewModelScope.launch {
            runCatching { userRepo.claimDaily() }.onSuccess {
                _message.value = "Daily reward claimed!"
                runCatching { userRepo.profile() }.onSuccess { p ->
                    _coinBalance.value = p.coinBalance
                    _streak.value = p.readingStreak
                }
            }.onFailure {
                _message.value = it.message ?: "Already claimed today"
            }
        }
    }

    fun onAdRewarded() {
        _message.value = "Ad watched. Reward arrives after secure verification."
        viewModelScope.launch {
            runCatching { userRepo.profile() }.onSuccess {
                _coinBalance.value = it.coinBalance
                _streak.value = it.readingStreak
                _userId.value = it.id
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(onBack: () -> Unit, vm: RewardsViewModel = hiltViewModel()) {
    val cfg by vm.config.collectAsState()
    val coinBalance by vm.coinBalance.collectAsState()
    val streak by vm.streak.collectAsState()
    val message by vm.message.collectAsState()
    val userId by vm.userId.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reward", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = Tokens.Ink2) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg0),
            )
        },
        containerColor = Tokens.Bg0,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Bonus / Points
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$coinBalance", color = Tokens.Ink0, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Tokens.Accent))
                        Spacer(Modifier.width(4.dp))
                        Text("Bonus >", color = Tokens.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("0", color = Tokens.Ink0, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                        Spacer(Modifier.width(4.dp))
                        Text("Points >", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Convert card
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tokens.Bg2).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = Tokens.Gold, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Convert Points into Bonus", color = Tokens.Ink1, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(">", color = Tokens.Ink3, fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Daily Check-in — dynamic daily_login_coins
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(Tokens.GradientVip)).padding(20.dp)) {
                Column {
                    Text("Day ${streak + 1}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Daily reward: +${cfg.dailyLoginCoins} coins", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (i in 0..6) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Star, null, tint = if (i == 0) Tokens.Gold else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                                Text(if (i == 0) "Today" else "Day ${i + 1}", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(Tokens.GradientPink)).clickable { vm.checkIn() }, contentAlignment = Alignment.Center) {
                        Text("Check In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Tokens.Gold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Ads Reward — DYNAMIC from config
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(Tokens.GradientReward)).padding(20.dp)) {
                Column {
                    Text("Ads Reward", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    RewardRow(
                        "${cfg.adMaxAdsForPoints} Ads = ${cfg.adMaxPoints} Points Max",
                        "+${cfg.adPointsPerAd} Points",
                        "(0/${cfg.adMaxAdsForPoints})",
                        onGo = {
                            val activity = (view.context as? android.app.Activity) ?: return@RewardRow
                            com.omniread.app.util.AdManager.showRewardedAd(
                                activity = activity,
                                userId = userId,
                                onRewarded = { _, _ -> vm.onAdRewarded() },
                                onDismissed = {},
                                onFailed = {},
                            )
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    RewardRow(
                        "${cfg.adMaxAdsForBonus} Ads = ${cfg.adMaxBonus} Bonus Max",
                        "+${cfg.adBonusPerAd} Bonus",
                        "(0/${cfg.adMaxAdsForBonus})",
                        onGo = {
                            val activity = (view.context as? android.app.Activity) ?: return@RewardRow
                            com.omniread.app.util.AdManager.showRewardedAd(
                                activity = activity,
                                userId = userId,
                                onRewarded = { _, _ -> vm.onAdRewarded() },
                                onDismissed = {},
                                onFailed = {},
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Tasks — DYNAMIC from config
            if (cfg.newReaderTasks.isNotEmpty()) {
                Text("New Reader Gift", color = Tokens.Ink0, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                cfg.newReaderTasks.forEach { task ->
                    TaskItem(task.label, "+${task.bonus} Bonus")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Reading Challenge — DYNAMIC milestones
            if (cfg.readingMilestones.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tokens.Bg2).padding(16.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reading Challenge", color = Tokens.Ink0, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Tokens.Accent.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text("Claim", color = Tokens.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text("Points earned from reading", color = Tokens.Ink3, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            cfg.readingMilestones.forEach { m ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(Tokens.Bg3), contentAlignment = Alignment.Center) {
                                        Text("+${m.points}", color = Color(0xFF38BDF8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${m.minutes}min", color = Tokens.Ink3, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Unlock Reward — DYNAMIC milestones
            if (cfg.unlockMilestones.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tokens.Bg2).padding(16.dp)) {
                    Column {
                        Text("Unlock Reward", color = Tokens.Ink0, fontWeight = FontWeight.Bold)
                        Text("Points earned from unlocking chapters", color = Tokens.Ink3, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            cfg.unlockMilestones.forEach { m ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(Tokens.Bg3), contentAlignment = Alignment.Center) {
                                        Text("+${m.points}", color = Color(0xFF38BDF8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${m.chapters}ch", color = Tokens.Ink3, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun RewardRow(title: String, reward: String, progress: String, onGo: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp)
            Row {
                Text(reward, color = Color(0xFF38BDF8), fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text(progress, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Tokens.Accent).padding(horizontal = 16.dp, vertical = 6.dp).clickable { onGo() }) {
            Text("Go", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TaskItem(title: String, reward: String, onGo: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Tokens.Ink0, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(reward, color = Tokens.Accent, fontSize = 12.sp)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Tokens.Accent).padding(horizontal = 16.dp, vertical = 6.dp).clickable { onGo() }) {
            Text("Go", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
