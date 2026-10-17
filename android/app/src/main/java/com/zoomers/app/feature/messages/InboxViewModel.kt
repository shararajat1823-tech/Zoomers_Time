package com.zoomers.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.MessageRepository
import com.zoomers.app.core.model.ConversationUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val conversations: List<ConversationUi> = emptyList(),
    val error: String? = null,
    val nextCursor: String? = null,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val messages: MessageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(refreshing = !it.loading, error = null) }
        viewModelScope.launch {
            when (val r = messages.conversations()) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, refreshing = false, conversations = r.data.items, nextCursor = r.data.nextCursor)
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, refreshing = false, error = r.message) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = messages.conversations(cursor = cursor)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loadingMore = false, conversations = it.conversations + r.data.items, nextCursor = r.data.nextCursor)
                }
                is ApiResult.Error -> _state.update { it.copy(loadingMore = false, error = r.message) }
            }
        }
    }
}
