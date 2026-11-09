package com.voxstream.core.scheduler

import com.voxstream.core.accounting.UsageTracker
import com.voxstream.core.models.ChunkSpec
import com.voxstream.core.models.Cue
import com.voxstream.core.models.DubClip
import com.voxstream.core.models.LookaheadWindow
import com.voxstream.core.models.PlaybackMode
import com.voxstream.core.models.Segment
import com.voxstream.core.models.TranslatedSegment
import com.voxstream.core.providers.SttProvider
import com.voxstream.core.providers.TranslateProvider
import com.voxstream.core.providers.TtsProvider
import com.voxstream.core.subtitles.SubtitleStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Orchestrates lookahead audio ingestion, STT, translation, and TTS synthesis.
 * Implements generation tracking on seeks and non-blocking coroutine pipelines.
 */
class LookaheadScheduler(
    private val scope: CoroutineScope,
    private val sttProvider: SttProvider,
    private val translateProvider: TranslateProvider,
    private val ttsProvider: TtsProvider,
    private val subtitleStore: SubtitleStore,
    private val usageTracker: UsageTracker = UsageTracker(),
    val targetLookaheadMs: Long = 60_000L,
    val chunkDurationMs: Long = 15_000L,
    val minBufferUnderrunMs: Long = 2_000L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val generation = AtomicLong(0L)
    private val currentPlayheadMs = AtomicLong(0L)
    private val isUserPaused = AtomicBoolean(false)
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val dubClips = ConcurrentHashMap<String, DubClip>()
    private val pipelineMutex = Mutex()

    private val _windowState = MutableStateFlow(
        LookaheadWindow(
            playheadMs = 0L,
            targetLookaheadMs = targetLookaheadMs,
            bufferedSttMs = 0L,
            bufferedTranslateMs = 0L,
            bufferedDubMs = 0L
        )
    )
    val windowState: StateFlow<LookaheadWindow> = _windowState.asStateFlow()

    private val _dubClipReady = MutableSharedFlow<DubClip>(extraBufferCapacity = 64)
    val dubClipReady: SharedFlow<DubClip> = _dubClipReady.asSharedFlow()

    private val _underrunEvent = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    val underrunEvent: SharedFlow<Boolean> = _underrunEvent.asSharedFlow()

    private var sourceLanguage: String = "en"
    private var targetLanguage: String = "es"
    private var playbackMode: PlaybackMode = PlaybackMode.BOTH

    fun setLanguages(source: String, target: String) {
        this.sourceLanguage = source
        this.targetLanguage = target
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        this.playbackMode = mode
    }

    fun setUserPaused(paused: Boolean) {
        isUserPaused.set(paused)
    }

    fun getActiveGeneration(): Long = generation.get()

    /**
     * Handle user seek to a new playhead timestamp:
     * Bumps generation counter to immediately invalidate all in-flight pipeline tasks,
     * clears active jobs, and updates playhead.
     */
    fun onSeek(newPlayheadMs: Long) {
        val nextGen = generation.incrementAndGet()
        // Cancel all existing chunk processing coroutines
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()

        currentPlayheadMs.set(newPlayheadMs)
        updateWindowState(newPlayheadMs, nextGen)
    }

    /**
     * Periodic step called by playback ticker or controller to examine buffer health
     * and schedule new lookahead chunks as needed.
     */
    fun step(
        playheadMs: Long,
        mediaDurationMs: Long,
        audioExtractor: (startMs: Long, endMs: Long) -> ByteArray?
    ) {
        currentPlayheadMs.set(playheadMs)
        val gen = generation.get()

        val bufferedEndMs = computeBufferedEndMs()
        val neededLookaheadEnd = minOf(playheadMs + targetLookaheadMs, mediaDurationMs)

        // Check for buffer underrun
        val remainingBuffer = bufferedEndMs - playheadMs
        val isUnderrun = remainingBuffer < minBufferUnderrunMs && playheadMs < mediaDurationMs
        _underrunEvent.tryEmit(isUnderrun)

        updateWindowState(playheadMs, gen, isUnderrun)

        if (bufferedEndMs >= neededLookaheadEnd) {
            return // Sufficient lookahead buffer exists
        }

        // Schedule next chunk
        val chunkIndex = (bufferedEndMs / chunkDurationMs).toInt()
        if (activeJobs.containsKey(chunkIndex)) {
            return // Already in progress
        }

        val chunkStart = chunkIndex * chunkDurationMs
        val chunkEnd = minOf(chunkStart + chunkDurationMs, mediaDurationMs)
        if (chunkStart >= chunkEnd) return

        val chunkSpec = ChunkSpec(chunkIndex, chunkStart, chunkEnd, gen)

        val job = scope.launch(dispatcher) {
            try {
                processChunk(chunkSpec, audioExtractor)
            } catch (e: CancellationException) {
                // Seek or cancel occurred; gracefully ignore
            } catch (e: Exception) {
                // Log and permit next step iteration to retry
            } finally {
                activeJobs.remove(chunkIndex)
            }
        }
        activeJobs[chunkIndex] = job
    }

    private suspend fun processChunk(
        chunk: ChunkSpec,
        audioExtractor: (startMs: Long, endMs: Long) -> ByteArray?
    ) {
        // Guard against stale generation
        if (chunk.generation != generation.get()) return

        val audioBytes = audioExtractor(chunk.startMs, chunk.endMs)
            ?: ByteArray(0)

        // Track usage
        usageTracker.recordSttDuration(chunk.durationMs / 1000)

        // Stage 1: STT Transcription
        val segments = sttProvider.transcribe(
            audioBytes = audioBytes,
            sampleRate = 16000,
            language = sourceLanguage
        )

        if (chunk.generation != generation.get()) return

        // Stage 2: Translation & Cue emission
        for (seg in segments) {
            if (chunk.generation != generation.get()) return

            val absoluteStart = chunk.startMs + seg.startMs
            val absoluteEnd = chunk.startMs + seg.endMs

            val translatedText = translateProvider.translate(
                text = seg.text,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )

            // Approximate token usage accounting
            usageTracker.recordTranslateTokens((seg.text.length + translatedText.length) / 4L + 20L)

            val cue = Cue(
                id = seg.id,
                startMs = absoluteStart,
                endMs = absoluteEnd,
                originalText = seg.text,
                translatedText = translatedText,
                speakerTag = seg.speakerTag
            )
            subtitleStore.addCue(cue)

            // Stage 3: TTS Dubbing (if enabled in mode)
            if (playbackMode == PlaybackMode.DUBBING || playbackMode == PlaybackMode.BOTH) {
                if (chunk.generation != generation.get()) return

                usageTracker.recordTtsCharacters(translatedText.length.toLong())

                val dubClip = ttsProvider.synthesize(
                    text = translatedText,
                    language = targetLanguage,
                    speedMultiplier = 1.0f
                )

                val positionedClip = dubClip.copy(
                    startMs = absoluteStart,
                    endMs = absoluteEnd
                )
                dubClips[positionedClip.id] = positionedClip
                _dubClipReady.tryEmit(positionedClip)
            }
        }
    }

    /**
     * Retrieves any dub clip scheduled for playback at [playheadMs].
     */
    fun getActiveDubClip(playheadMs: Long): DubClip? {
        return dubClips.values.firstOrNull { playheadMs in it.startMs until it.endMs }
    }

    private fun computeBufferedEndMs(): Long {
        val lastCue = subtitleStore.getAllCues().maxByOrNull { it.endMs }
        val cueEnd = lastCue?.endMs ?: currentPlayheadMs.get()
        return cueEnd
    }

    private fun updateWindowState(
        playheadMs: Long,
        gen: Long,
        isUnderrun: Boolean = false
    ) {
        val buffered = computeBufferedEndMs()
        _windowState.value = LookaheadWindow(
            playheadMs = playheadMs,
            targetLookaheadMs = targetLookaheadMs,
            bufferedSttMs = buffered,
            bufferedTranslateMs = buffered,
            bufferedDubMs = buffered,
            isUnderrun = isUnderrun,
            activeGeneration = gen
        )
    }

    fun clear() {
        generation.incrementAndGet()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        dubClips.clear()
        subtitleStore.clear()
    }
}
