package com.omniread.app.ui.screens.vip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniread.app.ui.theme.Tokens

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.omniread.app.data.repo.ConfigRepository
import com.omniread.app.util.BillingManager
import com.omniread.app.util.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class VipViewModel @Inject constructor(
    configRepo: ConfigRepository,
    val billing: BillingManager,
) : ViewModel() {
    val config = configRepo.config

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        viewModelScope.launch {
            billing.purchaseResult.collect { result ->
                when (result) {
                    is PurchaseResult.SubscriptionSuccess -> _message.value = "VIP activated! Enjoy unlimited reading."
                    is PurchaseResult.Failed -> _message.value = "Purchase failed: ${result.message}"
                    else -> Unit
                }
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun subscribe(activity: Activity?, sku: String) {
        if (activity == null) {
            _message.value = "Purchase is not available from this screen."
            return
        }
        if (!billing.connected.value) {
            _message.value = "Google Play Billing is still connecting. Try again in a moment."
            billing.refreshConnection()
            return
        }
        billing.launchSubscription(activity, sku)
    }

    fun refreshBilling() {
        billing.refreshConnection()
        _message.value = "Preparing Google Play Billing. Try Subscribe again in a moment."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipScreen(onBack: () -> Unit, vm: VipViewModel = hiltViewModel()) {
    val cfg by vm.config.collectAsState()
    val message by vm.message.collectAsState()
    val billingConnected by vm.billing.connected.collectAsState()
    val productDetails by vm.billing.productDetails.collectAsState()
    val activity = LocalContext.current as? Activity
    val benefits = cfg.vipBenefits.ifEmpty { listOf("All Books Unlimited Reading", "New Stories First", "No Ads") }
    val price = cfg.vipPlans.firstOrNull()?.price_usd ?: 2.99
    val configuredSku = cfg.vipPlans.firstOrNull()?.id ?: "vip_monthly"
    val vipSku = if (productDetails.containsKey(configuredSku)) configuredSku else "vip_monthly"
    val hasVipProduct = productDetails.containsKey(vipSku)
    val billingStatus = when {
        !billingConnected -> "Google Play Billing is connecting"
        !hasVipProduct -> "Subscription product is not available in this debug build"
        else -> "Ready with Google Play"
    }
    val ctaBrush = if (billingConnected && hasVipProduct) {
        Brush.linearGradient(Tokens.GradientPink)
    } else {
        Brush.linearGradient(listOf(Tokens.Bg3, Tokens.Bg2))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Membership", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg0),
            )
        },
        containerColor = Tokens.Bg0,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            // VIP badge
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(Tokens.GradientVip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }

            Spacer(Modifier.height(20.dp))

            Text("VIP Membership", color = Tokens.Ink0, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Unlock everything for one low price", color = Tokens.Ink2, fontSize = 14.sp)

            Spacer(Modifier.height(32.dp))

            // Benefits
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tokens.Bg1).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                benefits.forEach { Benefit(it) }
            }

            Spacer(Modifier.height(32.dp))

            // Price card
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Tokens.Accent, RoundedCornerShape(16.dp))
                    .background(Tokens.Bg1).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Monthly", color = Tokens.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$${"%.2f".format(price)}", color = Tokens.Ink0, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(" /month", color = Tokens.Ink3, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("Cancel anytime", color = Tokens.Ink3, fontSize = 12.sp)
            }

            Spacer(Modifier.height(28.dp))

            // Subscribe CTA
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .background(ctaBrush)
                    .clickable {
                        if (billingConnected && hasVipProduct) {
                            vm.subscribe(activity, vipSku)
                        } else {
                            vm.refreshBilling()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Subscribe — $${"%.2f".format(price)}/month", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            if (!billingConnected || !hasVipProduct) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.refreshBilling() }) {
                    Text("Refresh billing", color = Tokens.Accent)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                billingStatus,
                color = if (billingConnected && hasVipProduct) Tokens.Success else Tokens.Ink3,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            message?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Tokens.Bg2).padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(msg, color = Tokens.Ink1, textAlign = TextAlign.Center)
                }
                androidx.compose.runtime.LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(4_000)
                    vm.clearMessage()
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Payment will be charged to your Google Play account. Subscription auto-renews monthly unless cancelled at least 24 hours before the end of the current period.",
                color = Tokens.Ink3,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null, tint = Tokens.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(text, color = Tokens.Ink1, fontSize = 15.sp)
    }
}
