package com.voxstream.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = false // Custom Compose overlay controls are used
            }
        },
        update = { view ->
            if (view.player != exoPlayer) {
                view.player = exoPlayer
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}
