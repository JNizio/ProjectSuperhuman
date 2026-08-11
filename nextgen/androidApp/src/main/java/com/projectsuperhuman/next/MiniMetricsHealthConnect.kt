package com.projectsuperhuman.next

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal data class MiniHealthSyncResult(
    val success: Boolean,
    val imported: Int,
    val message: String
)

/**
 * Galaxy Fit / Galaxy Watch mini-vitals importer.
 *
 * Samsung Health writes accessory data to Health Connect using Samsung Health's app origin.
 * Filtering to that origin keeps these dashboard values tied to Samsung Health rather than
 * silently mixing another step counter or fitness app into the same number.
 */
internal object MiniMetricsHealthConnect {
    const val SOURCE = "health-connect-samsung-vitals"
    private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
    private const val HISTORY_DAYS = 7L

    private val samsungOrigin = DataOrigin(SAMSUNG_HEALTH_PACKAGE)
    private val samsungFilter = setOf(samsungOrigin)

    val heartRatePermission: String = HealthPermission.getReadPermission(HeartRateRecord::class)
    val stepsPermission: String = HealthPermission.getReadPermission(StepsRecord::class)
    val oxygenPermission: String = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    val permissions: Set<String> = setOf(heartRatePermission, stepsPermission, oxygenPermission)

    fun permissionFor(metric: HomeMiniMetric): String? = when (metric) {
        HomeMiniMetric.HEART_RATE -> heartRatePermission
        HomeMiniMetric.STEPS -> stepsPermission
        HomeMiniMetric.BLOOD_OXYGEN -> oxygenPermission
        HomeMiniMetric.STRESS -> null
    }

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun grantedPermissions(context: Context): Set<String> {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) return emptySet()
        return runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
    }

    suspend fun hasAnyPermission(context: Context): Boolean =
        grantedPermissions(context).any(permissions::contains)

    suspend fun hasPermission(context: Context, metric: HomeMiniMetric): Boolean {
        val permission = permissionFor(metric) ?: return false
        return permission in grantedPermissions(context)
    }

    suspend fun sync(context: Context): MiniHealthSyncResult {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) {
            return MiniHealthSyncResult(false, 0, "Health Connect isn’t available on this device")
        }

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val readable = permissions.intersect(granted)
        if (readable.isEmpty()) {
            return MiniHealthSyncResult(false, 0, "Connect a wearable metric to start syncing")
        }

        return try {
            withTimeout(15_000L) {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val now = Instant.now()
                val values = mutableListOf<HealthValue>()

                if (heartRatePermission in granted) {
                    importHeartRate(client, today, now, zone, values)
                }
                if (stepsPermission in granted) {
                    importSteps(client, today, now, zone, values)
                }
                if (oxygenPermission in granted) {
                    importOxygen(client, today, now, zone, values)
                }

                val changed = replaceSummaryRows(values)
                val label = when (readable.size) {
                    1 -> "1 Samsung Health metric synced"
                    else -> "${readable.size} Samsung Health metrics synced"
                }
                MiniHealthSyncResult(true, changed, label)
            }
        } catch (_: TimeoutCancellationException) {
            MiniHealthSyncResult(false, 0, "Health Connect took too long to respond — try again")
        } catch (t: Throwable) {
            MiniHealthSyncResult(false, 0, "Wearable sync couldn’t finish: ${t.javaClass.simpleName}")
        }
    }

    /**
     * Lightweight foreground refresh for the mini dashboard.
     *
     * Health Connect is not a streaming transport from Samsung Health, so this checks only the
     * current day/latest reading instead of re-reading the full seven-day history every poll.
     * New latest HR/SpO2 samples are deduplicated by Health Connect record id; today's aggregate
     * rows are replaced in place so step totals stay current without stacking duplicates.
     */
    suspend fun syncCurrent(context: Context, metric: HomeMiniMetric? = null): MiniHealthSyncResult {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) {
            return MiniHealthSyncResult(false, 0, "Health Connect isn’t available on this device")
        }
        if (metric == HomeMiniMetric.STRESS) {
            return MiniHealthSyncResult(false, 0, "Samsung Stress is not shared through Health Connect")
        }

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val requestedPermissions = when (metric) {
            HomeMiniMetric.HEART_RATE -> setOf(heartRatePermission)
            HomeMiniMetric.STEPS -> setOf(stepsPermission)
            HomeMiniMetric.BLOOD_OXYGEN -> setOf(oxygenPermission)
            HomeMiniMetric.STRESS -> emptySet()
            null -> permissions
        }
        val readable = requestedPermissions.intersect(granted)
        if (readable.isEmpty()) {
            return MiniHealthSyncResult(false, 0, "No readable Samsung Health mini metrics")
        }

        return try {
            withTimeout(8_000L) {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val now = Instant.now()
                val values = mutableListOf<HealthValue>()

                if (heartRatePermission in readable) importHeartRateCurrent(client, today, now, zone, values)
                if (stepsPermission in readable) importStepsCurrent(client, today, now, zone, values)
                if (oxygenPermission in readable) importOxygenCurrent(client, today, now, zone, values)

                val changed = replaceSummaryRows(values)
                MiniHealthSyncResult(true, changed, "Foreground wearable metrics refreshed")
            }
        } catch (_: TimeoutCancellationException) {
            MiniHealthSyncResult(false, 0, "Foreground wearable refresh timed out")
        } catch (t: Throwable) {
            MiniHealthSyncResult(false, 0, "Foreground refresh couldn’t finish: ${t.javaClass.simpleName}")
        }
    }

    private suspend fun importHeartRateCurrent(
        client: HealthConnectClient,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        output: MutableList<HealthValue>
    ) {
        val start = today.atStartOfDay(zone).toInstant()
        if (!now.isAfter(start)) return

        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    HeartRateRecord.BPM_AVG,
                    HeartRateRecord.BPM_MIN,
                    HeartRateRecord.BPM_MAX,
                    HeartRateRecord.MEASUREMENTS_COUNT
                ),
                timeRangeFilter = TimeRangeFilter.between(start, now),
                dataOriginFilter = samsungFilter
            )
        )
        val timestamp = now.toEpochMilli()
        val count = result[HeartRateRecord.MEASUREMENTS_COUNT]
        val meta = summaryMeta(today, "heart-rate") + mapOf("measurementCount" to (count ?: 0L).toString())
        result[HeartRateRecord.BPM_AVG]?.let { output += row(HealthDomain.EXERCISE, "heart_rate_avg_bpm", it.toDouble(), "bpm", timestamp, meta) }
        result[HeartRateRecord.BPM_MIN]?.let { output += row(HealthDomain.EXERCISE, "heart_rate_min_bpm", it.toDouble(), "bpm", timestamp, meta) }
        result[HeartRateRecord.BPM_MAX]?.let { output += row(HealthDomain.EXERCISE, "heart_rate_max_bpm", it.toDouble(), "bpm", timestamp, meta) }

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now),
                dataOriginFilter = samsungFilter,
                ascendingOrder = false,
                pageSize = 250
            )
        )
        val latest = response.records.asSequence()
            .flatMap { record -> record.samples.asSequence().map { sample -> record to sample } }
            .maxByOrNull { it.second.time }
        latest?.let { (record, sample) ->
            output += row(
                HealthDomain.EXERCISE,
                "heart_rate_bpm",
                sample.beatsPerMinute.toDouble(),
                "bpm",
                sample.time.toEpochMilli(),
                mapOf(
                    "sourceRecordId" to "samsung-hc:heart:${record.metadata.id}:${sample.time.toEpochMilli()}",
                    "healthConnectRecordId" to record.metadata.id,
                    "sourcePackage" to record.metadata.dataOrigin.packageName,
                    "summaryType" to "latest-reading"
                )
            )
        }
    }

    private suspend fun importStepsCurrent(
        client: HealthConnectClient,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        output: MutableList<HealthValue>
    ) {
        val start = today.atStartOfDay(zone).toInstant()
        if (!now.isAfter(start)) return
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, now),
                dataOriginFilter = samsungFilter
            )
        )
        val count = result[StepsRecord.COUNT_TOTAL] ?: 0L
        output += row(
            HealthDomain.EXERCISE,
            "steps",
            count.toDouble(),
            "count",
            now.toEpochMilli(),
            summaryMeta(today, "steps") + mapOf("refreshMode" to "foreground")
        )
    }

    private suspend fun importOxygenCurrent(
        client: HealthConnectClient,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        output: MutableList<HealthValue>
    ) {
        val start = today.atStartOfDay(zone).toInstant()
        if (!now.isAfter(start)) return
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now),
                dataOriginFilter = samsungFilter,
                ascendingOrder = true,
                pageSize = 5000
            )
        )
        val records = response.records
        if (records.isEmpty()) return
        val values = records.map { it.percentage.value }
        val meta = summaryMeta(today, "oxygen") + mapOf("measurementCount" to records.size.toString())
        val timestamp = now.toEpochMilli()
        output += row(HealthDomain.BODY, "blood_oxygen_avg_percent", values.average(), "%", timestamp, meta)
        values.minOrNull()?.let { output += row(HealthDomain.BODY, "blood_oxygen_min_percent", it, "%", timestamp, meta) }
        values.maxOrNull()?.let { output += row(HealthDomain.BODY, "blood_oxygen_max_percent", it, "%", timestamp, meta) }

        records.maxByOrNull { it.time }?.let { record ->
            output += row(
                HealthDomain.BODY,
                "blood_oxygen_percent",
                record.percentage.value,
                "%",
                record.time.toEpochMilli(),
                mapOf(
                    "sourceRecordId" to "samsung-hc:oxygen:${record.metadata.id}",
                    "healthConnectRecordId" to record.metadata.id,
                    "sourcePackage" to record.metadata.dataOrigin.packageName,
                    "summaryType" to "latest-reading"
                )
            )
        }
    }

    private suspend fun importHeartRate(
        client: HealthConnectClient,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        output: MutableList<HealthValue>
    ) {
        repeat(HISTORY_DAYS.toInt()) { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            val end = if (date == today) now else date.plusDays(1).atStartOfDay(zone).toInstant()
            if (!end.isAfter(start)) return@repeat

            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MIN,
                        HeartRateRecord.BPM_MAX,
                        HeartRateRecord.MEASUREMENTS_COUNT
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = samsungFilter
                )
            )
            val avg = result[HeartRateRecord.BPM_AVG]
            val min = result[HeartRateRecord.BPM_MIN]
            val max = result[HeartRateRecord.BPM_MAX]
            val count = result[HeartRateRecord.MEASUREMENTS_COUNT]
            val timestamp = summaryTimestamp(date, today, end, now)
            val meta = summaryMeta(date, "heart-rate") + mapOf("measurementCount" to (count ?: 0L).toString())

            if (avg != null) output += row(HealthDomain.EXERCISE, "heart_rate_avg_bpm", avg.toDouble(), "bpm", timestamp, meta)
            if (min != null) output += row(HealthDomain.EXERCISE, "heart_rate_min_bpm", min.toDouble(), "bpm", timestamp, meta)
            if (max != null) output += row(HealthDomain.EXERCISE, "heart_rate_max_bpm", max.toDouble(), "bpm", timestamp, meta)
        }

        val start = today.minusDays(HISTORY_DAYS - 1L).atStartOfDay(zone).toInstant()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now),
                dataOriginFilter = samsungFilter,
                ascendingOrder = false,
                pageSize = 250
            )
        )
        val latest = response.records.asSequence()
            .flatMap { record -> record.samples.asSequence().map { sample -> record to sample } }
            .maxByOrNull { it.second.time }

        if (latest != null) {
            val (record, sample) = latest
            output += row(
                HealthDomain.EXERCISE,
                "heart_rate_bpm",
                sample.beatsPerMinute.toDouble(),
                "bpm",
                sample.time.toEpochMilli(),
                mapOf(
                    "sourceRecordId" to "samsung-hc:heart:${record.metadata.id}:${sample.time.toEpochMilli()}",
                    "healthConnectRecordId" to record.metadata.id,
                    "sourcePackage" to record.metadata.dataOrigin.packageName,
                    "summaryType" to "latest-reading"
                )
            )
        }
    }

    private suspend fun importSteps(
        client: HealthConnectClient,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        output: MutableList<HealthValue>
    ) {
        repeat(HISTORY_DAYS.toInt()) { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            val end = if (date == today) now else date.plusDays(1).atStartOfDay(zone).toInstant()
            if (!end.isAfter(start)) return@repeat

            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = samsungFilter
                )
            )
            val count = result[StepsRecord.COUNT_TOTAL] ?: 0L
            output += row(
                HealthDomain.EXERCISE,
                "steps",
                count.toDouble(),
                "count",
                summaryTimestamp(date, today, end, now),
                summaryMeta(date, "steps")
            )
        }
    }

    private suspend fun importOxygen(
        client: HealthConnectClient,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        output: MutableList<HealthValue>
    ) {
        var newest: OxygenSaturationRecord? = null

        repeat(HISTORY_DAYS.toInt()) { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            val end = if (date == today) now else date.plusDays(1).atStartOfDay(zone).toInstant()
            if (!end.isAfter(start)) return@repeat

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = samsungFilter,
                    ascendingOrder = true,
                    pageSize = 5000
                )
            )
            val records = response.records
            if (records.isEmpty()) return@repeat
            val values = records.map { it.percentage.value }
            val meta = summaryMeta(date, "oxygen") + mapOf("measurementCount" to records.size.toString())
            val timestamp = summaryTimestamp(date, today, end, now)
            output += row(HealthDomain.BODY, "blood_oxygen_avg_percent", values.average(), "%", timestamp, meta)
            output += row(HealthDomain.BODY, "blood_oxygen_min_percent", values.minOrNull() ?: return@repeat, "%", timestamp, meta)
            output += row(HealthDomain.BODY, "blood_oxygen_max_percent", values.maxOrNull() ?: return@repeat, "%", timestamp, meta)

            val dayNewest = records.maxByOrNull { it.time }
            if (dayNewest != null && (newest == null || dayNewest.time.isAfter(newest!!.time))) newest = dayNewest
        }

        newest?.let { record ->
            output += row(
                HealthDomain.BODY,
                "blood_oxygen_percent",
                record.percentage.value,
                "%",
                record.time.toEpochMilli(),
                mapOf(
                    "sourceRecordId" to "samsung-hc:oxygen:${record.metadata.id}",
                    "healthConnectRecordId" to record.metadata.id,
                    "sourcePackage" to record.metadata.dataOrigin.packageName,
                    "summaryType" to "latest-reading"
                )
            )
        }
    }

    /**
     * Daily aggregate rows are replaceable snapshots: steps can keep increasing through the day,
     * and Samsung may finish processing wearable data later. Replace our previous summary for the
     * same metric/date instead of accumulating duplicate daily totals in the Data Vault.
     */
    private suspend fun replaceSummaryRows(values: List<HealthValue>): Int {
        if (values.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val toSave = mutableListOf<HealthValue>()

        values.forEach { value ->
            val summaryDate = value.metadata["summaryDate"]
            if (summaryDate != null) {
                val date = runCatching { LocalDate.parse(summaryDate) }.getOrNull()
                if (date != null) {
                    val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                    val old = NativeDataHub.between(value.domain, value.metric, from, to)
                        .filter { it.source == SOURCE && it.metadata["summaryDate"] == summaryDate }
                    if (old.isNotEmpty()) NativeDataHub.deleteValues(old)
                }
                toSave += value
            } else {
                val id = value.metadata["sourceRecordId"]
                val exists = if (id.isNullOrBlank()) false else {
                    NativeDataHub.between(
                        value.domain,
                        value.metric,
                        (value.timestampEpochMs - 1L).coerceAtLeast(1L),
                        value.timestampEpochMs + 1L
                    ).any { it.source == SOURCE && it.metadata["sourceRecordId"] == id }
                }
                if (!exists) toSave += value
            }
        }

        if (toSave.isNotEmpty()) NativeDataHub.saveValues(toSave)
        return toSave.size
    }

    private fun summaryTimestamp(date: LocalDate, today: LocalDate, end: Instant, now: Instant): Long =
        if (date == today) now.toEpochMilli() else end.toEpochMilli() - 1L

    private fun summaryMeta(date: LocalDate, type: String): Map<String, String> = mapOf(
        "sourceRecordId" to "samsung-hc:$type:$date",
        "summaryDate" to date.toString(),
        "sourcePackage" to SAMSUNG_HEALTH_PACKAGE,
        "sourceDeviceFamily" to "Samsung Health connected wearable",
        "summaryType" to "daily"
    )

    private fun row(
        domain: HealthDomain,
        metric: String,
        value: Double,
        unit: String,
        timestamp: Long,
        metadata: Map<String, String>
    ): HealthValue = HealthValue(
        domain = domain,
        metric = metric,
        value = value,
        unit = unit,
        timestampEpochMs = timestamp,
        source = SOURCE,
        metadata = metadata
    )
}
