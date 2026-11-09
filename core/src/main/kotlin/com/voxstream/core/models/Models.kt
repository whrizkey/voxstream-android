package com.voxstream.core.models

import kotlinx.serialization.Serializable

/**
 * Playback presentation modes supported by VoxStream.
 */
@Serializable
enum class PlaybackMode {
    SUBTITLES,
    DUBBING,
    BOTH
}

/**
 * Supported or recognized language descriptor.
 */
@Serializable
data class Language(
    val code: String,
    val displayName: String
) {
    companion object {
        val ENGLISH = Language("en", "English")
        val SPANISH = Language("es", "Spanish")
        val FRENCH = Language("fr", "French")
        val GERMAN = Language("de", "German")
        val JAPANESE = Language("ja", "Japanese")
        val CHINESE = Language("zh", "Chinese")
        val RUSSIAN = Language("ru", "Russian")
        val ARABIC = Language("ar", "Arabic")
        val HINDI = Language("hi", "Hindi")
        val PORTUGUESE = Language("pt", "Portuguese")
        val ITALIAN = Language("it", "Italian")
        val KOREAN = Language("ko", "Korean")

        val DEFAULT_LIST = listOf(
            ENGLISH, SPANISH, FRENCH, GERMAN, JAPANESE,
            CHINESE, RUSSIAN, ARABIC, HINDI, PORTUGUESE, ITALIAN, KOREAN
        )
    }
}

/**
 * Represents a time slice scheduled for ingestion and processing.
 */
@Serializable
data class ChunkSpec(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val generation: Long
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * A speech recognition segment extracted from audio.
 */
@Serializable
data class Segment(
    val id: String,
    val chunkIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speakerTag: String? = null,
    val confidence: Float = 1.0f
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * A translated speech segment paired with original source metadata.
 */
@Serializable
data class TranslatedSegment(
    val id: String,
    val source: Segment,
    val targetText: String,
    val targetLanguage: String
) {
    val startMs: Long get() = source.startMs
    val endMs: Long get() = source.endMs
}

/**
 * Synthesized audio clip ready for dub playback.
 */
data class DubClip(
    val id: String,
    val segmentId: String,
    val startMs: Long,
    val endMs: Long,
    val audioData: ByteArray,
    val sampleRate: Int = 24000,
    val channels: Int = 1,
    val durationMs: Long,
    val speedMultiplier: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DubClip) return false
        return id == other.id && startMs == other.startMs && endMs == other.endMs
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + startMs.hashCode()
        result = 31 * result + endMs.hashCode()
        return result
    }
}

/**
 * Subtitle cue containing source and translated text.
 */
@Serializable
data class Cue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String,
    val speakerTag: String? = null
) : Comparable<Cue> {
    override fun compareTo(other: Cue): Int {
        val startCmp = this.startMs.compareTo(other.startMs)
        return if (startCmp != 0) startCmp else this.endMs.compareTo(other.endMs)
    }

    fun contains(playheadMs: Long): Boolean = playheadMs in startMs until endMs
}

/**
 * Status snapshot of the lookahead buffer pipeline.
 */
@Serializable
data class LookaheadWindow(
    val playheadMs: Long,
    val targetLookaheadMs: Long,
    val bufferedSttMs: Long,
    val bufferedTranslateMs: Long,
    val bufferedDubMs: Long,
    val isUnderrun: Boolean = false,
    val activeGeneration: Long = 0L
)
