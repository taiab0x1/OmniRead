package com.omniread.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefsStore by preferencesDataStore(name = "omniread_app_prefs")

@Singleton
class AppPrefsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val onboardingSeenKey = booleanPreferencesKey("onboarding_seen")
    private val preferredGenresKey = stringSetPreferencesKey("preferred_genres")

    val onboardingSeenFlow: Flow<Boolean> = ctx.appPrefsStore.data.map {
        it[onboardingSeenKey] ?: false
    }

    val preferredGenresFlow: Flow<List<String>> = ctx.appPrefsStore.data.map {
        it[preferredGenresKey]?.toList().orEmpty()
    }

    suspend fun markOnboardingSeen(preferredGenres: List<String> = emptyList()) {
        ctx.appPrefsStore.edit {
            it[onboardingSeenKey] = true
            if (preferredGenres.isNotEmpty()) it[preferredGenresKey] = preferredGenres.toSet()
        }
    }
}
