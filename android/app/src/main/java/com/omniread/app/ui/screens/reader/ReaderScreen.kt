package com.omniread.app.ui.screens.reader

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniread.app.data.local.OfflineChapterStore
import com.omniread.app.data.local.ReaderFontChoice
import com.omniread.app.data.local.ReaderSettingsStore
import com.omniread.app.data.local.ReadingTheme
import com.omniread.app.data.model.ChapterContent
import com.omniread.app.data.model.Story
import com.omniread.app.data.repo.ConfigRepository
import com.omniread.app.data.repo.ChapterFetch
import com.omniread.app.data.repo.StoryRepository
import com.omniread.app.data.repo.UserRepository
import com.omniread.app.ui.screens.detail.CommentData
import com.omniread.app.ui.components.ErrorBanner
import com.omniread.app.ui.components.FullScreenLoading
import com.omniread.app.ui.theme.Tokens
import com.omniread.app.util.AdManager
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repo: StoryRepository,
    private val readerSettings: ReaderSettingsStore,
    private val offlineStore: OfflineChapterStore,
    private val userRepo: UserRepository,
    private val tokenStore: com.omniread.app.data.local.TokenStore,
    configRepo: ConfigRepository,
) : ViewModel() {
    val config = configRepo.config

    data class State(
        val fetch: ChapterFetch? = null,
        val loading: Boolean = false,
        val unlocking: Boolean = false,
        val error: String? = null,
        val unlockMessage: String? = null,
        val justUnlockedNewBalance: Int? = null,
        val relatedStories: List<Story> = emptyList(),
        val fontSizeSp: Int = ReaderSettingsStore.DEFAULT_FONT_SP,
        val theme: ReadingTheme = ReadingTheme.DARK,
        val lineSpacing: Float = ReaderSettingsStore.DEFAULT_LINE_SPACING,
        val fontChoice: ReaderFontChoice = ReaderFontChoice.SERIF,
        val brightness: Float = ReaderSettingsStore.DEFAULT_BRIGHTNESS,
        val lightweightMode: Boolean = false,
        val offlineSaved: Boolean = false,
        val saveOfflineMessage: String? = null,
        val coinBalance: Int? = null,
        val comments: List<CommentData> = emptyList(),
        val commentsLoading: Boolean = false,
        val commentPosting: Boolean = false,
        val commentMessage: String? = null,
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
            readerSettings.lineSpacingFlow.collect { spacing ->
                _state.value = _state.value.copy(lineSpacing = spacing)
            }
        }
        viewModelScope.launch {
            readerSettings.fontChoiceFlow.collect { choice ->
                _state.value = _state.value.copy(fontChoice = choice)
            }
        }
        viewModelScope.launch {
            readerSettings.brightnessFlow.collect { brightness ->
                _state.value = _state.value.copy(brightness = brightness)
            }
        }
        viewModelScope.launch {
            readerSettings.lightweightFlow.collect { enabled ->
                _state.value = _state.value.copy(lightweightMode = enabled)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(userId = tokenStore.userId())
        }
        refreshBalance()
    }

    fun setFontSize(sp: Int) {
        viewModelScope.launch { readerSettings.setFontSize(sp) }
    }

    fun setTheme(theme: ReadingTheme) {
        viewModelScope.launch { readerSettings.setTheme(theme) }
    }

    fun setLineSpacing(value: Float) {
        viewModelScope.launch { readerSettings.setLineSpacing(value) }
    }

    fun setFontChoice(choice: ReaderFontChoice) {
        viewModelScope.launch { readerSettings.setFontChoice(choice) }
    }

    fun setBrightness(value: Float) {
        viewModelScope.launch { readerSettings.setBrightness(value) }
    }

    fun setLightweight(enabled: Boolean) {
        viewModelScope.launch { readerSettings.setLightweight(enabled) }
    }

    fun load(chapterId: String) {
        _state.value = _state.value.copy(
            loading = true,
            error = null,
            unlockMessage = null,
            saveOfflineMessage = null,
            commentMessage = null,
        )
        viewModelScope.launch {
            runCatching { repo.chapter(chapterId) }
                .onSuccess {
                    val saved = offlineStore.isSaved(chapterId)
                    _state.value = _state.value.copy(
                        fetch = it,
                        loading = false,
                        unlocking = false,
                        offlineSaved = saved,
                    )
                    val storyId = when (it) {
                        is ChapterFetch.Unlocked -> it.content.storyId
                        is ChapterFetch.Locked -> it.preview.storyId
                    }
                    if (it is ChapterFetch.Unlocked) loadComments(it.content.id)
                    loadRelated(storyId)
                }
                .onFailure { error ->
                    val offline = offlineStore.get(chapterId)
                    if (offline != null) {
                        _state.value = _state.value.copy(
                            fetch = ChapterFetch.Unlocked(offline),
                            loading = false,
                            unlocking = false,
                            offlineSaved = true,
                            error = "Offline saved copy",
                        )
                        loadComments(offline.id)
                        loadRelated(offline.storyId)
                    } else {
                        _state.value = _state.value.copy(loading = false, unlocking = false, error = error.message)
                    }
                }
        }
    }

    private fun loadRelated(storyId: String) {
        viewModelScope.launch {
            runCatching { repo.related(storyId).filterNot { it.id == storyId }.take(6) }
                .onSuccess { related -> _state.value = _state.value.copy(relatedStories = related) }
        }
    }

    fun unlock(chapterId: String, idempotency: String? = null) {
        _state.value = _state.value.copy(unlocking = true, error = null, unlockMessage = "Unlocking chapter...")
        viewModelScope.launch {
            runCatching { repo.unlockWithCoins(chapterId, idempotency) }
                .onSuccess { resp ->
                    _state.value = _state.value.copy(
                        justUnlockedNewBalance = resp.newBalance,
                        coinBalance = resp.newBalance,
                        unlockMessage = "Unlocked. ${resp.newBalance} coins left.",
                    )
                    load(chapterId)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        unlocking = false,
                        error = it.message,
                        unlockMessage = "Could not unlock with coins.",
                    )
                }
        }
    }

    fun setUnlockMessage(message: String?) {
        _state.value = _state.value.copy(unlockMessage = message)
    }

    fun refreshBalance() {
        viewModelScope.launch {
            runCatching { userRepo.profile() }
                .onSuccess { profile ->
                    _state.value = _state.value.copy(coinBalance = profile.coinBalance)
                }
        }
    }

    fun saveOffline() {
        val content = (_state.value.fetch as? ChapterFetch.Unlocked)?.content ?: return
        _state.value = _state.value.copy(saveOfflineMessage = "Saving chapter...")
        viewModelScope.launch {
            runCatching { offlineStore.save(content) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        offlineSaved = true,
                        saveOfflineMessage = "Saved offline",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(saveOfflineMessage = "Could not save offline")
                }
        }
    }

    fun loadComments(chapterId: String) {
        _state.value = _state.value.copy(commentsLoading = true, commentMessage = null)
        viewModelScope.launch {
            runCatching { repo.chapterComments(chapterId) }
                .onSuccess { comments ->
                    _state.value = _state.value.copy(comments = comments, commentsLoading = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(commentsLoading = false)
                }
        }
    }

    fun postComment(chapterId: String, content: String) {
        if (content.isBlank() || _state.value.commentPosting) return
        _state.value = _state.value.copy(commentPosting = true, commentMessage = null)
        viewModelScope.launch {
            runCatching { repo.postChapterComment(chapterId, content.trim()) }
                .onSuccess {
                    _state.value = _state.value.copy(commentPosting = false, commentMessage = "Comment posted")
                    loadComments(chapterId)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        commentPosting = false,
                        commentMessage = it.message ?: "Could not post comment",
                    )
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
    onHome: () -> Unit = onBack,
    onOpenCoinStore: () -> Unit,
    onOpenChapter: (String) -> Unit = {},
    onOpenStory: (String) -> Unit = {},
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

    var showControls by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(scrollPosition) {
        snapshotProgress(state, vm, chapterId, scrollPosition)
    }

    val activity = view.context as? Activity
    DisposableEffect(activity, state.brightness) {
        val window = activity?.window
        val attrs = window?.attributes
        val originalBrightness = attrs?.screenBrightness
        if (window != null && attrs != null) {
            attrs.screenBrightness = state.brightness.coerceIn(
                ReaderSettingsStore.MIN_BRIGHTNESS,
                ReaderSettingsStore.MAX_BRIGHTNESS,
            )
            window.attributes = attrs
        }
        onDispose {
            if (window != null && attrs != null) {
                attrs.screenBrightness = originalBrightness ?: -1f
                window.attributes = attrs
            }
        }
    }

    Scaffold(
        topBar = {
            if (showControls || state.fetch !is ChapterFetch.Unlocked) {
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
                            IconButton(onClick = { vm.saveOffline() }) {
                                Icon(
                                    if (state.offlineSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "Save offline",
                                )
                            }
                            IconButton(onClick = { showSettingsSheet = true }) {
                                Icon(Icons.Filled.Tune, contentDescription = "Reader controls")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor),
                )
            }
        },
        containerColor = bgColor,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(state.fetch) {
                    detectTapGestures {
                        if (state.fetch is ChapterFetch.Unlocked) showControls = !showControls
                    }
                },
        ) {
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
                            chapter = (state.fetch as ChapterFetch.Unlocked).content,
                            fontSizeSp = state.fontSizeSp,
                            lineSpacing = state.lineSpacing,
                            fontChoice = state.fontChoice,
                            textColor = textColor,
                            bgColor = bgColor,
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
                            onBackToStory = onBack,
                            onHome = onHome,
                            relatedStories = state.relatedStories,
                            onOpenStory = onOpenStory,
                            lightweightMode = state.lightweightMode,
                            comments = state.comments,
                            commentsLoading = state.commentsLoading,
                            commentPosting = state.commentPosting,
                            commentMessage = state.commentMessage,
                            onPostComment = { vm.postComment(chapterId, it) },
                        )
                        state.fetch is ChapterFetch.Locked -> LockedView(
                            title = (state.fetch as ChapterFetch.Locked).preview.title,
                            preview = (state.fetch as ChapterFetch.Locked).preview.preview,
                            cost = (state.fetch as ChapterFetch.Locked).preview.coinCost,
                            chapterNumber = (state.fetch as ChapterFetch.Locked).preview.chapterNumber,
                            isUnlocking = state.unlocking,
                            message = state.unlockMessage,
                            error = state.error,
                            coinBalance = state.coinBalance,
                            onUnlock = { vm.unlock(chapterId) },
                            onWatchAd = {
                                if (activity == null) {
                                    vm.setUnlockMessage("Could not open an ad on this device.")
                                } else {
                                    var rewardEarned = false
                                    vm.setUnlockMessage("Opening rewarded ad...")
                                    com.omniread.app.util.AdManager.showRewardedAd(
                                        activity = activity,
                                        userId = state.userId,
                                        customData = "chapter:$chapterId",
                                        onRewarded = { _, _ ->
                                            rewardEarned = true
                                            vm.setUnlockMessage("Ad watched. Waiting for secure reward verification...")
                                        },
                                        onDismissed = {
                                            if (!rewardEarned) {
                                                vm.setUnlockMessage("Watch the full rewarded ad to unlock this chapter.")
                                            }
                                        },
                                        onFailed = { message ->
                                            vm.setUnlockMessage(message.ifBlank { "Ad is not ready. Try again in a moment." })
                                        },
                                    )
                                }
                            },
                            onCoinStore = onOpenCoinStore,
                        )
                    }
                }
            }

            if (state.fetch is ChapterFetch.Unlocked && (showControls || !state.saveOfflineMessage.isNullOrBlank())) {
                ReaderMiniBar(
                    savedOffline = state.offlineSaved,
                    saveMessage = state.saveOfflineMessage,
                    onSaveOffline = { vm.saveOffline() },
                    onOpenSettings = { showSettingsSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            if (showSettingsSheet) {
                ReaderSettingsSheet(
                    state = state,
                    onDismiss = { showSettingsSheet = false },
                    onFontSize = vm::setFontSize,
                    onTheme = vm::setTheme,
                    onLineSpacing = vm::setLineSpacing,
                    onFontChoice = vm::setFontChoice,
                    onBrightness = vm::setBrightness,
                    onLightweight = vm::setLightweight,
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
    chapter: ChapterContent,
    fontSizeSp: Int,
    lineSpacing: Float,
    fontChoice: ReaderFontChoice,
    textColor: Color = Tokens.Ink1,
    bgColor: Color = Tokens.Bg0,
    onNextChapter: (String) -> Unit = {},
    onBackToStory: () -> Unit = {},
    onHome: () -> Unit = {},
    relatedStories: List<Story> = emptyList(),
    onOpenStory: (String) -> Unit = {},
    lightweightMode: Boolean = false,
    comments: List<CommentData> = emptyList(),
    commentsLoading: Boolean = false,
    commentPosting: Boolean = false,
    commentMessage: String? = null,
    onPostComment: (String) -> Unit = {},
) {
    val paragraphs = remember(chapter.content) { chapter.content.split("\n").filter { it.isNotBlank() } }
    val lineHeightSp = (fontSizeSp * lineSpacing).sp
    val bodyFontFamily = when (fontChoice) {
        ReaderFontChoice.SERIF -> FontFamily.Serif
        ReaderFontChoice.SANS -> FontFamily.SansSerif
        ReaderFontChoice.MONO -> FontFamily.Monospace
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(bgColor),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Chapter ${chapter.chapterNumber}",
                    color = textColor.copy(alpha = 0.52f),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (!chapter.title.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        chapter.title,
                        color = textColor,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        items(paragraphs.size) { idx ->
            Text(
                paragraphs[idx],
                color = textColor,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = bodyFontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = lineHeightSp,
                ),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
        item {
            Spacer(Modifier.height(24.dp))
            ChapterEndPanel(
                hasNext = chapter.nextChapterId != null,
                onNext = { chapter.nextChapterId?.let(onNextChapter) },
                onBackToStory = onBackToStory,
                onHome = onHome,
                relatedStories = relatedStories,
                onOpenStory = onOpenStory,
                lightweightMode = lightweightMode,
                comments = comments,
                commentsLoading = commentsLoading,
                commentPosting = commentPosting,
                commentMessage = commentMessage,
                onPostComment = onPostComment,
            )
        }
    }
}

@Composable
private fun ChapterEndPanel(
    hasNext: Boolean,
    onNext: () -> Unit,
    onBackToStory: () -> Unit,
    onHome: () -> Unit,
    relatedStories: List<Story>,
    onOpenStory: (String) -> Unit,
    lightweightMode: Boolean,
    comments: List<CommentData>,
    commentsLoading: Boolean,
    commentPosting: Boolean,
    commentMessage: String?,
    onPostComment: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Tokens.Bg1.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (hasNext) Tokens.Success else Tokens.Gold,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasNext) "Chapter complete" else "You are caught up",
                    color = Tokens.Ink0,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (hasNext) "Progress saved" else "More chapters coming soon",
                    color = Tokens.Ink3,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (hasNext) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Accent),
            ) {
                Text("Next chapter", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryReaderAction(
                label = "Story",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBackToStory,
                modifier = Modifier.weight(1f),
            )
            SecondaryReaderAction(
                label = "Home",
                icon = Icons.Filled.Home,
                onClick = onHome,
                modifier = Modifier.weight(1f),
            )
        }
        if (relatedStories.isNotEmpty() && !lightweightMode) {
            Spacer(Modifier.height(12.dp))
            Text("More from this vibe", color = Tokens.Ink2, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(relatedStories.take(4), key = { it.id }) { story ->
                    RelatedStoryChip(story = story, onClick = { onOpenStory(story.id) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ReaderComments(
            comments = comments,
            loading = commentsLoading,
            posting = commentPosting,
            message = commentMessage,
            onPost = onPostComment,
        )
    }
}

@Composable
private fun SecondaryReaderAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(13.dp))
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Tokens.Ink1, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Tokens.Ink1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RelatedStoryChip(story: Story, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(190.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Tokens.Bg2),
        ) {
            AsyncImage(
                model = story.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                story.title,
                color = Tokens.Ink0,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                story.genre.replace("_", " "),
                color = Tokens.Ink3,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReaderMiniBar(
    savedOffline: Boolean,
    saveMessage: String?,
    onSaveOffline: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Tokens.Bg1.copy(alpha = 0.95f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniIconAction(
            icon = if (savedOffline) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            label = saveMessage ?: if (savedOffline) "Offline" else "Save",
            onClick = onSaveOffline,
        )
        Box(Modifier.width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.1f)))
        MiniIconAction(
            icon = Icons.Filled.Tune,
            label = "Controls",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun MiniIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Tokens.Ink1, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Tokens.Ink1, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun ReaderSettingsSheet(
    state: ReaderViewModel.State,
    onDismiss: () -> Unit,
    onFontSize: (Int) -> Unit,
    onTheme: (ReadingTheme) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onFontChoice: (ReaderFontChoice) -> Unit,
    onBrightness: (Float) -> Unit,
    onLightweight: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Tokens.Bg1)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clickable(enabled = false) {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reader controls", color = Tokens.Ink0, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", color = Tokens.Accent) }
            }
            Spacer(Modifier.height(8.dp))

            SettingLabel("Text size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReaderSettingsStore.PRESETS.forEach { (label, sp) ->
                    val selected = state.fontSizeSp == sp
                    SegmentChip(
                        label = label,
                        selected = selected,
                        onClick = { onFontSize(sp) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingLabel("Font")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    ReaderFontChoice.SERIF to "Serif",
                    ReaderFontChoice.SANS to "Sans",
                    ReaderFontChoice.MONO to "Mono",
                ).forEach { (choice, label) ->
                    SegmentChip(
                        label = label,
                        selected = state.fontChoice == choice,
                        onClick = { onFontChoice(choice) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingLabel("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeChip(
                    theme = ReadingTheme.DARK,
                    label = "Dark",
                    icon = Icons.Filled.DarkMode,
                    selected = state.theme == ReadingTheme.DARK,
                    onClick = onTheme,
                    modifier = Modifier.weight(1f),
                )
                ThemeChip(
                    theme = ReadingTheme.SEPIA,
                    label = "Sepia",
                    icon = Icons.Filled.WbSunny,
                    selected = state.theme == ReadingTheme.SEPIA,
                    onClick = onTheme,
                    modifier = Modifier.weight(1f),
                )
                ThemeChip(
                    theme = ReadingTheme.WHITE,
                    label = "White",
                    icon = Icons.Filled.LightMode,
                    selected = state.theme == ReadingTheme.WHITE,
                    onClick = onTheme,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            SettingSlider(
                label = "Line spacing",
                value = state.lineSpacing,
                range = ReaderSettingsStore.MIN_LINE_SPACING..ReaderSettingsStore.MAX_LINE_SPACING,
                onValue = onLineSpacing,
            )
            SettingSlider(
                label = "Brightness",
                value = state.brightness,
                range = ReaderSettingsStore.MIN_BRIGHTNESS..ReaderSettingsStore.MAX_BRIGHTNESS,
                onValue = onBrightness,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Tokens.Bg2.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lightweight mode", color = Tokens.Ink1, style = MaterialTheme.typography.labelLarge)
                    Text("Reduces end-of-chapter extras", color = Tokens.Ink3, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.lightweightMode, onCheckedChange = onLightweight)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, color = Tokens.Ink3, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Tokens.Accent else Tokens.Bg2)
            .border(1.dp, if (selected) Tokens.AccentSoft else Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = if (selected) Color.White else Tokens.Ink1, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun ThemeChip(
    theme: ReadingTheme,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: (ReadingTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentChipWithIcon(
        label = label,
        icon = icon,
        selected = selected,
        onClick = { onClick(theme) },
        modifier = modifier,
    )
}

@Composable
private fun SegmentChipWithIcon(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Tokens.Accent else Tokens.Bg2)
            .border(1.dp, if (selected) Tokens.AccentSoft else Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else Tokens.Ink2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (selected) Color.White else Tokens.Ink1, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Tokens.Ink2, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(98.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReaderComments(
    comments: List<CommentData>,
    loading: Boolean,
    posting: Boolean,
    message: String?,
    onPost: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chapter comments", color = Tokens.Ink1, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                if (loading) "Loading" else "${comments.size}",
                color = Tokens.Ink3,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(500) },
                textStyle = TextStyle(color = Tokens.Ink0, fontSize = 14.sp),
                cursorBrush = SolidColor(Tokens.Accent),
                singleLine = false,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp, max = 92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Tokens.Bg2.copy(alpha = 0.78f))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("React to this chapter...", color = Tokens.Ink3, fontSize = 13.sp)
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (text.isNotBlank() && !posting) Tokens.Accent else Tokens.Bg2)
                    .clickable(enabled = text.isNotBlank() && !posting) {
                        onPost(text)
                        text = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = if (message.contains("Could not", true)) Tokens.Danger else Tokens.Success, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (comments.isEmpty() && !loading) {
            Text("No comments yet", color = Tokens.Ink3, style = MaterialTheme.typography.bodySmall)
        } else {
            comments.take(3).forEach { comment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Tokens.Accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(comment.username?.firstOrNull()?.uppercase() ?: "?", color = Tokens.Accent, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(comment.username ?: "Reader", color = Tokens.Ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(comment.content, color = Tokens.Ink1, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedView(
    title: String?,
    preview: String,
    cost: Int,
    chapterNumber: Int,
    isUnlocking: Boolean,
    message: String?,
    error: String?,
    coinBalance: Int?,
    onUnlock: () -> Unit,
    onWatchAd: () -> Unit,
    onCoinStore: () -> Unit,
) {
    val missingCoins = ((cost) - (coinBalance ?: 0)).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Bg0)
            .padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Tokens.Gold.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Tokens.Gold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Chapter $chapterNumber",
                        color = Tokens.Ink2,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        title?.takeIf { it.isNotBlank() } ?: "Locked chapter",
                        color = Tokens.Ink0,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Tokens.Bg1, Tokens.Bg2.copy(alpha = 0.72f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(18.dp),
            ) {
                Column {
                    Text("Preview", color = Tokens.AccentSoft, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        preview,
                        color = Tokens.Ink1,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            lineHeight = 30.sp,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Unlock to continue reading.",
                        color = Tokens.Ink3,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Tokens.Bg1.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = Tokens.Gold)
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Continue the story", color = Tokens.Ink0, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (coinBalance != null) "${coinBalance} coins available" else "Coin unlock is permanent for this account.",
                        color = Tokens.Ink3,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Tokens.Gold.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("$cost coins", color = Tokens.Gold, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (missingCoins > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Need $missingCoins more coins, or unlock with a rewarded ad.",
                    color = Tokens.Gold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!message.isNullOrBlank() || !error.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (error.isNullOrBlank()) Tokens.Bg2 else Tokens.Danger.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = error ?: message.orEmpty(),
                        color = if (error.isNullOrBlank()) Tokens.Ink2 else Tokens.Danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = if (missingCoins > 0) onCoinStore else onUnlock,
                enabled = !isUnlocking,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Accent),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isUnlocking -> "Unlocking..."
                        missingCoins > 0 -> "Get $missingCoins coins"
                        else -> "Unlock with $cost coins"
                    },
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onWatchAd,
                enabled = !isUnlocking,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Success),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Watch ad to unlock free", color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onCoinStore() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = Tokens.Ink2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Coins and VIP options", color = Tokens.Ink2, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Ads are optional and verified by the server. If an ad is unavailable, no coins are spent.",
                color = Tokens.Ink3,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
