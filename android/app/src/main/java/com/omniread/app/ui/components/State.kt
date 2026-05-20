package com.omniread.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omniread.app.ui.theme.Tokens

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BookLoadingIndicator()
    }
}

@Composable
fun BookLoadingIndicator(
    modifier: Modifier = Modifier,
    label: String = "Opening pages",
) {
    val transition = rememberInfiniteTransition(label = "book_loader")
    val pageFlip by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0 using FastOutSlowInEasing
                1f at 860 using FastOutSlowInEasing
                1f at 1200
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "page_flip",
    )
    val pageGlow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "page_glow",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .height(62.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Tokens.Bg2)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            )
            BookPage(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 7.dp),
                tint = Tokens.Accent.copy(alpha = pageGlow),
            )
            BookPage(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 7.dp),
                tint = Tokens.AccentRose.copy(alpha = pageGlow),
            )
            TurningPage(
                progress = pageFlip,
                tint = Tokens.Accent.copy(alpha = pageGlow),
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Tokens.Accent.copy(alpha = 0.75f))
            )
        }
        Text(
            text = label,
            color = Tokens.Ink2,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun TurningPage(progress: Float, tint: Color, modifier: Modifier = Modifier) {
    val eased = progress.coerceIn(0f, 1f)
    val xOffset = 21f - (42f * eased)
    val rotation = 68f - (136f * eased)
    val alpha = when {
        eased < 0.08f -> eased / 0.08f
        eased > 0.92f -> (1f - eased) / 0.08f
        else -> 1f
    }.coerceIn(0.18f, 1f)

    BookPage(
        modifier = modifier
            .graphicsLayer {
                translationX = xOffset * density
                rotationY = rotation
                rotationZ = -4f + (8f * eased)
                scaleX = 0.98f - (0.08f * kotlin.math.abs(eased - 0.5f))
                scaleY = 1.04f
                this.alpha = alpha
                cameraDistance = 18f * density
            },
        tint = tint,
    )
}

@Composable
private fun BookPage(modifier: Modifier = Modifier, tint: Color) {
    Box(
        modifier = modifier
            .width(38.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp, topEnd = 3.dp, bottomEnd = 3.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.78f),
                    )
                )
            )
            .border(1.dp, tint.copy(alpha = 0.42f), RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp, topEnd = 3.dp, bottomEnd = 3.dp))
            .padding(horizontal = 7.dp, vertical = 8.dp),
    ) {
        Column {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .width(if (index == 3) 14.dp else 22.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(tint.copy(alpha = 0.48f))
                )
                if (index != 3) androidx.compose.foundation.layout.Spacer(Modifier.size(5.dp))
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Danger.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(message, color = Tokens.Danger, style = MaterialTheme.typography.bodyMedium)
    }
}
