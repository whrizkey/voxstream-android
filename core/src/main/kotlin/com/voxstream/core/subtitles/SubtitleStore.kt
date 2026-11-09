package com.voxstream.core.subtitles

import com.voxstream.core.models.Cue
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe, sorted subtitle store optimized for binary-search playhead queries
 * and timeline rendering.
 */
class SubtitleStore {
    private val cues = ArrayList<Cue>()
    private val lock = Any()

    /**
     * Insert or update a subtitle cue maintaining sorted order.
     */
    fun addCue(cue: Cue) {
        synchronized(lock) {
            // Remove existing cue with same ID if present
            cues.removeAll { it.id == cue.id }
            val index = cues.binarySearch(cue)
            val insertionPoint = if (index >= 0) index else -(index + 1)
            cues.add(insertionPoint, cue)
        }
    }

    /**
     * Bulk add cues and ensure strict sorting.
     */
    fun addAll(newCues: Collection<Cue>) {
        synchronized(lock) {
            val ids = newCues.map { it.id }.toSet()
            cues.removeAll { it.id in ids }
            cues.addAll(newCues)
            cues.sort()
        }
    }

    /**
     * Binary search lookup for the active cue(s) at current playhead timestamp.
     * Runtime complexity is O(log N) for the initial search.
     */
    fun getActiveCues(playheadMs: Long): List<Cue> {
        synchronized(lock) {
            if (cues.isEmpty()) return emptyList()

            // Binary search for insertion or match
            var low = 0
            var high = cues.size - 1
            var matchIdx = -1

            while (low <= high) {
                val mid = (low + high) ushr 1
                val cue = cues[mid]
                when {
                    playheadMs < cue.startMs -> high = mid - 1
                    playheadMs >= cue.endMs -> low = mid + 1
                    else -> {
                        matchIdx = mid
                        break
                    }
                }
            }

            if (matchIdx == -1) {
                // Check if any neighboring cue spans across due to overlaps
                return cues.filter { it.contains(playheadMs) }
            }

            // Gather any overlapping cues around the found index
            val result = mutableListOf<Cue>()
            var i = matchIdx
            while (i >= 0 && cues[i].endMs > playheadMs) {
                if (cues[i].contains(playheadMs)) {
                    result.add(cues[i])
                }
                i--
            }
            i = matchIdx + 1
            while (i < cues.size && cues[i].startMs <= playheadMs) {
                if (cues[i].contains(playheadMs)) {
                    result.add(cues[i])
                }
                i++
            }

            return result.distinctBy { it.id }.sorted()
        }
    }

    /**
     * Returns all cues in range [fromMs, toMs] for timeline visualization.
     */
    fun getCuesInRange(fromMs: Long, toMs: Long): List<Cue> {
        synchronized(lock) {
            return cues.filter { it.endMs >= fromMs && it.startMs <= toMs }
        }
    }

    fun getAllCues(): List<Cue> {
        synchronized(lock) {
            return ArrayList(cues)
        }
    }

    fun size(): Int = synchronized(lock) { cues.size }

    fun clear() {
        synchronized(lock) {
            cues.clear()
        }
    }

    /**
     * Exports subtitles in SubRip (.srt) format.
     */
    fun exportSrt(useTranslated: Boolean = true): String {
        val list = getAllCues()
        val sb = StringBuilder()
        list.forEachIndexed { index, cue ->
            sb.append(index + 1).append("\n")
            sb.append(formatSrtTimestamp(cue.startMs))
                .append(" --> ")
                .append(formatSrtTimestamp(cue.endMs))
                .append("\n")
            sb.append(if (useTranslated) cue.translatedText else cue.originalText)
            sb.append("\n\n")
        }
        return sb.toString()
    }

    private fun formatSrtTimestamp(millis: Long): String {
        val hours = millis / 3_600_000
        val minutes = (millis % 3_600_000) / 60_000
        val seconds = (millis % 60_000) / 1000
        val ms = millis % 1000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, ms)
    }
}
