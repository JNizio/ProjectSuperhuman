package com.projectsuperhuman.next

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

internal object SleepHealthConnect {
    val permission: String = HealthPermission.getReadPermission(SleepSessionRecord::class)

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun hasPermission(context: Context): Boolean {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) return false
        val client = HealthConnectClient.getOrCreate(context)
        return permission in client.permissionController.getGrantedPermissions()
    }

    suspend fun sync(context: Context): SleepSyncResult {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) {
            return SleepSyncResult(false, 0, "Health Connect isn’t available on this device yet")
        }
        val client = HealthConnectClient.getOrCreate(context)
        if (permission !in client.permissionController.getGrantedPermissions()) {
            return SleepSyncResult(false, 0, "Sleep access is needed before syncing")
        }

        return runCatching {
            val end = Instant.now()
            val start = end.minus(Duration.ofDays(30))
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = false,
                    pageSize = 200
                )
            )
            val sessions = response.records.sortedByDescending { it.endTime }
            if (sessions.isEmpty()) return@runCatching SleepSyncResult(true, 0, "No recent sleep data found")

            val values = buildList {
                sessions.forEach { session ->
                    val breakdown = stageBreakdown(session)
                    val totalMinutes = Duration.between(session.startTime, session.endTime).toMinutes().coerceAtLeast(0).toInt()
                    val effectiveSleep = breakdown.sleepMinutes.takeIf { it > 0 }
                        ?: (totalMinutes - breakdown.awakeMinutes).coerceAtLeast(0)
                    val score = calculateScore(totalMinutes, breakdown.awakeMinutes, breakdown.deepMinutes, breakdown.remMinutes, effectiveSleep)
                    val recordId = session.metadata.id
                    val common = mapOf(
                        "sourceRecordId" to recordId,
                        "sessionStart" to session.startTime.toEpochMilli().toString(),
                        "sessionEnd" to session.endTime.toEpochMilli().toString()
                    )
                    val timestamp = session.endTime.toEpochMilli()
                    add(HealthValue(HealthDomain.SLEEP, "sleep_score", score.toDouble(), "score", timestamp, "health-connect", common))
                    add(HealthValue(HealthDomain.SLEEP, "sleep_total_minutes", totalMinutes.toDouble(), "min", timestamp, "health-connect", common))
                    add(HealthValue(HealthDomain.SLEEP, "sleep_awake_minutes", breakdown.awakeMinutes.toDouble(), "min", timestamp, "health-connect", common))
                    add(HealthValue(HealthDomain.SLEEP, "sleep_light_minutes", breakdown.lightMinutes.toDouble(), "min", timestamp, "health-connect", common))
                    add(HealthValue(HealthDomain.SLEEP, "sleep_deep_minutes", breakdown.deepMinutes.toDouble(), "min", timestamp, "health-connect", common))
                    add(HealthValue(HealthDomain.SLEEP, "sleep_rem_minutes", breakdown.remMinutes.toDouble(), "min", timestamp, "health-connect", common))
                    add(HealthValue(HealthDomain.SLEEP, "sleep_start_epoch_ms", session.startTime.toEpochMilli().toDouble(), "ms", timestamp, "health-connect", common))
                    add(HealthValue(HealthDomain.SLEEP, "sleep_end_epoch_ms", timestamp.toDouble(), "ms", timestamp, "health-connect", common))
                }
                add(
                    HealthValue(
                        HealthDomain.SLEEP,
                        "sleep_sessions_imported",
                        sessions.size.toDouble(),
                        "count",
                        System.currentTimeMillis(),
                        "health-connect",
                        mapOf("sourceRecordId" to "sync-summary")
                    )
                )
            }
            NativeDataHub.saveValues(values)
            SleepSyncResult(true, sessions.size, "Sleep is up to date")
        }.getOrElse {
            SleepSyncResult(false, 0, "Couldn’t sync sleep right now")
        }
    }

    private data class StageBreakdown(
        val awakeMinutes: Int,
        val lightMinutes: Int,
        val deepMinutes: Int,
        val remMinutes: Int,
        val sleepMinutes: Int
    )

    private fun stageBreakdown(session: SleepSessionRecord): StageBreakdown {
        var awake = 0L
        var light = 0L
        var deep = 0L
        var rem = 0L
        var generic = 0L
        session.stages.forEach { stage ->
            val minutes = Duration.between(stage.startTime, stage.endTime).toMinutes().coerceAtLeast(0)
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += minutes
                SleepSessionRecord.STAGE_TYPE_LIGHT -> light += minutes
                SleepSessionRecord.STAGE_TYPE_DEEP -> deep += minutes
                SleepSessionRecord.STAGE_TYPE_REM -> rem += minutes
                SleepSessionRecord.STAGE_TYPE_SLEEPING -> generic += minutes
            }
        }
        return StageBreakdown(
            awake.toInt(),
            light.toInt(),
            deep.toInt(),
            rem.toInt(),
            (light + deep + rem + generic).toInt()
        )
    }

    private fun calculateScore(total: Int, awake: Int, deep: Int, rem: Int, effectiveSleep: Int): Int {
        if (total <= 0) return 0
        val durationHours = effectiveSleep / 60.0
        val durationScore = (100.0 - abs(durationHours - 8.0) * 18.0).coerceIn(0.0, 100.0)
        val efficiency = ((total - awake).toDouble() / total * 100.0).coerceIn(0.0, 100.0)
        val deepPct = if (effectiveSleep > 0) deep.toDouble() / effectiveSleep * 100.0 else 0.0
        val remPct = if (effectiveSleep > 0) rem.toDouble() / effectiveSleep * 100.0 else 0.0
        val deepScore = (100.0 - abs(deepPct - 18.0) * 5.0).coerceIn(0.0, 100.0)
        val remScore = (100.0 - abs(remPct - 22.0) * 4.0).coerceIn(0.0, 100.0)
        return (durationScore * .40 + efficiency * .30 + deepScore * .15 + remScore * .15).roundToInt().coerceIn(0, 100)
    }
}

internal data class SleepSyncResult(val success: Boolean, val sessions: Int, val message: String)
