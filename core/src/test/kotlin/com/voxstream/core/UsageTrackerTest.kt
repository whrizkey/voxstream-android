package com.voxstream.core

import com.voxstream.core.accounting.BudgetExceededException
import com.voxstream.core.accounting.UsageTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTrackerTest {

    @Test
    fun testAccurateCostEstimation() {
        val tracker = UsageTracker(
            maxCostUsd = 10.0,
            whisperCostPerMinuteUsd = 0.006,
            translateCostPerMillionTokensUsd = 1.0,
            ttsCostPerMillionCharsUsd = 10.0
        )

        // 10 minutes of audio -> 0.06 USD
        tracker.recordSttDuration(600)
        // 100,000 tokens -> 0.10 USD
        tracker.recordTranslateTokens(100_000)
        // 10,000 chars -> 0.10 USD
        tracker.recordTtsCharacters(10_000)

        val totalCost = tracker.getEstimatedCostUsd()
        assertEquals(0.26, totalCost, 0.001)
    }

    @Test(expected = BudgetExceededException::class)
    fun testHardCeilingEnforcementThrowsException() {
        val tracker = UsageTracker(
            maxCostUsd = 0.05,
            whisperCostPerMinuteUsd = 0.006
        )

        // 10 minutes -> $0.06 > $0.05 ceiling -> should throw BudgetExceededException
        tracker.recordSttDuration(600)
    }
}
