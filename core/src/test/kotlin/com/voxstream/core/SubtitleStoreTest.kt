package com.voxstream.core

import com.voxstream.core.models.Cue
import com.voxstream.core.subtitles.SubtitleStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubtitleStoreTest {

    private lateinit var store: SubtitleStore

    @Before
    fun setUp() {
        store = SubtitleStore()
    }

    @Test
    fun testSortedInsertionAndCount() {
        store.addCue(Cue("3", 2000L, 3000L, "Three", "Tres"))
        store.addCue(Cue("1", 0L, 1000L, "One", "Uno"))
        store.addCue(Cue("2", 1000L, 2000L, "Two", "Dos"))

        val all = store.getAllCues()
        assertEquals(3, all.size)
        assertEquals("One", all[0].originalText)
        assertEquals("Two", all[1].originalText)
        assertEquals("Three", all[2].originalText)
    }

    @Test
    fun testBinarySearchActiveCueLookup() {
        store.addAll(
            listOf(
                Cue("1", 1000L, 3000L, "Hello", "Hola"),
                Cue("2", 3000L, 5000L, "World", "Mundo"),
                Cue("3", 7000L, 9000L, "Later", "Más tarde")
            )
        )

        // Before first cue
        assertTrue(store.getActiveCues(500L).isEmpty())

        // Inside first cue
        val cuesAt2000 = store.getActiveCues(2000L)
        assertEquals(1, cuesAt2000.size)
        assertEquals("Hello", cuesAt2000[0].originalText)

        // Inside second cue boundary
        val cuesAt3000 = store.getActiveCues(3000L)
        assertEquals(1, cuesAt3000.size)
        assertEquals("World", cuesAt3000[0].originalText)

        // In gap between cue 2 and 3
        assertTrue(store.getActiveCues(6000L).isEmpty())

        // Inside third cue
        val cuesAt8500 = store.getActiveCues(8500L)
        assertEquals(1, cuesAt8500.size)
        assertEquals("Later", cuesAt8500[0].originalText)
    }

    @Test
    fun testSrtExportFormat() {
        store.addCue(Cue("1", 1500L, 4000L, "Original text", "Texto traducido"))
        val srt = store.exportSrt(useTranslated = true)

        assertTrue(srt.contains("1"))
        assertTrue(srt.contains("00:00:01,500 --> 00:00:04,000"))
        assertTrue(srt.contains("Texto traducido"))
    }
}
