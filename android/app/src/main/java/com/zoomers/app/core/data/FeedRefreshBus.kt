package com.zoomers.app.core.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny app-wide signal that the following feed should reload — emitted after
 * the user creates a post, collected by the home feed. Decouples the create
 * flow from the feed without a shared ViewModel.
 */
@Singleton
class FeedRefreshBus @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun requestRefresh() {
        _events.tryEmit(Unit)
    }
}
