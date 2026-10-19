package com.zoomers.app.feature.reels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.zoomers.app.core.model.ReelUi
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Spacing

@Composable
fun ReelsScreen(
    onOpenProfile: (String) -> Unit = {},
    viewModel: ReelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.addReel(uri)
    }
    fun pickVideo() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
    )

    // One shared player for the whole feed; the visible page attaches its view.
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    var muted by remember { mutableStateOf(false) }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loading -> CircularProgressIndicator(
                color = Color.White, strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )

            state.reels.isEmpty() -> EmptyReels(onPick = { pickVideo() }, uploading = state.uploading)

            else -> {
                val pagerState = rememberPagerState(pageCount = { state.reels.size })

                // Point the player at whichever reel settled into view.
                LaunchedEffect(pagerState.settledPage, state.reels) {
                    val reel = state.reels.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
                    player.setMediaItem(MediaItem.fromUri(reel.mediaUrl))
                    player.prepare()
                    player.play()
                }
                // Infinite scroll.
                LaunchedEffect(pagerState.settledPage) {
                    if (pagerState.settledPage >= state.reels.size - 2) viewModel.loadMore()
                }

                VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val reel = state.reels[page]
                    ReelPage(
                        reel = reel,
                        isActive = page == pagerState.settledPage,
                        player = player,
                        muted = muted,
                        onToggleMute = { muted = !muted },
                        onToggleLike = { viewModel.toggleLike(reel) },
                        onOpenProfile = { onOpenProfile(reel.username) },
                    )
                }
            }
        }

        // Header (over the video).
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reels", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            if (state.uploading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Box(Modifier.size(40.dp).clickable { pickVideo() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = "New reel", tint = Color.White)
                }
            }
        }

        state.error?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(Spacing.lg),
            )
        }
    }
}

@Composable
private fun ReelPage(
    reel: ReelUi,
    isActive: Boolean,
    player: ExoPlayer,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(reel.id) {
                detectTapGestures(onDoubleTap = {
                    onToggleLike()
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                })
            },
    ) {
        if (isActive) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setPlayer(player)
                    }
                },
                update = { it.setPlayer(player) },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!reel.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Right-hand action rail: like + mute.
        Column(
            Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clickable(onClick = onToggleLike), contentAlignment = Alignment.Center) {
                    Icon(
                        if (reel.viewerHasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (reel.viewerHasLiked) Color(0xFFFF3B5C) else Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text("${reel.likeCount}", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.size(44.dp).clickable(onClick = onToggleMute), contentAlignment = Alignment.Center) {
                Icon(
                    if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Bottom-left: author + caption.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(Spacing.lg)
                .fillMaxWidth(0.8f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpenProfile)) {
                ZAvatar(key = reel.username, size = 36.dp, imageUrl = reel.avatarUrl)
                Spacer(Modifier.width(Spacing.sm))
                Text(reel.username, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (!reel.caption.isNullOrBlank()) {
                Text(reel.caption, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
        }
    }
}

@Composable
private fun EmptyReels(onPick: () -> Unit, uploading: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Spacing.lg))
        Text("No reels yet", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.sm))
        Text("Be the first — share a short video.", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.xl))
        if (uploading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        } else {
            Row(
                Modifier
                    .clickable(onClick = onPick)
                    .background(Color.White.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Pick a video", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
