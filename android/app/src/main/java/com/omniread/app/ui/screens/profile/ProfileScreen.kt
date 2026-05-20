package com.omniread.app.ui.screens.profile

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniread.app.data.local.TokenStore
import com.omniread.app.data.model.UserProfile
import com.omniread.app.data.repo.ConfigRepository
import com.omniread.app.data.repo.UserRepository
import com.omniread.app.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val configRepo: ConfigRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()
    val config = configRepo.config
    private val _isGuest = MutableStateFlow(true)
    val isGuest: StateFlow<Boolean> = _isGuest.asStateFlow()
    fun load() {
        viewModelScope.launch {
            val (token, _, _) = tokenStore.read()
            if (!token.isNullOrBlank()) {
                runCatching { userRepo.profile() }.onSuccess {
                    _profile.value = it
                    _isGuest.value = it.isGuest
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    onOpenCoinStore: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenRewards: () -> Unit = {},
    onOpenVip: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    vm: ProfileViewModel = hiltViewModel(),
) {
    val profile by vm.profile.collectAsState()
    val cfg by vm.config.collectAsState()
    val isGuest by vm.isGuest.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    val coins = profile?.coinBalance ?: 0
    val userId = profile?.id ?: "—"
    val vipBenefits = cfg.vipBenefits.ifEmpty { listOf("All Books Unlimited Reading", "New Stories First", "No Ads") }

    Column(
        modifier = Modifier.fillMaxSize().background(Tokens.Bg0).verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Sign in banner for guests
        if (isGuest) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Tokens.Bg2).padding(16.dp).clickable { onOpenLogin() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(Tokens.GradientPink)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sign in", color = Tokens.Ink0, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Save progress & sync across devices", color = Tokens.Ink3, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Tokens.Accent)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // VIP Banner
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(Tokens.GradientPink)).padding(20.dp).clickable { onOpenVip() },
        ) {
            Column {
                vipBenefits.forEach { benefit ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(benefit, color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { onOpenVip() },
                    contentAlignment = Alignment.Center,
                ) { Text("Subscribe", color = Tokens.Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Bonus / Points row
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tokens.Bg1).padding(16.dp).clickable { onOpenCoinStore() },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$coins", color = Tokens.Ink0, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Tokens.Accent))
                    Spacer(Modifier.width(4.dp))
                    Text("Bonus", color = Tokens.Ink2, fontSize = 12.sp)
                }
            }
            Box(Modifier.width(1.dp).height(30.dp).background(Tokens.Ink3.copy(alpha = 0.3f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$coins", color = Tokens.Ink0, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                    Spacer(Modifier.width(4.dp))
                    Text("Points", color = Tokens.Ink2, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Earn Rewards
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tokens.Bg1)
                .clickable { onOpenRewards() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CardGiftcard, null, tint = Tokens.Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Earn Rewards", color = Tokens.Ink0, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Get More Bonus!", color = Tokens.Ink3, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Tokens.Accent)
        }

        Spacer(Modifier.height(20.dp))

        // Menu items — dynamic from admin config
        val menuItems = cfg.profileMenuItems.filter { it.visible }
        if (menuItems.isNotEmpty()) {
            menuItems.forEach { item ->
                MenuItem(item.label, Color(android.graphics.Color.parseColor(item.color))) {
                    when (item.key) {
                        "inbox" -> onOpenNotifications()
                        "viewed" -> {}
                        "feedback" -> {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${cfg.feedbackEmail}?subject=Feedback"))
                            context.startActivity(Intent.createChooser(intent, "Send feedback"))
                        }
                        "rate_us" -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cfg.rateUsUrl))
                            context.startActivity(intent)
                        }
                        "language" -> {}
                    }
                }
            }
        } else {
            MenuItem("Inbox", Tokens.Accent) { onOpenNotifications() }
            MenuItem("Viewed", Color(0xFFF97316)) {}
            MenuItem("Feedback", Tokens.Danger) {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@omniread.app?subject=Feedback"))
                context.startActivity(Intent.createChooser(intent, "Send feedback"))
            }
            MenuItem("Rate Us", Tokens.Accent) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.omniread.app"))
                context.startActivity(intent)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("UID:${userId.take(8)}", color = Tokens.Ink3, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun MenuItem(label: String, iconColor: Color, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(iconColor))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = Tokens.Ink0, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Tokens.Accent, modifier = Modifier.size(20.dp))
    }
}
