package com.zoomers.app.feature.editprofile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.FeedRefreshBus
import com.zoomers.app.core.data.ProfileRepository
import com.zoomers.app.core.media.ImageCompressor
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val loading: Boolean = true,
    val displayName: String = "",
    val bio: String = "",
    val website: String = "",
    val currentAvatarUrl: String? = null,
    val newAvatarUri: Uri? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = displayName.isNotBlank() && !saving && !loading
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val feedRefreshBus: FeedRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = profileRepository.me()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        displayName = r.data.displayName,
                        bio = r.data.bio.orEmpty(),
                        website = r.data.website.orEmpty(),
                        currentAvatarUrl = r.data.avatarUrl,
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun onDisplayNameChange(v: String) = _state.update { it.copy(displayName = v) }
    fun onBioChange(v: String) = _state.update { it.copy(bio = v) }
    fun onWebsiteChange(v: String) = _state.update { it.copy(website = v) }
    fun onAvatarPicked(uri: Uri?) { if (uri != null) _state.update { it.copy(newAvatarUri = uri) } }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val avatar = s.newAvatarUri?.let { ImageCompressor.fromUri(context, it) }
            if (s.newAvatarUri != null && avatar == null) {
                _state.update { it.copy(saving = false, error = "Couldn't read that image. Try another.") }
                return@launch
            }
            when (val r = profileRepository.updateProfile(s.displayName, s.bio, s.website, avatar)) {
                is ApiResult.Success -> {
                    feedRefreshBus.requestRefresh() // profile screen reloads
                    _state.update { it.copy(saving = false, saved = true) }
                }
                is ApiResult.Error -> _state.update { it.copy(saving = false, error = r.message) }
            }
        }
    }
}
