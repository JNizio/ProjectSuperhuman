from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

home_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/HomeMiniMetrics.kt')
home = home_path.read_text()
home = replace_once(home, '    STRESS("Stress", "Recovery & strain")', '    CALORIES("Calories", "Burned vs eaten")', 'enum')
home = replace_once(
    home,
    '    val bloodOxygenTimestampMs: Long? = null,\n    val stressScore: Int? = null\n)',
    '    val bloodOxygenTimestampMs: Long? = null,\n    val caloriesBurned: Int? = null,\n    val caloriesEaten: Int? = null\n)',
    'snapshot fields'
)
home = home.replace('MiniStress', 'MiniCalories')
home = replace_once(home, 'private val MiniCalories = Color(0xFF7260BF)', 'private val MiniCalories = Color(0xFFE08A2E)', 'calorie accent')

old_card = '''            MiniMetricCard(
                metric = HomeMiniMetric.STRESS,
                value = metrics.stressScore?.toString() ?: "—",
                unit = if (metrics.stressScore != null) "/100" else "",
                status = if (metrics.stressScore != null) "Latest reading" else "Not shared by Samsung",
                accent = MiniCalories,
                modifier = Modifier.weight(1.18f),
                style = MiniVisualStyle.WAVES,
                onClick = { openMetric(HomeMiniMetric.STRESS) }
            )'''
new_card = '''            MiniMetricCard(
                metric = HomeMiniMetric.CALORIES,
                value = metrics.caloriesBurned?.let(::compactCount) ?: "—",
                unit = if (metrics.caloriesBurned != null) "kcal" else "",
                status = when {
                    metrics.caloriesBurned != null -> "${compactCount(metrics.caloriesEaten ?: 0)} eaten"
                    metrics.caloriesEaten != null -> "${compactCount(metrics.caloriesEaten)} eaten · connect burn"
                    else -> "Tap to connect"
                },
                accent = MiniCalories,
                modifier = Modifier.weight(1.18f),
                style = MiniVisualStyle.WAVES,
                onClick = { openMetric(HomeMiniMetric.CALORIES) }
            )'''
home = replace_once(home, old_card, new_card, 'calories card')

home = home.replace('HomeMiniMetric.STRESS', 'HomeMiniMetric.CALORIES')
home = replace_once(home, '        if (metric == HomeMiniMetric.CALORIES) return\n', '', 'remove stress connect guard')
home = replace_once(
    home,
    '''        if (metric == HomeMiniMetric.CALORIES) {
            status = "Samsung Health does not currently share its Stress score through Health Connect"
            return@LaunchedEffect
        }
''',
    '',
    'remove stress launch guard'
)
home = replace_once(home, '        if (!connected || metric == HomeMiniMetric.CALORIES || !isForeground) return@LaunchedEffect', '        if (!connected || !isForeground) return@LaunchedEffect', 'foreground guard')
home = replace_once(home, '            HomeMiniMetric.CALORIES -> 60_000L', '            HomeMiniMetric.CALORIES -> 30_000L', 'calories polling')
home = replace_once(
    home,
    '''        if (metric == HomeMiniMetric.CALORIES && detail.current == null) {
            UnsupportedStressCard()
        } else {''',
    '        run {',
    'remove unsupported stress branch'
)
home = replace_once(
    home,
    '''        if (metric != HomeMiniMetric.CALORIES) {
            HealthConnectMiniCard(
                connected = connected,
                syncing = syncing,
                status = status,
                onClick = ::connectOrSync
            )
        }''',
    '''        HealthConnectMiniCard(
            connected = connected,
            syncing = syncing,
            status = status,
            onClick = ::connectOrSync
        )''',
    'always show health connect card'
)
home = home.replace('HomeMiniMetric.CALORIES -> "Latest stress value"', 'HomeMiniMetric.CALORIES -> "Total energy burned today compared with food logged in Project Superhuman"')
home = home.replace('HomeMiniMetric.CALORIES -> "Daily stress"', 'HomeMiniMetric.CALORIES -> "Daily calories burned"')

old_snapshot_tail = '''    suspend fun fallback(vararg names: String): Double? {
        names.forEach { name -> NativeDataHub.latest(name)?.value?.let { return it } }
        return null
    }

    val stress = fallback("stress_score", "stress_level")
    return MiniMetricSnapshot(
        heartRateBpm = heart?.value?.roundToInt(),
        heartRateTimestampMs = heart?.timestampEpochMs,
        steps = steps?.value?.roundToInt(),
        stepsRecentAverage = stepsRecentAverage,
        bloodOxygenPct = oxygen?.value?.roundToInt(),
        bloodOxygenTimestampMs = oxygen?.timestampEpochMs,
        stressScore = stress?.let { if (it <= 10.0) (it * 10.0).roundToInt() else it.roundToInt() }
    )'''
new_snapshot_tail = '''    val caloriesBurned = latestSamsung(
        NativeDataHub.between(HealthDomain.EXERCISE, "calories_burned_total_kcal", todayStart, now)
    )?.value
    val caloriesEaten = NativeDataHub.between(HealthDomain.NUTRITION, "food_kcal", todayStart, now)
        .sumOf { it.value }

    return MiniMetricSnapshot(
        heartRateBpm = heart?.value?.roundToInt(),
        heartRateTimestampMs = heart?.timestampEpochMs,
        steps = steps?.value?.roundToInt(),
        stepsRecentAverage = stepsRecentAverage,
        bloodOxygenPct = oxygen?.value?.roundToInt(),
        bloodOxygenTimestampMs = oxygen?.timestampEpochMs,
        caloriesBurned = caloriesBurned?.roundToInt(),
        caloriesEaten = caloriesEaten.takeIf { it > 0.0 }?.roundToInt()
    )'''
home = replace_once(home, old_snapshot_tail, new_snapshot_tail, 'snapshot calories')

old_detail_case = '''        HomeMiniMetric.CALORIES -> {
            val raw = NativeDataHub.latest("stress_score")?.value ?: NativeDataHub.latest("stress_level")?.value
            val current = raw?.let { if (it <= 10.0) it * 10.0 else it }
            MiniMetricDetailData(
                current = current,
                unit = "/100",
                primaryLabel = "SOURCE",
                primaryValue = if (current != null) "Local" else "—",
                secondaryLabel = "SAMSUNG",
                secondaryValue = "Not shared",
                history = emptyList(),
                hasSamsungData = false
            )
        }'''
new_detail_case = '''        HomeMiniMetric.CALORIES -> {
            suspend fun eaten(date: LocalDate): Double? {
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                val total = NativeDataHub.between(HealthDomain.NUTRITION, "food_kcal", start, end).sumOf { it.value }
                return total.takeIf { it > 0.0 }
            }

            val history = dates.map { summary(HealthDomain.EXERCISE, "calories_burned_total_kcal", it) }
            val current = history.lastOrNull()
            val eatenToday = eaten(today)
            MiniMetricDetailData(
                current = current,
                unit = "kcal",
                primaryLabel = "BURNED",
                primaryValue = current?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "—",
                secondaryLabel = "EATEN",
                secondaryValue = eatenToday?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "0 kcal",
                sourceValue = "Samsung + diary",
                history = history,
                hasSamsungData = history.any { it != null }
            )
        }'''
home = replace_once(home, old_detail_case, new_detail_case, 'calories detail')

if 'HomeMiniMetric.STRESS' in home or 'stressScore' in home:
    raise SystemExit('stale stress mini metric reference remains in HomeMiniMetrics.kt')
home_path.write_text(home)

hc_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/MiniMetricsHealthConnect.kt')
hc = hc_path.read_text()
hc = replace_once(hc, 'import androidx.health.connect.client.records.StepsRecord\n', 'import androidx.health.connect.client.records.StepsRecord\nimport androidx.health.connect.client.records.TotalCaloriesBurnedRecord\n', 'calorie import')
hc = replace_once(
    hc,
    '    val oxygenPermission: String = HealthPermission.getReadPermission(OxygenSaturationRecord::class)\n    val permissions: Set<String> = setOf(heartRatePermission, stepsPermission, oxygenPermission)',
    '    val oxygenPermission: String = HealthPermission.getReadPermission(OxygenSaturationRecord::class)\n    val totalCaloriesPermission: String = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)\n    val permissions: Set<String> = setOf(heartRatePermission, stepsPermission, oxygenPermission, totalCaloriesPermission)',
    'calorie permission'
)
hc = hc.replace('HomeMiniMetric.STRESS', 'HomeMiniMetric.CALORIES')
hc = replace_once(hc, '        HomeMiniMetric.CALORIES -> null', '        HomeMiniMetric.CALORIES -> totalCaloriesPermission', 'permission mapping')
hc = replace_once(
    hc,
    '''                if (oxygenPermission in granted) {
                    importOxygen(client, today, now, zone, values)
                }
''',
    '''                if (oxygenPermission in granted) {
                    importOxygen(client, today, now, zone, values)
                }
                if (totalCaloriesPermission in granted) {
                    importCalories(client, today, now, zone, values)
                }
''',
    'full calorie sync'
)
hc = replace_once(
    hc,
    '''        if (metric == HomeMiniMetric.CALORIES) {
            return MiniHealthSyncResult(false, 0, "Samsung Stress is not shared through Health Connect")
        }

''',
    '',
    'remove stress current guard'
)
hc = replace_once(hc, '            HomeMiniMetric.CALORIES -> emptySet()', '            HomeMiniMetric.CALORIES -> setOf(totalCaloriesPermission)', 'current calorie permission')
hc = replace_once(
    hc,
    '                if (oxygenPermission in readable) importOxygenCurrent(client, today, now, zone, values)\n',
    '                if (oxygenPermission in readable) importOxygenCurrent(client, today, now, zone, values)\n                if (totalCaloriesPermission in readable) importCaloriesCurrent(client, today, now, zone, values)\n',
    'current calorie sync'
)

current_calories = '''
    private suspend fun importCaloriesCurrent(
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
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, now),
                dataOriginFilter = samsungFilter
            )
        )
        result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { energy ->
            output += row(
                HealthDomain.EXERCISE,
                "calories_burned_total_kcal",
                energy.inKilocalories,
                "kcal",
                now.toEpochMilli(),
                summaryMeta(today, "calories-burned") + mapOf("refreshMode" to "foreground")
            )
        }
    }

'''
hc = replace_once(hc, '    private suspend fun importOxygenCurrent(\n', current_calories + '    private suspend fun importOxygenCurrent(\n', 'insert current calorie importer')

history_calories = '''
    private suspend fun importCalories(
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
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = samsungFilter
                )
            )
            result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { energy ->
                output += row(
                    HealthDomain.EXERCISE,
                    "calories_burned_total_kcal",
                    energy.inKilocalories,
                    "kcal",
                    summaryTimestamp(date, today, end, now),
                    summaryMeta(date, "calories-burned")
                )
            }
        }
    }

'''
hc = replace_once(hc, '    private suspend fun importOxygen(\n', history_calories + '    private suspend fun importOxygen(\n', 'insert calorie history importer')
if 'HomeMiniMetric.STRESS' in hc:
    raise SystemExit('stale stress mini metric reference remains in MiniMetricsHealthConnect.kt')
hc_path.write_text(hc)

shell_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NextShellActivity.kt')
shell = shell_path.read_text()
shell = shell.replace('STRESS', 'CALORIES')
shell = shell.replace('HomeMiniMetric.CALORIES -> ShellPage.CALORIES', 'HomeMiniMetric.CALORIES -> ShellPage.CALORIES')
shell_path.write_text(shell)

manifest_path = Path('nextgen/androidApp/src/main/AndroidManifest.xml')
manifest = manifest_path.read_text()
if 'android.permission.health.READ_TOTAL_CALORIES_BURNED' not in manifest:
    manifest = replace_once(
        manifest,
        '    <uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" />\n',
        '    <uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" />\n    <uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />\n',
        'manifest calorie permission'
    )
manifest_path.write_text(manifest)

build_path = Path('nextgen/androidApp/build.gradle.kts')
build = build_path.read_text()
build = replace_once(build, 'versionCode = 11218', 'versionCode = 11219', 'version code')
build = replace_once(build, 'versionName = "11.2.18"', 'versionName = "11.2.19"', 'version name')
build_path.write_text(build)

print('Applied calories burned-vs-eaten mini module and version 11.2.19')
