package com.voxstream.core.accounting

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class BudgetExceededException(message: String) : Exception(message)

/**
 * Tracks API usage units and enforces a hard cost ceiling to protect against
 * runaway billing.
 */
class UsageTracker(
    val maxCostUsd: Double = 5.0,
    val whisperCostPerMinuteUsd: Double = 0.006,
    val translateCostPerMillionTokensUsd: Double = 0.150,
    val ttsCostPerMillionCharsUsd: Double = 15.00
) {
    private val totalAudioSeconds = AtomicLong(0)
    private val totalTranslateTokens = AtomicLong(0)
    private val totalTtsCharacters = AtomicLong(0)

    /**
     * Records STT audio processed and checks budget.
     */
    fun recordSttDuration(durationSeconds: Long) {
        totalAudioSeconds.addAndGet(durationSeconds)
        checkBudget()
    }

    /**
     * Records translation token consumption and checks budget.
     */
    fun recordTranslateTokens(tokens: Long) {
        totalTranslateTokens.addAndGet(tokens)
        checkBudget()
    }

    /**
     * Records TTS characters synthesized and checks budget.
     */
    fun recordTtsCharacters(chars: Long) {
        totalTtsCharacters.addAndGet(chars)
        checkBudget()
    }

    /**
     * Computes total estimated expenditure in USD.
     */
    fun getEstimatedCostUsd(): Double {
        val sttCost = (totalAudioSeconds.get() / 60.0) * whisperCostPerMinuteUsd
        val translateCost = (totalTranslateTokens.get() / 1_000_000.0) * translateCostPerMillionTokensUsd
        val ttsCost = (totalTtsCharacters.get() / 1_000_000.0) * ttsCostPerMillionCharsUsd
        return sttCost + translateCost + ttsCost
    }

    fun checkBudget() {
        if (maxCostUsd > 0.0) {
            val cost = getEstimatedCostUsd()
            if (cost >= maxCostUsd) {
                throw BudgetExceededException(
                    "Cost ceiling exceeded: estimated $${String.format("%.4f", cost)} >= max allowed $${String.format("%.2f", maxCostUsd)}"
                )
            }
        }
    }

    fun reset() {
        totalAudioSeconds.set(0)
        totalTranslateTokens.set(0)
        totalTtsCharacters.set(0)
    }

    val audioSeconds: Long get() = totalAudioSeconds.get()
    val translateTokens: Long get() = totalTranslateTokens.get()
    val ttsCharacters: Long get() = totalTtsCharacters.get()
}
