package com.projectsuperhuman.next

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

/**
 * Narrow presentation integration seam. No networking, storage or location logic lives here.
 * Until Agent 5's adapter is installed the UI deliberately renders NoData.
 */
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
