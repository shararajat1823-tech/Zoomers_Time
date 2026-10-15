package com.zoomers.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zoomers.app.designsystem.component.EmptyState

/** Lightweight destination placeholder for screens landing in later milestones. */
@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        EmptyState(title = title, subtitle = "Coming in the next milestone.")
    }
}
