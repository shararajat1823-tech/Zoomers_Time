package com.zoomers.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.feature.common.BackTopBar

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSaved: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    Column(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().verticalScroll(rememberScrollState()),
    ) {
        BackTopBar(title = "Settings", onBack = onBack)

        NavRow(icon = Icons.Outlined.BookmarkBorder, label = "Saved posts", onClick = onOpenSaved)

        SectionHeader("Privacy")
        ToggleRow("Private account", state.isPrivate, viewModel::setPrivate)
        state.settings?.let { s ->
            ToggleRow("Show online status", s.showOnlineStatus, viewModel::setOnlineStatus)

            SectionHeader("Notifications")
            ToggleRow("Likes", s.pushLikes, viewModel::setPushLikes)
            ToggleRow("Comments", s.pushComments, viewModel::setPushComments)
            ToggleRow("New followers", s.pushFollows, viewModel::setPushFollows)
        }

        SectionHeader("Account")
        NavRow(icon = Icons.AutoMirrored.Outlined.Logout, label = "Log out", tint = colors.like) {
            viewModel.logout()
            onLoggedOut()
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = ZoomersTheme.colors.textMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.primary,
            ),
        )
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = ZoomersTheme.colors.text,
    onClick: () -> Unit,
) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.padding(start = Spacing.md).weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textMuted)
    }
}
