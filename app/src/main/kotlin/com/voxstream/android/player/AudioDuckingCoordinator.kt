package com.voxstream.android.player

import androidx.media3.exoplayer.ExoPlayer
import com.voxstream.android.providers.AndroidNativeTtsProvider
import com.voxstream.core.audio.DuckingCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages dynamic audio ducking on ExoPlayer when dubbed audio is spoken.
 */
class AudioDuckingCoordinator(
    private val scope: CoroutineScope,
    private val exoPlayer: ExoPlayer,
    private val nativeTts: AndroidNativeTtsProvider,
    private var duckingDb: Float = -14.0f
) {
    private val isDuckingActive = AtomicBoolean(false)
    private var rampJob: Job? = null
    private var currentVolume = 1.0f

    fun setDuckingDb(db: Float) {
        this.duckingDb = db
    }

    /**
     * Ducks ExoPlayer and speaks the provided subtitle translation text,
     * restoring master volume once speaking concludes.
     */
    fun playDubbedSpeech(text: String, language: String) {
        if (text.isBlank()) return

        isDuckingActive.set(true)
        rampVolume(targetVolume = DuckingCalculator.dbToLinearGain(duckingDb))

        nativeTts.speakDirect(text, language) {
            isDuckingActive.set(false)
            rampVolume(targetVolume = 1.0f)
        }
    }

    /**
     * Smoothly transitions ExoPlayer volume towards [targetVolume] to prevent pops.
     */
    private fun rampVolume(targetVolume: Float) {
        rampJob?.cancel()
        rampJob = scope.launch(Dispatchers.Main) {
            val stepTimeMs = 15L
            while (isActive && kotlin.math.abs(currentVolume - targetVolume) > 0.02f) {
                currentVolume = DuckingCalculator.stepRampVolume(
                    currentGain = currentVolume,
                    targetGain = targetVolume,
                    deltaSeconds = stepTimeMs / 1000f,
                    attackSeconds = 0.05f,
                    releaseSeconds = 0.15f
                )
                exoPlayer.volume = currentVolume
                delay(stepTimeMs)
            }
            currentVolume = targetVolume
            exoPlayer.volume = targetVolume
        }
    }

    fun stop() {
        nativeTts.stop()
        isDuckingActive.set(false)
        rampJob?.cancel()
        currentVolume = 1.0f
        exoPlayer.volume = 1.0f
    }
}
