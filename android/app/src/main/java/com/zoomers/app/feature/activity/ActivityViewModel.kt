package com.zoomers.app.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.NotificationRepository
import com.zoomers.app.core.model.NotificationUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityUiState(
    val loading: Boolean = true,
    val items: List<NotificationUi> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val r = notificationRepository.notifications()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, items = r.data.items) }
                    // Mark read once viewed (best effort).
                    notificationRepository.markAllRead()
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
