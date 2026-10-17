package com.zoomers.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zoomers.app.core.model.StoryTrayUi
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

/**
 * Horizontally-scrolling stories rail backed by real story trays. "Your story"
 * leads with an add badge; each tray shows the teal unseen ring.
 */
@Composable
fun StoryRow(
    trays: List<StoryTrayUi>,
    myAvatarUrl: String?,
    onAddStory: () -> Unit,
    onOpenTray: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZoomersTheme.colors
    // "Your story" shortcut only leads the rail when you don't already have one.
    val ownTray = trays.firstOrNull { it.isSelf }
    val others = trays.filterNot { it.isSelf }

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(68.dp).clickable {
                    if (ownTray != null) onOpenTray(ownTray.authorId) else onAddStory()
                },
            ) {
                Box {
                    ZAvatar(key = "me", size = 56.dp, imageUrl = myAvatarUrl ?: ownTray?.avatarUrl, hasStoryRing = ownTray?.hasUnseen == true)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .clickable(onClick = onAddStory),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add to your story", tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                Text("Your story", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        items(others, key = { it.authorId }) { tray ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(68.dp).clickable { onOpenTray(tray.authorId) },
            ) {
                ZAvatar(key = tray.username, size = 56.dp, imageUrl = tray.avatarUrl, hasStoryRing = tray.hasUnseen)
                Text(
                    tray.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tray.hasUnseen) colors.text else colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
