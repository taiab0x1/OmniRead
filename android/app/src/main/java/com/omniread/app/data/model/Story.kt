package com.omniread.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Story(
    val id: String,
    val title: String,
    val slug: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("hook_line") val hookLine: String? = null,
    val summary: String? = null,
    val genre: String,
    val tags: List<String>? = null,
    @SerialName("age_rating") val ageRating: String = "13+",
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("total_chapters") val totalChapters: Int = 0,
    @SerialName("free_chapters") val freeChapters: Int = 3,
    @SerialName("view_count") val viewCount: Long = 0,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("avg_rating") val avgRating: Double = 0.0,
    @SerialName("estimated_read_time") val estimatedReadTime: Int? = null,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @SerialName("is_liked") val isLiked: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("user_rating") val userRating: Int? = null,
)

@Serializable
data class ChapterListItem(
    val id: String,
    @SerialName("chapter_number") val chapterNumber: Int,
    val title: String? = null,
    @SerialName("word_count") val wordCount: Int? = null,
    @SerialName("is_free") val isFree: Boolean = false,
    @SerialName("coin_cost") val coinCost: Int = 5,
    @SerialName("has_cliffhanger") val hasCliffhanger: Boolean = false,
    @SerialName("cliffhanger_preview") val cliffhangerPreview: String? = null,
    @SerialName("is_unlocked") val isUnlocked: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class ChapterContent(
    val id: String,
    @SerialName("story_id") val storyId: String,
    @SerialName("chapter_number") val chapterNumber: Int,
    val title: String? = null,
    val content: String,
    @SerialName("word_count") val wordCount: Int? = null,
    @SerialName("is_free") val isFree: Boolean = false,
    @SerialName("coin_cost") val coinCost: Int = 5,
    @SerialName("has_cliffhanger") val hasCliffhanger: Boolean = false,
    @SerialName("cliffhanger_preview") val cliffhangerPreview: String? = null,
    @SerialName("is_unlocked") val isUnlocked: Boolean = true,
    @SerialName("next_chapter_id") val nextChapterId: String? = null,
    @SerialName("prev_chapter_id") val prevChapterId: String? = null,
)

@Serializable
data class ChapterPreview(
    val id: String,
    @SerialName("story_id") val storyId: String,
    @SerialName("chapter_number") val chapterNumber: Int,
    val title: String? = null,
    val preview: String,
    @SerialName("is_free") val isFree: Boolean = false,
    @SerialName("coin_cost") val coinCost: Int = 5,
    @SerialName("is_unlocked") val isUnlocked: Boolean = false,
)
