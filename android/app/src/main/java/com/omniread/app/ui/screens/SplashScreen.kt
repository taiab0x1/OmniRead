package com.omniread.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniread.app.data.local.TokenStore
import com.omniread.app.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SplashViewModel @Inject constructor(private val tokenStore: TokenStore) : ViewModel() {
    private val _state = MutableStateFlow<Boolean?>(null)
    val isAuthed: StateFlow<Boolean?> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            val (access, _, _) = tokenStore.read()
            _state.value = !access.isNullOrBlank()
        }
    }
}

@Composable
fun SplashScreen(
    onAuthed: () -> Unit,
    onUnauthed: () -> Unit,
    vm: SplashViewModel = hiltViewModel(),
) {
    val authed by vm.isAuthed.collectAsState()

    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(600, easing = EaseOutCubic))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, tween(900, easing = EaseOutCubic))
    }
    LaunchedEffect(Unit) {
        delay(300)
        textAlpha.animateTo(1f, tween(500))
    }

    LaunchedEffect(authed) {
        if (authed != null) {
            delay(1200)
            if (authed == true) onAuthed() else onUnauthed()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Tokens.Bg0),
        contentAlignment = Alignment.Center,
    ) {
        // Background orb
        Box(
            modifier = Modifier
                .size(300.dp)
                .scale(pulse)
                .blur(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Tokens.Accent.copy(alpha = 0.2f),
                            Color.Transparent,
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(logoScale.value)
                .alpha(logoAlpha.value),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Tokens.Accent, Tokens.AccentRose)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "O",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "OmniRead",
                color = Tokens.Ink0,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.alpha(textAlpha.value),
            )
        }
    }
}
