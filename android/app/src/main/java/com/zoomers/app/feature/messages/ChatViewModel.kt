package com.zoomers.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.MessageRepository
import com.zoomers.app.core.model.MessageUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val loading: Boolean = true,
    val conversationId: String? = null,
    val peerUsername: String = "",
    val peerDisplayName: String = "",
    val peerAvatarUrl: String? = null,
    val peerVerified: Boolean = false,
    // Newest-first; the screen renders with reverseLayout so newest sits at the bottom.
    val messages: List<MessageUi> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && !sending && conversationId != null
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messages: MessageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Opens (or creates) the conversation with [peerId], loads it, and starts polling. */
    fun load(peerId: String) {
        viewModelScope.launch {
            when (val r = messages.start(peerId)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            conversationId = r.data.id,
                            peerUsername = r.data.username,
                            peerDisplayName = r.data.displayName,
                            peerAvatarUrl = r.data.avatarUrl,
                            peerVerified = r.data.isVerified,
                        )
                    }
                    loadFirstPage(r.data.id)
                    startPolling(r.data.id)
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private suspend fun loadFirstPage(conversationId: String) {
        when (val r = messages.messages(conversationId)) {
            is ApiResult.Success -> {
                _state.update { it.copy(loading = false, messages = r.data.items, nextCursor = r.data.nextCursor) }
                markRead(conversationId)
            }
            is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
        }
    }

    private fun startPolling(conversationId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(PollIntervalMs)
                when (val r = messages.messages(conversationId)) {
                    is ApiResult.Success -> {
                        val before = _state.value.messages.map { it.id }.toSet()
                        val merged = merge(_state.value.messages, r.data.items)
                        _state.update { it.copy(messages = merged) }
                        // New incoming message while we're looking → mark it read.
                        if (merged.any { it.id !in before && !it.isMine }) markRead(conversationId)
                    }
                    is ApiResult.Error -> { /* transient; keep showing what we have */ }
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cid = s.conversationId ?: return
        val cursor = s.nextCursor ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = messages.messages(cid, cursor = cursor)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loadingMore = false, messages = merge(it.messages, r.data.items), nextCursor = r.data.nextCursor)
                }
                is ApiResult.Error -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun onInputChange(v: String) = _state.update { it.copy(input = v) }

    fun send() {
        val s = _state.value
        val cid = s.conversationId ?: return
        if (!s.canSend) return
        val body = s.input
        _state.update { it.copy(sending = true, input = "", error = null) }
        viewModelScope.launch {
            when (val r = messages.send(cid, body)) {
                is ApiResult.Success -> _state.update { it.copy(sending = false, messages = merge(it.messages, listOf(r.data))) }
                is ApiResult.Error -> _state.update { it.copy(sending = false, input = body, error = r.message) }
            }
        }
    }

    private fun markRead(conversationId: String) {
        viewModelScope.launch { messages.markRead(conversationId) }
    }

    /**
     * Union by id, newest first (ISO-8601 timestamps sort lexicographically in time
     * order). Incoming is listed last so the fresher server copy wins on duplicates.
     */
    private fun merge(current: List<MessageUi>, incoming: List<MessageUi>): List<MessageUi> =
        (current + incoming)
            .associateBy { it.id }
            .values
            .sortedByDescending { it.createdAtIso }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val PollIntervalMs = 3_000L
    }
}
