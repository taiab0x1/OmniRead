package com.omniread.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.omniread.app.data.model.Story
import com.omniread.app.data.repo.ConfigRepository
import com.omniread.app.data.repo.StoryRepository
import com.omniread.app.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: StoryRepository,
    private val configRepo: ConfigRepository,
) : ViewModel() {
    data class State(
        val stories: List<Story> = emptyList(),
        val results: List<Story> = emptyList(),
        val trendingTags: List<String> = emptyList(),
        val trendingTitles: List<String> = emptyList(),
        val searching: Boolean = false,
        val error: String? = null,
    )
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    init {
        viewModelScope.launch { runCatching { repo.trending() }.onSuccess { _state.value = _state.value.copy(stories = it) } }
        viewModelScope.launch {
            val cfg = configRepo.config.value
            _state.value = _state.value.copy(trendingTags = cfg.trendingTags, trendingTitles = cfg.trendingTitles)
        }
    }

    fun search(q: String) {
        val query = q.trim()
        if (query.length < 2) {
            _state.value = _state.value.copy(results = emptyList(), searching = false, error = null)
            return
        }
        _state.value = _state.value.copy(searching = true, error = null)
        viewModelScope.launch {
            runCatching { repo.search(query, genre = null, cursor = null) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(results = page.items, searching = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(searching = false, error = e.message)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repo.trending() }.onSuccess {
                _state.value = _state.value.copy(stories = it)
            }
        }
    }
}

@Composable
fun SearchScreen(onOpenStory: (String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val showingResults = query.trim().length >= 2
    val list = if (showingResults) state.results else state.stories

    Column(modifier = Modifier.fillMaxSize().background(Tokens.Bg0).verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(44.dp)
                .clip(RoundedCornerShape(22.dp)).background(Tokens.Bg2).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Tokens.Ink3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.search(it)
                },
                textStyle = TextStyle(color = Tokens.Ink0, fontSize = 14.sp),
                cursorBrush = SolidColor(Tokens.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Title / Author / Tags / Genre", color = Tokens.Ink3, fontSize = 14.sp)
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (showingResults) "Search Results" else "Trending", color = Tokens.Ink0, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { if (showingResults) vm.search(query) else vm.refresh() }) { Icon(Icons.Filled.Refresh, null, tint = Tokens.Ink3, modifier = Modifier.size(18.dp)) }
        }

        if (!showingResults && state.trendingTags.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.trendingTags.forEach { tag ->
                    Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Tokens.Bg2).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(tag.replace("_", " "), color = Tokens.Ink1, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (!showingResults) {
            state.trendingTitles.forEach { title ->
                Text("🔥 $title", color = Tokens.Ink1, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (showingResults) "Matches" else "Hot Ranking", color = Tokens.Ink0, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("More >", color = Tokens.Accent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))

        if (state.searching) {
            Text("Searching…", color = Tokens.Ink3, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        state.error?.let {
            Text(it, color = Tokens.Danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        list.firstOrNull()?.let { s ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Tokens.Bg1).padding(12.dp).clickable { onOpenStory(s.id) }) {
                Box(modifier = Modifier.size(width = 80.dp, height = 110.dp).clip(RoundedCornerShape(8.dp)).background(Tokens.Bg2)) {
                    AsyncImage(model = s.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Tokens.Accent), contentAlignment = Alignment.Center) {
                    Text("01", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.title, color = Tokens.Ink0, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(s.hookLine ?: "", color = Tokens.Ink2, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Tokens.Bg2).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(s.genre.replace("_", " "), color = Tokens.Ink2, fontSize = 10.sp) }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.RemoveRedEye, null, tint = Tokens.ViewGreen, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(formatK(s.viewCount), color = Tokens.Ink2, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val grid = list.drop(1).take(8)
        for (i in grid.indices step 2) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                grid.getOrNull(i)?.let { RankCard(it, i + 2, Modifier.weight(1f), onOpenStory) }
                grid.getOrNull(i + 1)?.let { RankCard(it, i + 3, Modifier.weight(1f), onOpenStory) } ?: Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun RankCard(story: Story, rank: Int, modifier: Modifier, onOpenStory: (String) -> Unit) {
    Row(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Tokens.Bg1).clickable { onOpenStory(story.id) }.padding(8.dp)) {
        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (rank <= 3) Tokens.Accent else Tokens.Bg3), contentAlignment = Alignment.Center) {
            Text(rank.toString().padStart(2, '0'), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.size(width = 50.dp, height = 68.dp).clip(RoundedCornerShape(6.dp)).background(Tokens.Bg2)) {
            AsyncImage(model = story.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(story.title, color = Tokens.Ink1, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.RemoveRedEye, null, tint = Tokens.ViewGreen, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(3.dp))
                Text(formatK(story.viewCount), color = Tokens.Ink3, fontSize = 10.sp)
            }
        }
    }
}

private fun formatK(n: Long): String = when {
    n >= 1_000_000 -> "%.2fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.2fK".format(n / 1_000.0)
    else -> n.toString()
}
