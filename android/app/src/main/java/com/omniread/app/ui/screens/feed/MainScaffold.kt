package com.omniread.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.omniread.app.ui.screens.library.LibraryScreen
import com.omniread.app.ui.screens.profile.ProfileScreen
import com.omniread.app.ui.screens.search.SearchScreen
import com.omniread.app.ui.screens.vip.VipScreen
import com.omniread.app.ui.theme.Tokens

private enum class Tab { Discover, Search, Vip, Library, Me }

@Composable
fun MainScaffold(
    onOpenStory: (String) -> Unit,
    onOpenCoinStore: () -> Unit,
    onOpenVip: () -> Unit,
    onOpenRewards: () -> Unit,
    onOpenReader: (String, String?) -> Unit,
    onOpenLogin: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Discover) }

    Scaffold(
        bottomBar = { BottomNavBar(current = tab, onSelect = { tab = it }) },
        containerColor = Tokens.Bg0,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Discover -> FeedScreen(
                    onOpenStory = onOpenStory,
                    onOpenReader = { onOpenReader(it, null) },
                    onOpenNotifications = onOpenNotifications,
                )
                Tab.Search -> SearchScreen(onOpenStory = onOpenStory)
                Tab.Library -> LibraryScreen(
                    onOpenStory = onOpenStory,
                    onOpenChapter = { c, s -> onOpenReader(c, s) },
                    onOpenNotifications = onOpenNotifications,
                )
                Tab.Me -> ProfileScreen(
                    onOpenCoinStore = onOpenCoinStore,
                    onSignedOut = {},
                    onOpenRewards = onOpenRewards,
                    onOpenVip = onOpenVip,
                    onOpenLogin = onOpenLogin,
                    onOpenNotifications = onOpenNotifications,
                )
                Tab.Vip -> VipScreen(onBack = { tab = Tab.Discover })
            }
        }
    }
}

@Composable
private fun BottomNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Main bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Tokens.Bg1.copy(alpha = 0.95f), Tokens.Bg0)
                    )
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Filled.AutoStories, "Discover", current == Tab.Discover) { onSelect(Tab.Discover) }
            NavItem(Icons.Filled.Search, "Search", current == Tab.Search) { onSelect(Tab.Search) }

            // VIP center button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .offset(y = (-14).dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(Tokens.GradientPink))
                    .clickable { onSelect(Tab.Vip) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Diamond, "VIP", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            NavItem(Icons.AutoMirrored.Filled.LibraryBooks, "Library", current == Tab.Library) { onSelect(Tab.Library) }
            NavItem(Icons.Filled.Person, "Me", current == Tab.Me) { onSelect(Tab.Me) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) Tokens.Accent else Tokens.Ink3
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) Tokens.Accent.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 9.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}
