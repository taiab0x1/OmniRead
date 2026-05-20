package com.omniread.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniread.app.ui.theme.Tokens
import kotlinx.coroutines.launch

private data class Slide(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val sampleTitle: String,
    val sampleMeta: String,
    val sceneLines: List<String>,
    val chips: List<String>,
)

private val slides = listOf(
    Slide(
        eyebrow = "A smarter story feed",
        title = "Open the app and fall straight into a scene.",
        subtitle = "Swipe through cinematic fiction, AI-powered picks, and tiny chapters built for quick reading breaks.",
        icon = Icons.Rounded.AutoStories,
        tint = Tokens.Accent,
        sampleTitle = "The Clockwork Moon",
        sampleMeta = "Fantasy mystery - 8 min read",
        sceneLines = listOf(
            "Rain tapped the copper roofs as Mara held the last moon-key.",
            "Behind her, the city lights blinked out one district at a time.",
        ),
        chips = listOf("For you", "Fresh", "Immersive"),
    ),
    Slide(
        eyebrow = "Always in sync",
        title = "Your library remembers every cliffhanger.",
        subtitle = "Bookmarks, progress, and favorite genres stay ready so the next chapter is never buried.",
        icon = Icons.Rounded.BookmarkBorder,
        tint = Tokens.PurpleSoft,
        sampleTitle = "Signal in the Static",
        sampleMeta = "Sci-fi thriller - chapter 12",
        sceneLines = listOf(
            "The message repeated every thirteen seconds.",
            "This time, Nia heard her own voice hidden under the static.",
        ),
        chips = listOf("Saved", "Chapter 12", "Synced"),
    ),
    Slide(
        eyebrow = "Rewards built in",
        title = "Earn coins, unlock twists, keep reading.",
        subtitle = "Watch a quick reward, collect streak bonuses, or upgrade when you want unlimited access.",
        icon = Icons.Rounded.LocalFireDepartment,
        tint = Tokens.Gold,
        sampleTitle = "Hearts Under Neon",
        sampleMeta = "Romance drama - premium",
        sceneLines = listOf(
            "Jules waited below the violet sign with two train tickets.",
            "Only one of them knew where the night was supposed to end.",
        ),
        chips = listOf("+50 coins", "Streak", "VIP ready"),
    ),
    Slide(
        eyebrow = "Read your way",
        title = "Make every chapter feel made for you.",
        subtitle = "Tune your theme, pace, and text comfort for late nights, commutes, and one-more-page moments.",
        icon = Icons.Rounded.DarkMode,
        tint = Tokens.ViewGreen,
        sampleTitle = "The Orchard Door",
        sampleMeta = "Cozy fantasy - custom theme",
        sceneLines = listOf(
            "The door appeared only after dusk, warm with impossible sunlight.",
            "Toma stepped through before fear could learn his name.",
        ),
        chips = listOf("Night", "Lora", "Calm pace"),
    ),
)

private val onboardingGenreChoices = listOf(
    "Romance",
    "Fantasy",
    "Werewolf",
    "Billionaire",
    "Horror",
    "Sci-Fi",
    "Mystery",
    "Drama",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: (List<String>) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val currentSlide = slides[pagerState.currentPage]
    var selectedGenres by remember { mutableStateOf(setOf<String>()) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Bg0)
    ) {
        val compactHeight = maxHeight < 720.dp

        AmbientBackdrop(
            tint = currentSlide.tint,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            OnboardingTopBar(
                page = pagerState.currentPage,
                pageCount = slides.size,
                onSkip = { onDone(emptyList()) },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compactHeight) 440.dp else 520.dp),
                ) { page ->
                    SlidePage(
                        slide = slides[page],
                        compact = compactHeight,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            OnboardingActions(
                pagerState = pagerState,
                onDone = onDone,
                selectedGenres = selectedGenres,
                onToggleGenre = { genre ->
                    selectedGenres = if (genre in selectedGenres) {
                        selectedGenres - genre
                    } else {
                        (selectedGenres + genre).take(3).toSet()
                    }
                },
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AmbientBackdrop(tint: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_backdrop")
    val drift by infiniteTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "backdrop_drift",
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF111827),
                        Tokens.Bg0,
                        Color(0xFF090B10),
                    )
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.18f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.22f, size.height * 0.18f),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Tokens.AccentRose.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.74f),
                    radius = size.width * 0.65f,
                ),
                radius = size.width * 0.66f,
                center = Offset(size.width * 0.88f, size.height * 0.74f),
            )

            val path = Path().apply {
                moveTo(0f, size.height * 0.7f)
                cubicTo(
                    size.width * 0.25f,
                    size.height * 0.62f,
                    size.width * 0.42f,
                    size.height * 0.82f,
                    size.width,
                    size.height * 0.68f,
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Tokens.Bg1.copy(alpha = 0.68f))
                ),
            )
        }

        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-86).dp + drift.dp, y = 86.dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .size(190.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 54.dp - drift.dp, y = (-72).dp)
                .blur(82.dp)
                .clip(CircleShape)
                .background(Tokens.Purple.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun OnboardingTopBar(page: Int, pageCount: Int, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(Tokens.GradientPink)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "O",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "OmniRead",
                color = Tokens.Ink0,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            PageDots(page = page, pageCount = pageCount)
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onSkip, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text("Skip", color = Tokens.Ink2, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PageDots(page: Int, pageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(if (index == page) 22.dp else 7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (index == page) Tokens.Ink0 else Color.White.copy(alpha = 0.18f))
            )
        }
    }
}

@Composable
private fun SlidePage(slide: Slide, compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StoryPreview(
            slide = slide,
            compact = compact,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 250.dp else 310.dp),
        )

        Spacer(Modifier.height(if (compact) 22.dp else 30.dp))

        Text(
            text = slide.eyebrow.uppercase(),
            color = slide.tint,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = slide.title,
            color = Tokens.Ink0,
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = slide.subtitle,
            color = Tokens.Ink2,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun StoryPreview(slide: Slide, compact: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "story_preview")
    val float by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "preview_float",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        FloatingBook(
            slide = slide,
            compact = compact,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(0.72f)
                .offset(y = float.dp)
                .rotate(-2.5f),
        )

        FeatureBadge(
            icon = Icons.Rounded.Star,
            text = slide.chips.first(),
            tint = slide.tint,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.dp, y = if (compact) 16.dp else 22.dp),
        )

        FeatureBadge(
            icon = Icons.Rounded.Bolt,
            text = slide.chips[1],
            tint = Tokens.Gold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = if (compact) (-18).dp else (-28).dp),
        )

        FeatureBadge(
            icon = Icons.Rounded.Star,
            text = slide.chips[2],
            tint = Tokens.PurpleSoft,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-4).dp, y = (-28).dp),
        )
    }
}

@Composable
private fun FloatingBook(slide: Slide, compact: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        slide.tint.copy(alpha = 0.95f),
                        Tokens.Bg2,
                        Tokens.Bg0,
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(30.dp))
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.11f),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.16f, size.height * 0.14f),
            )
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 214f,
                sweepAngle = 86f,
                useCenter = false,
                topLeft = Offset(size.width * 0.18f, size.height * 0.04f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.72f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
            )
            drawOval(
                color = Color.Black.copy(alpha = 0.16f),
                topLeft = Offset(size.width * 0.02f, size.height * 0.78f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.96f, size.height * 0.22f),
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
                        .size(if (compact) 42.dp else 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = slide.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (compact) 23.dp else 26.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Tokens.ViewGreen,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Live", color = Tokens.Ink0, style = MaterialTheme.typography.labelSmall)
                }
            }

            Column {
                Text(
                    text = slide.sampleTitle,
                    color = Color.White,
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = slide.sampleMeta,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
                SceneSnippet(lines = slide.sceneLines)
            }
        }
    }
}

@Composable
private fun SceneSnippet(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.23f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lines.forEachIndexed { index, line ->
            Text(
                text = line,
                color = if (index == 0) Tokens.Ink0 else Tokens.Ink1.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeatureBadge(icon: ImageVector, text: String, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Tokens.Bg1.copy(alpha = 0.9f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = Tokens.Ink1,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
private fun OnboardingActions(
    pagerState: PagerState,
    onDone: (List<String>) -> Unit,
    selectedGenres: Set<String>,
    onToggleGenre: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLastPage = pagerState.currentPage == slides.lastIndex

    Column(modifier = modifier.padding(bottom = 6.dp)) {
        if (isLastPage) {
            GenrePreferencePicker(
                selectedGenres = selectedGenres,
                onToggleGenre = onToggleGenre,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = { if (isLastPage) onDone(selectedGenres.toList()) else onNext() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Accent),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            AnimatedContent(
                targetState = isLastPage,
                transitionSpec = {
                    (fadeIn(tween(180)) togetherWith fadeOut(tween(180))).using(SizeTransform(clip = false))
                },
                label = "cta_text",
            ) { done ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (done) "Start reading" else "Continue",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isLastPage) "Pick up to 3 genres" else "No setup needed",
                color = Tokens.Ink3,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Tokens.Ink3.copy(alpha = 0.6f))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isLastPage) "You can skip this" else "Personalized after you read",
                color = Tokens.Ink3,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenrePreferencePicker(
    selectedGenres: Set<String>,
    onToggleGenre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        onboardingGenreChoices.forEach { genre ->
            val selected = genre in selectedGenres
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Tokens.Accent else Tokens.Bg1.copy(alpha = 0.82f))
                    .border(
                        width = 1.dp,
                        color = if (selected) Tokens.AccentSoft else Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .clickable { onToggleGenre(genre) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = genre,
                    color = if (selected) Color.White else Tokens.Ink1,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}
