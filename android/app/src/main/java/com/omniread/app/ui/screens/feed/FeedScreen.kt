package com.omniread.app.ui.screens.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniread.app.data.local.AppPrefsStore
import coil.compose.AsyncImage
import com.omniread.app.data.model.HistoryItem
import com.omniread.app.data.model.Story
import com.omniread.app.data.repo.ConfigRepository
import com.omniread.app.data.repo.StoryRepository
import com.omniread.app.data.repo.UserRepository
import com.omniread.app.ui.components.FullScreenLoading
import com.omniread.app.ui.components.PressableCard
import com.omniread.app.ui.theme.Tokens
import com.omniread.app.util.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class FeedGenreTab(val label: String, val query: String?)

private data class FeedSection(
    val title: String,
    val subtitle: String,
    val stories: List<Story>,
    val large: Boolean,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repo: StoryRepository,
    private val configRepo: ConfigRepository,
    private val userRepo: UserRepository,
    private val prefs: AppPrefsStore,
) : ViewModel() {
    val config = configRepo.config

    data class State(
        val items: List<Story> = emptyList(),
        val trendingItems: List<Story> = emptyList(),
        val newestItems: List<Story> = emptyList(),
        val recommendedItems: List<Story> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val initialized: Boolean = false,
        val genreTabs: List<FeedGenreTab> = emptyList(),
        val notificationCount: Int = 0,
        val continueItem: HistoryItem? = null,
        val readingStreak: Int? = null,
        val coinBalance: Int? = null,
        val rewardMessage: String? = null,
        val claimingDaily: Boolean = false,
        val dailyRewardPromptVisible: Boolean = true,
        val initialGenre: String? = null,
    )
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            runCatching { configRepo.refresh() }
            val cfg = configRepo.config.value
            val tabs = cfg.genres.map { g ->
                val label = if (g.emoji != null) "${g.emoji} ${g.label}" else g.label
                val key = g.key.ifBlank { g.label }
                val query = key.takeUnless { it.equals("discover", ignoreCase = true) || it.equals("all", ignoreCase = true) }
                FeedGenreTab(label = label, query = query)
            }
            _state.value = _state.value.copy(genreTabs = tabs.ifEmpty { fallbackGenres })
        }
        refreshPersonalized()
        refreshNotificationCount()
        loadHomeExtras()
    }

    private fun refreshPersonalized() {
        viewModelScope.launch {
            val localGenre = prefs.preferredGenresFlow.first().firstOrNull()
            val profileGenre = runCatching { userRepo.profile().preferredGenres?.firstOrNull() }.getOrNull()
            val genre = localGenre ?: profileGenre
            _state.value = _state.value.copy(initialGenre = genre)
            refresh(genre)
        }
    }

    fun refresh(genre: String? = null) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repo.feed(cursor = null, genre = genre) }
                .onSuccess { page ->
                    val feedItems = page.items
                    val trending = runCatching { repo.trending(genre = genre).ifEmpty { feedItems } }
                        .getOrElse { feedItems.sortedByDescending { story -> story.viewCount } }
                    val newest = runCatching { repo.newest(genre = genre).ifEmpty { feedItems } }
                        .getOrElse { feedItems.sortedByDescending { story -> story.publishedAt ?: "" } }
                    val recommended = runCatching {
                        repo.recommended().filterByGenre(genre).ifEmpty { feedItems }
                    }.getOrElse {
                        feedItems.sortedWith(compareByDescending<Story> { story -> story.avgRating }.thenByDescending { story -> story.likeCount })
                    }
                    _state.value = _state.value.copy(
                        items = feedItems,
                        trendingItems = trending,
                        newestItems = newest,
                        recommendedItems = recommended,
                        loading = false,
                        initialized = true,
                    )
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message, initialized = true) }
        }
    }

    fun refreshNotificationCount() {
        viewModelScope.launch {
            runCatching { userRepo.notifications(limit = 50) }
                .onSuccess { items ->
                    _state.value = _state.value.copy(notificationCount = items.count { !it.isRead })
                }
        }
    }

    fun loadHomeExtras() {
        viewModelScope.launch {
            runCatching { userRepo.history().firstOrNull() }
                .onSuccess { item -> _state.value = _state.value.copy(continueItem = item) }
        }
        viewModelScope.launch {
            runCatching { userRepo.profile() }
                .onSuccess { profile ->
                    _state.value = _state.value.copy(
                        readingStreak = profile.readingStreak,
                        coinBalance = profile.coinBalance,
                    )
                }
        }
    }

    fun claimDailyReward() {
        if (_state.value.claimingDaily) return
        _state.value = _state.value.copy(claimingDaily = true, rewardMessage = null)
        viewModelScope.launch {
            runCatching { userRepo.claimDaily() }
                .onSuccess {
                    val profile = runCatching { userRepo.profile() }.getOrNull()
                    _state.value = _state.value.copy(
                        claimingDaily = false,
                        readingStreak = profile?.readingStreak ?: _state.value.readingStreak,
                        coinBalance = profile?.coinBalance ?: _state.value.coinBalance,
                        rewardMessage = "Daily reward checked",
                    )
                    autoHideDailyRewardPrompt()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        claimingDaily = false,
                        rewardMessage = it.message ?: "Already checked today",
                    )
                    autoHideDailyRewardPrompt()
                }
        }
    }

    private fun autoHideDailyRewardPrompt() {
        viewModelScope.launch {
            delay(1600)
            _state.value = _state.value.copy(dailyRewardPromptVisible = false)
        }
    }
}

private val fallbackGenres = listOf(
    FeedGenreTab("Discover", null),
    FeedGenreTab("Discount", "discount"),
    FeedGenreTab("Werewolf", "Werewolf"),
    FeedGenreTab("Romance", "Romance"),
    FeedGenreTab("Fantasy", "Fantasy"),
    FeedGenreTab("Billionaire", "Billionaire"),
    FeedGenreTab("Horror", "Horror"),
    FeedGenreTab("Sci-Fi", "Sci-Fi"),
)

@Composable
fun FeedScreen(
    onOpenStory: (String) -> Unit,
    onOpenReader: (String) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    vm: FeedViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val cfg by vm.config.collectAsState()
    val genreTabs = state.genreTabs.ifEmpty { fallbackGenres }
    var selectedGenreQuery by remember { mutableStateOf<String?>(null) }
    var appliedInitialGenre by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf<FeedSection?>(null) }
    val featuredStory = remember(state.items) {
        state.items.maxWithOrNull(compareBy<Story> { it.avgRating }.thenBy { it.viewCount })
    }
    val trendingPool = remember(state.trendingItems, state.items) {
        state.trendingItems.ifEmpty { state.items.sortedByDescending { it.viewCount } }.distinctStories()
    }
    val trendingStories = remember(trendingPool) { trendingPool.take(8) }
    val shortReadsPool = remember(state.newestItems, state.items) {
        val source = state.newestItems.ifEmpty { state.items }
        source.sortedWith(compareBy<Story> { it.totalChapters.takeIf { chapters -> chapters > 0 } ?: Int.MAX_VALUE }
            .thenByDescending { it.avgRating }).distinctStories()
    }
    val shortReads = remember(shortReadsPool) { shortReadsPool.take(8) }
    val editorsChoicePool = remember(state.recommendedItems, state.items) {
        val source = state.recommendedItems.ifEmpty { state.items }
        source.sortedWith(compareByDescending<Story> { it.avgRating }.thenByDescending { it.likeCount }).distinctStories()
    }
    val editorsChoice = remember(editorsChoicePool) { editorsChoicePool.take(8) }
    val hotReadsPool = remember(state.trendingItems, state.recommendedItems, state.items) {
        (state.trendingItems + state.recommendedItems + state.items).distinctStories()
            .sortedWith(compareByDescending<Story> { it.likeCount + (it.viewCount / 100).toInt() }
            .thenByDescending { it.publishedAt ?: "" })
    }
    val hotReads = remember(hotReadsPool) { hotReadsPool.take(8) }

    LaunchedEffect(state.initialGenre, genreTabs) {
        val preferred = state.initialGenre
        if (!appliedInitialGenre && !preferred.isNullOrBlank()) {
            selectedGenreQuery = genreTabs.firstOrNull { tab ->
                tab.query.equals(preferred, ignoreCase = true) ||
                    tab.label.contains(preferred, ignoreCase = true)
            }?.query ?: preferred
            appliedInitialGenre = true
        }
    }

    if (!state.initialized && state.loading) { FullScreenLoading(); return }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(Tokens.Bg0).verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(Tokens.GradientPink)),
                        contentAlignment = Alignment.Center,
                    ) { Text("O", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(9.dp))
                    Text("OmniRead", color = Tokens.Accent, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                }
                Row {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Filled.Search, "Search", tint = Tokens.Ink1, modifier = Modifier.size(21.dp))
                    }
                    Box {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(Icons.Filled.Notifications, "Notifications", tint = Tokens.Ink1, modifier = Modifier.size(21.dp))
                        }
                        if (state.notificationCount > 0) {
                            Box(
                                modifier = Modifier.size(16.dp).offset(x = 8.dp, y = (-2).dp)
                                    .clip(CircleShape).background(Tokens.Accent).align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center,
                            ) { Text("${state.notificationCount}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            HomeQuickActions(
                continueItem = state.continueItem,
                readingStreak = state.readingStreak,
                coinBalance = state.coinBalance,
                rewardMessage = state.rewardMessage,
                claimingDaily = state.claimingDaily,
                dailyRewardPromptVisible = state.dailyRewardPromptVisible,
                onContinue = { onOpenReader(it.chapterId) },
                onClaimDaily = vm::claimDailyReward,
            )

            // Genre tabs
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                genreTabs.forEach { genre ->
                    val selected = selectedGenreQuery == genre.query
                    val color by animateColorAsState(if (selected) Tokens.Ink0 else Tokens.Ink3, label = "tab")
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedGenreQuery = genre.query
                            vm.refresh(genre.query)
                        }.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(genre.label, color = color, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (selected) {
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(20.dp).height(2.5.dp).clip(RoundedCornerShape(1.dp)).background(Tokens.Accent))
                        }
                    }
                }
            }

            // Divider
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.05f)))

            Spacer(Modifier.height(14.dp))

            when {
                state.loading && state.items.isEmpty() -> DiscoverLoading()
                state.error != null && state.items.isEmpty() -> DiscoverMessage(
                    title = "Could not load Discover",
                    body = state.error ?: "Check your connection and try again.",
                )
                state.items.isEmpty() -> DiscoverMessage(
                    title = "No stories yet",
                    body = "Published stories from the backend will appear here.",
                )
                else -> {
                    featuredStory?.let { story ->
                        FeaturedHero(story = story, onOpenStory = onOpenStory)
                        Spacer(Modifier.height(18.dp))
                        DiscoveryStats(stories = state.items)
                        Spacer(Modifier.height(22.dp))
                    }

                    SectionHeader(
                        emoji = "🔥",
                        title = "Trending Now",
                        subtitle = "Most opened this week",
                        onMore = {
                            selectedSection = FeedSection("Trending Now", "Most opened this week", trendingPool, true)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    StoryCarousel(stories = trendingStories, onOpenStory = onOpenStory, large = true)

                    Spacer(Modifier.height(22.dp))

                    if (cfg.bannerAdsEnabled) {
                        DiscoverAdSlot()
                        Spacer(Modifier.height(22.dp))
                    }

                    SectionHeader(
                        emoji = "📚",
                        title = "Quick Escapes",
                        subtitle = "Shorter reads to finish tonight",
                        onMore = {
                            selectedSection = FeedSection("Quick Escapes", "Shorter reads to finish tonight", shortReadsPool, false)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    StoryCarousel(stories = shortReads, onOpenStory = onOpenStory, large = false)

                    Spacer(Modifier.height(22.dp))

                    SectionHeader(
                        emoji = "✨",
                        title = "Editor's Choice",
                        subtitle = "High rating, strong hooks",
                        onMore = {
                            selectedSection = FeedSection("Editor's Choice", "High rating, strong hooks", editorsChoicePool, false)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    StoryCarousel(stories = editorsChoice, onOpenStory = onOpenStory, large = false)

                    Spacer(Modifier.height(22.dp))

                    SectionHeader(
                        emoji = "⚡",
                        title = "Hot Read",
                        subtitle = "Readers are reacting",
                        onMore = {
                            selectedSection = FeedSection("Hot Read", "Readers are reacting", hotReadsPool, true)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    StoryCarousel(stories = hotReads, onOpenStory = onOpenStory, large = true)
                }
            }

            Spacer(Modifier.height(100.dp))
        }

        // Search overlay
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            SearchOverlay(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = { showSearch = false; searchQuery = "" },
                stories = state.items.filter { it.title.contains(searchQuery, ignoreCase = true) },
                onOpenStory = { onOpenStory(it); showSearch = false },
            )
        }

        selectedSection?.let { section ->
            SectionDetailOverlay(
                section = section,
                onClose = { selectedSection = null },
                onOpenStory = {
                    selectedSection = null
                    onOpenStory(it)
                },
            )
        }
    }
}

@Composable
private fun HomeQuickActions(
    continueItem: HistoryItem?,
    readingStreak: Int?,
    coinBalance: Int?,
    rewardMessage: String?,
    claimingDaily: Boolean,
    dailyRewardPromptVisible: Boolean,
    onContinue: (HistoryItem) -> Unit,
    onClaimDaily: () -> Unit,
) {
    val showDailyReward = dailyRewardPromptVisible && (readingStreak != null || coinBalance != null || rewardMessage != null || claimingDaily)
    if (continueItem == null && !showDailyReward) return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        continueItem?.let { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Tokens.Bg1.copy(alpha = 0.9f))
                    .clickable { onContinue(item) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Tokens.Bg2),
                ) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Continue reading", color = Tokens.AccentSoft, style = MaterialTheme.typography.labelMedium)
                    Text(
                        item.title,
                        color = Tokens.Ink0,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Resume from your last chapter",
                        color = Tokens.Ink3,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Tokens.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (showDailyReward) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Tokens.Bg1.copy(alpha = 0.68f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Tokens.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Day ${readingStreak ?: 0} streak",
                        color = Tokens.Ink1,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        rewardMessage ?: "${coinBalance ?: 0} coins available",
                        color = Tokens.Ink3,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Tokens.Accent.copy(alpha = if (claimingDaily) 0.38f else 1f))
                        .clickable(enabled = !claimingDaily) { onClaimDaily() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (claimingDaily) "Checking" else "Claim",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    stories: List<Story>,
    onOpenStory: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Tokens.Bg0.copy(alpha = 0.97f)).statusBarsPadding().padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tokens.Bg2).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Tokens.Ink3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = Tokens.Ink0, fontSize = 15.sp),
                cursorBrush = SolidColor(Tokens.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search stories…", color = Tokens.Ink3, fontSize = 15.sp)
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "Close", tint = Tokens.Ink2, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        if (query.length >= 2) {
            stories.take(10).forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenStory(s.id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(width = 44.dp, height = 60.dp).clip(RoundedCornerShape(6.dp)).background(Tokens.Bg2)) {
                        AsyncImage(model = s.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(s.title, color = Tokens.Ink0, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(s.genre.replace("_", " "), color = Tokens.Ink3, fontSize = 12.sp)
                    }
                }
            }
            if (stories.isEmpty()) {
                Text("No results for \"$query\"", color = Tokens.Ink3, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp))
            }
        } else {
            Text("Type at least 2 characters to search", color = Tokens.Ink3, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeaturedHero(story: Story, onOpenStory: (String) -> Unit) {
    PressableCard(onClick = { onOpenStory(story.id) }, modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(268.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Tokens.Bg2),
        ) {
            AsyncImage(
                model = story.coverUrl,
                contentDescription = story.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.92f),
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverChip(text = "Featured", bg = Tokens.Accent.copy(alpha = 0.92f), fg = Color.White)
                CoverChip(text = cleanGenre(story.genre), bg = Color.Black.copy(alpha = 0.45f), fg = Tokens.Ink0)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = Tokens.Warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Top pick today", color = Tokens.Ink1, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    story.title,
                    color = Color.White,
                    fontSize = 25.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    story.hookLine ?: story.summary ?: "Start a new chapter from OmniRead.",
                    color = Tokens.Ink1,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroMeta(icon = "★", text = ratingText(story.avgRating))
                    HeroMeta(icon = "●", text = "${formatViews(story.viewCount)} reads")
                    HeroMeta(icon = "↳", text = "${story.freeChapters} free")
                }
            }
        }
    }
}

@Composable
private fun DiscoveryStats(stories: List<Story>) {
    val avgRating = stories.map { it.avgRating }.filter { it > 0.0 }.average().takeIf { !it.isNaN() } ?: 0.0
    val freeChapters = stories.sumOf { it.freeChapters }
    val genres = stories.map { it.genre }.distinct().size
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatPill(label = "Stories", value = stories.size.toString(), modifier = Modifier.weight(1f))
        StatPill(label = "Rating", value = ratingText(avgRating), modifier = Modifier.weight(1f))
        StatPill(label = "Free", value = "$freeChapters ch", modifier = Modifier.weight(1f))
        StatPill(label = "Genres", value = genres.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Tokens.Bg1)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = Tokens.Ink0, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Tokens.Ink3, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun DiscoverLoading() {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(8.dp)).background(Tokens.Bg1))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Box(Modifier.weight(1f).height(160.dp).clip(RoundedCornerShape(8.dp)).background(Tokens.Bg1))
            }
        }
    }
}

@Composable
private fun DiscoverMessage(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Tokens.Bg1)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Tokens.Ink0, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Tokens.Ink2, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DiscoverAdSlot() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Tokens.Bg1)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text("Sponsored", color = Tokens.Ink3, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        AndroidView(
            factory = { context -> AdManager.createBannerAdView(context) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        )
    }
}

@Composable
private fun SectionHeader(
    emoji: String? = null,
    title: String,
    subtitle: String? = null,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${emoji ?: ""} $title".trim(),
                style = MaterialTheme.typography.titleLarge,
                color = Tokens.Ink0,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = Tokens.Ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (onMore != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Tokens.Accent.copy(alpha = 0.13f))
                    .clickable { onMore() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("More", color = Tokens.Accent, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SectionDetailOverlay(
    section: FeedSection,
    onClose: () -> Unit,
    onOpenStory: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Bg0)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Tokens.Ink1)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(section.title, color = Tokens.Ink0, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(section.subtitle, color = Tokens.Ink3, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CoverChip(
                text = "${section.stories.size} stories",
                bg = Tokens.Accent.copy(alpha = 0.18f),
                fg = Tokens.Accent,
            )
        }

        val topStory = section.stories.firstOrNull()
        topStory?.let { story ->
            PressableCard(onClick = { onOpenStory(story.id) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Tokens.Bg2, Tokens.Bg1)))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 74.dp, height = 102.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Tokens.Bg3),
                    ) {
                        AsyncImage(
                            model = story.coverUrl,
                            contentDescription = story.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start here", color = Tokens.AccentSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            story.title,
                            color = Tokens.Ink0,
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            story.hookLine ?: story.summary ?: cleanGenre(story.genre),
                            color = Tokens.Ink2,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HeroMeta(icon = "★", text = ratingText(story.avgRating))
                            HeroMeta(icon = "●", text = "${formatViews(story.viewCount)} reads")
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(section.stories, key = { it.id }) { story ->
                SectionStoryRow(story = story, prominent = section.large, onClick = { onOpenStory(story.id) })
            }
        }
    }
}

@Composable
private fun SectionStoryRow(story: Story, prominent: Boolean, onClick: () -> Unit) {
    val coverWidth = if (prominent) 62.dp else 56.dp
    val coverHeight = if (prominent) 86.dp else 76.dp
    PressableCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Tokens.Bg1)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = coverWidth, height = coverHeight)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Tokens.Bg2),
            ) {
                AsyncImage(
                    model = story.coverUrl,
                    contentDescription = story.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    story.title,
                    color = Tokens.Ink0,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    story.hookLine ?: story.summary ?: story.authorName,
                    color = Tokens.Ink2,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(cleanGenre(story.genre), color = Tokens.AccentSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(ratingText(story.avgRating), color = Tokens.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${story.freeChapters} free", color = Tokens.Ink3, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StoryCarousel(stories: List<Story>, onOpenStory: (String) -> Unit, large: Boolean) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(stories, key = { it.id }) { story ->
            StoryCard(story = story, onClick = { onOpenStory(story.id) }, large = large)
        }
    }
}

@Composable
private fun StoryCard(story: Story, onClick: () -> Unit, large: Boolean) {
    val w = if (large) 140.dp else 115.dp
    val h = if (large) 195.dp else 160.dp

    PressableCard(onClick = onClick) {
        Column(modifier = Modifier.width(w)) {
            Box(modifier = Modifier.width(w).height(h).clip(RoundedCornerShape(8.dp)).background(Tokens.Bg2)) {
                AsyncImage(
                    model = story.coverUrl,
                    contentDescription = story.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                )
                CoverChip(
                    text = cleanGenre(story.genre),
                    bg = Color.Black.copy(alpha = 0.48f),
                    fg = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).widthIn(max = if (large) 86.dp else 70.dp),
                )
                if (story.avgRating > 0.0) {
                    CoverChip(
                        text = ratingText(story.avgRating),
                        bg = Tokens.Gold.copy(alpha = 0.94f),
                        fg = Color.Black,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.RemoveRedEye, null, tint = Tokens.ViewGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(formatViews(story.viewCount), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Star, null, tint = Tokens.Gold, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${story.freeChapters} free", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(story.title, color = Tokens.Ink1, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(story.authorName, color = Tokens.Ink3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CoverChip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroMeta(icon: String, text: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = Tokens.AccentSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun formatViews(n: Long): String = when {
    n >= 1_000_000 -> "%.2fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.2fK".format(n / 1_000.0)
    else -> n.toString()
}

private fun ratingText(value: Double): String = if (value > 0.0) "%.1f".format(value) else "New"

private fun cleanGenre(value: String): String = value.replace("_", " ").trim().ifBlank { "Story" }

private fun List<Story>.filterByGenre(genre: String?): List<Story> {
    if (genre.isNullOrBlank()) return this
    return filter { it.genre.equals(genre, ignoreCase = true) }
}

private fun List<Story>.distinctStories(): List<Story> = distinctBy { it.id }
