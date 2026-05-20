package com.omniread.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Envelope<T>(
    val success: Boolean = true,
    val data: T? = null,
    val meta: Meta? = null,
    val error: ApiError? = null,
)

@Serializable
data class Meta(
    val page: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    val total: Int? = null,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val extra: kotlinx.serialization.json.JsonElement? = null,
)

class ApiException(val code: String, message: String) : RuntimeException(message)
