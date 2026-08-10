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

    private val maxInterruptionGap: Duration = Duration.ofHours(3)
    private val maxNightSpan: Duration = Duration.ofHours(16)

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
                        ascendingOrder = true,
                        pageSize = 200
                    )
                )
                val sessions = response.records.sortedBy { it.startTime }
                if (sessions.isEmpty()) {
                    return@withTimeout SleepSyncResult(true, 0, "Connected — no sleep records are being shared yet")
                }

                val nights = groupIntoNights(sessions)
                val values = buildList {
                    nights.forEach { night ->
                        val summary = summariseNight(night)
                        val analysis = SleepAnalysisEngine.analyse(
                            summary.asleepMinutes,
                            summary.awakeMinutes,
                            summary.deepMinutes,
                            summary.remMinutes,
                            summary.lightMinutes
                        )
                        val timestamp = summary.end.toEpochMilli()
                        val nightId = "night:${summary.start.toEpochMilli()}:${summary.end.toEpochMilli()}"
                        val baseMeta = mapOf(
                            "nightStart" to summary.start.toEpochMilli().toString(),
                            "nightEnd" to timestamp.toString(),
                            "sleepBlocks" to night.size.toString(),
                            "interruptions" to summary.interruptionCount.toString(),
                            "longestInterruptionMinutes" to summary.longestInterruptionMinutes.toString(),
                            "healthConnectRecordIds" to night.joinToString(",") { it.metadata.id }
                        )

                        fun addMetric(metric: String, value: Double, unit: String, extra: Map<String, String> = emptyMap()) {
                            add(
                                HealthValue(
                                    HealthDomain.SLEEP,
                                    metric,
                                    value,
                                    unit,
                                    timestamp,
                                    "health-connect-night",
                                    baseMeta + extra + ("sourceRecordId" to "$nightId:$metric")
                                )
                            )
                        }

                        addMetric("sleep_score", analysis.score.toDouble(), "score")
                        addMetric("sleep_total_minutes", summary.asleepMinutes.toDouble(), "min")
                        addMetric("sleep_awake_minutes", summary.awakeMinutes.toDouble(), "min")
                        addMetric("sleep_light_minutes", summary.lightMinutes.toDouble(), "min")
                        addMetric("sleep_deep_minutes", summary.deepMinutes.toDouble(), "min")
                        addMetric("sleep_rem_minutes", summary.remMinutes.toDouble(), "min")
                        addMetric("sleep_efficiency_pct", analysis.efficiencyPct.toDouble(), "%")
                        addMetric("sleep_duration_score", analysis.durationScore.toDouble(), "score")
                        addMetric("sleep_continuity_score", analysis.continuityScore.toDouble(), "score")
                        addMetric("sleep_stage_balance_score", analysis.stageBalanceScore.toDouble(), "score")
                        addMetric("sleep_start_epoch_ms", summary.start.toEpochMilli().toDouble(), "ms")
                        addMetric("sleep_end_epoch_ms", timestamp.toDouble(), "ms")
                        addMetric("sleep_interruption_count", summary.interruptionCount.toDouble(), "count")
                        addMetric("sleep_longest_interruption_minutes", summary.longestInterruptionMinutes.toDouble(), "min")
                        addMetric("sleep_block_count", night.size.toDouble(), "count")
                        addMetric(
                            "sleep_stage_timeline",
                            1.0,
                            "timeline",
                            mapOf("segments" to summary.timeline)
                        )
                    }

                    add(
                        HealthValue(
                            HealthDomain.SLEEP,
                            "sleep_sessions_imported",
                            nights.size.toDouble(),
                            "count",
                            System.currentTimeMillis(),
                            "health-connect",
                            mapOf("sourceRecordId" to "sync-summary:sleep_sessions_imported")
                        )
                    )
                    add(
                        HealthValue(
                            HealthDomain.SLEEP,
                            "sleep_blocks_imported",
                            sessions.size.toDouble(),
                            "count",
                            System.currentTimeMillis(),
                            "health-connect",
                            mapOf("sourceRecordId" to "sync-summary:sleep_blocks_imported")
                        )
                    )
                }

                NativeDataHub.saveValues(values)
                val nightLabel = if (nights.size == 1) "1 recent night" else "${nights.size} recent nights"
                val blockLabel = if (sessions.size == 1) "1 sleep block" else "${sessions.size} sleep blocks"
                SleepSyncResult(true, nights.size, "$nightLabel synced · $blockLabel detected")
            }
        } catch (_: TimeoutCancellationException) {
            SleepSyncResult(false, 0, "Health Connect took too long to respond — try again")
        } catch (t: Throwable) {
            SleepSyncResult(false, 0, "Sync couldn’t finish: ${t.javaClass.simpleName}")
        }
    }

    /**
     * Health Connect may represent one interrupted night as multiple SleepSessionRecords.
     * Blocks separated by up to three hours are treated as one night, provided the full
     * episode remains within a realistic overnight window.
     */
    private fun groupIntoNights(sessions: List<SleepSessionRecord>): List<List<SleepSessionRecord>> {
        if (sessions.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<SleepSessionRecord>>()
        sessions.forEach { session ->
            val current = groups.lastOrNull()
            if (current == null) {
                groups += mutableListOf(session)
                return@forEach
            }

            val previous = current.last()
            val gap = Duration.between(previous.endTime, session.startTime)
            val candidateSpan = Duration.between(current.first().startTime, session.endTime)
            val belongsToCurrent = !gap.isNegative && gap <= maxInterruptionGap && candidateSpan <= maxNightSpan

            if (belongsToCurrent) current += session else groups += mutableListOf(session)
        }
        return groups
    }

    private data class Breakdown(val awake: Int, val light: Int, val deep: Int, val rem: Int) {
        val stagedSleep: Int get() = light + deep + rem
    }

    private data class NightSummary(
        val start: Instant,
        val end: Instant,
        val asleepMinutes: Int,
        val awakeMinutes: Int,
        val lightMinutes: Int,
        val deepMinutes: Int,
        val remMinutes: Int,
        val interruptionCount: Int,
        val longestInterruptionMinutes: Int,
        val timeline: String
    )

    private fun summariseNight(sessions: List<SleepSessionRecord>): NightSummary {
        val ordered = sessions.sortedBy { it.startTime }
        var awake = 0
        var light = 0
        var deep = 0
        var rem = 0
        var asleep = 0
        var interruptionCount = 0
        var longestInterruption = 0
        val timeline = mutableListOf<String>()

        ordered.forEachIndexed { index, session ->
            if (index > 0) {
                val previous = ordered[index - 1]
                val gapMinutes = Duration.between(previous.endTime, session.startTime).toMinutes().coerceAtLeast(0).toInt()
                if (gapMinutes > 0) {
                    awake += gapMinutes
                    if (gapMinutes >= 5) interruptionCount += 1
                    longestInterruption = maxOf(longestInterruption, gapMinutes)
                    timeline += "awake,${previous.endTime.toEpochMilli()},${session.startTime.toEpochMilli()}"
                }
            }

            val breakdown = stageBreakdown(session)
            val sessionMinutes = Duration.between(session.startTime, session.endTime).toMinutes().coerceAtLeast(0).toInt()
            val stagedSleep = breakdown.stagedSleep
            val inferredSleep = (sessionMinutes - breakdown.awake).coerceAtLeast(0)
            asleep += if (stagedSleep > 0) stagedSleep else inferredSleep
            awake += breakdown.awake
            light += if (stagedSleep > 0) breakdown.light else inferredSleep
            deep += breakdown.deep
            rem += breakdown.rem

            val encoded = encodeStages(session)
            if (encoded.isNotBlank()) timeline += encoded
            else timeline += "light,${session.startTime.toEpochMilli()},${session.endTime.toEpochMilli()}"
        }

        return NightSummary(
            start = ordered.first().startTime,
            end = ordered.last().endTime,
            asleepMinutes = asleep,
            awakeMinutes = awake,
            lightMinutes = light,
            deepMinutes = deep,
            remMinutes = rem,
            interruptionCount = interruptionCount,
            longestInterruptionMinutes = longestInterruption,
            timeline = timeline.filter { it.isNotBlank() }.joinToString(";")
        )
    }

    private fun stageBreakdown(session: SleepSessionRecord): Breakdown {
        var awake = 0L
        var light = 0L
        var deep = 0L
        var rem = 0L
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
