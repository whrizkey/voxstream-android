package com.voxstream.core.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Calculates audio ducking gains and smooth transition ramps for mixing dubbed speech
 * over original media audio.
 */
object DuckingCalculator {

    /**
     * Converts attenuation in decibels (negative value) to linear amplitude multiplier.
     * E.g. -14 dB -> ~0.20 linear gain.
     */
    fun dbToLinearGain(db: Float): Float {
        if (db >= 0f) return 1.0f
        return 10.0f.pow(db / 20.0f).coerceIn(0.01f, 1.0f)
    }

    /**
     * Computes the instant target volume for master video audio based on whether
     * any dub clip is active at timestamp [playheadMs].
     *
     * @param isDubActive Whether dubbed speech is currently sounding
     * @param duckingDb Desired ducking reduction in dB (e.g. -14.0f)
     * @return Linear volume factor between 0.0 and 1.0
     */
    fun calculateTargetVolume(isDubActive: Boolean, duckingDb: Float = -14.0f): Float {
        return if (isDubActive) dbToLinearGain(duckingDb) else 1.0f
    }

    /**
     * Calculates smooth step transition towards target volume (attack / release ramp)
     * to eliminate pop / click artifacts during abrupt ducking changes.
     */
    fun stepRampVolume(
        currentGain: Float,
        targetGain: Float,
        deltaSeconds: Float,
        attackSeconds: Float = 0.05f,
        releaseSeconds: Float = 0.15f
    ): Float {
        val rampTime = if (targetGain < currentGain) attackSeconds else releaseSeconds
        val maxStep = deltaSeconds / max(0.001f, rampTime)
        return when {
            targetGain > currentGain -> min(targetGain, currentGain + maxStep)
            targetGain < currentGain -> max(targetGain, currentGain - maxStep)
            else -> targetGain
        }
    }

    /**
     * Computes time-stretch / playback speed multiplier required so that synthesized speech
     * fits into the allotted segment duration without bleeding excessively into next segment.
     */
    fun calculateFitSpeedMultiplier(
        synthesizedDurationMs: Long,
        availableDurationMs: Long,
        maxSpeed: Float = 1.35f,
        minSpeed: Float = 0.85f
    ): Float {
        if (availableDurationMs <= 0 || synthesizedDurationMs <= 0) return 1.0f
        val neededRatio = synthesizedDurationMs.toFloat() / availableDurationMs.toFloat()
        return neededRatio.coerceIn(minSpeed, maxSpeed)
    }
}
