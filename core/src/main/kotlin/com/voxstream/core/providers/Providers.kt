package com.voxstream.core.providers

import com.voxstream.core.models.DubClip
import com.voxstream.core.models.Segment
import kotlinx.coroutines.delay
import java.util.UUID

interface SttProvider {
    val name: String
    val isOffline: Boolean
    suspend fun transcribe(
        audioBytes: ByteArray,
        sampleRate: Int,
        language: String?
    ): List<Segment>
}

interface TranslateProvider {
    val name: String
    val isOffline: Boolean
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String
}

interface TtsProvider {
    val name: String
    val isOffline: Boolean
    suspend fun synthesize(
        text: String,
        language: String,
        speedMultiplier: Float = 1.0f
    ): DubClip
}

/**
 * Deterministic fake STT provider for tests and preview simulations.
 */
class FakeSttProvider(
    override val name: String = "FakeSTT",
    override val isOffline: Boolean = true,
    private val simulatedDelayMs: Long = 10L
) : SttProvider {
    override suspend fun transcribe(
        audioBytes: ByteArray,
        sampleRate: Int,
        language: String?
    ): List<Segment> {
        if (simulatedDelayMs > 0) delay(simulatedDelayMs)
        val text = "Simulated transcribed speech fragment of ${audioBytes.size} bytes"
        return listOf(
            Segment(
                id = UUID.randomUUID().toString(),
                chunkIndex = 0,
                startMs = 0L,
                endMs = 3000L,
                text = text,
                confidence = 0.95f
            )
        )
    }
}

/**
 * Deterministic fake translation provider.
 */
class FakeTranslateProvider(
    override val name: String = "FakeTranslate",
    override val isOffline: Boolean = true,
    private val simulatedDelayMs: Long = 10L
) : TranslateProvider {
    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String {
        if (simulatedDelayMs > 0) delay(simulatedDelayMs)
        return "[$targetLanguage] $text"
    }
}

/**
 * Deterministic fake TTS provider producing synthetic PCM waveform.
 */
class FakeTtsProvider(
    override val name: String = "FakeTTS",
    override val isOffline: Boolean = true,
    private val sampleRate: Int = 24000
) : TtsProvider {
    override suspend fun synthesize(
        text: String,
        language: String,
        speedMultiplier: Float
    ): DubClip {
        val durationMs = ((text.length * 60L) / speedMultiplier).toLong().coerceAtLeast(500L)
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val pcm = ByteArray(numSamples * 2) // 16-bit mono silence/sine
        return DubClip(
            id = UUID.randomUUID().toString(),
            segmentId = UUID.randomUUID().toString(),
            startMs = 0L,
            endMs = durationMs,
            audioData = pcm,
            sampleRate = sampleRate,
            durationMs = durationMs,
            speedMultiplier = speedMultiplier
        )
    }
}
