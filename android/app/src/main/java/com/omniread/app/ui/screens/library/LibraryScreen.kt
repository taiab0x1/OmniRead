package com.omniread.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.omniread.app.data.model.BookmarkItem
import com.omniread.app.data.model.HistoryItem
import com.omniread.app.data.repo.ConfigRepository
import com.omniread.app.data.repo.UserRepository
import com.omniread.app.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val users: UserRepository,
    val configRepo: ConfigRepository,
) : ViewModel() {
    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()
    val config = configRepo.config
    fun load() {
        viewModelScope.launch { runCatching { users.bookmarks() }.onSuccess { _bookmarks.value = it } }
        viewModelScope.launch { runCatching { users.history() }.onSuccess { _history.value = it } }
    }
}

@Composable
fun LibraryScreen(
    onOpenStory: (String) -> Unit,
    onOpenChapter: (String, String?) -> Unit,
    onOpenNotifications: () -> Unit = {},
    vm: LibraryViewModel = hiltViewModel(),
) {
    val bookmarks by vm.bookmarks.collectAsState()
    val history by vm.history.collectAsState()
    val cfg by vm.config.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(Tokens.Bg0).padding(top = 16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", color = Tokens.Ink0, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenNotifications) { Icon(Icons.Filled.Notifications, null, tint = Tokens.Accent, modifier = Modifier.size(22.dp)) }
        }

        Spacer(Modifier.height(8.dp))

        // Ad banner
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(Tokens.GradientPink)).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CardGiftcard, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Watch Ads Get ${cfg.adMaxPoints} Points Max", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("+${cfg.adPointsPerAd} Points (0/${cfg.adMaxAdsForPoints})", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tabs
        TabRow(selectedTabIndex = tab, containerColor = Tokens.Bg0, contentColor = Tokens.Accent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Saved (${bookmarks.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History (${history.size})") })
        }

        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> {
                if (bookmarks.isEmpty()) {
                    EmptyState("No saved books", "Bookmark stories to find them here")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(bookmarks, key = { it.id }) { item ->
                            Column(modifier = Modifier.clickable { onOpenStory(item.id) }) {
                                Box(modifier = Modifier.aspectRatio(0.67f).clip(RoundedCornerShape(10.dp)).background(Tokens.Bg2)) {
                                    AsyncImage(model = item.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.title, color = Tokens.Ink1, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier.aspectRatio(0.67f).clip(RoundedCornerShape(10.dp)).background(Tokens.Bg2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Add, null, tint = Tokens.Ink3, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text("Add book", color = Tokens.Ink3, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (history.isEmpty()) {
                    EmptyState("No reading history", "Stories you read will appear here")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(history, key = { it.storyId }) { item ->
                            Column(modifier = Modifier.clickable { onOpenChapter(item.chapterId, item.storyId) }) {
                                Box(modifier = Modifier.aspectRatio(0.67f).clip(RoundedCornerShape(10.dp)).background(Tokens.Bg2)) {
                                    AsyncImage(model = item.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.title, color = Tokens.Ink1, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (item.completed) "Completed" else "Continue", color = Tokens.Ink3, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, sub: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Tokens.Ink1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(sub, color = Tokens.Ink3, fontSize = 13.sp)
    }
}
