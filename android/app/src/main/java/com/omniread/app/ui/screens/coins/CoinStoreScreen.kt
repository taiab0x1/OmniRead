package com.omniread.app.ui.screens.coins

import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniread.app.data.local.TokenStore
import com.omniread.app.data.model.CoinPackage
import com.omniread.app.data.repo.PaymentRepository
import com.omniread.app.data.repo.UserRepository
import com.omniread.app.ui.components.Pill
import com.omniread.app.ui.theme.Tokens
import com.omniread.app.util.BillingManager
import com.omniread.app.util.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CoinStoreViewModel @Inject constructor(
    private val payments: PaymentRepository,
    private val users: UserRepository,
    private val tokenStore: TokenStore,
    val billing: BillingManager,
) : ViewModel() {
    private val _packages = MutableStateFlow<List<CoinPackage>>(emptyList())
    val packages: StateFlow<List<CoinPackage>> = _packages.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _balance = MutableStateFlow<Int?>(null)
    val balance: StateFlow<Int?> = _balance.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching { payments.packages() }.onSuccess { _packages.value = it }
        }
        viewModelScope.launch {
            _userId.value = tokenStore.userId()
            runCatching { users.profile() }.onSuccess {
                _balance.value = it.coinBalance
                _userId.value = it.id
            }
        }
        viewModelScope.launch {
            billing.purchaseResult.collect { result ->
                when (result) {
                    is PurchaseResult.Success -> {
                        _toastMessage.value = "Purchase complete!"
                        refreshBalance()
                    }
                    is PurchaseResult.Failed -> _toastMessage.value = "Purchase failed: ${result.message}"
                    is PurchaseResult.Cancelled -> Unit
                    else -> Unit
                }
            }
        }
    }

    fun clearToast() { _toastMessage.value = null }

    fun claimDaily() {
        viewModelScope.launch {
            runCatching { users.claimDaily() }
                .onSuccess {
                    _toastMessage.value = "Daily reward checked."
                    refreshBalance()
                }
                .onFailure { _toastMessage.value = it.message ?: "Could not claim daily reward" }
        }
    }

    fun onAdRewarded() {
        _toastMessage.value = "Ad watched. Coins arrive after secure verification."
        refreshBalance()
    }

    private fun refreshBalance() {
        viewModelScope.launch {
            runCatching { users.profile() }.onSuccess {
                _balance.value = it.coinBalance
                _userId.value = it.id
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinStoreScreen(onBack: () -> Unit, vm: CoinStoreViewModel = hiltViewModel()) {
    val packages by vm.packages.collectAsState()
    val toastMessage by vm.toastMessage.collectAsState()
    val balance by vm.balance.collectAsState()
    val userId by vm.userId.collectAsState()
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coins") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg0),
            )
        },
        containerColor = Tokens.Bg0,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            item {
                Text(
                    "${balance ?: 0} coins",
                    color = Tokens.Gold,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Earn coins",
                    color = Tokens.Ink2,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(10.dp))
                EarnTile("Watch a quick ad", "+10 coins each, 5/day", action = "Watch", onClick = {
                    val act = activity ?: return@EarnTile
                    com.omniread.app.util.AdManager.showRewardedAd(
                        activity = act,
                        userId = userId,
                        onRewarded = { _, _ -> vm.onAdRewarded() },
                        onDismissed = {},
                        onFailed = { },
                    )
                })
                Spacer(Modifier.height(8.dp))
                EarnTile("Daily login", "+5 coins, once per day", action = "Claim", onClick = { vm.claimDaily() })
                Spacer(Modifier.height(24.dp))
                Text("Packs", color = Tokens.Ink2, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
            }
            items(packages, key = { it.id }) { pkg ->
                PackageRow(pkg, onClick = {
                    if (activity != null) vm.billing.launchCoinPurchase(activity, pkg.sku)
                })
                Spacer(Modifier.height(10.dp))
            }
            item {
                toastMessage?.let { msg ->
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Tokens.Bg2).padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(msg, color = Tokens.Ink1)
                    }
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3_000)
                        vm.clearToast()
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun EarnTile(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tokens.Bg1)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Tokens.Ink0, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Tokens.Ink2, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Tokens.Accent)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(action, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PackageRow(pkg: CoinPackage, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tokens.Bg1)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pkg.name, color = Tokens.Ink0, style = MaterialTheme.typography.titleMedium)
                if (pkg.isBestValue) {
                    Spacer(Modifier.height(0.dp))
                    Pill("Best value", bg = Tokens.Gold.copy(alpha = 0.18f), fg = Tokens.Gold)
                }
            }
            Text(
                "${pkg.coins}${if (pkg.bonusCoins > 0) " + ${pkg.bonusCoins} bonus" else ""} coins",
                color = Tokens.Ink2,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "$${"%.2f".format(pkg.priceUsd)}",
            color = Tokens.Gold,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
