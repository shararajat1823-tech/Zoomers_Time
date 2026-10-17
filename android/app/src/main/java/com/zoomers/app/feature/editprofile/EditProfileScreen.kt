package com.zoomers.app.feature.editprofile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.zoomers.app.designsystem.component.ZPrimaryButton
import com.zoomers.app.designsystem.component.ZTextField
import com.zoomers.app.designsystem.component.ZTextLink
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

/** Edit the signed-in user's profile: display name, bio, website, and avatar. */
@Composable
fun EditProfileScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onAvatarPicked(uri) }

    fun pickAvatar() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Box(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ZTextLink(text = "Cancel", onClick = onCancel)
                Text("Edit profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text)
                Text("Cancel", color = androidx.compose.ui.graphics.Color.Transparent)
            }

            // Avatar
            val avatarModel: Any? = state.newAvatarUri ?: state.currentAvatarUrl
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(tintFor(state.displayName.ifBlank { "z" }))
                    .clickable(enabled = !state.saving) { pickAvatar() },
                contentAlignment = Alignment.Center,
            ) {
                if (avatarModel != null) {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("＋", style = MaterialTheme.typography.headlineMedium, color = colors.background)
                }
            }
            ZTextLink(text = "Change profile photo", onClick = { pickAvatar() })

            // Fields
            ZTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = "Name",
                placeholder = "Your name",
            )
            ZTextField(
                value = state.bio,
                onValueChange = viewModel::onBioChange,
                label = "Bio",
                placeholder = "Tell people about yourself",
                singleLine = false,
            )
            ZTextField(
                value = state.website,
                onValueChange = viewModel::onWebsiteChange,
                label = "Website",
                placeholder = "https://",
            )

            if (state.error != null) {
                Text(state.error!!, color = colors.like, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            ZPrimaryButton(
                text = if (state.saving) "Saving…" else "Save",
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
