package com.zoomers.app.core.network

import com.zoomers.app.core.network.dto.ProblemDto
import kotlinx.serialization.json.Json
import retrofit2.Response

/** A typed network outcome so the UI can render success / error / retry without try-catch everywhere. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val code: String? = null, val httpStatus: Int? = null) : ApiResult<Nothing>
}

private val problemJson = Json { ignoreUnknownKeys = true }

/** Wraps a Retrofit call, mapping transport/HTTP failures to a friendly [ApiResult.Error]. */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            val body = (response.body() ?: Unit) as T
            ApiResult.Success(body)
        } else {
            val problem = runCatching {
                response.errorBody()?.string()?.let { problemJson.decodeFromString<ProblemDto>(it) }
            }.getOrNull()
            ApiResult.Error(
                message = problem?.detail ?: defaultMessage(response.code()),
                code = problem?.code,
                httpStatus = response.code(),
            )
        }
    } catch (e: Exception) {
        ApiResult.Error(message = "Couldn't reach Zoomers. Check your connection and try again.")
    }
}

private fun defaultMessage(status: Int): String = when (status) {
    401 -> "Your session has expired. Please sign in again."
    403 -> "You don't have access to that."
    404 -> "Not found."
    429 -> "You're going too fast. Try again in a moment."
    in 500..599 -> "Something went wrong on our side. Please try again."
    else -> "Something went wrong. Please try again."
}
