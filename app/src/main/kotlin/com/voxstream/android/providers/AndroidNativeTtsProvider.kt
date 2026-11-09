package com.voxstream.android.providers

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.voxstream.core.models.DubClip
import com.voxstream.core.providers.TtsProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device TTS provider utilizing the Android platform TextToSpeech engine.
 */
class AndroidNativeTtsProvider(
    private val context: Context
) : TtsProvider {

    override val name: String = "Android System TTS"
    override val isOffline: Boolean = true

    private var tts: TextToSpeech? = null
    private val isInitialized = AtomicBoolean(false)
    private val initLock = Object()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized.set(true)
                synchronized(initLock) {
                    initLock.notifyAll()
                }
            }
        }
    }

    private fun waitForInit() {
        if (!isInitialized.get()) {
            synchronized(initLock) {
                if (!isInitialized.get()) {
                    initLock.wait(3000)
                }
            }
        }
    }

    override suspend fun synthesize(
        text: String,
        language: String,
        speedMultiplier: Float
    ): DubClip = suspendCancellableCoroutine { continuation ->
        waitForInit()
        val engine = tts ?: run {
            continuation.resumeWithException(IllegalStateException("TTS engine uninitialized"))
            return@suspendCancellableCoroutine
        }

        val locale = Locale.forLanguageTag(language)
        engine.language = locale
        engine.setSpeechRate(speedMultiplier.coerceIn(0.5f, 2.0f))

        val utteranceId = UUID.randomUUID().toString()
        val tempFile = File(context.cacheDir, "dub_$utteranceId.wav")

        val listener = object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    val bytes = if (tempFile.exists()) tempFile.readBytes() else ByteArray(0)
                    tempFile.delete()
                    val durationMs = ((text.length * 65L) / speedMultiplier).toLong().coerceAtLeast(300L)
                    continuation.resume(
                        DubClip(
                            id = utteranceId,
                            segmentId = utteranceId,
                            startMs = 0L,
                            endMs = durationMs,
                            audioData = bytes,
                            sampleRate = 22050,
                            durationMs = durationMs,
                            speedMultiplier = speedMultiplier
                        )
                    )
                }
            }

            override fun onError(id: String?) {
                if (id == utteranceId) {
                    continuation.resumeWithException(RuntimeException("TTS synthesis failed for $utteranceId"))
                }
            }
        }

        engine.setOnUtteranceProgressListener(listener)
        val params = Bundle()
        engine.synthesizeToFile(text, params, tempFile, utteranceId)
    }

    fun speakDirect(text: String, language: String, onDone: () -> Unit) {
        waitForInit()
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(language)
        engine.language = locale
        val utteranceId = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (id == utteranceId) onDone() }
            override fun onError(id: String?) { if (id == utteranceId) onDone() }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
