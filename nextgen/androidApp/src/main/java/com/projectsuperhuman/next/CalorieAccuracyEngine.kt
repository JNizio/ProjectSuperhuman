package com.projectsuperhuman.next

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Repairs a common Health Connect calorie mismatch seen with Samsung Health.
 *
 * Samsung's own Daily activity screen can include basal/resting burn continuously from midnight,
 * while TotalCaloriesBurnedRecord intervals exposed through Health Connect can begin later or have
 * coverage gaps. Simply aggregating those total-calorie records therefore undercounts some days.
 *
 * This engine keeps Samsung Health's total-calorie records authoritative wherever they exist, then
 * fills uncovered intervals using Health Connect basal energy and, when available, Samsung active
 * calories. If Samsung does not expose ActiveCaloriesBurnedRecord, active burn can be conservatively
 * inferred only inside covered total-calorie intervals as total minus basal. Every correction is
 * stored with an audit trail; raw Samsung total is preserved separately.
 */
internal object CalorieAccuracyEngine {
    private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
    private const val HISTORY_DAYS = 7L
    private val samsungFilter = setOf(DataOrigin(SAMSUNG_HEALTH_PACKAGE))

    val basalCaloriesPermission: String = HealthPermission.getReadPermission(BasalMetabolicRateRecord::class)

    internal data class SyncResult(
        val success: Boolean,
        val changed: Int,
        val message: String
    )

    private data class Coverage(val start: Instant, val end: Instant)

    private data class Snapshot(
        val total: Double?,
        val rawTotal: Double?,
        val active: Double?,
        val basal: Double?,
        val coveragePct: Int,
        val method: String,
        val activeMethod: String
    )

    suspend fun sync(context: Context): SyncResult {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return SyncResult(false, 0, "Health Connect isn’t available")
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val caloriePermissions = setOf(
            MiniMetricsHealthConnect.totalCaloriesPermission,
            MiniMetricsHealthConnect.activeCaloriesPermission,
            basalCaloriesPermission
        )
        if (granted.intersect(caloriePermissions).isEmpty()) {
            return SyncResult(false, 0, "Calorie permissions are not enabled")
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = Instant.now()
        var changed = 0
        repeat(HISTORY_DAYS.toInt()) { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            val end = if (date == today) now else date.plusDays(1).atStartOfDay(zone).toInstant()
            if (end.isAfter(start)) changed += syncDay(client, granted, date, today, start, end, now, zone)
        }
        return SyncResult(true, changed, "Calorie burn calibrated against Samsung + Health Connect coverage")
    }

    suspend fun syncCurrent(context: Context): SyncResult {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return SyncResult(false, 0, "Health Connect isn’t available")
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant()
        val now = Instant.now()
        if (!now.isAfter(start)) return SyncResult(true, 0, "No calorie interval yet")
        val changed = syncDay(client, granted, today, today, start, now, now, zone)
        return SyncResult(true, changed, "Current calorie burn calibrated")
    }

    private suspend fun syncDay(
        client: HealthConnectClient,
        granted: Set<String>,
        date: LocalDate,
        today: LocalDate,
        start: Instant,
        end: Instant,
        now: Instant,
        zone: ZoneId
    ): Int {
        val snapshot = buildSnapshot(client, granted, start, end)
        if (snapshot.total == null && snapshot.active == null && snapshot.basal == null) return 0

        val timestamp = if (date == today) now.toEpochMilli() else end.toEpochMilli() - 1L
        val dayToken = date.toString()
        val common = mapOf(
            "summaryDate" to dayToken,
            "sourcePackage" to SAMSUNG_HEALTH_PACKAGE,
            "sourceDeviceFamily" to "Samsung Health connected wearable",
            "summaryType" to "daily",
            "calorieAccuracyEngine" to "coverage-repair-v1",
            "calorieMethod" to snapshot.method,
            "activeMethod" to snapshot.activeMethod,
            "totalRecordCoveragePct" to snapshot.coveragePct.toString(),
            "rawSamsungTotalKcal" to (snapshot.rawTotal?.let(::oneDecimal) ?: "unknown"),
            "basalHealthConnectKcal" to (snapshot.basal?.let(::oneDecimal) ?: "unknown")
        )

        val rows = buildList {
            snapshot.total?.let {
                add(row("calories_burned_total_kcal", it, timestamp, common + ("sourceRecordId" to "samsung-hc:calorie-accuracy:total:$dayToken")))
            }
            snapshot.active?.let {
                add(row("calories_burned_active_kcal", it, timestamp, common + ("sourceRecordId" to "samsung-hc:calorie-accuracy:active:$dayToken")))
            }
            snapshot.basal?.let {
                add(row("calories_burned_basal_kcal", it, timestamp, common + ("sourceRecordId" to "samsung-hc:calorie-accuracy:basal:$dayToken")))
            }
            snapshot.rawTotal?.let {
                add(row("calories_burned_total_raw_kcal", it, timestamp, common + ("sourceRecordId" to "samsung-hc:calorie-accuracy:raw-total:$dayToken")))
            }
        }

        if (rows.isEmpty()) return 0
        val fromMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        rows.map { it.metric }.distinct().forEach { metric ->
            val old = NativeDataHub.between(HealthDomain.EXERCISE, metric, fromMs, toMs)
                .filter {
                    it.source == MiniMetricsHealthConnect.SOURCE &&
                        it.metadata["summaryDate"] == dayToken
                }
            if (old.isNotEmpty()) NativeDataHub.deleteValues(old)
        }
        NativeDataHub.saveValues(rows)
        return rows.size
    }

    private suspend fun buildSnapshot(
        client: HealthConnectClient,
        granted: Set<String>,
        start: Instant,
        end: Instant
    ): Snapshot {
        val totalAllowed = MiniMetricsHealthConnect.totalCaloriesPermission in granted
        val activeAllowed = MiniMetricsHealthConnect.activeCaloriesPermission in granted
        val basalAllowed = basalCaloriesPermission in granted

        val rawTotal = if (totalAllowed) aggregateTotal(client, start, end) else null
        val directActive = if (activeAllowed) aggregateActive(client, start, end) else null
        val fullBasal = if (basalAllowed) aggregateBasal(client, start, end) else null

        val coverage = if (totalAllowed) readTotalCoverage(client, start, end) else emptyList()
        val coveredMs = coverage.sumOf { Duration.between(it.start, it.end).toMillis().coerceAtLeast(0L) }
        val fullMs = Duration.between(start, end).toMillis().coerceAtLeast(1L)
        val coveragePct = ((coveredMs.toDouble() / fullMs.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        val coveredBasal = if (basalAllowed && coverage.isNotEmpty()) {
            coverage.mapNotNull { aggregateBasal(client, it.start, it.end) }.sum().takeIf { it > 0.0 }
        } else null
        val coveredActive = if (activeAllowed && coverage.isNotEmpty()) {
            coverage.mapNotNull { aggregateActive(client, it.start, it.end) }.sum().takeIf { it >= 0.0 }
        } else null

        val inferredActive = if (directActive == null && rawTotal != null && coveredBasal != null) {
            (rawTotal - coveredBasal).coerceAtLeast(0.0)
        } else null
        val active = directActive ?: inferredActive

        val repairedFromCoverage = if (rawTotal != null) {
            val missingBasal = if (fullBasal != null && coveredBasal != null) {
                (fullBasal - coveredBasal).coerceAtLeast(0.0)
            } else 0.0
            val missingActive = if (directActive != null && coveredActive != null) {
                (directActive - coveredActive).coerceAtLeast(0.0)
            } else 0.0
            rawTotal + missingBasal + missingActive
        } else null

        val componentTotal = if (fullBasal != null && active != null) fullBasal + active else null
        val candidates = listOfNotNull(rawTotal, repairedFromCoverage, componentTotal)
        val total = candidates.maxOrNull()

        val method = when {
            total == null -> "unavailable"
            rawTotal == null && componentTotal != null -> "basal-plus-active"
            rawTotal != null && total > rawTotal + 1.0 -> "samsung-total-plus-coverage-repair"
            else -> "samsung-total-direct"
        }
        val activeMethod = when {
            directActive != null -> "samsung-active-direct"
            inferredActive != null -> "inferred-total-minus-basal-covered"
            else -> "unavailable"
        }

        return Snapshot(
            total = total,
            rawTotal = rawTotal,
            active = active,
            basal = fullBasal,
            coveragePct = coveragePct,
            method = method,
            activeMethod = activeMethod
        )
    }

    private suspend fun aggregateTotal(client: HealthConnectClient, start: Instant, end: Instant): Double? =
        runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = samsungFilter
                )
            )[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
        }.getOrNull()

    private suspend fun aggregateActive(client: HealthConnectClient, start: Instant, end: Instant): Double? =
        runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = samsungFilter
                )
            )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        }.getOrNull()

    private suspend fun aggregateBasal(client: HealthConnectClient, start: Instant, end: Instant): Double? =
        runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories
        }.getOrNull()

    private suspend fun readTotalCoverage(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<Coverage> = runCatching {
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                dataOriginFilter = samsungFilter,
                ascendingOrder = true,
                pageSize = 5000
            )
        ).records

        val intervals = records.mapNotNull { record ->
            val s = if (record.startTime.isBefore(start)) start else record.startTime
            val e = if (record.endTime.isAfter(end)) end else record.endTime
            if (e.isAfter(s)) Coverage(s, e) else null
        }.sortedBy { it.start }

        if (intervals.isEmpty()) return@runCatching emptyList()
        val merged = mutableListOf<Coverage>()
        var current = intervals.first()
        intervals.drop(1).forEach { next ->
            if (!next.start.isAfter(current.end)) {
                current = Coverage(current.start, if (next.end.isAfter(current.end)) next.end else current.end)
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        merged
    }.getOrDefault(emptyList())

    private fun row(metric: String, value: Double, timestamp: Long, metadata: Map<String, String>): HealthValue =
        HealthValue(
            domain = HealthDomain.EXERCISE,
            metric = metric,
            value = value,
            unit = "kcal",
            timestampEpochMs = timestamp,
            source = MiniMetricsHealthConnect.SOURCE,
            metadata = metadata
        )

    private fun oneDecimal(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()
}
