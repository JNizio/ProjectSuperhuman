from pathlib import Path

hc = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/MiniMetricsHealthConnect.kt')
text = hc.read_text()
marker = '    private suspend fun importHeartRate(\n'
if 'suspend fun syncCurrent(' not in text:
    insert = r'''    /**
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

'''
    if marker not in text:
        raise SystemExit('Health Connect insertion point not found')
    text = text.replace(marker, insert + marker, 1)
    hc.write_text(text)

ui = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/HomeMiniMetrics.kt')
text = ui.read_text()
if 'import kotlinx.coroutines.delay' not in text:
    text = text.replace('import kotlinx.coroutines.launch\n', 'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n', 1)

old_snapshot = '''private data class MiniMetricSnapshot(
    val heartRateBpm: Int? = null,
    val steps: Int? = null,
    val bloodOxygenPct: Int? = null,
    val stressScore: Int? = null
)'''
new_snapshot = '''private data class MiniMetricSnapshot(
    val heartRateBpm: Int? = null,
    val heartRateTimestampMs: Long? = null,
    val steps: Int? = null,
    val steps7dAverage: Int? = null,
    val bloodOxygenPct: Int? = null,
    val bloodOxygenTimestampMs: Long? = null,
    val stressScore: Int? = null
)'''
if old_snapshot in text:
    text = text.replace(old_snapshot, new_snapshot, 1)

old_home_effect = '''    LaunchedEffect(Unit) {
        metrics = loadMiniMetricSnapshot()
        if (MiniMetricsHealthConnect.hasAnyPermission(context)) {
            MiniMetricsHealthConnect.sync(context)
            metrics = loadMiniMetricSnapshot()
        }
    }'''
new_home_effect = '''    LaunchedEffect(Unit) {
        metrics = loadMiniMetricSnapshot()
        if (MiniMetricsHealthConnect.hasAnyPermission(context)) {
            MiniMetricsHealthConnect.sync(context)
            metrics = loadMiniMetricSnapshot()
            while (true) {
                delay(30_000L)
                MiniMetricsHealthConnect.syncCurrent(context)
                metrics = loadMiniMetricSnapshot()
            }
        }
    }'''
if old_home_effect in text:
    text = text.replace(old_home_effect, new_home_effect, 1)

text = text.replace(
    'status = if (metrics.heartRateBpm != null) "Samsung Health · latest" else "Tap to connect",',
    'status = if (metrics.heartRateBpm != null) freshnessLabel(metrics.heartRateTimestampMs) else "Tap to connect",',
    1
)
text = text.replace(
    'status = if (metrics.steps != null) "today" else "Tap to connect",',
    'status = if (metrics.steps != null) metrics.steps7dAverage?.let { "7d avg ${compactCount(it)}" } ?: "today · auto refresh" else "Tap to connect",',
    1
)
text = text.replace(
    'status = if (metrics.bloodOxygenPct != null) "Samsung Health · latest" else "Tap to connect",',
    'status = if (metrics.bloodOxygenPct != null) freshnessLabel(metrics.bloodOxygenTimestampMs) else "Tap to connect",',
    1
)

anchor = '''    LaunchedEffect(metric) {
        refresh()
        if (metric == HomeMiniMetric.STRESS) {
            status = "Samsung Health does not currently share its Stress score through Health Connect"
            return@LaunchedEffect
        }
        val available = MiniMetricsHealthConnect.availability(context) == HealthConnectClient.SDK_AVAILABLE
        connected = available && MiniMetricsHealthConnect.hasPermission(context, metric)
        status = when {
            !available -> "Health Connect isn’t available on this device"
            connected -> "Samsung Health connected through Health Connect"
            else -> "Connect this metric from Samsung Health"
        }
        if (connected) sync()
    }
'''
if 'LaunchedEffect(metric, connected)' not in text:
    if anchor not in text:
        raise SystemExit('Mini detail launch effect not found')
    addition = anchor + '''
    LaunchedEffect(metric, connected) {
        if (!connected || metric == HomeMiniMetric.STRESS) return@LaunchedEffect
        val intervalMs = when (metric) {
            HomeMiniMetric.HEART_RATE, HomeMiniMetric.STEPS -> 15_000L
            HomeMiniMetric.BLOOD_OXYGEN -> 30_000L
            HomeMiniMetric.STRESS -> 60_000L
        }
        while (true) {
            delay(intervalMs)
            MiniMetricsHealthConnect.syncCurrent(context, metric)
            refresh()
        }
    }
'''
    text = text.replace(anchor, addition, 1)

old_rows = '''    val heart = latestSamsung(NativeDataHub.between(HealthDomain.EXERCISE, "heart_rate_bpm", sevenDaysAgo, now))
    val steps = latestSamsung(NativeDataHub.between(HealthDomain.EXERCISE, "steps", todayStart, now))
    val oxygen = latestSamsung(NativeDataHub.between(HealthDomain.BODY, "blood_oxygen_percent", sevenDaysAgo, now))
'''
new_rows = '''    val heart = latestSamsung(NativeDataHub.between(HealthDomain.EXERCISE, "heart_rate_bpm", sevenDaysAgo, now))
    val stepRows = NativeDataHub.between(HealthDomain.EXERCISE, "steps", sevenDaysAgo, now)
        .filter { it.source == MiniMetricsHealthConnect.SOURCE && it.metadata["summaryDate"] != null }
    val steps = latestSamsung(stepRows.filter { it.timestampEpochMs >= todayStart })
    val latestStepPerDay = stepRows.groupBy { it.metadata["summaryDate"].orEmpty() }
        .values
        .mapNotNull { rows -> rows.maxByOrNull { it.timestampEpochMs } }
    val steps7dAverage = latestStepPerDay.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.roundToInt()
    val oxygen = latestSamsung(NativeDataHub.between(HealthDomain.BODY, "blood_oxygen_percent", sevenDaysAgo, now))
'''
if old_rows in text:
    text = text.replace(old_rows, new_rows, 1)

old_return = '''    return MiniMetricSnapshot(
        heartRateBpm = heart?.value?.roundToInt(),
        steps = steps?.value?.roundToInt(),
        bloodOxygenPct = oxygen?.value?.roundToInt(),
        stressScore = stress?.let { if (it <= 10.0) (it * 10.0).roundToInt() else it.roundToInt() }
    )'''
new_return = '''    return MiniMetricSnapshot(
        heartRateBpm = heart?.value?.roundToInt(),
        heartRateTimestampMs = heart?.timestampEpochMs,
        steps = steps?.value?.roundToInt(),
        steps7dAverage = steps7dAverage,
        bloodOxygenPct = oxygen?.value?.roundToInt(),
        bloodOxygenTimestampMs = oxygen?.timestampEpochMs,
        stressScore = stress?.let { if (it <= 10.0) (it * 10.0).roundToInt() else it.roundToInt() }
    )'''
if old_return in text:
    text = text.replace(old_return, new_return, 1)

helper_anchor = '''private fun compactCount(value: Int): String = when {
    value >= 100_000 -> "${value / 1000}k"
    value >= 10_000 -> "%.1fk".format(value / 1000.0)
    else -> "%,d".format(value)
}'''
if 'private fun freshnessLabel(' not in text:
    helper = '''private fun freshnessLabel(timestampMs: Long?): String {
    if (timestampMs == null) return "Samsung Health"
    val ageMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = ageMs / 60_000L
    return when {
        minutes <= 1L -> "just updated"
        minutes < 60L -> "${minutes}m ago"
        minutes < 24L * 60L -> "${minutes / 60L}h ago"
        else -> "saved history"
    }
}

''' + helper_anchor
    if helper_anchor not in text:
        raise SystemExit('compactCount helper not found')
    text = text.replace(helper_anchor, helper, 1)

ui.write_text(text)

gradle = Path('nextgen/androidApp/build.gradle.kts')
text = gradle.read_text()
text = text.replace('versionCode = 11217', 'versionCode = 11218', 1)
text = text.replace('versionName = "11.2.17"', 'versionName = "11.2.18"', 1)
gradle.write_text(text)
