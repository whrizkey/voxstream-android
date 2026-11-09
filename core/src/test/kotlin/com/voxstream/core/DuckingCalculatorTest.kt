package com.voxstream.core

import com.voxstream.core.audio.DuckingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckingCalculatorTest {

    @Test
    fun testDbToLinearGain() {
        // 0 dB -> 1.0 (no attenuation)
        assertEquals(1.0f, DuckingCalculator.dbToLinearGain(0f), 0.001f)

        // -6 dB -> ~0.501
        assertEquals(0.501f, DuckingCalculator.dbToLinearGain(-6.0f), 0.01f)

        // -14 dB -> ~0.199
        assertEquals(0.199f, DuckingCalculator.dbToLinearGain(-14.0f), 0.01f)

        // -20 dB -> ~0.100
        assertEquals(0.100f, DuckingCalculator.dbToLinearGain(-20.0f), 0.01f)
    }

    @Test
    fun testCalculateTargetVolume() {
        val quietGain = DuckingCalculator.calculateTargetVolume(isDubActive = true, duckingDb = -14.0f)
        assertTrue(quietGain in 0.19f..0.21f)

        val normalGain = DuckingCalculator.calculateTargetVolume(isDubActive = false, duckingDb = -14.0f)
        assertEquals(1.0f, normalGain, 0.001f)
    }

    @Test
    fun testStepRampVolumeSmoothTransitions() {
        val initial = 1.0f
        val target = 0.2f
        val delta = 0.02f // 20ms step

        val stepped = DuckingCalculator.stepRampVolume(
            currentGain = initial,
            targetGain = target,
            deltaSeconds = delta,
            attackSeconds = 0.05f
        )
        // Should have ramped down smoothly towards target
        assertTrue(stepped < initial)
        assertTrue(stepped >= target)
    }

    @Test
    fun testCalculateFitSpeedMultiplierBounds() {
        // Needs 1.5x speed -> capped at max 1.35x
        val speedFast = DuckingCalculator.calculateFitSpeedMultiplier(1500L, 1000L, maxSpeed = 1.35f)
        assertEquals(1.35f, speedFast, 0.001f)

        // Needs 0.5x speed -> capped at min 0.85x
        val speedSlow = DuckingCalculator.calculateFitSpeedMultiplier(500L, 1000L, minSpeed = 0.85f)
        assertEquals(0.85f, speedSlow, 0.001f)

        // Needs 1.1x speed -> exact
        val speedNormal = DuckingCalculator.calculateFitSpeedMultiplier(1100L, 1000L)
        assertEquals(1.1f, speedNormal, 0.01f)
    }
}
