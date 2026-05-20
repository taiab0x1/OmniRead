package com.omniread.app.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniread.app.ui.theme.Tokens
import com.omniread.app.util.deviceFingerprint
import kotlinx.coroutines.delay

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

    val sceneAlpha = remember { Animatable(0f) }
    val panelAlpha = remember { Animatable(0f) }
    val panelScale = remember { Animatable(0.94f) }

    LaunchedEffect(Unit) {
        sceneAlpha.animateTo(1f, tween(520, easing = EaseOutCubic))
    }
    LaunchedEffect(Unit) {
        delay(170)
        panelAlpha.animateTo(1f, tween(420, easing = EaseOutCubic))
    }
    LaunchedEffect(Unit) {
        delay(170)
        panelScale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "auth_landing")
    val drift by infiniteTransition.animateFloat(
        initialValue = -16f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(4600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auth_drift",
    )
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auth_glow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Bg0)
    ) {
        AuthStoryBackdrop(
            drift = drift,
            glow = glow,
            modifier = Modifier
                .fillMaxSize()
                .alpha(sceneAlpha.value),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AuthBrandHeader()
            AuthPreviewCard(modifier = Modifier.padding(top = 10.dp, bottom = 18.dp))
            AuthPopupPanel(
                state = state,
                onLogin = onLogin,
                onRegister = onRegister,
                onGuest = { vm.signInAsGuest(fingerprint) },
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(panelScale.value)
                    .alpha(panelAlpha.value),
            )
        }
    }
}

@Composable
private fun AuthStoryBackdrop(drift: Float, glow: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF141A2A),
                        Tokens.Bg0,
                        Color(0xFF070A0F),
                    )
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Tokens.Accent.copy(alpha = glow), Color.Transparent),
                    center = Offset(size.width * 0.25f, size.height * 0.2f),
                    radius = size.width * 0.76f,
                ),
                radius = size.width * 0.78f,
                center = Offset(size.width * 0.25f, size.height * 0.2f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Tokens.Purple.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.56f),
                    radius = size.width * 0.62f,
                ),
                radius = size.width * 0.64f,
                center = Offset(size.width * 0.95f, size.height * 0.56f),
            )
        }

        Box(
            modifier = Modifier
                .size(230.dp)
                .offset(x = (-68).dp + drift.dp, y = 118.dp)
                .blur(92.dp)
                .clip(CircleShape)
                .background(Tokens.AccentRose.copy(alpha = 0.16f))
        )
        Box(
            modifier = Modifier
                .size(170.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 58.dp - drift.dp, y = (-30).dp)
                .blur(78.dp)
                .clip(CircleShape)
                .background(Tokens.Gold.copy(alpha = 0.1f))
        )
    }
}

@Composable
private fun AuthBrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(Tokens.GradientPink)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "O",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("OmniRead", color = Tokens.Ink0, style = MaterialTheme.typography.titleMedium)
                Text("Your next chapter is ready", color = Tokens.Ink3, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = Tokens.Gold, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("New", color = Tokens.Ink1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AuthPreviewCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(236.dp)
                .offset(y = 14.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(alpha = 0.22f))
                .blur(10.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(246.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Tokens.Accent,
                            Tokens.Bg2,
                            Color(0xFF10131D),
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                .padding(18.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.1f, size.height * 0.08f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.12f),
                    radius = size.width * 0.44f,
                    center = Offset(size.width * 0.9f, size.height * 0.96f),
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = Color.White)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            tint = Tokens.ViewGreen,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Saved", color = Tokens.Ink0, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Column {
                    Text(
                        text = "The Midnight Library",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "A hidden door opens only for readers who come back.",
                        color = Color.White.copy(alpha = 0.74f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthPopupPanel(
    state: AuthUiState,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onGuest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Tokens.Bg1.copy(alpha = 0.96f))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            )
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.18f))
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Welcome back to the story.",
            color = Tokens.Ink0,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sign in to sync your library, or start reading as a guest.",
            color = Tokens.Ink2,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Accent),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign in", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(17.dp))
                .clickable { onRegister() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = Tokens.Ink1, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create account", style = MaterialTheme.typography.labelLarge, color = Tokens.Ink1)
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(enabled = state !is AuthUiState.Loading) { onGuest() },
            contentAlignment = Alignment.Center,
        ) {
            if (state is AuthUiState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Tokens.Accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Continuing...", color = Tokens.Ink2, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("Continue as guest", color = Tokens.Ink3, style = MaterialTheme.typography.bodyMedium)
            }
        }

        (state as? AuthUiState.Error)?.let {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tokens.Danger.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(it.message, color = Tokens.Danger, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
