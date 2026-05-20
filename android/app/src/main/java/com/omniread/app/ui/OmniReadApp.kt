package com.omniread.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omniread.app.data.local.AppPrefsStore
import com.omniread.app.data.local.TokenStore
import com.omniread.app.ui.screens.coins.CoinStoreScreen
import com.omniread.app.ui.screens.detail.StoryDetailScreen
import com.omniread.app.ui.screens.feed.MainScaffold
import com.omniread.app.ui.screens.auth.AuthLandingScreen
import com.omniread.app.ui.screens.auth.ForgotPasswordScreen
import com.omniread.app.ui.screens.auth.LoginScreen
import com.omniread.app.ui.screens.auth.RegisterScreen
import com.omniread.app.ui.screens.notifications.NotificationsScreen
import com.omniread.app.ui.screens.onboarding.OnboardingScreen
import com.omniread.app.ui.screens.reader.ReaderScreen
import com.omniread.app.ui.screens.rewards.RewardsScreen
import com.omniread.app.ui.screens.vip.VipScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object Routes {
    const val Main = "main"
    const val Onboarding = "onboarding"
    const val AuthLanding = "auth"
    const val Login = "login"
    const val Register = "register"
    const val ForgotPassword = "forgot-password"
    const val StoryDetail = "story/{storyId}"
    const val Reader = "reader/{chapterId}"
    const val CoinStore = "coins"
    const val Vip = "vip"
    const val Rewards = "rewards"
    const val Notifications = "notifications"

    fun storyDetail(id: String) = "story/$id"
    fun reader(chapterId: String) = "reader/$chapterId"
}

@HiltViewModel
class AppNavViewModel @Inject constructor(
    tokenStore: TokenStore,
    private val prefs: AppPrefsStore,
) : ViewModel() {
    data class State(val ready: Boolean = false, val onboardingSeen: Boolean = false, val hasToken: Boolean = false)

    val state: StateFlow<State> = combine(
        prefs.onboardingSeenFlow,
        tokenStore.accessTokenFlow,
    ) { seen, token ->
        State(ready = true, onboardingSeen = seen, hasToken = !token.isNullOrBlank())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun markOnboardingSeen() {
        viewModelScope.launch { prefs.markOnboardingSeen() }
    }
}

@Composable
fun OmniReadApp(modifier: Modifier = Modifier, vm: AppNavViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
    if (!state.ready) return

    val start = when {
        !state.onboardingSeen -> Routes.Onboarding
        state.hasToken -> Routes.Main
        else -> Routes.AuthLanding
    }
    fun goMain() {
        nav.navigate(Routes.Main) {
            popUpTo(nav.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = nav, startDestination = start, modifier = modifier) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onDone = {
                    vm.markOnboardingSeen()
                    nav.navigate(Routes.AuthLanding) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.AuthLanding) {
            AuthLandingScreen(
                onLogin = { nav.navigate(Routes.Login) },
                onRegister = { nav.navigate(Routes.Register) },
                onGuest = { goMain() },
            )
        }
        composable(Routes.Main) {
            MainScaffold(
                onOpenStory = { nav.navigate(Routes.storyDetail(it)) },
                onOpenCoinStore = { nav.navigate(Routes.CoinStore) },
                onOpenVip = { nav.navigate(Routes.Vip) },
                onOpenRewards = { nav.navigate(Routes.Rewards) },
                onOpenReader = { chapterId, _ -> nav.navigate(Routes.reader(chapterId)) },
                onOpenLogin = { nav.navigate(Routes.Login) },
                onOpenNotifications = { nav.navigate(Routes.Notifications) },
            )
        }
        composable(
            Routes.StoryDetail,
            arguments = listOf(navArgument("storyId") { type = NavType.StringType }),
        ) { backStack ->
            val id = backStack.arguments?.getString("storyId") ?: return@composable
            StoryDetailScreen(
                storyId = id,
                onBack = { nav.popBackStack() },
                onOpenReader = { chapterId -> nav.navigate(Routes.reader(chapterId)) },
                onOpenCoinStore = { nav.navigate(Routes.CoinStore) },
                onOpenLogin = { nav.navigate(Routes.Login) },
            )
        }
        composable(
            Routes.Reader,
            arguments = listOf(navArgument("chapterId") { type = NavType.StringType }),
        ) { backStack ->
            val chapterId = backStack.arguments?.getString("chapterId") ?: return@composable
            ReaderScreen(
                chapterId = chapterId,
                onBack = { nav.popBackStack() },
                onOpenCoinStore = { nav.navigate(Routes.CoinStore) },
                onOpenChapter = { nav.navigate(Routes.reader(it)) },
            )
        }
        composable(Routes.CoinStore) {
            CoinStoreScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.Vip) {
            VipScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.Login) {
            LoginScreen(
                onSignedIn = { goMain() },
                onBack = { nav.popBackStack() },
                onForgot = { nav.navigate(Routes.ForgotPassword) },
                onRegister = {
                    nav.navigate(Routes.Register) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ForgotPassword) {
            ForgotPasswordScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.Register) {
            RegisterScreen(
                onSignedUp = { goMain() },
                onBack = { nav.popBackStack() },
                onLogin = {
                    nav.navigate(Routes.Login) {
                        popUpTo(Routes.Register) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Rewards) {
            RewardsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.Notifications) {
            NotificationsScreen(onBack = { nav.popBackStack() })
        }
    }
}
