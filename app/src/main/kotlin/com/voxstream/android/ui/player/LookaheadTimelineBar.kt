package com.voxstream.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxstream.android.ui.theme.Emerald500
import com.voxstream.android.ui.theme.Indigo500
import com.voxstream.android.ui.theme.Slate400
import com.voxstream.android.ui.theme.Slate700
import com.voxstream.core.models.LookaheadWindow

@Composable
fun LookaheadTimelineBar(
    playheadMs: Long,
    durationMs: Long,
    lookaheadWindow: LookaheadWindow,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationSafe = durationMs.coerceAtLeast(1L)
    val playheadFraction = (playheadMs.toFloat() / durationSafe).coerceIn(0f, 1f)
    val bufferedEndMs = maxOf(playheadMs, lookaheadWindow.bufferedTranslateMs)
    val bufferedFraction = (bufferedEndMs.toFloat() / durationSafe).coerceIn(0f, 1f)

    val lookaheadSeconds = ((bufferedEndMs - playheadMs) / 1000).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Track visual container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Slate700)
        ) {
            // Buffered lookahead lead
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .height(6.dp)
                    .background(Indigo500.copy(alpha = 0.45f))
            )
            // Active playhead progress
            Box(
                modifier = Modifier
                    .fillMaxWidth(playheadFraction)
                    .height(6.dp)
                    .background(Indigo500)
            )
        }

        // Interactive seek slider
        Slider(
            value = playheadMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationSafe.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Indigo500,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(playheadMs)} / ${formatTime(durationMs)}",
                color = Slate400,
                fontSize = 12.sp
            )

            Text(
                text = "Lookahead buffer: +${lookaheadSeconds}s",
                color = if (lookaheadSeconds >= 10) Emerald500 else Color(0xFFF59E0B),
                fontSize = 12.sp
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSecs = (millis / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%02d:%02d".format(mins, secs)
}
