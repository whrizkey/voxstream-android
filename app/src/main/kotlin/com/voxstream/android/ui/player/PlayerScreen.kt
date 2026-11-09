package com.voxstream.android.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxstream.android.player.VoxStreamPlayerController
import com.voxstream.android.ui.dialogs.LanguagePickerDialog
import com.voxstream.android.ui.dialogs.StreamUrlDialog
import com.voxstream.android.ui.theme.Indigo500
import com.voxstream.android.ui.theme.Slate900

@Composable
fun PlayerScreen(
    controller: VoxStreamPlayerController,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by controller.isPlaying.collectAsState()
    val playheadMs by controller.playheadMs.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    val activeCues by controller.activeCues.collectAsState()
    val mode by controller.playbackMode.collectAsState()
    val sourceLang by controller.sourceLang.collectAsState()
    val targetLang by controller.targetLang.collectAsState()
    val isBuffering by controller.isBuffering.collectAsState()
    val lookaheadWindow by controller.scheduler.windowState.collectAsState()

    var showStreamDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Video surface
            VideoPlayerView(
                exoPlayer = controller.exoPlayer,
                modifier = Modifier.fillMaxSize()
            )

            // Dual subtitle overlay (positioned over bottom-center of video)
            DualSubtitleOverlay(
                cues = activeCues,
                showOriginal = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            )

            // Buffering / Underrun spinner
            if (isBuffering || lookaheadWindow.isUnderrun) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Indigo500)
                        Text(
                            text = if (lookaheadWindow.isUnderrun) "Buffering lookahead pipeline…" else "Buffering video…",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            // Bottom control bar (timeline scrubber + playback actions)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Slate900.copy(alpha = 0.88f))
            ) {
                LookaheadTimelineBar(
                    playheadMs = playheadMs,
                    durationMs = durationMs,
                    lookaheadWindow = lookaheadWindow,
                    onSeek = { targetMs -> controller.seekTo(targetMs) }
                )

                PlayerControls(
                    isPlaying = isPlaying,
                    mode = mode,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    onTogglePlayPause = { controller.togglePlayPause() },
                    onSeekRelative = { delta -> controller.seekTo(playheadMs + delta) },
                    onModeChange = { newMode -> controller.setPlaybackMode(newMode) },
                    onOpenStreamClick = { showStreamDialog = true },
                    onOpenSettingsClick = onNavigateToSettings,
                    onOpenLanguagesClick = { showLanguageDialog = true }
                )
            }
        }
    }

    if (showStreamDialog) {
        StreamUrlDialog(
            onDismiss = { showStreamDialog = false },
            onConfirm = { url ->
                showStreamDialog = false
                controller.openMedia(Uri.parse(url))
            }
        )
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            currentSource = sourceLang,
            currentTarget = targetLang,
            onDismiss = { showLanguageDialog = false },
            onLanguagesSelected = { src, tgt ->
                showLanguageDialog = false
                controller.setLanguages(src, tgt)
            }
        )
    }
}
