package com.projectsuperhuman.next

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class CardioQuickSaveResult(
    val success: Boolean,
    val session: CardioSession?,
    val message: String
)

internal data class CardioUndoResult(
    val restored: Boolean,
    val completedCopyDeleted: Boolean
)

internal class CardioSessionCoordinator(
    private val store: CardioSessionStore,
    private val repository: CardioRepository,
    private val controller: CardioLiveSessionController
) {
    private val mutationMutex = Mutex()

    suspend fun start(activity: CardioActivityType, workoutType: CardioWorkoutType): CardioLiveDraft =
        mutationMutex.withLock {
            val existing = store.load().draft
            if (existing != null) return@withLock existing
            controller.start(activity, workoutType).also { store.save(it) }
        }

    suspend fun pause(): CardioLiveDraft? = mutationMutex.withLock {
        val current = store.load().draft ?: return@withLock null
        controller.pause(current).also { store.save(it) }
    }

    suspend fun resume(): CardioLiveDraft? = mutationMutex.withLock {
        val current = store.load().draft ?: return@withLock null
        controller.resume(current).also { store.save(it) }
    }

    suspend fun prepareDetailedFinish(): CardioPreparedCompletion? = mutationMutex.withLock {
        val current = store.load().draft ?: return@withLock null
        val prepared = controller.freezeForCompletion(current)
        store.save(prepared.draft)
        prepared
    }

    suspend fun quickSave(): CardioQuickSaveResult = mutationMutex.withLock {
        val current = store.load().draft
            ?: return@withLock CardioQuickSaveResult(false, null, "No active cardio session")
        val prepared = controller.freezeForCompletion(current)

        // Freeze + checkpoint before touching completed storage. A failed/rejected ingestion can
        // never destroy the only recoverable copy of the workout.
        store.save(prepared.draft)
        val write = repository.save(prepared.session)
        if (!write.success) {
            return@withLock CardioQuickSaveResult(false, prepared.session, write.message)
        }

        store.clear(expectedSessionId = prepared.session.id)
        CardioQuickSaveResult(true, prepared.session, "Cardio session saved")
    }

    suspend fun saveDetailedLive(session: CardioSession): CardioWriteResult = mutationMutex.withLock {
        val current = store.load().draft
        if (current == null || current.sessionId != session.id) {
            return@withLock CardioWriteResult(false, "Live cardio draft is no longer available")
        }
        val write = repository.update(session.copy(source = "live"))
        if (write.success) store.clear(expectedSessionId = session.id)
        write
    }

    suspend fun saveManual(session: CardioSession): CardioWriteResult =
        mutationMutex.withLock { repository.save(session) }

    suspend fun updateCompleted(session: CardioSession): CardioWriteResult =
        mutationMutex.withLock { repository.update(session) }

    suspend fun discard(): Boolean = mutationMutex.withLock {
        val current = store.load().draft ?: return@withLock true
        store.clear(expectedSessionId = current.sessionId)
    }

    suspend fun undoQuickSave(session: CardioSession): CardioUndoResult = mutationMutex.withLock {
        val restored = controller.restorePausedFromSession(session)
        // Restore the draft before deleting the completed row. If deletion fails, the next save
        // uses the same sourceRecordId and safely replaces the completed copy.
        store.save(restored)
        val deleted = repository.delete(session.id)
        CardioUndoResult(restored = true, completedCopyDeleted = deleted)
    }
}
