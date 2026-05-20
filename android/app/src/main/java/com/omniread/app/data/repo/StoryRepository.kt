package com.omniread.app.data.repo

import com.omniread.app.data.api.OmniReadApi
import com.omniread.app.data.model.ChapterContent
import com.omniread.app.data.model.ChapterListItem
import com.omniread.app.data.model.ChapterPreview
import com.omniread.app.data.model.Story
import com.omniread.app.data.model.UnlockResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

sealed class ChapterFetch {
    data class Unlocked(val content: ChapterContent) : ChapterFetch()
    data class Locked(val preview: ChapterPreview) : ChapterFetch()
}

@Singleton
class StoryRepository @Inject constructor(
    private val api: OmniReadApi,
    private val json: Json,
) {
    data class FeedPage(val items: List<Story>, val nextCursor: String?)

    suspend fun feed(cursor: String?, limit: Int = 20, genre: String? = null): FeedPage {
        val env = api.feed(cursor, limit, genre)
        env.error?.let { throw com.omniread.app.data.api.ApiException(it.code, it.message) }
        return FeedPage(env.data ?: emptyList(), env.meta?.nextCursor)
    }

    suspend fun trending(genre: String? = null): List<Story> = api.trending(genre = genre).unwrap()
    suspend fun newest(genre: String? = null): List<Story> = api.newest(genre = genre).unwrap()
    suspend fun recommended(): List<Story> = api.recommended().unwrap()
    suspend fun search(q: String?, genre: String?, cursor: String?): FeedPage {
        val env = api.search(q = q, genre = genre, cursor = cursor)
        env.error?.let { throw com.omniread.app.data.api.ApiException(it.code, it.message) }
        return FeedPage(env.data ?: emptyList(), env.meta?.nextCursor)
    }

    suspend fun storyDetail(id: String): Story = api.storyDetail(id).unwrap()
    suspend fun chapters(storyId: String): List<ChapterListItem> = api.chapterList(storyId).unwrap()
    suspend fun related(storyId: String): List<Story> = api.related(storyId).unwrap()

    suspend fun like(storyId: String): JsonElement = api.likeStory(storyId).unwrap()
    suspend fun bookmark(storyId: String): JsonElement = api.bookmark(storyId).unwrap()
    suspend fun unbookmark(storyId: String): JsonElement = api.unbookmark(storyId).unwrap()
    suspend fun rate(storyId: String, rating: Int, review: String?): JsonElement {
        val body = buildJsonObject {
            put("rating", rating)
            if (review != null) put("review", review)
        }
        return api.rateStory(storyId, body).unwrap()
    }

    suspend fun chapter(chapterId: String): ChapterFetch {
        val raw = api.chapter(chapterId).unwrap() as JsonObject
        val unlocked = raw["is_unlocked"]?.jsonPrimitive?.boolean ?: false
        return if (unlocked) {
            ChapterFetch.Unlocked(json.decodeFromJsonElement(ChapterContent.serializer(), raw))
        } else {
            ChapterFetch.Locked(json.decodeFromJsonElement(ChapterPreview.serializer(), raw))
        }
    }

    suspend fun unlockWithCoins(chapterId: String, idempotency: String? = null): UnlockResponse =
        api.unlockWithCoins(chapterId, idempotency).unwrap()

    suspend fun saveProgress(chapterId: String, scrollPosition: Int, completed: Boolean): JsonElement {
        val body = buildJsonObject {
            put("chapter_id", chapterId)
            put("scroll_position", scrollPosition)
            put("completed", completed)
        }
        return api.saveProgress(chapterId, body).unwrap()
    }
}
