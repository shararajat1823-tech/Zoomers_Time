package com.zoomers.app.core.network

import com.zoomers.app.core.network.dto.AuthResponseDto
import com.zoomers.app.core.network.dto.CursorPageDto
import com.zoomers.app.core.network.dto.LikeStateDto
import com.zoomers.app.core.network.dto.LoginRequestDto
import com.zoomers.app.core.network.dto.LogoutRequestDto
import com.zoomers.app.core.network.dto.PostViewDto
import com.zoomers.app.core.network.dto.ProfileViewDto
import com.zoomers.app.core.network.dto.RefreshRequestDto
import com.zoomers.app.core.network.dto.RegisterRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit surface for the Zoomers API (v1). Paths are relative to API_BASE_URL. */
interface ZoomersApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<AuthResponseDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<AuthResponseDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): Response<AuthResponseDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<Unit>

    @GET("users/me")
    suspend fun me(): Response<ProfileViewDto>

    @GET("feed/following")
    suspend fun followingFeed(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<PostViewDto>>

    @POST("posts/{postId}/likes")
    suspend fun likePost(@Path("postId") postId: String): Response<LikeStateDto>

    @DELETE("posts/{postId}/likes")
    suspend fun unlikePost(@Path("postId") postId: String): Response<LikeStateDto>

    @POST("posts/{postId}/save")
    suspend fun savePost(@Path("postId") postId: String): Response<Unit>

    @DELETE("posts/{postId}/save")
    suspend fun unsavePost(@Path("postId") postId: String): Response<Unit>
}
