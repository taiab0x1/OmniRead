package com.omniread.app.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniread.app.ui.theme.Tokens
import com.omniread.app.util.deviceFingerprint

@Composable
fun AuthLandingScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onGuest: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val fingerprint = remember { deviceFingerprint(ctx) }

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onGuest()
    }

    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(800, easing = EaseOutCubic))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, tween(1000, easing = EaseOutCubic))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        contentAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val orbOffset by infiniteTransition.animateFloat(
        initialValue = -20f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb"
    )
    val orbOffset2 by infiniteTransition.animateFloat(
        initialValue = 15f, targetValue = -15f,
        animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb2"
    )

    Box(modifier = Modifier.fillMaxSize().background(Tokens.Bg0)) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-60).dp + orbOffset.dp, y = 120.dp)
                .blur(100.dp)
                .clip(CircleShape)
                .background(Tokens.Accent.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 180.dp + orbOffset2.dp, y = 500.dp)
                .blur(80.dp)
                .clip(CircleShape)
                .background(Tokens.AccentRose.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(x = 100.dp, y = (-30).dp + orbOffset.dp)
                .blur(60.dp)
                .clip(CircleShape)
                .background(Tokens.Gold.copy(alpha = 0.06f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(80.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Tokens.Accent, Tokens.AccentRose)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("O", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "OmniRead",
                    style = MaterialTheme.typography.displayLarge,
                    color = Tokens.Ink0,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stories that don't let go.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Tokens.Ink2,
                    textAlign = TextAlign.Center,
                )
            }

            Column(modifier = Modifier.fillMaxWidth().alpha(contentAlpha.value)) {
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Tokens.Accent),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
                ) {
                    Text("Sign in", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .clickable { onRegister() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Create account", style = MaterialTheme.typography.labelLarge, color = Tokens.Ink1)
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(enabled = state !is AuthUiState.Loading) { vm.signInAsGuest(fingerprint) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state is AuthUiState.Loading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Tokens.Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Continuing…", color = Tokens.Ink2, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text("Continue as guest", color = Tokens.Ink3, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                (state as? AuthUiState.Error)?.let {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Tokens.Danger.copy(alpha = 0.12f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(it.message, color = Tokens.Danger, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
