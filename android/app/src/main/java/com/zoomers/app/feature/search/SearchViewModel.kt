package com.zoomers.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.FeedRepository
import com.zoomers.app.core.data.ProfileRepository
import com.zoomers.app.core.data.ReelRepository
import com.zoomers.app.core.data.UserRepository
import com.zoomers.app.core.model.GalleryItem
import com.zoomers.app.core.model.HashtagUi
import com.zoomers.app.core.model.UserListItemUi
import com.zoomers.app.core.model.toGalleryItem
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

/** Content filter for the explore gallery, matching the All/Photos/Videos/Favorites chips. */
enum class GalleryTab { All, Photos, Videos, Favorites }

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.People,
    val loading: Boolean = false,
    val users: List<UserListItemUi> = emptyList(),
    val hashtags: List<HashtagUi> = emptyList(),
    // Explore gallery (shown when the query is empty).
    val galleryTab: GalleryTab = GalleryTab.All,
    val gallery: List<GalleryItem> = emptyList(),
    val galleryLoading: Boolean = false,
    val galleryRefreshing: Boolean = false,
    val galleryCursor: String? = null,
    val error: String? = null,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val reelRepository: ReelRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadingMore = false

    init { loadGallery() }

    // ---- text search (People / Tags) ----

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
            _state.update { it.copy(loading = false, users = emptyList(), hashtags = emptyList(), error = null) }
            if (_state.value.gallery.isEmpty()) loadGallery()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
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

    // ---- explore gallery (All / Photos / Videos / Favorites) ----

    fun selectGalleryTab(tab: GalleryTab) {
        if (tab == _state.value.galleryTab) return
        _state.update { it.copy(galleryTab = tab, gallery = emptyList(), galleryCursor = null) }
        loadGallery()
    }

    fun refreshGallery() = loadGallery(refresh = true)

    private fun loadGallery(refresh: Boolean = false) {
        val tab = _state.value.galleryTab
        _state.update {
            it.copy(
                galleryLoading = !refresh && it.gallery.isEmpty(),
                galleryRefreshing = refresh,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = fetchGallery(tab, cursor = null)
            _state.update {
                if (result == null) it.copy(galleryLoading = false, galleryRefreshing = false, error = "Couldn't load.")
                else it.copy(galleryLoading = false, galleryRefreshing = false, gallery = result.items, galleryCursor = result.cursor)
            }
        }
    }

    fun loadMoreGallery() {
        val cursor = _state.value.galleryCursor ?: return
        if (loadingMore) return
        loadingMore = true
        val tab = _state.value.galleryTab
        viewModelScope.launch {
            val result = fetchGallery(tab, cursor = cursor, moreOnly = true)
            if (result != null) {
                _state.update {
                    val existing = it.gallery.map { g -> g.id }.toSet()
                    it.copy(gallery = it.gallery + result.items.filter { g -> g.id !in existing }, galleryCursor = result.cursor)
                }
            }
            loadingMore = false
        }
    }

    private data class GalleryPage(val items: List<GalleryItem>, val cursor: String?)

    /**
     * Fetches a page for the given tab. Pagination follows the tab's primary source
     * (explore posts for All/Photos, reels for Videos, saved for Favorites). For
     * All, the first page also folds in reels, interleaved by recency.
     */
    private suspend fun fetchGallery(tab: GalleryTab, cursor: String?, moreOnly: Boolean = false): GalleryPage? {
        return when (tab) {
            GalleryTab.Photos -> (feedRepository.explore(cursor) as? ApiResult.Success)?.data
                ?.let { GalleryPage(it.items.map { p -> p.toGalleryItem() }, it.nextCursor) }

            GalleryTab.Videos -> (reelRepository.feed(cursor) as? ApiResult.Success)?.data
                ?.let { GalleryPage(it.items.map { r -> r.toGalleryItem() }, it.nextCursor) }

            GalleryTab.Favorites -> (profileRepository.savedPosts(cursor) as? ApiResult.Success)?.data
                ?.let { GalleryPage(it.items.map { p -> p.toGalleryItem() }, it.nextCursor) }

            GalleryTab.All -> {
                val explore = (feedRepository.explore(cursor) as? ApiResult.Success)?.data ?: return null
                var items = explore.items.map { it.toGalleryItem() }
                if (!moreOnly) {
                    val reels = (reelRepository.feed(null) as? ApiResult.Success)?.data?.items.orEmpty().map { it.toGalleryItem() }
                    items = (items + reels).sortedByDescending { it.createdAtIso }
                }
                GalleryPage(items, explore.nextCursor)
            }
        }
    }
}
