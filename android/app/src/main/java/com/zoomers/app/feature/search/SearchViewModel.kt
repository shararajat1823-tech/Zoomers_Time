package com.zoomers.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.UserRepository
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

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val users: List<UserListItemUi> = emptyList(),
    val showingSuggested: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init { loadSuggested() }

    private fun loadSuggested() {
        _state.update { it.copy(loading = true, showingSuggested = true, error = null) }
        viewModelScope.launch {
            when (val r = userRepository.suggested()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, users = r.data.items) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            loadSuggested()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _state.update { it.copy(loading = true, showingSuggested = false, error = null) }
            when (val r = userRepository.searchUsers(q.trim())) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, users = r.data.items) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
