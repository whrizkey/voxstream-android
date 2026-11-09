package com.voxstream.core

import com.voxstream.core.accounting.UsageTracker
import com.voxstream.core.models.PlaybackMode
import com.voxstream.core.providers.FakeSttProvider
import com.voxstream.core.providers.FakeTranslateProvider
import com.voxstream.core.providers.FakeTtsProvider
import com.voxstream.core.scheduler.LookaheadScheduler
import com.voxstream.core.subtitles.SubtitleStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LookaheadSchedulerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var store: SubtitleStore
    private lateinit var scheduler: LookaheadScheduler

    @Before
    fun setUp() {
        store = SubtitleStore()
        scheduler = LookaheadScheduler(
            scope = testScope,
            sttProvider = FakeSttProvider(simulatedDelayMs = 0),
            translateProvider = FakeTranslateProvider(simulatedDelayMs = 0),
            ttsProvider = FakeTtsProvider(),
            subtitleStore = store,
            usageTracker = UsageTracker(maxCostUsd = 100.0),
            targetLookaheadMs = 30_000L,
            chunkDurationMs = 10_000L,
            minBufferUnderrunMs = 2_000L,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun testGenerationBumpingOnSeek() {
        val initialGen = scheduler.getActiveGeneration()
        scheduler.onSeek(5000L)
        val genAfterSeek = scheduler.getActiveGeneration()

        assertTrue(genAfterSeek > initialGen)
        assertEquals(5000L, scheduler.windowState.value.playheadMs)
    }

    @Test
    fun testStepIngestionPipelineProducesCues() = testScope.runTest {
        // Step at t = 0 on a 60-second media
        scheduler.step(playheadMs = 0L, mediaDurationMs = 60_000L) { _, _ ->
            ByteArray(1024)
        }

        testScope.advanceUntilIdle()

        // SubtitleStore should now contain generated cue
        assertTrue(store.size() > 0)
        val cues = store.getActiveCues(1500L)
        assertEquals(1, cues.size)
        assertTrue(cues[0].translatedText.contains("[es]"))
    }

    @Test
    fun testSubtitleOnlyModeSkipsTtsEmission() = testScope.runTest {
        scheduler.setPlaybackMode(PlaybackMode.SUBTITLES)

        scheduler.step(playheadMs = 0L, mediaDurationMs = 60_000L) { _, _ ->
            ByteArray(1024)
        }

        testScope.advanceUntilIdle()

        // No dub clip scheduled at playhead
        val dub = scheduler.getActiveDubClip(1000L)
        assertEquals(null, dub)
        // But subtitles are present
        assertTrue(store.size() > 0)
    }
}
