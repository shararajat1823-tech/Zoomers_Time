package com.zoomers.app.feature.reels

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import com.zoomers.app.core.common.formatCount
import com.zoomers.app.core.model.ReelUi
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.AccentGradient
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
                        showFollow = reel.authorId !in state.followedAuthors,
                        onToggleMute = { muted = !muted },
                        onToggleLike = { viewModel.toggleLike(reel) },
                        onOpenProfile = { onOpenProfile(reel.username) },
                        onFollow = { viewModel.follow(reel.authorId) },
                    )
                }
            }
        }

        // Header (over the video): "Reels" with a gradient underline + actions.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Reels", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(38.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(AccentGradient)),
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Search",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(Spacing.lg))
            if (state.uploading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = "New reel",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp).clickable { pickVideo() },
                )
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
    showFollow: Boolean,
    onToggleMute: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenProfile: () -> Unit,
    onFollow: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current

    fun shareReel() {
        val text = buildString {
            reel.caption?.takeIf { it.isNotBlank() }?.let { append(it); append("\n\n") }
            append("Watch @${reel.username}'s reel on Zoomers")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Share reel")) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(reel.id) {
                detectTapGestures(
                    onTap = { onToggleMute() },
                    onDoubleTap = {
                        onToggleLike()
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    },
                )
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

        // Legibility scrim along the bottom.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))),
        )

        // Small mute indicator when sound is off.
        if (muted) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(Spacing.sm),
            ) {
                Icon(Icons.Filled.VolumeOff, contentDescription = "Muted", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Right-hand action rail.
        Column(
            Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = Spacing.md, bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            RailAction(
                icon = if (reel.viewerHasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                tint = if (reel.viewerHasLiked) Color(0xFFFF3B5C) else Color.White,
                label = formatCount(reel.likeCount),
                onClick = onToggleLike,
            )
            RailAction(icon = Icons.Outlined.ChatBubbleOutline, label = null, onClick = {})
            RailAction(icon = Icons.AutoMirrored.Filled.Send, label = null, onClick = { shareReel() })
            RailAction(icon = Icons.Filled.MoreHoriz, label = null, onClick = {})
            SpinningDisc(imageUrl = reel.thumbnailUrl ?: reel.avatarUrl, active = isActive)
        }

        // Bottom-left: author, caption, audio.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(start = Spacing.lg, end = 72.dp, bottom = Spacing.xl)
                .fillMaxWidth(0.82f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpenProfile)) {
                    ZAvatar(key = reel.username, size = 34.dp, imageUrl = reel.avatarUrl, hasStoryRing = true)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(reel.username, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (reel.isVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
                if (showFollow) {
                    Spacer(Modifier.width(Spacing.md))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                            .clickable(onClick = onFollow)
                            .padding(horizontal = Spacing.md, vertical = 5.dp),
                    ) {
                        Text("Follow", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!reel.caption.isNullOrBlank()) {
                Text(captionWithTags(reel.caption), color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            // Audio pill (we store no track metadata yet, so show the default original-audio label).
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = Spacing.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Original audio · ${reel.username}", color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** One icon + optional count in the vertical action rail. */
@Composable
private fun RailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String?, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        }
        if (label != null) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The rotating "record" at the bottom of the rail, Instagram-style. */
@Composable
private fun SpinningDisc(imageUrl: String?, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "disc")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "angle",
    )
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { if (active) rotationZ = angle }
            .clip(CircleShape)
            .background(Brush.linearGradient(AccentGradient))
            .border(4.dp, Color.Black.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(22.dp).clip(CircleShape),
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Highlights #hashtags within a caption. */
private fun captionWithTags(caption: String) = buildAnnotatedString {
    caption.split(" ").forEachIndexed { i, word ->
        if (i > 0) append(" ")
        if (word.startsWith("#") && word.length > 1) {
            withStyle(SpanStyle(color = Color(0xFFBFD0FF), fontWeight = FontWeight.SemiBold)) { append(word) }
        } else {
            append(word)
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
