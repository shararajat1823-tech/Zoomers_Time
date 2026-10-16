package com.zoomers.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.FeedRefreshBus
import com.zoomers.app.core.data.FeedRepository
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FeedTab { Following, ForYou }

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val posts: List<PostUi> = emptyList(),
    val stories: List<StoryUi> = emptyList(),
    val nextCursor: String? = null,
    val endReached: Boolean = false,
    val error: String? = null,
    val selectedTab: FeedTab = FeedTab.Following,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    feedRefreshBus: FeedRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
        // Reload when a new post is created elsewhere (the create flow).
        feedRefreshBus.events.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun selectTab(tab: FeedTab) {
        if (tab == _state.value.selectedTab) return
        _state.update { it.copy(selectedTab = tab) }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = it.posts.isNotEmpty(), loading = it.posts.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val r = feedRepository.followingFeed(cursor = null)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        posts = r.data.items,
                        stories = deriveStories(r.data.items),
                        nextCursor = r.data.nextCursor,
                        endReached = r.data.nextCursor == null,
                        error = null,
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, refreshing = false, error = r.message) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.endReached || s.nextCursor == null) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = feedRepository.followingFeed(cursor = s.nextCursor)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loadingMore = false,
                        posts = it.posts + r.data.items,
                        nextCursor = r.data.nextCursor,
                        endReached = r.data.nextCursor == null,
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(loadingMore = false, error = r.message) }
            }
        }
    }

    /** Optimistic like toggle with rollback on failure. */
    fun toggleLike(post: PostUi) {
        val liked = !post.hasLiked
        updatePost(post.id) { it.copy(hasLiked = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            val r = if (liked) feedRepository.like(post.id) else feedRepository.unlike(post.id)
            when (r) {
                is ApiResult.Success -> updatePost(post.id) { it.copy(likeCount = r.data) }
                is ApiResult.Error -> updatePost(post.id) { it.copy(hasLiked = post.hasLiked, likeCount = post.likeCount) }
            }
        }
    }

    /** Optimistic save toggle with rollback on failure. */
    fun toggleSave(post: PostUi) {
        val saved = !post.hasSaved
        updatePost(post.id) { it.copy(hasSaved = saved) }
        viewModelScope.launch {
            val r = if (saved) feedRepository.save(post.id) else feedRepository.unsave(post.id)
            if (r is ApiResult.Error) updatePost(post.id) { it.copy(hasSaved = post.hasSaved) }
        }
    }

    private fun updatePost(id: String, transform: (PostUi) -> PostUi) {
        _state.update { s -> s.copy(posts = s.posts.map { if (it.id == id) transform(it) else it }) }
    }

    private fun deriveStories(posts: List<PostUi>): List<StoryUi> =
        posts.map { it.username }.distinct().take(12).map { StoryUi(it, hasUnseen = true) }
}
