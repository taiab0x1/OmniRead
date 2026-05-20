package com.omniread.app.ui.screens.detail

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.omniread.app.data.api.ApiException
import com.omniread.app.data.local.TokenStore
import com.omniread.app.data.model.ChapterListItem
import com.omniread.app.data.model.Story
import com.omniread.app.data.repo.StoryRepository
import com.omniread.app.data.repo.UserRepository
import com.omniread.app.ui.components.ErrorBanner
import com.omniread.app.ui.components.FullScreenLoading
import com.omniread.app.ui.components.Pill
import com.omniread.app.ui.theme.Tokens
import com.omniread.app.util.AppLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold

@HiltViewModel
class StoryDetailViewModel @Inject constructor(
    private val repo: StoryRepository,
    private val userRepo: UserRepository,
    private val tokenStore: TokenStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    data class State(
        val story: Story? = null,
        val chapters: List<ChapterListItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val isGuest: Boolean = true,
        val isSignedIn: Boolean = false,
    )

    private val _state = MutableStateFlow(State(loading = true))
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

    fun load(storyId: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val (token, _, _) = tokenStore.read()
            val signedIn = !token.isNullOrBlank()
            var guest = true
            if (signedIn) {
                runCatching { userRepo.profile() }.onSuccess { guest = it.isGuest }
            }
            runCatching {
                val story = repo.storyDetail(storyId)
                val chapters = repo.chapters(storyId)
                story to chapters
            }.onSuccess { (s, c) ->
                _state.value = State(story = s, chapters = c, loading = false, isGuest = guest, isSignedIn = signedIn)
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message, isGuest = guest, isSignedIn = signedIn)
            }
        }
    }

    fun toggleBookmark() {
        val s = _state.value.story ?: return
        if (_state.value.isGuest) {
            viewModelScope.launch { _events.emit(DetailEvent.RequireSignIn("Sign in to bookmark")) }
            return
        }
        viewModelScope.launch {
            runCatching {
                if (s.isBookmarked) repo.unbookmark(s.id) else repo.bookmark(s.id)
            }.onSuccess {
                _state.value = _state.value.copy(story = s.copy(isBookmarked = !s.isBookmarked))
            }.onFailure {
                _events.emit(DetailEvent.Toast("Could not update bookmark — try again"))
            }
        }
    }

    fun rateStory(storyId: String, rating: Int) {
        if (_state.value.isGuest) {
            viewModelScope.launch { _events.emit(DetailEvent.RequireSignIn("Sign in to rate this story")) }
            return
        }
        viewModelScope.launch {
            runCatching { repo.rate(storyId, rating, null) }.onSuccess {
                val s = _state.value.story ?: return@launch
                _state.value = _state.value.copy(story = s.copy(userRating = rating))
            }.onFailure { err ->
                val msg = (err as? ApiException)?.let {
                    if (it.code == "guest_forbidden") "Sign in to rate this story" else "Couldn't save rating"
                } ?: "Couldn't save rating"
                _events.emit(DetailEvent.Toast(msg))
            }
        }
    }

    fun signalGuestForComments() {
        viewModelScope.launch { _events.emit(DetailEvent.RequireSignIn("Sign in to comment")) }
    }
}

sealed class DetailEvent {
    data class Toast(val message: String) : DetailEvent()
    data class RequireSignIn(val message: String) : DetailEvent()
}

@Composable
fun StoryDetailScreen(
    storyId: String,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenCoinStore: () -> Unit,
    onOpenLogin: () -> Unit = {},
    vm: StoryDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(storyId) { vm.load(storyId) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is DetailEvent.Toast -> snackbar.showSnackbar(event.message)
                is DetailEvent.RequireSignIn -> {
                    val result = snackbar.showSnackbar(
                        message = event.message,
                        actionLabel = "Sign in",
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        onOpenLogin()
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Tokens.Bg0,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.story == null -> FullScreenLoading()
                state.error != null && state.story == null -> ErrorBanner(state.error ?: "Failed to load")
                state.story != null -> Body(
                    story = state.story!!,
                    chapters = state.chapters,
                    isGuest = state.isGuest,
                    onBack = onBack,
                    onOpenReader = onOpenReader,
                    onToggleBookmark = vm::toggleBookmark,
                    onRate = { rating -> vm.rateStory(state.story!!.id, rating) },
                    onCommentGuestBlocked = vm::signalGuestForComments,
                    onShare = {
                        val s = state.story!!
                        val text = buildString {
                            append(s.title)
                            if (!s.summary.isNullOrBlank()) append("\n\n").append(s.summary)
                            append("\n\nRead on OmniRead: ${AppLinks.Website}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, s.title)
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share story"))
                    },
                )
            }
        }
    }
}

@Composable
private fun Body(
    story: Story,
    chapters: List<ChapterListItem>,
    isGuest: Boolean,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    onToggleBookmark: () -> Unit,
    onRate: (Int) -> Unit,
    onCommentGuestBlocked: () -> Unit,
    onShare: () -> Unit,
) {
    val firstReadableChapter = chapters.firstOrNull { it.isFree || it.isUnlocked } ?: chapters.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Tokens.Bg0),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                AsyncImage(
                    model = story.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Black.copy(alpha = 0.5f),
                            1f to Tokens.Bg0,
                        )
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Tokens.Ink0)
                    }
                    Row {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = "Share", tint = Tokens.Ink0)
                        }
                        IconButton(onClick = onToggleBookmark) {
                            Icon(
                                if (story.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (story.isBookmarked) Tokens.Accent else Tokens.Ink0,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        text = story.genre.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        bg = Tokens.Accent.copy(alpha = 0.18f), fg = Tokens.AccentSoft,
                    )
                    Pill(text = story.ageRating, bg = Tokens.Bg2, fg = Tokens.Ink2)
                    if (story.isPremium) Pill(text = "Premium", bg = Tokens.Gold.copy(alpha = 0.18f), fg = Tokens.Gold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    story.title,
                    color = Tokens.Ink0,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(6.dp))
                Text("by ${story.authorName}", color = Tokens.Ink2, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                if (!story.summary.isNullOrBlank()) {
                    Text(
                        story.summary,
                        color = Tokens.Ink1,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(20.dp))
                StoryStatsRow(story = story, chapters = chapters)
                Spacer(Modifier.height(18.dp))
                StartReadingButton(
                    chapter = firstReadableChapter,
                    onOpenReader = onOpenReader,
                )
                Spacer(Modifier.height(26.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text("Chapters", color = Tokens.Ink0, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${chapters.size} published - ${chapters.count { it.isFree || it.isUnlocked }} ready now",
                            color = Tokens.Ink3,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (chapters.any { !it.isFree && !it.isUnlocked }) {
                        Pill(text = "Locked chapters preview first", bg = Tokens.Bg2, fg = Tokens.Ink3)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        items(chapters, key = { it.id }) { ch ->
            ChapterRow(ch, onClick = { onOpenReader(ch.id) })
        }

        // Rating section
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Reviews", color = Tokens.Ink0, style = MaterialTheme.typography.headlineMedium)
                        Text("Tap a star to rate this story", color = Tokens.Ink3, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Tokens.Gold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("%.1f".format(story.avgRating), color = Tokens.Ink0, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                RatingBar(
                    currentRating = story.userRating ?: 0,
                    onRate = { rating -> onRate(rating) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (story.userRating != null) "Your rating is saved." else "Sign in before rating if you are using guest mode.",
                    color = Tokens.Ink3,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Comments section
        item {
            CommentsSection(
                chapters = chapters,
                isGuest = isGuest,
                onGuestBlocked = onCommentGuestBlocked,
            )
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun StoryStatsRow(story: Story, chapters: List<ChapterListItem>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Tokens.Bg1.copy(alpha = 0.86f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatItem(value = "${story.totalChapters}", label = "Chapters")
        StatItem(value = "%.1f".format(story.avgRating), label = "Rating")
        StatItem(value = story.estimatedReadTime?.let { "${it}m" } ?: "${chapters.sumOf { it.wordCount ?: 0 }.coerceAtLeast(0) / 220}m", label = "Read time")
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(value, color = Tokens.Ink0, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Tokens.Ink3, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun StartReadingButton(chapter: ChapterListItem?, onOpenReader: (String) -> Unit) {
    val enabled = chapter != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Brush.linearGradient(Tokens.GradientPink) else Brush.linearGradient(listOf(Tokens.Bg2, Tokens.Bg2)))
            .clickable(enabled = enabled) { chapter?.let { onOpenReader(it.id) } }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = chapter?.let {
                if (it.isFree || it.isUnlocked) "Start reading" else "Preview locked chapter"
            } ?: "No chapters yet",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ChapterRow(c: ChapterListItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Tokens.Bg1.copy(alpha = 0.56f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (c.isFree || c.isUnlocked) Tokens.Success.copy(alpha = 0.14f)
                    else Tokens.Gold.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                c.chapterNumber.toString(),
                color = if (c.isFree || c.isUnlocked) Tokens.Success else Tokens.Gold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                c.title?.takeIf { it.isNotBlank() } ?: "Chapter ${c.chapterNumber}",
                color = Tokens.Ink0,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            if (!c.cliffhangerPreview.isNullOrBlank()) {
                Text(
                    c.cliffhangerPreview,
                    color = Tokens.Ink2,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            } else {
                Text(
                    when {
                        c.isFree -> "Free chapter"
                        c.isUnlocked -> "Unlocked"
                        else -> "Preview available before unlock"
                    },
                    color = Tokens.Ink3,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        when {
            c.isFree || c.isUnlocked -> Icon(Icons.Filled.LockOpen, null, tint = Tokens.Success, modifier = Modifier.size(20.dp))
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = Tokens.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("${c.coinCost}", color = Tokens.Gold, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Tokens.Ink3, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RatingBar(currentRating: Int, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 1..5) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Star $i",
                tint = if (i <= currentRating) Tokens.Gold else Tokens.Ink3.copy(alpha = 0.3f),
                modifier = Modifier.size(36.dp).clickable { onRate(i) },
            )
        }
    }
}

@Composable
private fun CommentsSection(
    chapters: List<ChapterListItem>,
    isGuest: Boolean,
    onGuestBlocked: () -> Unit,
) {
    val firstChapterId = chapters.firstOrNull()?.id
    var commentText by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf<List<CommentData>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var posted by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(firstChapterId) {
        if (firstChapterId != null) {
            loading = true
            try {
                val api = com.omniread.app.di.ApiProvider.api
                val resp = api.chapterComments(firstChapterId)
                if (resp.success && resp.data != null) {
                    comments = resp.data
                }
            } catch (_: Exception) {}
            loading = false
        }
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Comments", color = Tokens.Ink0, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Showing chapter 1 discussion",
                    color = Tokens.Ink3,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Pill(text = "${comments.size}", bg = Tokens.Bg2, fg = Tokens.Ink2)
        }
        Spacer(Modifier.height(12.dp))

        if (isGuest) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Tokens.Bg1)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .clickable { onGuestBlocked() }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Tokens.Accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sign in to comment", color = Tokens.Ink0, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Guests can read comments, signed-in readers can join in.", color = Tokens.Ink3, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = commentText,
                    onValueChange = { commentText = it; postError = null },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Tokens.Ink0, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Tokens.Accent),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Tokens.Bg1)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                        ) {
                            if (commentText.isEmpty()) Text("Write a comment...", color = Tokens.Ink3, fontSize = 14.sp)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (commentText.isNotBlank()) Tokens.Accent else Tokens.Bg2)
                        .clickable {
                            if (commentText.isNotBlank() && firstChapterId != null) {
                                scope.launch {
                                    try {
                                        val api = com.omniread.app.di.ApiProvider.api
                                        val resp = api.postComment(firstChapterId, kotlinx.serialization.json.buildJsonObject {
                                            put("content", kotlinx.serialization.json.JsonPrimitive(commentText))
                                            put("is_spoiler", kotlinx.serialization.json.JsonPrimitive(false))
                                        })
                                        if (resp.error != null) {
                                            postError = if (resp.error.code == "guest_forbidden")
                                                "Sign in to comment" else resp.error.message
                                        } else {
                                            posted = true
                                            commentText = ""
                                            // Optimistic refresh
                                            runCatching { api.chapterComments(firstChapterId) }
                                                .onSuccess { r -> if (r.data != null) comments = r.data }
                                        }
                                    } catch (e: Exception) {
                                        postError = "Could not post — try again"
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            if (posted) {
                Spacer(Modifier.height(8.dp))
                Text("Comment posted!", color = Tokens.Success, fontSize = 12.sp)
            }
            if (postError != null) {
                Spacer(Modifier.height(8.dp))
                Text(postError!!, color = Tokens.Danger, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (comments.isEmpty() && !loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Tokens.Bg1.copy(alpha = 0.72f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No comments yet. Be the first!", color = Tokens.Ink3, fontSize = 13.sp)
            }
        }
        comments.take(10).forEach { comment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Tokens.Bg1.copy(alpha = 0.58f))
                    .padding(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Tokens.Accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text(comment.username?.firstOrNull()?.uppercase() ?: "?", color = Tokens.Accent, fontSize = 12.sp) }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comment.username ?: "User", color = Tokens.Ink1, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        if (comment.isAdmin) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Tokens.Accent).padding(horizontal = 4.dp, vertical = 1.dp),
                            ) { Text("Author", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(comment.content, color = Tokens.Ink2, fontSize = 13.sp)
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class CommentData(
    val id: String = "",
    val content: String = "",
    val username: String? = null,
    @kotlinx.serialization.SerialName("like_count") val likeCount: Int = 0,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
    @kotlinx.serialization.SerialName("is_admin") val isAdmin: Boolean = false,
    @kotlinx.serialization.SerialName("parent_id") val parentId: String? = null,
)
