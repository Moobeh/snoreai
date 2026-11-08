package com.moobehgames.snoresafe.core.support

import com.moobehgames.snoresafe.core.session.SessionSummary
import kotlinx.serialization.Serializable

@Serializable
data class SupportState(
    /** True after a tip, or after "I've already supported you". Hides every support prompt for good. */
    val supported: Boolean = false,
    val lastPromptEpochMs: Long? = null,
)

/** The few good moments when the "Buy me a coffee" card may appear. */
enum class SupportMoment { SEVENTH_NIGHT, NEW_LOWEST_SCORE, EXPORT_DONE }

/** Rules that keep the tip jar polite: rare, only at good moments, never once someone has supported. */
object SupportPolicy {
    const val MIN_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000
    private const val NIGHTS_BEFORE_LOWEST_COUNTS = 3

    fun shouldShowCard(state: SupportState, moment: SupportMoment?, nowMs: Long, onboardingDone: Boolean, recording: Boolean): Boolean {
        if (moment == null || state.supported || !onboardingDone || recording) return false
        val last = state.lastPromptEpochMs ?: return true
        return nowMs - last >= MIN_INTERVAL_MS
    }

    fun showSettingsEntry(state: SupportState): Boolean = !state.supported

    /** The moment a finished night creates, if any. [history] excludes [night]. */
    fun momentAfterNight(night: SessionSummary, history: List<SessionSummary>): SupportMoment? {
        val total = history.size + 1
        if (total == 7) return SupportMoment.SEVENTH_NIGHT
        if (history.size >= NIGHTS_BEFORE_LOWEST_COUNTS && night.monitoredMs >= 2 * 60 * 60 * 1000L) {
            val lowest = history.minOf { it.snoreScore }
            if (night.snoreScore < lowest) return SupportMoment.NEW_LOWEST_SCORE
        }
        return null
    }

    fun shown(state: SupportState, nowMs: Long): SupportState = state.copy(lastPromptEpochMs = nowMs)

    fun supported(state: SupportState): SupportState = state.copy(supported = true)
}
