package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioCoreReliabilityTest {
    @Test
    fun startPauseResumeFinish_tracksActiveAndPausedSeparately() {
        val clock = FakeClock()
        val controller = controller(clock)
        var draft = controller.start(CardioActivityType.RUNNING, CardioWorkoutType.ZONE_2)

        clock.advance(10_000)
        draft = controller.pause(draft)
        clock.advance(20_000)
        draft = controller.resume(draft)
        clock.advance(5_000)
        val completed = controller.freezeForCompletion(draft)

        assertEquals(15, completed.session.durationSeconds)
        assertEquals(20, completed.session.pausedDurationSeconds)
        assertEquals(CardioLivePhase.FINISHING, completed.draft.phase)
    }

    @Test
    fun wallClockChangeDoesNotCorruptActiveElapsedDuration() {
        val clock = FakeClock()
        val controller = controller(clock)
        val draft = controller.start(CardioActivityType.RUNNING, CardioWorkoutType.FREE)

        clock.elapsedMs += 30_000
        clock.epochMs += 3_600_000
        val timing = controller.timing(draft)

        assertEquals(30, timing.activeSeconds)
    }

    @Test
    fun pauseTimeIsNotCountedAsActiveDuration() {
        val clock = FakeClock()
        val controller = controller(clock)
        var draft = controller.start(CardioActivityType.CYCLING, CardioWorkoutType.EASY)

        clock.advance(12_000)
        draft = controller.pause(draft)
        clock.advance(45_000)
        val timing = controller.timing(draft)

        assertEquals(12, timing.activeSeconds)
        assertEquals(45, timing.pausedSeconds)
    }

    @Test
    fun finishTimestampIsActualWallClockCompletionNotStartPlusActiveDuration() {
        val clock = FakeClock()
        val controller = controller(clock)
        var draft = controller.start(CardioActivityType.RUNNING, CardioWorkoutType.INTERVALS)
        val start = draft.startedAtEpochMs

        clock.advance(10_000)
        draft = controller.pause(draft)
        clock.advance(120_000)
        draft = controller.resume(draft)
        clock.advance(10_000)

        val completed = controller.freezeForCompletion(draft)
        assertEquals(start + 140_000, completed.session.endedAt)
        assertEquals(20, completed.session.durationSeconds)
        assertNotEquals(start + completed.session.durationSeconds * 1000L, completed.session.endedAt)
    }

    @Test
    fun quickSaveCreatesExactlyOneCompletedSession() = runTest {
        val fixture = fixture()
        fixture.coordinator.start(CardioActivityType.WALKING, CardioWorkoutType.EASY)
        fixture.clock.advance(60_000)

        val result = fixture.coordinator.quickSave()

        assertTrue(result.success)
        assertEquals(1, fixture.repository.sessions.size)
        assertNull(fixture.store.load().draft)
    }

    @Test
    fun repeatedSaveAttemptsDoNotDuplicateSession() = runTest {
        val fixture = fixture()
        fixture.coordinator.start(CardioActivityType.RUNNING, CardioWorkoutType.FREE)
        fixture.clock.advance(30_000)

        val first = fixture.coordinator.quickSave()
        val second = fixture.coordinator.quickSave()

        assertTrue(first.success)
        assertFalse(second.success)
        assertEquals(1, fixture.repository.sessions.size)
        assertEquals(1, fixture.repository.successfulWriteCount)
    }

    @Test
    fun failedIngestionPreservesFrozenDraft() = runTest {
        val fixture = fixture()
        val draft = fixture.coordinator.start(CardioActivityType.ROWING, CardioWorkoutType.TEMPO)
        fixture.clock.advance(20_000)
        fixture.repository.nextWrite = CardioWriteResult(false, "disk failure")

        val result = fixture.coordinator.quickSave()

        assertFalse(result.success)
        val restored = assertNotNull(fixture.store.load().draft)
        assertEquals(draft.sessionId, restored.sessionId)
        assertEquals(CardioLivePhase.FINISHING, restored.phase)
    }

    @Test
    fun rejectedIngestionPreservesDraft() = runTest {
        val fixture = fixture()
        fixture.coordinator.start(CardioActivityType.HIIT, CardioWorkoutType.VO2_MAX)
        fixture.clock.advance(15_000)
        fixture.repository.nextWrite = CardioWriteResult(
            success = false,
            message = "rejected by validation",
            accepted = 0,
            rejected = 1
        )

        val result = fixture.coordinator.quickSave()

        assertFalse(result.success)
        assertNotNull(fixture.store.load().draft)
        assertTrue(fixture.repository.sessions.isEmpty())
    }

    @Test
    fun processRecreationRestoresRunningSessionAndElapsedTime() = runTest {
        val clock = FakeClock()
        val store = FakeStore()
        val firstController = controller(clock)
        val repository = FakeRepository()
        val first = CardioSessionCoordinator(store, repository, firstController)
        val started = first.start(CardioActivityType.RUNNING, CardioWorkoutType.LONG)

        clock.advance(25_000)
        val recreatedController = controller(clock)
        val loaded = assertNotNull(store.load().draft)

        assertEquals(started.sessionId, loaded.sessionId)
        assertEquals(CardioLivePhase.RECORDING, loaded.phase)
        assertEquals(25, recreatedController.timing(loaded).activeSeconds)
    }

    @Test
    fun pausedDraftRestoresPausedWithoutAddingActiveTime() = runTest {
        val fixture = fixture()
        fixture.coordinator.start(CardioActivityType.HIKING, CardioWorkoutType.EASY)
        fixture.clock.advance(8_000)
        fixture.coordinator.pause()
        fixture.clock.advance(50_000)

        val restored = assertNotNull(fixture.store.load().draft)
        val recreated = controller(fixture.clock)
        val timing = recreated.timing(restored)

        assertEquals(CardioLivePhase.PAUSED, restored.phase)
        assertEquals(8, timing.activeSeconds)
        assertEquals(50, timing.pausedSeconds)
    }

    @Test
    fun runningDraftContinuesElapsedAfterRecreation() = runTest {
        val fixture = fixture()
        fixture.coordinator.start(CardioActivityType.ELLIPTICAL, CardioWorkoutType.ZONE_2)
        fixture.clock.advance(7_000)
        val persisted = assertNotNull(fixture.store.load().draft)

        fixture.clock.advance(13_000)
        val recreatedController = controller(fixture.clock)

        assertEquals(20, recreatedController.timing(persisted).activeSeconds)
    }

    @Test
    fun explicitDiscardRemovesOnlyLiveDraft() = runTest {
        val fixture = fixture()
        fixture.coordinator.start(CardioActivityType.JUMP_ROPE, CardioWorkoutType.INTERVALS)
        assertNotNull(fixture.store.load().draft)

        assertTrue(fixture.coordinator.discard())
        assertNull(fixture.store.load().draft)
        assertTrue(fixture.repository.sessions.isEmpty())
    }

    @Test
    fun failedEditCannotEraseOriginalSession() = runTest {
        val fixture = fixture()
        val original = validSession(id = "stable-edit-id", duration = 600)
        fixture.repository.sessions[original.id] = original
        fixture.repository.nextWrite = CardioWriteResult(false, "replacement rejected", rejected = 1)

        val replacement = original.copy(durationSeconds = 900, notes = "edited")
        val result = fixture.coordinator.updateCompleted(replacement)

        assertFalse(result.success)
        assertEquals(original, fixture.repository.sessions[original.id])
    }

    @Test
    fun futureFinishDateIsRejected() {
        val now = 1_800_000_000_000L
        val issues = CardioValidation.validate(
            validSession(endedAt = now + 61_000L),
            nowEpochMs = now
        )
        assertTrue(issues.any { it.field == "endedAt" && it.message.contains("future") })
    }

    @Test
    fun zeroAndNegativeDurationAreRejected() {
        val now = 1_800_000_000_000L
        val zero = CardioValidation.validate(validSession(duration = 0, endedAt = now), now)
        val negative = CardioValidation.validate(validSession(duration = -1, endedAt = now), now)

        assertTrue(zero.any { it.field == "durationSeconds" })
        assertTrue(negative.any { it.field == "durationSeconds" })
    }

    @Test
    fun zoneDurationCannotExceedWorkoutDuration() {
        val now = 1_800_000_000_000L
        val issues = CardioValidation.validate(
            validSession(duration = 60, endedAt = now).copy(zoneSeconds = mapOf(2 to 61)),
            now
        )
        assertTrue(issues.any { it.field == "zoneSeconds" })
    }

    @Test
    fun stableSessionIdSurvivesPauseResumeAndDraftRestoration() = runTest {
        val fixture = fixture(sessionId = "permanent-cardio-id")
        val started = fixture.coordinator.start(CardioActivityType.SWIMMING, CardioWorkoutType.THRESHOLD)
        fixture.clock.advance(3_000)
        val paused = assertNotNull(fixture.coordinator.pause())
        fixture.clock.advance(2_000)
        val resumed = assertNotNull(fixture.coordinator.resume())
        val loaded = assertNotNull(fixture.store.load().draft)

        assertEquals("permanent-cardio-id", started.sessionId)
        assertEquals(started.sessionId, paused.sessionId)
        assertEquals(started.sessionId, resumed.sessionId)
        assertEquals(started.sessionId, loaded.sessionId)
    }

    @Test
    fun navigatingAwayAndRecreatingUiDoesNotDiscardLiveWorkout() = runTest {
        val clock = FakeClock()
        val store = FakeStore()
        val repository = FakeRepository()
        val originalCoordinator = CardioSessionCoordinator(store, repository, controller(clock))
        val started = originalCoordinator.start(CardioActivityType.RUNNING, CardioWorkoutType.ZONE_2)

        // Back navigation from the live screen intentionally performs no destructive coordinator
        // action. Recreate the UI-facing coordinator against the same persistent store.
        val recreatedCoordinator = CardioSessionCoordinator(store, repository, controller(clock))
        val stillThere = assertNotNull(store.load().draft)
        val resumed = assertNotNull(recreatedCoordinator.pause())

        assertEquals(started.sessionId, stillThere.sessionId)
        assertEquals(started.sessionId, resumed.sessionId)
    }

    @Test
    fun undoQuickSaveRestoresSameWorkoutAsPausedDraft() = runTest {
        val fixture = fixture()
        val started = fixture.coordinator.start(CardioActivityType.CYCLING, CardioWorkoutType.TEMPO)
        fixture.clock.advance(40_000)
        val saved = fixture.coordinator.quickSave()
        val completed = assertNotNull(saved.session)

        val undo = fixture.coordinator.undoQuickSave(completed)
        val restored = assertNotNull(fixture.store.load().draft)

        assertTrue(undo.restored)
        assertTrue(undo.completedCopyDeleted)
        assertEquals(started.sessionId, restored.sessionId)
        assertEquals(CardioLivePhase.PAUSED, restored.phase)
        assertNull(fixture.repository.sessions[started.sessionId])
    }

    @Test
    fun oldDraftSchemaMigratesAndFutureSchemaFailsGracefully() {
        val old = CardioDraftSchema.decode(
            CardioDraftRaw(
                schemaVersion = 1,
                sessionId = null,
                activity = CardioActivityType.RUNNING.name,
                workoutType = CardioWorkoutType.EASY.name,
                startedAtEpochMs = 1_700_000_000_000L,
                accumulatedActiveMs = 35_000L,
                accumulatedPausedMs = 0L,
                phase = null,
                phaseStartedEpochMs = 1_700_000_010_000L,
                phaseStartedElapsedRealtimeMs = 0L,
                pendingCompletionEpochMs = null,
                legacyIsRunning = false
            )
        )
        val migrated = assertNotNull(old.draft)
        assertTrue(old.migratedSchema)
        assertEquals(CARDIO_LIVE_DRAFT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(CardioLivePhase.PAUSED, migrated.phase)
        assertTrue(migrated.sessionId.startsWith("cardio-legacy-"))

        val future = CardioDraftSchema.decode(
            CardioDraftRaw(
                schemaVersion = CARDIO_LIVE_DRAFT_SCHEMA_VERSION + 10,
                sessionId = "future",
                activity = CardioActivityType.RUNNING.name,
                workoutType = CardioWorkoutType.FREE.name,
                startedAtEpochMs = 1_700_000_000_000L,
                accumulatedActiveMs = 0L,
                accumulatedPausedMs = 0L,
                phase = CardioLivePhase.PAUSED.name,
                phaseStartedEpochMs = 1_700_000_000_000L,
                phaseStartedElapsedRealtimeMs = 0L,
                pendingCompletionEpochMs = null
            )
        )
        assertNull(future.draft)
        assertEquals(CARDIO_LIVE_DRAFT_SCHEMA_VERSION + 10, future.incompatibleSchemaVersion)
    }

    @Test
    fun persistenceMappingUsesStableSourceRecordIdAcrossEdits() {
        val first = validSession(id = "stable-row", duration = 600).toHealthValue()
        val edited = validSession(id = "stable-row", duration = 900).toHealthValue()

        assertEquals("cardio:stable-row", first.metadata["sourceRecordId"])
        assertEquals(first.metadata["sourceRecordId"], edited.metadata["sourceRecordId"])
        assertNotEquals(first.value, edited.value)
    }

    private fun fixture(sessionId: String = "session-1"): Fixture {
        val clock = FakeClock()
        val store = FakeStore()
        val repository = FakeRepository()
        val controller = controller(clock, sessionId)
        return Fixture(
            clock = clock,
            store = store,
            repository = repository,
            coordinator = CardioSessionCoordinator(store, repository, controller)
        )
    }

    private fun controller(clock: FakeClock, id: String = "session-1") =
        CardioLiveSessionController(clock) { id }

    private fun validSession(
        id: String = "valid",
        duration: Int = 600,
        endedAt: Long = 1_800_000_000_000L
    ) = CardioSession(
        id = id,
        activity = CardioActivityType.RUNNING,
        startedAt = endedAt - duration.coerceAtLeast(0) * 1000L,
        endedAt = endedAt,
        durationSeconds = duration,
        source = "manual"
    )

    private data class Fixture(
        val clock: FakeClock,
        val store: FakeStore,
        val repository: FakeRepository,
        val coordinator: CardioSessionCoordinator
    )


    @Test
    fun importedStorageIdentitySurvivesDecodeEditEncode() {
        val row = HealthValue(
            domain = HealthDomain.EXERCISE,
            metric = "cardio_session",
            value = 30.0,
            unit = "min",
            timestampEpochMs = 1_800_000_000_000L,
            source = "health-connect-cardio",
            metadata = mapOf(
                "sessionId" to "hc-cardio-example",
                "sourceRecordId" to "hc-cardio:com.example.health:record-1",
                "activityType" to CardioActivityType.RUNNING.name,
                "startedAt" to "1799998200000",
                "endedAt" to "1800000000000",
                "durationSeconds" to "1800",
                "cardioSource" to "health-connect-cardio",
                "healthConnectRecordId" to "record-1"
            )
        )

        val edited = cardioSessionFromValue(row).copy(notes = "edited")
        val remapped = edited.toHealthValue()

        assertEquals("health-connect-cardio", remapped.source)
        assertEquals(
            "hc-cardio:com.example.health:record-1",
            remapped.metadata["sourceRecordId"]
        )
        assertEquals("record-1", remapped.metadata["ext.healthConnectRecordId"])
    }

    private class FakeClock(
        var epochMs: Long = 1_800_000_000_000L,
        var elapsedMs: Long = 10_000L
    ) : CardioClock {
        override fun epochMillis(): Long = epochMs
        override fun elapsedRealtimeMillis(): Long = elapsedMs

        fun advance(ms: Long) {
            epochMs += ms
            elapsedMs += ms
        }
    }

    private class FakeStore(initial: CardioLiveDraft? = null) : CardioSessionStore {
        private val state = MutableStateFlow(initial)

        override fun observe(): Flow<CardioLiveDraft?> = state

        override suspend fun load(): CardioDraftLoadResult = CardioDraftLoadResult(state.value)

        override suspend fun migrateLegacyIfNeeded(): CardioDraftLoadResult =
            CardioDraftLoadResult(state.value)

        override suspend fun save(draft: CardioLiveDraft) {
            state.value = draft
        }

        override suspend fun clear(expectedSessionId: String?): Boolean {
            val current = state.value
            if (current == null) return true
            if (expectedSessionId != null && current.sessionId != expectedSessionId) return false
            state.value = null
            return true
        }
    }

    private class FakeRepository : CardioRepository {
        val sessions = linkedMapOf<String, CardioSession>()
        var successfulWriteCount = 0
        var nextWrite: CardioWriteResult? = null

        override suspend fun save(session: CardioSession): CardioWriteResult = write(session)

        override suspend fun update(session: CardioSession): CardioWriteResult = write(session)

        private fun write(session: CardioSession): CardioWriteResult {
            val forced = nextWrite.also { nextWrite = null }
            if (forced != null && !forced.success) return forced
            sessions[session.id] = session
            successfulWriteCount += 1
            return forced ?: CardioWriteResult(true, "ok", accepted = 1)
        }

        override suspend fun delete(sessionId: String): Boolean =
            sessions.remove(sessionId) != null

        override suspend fun sessionById(sessionId: String): CardioSession? = sessions[sessionId]

        override suspend fun recentSessions(limit: Int): List<CardioSession> =
            sessions.values.sortedByDescending { it.endedAt }.take(limit)

        override suspend fun sessionsBetween(fromEpochMs: Long, toEpochMs: Long): List<CardioSession> =
            sessions.values.filter { it.endedAt in fromEpochMs..toEpochMs }

        override suspend fun pageHistory(limit: Int, offset: Int): List<CardioSession> =
            sessions.values.sortedByDescending { it.endedAt }.drop(offset).take(limit)
    }
}
