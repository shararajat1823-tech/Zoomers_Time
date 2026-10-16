package com.zoomers.app.feature.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zoomers.app.designsystem.component.ZPillTabs
import com.zoomers.app.designsystem.component.ZPrimaryButton
import com.zoomers.app.designsystem.component.ZTextField
import com.zoomers.app.designsystem.component.ZTextLink
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

/**
 * New-post composer: pick a photo, add a caption, choose who can see it, share.
 * Responsive — the content column is width-capped and centred on larger screens.
 */
@Composable
fun CreatePostScreen(
    onPosted: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onImagePicked(uri) }

    fun launchPicker() =
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(state.posted) {
        if (state.posted) onPosted()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ZTextLink(text = "Cancel", onClick = onCancel)
                Text(
                    "New post",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
                // Spacer balance so the title stays centred.
                Text("Cancel", color = androidx.compose.ui.graphics.Color.Transparent)
            }

            // Photo picker / preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceElevated)
                    .clickable(enabled = !state.submitting) { launchPicker() },
                contentAlignment = Alignment.Center,
            ) {
                if (state.imageUri != null) {
                    AsyncImage(
                        model = state.imageUri,
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("＋", style = MaterialTheme.typography.displaySmall, color = colors.primary)
                        Text("Tap to choose a photo", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    }
                }
            }

            if (state.imageUri != null && !state.submitting) {
                ZTextLink(text = "Change photo", onClick = { launchPicker() })
            }

            // Caption
            ZTextField(
                value = state.caption,
                onValueChange = viewModel::onCaptionChange,
                label = "Caption",
                placeholder = "Write a caption…",
                singleLine = false,
            )

            // Audience
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Who can see this", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                ZPillTabs(
                    options = PostAudience.entries.map { it.label },
                    selectedIndex = state.audience.ordinal,
                    onSelect = { viewModel.onAudienceChange(PostAudience.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.error != null) {
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.like,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ZPrimaryButton(
                text = if (state.submitting) "Sharing…" else "Share",
                onClick = viewModel::share,
                enabled = state.canShare,
                loading = state.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
