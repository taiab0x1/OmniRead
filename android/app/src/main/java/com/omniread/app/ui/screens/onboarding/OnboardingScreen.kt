package com.omniread.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omniread.app.ui.theme.Tokens
import kotlinx.coroutines.launch

private data class Slide(val title: String, val subtitle: String)

private val slides = listOf(
    Slide("Discover stories", "A vertical feed of bite-sized fiction tuned to your taste."),
    Slide("Bookmarks travel with you", "Your library and progress sync across sessions."),
    Slide("Earn coins, unlock cliffhangers", "Watch a quick ad or go premium for unlimited access."),
    Slide("Read on your terms", "Adjust theme, font, and pacing to make every chapter yours."),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) {
            Text("Skip", color = Tokens.Ink2)
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(360.dp)) { page ->
            val s = slides[page]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            ) {
                Text(
                    s.title,
                    color = Tokens.Ink0,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    s.subtitle,
                    color = Tokens.Ink2,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Button(
            onClick = {
                if (pagerState.currentPage == slides.lastIndex) onDone()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (pagerState.currentPage == slides.lastIndex) "Get started" else "Next")
        }
    }
}
