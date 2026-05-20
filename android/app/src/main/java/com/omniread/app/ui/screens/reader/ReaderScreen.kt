package com.omniread.app.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniread.app.data.local.ReaderSettingsStore
import com.omniread.app.data.local.ReadingTheme
import com.omniread.app.data.repo.ConfigRepository
import com.omniread.app.data.repo.ChapterFetch
import com.omniread.app.data.repo.StoryRepository
import com.omniread.app.ui.components.ErrorBanner
import com.omniread.app.ui.components.FullScreenLoading
import com.omniread.app.ui.theme.Tokens
import com.omniread.app.util.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repo: StoryRepository,
    private val readerSettings: ReaderSettingsStore,
    private val tokenStore: com.omniread.app.data.local.TokenStore,
    configRepo: ConfigRepository,
) : ViewModel() {
    val config = configRepo.config

    data class State(
        val fetch: ChapterFetch? = null,
        val loading: Boolean = false,
        val unlocking: Boolean = false,
        val error: String? = null,
        val justUnlockedNewBalance: Int? = null,
        val fontSizeSp: Int = ReaderSettingsStore.DEFAULT_FONT_SP,
        val theme: ReadingTheme = ReadingTheme.DARK,
        val userId: String? = null,
    )

    private val _state = MutableStateFlow(State(loading = true))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            readerSettings.fontSizeFlow.collect { sp ->
                _state.value = _state.value.copy(fontSizeSp = sp)
            }
        }
        viewModelScope.launch {
            readerSettings.themeFlow.collect { theme ->
                _state.value = _state.value.copy(theme = theme)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(userId = tokenStore.userId())
        }
    }

    fun setFontSize(sp: Int) {
        viewModelScope.launch { readerSettings.setFontSize(sp) }
    }

    fun setTheme(theme: ReadingTheme) {
        viewModelScope.launch { readerSettings.setTheme(theme) }
    }

    fun load(chapterId: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repo.chapter(chapterId) }
                .onSuccess { _state.value = _state.value.copy(fetch = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun unlock(chapterId: String, idempotency: String? = null) {
        _state.value = _state.value.copy(unlocking = true, error = null)
        viewModelScope.launch {
            runCatching { repo.unlockWithCoins(chapterId, idempotency) }
                .onSuccess { resp ->
                    _state.value = _state.value.copy(justUnlockedNewBalance = resp.newBalance)
                    load(chapterId)
                }
                .onFailure {
                    _state.value = _state.value.copy(unlocking = false, error = it.message)
                }
        }
    }

    fun saveProgress(chapterId: String, scrollPosition: Int, completed: Boolean) {
        viewModelScope.launch {
            runCatching { repo.saveProgress(chapterId, scrollPosition, completed) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapterId: String,
    onBack: () -> Unit,
    onOpenCoinStore: () -> Unit,
    onOpenChapter: (String) -> Unit = {},
    vm: ReaderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val cfg by vm.config.collectAsState()
    LaunchedEffect(chapterId) { vm.load(chapterId) }

    // Theme-derived colors
    val bgColor = when (state.theme) {
        ReadingTheme.DARK -> Color(0xFF0B0B0F)
        ReadingTheme.SEPIA -> Color(0xFFF5ECD7)
        ReadingTheme.WHITE -> Color(0xFFFFFFFF)
    }
    val textColor = when (state.theme) {
        ReadingTheme.DARK -> Color(0xFFE0E0F0)
        ReadingTheme.SEPIA -> Color(0xFF3B2E1E)
        ReadingTheme.WHITE -> Color(0xFF1A1A1A)
    }
    val topBarColor = when (state.theme) {
        ReadingTheme.DARK -> Color(0xFF0B0B0F)
        ReadingTheme.SEPIA -> Color(0xFFEDD9B4)
        ReadingTheme.WHITE -> Color(0xFFF8F8F8)
    }

    val view = LocalView.current
    val listState = rememberLazyListState()
    val scrollPosition by remember { derivedStateOf { listState.firstVisibleItemScrollOffset + listState.firstVisibleItemIndex * 1000 } }

    // Scroll-fraction across the chapter for progress bar
    val progressFraction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) 0f else {
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                (last + 1).toFloat() / total.toFloat()
            }
        }
    }

    var showFontPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    LaunchedEffect(scrollPosition) {
        snapshotProgress(state, vm, chapterId, scrollPosition)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state.fetch as? ChapterFetch.Unlocked)?.content?.title
                        ?: (state.fetch as? ChapterFetch.Locked)?.preview?.title
                    Text(title ?: "Chapter", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.fetch is ChapterFetch.Unlocked) {
                        IconButton(onClick = { showThemePicker = !showThemePicker }) {
                            Icon(Icons.Filled.Palette, contentDescription = "Theme")
                        }
                        IconButton(onClick = { showFontPicker = !showFontPicker }) {
                            Icon(Icons.Filled.FormatSize, contentDescription = "Font size")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor),
            )
        },
        containerColor = bgColor,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Progress bar (only when reading unlocked content)
                if (state.fetch is ChapterFetch.Unlocked) {
                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Tokens.Accent,
                        trackColor = bgColor,
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.loading && state.fetch == null -> FullScreenLoading()
                        state.error != null && state.fetch == null -> ErrorBanner(state.error ?: "Failed to load")
                        state.fetch is ChapterFetch.Unlocked -> UnlockedView(
                            listState = listState,
                            text = (state.fetch as ChapterFetch.Unlocked).content.content,
                            fontSizeSp = state.fontSizeSp,
                            textColor = textColor,
                            bgColor = bgColor,
                            nextChapterId = (state.fetch as ChapterFetch.Unlocked).content.nextChapterId,
                            onNextChapter = { nextId ->
                                val current = (state.fetch as? ChapterFetch.Unlocked)?.content
                                if (current != null) {
                                    vm.saveProgress(current.id, scrollPosition, completed = true)
                                }
                                val activity = view.context as? android.app.Activity
                                if (cfg.interstitialAdsEnabled && activity != null) {
                                    AdManager.maybeShowInterstitial(
                                        activity = activity,
                                        onDismissed = { onOpenChapter(nextId) },
                                        onSkipped = { onOpenChapter(nextId) },
                                    )
                                } else {
                                    onOpenChapter(nextId)
                                }
                            },
                        )
                        state.fetch is ChapterFetch.Locked -> LockedView(
                            preview = (state.fetch as ChapterFetch.Locked).preview.preview,
                            cost = (state.fetch as ChapterFetch.Locked).preview.coinCost,
                            chapterNumber = (state.fetch as ChapterFetch.Locked).preview.chapterNumber,
                            isUnlocking = state.unlocking,
                            onUnlock = { vm.unlock(chapterId) },
                            onWatchAd = {
                                val activity = (view.context as? android.app.Activity) ?: return@LockedView
                                com.omniread.app.util.AdManager.showRewardedAd(
                                    activity = activity,
                                    userId = state.userId,
                                    customData = "chapter:$chapterId",
                                    onRewarded = { _, _ -> vm.unlock(chapterId) },
                                    onDismissed = {},
                                    onFailed = {},
                                )
                            },
                            onCoinStore = onOpenCoinStore,
                        )
                    }
                }
            }

            if (showFontPicker) {
                FontSizePicker(
                    current = state.fontSizeSp,
                    onPick = { sp -> vm.setFontSize(sp) },
                    onDismiss = { showFontPicker = false },
                )
            }
            if (showThemePicker) {
                ThemePicker(
                    current = state.theme,
                    onPick = { theme -> vm.setTheme(theme) },
                    onDismiss = { showThemePicker = false },
                )
            }
        }
    }
}

private fun snapshotProgress(state: ReaderViewModel.State, vm: ReaderViewModel, chapterId: String, position: Int) {
    val unlocked = state.fetch as? ChapterFetch.Unlocked ?: return
    if (position % 200 == 0 && position > 0) {
        vm.saveProgress(unlocked.content.id, position, completed = false)
    }
}

@Composable
private fun UnlockedView(
    listState: androidx.compose.foundation.lazy.LazyListState,
    text: String,
    fontSizeSp: Int,
    textColor: Color = Tokens.Ink1,
    bgColor: Color = Tokens.Bg0,
    nextChapterId: String? = null,
    onNextChapter: (String) -> Unit = {},
) {
    val paragraphs = remember(text) { text.split("\n").filter { it.isNotBlank() } }
    val lineHeightSp = (fontSizeSp * 1.7f).sp
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(bgColor).padding(horizontal = 22.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        items(paragraphs.size) { idx ->
            Text(
                paragraphs[idx],
                color = textColor,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = fontSizeSp.sp,
                    lineHeight = lineHeightSp,
                ),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item {
            Spacer(Modifier.height(32.dp))
            if (nextChapterId != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(Tokens.GradientPink))
                        .clickable { onNextChapter(nextChapterId) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Next Chapter →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tokens.Bg2).padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("End of available chapters", color = Tokens.Ink3, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun FontSizePicker(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    // Scrim
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Sheet
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Tokens.Bg1).padding(20.dp)
                .clickable(enabled = false) {},
        ) {
            Text("Text size", color = Tokens.Ink0, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ReaderSettingsStore.PRESETS.forEach { (label, sp) ->
                    val selected = current == sp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { onPick(sp) }.padding(8.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(if (selected) Tokens.Accent else Tokens.Bg2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Aa",
                                color = if (selected) Color.White else Tokens.Ink1,
                                fontSize = (sp.coerceAtMost(20)).sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(label, color = if (selected) Tokens.Accent else Tokens.Ink2, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ThemePicker(current: ReadingTheme, onPick: (ReadingTheme) -> Unit, onDismiss: () -> Unit) {
    val themes = listOf(
        ReadingTheme.DARK to Pair(Color(0xFF0B0B0F), "Dark"),
        ReadingTheme.SEPIA to Pair(Color(0xFFF5ECD7), "Sepia"),
        ReadingTheme.WHITE to Pair(Color(0xFFFFFFFF), "White"),
    )
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Tokens.Bg1).padding(20.dp).clickable(enabled = false) {},
        ) {
            Text("Reading theme", color = Tokens.Ink0, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                themes.forEach { (theme, pair) ->
                    val (bg, label) = pair
                    val selected = current == theme
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onPick(theme) }.padding(8.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .then(if (selected) Modifier.border(2.dp, Tokens.Accent, RoundedCornerShape(12.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Aa", color = if (theme == ReadingTheme.DARK) Color.White else Color(0xFF333333), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(label, color = if (selected) Tokens.Accent else Tokens.Ink2, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LockedView(
    preview: String,
    cost: Int,
    chapterNumber: Int,
    isUnlocking: Boolean,
    onUnlock: () -> Unit,
    onWatchAd: () -> Unit,
    onCoinStore: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "Chapter $chapterNumber",
                color = Tokens.Ink2,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                preview,
                color = Tokens.Ink1,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                ),
            )
            Spacer(Modifier.height(20.dp))
            Text("…", color = Tokens.Ink3, style = MaterialTheme.typography.headlineMedium)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Tokens.Bg1)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = Tokens.Gold)
                Spacer(Modifier.height(0.dp))
                Text("  Continue the story", color = Tokens.Ink0, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onUnlock,
                enabled = !isUnlocking,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Accent),
            ) {
                Text(if (isUnlocking) "Unlocking…" else "🪙 Unlock with $cost coins", color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onWatchAd,
                enabled = !isUnlocking,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Success),
            ) {
                Text("📺 Watch ad to unlock (free!)", color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Or get unlimited access with VIP",
                color = Tokens.Ink3,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onCoinStore() }.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
