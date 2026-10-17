package com.zoomers.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.core.model.UserListItemUi
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Shapes
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

@Composable
fun SearchScreen(
    onOpenUser: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            placeholder = { Text("Search people") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textMuted) },
            singleLine = true,
            shape = Shapes.pill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.hairline,
                cursorColor = colors.primary,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
            ),
        )

        if (state.showingSuggested && state.users.isNotEmpty()) {
            Text(
                "Suggested",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = colors.textMuted)
                }
                state.users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.query.isBlank()) "Search for people to follow" else "No people found",
                        color = colors.textMuted,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.users, key = { it.id }) { user ->
                        UserRow(user, onClick = { onOpenUser(user.username) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: UserListItemUi, onClick: () -> Unit) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZAvatar(key = user.username, size = 44.dp)
        Column(Modifier.padding(start = Spacing.md)) {
            Text(user.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(user.displayName, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
    }
}
