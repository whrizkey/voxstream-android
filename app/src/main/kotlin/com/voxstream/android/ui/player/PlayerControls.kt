package com.voxstream.android.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxstream.android.ui.theme.Indigo500
import com.voxstream.android.ui.theme.Slate200
import com.voxstream.android.ui.theme.Slate800
import com.voxstream.core.models.PlaybackMode

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    mode: PlaybackMode,
    sourceLang: String,
    targetLang: String,
    onTogglePlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onModeChange: (PlaybackMode) -> Unit,
    onOpenStreamClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onOpenLanguagesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Stream URL and Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenStreamClick) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Open Stream URL",
                    tint = Slate200
                )
            }

            IconButton(onClick = onOpenSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Slate200
                )
            }

            // Language pair badge
            FilterChip(
                selected = true,
                onClick = onOpenLanguagesClick,
                label = { Text("${sourceLang.uppercase()} → ${targetLang.uppercase()}", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Slate800,
                    selectedLabelColor = Indigo500
                )
            )
        }

        // Center: Transport Controls (Rewind, Play/Pause, Forward)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onSeekRelative(-10_000L) }) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Rewind 10s",
                    tint = Slate200,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = { onSeekRelative(10_000L) }) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Forward 10s",
                    tint = Slate200,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Right: Mode selector chips (Subs, Dub, Both)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeChip(
                label = "Subs",
                selected = mode == PlaybackMode.SUBTITLES,
                onClick = { onModeChange(PlaybackMode.SUBTITLES) }
            )
            ModeChip(
                label = "Dub",
                selected = mode == PlaybackMode.DUBBING,
                onClick = { onModeChange(PlaybackMode.DUBBING) }
            )
            ModeChip(
                label = "Both",
                selected = mode == PlaybackMode.BOTH,
                onClick = { onModeChange(PlaybackMode.BOTH) }
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Indigo500,
            selectedLabelColor = Color.White,
            containerColor = Slate800,
            labelColor = Slate200
        )
    )
}
