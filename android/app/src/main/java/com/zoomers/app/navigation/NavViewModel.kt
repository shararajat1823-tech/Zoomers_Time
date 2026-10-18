package com.zoomers.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.ProfileRepository
import com.zoomers.app.core.model.ProfileUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Supplies the signed-in user's profile so the nav can show their avatar. */
@HiltViewModel
class NavViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _me = MutableStateFlow<ProfileUi?>(null)
    val me: StateFlow<ProfileUi?> = _me.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            when (val r = profileRepository.me()) {
                is ApiResult.Success -> _me.value = r.data
                is ApiResult.Error -> { /* leave null; nav falls back to a placeholder dot */ }
            }
        }
    }
}
