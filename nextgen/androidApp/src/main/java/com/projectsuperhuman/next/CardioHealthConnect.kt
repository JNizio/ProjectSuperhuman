package com.projectsuperhuman.next

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

internal data class CardioHealthConnectActivityMapping(
    val activity: CardioActivityType,
    val recognised: Boolean,
    val originalExerciseType: Int
)

internal enum class CardioExternalDedupeDecision {
    IMPORT,
    UPDATE,
    DEDUPLICATE
}

internal data class CardioWindowedValue<T>(
    val timestampEpochMs: Long,
    val value: T
)

internal object CardioHealthConnectRules {
    fun mapActivity(exerciseType: Int): CardioHealthConnectActivityMapping {
        val mapped = when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> CardioActivityType.WALKING
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> CardioActivityType.RUNNING
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> CardioActivityType.TREADMILL
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> CardioActivityType.CYCLING
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> CardioActivityType.STATIONARY_BIKE
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> CardioActivityType.ROWING
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> CardioActivityType.ELLIPTICAL
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> CardioActivityType.STAIR_CLIMBER
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> CardioActivityType.SWIMMING
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> CardioActivityType.HIKING
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> CardioActivityType.HIIT
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> CardioActivityType.GENERAL_CARDIO
            else -> CardioActivityType.GENERAL_CARDIO
        }
        val recognised = exerciseType in setOf(
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        )
        return CardioHealthConnectActivityMapping(mapped, recognised, exerciseType)
    }

    fun isExplicitlyNonCardio(exerciseType: Int): Boolean = exerciseType in setOf(
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING
    )

    fun sourceRecordId(sourcePackage: String, healthConnectRecordId: String): String =
        "hc-cardio:$sourcePackage:$healthConnectRecordId"

    fun dedupeDecision(
        existingLastModifiedEpochMs: Long?,
        candidateLastModifiedEpochMs: Long
    ): CardioExternalDedupeDecision = when {
        existingLastModifiedEpochMs == null -> CardioExternalDedupeDecision.IMPORT
        existingLastModifiedEpochMs == candidateLastModifiedEpochMs -> CardioExternalDedupeDecision.DEDUPLICATE
        else -> CardioExternalDedupeDecision.UPDATE
    }

    fun <T> valuesWithinWindow(
        values: List<CardioWindowedValue<T>>,
        startEpochMs: Long,
        endEpochMs: Long
    ): List<CardioWindowedValue<T>> =
        values.filter { it.timestampEpochMs in startEpochMs..endEpochMs }
}

internal data class CardioHealthConnectSyncResult(
    val success: Boolean,
    val imported: Int,
    val updated: Int,
    val deduplicated: Int,
    val rejected: Int,
    val providers: Set<String>,
    val lastSyncEpochMs: Long,
    val message: String
)

private data class CardioImportedMetrics(
    val heartRate: CardioHeartRateSummary = CardioHeartRateSummary(),
    val distanceKm: Double? = null,
    val averageSpeedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val elevationGainM: Double? = null,
    val cadence: Int? = null,
    val caloriesKcal: Double? = null
)

/**
 * Workout importer intentionally reads metrics only inside each ExerciseSessionRecord time window
 * and, where possible, from the same Health Connect data origin as the workout. It never attaches
 * day-wide summaries to a session.
 */
internal object CardioHealthConnect {
    const val SOURCE = "health-connect-cardio"
    private const val HISTORY_DAYS = 30L

    val exercisePermission: String = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val heartRatePermission: String = HealthPermission.getReadPermission(HeartRateRecord::class)
    val distancePermission: String = HealthPermission.getReadPermission(DistanceRecord::class)
    val speedPermission: String = HealthPermission.getReadPermission(SpeedRecord::class)
    val elevationPermission: String = HealthPermission.getReadPermission(ElevationGainedRecord::class)
    val cadencePermission: String = HealthPermission.getReadPermission(CyclingPedalingCadenceRecord::class)
    val stepsCadencePermission: String = HealthPermission.getReadPermission(StepsCadenceRecord::class)
    val caloriesPermission: String = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)

    val permissions: Set<String> = setOf(
        exercisePermission,
        heartRatePermission,
        distancePermission,
        speedPermission,
        elevationPermission,
        cadencePermission,
        stepsCadencePermission,
        caloriesPermission
    )

    private val _syncState = MutableStateFlow<CardioHealthConnectSyncResult?>(null)
    val syncState: StateFlow<CardioHealthConnectSyncResult?> = _syncState.asStateFlow()

    suspend fun hasExercisePermission(context: Context): Boolean {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return false
        return exercisePermission in HealthConnectClient.getOrCreate(context)
            .permissionController.getGrantedPermissions()
    }

    suspend fun sync(context: Context): CardioHealthConnectSyncResult {
        val nowMs = System.currentTimeMillis()
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return result(false, 0, 0, 0, 0, emptySet(), nowMs, "Health Connect isn’t available")
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (exercisePermission !in granted) {
            return result(false, 0, 0, 0, 0, emptySet(), nowMs, "Exercise access is required to import cardio workouts")
        }

        return try {
            withTimeout(30_000L) {
                val end = Instant.now()
                val start = end.minus(Duration.ofDays(HISTORY_DAYS))
                val sessions = readExerciseSessions(client, start, end)
                val existing = NativeDataHub.between(
                    HealthDomain.EXERCISE,
                    "cardio_session",
                    start.toEpochMilli(),
                    end.toEpochMilli() + 1L
                ).associateBy { it.metadata["sourceRecordId"].orEmpty() }

                var imported = 0
                var updated = 0
                var deduplicated = 0
                var rejected = 0
                val providers = linkedSetOf<String>()

                sessions.forEach { session ->
                    if (CardioHealthConnectRules.isExplicitlyNonCardio(session.exerciseType)) {
                        rejected += 1
                        return@forEach
                    }
                    val packageName = session.metadata.dataOrigin.packageName.ifBlank { "unknown-origin" }
                    providers += packageName
                    val externalId = session.metadata.id
                    if (externalId.isBlank()) {
                        rejected += 1
                        return@forEach
                    }
                    val sourceRecordId = CardioHealthConnectRules.sourceRecordId(packageName, externalId)
                    val modifiedMs = session.metadata.lastModifiedTime.toEpochMilli()
                    val old = existing[sourceRecordId]
                    val oldModified = old?.metadata?.get("healthConnectLastModifiedEpochMs")?.toLongOrNull()
                    when (CardioHealthConnectRules.dedupeDecision(
                        if (old == null) null else oldModified ?: Long.MIN_VALUE,
                        modifiedMs
                    )) {
                        CardioExternalDedupeDecision.DEDUPLICATE -> {
                            deduplicated += 1
                            return@forEach
                        }
                        CardioExternalDedupeDecision.UPDATE -> {
                            old?.let { NativeDataHub.deleteValue(it) }
                            updated += 1
                        }
                        CardioExternalDedupeDecision.IMPORT -> imported += 1
                    }

                    val mapping = CardioHealthConnectRules.mapActivity(session.exerciseType)
                    val metrics = readWorkoutMetrics(client, session, granted, mapping.activity)
                    val value = toHealthValue(session, mapping, metrics, packageName, sourceRecordId, modifiedMs)
                    NativeDataHub.saveValues(listOf(value))
                }

                result(
                    true,
                    imported,
                    updated,
                    deduplicated,
                    rejected,
                    providers,
                    System.currentTimeMillis(),
                    buildString {
                        append(imported + updated)
                        append(" cardio workout")
                        if (imported + updated != 1) append("s")
                        append(" synced")
                        if (deduplicated > 0) append(" · $deduplicated unchanged")
                        if (rejected > 0) append(" · $rejected non-cardio/rejected")
                    }
                )
            }
        } catch (_: TimeoutCancellationException) {
            result(false, 0, 0, 0, 0, emptySet(), System.currentTimeMillis(), "Cardio workout sync timed out")
        } catch (t: Throwable) {
            result(
                false,
                0,
                0,
                0,
                0,
                emptySet(),
                System.currentTimeMillis(),
                "Cardio workout sync failed: ${t.javaClass.simpleName}"
            )
        }
    }

    private suspend fun readExerciseSessions(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<ExerciseSessionRecord> {
        val output = mutableListOf<ExerciseSessionRecord>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                    pageSize = 500,
                    pageToken = pageToken
                )
            )
            output += response.records
            pageToken = response.pageToken
            pages += 1
        } while (pageToken != null && pages < 20)
        return output.distinctBy { it.metadata.dataOrigin.packageName to it.metadata.id }
    }

    private suspend fun readWorkoutMetrics(
        client: HealthConnectClient,
        session: ExerciseSessionRecord,
        granted: Set<String>,
        activity: CardioActivityType
    ): CardioImportedMetrics {
        val start = session.startTime
        val end = session.endTime
        val originPackage = session.metadata.dataOrigin.packageName
        val originFilter = if (originPackage.isBlank()) emptySet() else setOf(DataOrigin(originPackage))

        val hr = if (heartRatePermission in granted) {
            readHeartRate(client, start, end, originFilter)
        } else CardioHeartRateSummary()

        val distanceKm = if (distancePermission in granted) {
            runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        dataOriginFilter = originFilter
                    )
                )[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.div(1000.0)
            }.getOrNull()
        } else null

        val speed = if (speedPermission in granted) {
            runCatching {
                val aggregate = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(SpeedRecord.SPEED_AVG, SpeedRecord.SPEED_MAX),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        dataOriginFilter = originFilter
                    )
                )
                val avg = aggregate[SpeedRecord.SPEED_AVG]?.inMetersPerSecond?.times(3.6)
                val max = aggregate[SpeedRecord.SPEED_MAX]?.inMetersPerSecond?.times(3.6)
                avg to max
            }.getOrNull()
        } else null

        val elevation = if (elevationPermission in granted) {
            runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ElevationGainedRecord.ELEVATION_GAINED_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        dataOriginFilter = originFilter
                    )
                )[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters
            }.getOrNull()
        } else null

        val cadence = when {
            activity in setOf(CardioActivityType.CYCLING, CardioActivityType.STATIONARY_BIKE) &&
                cadencePermission in granted -> runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(CyclingPedalingCadenceRecord.RPM_AVG),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        dataOriginFilter = originFilter
                    )
                )[CyclingPedalingCadenceRecord.RPM_AVG]?.roundToInt()
            }.getOrNull()
            activity in setOf(
                CardioActivityType.WALKING,
                CardioActivityType.RUNNING,
                CardioActivityType.TREADMILL,
                CardioActivityType.HIKING
            ) && stepsCadencePermission in granted -> runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsCadenceRecord.RATE_AVG),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        dataOriginFilter = originFilter
                    )
                )[StepsCadenceRecord.RATE_AVG]?.roundToInt()
            }.getOrNull()
            else -> null
        }

        val calories = if (caloriesPermission in granted) {
            runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        dataOriginFilter = originFilter
                    )
                )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            }.getOrNull()
        } else null

        return CardioImportedMetrics(
            heartRate = hr,
            distanceKm = distanceKm?.takeIf { it > 0.0 },
            averageSpeedKmh = speed?.first?.takeIf { it > 0.0 },
            maxSpeedKmh = speed?.second?.takeIf { it > 0.0 },
            elevationGainM = elevation?.takeIf { it >= 0.0 },
            cadence = cadence?.takeIf { it > 0 },
            caloriesKcal = calories?.takeIf { it >= 0.0 }
        )
    }

    private suspend fun readHeartRate(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        originFilter: Set<DataOrigin>
    ): CardioHeartRateSummary {
        val samples = mutableListOf<CardioHeartRateSample>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = originFilter,
                    ascendingOrder = true,
                    pageSize = 5000,
                    pageToken = pageToken
                )
            )
            response.records.forEach { record ->
                val recordPackage = record.metadata.dataOrigin.packageName.ifBlank {
                    originFilter.firstOrNull()?.packageName ?: "unknown-origin"
                }
                val provenance = CardioSensorProvenance(
                    providerType = CardioSensorProviderType.HEALTH_CONNECT,
                    sourceName = "health-connect-heart-rate",
                    transport = CardioSensorTransport.IMPORTED_HEALTH_CONNECT,
                    sourcePackage = recordPackage,
                    externalRecordId = record.metadata.id
                )
                record.samples.forEach { sample ->
                    val timestamp = sample.time.toEpochMilli()
                    val bpm = sample.beatsPerMinute.toInt()
                    if (
                        timestamp in start.toEpochMilli()..end.toEpochMilli() &&
                        bpm in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM
                    ) {
                        samples += CardioHeartRateSample(timestamp, bpm, provenance)
                    }
                }
            }
            pageToken = response.pageToken
            pages += 1
        } while (pageToken != null && pages < 12)

        return CardioHeartRateProcessor.summarise(
            samples,
            listOf(CardioActiveWindow(start.toEpochMilli(), end.toEpochMilli()))
        )
    }

    private fun toHealthValue(
        session: ExerciseSessionRecord,
        mapping: CardioHealthConnectActivityMapping,
        metrics: CardioImportedMetrics,
        packageName: String,
        sourceRecordId: String,
        modifiedMs: Long
    ): HealthValue {
        val startedAt = session.startTime.toEpochMilli()
        val endedAt = session.endTime.toEpochMilli()
        val durationSeconds = Duration.between(session.startTime, session.endTime).seconds
            .coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val sessionProvenance = CardioSensorProvenance(
            providerType = CardioSensorProviderType.HEALTH_CONNECT,
            sourceName = SOURCE,
            transport = CardioSensorTransport.IMPORTED_HEALTH_CONNECT,
            sourcePackage = packageName,
            externalRecordId = session.metadata.id
        )
        val internalId = "hc-cardio-${CardioSensorIds.anonymous(sourceRecordId) ?: endedAt.toString()}"

        val metadata = buildMap {
            put("sessionId", internalId)
            put("sourceRecordId", sourceRecordId)
            put("activityType", mapping.activity.name)
            put("activityName", mapping.activity.displayName)
            put("startedAt", startedAt.toString())
            put("endedAt", endedAt.toString())
            put("durationSeconds", durationSeconds.toString())
            put("notes", session.notes.orEmpty())
            put("cardioSource", SOURCE)
            put("workoutType", CardioWorkoutType.FREE.name)
            put("healthConnectRecordId", session.metadata.id)
            put("healthConnectLastModifiedEpochMs", modifiedMs.toString())
            put("healthConnectClientRecordId", session.metadata.clientRecordId.orEmpty())
            put("healthConnectSourcePackage", packageName)
            put("healthConnectExerciseType", mapping.originalExerciseType.toString())
            put("healthConnectActivityRecognised", mapping.recognised.toString())
            putAll(sessionProvenance.toMetadata("workout"))
            putAll(metrics.heartRate.toMetadata())
            metrics.distanceKm?.let { put("distanceKm", it.toString()) }
            metrics.averageSpeedKmh?.let { put("avgSpeedKmh", it.toString()) }
            metrics.maxSpeedKmh?.let { put("maxSpeedKmh", it.toString()) }
            metrics.elevationGainM?.let { put("elevationGainM", it.toString()) }
            metrics.cadence?.let { put("cadence", it.toString()) }
            metrics.caloriesKcal?.let { put("caloriesKcal", it.toString()) }
        }

        return HealthValue(
            domain = HealthDomain.EXERCISE,
            metric = "cardio_session",
            value = durationSeconds / 60.0,
            unit = "min",
            timestampEpochMs = endedAt,
            source = SOURCE,
            metadata = metadata
        )
    }

    private fun result(
        success: Boolean,
        imported: Int,
        updated: Int,
        deduplicated: Int,
        rejected: Int,
        providers: Set<String>,
        lastSyncEpochMs: Long,
        message: String
    ): CardioHealthConnectSyncResult =
        CardioHealthConnectSyncResult(
            success = success,
            imported = imported,
            updated = updated,
            deduplicated = deduplicated,
            rejected = rejected,
            providers = providers,
            lastSyncEpochMs = lastSyncEpochMs,
            message = message
        ).also { _syncState.value = it }
}
