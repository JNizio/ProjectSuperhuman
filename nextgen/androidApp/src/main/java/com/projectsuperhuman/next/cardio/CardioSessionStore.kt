package com.projectsuperhuman.next

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cardioSessionDataStore by preferencesDataStore(name = "cardio_live_session")

internal data class CardioDraftLoadResult(
    val draft: CardioLiveDraft?,
    val restoredFromLegacy: Boolean = false,
    val migratedSchema: Boolean = false,
    val incompatibleSchemaVersion: Int? = null
)

internal data class CardioDraftRaw(
    val schemaVersion: Int,
    val sessionId: String?,
    val activity: String?,
    val workoutType: String?,
    val startedAtEpochMs: Long,
    val accumulatedActiveMs: Long,
    val accumulatedPausedMs: Long,
    val phase: String?,
    val phaseStartedEpochMs: Long,
    val phaseStartedElapsedRealtimeMs: Long,
    val pendingCompletionEpochMs: Long?,
    val legacyIsRunning: Boolean? = null
)

internal object CardioDraftSchema {
    fun decode(raw: CardioDraftRaw): CardioDraftLoadResult {
        if (raw.schemaVersion > CARDIO_LIVE_DRAFT_SCHEMA_VERSION) {
            return CardioDraftLoadResult(draft = null, incompatibleSchemaVersion = raw.schemaVersion)
        }
        if (raw.schemaVersion <= 0 || raw.startedAtEpochMs <= 0L) {
            return CardioDraftLoadResult(draft = null)
        }

        val activity = CardioActivityType.fromStored(raw.activity)
        val workoutType = CardioWorkoutType.fromStored(raw.workoutType)
        val sessionId = raw.sessionId?.takeIf { it.isNotBlank() }
            ?: "cardio-legacy-" + raw.startedAtEpochMs + "-" + activity.name.lowercase()

        return when (raw.schemaVersion) {
            1 -> {
                val running = raw.legacyIsRunning == true
                CardioDraftLoadResult(
                    draft = CardioLiveDraft(
                        sessionId = sessionId,
                        activity = activity,
                        workoutType = workoutType,
                        startedAtEpochMs = raw.startedAtEpochMs,
                        accumulatedActiveMs = raw.accumulatedActiveMs.coerceAtLeast(0L),
                        accumulatedPausedMs = 0L,
                        phase = if (running) CardioLivePhase.RECORDING else CardioLivePhase.PAUSED,
                        phaseStartedEpochMs = raw.phaseStartedEpochMs.takeIf { it > 0L }
                            ?: raw.startedAtEpochMs,
                        phaseStartedElapsedRealtimeMs = 0L
                    ),
                    migratedSchema = true
                )
            }
            CARDIO_LIVE_DRAFT_SCHEMA_VERSION -> {
                val phase = runCatching { CardioLivePhase.valueOf(raw.phase.orEmpty()) }
                    .getOrDefault(CardioLivePhase.PAUSED)
                CardioDraftLoadResult(
                    draft = CardioLiveDraft(
                        schemaVersion = CARDIO_LIVE_DRAFT_SCHEMA_VERSION,
                        sessionId = sessionId,
                        activity = activity,
                        workoutType = workoutType,
                        startedAtEpochMs = raw.startedAtEpochMs,
                        accumulatedActiveMs = raw.accumulatedActiveMs.coerceAtLeast(0L),
                        accumulatedPausedMs = raw.accumulatedPausedMs.coerceAtLeast(0L),
                        phase = phase,
                        phaseStartedEpochMs = raw.phaseStartedEpochMs.takeIf { it > 0L }
                            ?: raw.startedAtEpochMs,
                        phaseStartedElapsedRealtimeMs = raw.phaseStartedElapsedRealtimeMs.coerceAtLeast(0L),
                        pendingCompletionEpochMs = raw.pendingCompletionEpochMs
                    )
                )
            }
            else -> CardioDraftLoadResult(draft = null)
        }
    }
}

internal interface CardioSessionStore {
    fun observe(): Flow<CardioLiveDraft?>
    suspend fun load(): CardioDraftLoadResult
    suspend fun migrateLegacyIfNeeded(): CardioDraftLoadResult
    suspend fun save(draft: CardioLiveDraft)
    suspend fun clear(expectedSessionId: String? = null): Boolean
}

internal class DataStoreCardioSessionStore(context: Context) : CardioSessionStore {
    private val appContext = context.applicationContext
    private val dataStore = appContext.cardioSessionDataStore

    override fun observe(): Flow<CardioLiveDraft?> =
        dataStore.data.map { decodePreferences(it).draft }.distinctUntilChanged()

    override suspend fun load(): CardioDraftLoadResult =
        decodePreferences(dataStore.data.first())

    override suspend fun migrateLegacyIfNeeded(): CardioDraftLoadResult {
        val existing = load()
        if (existing.draft != null || existing.incompatibleSchemaVersion != null) {
            if (existing.migratedSchema && existing.draft != null) save(existing.draft)
            return existing
        }

        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val startedAt = legacy.getLong("live_started_at", 0L)
        if (startedAt <= 0L) return existing

        val old = CardioDraftRaw(
            schemaVersion = 1,
            sessionId = null,
            activity = legacy.getString("live_activity", null),
            workoutType = legacy.getString("live_workout_type", null),
            startedAtEpochMs = startedAt,
            accumulatedActiveMs = legacy.getInt("live_accumulated_seconds", 0)
                .coerceAtLeast(0) * 1000L,
            accumulatedPausedMs = 0L,
            phase = null,
            phaseStartedEpochMs = legacy.getLong("live_running_since", 0L)
                .takeIf { it > 0L } ?: startedAt,
            phaseStartedElapsedRealtimeMs = 0L,
            pendingCompletionEpochMs = null,
            legacyIsRunning = legacy.getBoolean("live_is_running", false)
        )
        val migrated = CardioDraftSchema.decode(old)
        migrated.draft?.let { save(it) }

        legacy.edit()
            .remove("live_activity")
            .remove("live_started_at")
            .remove("live_accumulated_seconds")
            .remove("live_running_since")
            .remove("live_is_running")
            .remove("live_workout_type")
            .apply()

        return migrated.copy(restoredFromLegacy = migrated.draft != null)
    }

    override suspend fun save(draft: CardioLiveDraft) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.SCHEMA_VERSION] = CARDIO_LIVE_DRAFT_SCHEMA_VERSION
            prefs[Keys.SESSION_ID] = draft.sessionId
            prefs[Keys.ACTIVITY] = draft.activity.name
            prefs[Keys.WORKOUT_TYPE] = draft.workoutType.name
            prefs[Keys.STARTED_AT] = draft.startedAtEpochMs
            prefs[Keys.ACTIVE_MS] = draft.accumulatedActiveMs
            prefs[Keys.PAUSED_MS] = draft.accumulatedPausedMs
            prefs[Keys.PHASE] = draft.phase.name
            prefs[Keys.PHASE_STARTED_EPOCH] = draft.phaseStartedEpochMs
            prefs[Keys.PHASE_STARTED_ELAPSED] = draft.phaseStartedElapsedRealtimeMs
            draft.pendingCompletionEpochMs?.let { prefs[Keys.PENDING_COMPLETION] = it }
        }
    }

    override suspend fun clear(expectedSessionId: String?): Boolean {
        var cleared = false
        dataStore.edit { prefs ->
            val current = prefs[Keys.SESSION_ID]
            if (expectedSessionId == null || current == expectedSessionId) {
                prefs.clear()
                cleared = true
            }
        }
        return cleared
    }

    private fun decodePreferences(prefs: Preferences): CardioDraftLoadResult {
        val version = prefs[Keys.SCHEMA_VERSION] ?: return CardioDraftLoadResult(draft = null)
        return CardioDraftSchema.decode(
            CardioDraftRaw(
                schemaVersion = version,
                sessionId = prefs[Keys.SESSION_ID],
                activity = prefs[Keys.ACTIVITY],
                workoutType = prefs[Keys.WORKOUT_TYPE],
                startedAtEpochMs = prefs[Keys.STARTED_AT] ?: 0L,
                accumulatedActiveMs = prefs[Keys.ACTIVE_MS] ?: 0L,
                accumulatedPausedMs = prefs[Keys.PAUSED_MS] ?: 0L,
                phase = prefs[Keys.PHASE],
                phaseStartedEpochMs = prefs[Keys.PHASE_STARTED_EPOCH] ?: 0L,
                phaseStartedElapsedRealtimeMs = prefs[Keys.PHASE_STARTED_ELAPSED] ?: 0L,
                pendingCompletionEpochMs = prefs[Keys.PENDING_COMPLETION],
                legacyIsRunning = prefs[Keys.LEGACY_IS_RUNNING]
            )
        )
    }

    private object Keys {
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
        val SESSION_ID = stringPreferencesKey("session_id")
        val ACTIVITY = stringPreferencesKey("activity")
        val WORKOUT_TYPE = stringPreferencesKey("workout_type")
        val STARTED_AT = longPreferencesKey("started_at_epoch_ms")
        val ACTIVE_MS = longPreferencesKey("accumulated_active_ms")
        val PAUSED_MS = longPreferencesKey("accumulated_paused_ms")
        val PHASE = stringPreferencesKey("phase")
        val PHASE_STARTED_EPOCH = longPreferencesKey("phase_started_epoch_ms")
        val PHASE_STARTED_ELAPSED = longPreferencesKey("phase_started_elapsed_realtime_ms")
        val PENDING_COMPLETION = longPreferencesKey("pending_completion_epoch_ms")
        val LEGACY_IS_RUNNING = booleanPreferencesKey("is_running")
    }

    private companion object {
        const val LEGACY_PREFS = "superhuman_cardio"
    }
}
