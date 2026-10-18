package com.zoomers.app.core.data

import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.RegisterDeviceRequestDto
import com.zoomers.app.core.network.dto.RemoveDeviceRequestDto
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Registers/removes this device's FCM token with the backend for push delivery. */
@Singleton
class DeviceRepository @Inject constructor(
    private val api: ZoomersApi,
) {
    suspend fun register(token: String, platform: String = "android"): ApiResult<Unit> =
        safeApiCall { api.registerDevice(RegisterDeviceRequestDto(token, platform)) }

    suspend fun remove(token: String): ApiResult<Unit> =
        safeApiCall { api.removeDevice(RemoveDeviceRequestDto(token)) }
}
