package com.projectsuperhuman.next

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.environment.AndroidEnvironmentalHttpClient
import com.projectsuperhuman.next.environment.EnvironmentalHttpRequest
import com.projectsuperhuman.next.environment.EnvironmentalMetricIds
import com.projectsuperhuman.next.environment.OpenMeteoRequestBuilder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round
import org.json.JSONArray
import org.json.JSONObject

private val EnvTimelineBg: Color get() = superhumanBackground
private val EnvTimelineSurface: Color get() = superhumanSurface
private val EnvTimelineElevated: Color get() = superhumanSurfaceElevated
private val EnvTimelineSoft: Color get() = superhumanSurfaceSoft
private val EnvTimelineNavy: Color get() = superhumanBrandText
private val EnvTimelineInk: Color get() = superhumanTextPrimary
private val EnvTimelineMuted: Color get() = superhumanTextMuted
private val EnvTimelineBlue: Color get() = superhumanBlue
private val EnvTimelineTeal: Color get() = superhumanGreen
private val EnvTimelineBorder: Color get() = superhumanBorder

internal data class EnvironmentalDailyMetricRange(
    val metricId: String,
    val label: String,
    val minimum: Double,
    val maximum: Double,
    val average: Double,
    val unit: String,
    val samples: Int
)

internal data class EnvironmentalDailyView(
    val date: LocalDate,
    val placeLabel: String,
    val sourceLabel: String,
    val forecast: Boolean,
    val ranges: List<EnvironmentalDailyMetricRange>
)

private sealed interface EnvironmentalDailyState {
    data object Loading : EnvironmentalDailyState
    data class Ready(val view: EnvironmentalDailyView?) : EnvironmentalDailyState
    data class Error(val message: String) : EnvironmentalDailyState
}

@Composable
internal fun NativeEnvironmentalTimelineRoute(
    onBack: () -> Unit,
    source: EnvironmentalPresentationSource = EnvironmentalUiRuntime.source()
) {
    val context = LocalContext.current.applicationContext
    val today = LocalDate.now()
    var selectedDate by remember { mutableStateOf(today) }
    val currentState = rememberEnvironmentalRenderState(source, selectedDate.toEpochDay().toInt())
    var dailyState by remember { mutableStateOf<EnvironmentalDailyState>(EnvironmentalDailyState.Loading) }

    LaunchedEffect(selectedDate) {
        if (selectedDate == today) return@LaunchedEffect
        dailyState = EnvironmentalDailyState.Loading
        dailyState = runCatching {
            val view = if (selectedDate.isBefore(today)) {
                loadRecordedEnvironmentalDay(context, selectedDate)
            } else {
                loadForecastEnvironmentalDay(context, selectedDate)
            }
            EnvironmentalDailyState.Ready(view)
        }.getOrElse { EnvironmentalDailyState.Error(it.message ?: "Could not load environmental day") }
    }

    Column(
        Modifier.fillMaxSize().background(EnvTimelineBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        EnvironmentalTimelineHeader(onBack)
        EnvironmentalDateNavigator(
            date = selectedDate,
            today = today,
            canGoForward = selectedDate.isBefore(today.plusDays(7)),
            onPrevious = { selectedDate = selectedDate.minusDays(1) },
            onNext = { if (selectedDate.isBefore(today.plusDays(7))) selectedDate = selectedDate.plusDays(1) },
            onToday = { selectedDate = today }
        )

        if (selectedDate == today) {
            when (currentState) {
                EnvironmentalRenderState.Loading -> EnvironmentalStateCard(
                    testTag = "environment_state_loading",
                    title = "Reading your environment",
                    message = "Getting the latest local context available to Project Superhuman.",
                    accent = EnvTimelineBlue,
                    loading = true
                )
                is EnvironmentalRenderState.Ready -> when (val result = currentState.result) {
                    is EnvironmentalLoadResult.Data -> EnvironmentalContent(result.conditions)
                    EnvironmentalLoadResult.NoPermission -> EnvironmentalStateCard(
                        "environment_state_no_permission", "Location access needed",
                        "Allow location so Project Superhuman can attach local conditions to your health timeline.",
                        EnvTimelineBlue
                    )
                    EnvironmentalLoadResult.NoData -> EnvironmentalStateCard(
                        "environment_state_no_data", "No environmental reading yet",
                        "A local observation has not been captured yet.", EnvTimelineBlue
                    )
                    is EnvironmentalLoadResult.Error -> EnvironmentalStateCard(
                        "environment_state_error", "Environmental data unavailable",
                        result.message ?: "Project Superhuman couldn't load the current environmental context.",
                        superhumanRed
                    )
                }
            }
        } else {
            when (val state = dailyState) {
                EnvironmentalDailyState.Loading -> EnvironmentalStateCard(
                    "environment_day_loading", "Loading ${dateTitle(selectedDate, today)}",
                    if (selectedDate.isAfter(today)) "Getting the Open-Meteo forecast for this day."
                    else "Reading stored Environmental observations from your Data Vault.",
                    EnvTimelineBlue,
                    loading = true
                )
                is EnvironmentalDailyState.Error -> EnvironmentalStateCard(
                    "environment_day_error", "Environmental day unavailable", state.message, superhumanRed
                )
                is EnvironmentalDailyState.Ready -> {
                    val view = state.view
                    if (view == null || view.ranges.isEmpty()) {
                        EnvironmentalStateCard(
                            "environment_day_empty",
                            if (selectedDate.isAfter(today)) "Forecast unavailable" else "No stored observations",
                            if (selectedDate.isAfter(today)) "No forecast data was returned for this date."
                            else "Environmental recording had not captured any samples for this day.",
                            EnvTimelineBlue
                        )
                    } else {
                        EnvironmentalDailyRangeCard(view)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun EnvironmentalTimelineHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.superhumanTopButton(onClick = onBack).semantics { contentDescription = "Back to home" },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = EnvTimelineNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Environment", color = EnvTimelineInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Local context for sleep, activity & wellbeing", color = EnvTimelineMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EnvironmentalDateNavigator(
    date: LocalDate,
    today: LocalDate,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().background(EnvTimelineSurface, shape).border(1.dp, EnvTimelineBorder, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArrowButton("‹", "Previous day", true, onPrevious)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dateTitle(date, today), color = EnvTimelineNavy, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())), color = EnvTimelineMuted, fontSize = 8.sp)
            if (date != today) {
                Text(
                    "TODAY",
                    color = EnvTimelineBlue,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable(onClick = onToday).padding(top = 3.dp)
                )
            }
        }
        ArrowButton("›", "Next day", canGoForward, onNext)
    }
}

@Composable
private fun ArrowButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(48.dp).height(42.dp)
            .background(if (enabled) EnvTimelineSoft else EnvTimelineElevated.copy(alpha = .72f), RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = if (enabled) EnvTimelineNavy else EnvTimelineMuted.copy(alpha = .55f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EnvironmentalDailyRangeCard(view: EnvironmentalDailyView) {
    val shape = RoundedCornerShape(29.dp)
    Column(
        Modifier.fillMaxWidth().background(EnvTimelineSurface, shape).border(1.dp, EnvTimelineBorder, shape).padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (view.forecast) "DAY FORECAST" else "RECORDED DAY", color = EnvTimelineBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(view.placeLabel, color = EnvTimelineNavy, fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
            Text(if (view.forecast) "FORECAST" else "VAULT", color = if (view.forecast) EnvTimelineBlue else EnvTimelineTeal, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(4.dp))
        Text(view.sourceLabel, color = EnvTimelineMuted, fontSize = 8.sp)
        Spacer(Modifier.height(14.dp))

        view.ranges.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { range -> DailyRangeMetric(range) }
                if (row.size == 1) Spacer(Modifier.width(145.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (view.forecast) "Forecasts are kept separate from recorded history and are never saved as observations."
            else "Min, average and max are calculated from observations automatically saved in the Data Vault.",
            color = EnvTimelineMuted,
            fontSize = 8.sp,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun DailyRangeMetric(range: EnvironmentalDailyMetricRange) {
    Column(
        Modifier.width(145.dp).background(EnvTimelineElevated, RoundedCornerShape(16.dp)).padding(11.dp)
    ) {
        Text(range.label.uppercase(), color = EnvTimelineMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("${formatMetric(range.average)} ${range.unit}", color = EnvTimelineNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text("${formatMetric(range.minimum)} – ${formatMetric(range.maximum)} ${range.unit}", color = EnvTimelineBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text("${range.samples} sample${if (range.samples == 1) "" else "s"}", color = EnvTimelineMuted, fontSize = 7.sp)
    }
}

private suspend fun loadRecordedEnvironmentalDay(context: Context, date: LocalDate): EnvironmentalDailyView? {
    val zone = ZoneId.systemDefault()
    val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
    val rows = NativeDataHub.domainBetween(HealthDomain.ENVIRONMENT, from, to)
    if (rows.isEmpty()) return null
    return EnvironmentalDailyView(
        date = date,
        placeLabel = rows.firstNotNullOfOrNull { it.metadata["environment.placeLabel"] }
            ?: EnvironmentalAutoRecorder.rememberedPlace(context)
            ?: "Local area",
        sourceLabel = "Data Vault · ${rows.size} recorded values",
        forecast = false,
        ranges = rangesFromStoredRows(rows)
    )
}

private fun rangesFromStoredRows(rows: List<HealthValue>): List<EnvironmentalDailyMetricRange> =
    rows.groupBy { it.metric }.mapNotNull { (metric, values) ->
        val label = environmentalMetricLabel(metric) ?: return@mapNotNull null
        val nums = values.map { it.value }.filter { it.isFinite() }
        if (nums.isEmpty()) return@mapNotNull null
        EnvironmentalDailyMetricRange(
            metricId = metric,
            label = label,
            minimum = nums.minOrNull() ?: return@mapNotNull null,
            maximum = nums.maxOrNull() ?: return@mapNotNull null,
            average = nums.average(),
            unit = values.first().unit,
            samples = nums.size
        )
    }.sortedBy { environmentalMetricOrder(it.metricId) }

private suspend fun loadForecastEnvironmentalDay(context: Context, date: LocalDate): EnvironmentalDailyView? {
    val coordinates = EnvironmentalAutoRecorder.rememberedCoordinates(context) ?: return null
    val c = coordinates.coarsened(2)
    val common = mapOf(
        "latitude" to c.latitude.toString(),
        "longitude" to c.longitude.toString(),
        "start_date" to date.toString(),
        "end_date" to date.toString(),
        "timezone" to "auto",
        "timeformat" to "unixtime"
    )
    val http = AndroidEnvironmentalHttpClient()
    val weatherRequest = EnvironmentalHttpRequest(
        OpenMeteoRequestBuilder.WEATHER_ENDPOINT,
        common + mapOf(
            "hourly" to "temperature_2m,apparent_temperature,relative_humidity_2m,surface_pressure,precipitation,cloud_cover,wind_speed_10m,wind_gusts_10m,uv_index",
            "temperature_unit" to "celsius",
            "wind_speed_unit" to "ms",
            "precipitation_unit" to "mm"
        )
    )
    val weatherResponse = http.get(weatherRequest)
    if (weatherResponse.statusCode !in 200..299) return null
    val ranges = parseForecastRanges(JSONObject(weatherResponse.body).optJSONObject("hourly"))

    val airRequest = EnvironmentalHttpRequest(
        OpenMeteoRequestBuilder.AIR_QUALITY_ENDPOINT,
        common + mapOf("hourly" to "european_aqi,pm2_5,pm10")
    )
    val airRanges = runCatching {
        val response = http.get(airRequest)
        if (response.statusCode !in 200..299) emptyList()
        else parseAirQualityForecastRanges(JSONObject(response.body).optJSONObject("hourly"))
    }.getOrDefault(emptyList())

    return EnvironmentalDailyView(
        date = date,
        placeLabel = EnvironmentalAutoRecorder.rememberedPlace(context) ?: "Local area",
        sourceLabel = "Open-Meteo · hourly forecast range",
        forecast = true,
        ranges = (ranges + airRanges).sortedBy { environmentalMetricOrder(it.metricId) }
    )
}

private fun parseForecastRanges(hourly: JSONObject?): List<EnvironmentalDailyMetricRange> {
    hourly ?: return emptyList()
    return listOfNotNull(
        hourly.range("temperature_2m", EnvironmentalMetricIds.TEMPERATURE_C, "Temperature", "°C"),
        hourly.range("apparent_temperature", EnvironmentalMetricIds.FEELS_LIKE_C, "Feels like", "°C"),
        hourly.range("relative_humidity_2m", EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT, "Humidity", "%"),
        hourly.range("precipitation", EnvironmentalMetricIds.PRECIPITATION_MM, "Precipitation", "mm"),
        hourly.range("surface_pressure", EnvironmentalMetricIds.SURFACE_PRESSURE_HPA, "Pressure", "hPa"),
        hourly.range("wind_speed_10m", EnvironmentalMetricIds.WIND_SPEED_MPS, "Wind", "m/s"),
        hourly.range("wind_gusts_10m", EnvironmentalMetricIds.WIND_GUST_MPS, "Wind gusts", "m/s"),
        hourly.range("cloud_cover", EnvironmentalMetricIds.CLOUD_COVER_PCT, "Cloud cover", "%"),
        hourly.range("uv_index", EnvironmentalMetricIds.UV_INDEX, "UV index", "")
    )
}

private fun parseAirQualityForecastRanges(hourly: JSONObject?): List<EnvironmentalDailyMetricRange> {
    hourly ?: return emptyList()
    return listOfNotNull(
        hourly.range("european_aqi", EnvironmentalMetricIds.EUROPEAN_AQI, "Air quality", "AQI"),
        hourly.range("pm2_5", EnvironmentalMetricIds.PM2_5_UG_M3, "PM2.5", "µg/m³"),
        hourly.range("pm10", EnvironmentalMetricIds.PM10_UG_M3, "PM10", "µg/m³")
    )
}

private fun JSONObject.range(jsonKey: String, metricId: String, label: String, unit: String): EnvironmentalDailyMetricRange? {
    val array = optJSONArray(jsonKey) ?: return null
    val values = array.finiteValues()
    if (values.isEmpty()) return null
    return EnvironmentalDailyMetricRange(metricId, label, values.minOrNull()!!, values.maxOrNull()!!, values.average(), unit, values.size)
}

private fun JSONArray.finiteValues(): List<Double> = buildList {
    for (i in 0 until length()) {
        if (isNull(i)) continue
        val value = optDouble(i, Double.NaN)
        if (value.isFinite()) add(value)
    }
}

private fun environmentalMetricLabel(id: String): String? = when (id) {
    EnvironmentalMetricIds.TEMPERATURE_C -> "Temperature"
    EnvironmentalMetricIds.FEELS_LIKE_C -> "Feels like"
    EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT -> "Humidity"
    EnvironmentalMetricIds.PRECIPITATION_MM -> "Precipitation"
    EnvironmentalMetricIds.SURFACE_PRESSURE_HPA -> "Pressure"
    EnvironmentalMetricIds.WIND_SPEED_MPS -> "Wind"
    EnvironmentalMetricIds.WIND_GUST_MPS -> "Wind gusts"
    EnvironmentalMetricIds.CLOUD_COVER_PCT -> "Cloud cover"
    EnvironmentalMetricIds.UV_INDEX -> "UV index"
    EnvironmentalMetricIds.EUROPEAN_AQI -> "Air quality"
    EnvironmentalMetricIds.PM2_5_UG_M3 -> "PM2.5"
    EnvironmentalMetricIds.PM10_UG_M3 -> "PM10"
    else -> null
}

private fun environmentalMetricOrder(id: String): Int = when (id) {
    EnvironmentalMetricIds.TEMPERATURE_C -> 0
    EnvironmentalMetricIds.FEELS_LIKE_C -> 1
    EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT -> 2
    EnvironmentalMetricIds.PRECIPITATION_MM -> 3
    EnvironmentalMetricIds.WIND_SPEED_MPS -> 4
    EnvironmentalMetricIds.WIND_GUST_MPS -> 5
    EnvironmentalMetricIds.SURFACE_PRESSURE_HPA -> 6
    EnvironmentalMetricIds.CLOUD_COVER_PCT -> 7
    EnvironmentalMetricIds.UV_INDEX -> 8
    EnvironmentalMetricIds.EUROPEAN_AQI -> 9
    EnvironmentalMetricIds.PM2_5_UG_M3 -> 10
    EnvironmentalMetricIds.PM10_UG_M3 -> 11
    else -> 99
}

private fun dateTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
}

private fun formatMetric(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
