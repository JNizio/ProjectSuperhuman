package com.projectsuperhuman.next

import com.projectsuperhuman.next.environment.EnvironmentalMeasurement
import com.projectsuperhuman.next.environment.EnvironmentalMetricIds
import com.projectsuperhuman.next.environment.EnvironmentalObservation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

/**
 * Presentation-only metric categories. Canonical Environmental metric IDs, units, timestamps and
 * provenance remain owned by the Environmental domain and are mapped into this boundary.
 */
internal enum class EnvironmentalMetricKind {
    TEMPERATURE,
    FEELS_LIKE,
    HUMIDITY,
    PRECIPITATION,
    UV,
    AIR_QUALITY,
    DAYLIGHT,
    WIND,
    PRESSURE
}

internal data class EnvironmentalMetricUi(
    val kind: EnvironmentalMetricKind,
    val label: String,
    val value: String,
    val unit: String? = null,
    val supportingText: String? = null
) {
    fun displayValue(): String {
        val cleanValue = value.trim()
        val cleanUnit = unit?.trim().orEmpty()
        if (cleanUnit.isBlank()) return cleanValue
        return if (cleanUnit in setOf("°C", "°F", "%")) "$cleanValue$cleanUnit" else "$cleanValue $cleanUnit"
    }
}

internal data class EnvironmentalConditionsUi(
    val weatherLabel: String? = null,
    val metrics: List<EnvironmentalMetricUi> = emptyList(),
    val locationLabel: String? = null,
    val observedAtLabel: String? = null,
    val retrievedAtLabel: String? = null,
    val providerLabel: String? = null,
    val freshnessLabel: String? = null
) {
    fun hasDisplayableData(): Boolean =
        !weatherLabel.isNullOrBlank() || metrics.any { it.value.isNotBlank() }

    fun metric(kind: EnvironmentalMetricKind): EnvironmentalMetricUi? =
        metrics.firstOrNull { it.kind == kind && it.value.isNotBlank() }

    fun headlineMetric(): EnvironmentalMetricUi? =
        metric(EnvironmentalMetricKind.TEMPERATURE)
            ?: metrics.firstOrNull { it.kind != EnvironmentalMetricKind.FEELS_LIKE && it.value.isNotBlank() }

    fun homeSupportingMetrics(): List<EnvironmentalMetricUi> {
        val headlineKind = headlineMetric()?.kind
        return listOf(
            EnvironmentalMetricKind.HUMIDITY,
            EnvironmentalMetricKind.UV,
            EnvironmentalMetricKind.AIR_QUALITY,
            EnvironmentalMetricKind.PRECIPITATION,
            EnvironmentalMetricKind.DAYLIGHT,
            EnvironmentalMetricKind.WIND,
            EnvironmentalMetricKind.PRESSURE
        ).mapNotNull(::metric).filterNot { it.kind == headlineKind }.take(2)
    }

    fun detailSections(): List<EnvironmentalDetailSectionUi> {
        fun metricsFor(vararg kinds: EnvironmentalMetricKind) = kinds.mapNotNull(::metric)
        return listOf(
            EnvironmentalDetailSectionUi(
                "OUTDOOR EXPOSURE",
                "Useful context when interpreting time spent outside.",
                metricsFor(EnvironmentalMetricKind.UV, EnvironmentalMetricKind.AIR_QUALITY)
            ),
            EnvironmentalDetailSectionUi(
                "ATMOSPHERE",
                "Conditions that can add context to activity and comfort.",
                metricsFor(
                    EnvironmentalMetricKind.HUMIDITY,
                    EnvironmentalMetricKind.PRECIPITATION,
                    EnvironmentalMetricKind.WIND,
                    EnvironmentalMetricKind.PRESSURE
                )
            ),
            EnvironmentalDetailSectionUi(
                "DAYLIGHT",
                "Light-window context for sleep timing and daily routine.",
                metricsFor(EnvironmentalMetricKind.DAYLIGHT)
            )
        ).filter { it.metrics.isNotEmpty() }
    }
}

internal data class EnvironmentalDetailSectionUi(
    val title: String,
    val subtitle: String,
    val metrics: List<EnvironmentalMetricUi>
)

internal sealed interface EnvironmentalLoadResult {
    data class Data(val conditions: EnvironmentalConditionsUi) : EnvironmentalLoadResult
    data object NoData : EnvironmentalLoadResult
    data object NoPermission : EnvironmentalLoadResult
    data class Error(val message: String? = null) : EnvironmentalLoadResult
}

internal fun interface EnvironmentalPresentationSource {
    suspend fun loadCurrent(): EnvironmentalLoadResult
}

internal object EnvironmentalUiRuntime {
    @Volatile
    private var installedSource: EnvironmentalPresentationSource =
        EnvironmentalPresentationSource { EnvironmentalLoadResult.NoData }

    fun installSource(source: EnvironmentalPresentationSource) {
        installedSource = source
    }

    fun source(): EnvironmentalPresentationSource = installedSource
}

internal fun normalizeEnvironmentalResult(result: EnvironmentalLoadResult): EnvironmentalLoadResult =
    when (result) {
        is EnvironmentalLoadResult.Data ->
            if (result.conditions.hasDisplayableData()) result else EnvironmentalLoadResult.NoData
        else -> result
    }

internal object EnvironmentalUiPreviewFixtures {
    val content = EnvironmentalConditionsUi(
        weatherLabel = "Partly cloudy",
        metrics = listOf(
            EnvironmentalMetricUi(EnvironmentalMetricKind.TEMPERATURE, "Temperature", "22", "°C"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.FEELS_LIKE, "Feels like", "21", "°C"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.HUMIDITY, "Humidity", "58", "%"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.UV, "UV", "4", supportingText = "Moderate"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.AIR_QUALITY, "Air quality", "32", "AQI", "Good"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.DAYLIGHT, "Daylight", "14h 37m", supportingText = "Sunset 20:03"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.WIND, "Wind", "12", "km/h"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.PRESSURE, "Pressure", "1017", "hPa")
        ),
        locationLabel = "Local area",
        observedAtLabel = "16:20",
        retrievedAtLabel = "16:24",
        providerLabel = "Preview source",
        freshnessLabel = "4 min old"
    )
}

internal fun EnvironmentalObservation.toEnvironmentalUi(): EnvironmentalConditionsUi {
    val values = buildList {
        addCanonical(this@toEnvironmentalUi, EnvironmentalMetricIds.TEMPERATURE_C, EnvironmentalMetricKind.TEMPERATURE, "Temperature")
        addCanonical(this@toEnvironmentalUi, EnvironmentalMetricIds.FEELS_LIKE_C, EnvironmentalMetricKind.FEELS_LIKE, "Feels like")
        addCanonical(this@toEnvironmentalUi, EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT, EnvironmentalMetricKind.HUMIDITY, "Humidity")
        addCanonical(this@toEnvironmentalUi, EnvironmentalMetricIds.PRECIPITATION_MM, EnvironmentalMetricKind.PRECIPITATION, "Precipitation")
        addCanonical(this@toEnvironmentalUi, EnvironmentalMetricIds.UV_INDEX, EnvironmentalMetricKind.UV, "UV")
        addCanonical(this@toEnvironmentalUi, EnvironmentalMetricIds.EUROPEAN_AQI, EnvironmentalMetricKind.AIR_QUALITY, "Air quality")
        addCanonical(this@toEnvironmentalUi, EnvironmentalMetricIds.WIND_SPEED_MPS, EnvironmentalMetricKind.WIND, "Wind")
        addCanonical(this@toEnvironmentalUi, EnvironmentalMetricIds.SURFACE_PRESSURE_HPA, EnvironmentalMetricKind.PRESSURE, "Pressure")
    }
    val observed = measurements.maxOfOrNull { it.measurementTimeEpochMs }
    val provider = measurements.firstOrNull()?.provenance?.providerId
    return EnvironmentalConditionsUi(
        weatherLabel = condition?.condition?.name?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
        metrics = values,
        locationLabel = "Local area",
        observedAtLabel = observed?.let(::environmentTimeLabel),
        retrievedAtLabel = environmentTimeLabel(retrievedAtEpochMs),
        providerLabel = provider?.let { if (it.equals("open-meteo", true)) "Open-Meteo" else it },
        freshnessLabel = if (freshness.ageSinceRetrievalMs < 60_000L) "Updated now" else "Updated ${freshness.ageSinceRetrievalMs / 60_000L}m ago"
    )
}

private fun MutableList<EnvironmentalMetricUi>.addCanonical(observation: EnvironmentalObservation, id: String, kind: EnvironmentalMetricKind, label: String) {
    observation.measurement(id)?.let { add(it.toPresentationMetric(kind, label)) }
}

private fun EnvironmentalMeasurement.toPresentationMetric(kind: EnvironmentalMetricKind, label: String): EnvironmentalMetricUi {
    val rounded = round(value * 10.0) / 10.0
    val text = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    return EnvironmentalMetricUi(kind, label, text, unit.symbol)
}

private fun environmentTimeLabel(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
