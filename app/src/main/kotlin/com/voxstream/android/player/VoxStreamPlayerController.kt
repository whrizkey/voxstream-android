package com.voxstream.android.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.voxstream.android.providers.AndroidNativeTtsProvider
import com.voxstream.android.providers.MlKitTranslateProvider
import com.voxstream.android.storage.SecureKeyStore
import com.voxstream.core.accounting.UsageTracker
import com.voxstream.core.models.Cue
import com.voxstream.core.models.LookaheadWindow
import com.voxstream.core.models.PlaybackMode
import com.voxstream.core.providers.FakeSttProvider
import com.voxstream.core.providers.FakeTtsProvider
import com.voxstream.core.providers.OpenAiCompatSttProvider
import com.voxstream.core.providers.OpenAiCompatTranslateProvider
import com.voxstream.core.providers.SttProvider
import com.voxstream.core.providers.TranslateProvider
import com.voxstream.core.providers.TtsProvider
import com.voxstream.core.scheduler.LookaheadScheduler
import com.voxstream.core.subtitles.SubtitleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Master player controller coupling ExoPlayer video playback with VoxStream's
 * real-time rolling lookahead subtitle and dubbing pipeline.
 */
class VoxStreamPlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    val secureKeyStore = SecureKeyStore(context)
    val subtitleStore = SubtitleStore()
    val usageTracker = UsageTracker(maxCostUsd = secureKeyStore.maxCostUsd.toDouble())

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    val nativeTts = AndroidNativeTtsProvider(context)
    val duckingCoordinator = AudioDuckingCoordinator(
        scope = scope,
        exoPlayer = exoPlayer,
        nativeTts = nativeTts,
        duckingDb = secureKeyStore.duckingAttenuationDb
    )

    private var activeSttProvider: SttProvider = createDefaultStt()
    private var activeTranslateProvider: TranslateProvider = MlKitTranslateProvider()
    private var activeTtsProvider: TtsProvider = nativeTts

    var scheduler = LookaheadScheduler(
        scope = scope,
        sttProvider = activeSttProvider,
        translateProvider = activeTranslateProvider,
        ttsProvider = activeTtsProvider,
        subtitleStore = subtitleStore,
        usageTracker = usageTracker,
        targetLookaheadMs = secureKeyStore.lookaheadBufferSeconds * 1000L
    )

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _activeCues = MutableStateFlow<List<Cue>>(emptyList())
    val activeCues: StateFlow<List<Cue>> = _activeCues.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.BOTH)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private val _sourceLang = MutableStateFlow("en")
    val sourceLang: StateFlow<String> = _sourceLang.asStateFlow()

    private val _targetLang = MutableStateFlow("es")
    val targetLang: StateFlow<String> = _targetLang.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private var tickerJob: Job? = null
    private var lastSpokenCueId: String? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                scheduler.setUserPaused(!playing)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                    _isBuffering.value = false
                } else if (playbackState == Player.STATE_BUFFERING) {
                    _isBuffering.value = true
                }
            }
        })

        startTicker()
    }

    private fun createDefaultStt(): SttProvider {
        val apiKey = secureKeyStore.openAiApiKey
        return if (apiKey.isNotBlank()) {
            OpenAiCompatSttProvider(
                baseUrl = secureKeyStore.openAiBaseUrl,
                apiKey = apiKey
            )
        } else {
            FakeSttProvider(name = "Simulated Audio STT", isOffline = true)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val currentMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                _playheadMs.value = currentMs
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                _durationMs.value = duration

                // Fetch active subtitles via fast binary search
                val cues = subtitleStore.getActiveCues(currentMs)
                _activeCues.value = cues

                // Trigger dub speech if in DUBBING or BOTH mode
                val mode = _playbackMode.value
                if (mode == PlaybackMode.DUBBING || mode == PlaybackMode.BOTH) {
                    val primaryCue = cues.firstOrNull()
                    if (primaryCue != null && primaryCue.id != lastSpokenCueId) {
                        lastSpokenCueId = primaryCue.id
                        duckingCoordinator.playDubbedSpeech(
                            text = primaryCue.translatedText,
                            language = _targetLang.value
                        )
                    }
                }

                // Advance lookahead pipeline step
                if (duration > 0L) {
                    scheduler.step(
                        playheadMs = currentMs,
                        mediaDurationMs = duration
                    ) { startMs, endMs ->
                        // Audio extractor placeholder or file slice
                        ByteArray(2048)
                    }
                }

                delay(50L) // 20 updates per second
            }
        }
    }

    fun openMedia(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        subtitleStore.clear()
        scheduler.onSeek(0L)
        lastSpokenCueId = null
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
        duckingCoordinator.stop()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _durationMs.value)
        exoPlayer.seekTo(target)
        scheduler.onSeek(target)
        duckingCoordinator.stop()
        lastSpokenCueId = null
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
        scheduler.setPlaybackMode(mode)
        if (mode == PlaybackMode.SUBTITLES) {
            duckingCoordinator.stop()
        }
    }

    fun setLanguages(source: String, target: String) {
        _sourceLang.value = source
        _targetLang.value = target
        scheduler.setLanguages(source, target)
        // Invalidate subtitles on language switch
        subtitleStore.clear()
        scheduler.onSeek(exoPlayer.currentPosition)
    }

    fun updateDuckingDb(db: Float) {
        secureKeyStore.duckingAttenuationDb = db
        duckingCoordinator.setDuckingDb(db)
    }

    fun release() {
        tickerJob?.cancel()
        exoPlayer.release()
        duckingCoordinator.stop()
        nativeTts.shutdown()
        scheduler.clear()
    }
}
