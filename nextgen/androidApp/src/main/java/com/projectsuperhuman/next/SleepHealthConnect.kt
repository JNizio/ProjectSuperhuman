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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal object SleepHealthConnect {
    val permission: String = HealthPermission.getReadPermission(SleepSessionRecord::class)

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun hasPermission(context: Context): Boolean {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) return false
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            permission in client.permissionController.getGrantedPermissions()
        }.getOrDefault(false)
    }

    suspend fun sync(context: Context): SleepSyncResult {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) {
            return SleepSyncResult(false, 0, "Health Connect isn’t available on this device yet")
        }
        val client = HealthConnectClient.getOrCreate(context)
        if (permission !in client.permissionController.getGrantedPermissions()) {
            return SleepSyncResult(false, 0, "Sleep access needs to be enabled in Health Connect")
        }

        return try {
            withTimeout(12_000L) {
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
                if (sessions.isEmpty()) {
                    return@withTimeout SleepSyncResult(true, 0, "Connected — no sleep records are being shared yet")
                }

                val values = buildList {
                    sessions.forEach { session ->
                        val breakdown = stageBreakdown(session)
                        val total = Duration.between(session.startTime, session.endTime).toMinutes().coerceAtLeast(0).toInt()
                        val analysis = SleepAnalysisEngine.analyse(total, breakdown.awake, breakdown.deep, breakdown.rem, breakdown.light)
                        val timestamp = session.endTime.toEpochMilli()
                        val baseId = session.metadata.id
                        val baseMeta = mapOf(
                            "healthConnectRecordId" to baseId,
                            "sessionStart" to session.startTime.toEpochMilli().toString(),
                            "sessionEnd" to timestamp.toString()
                        )
                        fun addMetric(metric: String, value: Double, unit: String, extra: Map<String, String> = emptyMap()) {
                            add(HealthValue(
                                HealthDomain.SLEEP, metric, value, unit, timestamp, "health-connect",
                                baseMeta + extra + ("sourceRecordId" to "$baseId:$metric")
                            ))
                        }

                        addMetric("sleep_score", analysis.score.toDouble(), "score")
                        addMetric("sleep_total_minutes", total.toDouble(), "min")
                        addMetric("sleep_awake_minutes", breakdown.awake.toDouble(), "min")
                        addMetric("sleep_light_minutes", breakdown.light.toDouble(), "min")
                        addMetric("sleep_deep_minutes", breakdown.deep.toDouble(), "min")
                        addMetric("sleep_rem_minutes", breakdown.rem.toDouble(), "min")
                        addMetric("sleep_efficiency_pct", analysis.efficiencyPct.toDouble(), "%")
                        addMetric("sleep_duration_score", analysis.durationScore.toDouble(), "score")
                        addMetric("sleep_continuity_score", analysis.continuityScore.toDouble(), "score")
                        addMetric("sleep_stage_balance_score", analysis.stageBalanceScore.toDouble(), "score")
                        addMetric("sleep_start_epoch_ms", session.startTime.toEpochMilli().toDouble(), "ms")
                        addMetric("sleep_end_epoch_ms", timestamp.toDouble(), "ms")
                        addMetric(
                            "sleep_stage_timeline", 1.0, "timeline",
                            mapOf("segments" to encodeStages(session))
                        )
                    }
                    add(HealthValue(
                        HealthDomain.SLEEP,
                        "sleep_sessions_imported",
                        sessions.size.toDouble(),
                        "count",
                        System.currentTimeMillis(),
                        "health-connect",
                        mapOf("sourceRecordId" to "sync-summary:sleep_sessions_imported")
                    ))
                }
                NativeDataHub.saveValues(values)
                SleepSyncResult(true, sessions.size, if (sessions.size == 1) "1 recent night synced" else "${sessions.size} recent nights synced")
            }
        } catch (_: TimeoutCancellationException) {
            SleepSyncResult(false, 0, "Health Connect took too long to respond — try again")
        } catch (t: Throwable) {
            SleepSyncResult(false, 0, "Sync couldn’t finish: ${t.javaClass.simpleName}")
        }
    }

    private data class Breakdown(val awake: Int, val light: Int, val deep: Int, val rem: Int)

    private fun stageBreakdown(session: SleepSessionRecord): Breakdown {
        var awake = 0L; var light = 0L; var deep = 0L; var rem = 0L
        session.stages.forEach { stage ->
            val min = Duration.between(stage.startTime, stage.endTime).toMinutes().coerceAtLeast(0)
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += min
                SleepSessionRecord.STAGE_TYPE_LIGHT,
                SleepSessionRecord.STAGE_TYPE_SLEEPING -> light += min
                SleepSessionRecord.STAGE_TYPE_DEEP -> deep += min
                SleepSessionRecord.STAGE_TYPE_REM -> rem += min
            }
        }
        return Breakdown(awake.toInt(), light.toInt(), deep.toInt(), rem.toInt())
    }

    private fun encodeStages(session: SleepSessionRecord): String = session.stages.joinToString(";") { stage ->
        val type = when (stage.stage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "awake"
            SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
            SleepSessionRecord.STAGE_TYPE_REM -> "rem"
            else -> "light"
        }
        "$type,${stage.startTime.toEpochMilli()},${stage.endTime.toEpochMilli()}"
    }
}

internal data class SleepSyncResult(val success: Boolean, val sessions: Int, val message: String)
