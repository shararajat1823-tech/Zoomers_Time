package com.zoomers.app.feature.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.CommentRepository
import com.zoomers.app.core.data.CurrentUserProvider
import com.zoomers.app.core.model.CommentUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentsUiState(
    val loading: Boolean = true,
    val comments: List<CommentUi> = emptyList(),
    val input: String = "",
    val submitting: Boolean = false,
    val currentUserId: String? = null,
    val error: String? = null,
    val nextCursor: String? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && !submitting
}

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val commentRepository: CommentRepository,
    private val currentUser: CurrentUserProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var postId: String = ""

    fun load(postId: String) {
        this.postId = postId
        _state.update { it.copy(loading = it.comments.isEmpty(), error = null) }
        viewModelScope.launch {
            val uid = currentUser.userId()
            when (val r = commentRepository.comments(postId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, comments = r.data.items, nextCursor = r.data.nextCursor, currentUserId = uid)
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message, currentUserId = uid) }
            }
        }
    }

    fun delete(comment: CommentUi) {
        _state.update { it.copy(comments = it.comments.filterNot { c -> c.id == comment.id }) }
        viewModelScope.launch { commentRepository.delete(comment.id) }
    }

    fun onInputChange(v: String) = _state.update { it.copy(input = v) }

    fun send() {
        val s = _state.value
        if (!s.canSend) return
        val body = s.input
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = commentRepository.add(postId, body)) {
                is ApiResult.Success -> _state.update {
                    it.copy(submitting = false, input = "", comments = it.comments + r.data)
                }
                is ApiResult.Error -> _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }
}
