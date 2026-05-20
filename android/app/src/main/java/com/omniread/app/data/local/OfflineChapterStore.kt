package com.omniread.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omniread.app.data.model.ChapterContent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.offlineChapterStore by preferencesDataStore(name = "omniread_offline_chapters")

@Singleton
class OfflineChapterStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val json: Json,
) {
    suspend fun save(chapter: ChapterContent) {
        ctx.offlineChapterStore.edit {
            it[key(chapter.id)] = json.encodeToString(ChapterContent.serializer(), chapter)
        }
    }

    suspend fun get(chapterId: String): ChapterContent? {
        val raw = ctx.offlineChapterStore.data.map { it[key(chapterId)] }.first() ?: return null
        return runCatching { json.decodeFromString(ChapterContent.serializer(), raw) }.getOrNull()
    }

    suspend fun isSaved(chapterId: String): Boolean {
        return ctx.offlineChapterStore.data.map { it.contains(key(chapterId)) }.first()
    }

    private fun key(chapterId: String) = stringPreferencesKey("chapter_$chapterId")
}
