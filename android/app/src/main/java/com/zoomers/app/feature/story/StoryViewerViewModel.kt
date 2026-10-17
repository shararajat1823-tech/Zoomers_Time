package com.zoomers.app.feature.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.StoryRepository
import com.zoomers.app.core.model.StoryItemUi
import com.zoomers.app.core.model.StoryTrayUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoryViewerUiState(
    val loading: Boolean = true,
    val trays: List<StoryTrayUi> = emptyList(),
    val authorIndex: Int = 0,
    val storyIndex: Int = 0,
    val finished: Boolean = false,
) {
    val tray: StoryTrayUi? get() = trays.getOrNull(authorIndex)
    val story: StoryItemUi? get() = tray?.stories?.getOrNull(storyIndex)
}

@HiltViewModel
class StoryViewerViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StoryViewerUiState())
    val state: StateFlow<StoryViewerUiState> = _state.asStateFlow()

    fun load(startAuthorId: String) {
        viewModelScope.launch {
            when (val r = storyRepository.feed()) {
                is ApiResult.Success -> {
                    val trays = r.data.filter { it.stories.isNotEmpty() }
                    val idx = trays.indexOfFirst { it.authorId == startAuthorId }.coerceAtLeast(0)
                    if (trays.isEmpty()) {
                        _state.update { it.copy(loading = false, finished = true) }
                    } else {
                        _state.update { it.copy(loading = false, trays = trays, authorIndex = idx, storyIndex = 0) }
                        markCurrentViewed()
                    }
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, finished = true) }
            }
        }
    }

    fun next() {
        val s = _state.value
        val tray = s.tray ?: return
        if (s.storyIndex < tray.stories.lastIndex) {
            _state.update { it.copy(storyIndex = it.storyIndex + 1) }
        } else if (s.authorIndex < s.trays.lastIndex) {
            _state.update { it.copy(authorIndex = it.authorIndex + 1, storyIndex = 0) }
        } else {
            _state.update { it.copy(finished = true) }
            return
        }
        markCurrentViewed()
    }

    fun prev() {
        val s = _state.value
        if (s.storyIndex > 0) {
            _state.update { it.copy(storyIndex = it.storyIndex - 1) }
        } else if (s.authorIndex > 0) {
            val prevTray = s.trays[s.authorIndex - 1]
            _state.update { it.copy(authorIndex = it.authorIndex - 1, storyIndex = prevTray.stories.lastIndex.coerceAtLeast(0)) }
        }
        // else: already at the very first story — stay.
    }

    private fun markCurrentViewed() {
        val story = _state.value.story ?: return
        if (story.viewed) return
        viewModelScope.launch { storyRepository.markViewed(story.id) }
    }
}
