from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Health Connect importer: faster current sync, source freshness, active calories,
# background-read capability.
# -----------------------------------------------------------------------------
hc_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/MiniMetricsHealthConnect.kt')
hc = hc_path.read_text()

hc = replace_once(
    hc,
    'import androidx.health.connect.client.HealthConnectClient\n',
    'import androidx.health.connect.client.HealthConnectClient\nimport androidx.health.connect.client.HealthConnectFeatures\n',
    'health connect features import'
)
hc = replace_once(
    hc,
    'import androidx.health.connect.client.records.HeartRateRecord\n',
    'import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord\nimport androidx.health.connect.client.records.HeartRateRecord\n',
    'active calories import'
)

hc = replace_once(
    hc,
    '''    val oxygenPermission: String = HealthPermission.getReadPermission(OxygenSaturationRecord::class)\n    val totalCaloriesPermission: String = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)\n    val permissions: Set<String> = setOf(heartRatePermission, stepsPermission, oxygenPermission, totalCaloriesPermission)\n''',
    '''    val oxygenPermission: String = HealthPermission.getReadPermission(OxygenSaturationRecord::class)\n    val activeCaloriesPermission: String = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)\n    val totalCaloriesPermission: String = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)\n    val backgroundReadPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND\n    val permissions: Set<String> = setOf(\n        heartRatePermission,\n        stepsPermission,\n        oxygenPermission,\n        activeCaloriesPermission,\n        totalCaloriesPermission\n    )\n''',
    'permission declarations'
)

hc = replace_once(
    hc,
    '''    fun permissionFor(metric: HomeMiniMetric): String? = when (metric) {\n        HomeMiniMetric.HEART_RATE -> heartRatePermission\n        HomeMiniMetric.STEPS -> stepsPermission\n        HomeMiniMetric.BLOOD_OXYGEN -> oxygenPermission\n        HomeMiniMetric.CALORIES -> totalCaloriesPermission\n    }\n\n    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)\n''',
    '''    fun permissionFor(metric: HomeMiniMetric): String? = when (metric) {\n        HomeMiniMetric.HEART_RATE -> heartRatePermission\n        HomeMiniMetric.STEPS -> stepsPermission\n        HomeMiniMetric.BLOOD_OXYGEN -> oxygenPermission\n        HomeMiniMetric.CALORIES -> activeCaloriesPermission\n    }\n\n    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)\n\n    fun backgroundReadAvailable(context: Context): Boolean {\n        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) return false\n        return runCatching {\n            HealthConnectClient.getOrCreate(context).features.getFeatureStatus(\n                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND\n            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE\n        }.getOrDefault(false)\n    }\n\n    fun requestPermissionsFor(context: Context, metric: HomeMiniMetric): Set<String> {\n        val metricPermissions = when (metric) {\n            HomeMiniMetric.HEART_RATE -> setOf(heartRatePermission)\n            HomeMiniMetric.STEPS -> setOf(stepsPermission)\n            HomeMiniMetric.BLOOD_OXYGEN -> setOf(oxygenPermission)\n            HomeMiniMetric.CALORIES -> setOf(activeCaloriesPermission, totalCaloriesPermission)\n        }\n        return if (backgroundReadAvailable(context)) metricPermissions + backgroundReadPermission else metricPermissions\n    }\n''',
    'permission helpers'
)

hc = replace_once(
    hc,
    '''    suspend fun hasAnyPermission(context: Context): Boolean =\n        grantedPermissions(context).any(permissions::contains)\n\n    suspend fun hasPermission(context: Context, metric: HomeMiniMetric): Boolean {\n''',
    '''    suspend fun hasAnyPermission(context: Context): Boolean =\n        grantedPermissions(context).any(permissions::contains)\n\n    suspend fun hasBackgroundReadPermission(context: Context): Boolean =\n        backgroundReadPermission in grantedPermissions(context)\n\n    suspend fun hasPermission(context: Context, metric: HomeMiniMetric): Boolean {\n''',
    'background permission query'
)

hc = replace_once(
    hc,
    '''                if (totalCaloriesPermission in granted) {\n                    importCalories(client, today, now, zone, values)\n                }\n\n                val changed = replaceSummaryRows(values)\n                val label = when (readable.size) {\n                    1 -> "1 Samsung Health metric synced"\n                    else -> "${readable.size} Samsung Health metrics synced"\n                }\n                MiniHealthSyncResult(true, changed, label)\n''',
    '''                if (activeCaloriesPermission in granted) {\n                    importActiveCalories(client, today, now, zone, values)\n                }\n                if (totalCaloriesPermission in granted) {\n                    importTotalCalories(client, today, now, zone, values)\n                }\n\n                val changed = replaceSummaryRows(values)\n                MiniHealthSyncResult(true, changed, "Samsung Health wearable data synced")\n''',
    'full calories sync'
)

hc = replace_once(
    hc,
    '''            HomeMiniMetric.BLOOD_OXYGEN -> setOf(oxygenPermission)\n            HomeMiniMetric.CALORIES -> setOf(totalCaloriesPermission)\n            null -> permissions\n''',
    '''            HomeMiniMetric.BLOOD_OXYGEN -> setOf(oxygenPermission)\n            HomeMiniMetric.CALORIES -> setOf(activeCaloriesPermission, totalCaloriesPermission)\n            null -> permissions\n''',
    'current calories requested permissions'
)

hc = replace_once(
    hc,
    '''                if (oxygenPermission in readable) importOxygenCurrent(client, today, now, zone, values)\n                if (totalCaloriesPermission in readable) importCaloriesCurrent(client, today, now, zone, values)\n''',
    '''                if (oxygenPermission in readable) importOxygenCurrent(client, today, now, zone, values)\n                if (activeCaloriesPermission in readable) importActiveCaloriesCurrent(client, today, now, zone, values)\n                if (totalCaloriesPermission in readable) importTotalCaloriesCurrent(client, today, now, zone, values)\n''',
    'current calories calls'
)

# Add source freshness to today's steps so the UI distinguishes "we checked now" from
# "Samsung actually received new Fit3 data now".
hc = replace_once(
    hc,
    '''        val count = result[StepsRecord.COUNT_TOTAL] ?: 0L\n        output += row(\n            HealthDomain.EXERCISE,\n            "steps",\n            count.toDouble(),\n            "count",\n            now.toEpochMilli(),\n            summaryMeta(today, "steps") + mapOf("refreshMode" to "foreground")\n        )\n''',
    '''        val count = result[StepsRecord.COUNT_TOTAL] ?: 0L\n        val sourceUpdatedAt = latestStepSourceUpdate(client, start, now)\n        val freshness = sourceUpdatedAt?.let { mapOf("sourceLastModifiedMs" to it.toString()) } ?: emptyMap()\n        output += row(\n            HealthDomain.EXERCISE,\n            "steps",\n            count.toDouble(),\n            "count",\n            now.toEpochMilli(),\n            summaryMeta(today, "steps") + mapOf("refreshMode" to "foreground") + freshness\n        )\n''',
    'step freshness current'
)

# Rename the existing total-calorie current function and insert active calories before it.
hc = replace_once(hc, '    private suspend fun importCaloriesCurrent(\n', '    private suspend fun importTotalCaloriesCurrent(\n', 'rename total calories current')
active_current = '''    private suspend fun importActiveCaloriesCurrent(\n        client: HealthConnectClient,\n        today: LocalDate,\n        now: Instant,\n        zone: ZoneId,\n        output: MutableList<HealthValue>\n    ) {\n        val start = today.atStartOfDay(zone).toInstant()\n        if (!now.isAfter(start)) return\n        val result = client.aggregate(\n            AggregateRequest(\n                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),\n                timeRangeFilter = TimeRangeFilter.between(start, now),\n                dataOriginFilter = samsungFilter\n            )\n        )\n        result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { energy ->\n            output += row(\n                HealthDomain.EXERCISE,\n                "calories_burned_active_kcal",\n                energy.inKilocalories,\n                "kcal",\n                now.toEpochMilli(),\n                summaryMeta(today, "active-calories-burned") + mapOf("refreshMode" to "foreground")\n            )\n        }\n    }\n\n'''
hc = replace_once(
    hc,
    '    private suspend fun importTotalCaloriesCurrent(\n',
    active_current + '    private suspend fun importTotalCaloriesCurrent(\n',
    'insert active calories current'
)

# Full-history calories: active and total are stored separately.
hc = replace_once(hc, '    private suspend fun importCalories(\n', '    private suspend fun importTotalCalories(\n', 'rename total calories history')
active_history = '''    private suspend fun importActiveCalories(\n        client: HealthConnectClient,\n        today: LocalDate,\n        now: Instant,\n        zone: ZoneId,\n        output: MutableList<HealthValue>\n    ) {\n        repeat(HISTORY_DAYS.toInt()) { offset ->\n            val date = today.minusDays(offset.toLong())\n            val start = date.atStartOfDay(zone).toInstant()\n            val end = if (date == today) now else date.plusDays(1).atStartOfDay(zone).toInstant()\n            if (!end.isAfter(start)) return@repeat\n            val result = client.aggregate(\n                AggregateRequest(\n                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),\n                    timeRangeFilter = TimeRangeFilter.between(start, end),\n                    dataOriginFilter = samsungFilter\n                )\n            )\n            result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { energy ->\n                output += row(\n                    HealthDomain.EXERCISE,\n                    "calories_burned_active_kcal",\n                    energy.inKilocalories,\n                    "kcal",\n                    summaryTimestamp(date, today, end, now),\n                    summaryMeta(date, "active-calories-burned")\n                )\n            }\n        }\n    }\n\n'''
hc = replace_once(
    hc,
    '    private suspend fun importTotalCalories(\n',
    active_history + '    private suspend fun importTotalCalories(\n',
    'insert active calories history'
)

# Make full step sync preserve Samsung-side update freshness for today.
hc = replace_once(
    hc,
    '''            val count = result[StepsRecord.COUNT_TOTAL] ?: 0L\n            output += row(\n                HealthDomain.EXERCISE,\n                "steps",\n                count.toDouble(),\n                "count",\n                summaryTimestamp(date, today, end, now),\n                summaryMeta(date, "steps")\n            )\n''',
    '''            val count = result[StepsRecord.COUNT_TOTAL] ?: 0L\n            val freshness = if (date == today) {\n                latestStepSourceUpdate(client, start, end)?.let { mapOf("sourceLastModifiedMs" to it.toString()) } ?: emptyMap()\n            } else emptyMap()\n            output += row(\n                HealthDomain.EXERCISE,\n                "steps",\n                count.toDouble(),\n                "count",\n                summaryTimestamp(date, today, end, now),\n                summaryMeta(date, "steps") + freshness\n            )\n''',
    'step freshness history'
)

# Helper uses Health Connect record metadata/end time to report when Samsung actually updated
# the step source rather than stamping every poll as "fresh".
step_helper = '''\n    private suspend fun latestStepSourceUpdate(\n        client: HealthConnectClient,\n        start: Instant,\n        end: Instant\n    ): Long? {\n        if (!end.isAfter(start)) return null\n        return runCatching {\n            val response = client.readRecords(\n                ReadRecordsRequest(\n                    recordType = StepsRecord::class,\n                    timeRangeFilter = TimeRangeFilter.between(start, end),\n                    dataOriginFilter = samsungFilter,\n                    ascendingOrder = false,\n                    pageSize = 256\n                )\n            )\n            response.records.maxOfOrNull { record ->\n                maxOf(record.endTime.toEpochMilli(), record.metadata.lastModifiedTime.toEpochMilli())\n            }\n        }.getOrNull()\n    }\n'''
hc = replace_once(
    hc,
    '    /**\n     * Daily aggregate rows are replaceable snapshots:',
    step_helper + '\n    /**\n     * Daily aggregate rows are replaceable snapshots:',
    'insert step source update helper'
)

hc_path.write_text(hc)

# -----------------------------------------------------------------------------
# Home/detail UI: active calorie parity, real Samsung step freshness, faster poll,
# optional background read control.
# -----------------------------------------------------------------------------
home_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/HomeMiniMetrics.kt')
home = home_path.read_text()

home = replace_once(
    home,
    '''    val steps: Int? = null,\n    val stepsRecentAverage: Int? = null,\n    val bloodOxygenPct: Int? = null,\n    val bloodOxygenTimestampMs: Long? = null,\n    val caloriesBurned: Int? = null,\n    val caloriesEaten: Int? = null\n)''',
    '''    val steps: Int? = null,\n    val stepsRecentAverage: Int? = null,\n    val stepsSourceUpdatedAtMs: Long? = null,\n    val bloodOxygenPct: Int? = null,\n    val bloodOxygenTimestampMs: Long? = null,\n    val caloriesActiveBurned: Int? = null,\n    val caloriesTotalBurned: Int? = null,\n    val caloriesEaten: Int? = null\n)''',
    'snapshot fields'
)

home = replace_once(
    home,
    '''    val sourceValue: String = "Samsung",\n    val history: List<Double?> = emptyList(),''',
    '''    val tertiaryLabel: String = "SOURCE",\n    val tertiaryValue: String = "Samsung",\n    val history: List<Double?> = emptyList(),''',
    'detail tertiary fields'
)

home = replace_once(home, '                delay(30_000L)\n', '                delay(10_000L)\n', 'home polling interval')

home = replace_once(
    home,
    '''                status = if (metrics.steps != null) metrics.stepsRecentAverage?.let { "recent avg ${compactCount(it)}" } ?: "today · auto refresh" else "Tap to connect",''',
    '''                status = if (metrics.steps != null) {\n                    val age = freshnessLabel(metrics.stepsSourceUpdatedAtMs)\n                    metrics.stepsRecentAverage?.let { "$age · avg ${compactCount(it)}" } ?: age\n                } else "Tap to connect",''',
    'steps freshness label'
)

home = replace_once(
    home,
    '''                value = metrics.caloriesBurned?.let(::compactCount) ?: "—",\n                unit = if (metrics.caloriesBurned != null) "kcal" else "",\n                status = if (metrics.caloriesBurned != null) {\n                    "${compactCount(metrics.caloriesEaten ?: 0)} eaten"\n                } else {\n                    metrics.caloriesEaten?.let { "${compactCount(it)} eaten · connect burn" } ?: "Tap to connect"\n                },''',
    '''                value = metrics.caloriesActiveBurned?.let(::compactCount) ?: "—",\n                unit = if (metrics.caloriesActiveBurned != null) "kcal" else "",\n                status = if (metrics.caloriesActiveBurned != null) {\n                    val eaten = compactCount(metrics.caloriesEaten ?: 0)\n                    val total = metrics.caloriesTotalBurned?.let(::compactCount)\n                    if (total != null) "$eaten eaten · $total total" else "$eaten eaten"\n                } else {\n                    metrics.caloriesEaten?.let { "${compactCount(it)} eaten · connect burn" } ?: "Tap to connect"\n                },''',
    'active calorie home card'
)

home = replace_once(
    home,
    '''    var syncing by remember(metric) { mutableStateOf(false) }\n    var connected by remember(metric) { mutableStateOf(false) }\n    var status by remember(metric) { mutableStateOf("Checking Samsung Health…") }\n''',
    '''    var syncing by remember(metric) { mutableStateOf(false) }\n    var connected by remember(metric) { mutableStateOf(false) }\n    var backgroundAvailable by remember(metric) { mutableStateOf(false) }\n    var backgroundEnabled by remember(metric) { mutableStateOf(false) }\n    var status by remember(metric) { mutableStateOf("Checking Samsung Health…") }\n''',
    'background ui state'
)

# Permission launcher: query actual final permission state, because a background-only request
# does not necessarily echo previously granted metric permissions in the callback set.
old_launcher = '''    val permissionLauncher = rememberLauncherForActivityResult(\n        contract = PermissionController.createRequestPermissionResultContract()\n    ) { granted ->\n        if (permission != null && permission in granted) {\n            connected = true\n            status = "Connected to Samsung Health through Health Connect"\n            scope.launch { sync() }\n        } else if (permission != null) {\n            connected = false\n            status = "Access wasn’t enabled"\n        }\n    }\n'''
new_launcher = '''    val permissionLauncher = rememberLauncherForActivityResult(\n        contract = PermissionController.createRequestPermissionResultContract()\n    ) { _ ->\n        scope.launch {\n            connected = MiniMetricsHealthConnect.hasPermission(context, metric)\n            backgroundAvailable = MiniMetricsHealthConnect.backgroundReadAvailable(context)\n            backgroundEnabled = MiniMetricsHealthConnect.hasBackgroundReadPermission(context)\n            if (backgroundEnabled) MiniMetricsBackgroundSync.ensureScheduled(context)\n            status = when {\n                connected && backgroundEnabled -> "Samsung Health connected · background sync enabled"\n                connected -> "Samsung Health connected through Health Connect"\n                else -> "Access wasn’t enabled"\n            }\n            if (connected) sync()\n        }\n    }\n'''
home = replace_once(home, old_launcher, new_launcher, 'permission launcher')

home = replace_once(
    home,
    '''                    if (MiniMetricsHealthConnect.hasPermission(context, metric)) sync()\n                    else if (permission != null) permissionLauncher.launch(setOf(permission))\n''',
    '''                    if (MiniMetricsHealthConnect.hasPermission(context, metric)) sync()\n                    else if (permission != null) permissionLauncher.launch(MiniMetricsHealthConnect.requestPermissionsFor(context, metric))\n''',
    'connect permission set'
)

home = replace_once(
    home,
    '''    }\n\n    LaunchedEffect(metric) {\n        refresh()\n        val available = MiniMetricsHealthConnect.availability(context) == HealthConnectClient.SDK_AVAILABLE\n        connected = available && MiniMetricsHealthConnect.hasPermission(context, metric)\n        status = when {\n            !available -> "Health Connect isn’t available on this device"\n            connected -> "Samsung Health connected through Health Connect"\n            else -> "Connect this metric from Samsung Health"\n        }\n        if (connected) sync()\n    }\n''',
    '''    }\n\n    fun enableBackgroundSync() {\n        if (backgroundAvailable) permissionLauncher.launch(setOf(MiniMetricsHealthConnect.backgroundReadPermission))\n    }\n\n    LaunchedEffect(metric) {\n        refresh()\n        val available = MiniMetricsHealthConnect.availability(context) == HealthConnectClient.SDK_AVAILABLE\n        connected = available && MiniMetricsHealthConnect.hasPermission(context, metric)\n        backgroundAvailable = available && MiniMetricsHealthConnect.backgroundReadAvailable(context)\n        backgroundEnabled = available && MiniMetricsHealthConnect.hasBackgroundReadPermission(context)\n        if (backgroundEnabled) MiniMetricsBackgroundSync.ensureScheduled(context)\n        status = when {\n            !available -> "Health Connect isn’t available on this device"\n            connected && backgroundEnabled -> "Samsung Health connected · background sync enabled"\n            connected -> "Samsung Health connected through Health Connect"\n            else -> "Connect this metric from Samsung Health"\n        }\n        if (connected) sync()\n    }\n''',
    'background startup state'
)

home = replace_once(
    home,
    '''            HomeMiniMetric.HEART_RATE, HomeMiniMetric.STEPS -> 15_000L\n            HomeMiniMetric.BLOOD_OXYGEN -> 30_000L\n            HomeMiniMetric.CALORIES -> 30_000L\n''',
    '''            HomeMiniMetric.HEART_RATE, HomeMiniMetric.STEPS -> 5_000L\n            HomeMiniMetric.BLOOD_OXYGEN -> 15_000L\n            HomeMiniMetric.CALORIES -> 10_000L\n''',
    'detail polling intervals'
)

home = replace_once(
    home,
    '''                MetricStat("SOURCE", detail.sourceValue, Modifier.weight(1f))\n''',
    '''                MetricStat(detail.tertiaryLabel, detail.tertiaryValue, Modifier.weight(1f))\n''',
    'tertiary stat rendering'
)

home = replace_once(
    home,
    '''            status = status,\n            onClick = ::connectOrSync\n        )''',
    '''            status = status,\n            backgroundAvailable = backgroundAvailable,\n            backgroundEnabled = backgroundEnabled,\n            onClick = ::connectOrSync,\n            onEnableBackground = ::enableBackgroundSync\n        )''',
    'health connect card call'
)

home = replace_once(
    home,
    '''                HomeMiniMetric.CALORIES -> "Total energy burned today compared with food logged in Project Superhuman"\n''',
    '''                HomeMiniMetric.CALORIES -> "Active Fit3 / Samsung burn today compared with food logged in Project Superhuman"\n''',
    'calorie hero description'
)

home = replace_once(
    home,
    '''                HomeMiniMetric.CALORIES -> "Daily calories burned"\n''',
    '''                HomeMiniMetric.CALORIES -> "Daily active calories burned"\n''',
    'calorie history label'
)

# Health Connect card with background-sync opt-in.
home = replace_once(
    home,
    '''private fun HealthConnectMiniCard(\n    connected: Boolean,\n    syncing: Boolean,\n    status: String,\n    onClick: () -> Unit\n) {''',
    '''private fun HealthConnectMiniCard(\n    connected: Boolean,\n    syncing: Boolean,\n    status: String,\n    backgroundAvailable: Boolean,\n    backgroundEnabled: Boolean,\n    onClick: () -> Unit,\n    onEnableBackground: () -> Unit\n) {''',
    'health connect card signature'
)

home = replace_once(
    home,
    '''        Box(\n            Modifier.background(Color(0xFFE8F3FA), RoundedCornerShape(14.dp))\n                .clickable(enabled = !syncing, onClick = onClick)\n                .padding(horizontal = 14.dp, vertical = 10.dp)\n        ) {\n            Text(\n                when {\n                    syncing -> "SYNCING…"\n                    connected -> "SYNC NOW"\n                    else -> "CONNECT"\n                },\n                color = Color(0xFF0D6CB4),\n                fontSize = 9.sp,\n                fontWeight = FontWeight.Black\n            )\n        }\n''',
    '''        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {\n            Box(\n                Modifier.background(Color(0xFFE8F3FA), RoundedCornerShape(14.dp))\n                    .clickable(enabled = !syncing, onClick = onClick)\n                    .padding(horizontal = 14.dp, vertical = 10.dp)\n            ) {\n                Text(\n                    when {\n                        syncing -> "SYNCING…"\n                        connected -> "SYNC NOW"\n                        else -> "CONNECT"\n                    },\n                    color = Color(0xFF0D6CB4),\n                    fontSize = 9.sp,\n                    fontWeight = FontWeight.Black\n                )\n            }\n            if (connected && backgroundAvailable && !backgroundEnabled) {\n                Box(\n                    Modifier.background(Color(0xFFF1F5F8), RoundedCornerShape(14.dp))\n                        .clickable(enabled = !syncing, onClick = onEnableBackground)\n                        .padding(horizontal = 12.dp, vertical = 10.dp)\n                ) {\n                    Text("BACKGROUND", color = MiniNavy, fontSize = 8.sp, fontWeight = FontWeight.Black)\n                }\n            } else if (connected && backgroundEnabled) {\n                Text("15 min background sync", color = MiniMuted, fontSize = 8.sp)\n            }\n        }\n''',
    'background sync card control'
)

# Snapshot uses actual Samsung source update time for steps and active calories for Fit3 parity.
home = replace_once(
    home,
    '''    val caloriesBurned = latestSamsung(\n        NativeDataHub.between(HealthDomain.EXERCISE, "calories_burned_total_kcal", todayStart, now)\n    )?.value\n''',
    '''    val caloriesActiveBurned = latestSamsung(\n        NativeDataHub.between(HealthDomain.EXERCISE, "calories_burned_active_kcal", todayStart, now)\n    )?.value\n    val caloriesTotalBurned = latestSamsung(\n        NativeDataHub.between(HealthDomain.EXERCISE, "calories_burned_total_kcal", todayStart, now)\n    )?.value\n''',
    'snapshot calorie reads'
)

home = replace_once(
    home,
    '''        steps = steps?.value?.roundToInt(),\n        stepsRecentAverage = stepsRecentAverage,\n        bloodOxygenPct = oxygen?.value?.roundToInt(),\n        bloodOxygenTimestampMs = oxygen?.timestampEpochMs,\n        caloriesBurned = caloriesBurned?.roundToInt(),\n        caloriesEaten = caloriesEaten.takeIf { it > 0.0 }?.roundToInt()\n''',
    '''        steps = steps?.value?.roundToInt(),\n        stepsRecentAverage = stepsRecentAverage,\n        stepsSourceUpdatedAtMs = steps?.metadata?.get("sourceLastModifiedMs")?.toLongOrNull(),\n        bloodOxygenPct = oxygen?.value?.roundToInt(),\n        bloodOxygenTimestampMs = oxygen?.timestampEpochMs,\n        caloriesActiveBurned = caloriesActiveBurned?.roundToInt(),\n        caloriesTotalBurned = caloriesTotalBurned?.roundToInt(),\n        caloriesEaten = caloriesEaten.takeIf { it > 0.0 }?.roundToInt()\n''',
    'snapshot output'
)

# Calories detail uses active burn to match Fit3 while retaining total expenditure separately.
old_cal_detail = '''            val history = dates.map { summary(HealthDomain.EXERCISE, "calories_burned_total_kcal", it) }\n            val current = history.lastOrNull()\n            val eatenToday = eaten(today)\n            MiniMetricDetailData(\n                current = current,\n                unit = "kcal",\n                primaryLabel = "BURNED",\n                primaryValue = current?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "—",\n                secondaryLabel = "EATEN",\n                secondaryValue = eatenToday?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "0 kcal",\n                sourceValue = "Samsung + diary",\n                history = history,\n                hasSamsungData = history.any { it != null }\n            )\n'''
new_cal_detail = '''            val history = dates.map { summary(HealthDomain.EXERCISE, "calories_burned_active_kcal", it) }\n            val current = history.lastOrNull()\n            val totalToday = summary(HealthDomain.EXERCISE, "calories_burned_total_kcal", today)\n            val eatenToday = eaten(today)\n            MiniMetricDetailData(\n                current = current,\n                unit = "kcal",\n                primaryLabel = "ACTIVE",\n                primaryValue = current?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "—",\n                secondaryLabel = "EATEN",\n                secondaryValue = eatenToday?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "0 kcal",\n                tertiaryLabel = "TOTAL",\n                tertiaryValue = totalToday?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "—",\n                history = history,\n                hasSamsungData = history.any { it != null } || totalToday != null\n            )\n'''
home = replace_once(home, old_cal_detail, new_cal_detail, 'calories detail')

# Non-calorie detail constructors still use default tertiary SOURCE/Samsung, so no other changes needed.
home_path.write_text(home)

# -----------------------------------------------------------------------------
# Reliable background reads via WorkManager (15 min is Android's periodic minimum).
# -----------------------------------------------------------------------------
worker_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/MiniMetricsBackgroundSync.kt')
worker_path.write_text('''package com.projectsuperhuman.next\n\nimport android.content.Context\nimport androidx.work.Constraints\nimport androidx.work.CoroutineWorker\nimport androidx.work.ExistingPeriodicWorkPolicy\nimport androidx.work.PeriodicWorkRequestBuilder\nimport androidx.work.WorkManager\nimport androidx.work.WorkerParameters\nimport java.util.concurrent.TimeUnit\n\ninternal class MiniMetricsBackgroundWorker(\n    appContext: Context,\n    workerParams: WorkerParameters\n) : CoroutineWorker(appContext, workerParams) {\n    override suspend fun doWork(): Result {\n        if (!MiniMetricsHealthConnect.backgroundReadAvailable(applicationContext)) return Result.success()\n        if (!MiniMetricsHealthConnect.hasBackgroundReadPermission(applicationContext)) return Result.success()\n        if (!MiniMetricsHealthConnect.hasAnyPermission(applicationContext)) return Result.success()\n\n        val result = MiniMetricsHealthConnect.syncCurrent(applicationContext)\n        return when {\n            result.success -> Result.success()\n            runAttemptCount < 2 -> Result.retry()\n            else -> Result.success()\n        }\n    }\n}\n\ninternal object MiniMetricsBackgroundSync {\n    private const val UNIQUE_WORK = "fit3-mini-metrics-background-sync"\n\n    fun ensureScheduled(context: Context) {\n        val constraints = Constraints.Builder()\n            .setRequiresBatteryNotLow(true)\n            .build()\n        val work = PeriodicWorkRequestBuilder<MiniMetricsBackgroundWorker>(\n            15, TimeUnit.MINUTES,\n            5, TimeUnit.MINUTES\n        ).setConstraints(constraints).build()\n\n        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(\n            UNIQUE_WORK,\n            ExistingPeriodicWorkPolicy.UPDATE,\n            work\n        )\n    }\n}\n''')

# Schedule the worker at app startup. It is inert until background health permission is granted.
shell_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NextShellActivity.kt')
shell = shell_path.read_text()
shell = replace_once(
    shell,
    '        NativeDataHub.initialize(this)\n        enableEdgeToEdge()\n',
    '        NativeDataHub.initialize(this)\n        MiniMetricsBackgroundSync.ensureScheduled(this)\n        enableEdgeToEdge()\n',
    'schedule background worker'
)
shell_path.write_text(shell)

# Manifest: active calories + optional Health Connect background reads.
manifest_path = Path('nextgen/androidApp/src/main/AndroidManifest.xml')
manifest = manifest_path.read_text()
manifest = replace_once(
    manifest,
    '    <uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />\n',
    '    <uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED" />\n    <uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />\n    <uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />\n',
    'manifest health permissions'
)
manifest_path.write_text(manifest)

# Gradle: WorkManager + release bump.
build_path = Path('nextgen/androidApp/build.gradle.kts')
build = build_path.read_text()
build = replace_once(build, 'versionCode = 11219', 'versionCode = 11220', 'version code')
build = replace_once(build, 'versionName = "11.2.19"', 'versionName = "11.2.20"', 'version name')
build = replace_once(
    build,
    '    implementation("androidx.health.connect:connect-client:1.1.0")\n',
    '    implementation("androidx.health.connect:connect-client:1.1.0")\n    implementation("androidx.work:work-runtime:2.11.2")\n',
    'workmanager dependency'
)
build_path.write_text(build)

print('Applied Fit3 sync hardening and bumped Project Superhuman to 11.2.20')
