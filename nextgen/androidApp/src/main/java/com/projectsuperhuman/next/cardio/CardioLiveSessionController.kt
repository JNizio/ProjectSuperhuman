package com.projectsuperhuman.next

import android.os.SystemClock
import java.util.UUID

internal interface CardioClock {
    fun epochMillis(): Long
    fun elapsedRealtimeMillis(): Long
}

internal object AndroidCardioClock : CardioClock {
    override fun epochMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

internal class CardioLiveSessionController(
    private val clock: CardioClock = AndroidCardioClock,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() }
) {
    fun start(
        activity: CardioActivityType,
        workoutType: CardioWorkoutType
    ): CardioLiveDraft {
        val epoch = clock.epochMillis()
        val elapsed = clock.elapsedRealtimeMillis()
        return CardioLiveDraft(
            sessionId = newSessionId(),
            activity = activity,
            workoutType = workoutType,
            startedAtEpochMs = epoch,
            phase = CardioLivePhase.RECORDING,
            phaseStartedEpochMs = epoch,
            phaseStartedElapsedRealtimeMs = elapsed
        )
    }

    fun timing(draft: CardioLiveDraft): CardioLiveTiming {
        val delta = currentPhaseDelta(draft)
        return when (draft.phase) {
            CardioLivePhase.RECORDING -> CardioLiveTiming(
                activeMs = draft.accumulatedActiveMs + delta,
                pausedMs = draft.accumulatedPausedMs
            )
            CardioLivePhase.PAUSED -> CardioLiveTiming(
                activeMs = draft.accumulatedActiveMs,
                pausedMs = draft.accumulatedPausedMs + delta
            )
            CardioLivePhase.FINISHING -> CardioLiveTiming(
                activeMs = draft.accumulatedActiveMs,
                pausedMs = draft.accumulatedPausedMs
            )
        }
    }

    fun pause(draft: CardioLiveDraft): CardioLiveDraft {
        if (draft.phase != CardioLivePhase.RECORDING) return draft
        val timing = timing(draft)
        val epoch = clock.epochMillis()
        return draft.copy(
            accumulatedActiveMs = timing.activeMs,
            accumulatedPausedMs = timing.pausedMs,
            phase = CardioLivePhase.PAUSED,
            phaseStartedEpochMs = epoch,
            phaseStartedElapsedRealtimeMs = clock.elapsedRealtimeMillis(),
            pendingCompletionEpochMs = null
        )
    }

    fun resume(draft: CardioLiveDraft): CardioLiveDraft {
        if (draft.phase == CardioLivePhase.RECORDING) return draft
        val timing = timing(draft)
        val epoch = clock.epochMillis()
        return draft.copy(
            accumulatedActiveMs = timing.activeMs,
            accumulatedPausedMs = timing.pausedMs,
            phase = CardioLivePhase.RECORDING,
            phaseStartedEpochMs = epoch,
            phaseStartedElapsedRealtimeMs = clock.elapsedRealtimeMillis(),
            pendingCompletionEpochMs = null
        )
    }

    fun freezeForCompletion(draft: CardioLiveDraft): CardioPreparedCompletion {
        if (draft.phase == CardioLivePhase.FINISHING && draft.pendingCompletionEpochMs != null) {
            return CardioPreparedCompletion(draft, toSession(draft))
        }

        val timing = timing(draft)
        val completedAt = clock.epochMillis()
        val frozen = draft.copy(
            accumulatedActiveMs = timing.activeMs.coerceAtLeast(1_000L),
            accumulatedPausedMs = timing.pausedMs.coerceAtLeast(0L),
            phase = CardioLivePhase.FINISHING,
            phaseStartedEpochMs = completedAt,
            phaseStartedElapsedRealtimeMs = clock.elapsedRealtimeMillis(),
            pendingCompletionEpochMs = completedAt
        )
        return CardioPreparedCompletion(frozen, toSession(frozen))
    }

    fun restorePausedFromSession(session: CardioSession): CardioLiveDraft {
        val epoch = clock.epochMillis()
        return CardioLiveDraft(
            sessionId = session.id,
            activity = session.activity,
            workoutType = session.workoutType,
            startedAtEpochMs = session.startedAt,
            accumulatedActiveMs = session.durationSeconds.coerceAtLeast(1) * 1000L,
            accumulatedPausedMs = session.pausedDurationSeconds.coerceAtLeast(0) * 1000L,
            phase = CardioLivePhase.PAUSED,
            phaseStartedEpochMs = epoch,
            phaseStartedElapsedRealtimeMs = clock.elapsedRealtimeMillis()
        )
    }

    private fun toSession(draft: CardioLiveDraft): CardioSession {
        val completedAt = requireNotNull(draft.pendingCompletionEpochMs) {
            "Draft must be frozen before conversion to a completed CardioSession"
        }
        return CardioSession(
            id = draft.sessionId,
            activity = draft.activity,
            startedAt = draft.startedAtEpochMs,
            endedAt = completedAt,
            durationSeconds = (draft.accumulatedActiveMs / 1000L).toInt().coerceAtLeast(1),
            pausedDurationSeconds = (draft.accumulatedPausedMs / 1000L).toInt().coerceAtLeast(0),
            source = "live",
            workoutType = draft.workoutType
        )
    }

    private fun currentPhaseDelta(draft: CardioLiveDraft): Long {
        if (draft.phase == CardioLivePhase.FINISHING) return 0L

        val nowElapsed = clock.elapsedRealtimeMillis()
        val monotonicStart = draft.phaseStartedElapsedRealtimeMs
        if (monotonicStart > 0L && nowElapsed >= monotonicStart) {
            return (nowElapsed - monotonicStart).coerceAtMost(MAX_RECOVERY_DELTA_MS)
        }

        // Only a reboot or migrated legacy draft can reach this fallback. Normal process death
        // stays on elapsedRealtime(), so manual wall-clock edits cannot alter active duration.
        return (clock.epochMillis() - draft.phaseStartedEpochMs)
            .coerceIn(0L, MAX_RECOVERY_DELTA_MS)
    }

    private companion object {
        const val MAX_RECOVERY_DELTA_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
