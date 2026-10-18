package com.zoomers.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.FeedRepository
import com.zoomers.app.core.data.UserRepository
import com.zoomers.app.core.model.HashtagUi
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.UserListItemUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode { People, Tags }

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.People,
    val loading: Boolean = false,
    val users: List<UserListItemUi> = emptyList(),
    val hashtags: List<HashtagUi> = emptyList(),
    // Discovery grid shown when the query is empty.
    val explorePosts: List<PostUi> = emptyList(),
    val exploreLoading: Boolean = false,
    val exploreCursor: String? = null,
    val error: String? = null,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadingMore = false

    init { loadExplore() }

    fun setMode(mode: SearchMode) {
        if (mode == _state.value.mode) return
        _state.update { it.copy(mode = mode) }
        if (_state.value.isSearching) runSearch(_state.value.query)
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        runSearch(q)
    }

    private fun runSearch(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            // Back to the explore grid; clear any stale results.
            _state.update { it.copy(loading = false, users = emptyList(), hashtags = emptyList(), error = null) }
            if (_state.value.explorePosts.isEmpty()) loadExplore()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _state.update { it.copy(loading = true, error = null) }
            when (_state.value.mode) {
                SearchMode.People -> when (val r = userRepository.searchUsers(q.trim())) {
                    is ApiResult.Success -> _state.update { it.copy(loading = false, users = r.data.items) }
                    is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
                }
                SearchMode.Tags -> when (val r = userRepository.searchHashtags(q.trim().removePrefix("#"))) {
                    is ApiResult.Success -> _state.update { it.copy(loading = false, hashtags = r.data.items) }
                    is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
                }
            }
        }
    }

    fun refreshExplore() = loadExplore()

    private fun loadExplore() {
        _state.update { it.copy(exploreLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = feedRepository.explore(cursor = null)) {
                is ApiResult.Success -> _state.update {
                    it.copy(exploreLoading = false, explorePosts = r.data.items, exploreCursor = r.data.nextCursor)
                }
                is ApiResult.Error -> _state.update { it.copy(exploreLoading = false, error = r.message) }
            }
        }
    }

    fun loadMoreExplore() {
        val cursor = _state.value.exploreCursor ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            when (val r = feedRepository.explore(cursor = cursor)) {
                is ApiResult.Success -> _state.update {
                    it.copy(explorePosts = it.explorePosts + r.data.items, exploreCursor = r.data.nextCursor)
                }
                is ApiResult.Error -> { /* keep what we have */ }
            }
            loadingMore = false
        }
    }
}
