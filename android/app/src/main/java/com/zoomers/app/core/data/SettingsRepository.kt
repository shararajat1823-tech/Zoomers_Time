package com.zoomers.app.core.data

import com.zoomers.app.core.model.SettingsUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.UpdateSettingsRequestDto
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val api: ZoomersApi,
) {
    suspend fun get(): ApiResult<SettingsUi> =
        when (val r = safeApiCall { api.settings() }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }

    suspend fun update(body: UpdateSettingsRequestDto): ApiResult<SettingsUi> =
        when (val r = safeApiCall { api.updateSettings(body) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }
}
