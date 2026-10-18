package com.zoomers.app.core.network

import com.zoomers.app.core.network.dto.AddCommentRequestDto
import com.zoomers.app.core.network.dto.AuthResponseDto
import com.zoomers.app.core.network.dto.CommentViewDto
import com.zoomers.app.core.network.dto.ConversationDto
import com.zoomers.app.core.network.dto.CreatePostRequestDto
import com.zoomers.app.core.network.dto.CreateStoryRequestDto
import com.zoomers.app.core.network.dto.CreateUploadSessionRequestDto
import com.zoomers.app.core.network.dto.CursorPageDto
import com.zoomers.app.core.network.dto.FollowResultDto
import com.zoomers.app.core.network.dto.HashtagResultDto
import com.zoomers.app.core.network.dto.LikeStateDto
import com.zoomers.app.core.network.dto.MessageDto
import com.zoomers.app.core.network.dto.NotificationViewDto
import com.zoomers.app.core.network.dto.SendMessageRequestDto
import com.zoomers.app.core.network.dto.StartConversationRequestDto
import com.zoomers.app.core.network.dto.UnreadCountDto
import com.zoomers.app.core.network.dto.PublicProfileViewDto
import com.zoomers.app.core.network.dto.SettingsViewDto
import com.zoomers.app.core.network.dto.UpdateSettingsRequestDto
import com.zoomers.app.core.network.dto.UserListItemDto
import com.zoomers.app.core.network.dto.LoginRequestDto
import com.zoomers.app.core.network.dto.LogoutRequestDto
import com.zoomers.app.core.network.dto.PostViewDto
import com.zoomers.app.core.network.dto.ProfileViewDto
import com.zoomers.app.core.network.dto.RefreshRequestDto
import com.zoomers.app.core.network.dto.RegisterDeviceRequestDto
import com.zoomers.app.core.network.dto.RegisterRequestDto
import com.zoomers.app.core.network.dto.RemoveDeviceRequestDto
import com.zoomers.app.core.network.dto.StoryTrayDto
import com.zoomers.app.core.network.dto.UpdateProfileRequestDto
import com.zoomers.app.core.network.dto.UploadSessionResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
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

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequestDto): Response<ProfileViewDto>

    @GET("users/{userId}/posts")
    suspend fun userPosts(
        @Path("userId") userId: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<PostViewDto>>

    @GET("users/me/saved")
    suspend fun savedPosts(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<PostViewDto>>

    @GET("feed/following")
    suspend fun followingFeed(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<PostViewDto>>

    @GET("posts/{postId}")
    suspend fun getPost(@Path("postId") postId: String): Response<PostViewDto>

    @DELETE("posts/{postId}")
    suspend fun deletePost(@Path("postId") postId: String): Response<Unit>

    @DELETE("comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: String): Response<Unit>

    @POST("posts/{postId}/likes")
    suspend fun likePost(@Path("postId") postId: String): Response<LikeStateDto>

    @DELETE("posts/{postId}/likes")
    suspend fun unlikePost(@Path("postId") postId: String): Response<LikeStateDto>

    @POST("posts/{postId}/save")
    suspend fun savePost(@Path("postId") postId: String): Response<Unit>

    @DELETE("posts/{postId}/save")
    suspend fun unsavePost(@Path("postId") postId: String): Response<Unit>

    // ---- Comments ----

    @GET("posts/{postId}/comments")
    suspend fun comments(
        @Path("postId") postId: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<CommentViewDto>>

    @POST("posts/{postId}/comments")
    suspend fun addComment(
        @Path("postId") postId: String,
        @Body body: AddCommentRequestDto,
    ): Response<CommentViewDto>

    // ---- Users / social ----

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<UserListItemDto>>

    @GET("search/explore")
    suspend fun explore(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 30,
    ): Response<CursorPageDto<PostViewDto>>

    @GET("search/suggested-users")
    suspend fun suggestedUsers(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<UserListItemDto>>

    @GET("search/hashtags")
    suspend fun searchHashtags(
        @Query("q") query: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<HashtagResultDto>>

    @GET("hashtags/{tag}/posts")
    suspend fun hashtagPosts(
        @Path("tag") tag: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<PostViewDto>>

    @GET("users/{username}")
    suspend fun userByUsername(@Path("username") username: String): Response<PublicProfileViewDto>

    // ---- Notifications ----

    @GET("notifications")
    suspend fun notifications(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): Response<CursorPageDto<NotificationViewDto>>

    @POST("notifications/read")
    suspend fun markNotificationsRead(): Response<Unit>

    // ---- Stories ----

    @GET("stories/feed")
    suspend fun storyFeed(): Response<List<StoryTrayDto>>

    @POST("stories")
    suspend fun createStory(@Body body: CreateStoryRequestDto): Response<Unit>

    @POST("stories/{storyId}/view")
    suspend fun markStoryViewed(@Path("storyId") storyId: String): Response<Unit>

    // ---- Settings ----

    @GET("settings")
    suspend fun settings(): Response<SettingsViewDto>

    @PATCH("settings")
    suspend fun updateSettings(@Body body: UpdateSettingsRequestDto): Response<SettingsViewDto>

    @POST("users/{userId}/follow")
    suspend fun follow(@Path("userId") userId: String): Response<FollowResultDto>

    @DELETE("users/{userId}/follow")
    suspend fun unfollow(@Path("userId") userId: String): Response<Unit>

    // ---- Direct messages ----

    @POST("conversations")
    suspend fun startConversation(@Body body: StartConversationRequestDto): Response<ConversationDto>

    @GET("conversations")
    suspend fun conversations(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 30,
    ): Response<CursorPageDto<ConversationDto>>

    @GET("conversations/unread-count")
    suspend fun conversationsUnreadCount(): Response<UnreadCountDto>

    @GET("conversations/{conversationId}/messages")
    suspend fun messages(
        @Path("conversationId") conversationId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 30,
    ): Response<CursorPageDto<MessageDto>>

    @POST("conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Body body: SendMessageRequestDto,
    ): Response<MessageDto>

    @POST("conversations/{conversationId}/read")
    suspend fun markConversationRead(@Path("conversationId") conversationId: String): Response<Unit>

    // ---- Push devices ----

    @POST("me/devices")
    suspend fun registerDevice(@Body body: RegisterDeviceRequestDto): Response<Unit>

    @POST("me/devices/remove")
    suspend fun removeDevice(@Body body: RemoveDeviceRequestDto): Response<Unit>

    // ---- Media upload + post creation ----

    @POST("media/upload-sessions")
    suspend fun createUploadSession(@Body body: CreateUploadSessionRequestDto): Response<UploadSessionResponseDto>

    @POST("media/upload-sessions/{sessionId}/complete")
    suspend fun completeUploadSession(@Path("sessionId") sessionId: String): Response<UploadSessionResponseDto>

    @POST("posts")
    suspend fun createPost(@Body body: CreatePostRequestDto): Response<PostViewDto>
}
