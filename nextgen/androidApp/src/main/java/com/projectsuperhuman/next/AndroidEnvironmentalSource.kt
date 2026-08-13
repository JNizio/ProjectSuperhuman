package com.projectsuperhuman.next

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.projectsuperhuman.next.environment.CachingEnvironmentalRepository
import com.projectsuperhuman.next.environment.EnvironmentalCoordinates
import com.projectsuperhuman.next.environment.EnvironmentalFetchResult
import com.projectsuperhuman.next.environment.EnvironmentalMeasurement
import com.projectsuperhuman.next.environment.EnvironmentalMetricIds
import com.projectsuperhuman.next.environment.EnvironmentalObservation
import com.projectsuperhuman.next.environment.EnvironmentalRepository
import com.projectsuperhuman.next.environment.OpenMeteoEnvironmentalProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.round
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AndroidEnvironmentalSource(
    context: Context,
    private val repository: EnvironmentalRepository = CachingEnvironmentalRepository(OpenMeteoEnvironmentalProvider())
) : EnvironmentalPresentationSource {
    private val appContext = context.applicationContext
    private val locations = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override suspend fun loadCurrent(): EnvironmentalLoadResult {
        if (!hasPermission()) return EnvironmentalLoadResult.NoPermission
        val location = currentLocation() ?: return EnvironmentalLoadResult.NoData
        val coordinates = EnvironmentalCoordinates(location.latitude, location.longitude)
        return when (val result = repository.current(coordinates, "local-area", System.currentTimeMillis())) {
            is EnvironmentalFetchResult.Success -> EnvironmentalLoadResult.Data(result.observation.toEnvironmentalUi())
            is EnvironmentalFetchResult.Failure -> EnvironmentalLoadResult.Error("Environmental conditions are unavailable right now.")
        }
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private suspend fun currentLocation(): android.location.Location? {
        if (!hasPermission()) return null
        val provider = when {
            runCatching { locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            runCatching { locations.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
            else -> return bestLastKnown()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return bestLastKnown()
        return suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                locations.getCurrentLocation(provider, cancellation, appContext.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location ?: bestLastKnown())
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun bestLastKnown(): android.location.Location? = runCatching {
        locations.getProviders(true)
            .mapNotNull { provider -> runCatching { locations.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull()
}

internal fun EnvironmentalObservation.toEnvironmentalUi(): EnvironmentalConditionsUi {
    val ui = buildList {
        addMetric(this@toEnvironmentalUi, EnvironmentalMetricIds.TEMPERATURE_C, EnvironmentalMetricKind.TEMPERATURE, "Temperature")
        addMetric(this@toEnvironmentalUi, EnvironmentalMetricIds.FEELS_LIKE_C, EnvironmentalMetricKind.FEELS_LIKE, "Feels like")
        addMetric(this@toEnvironmentalUi, EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT, EnvironmentalMetricKind.HUMIDITY, "Humidity")
        addMetric(this@toEnvironmentalUi, EnvironmentalMetricIds.PRECIPITATION_MM, EnvironmentalMetricKind.PRECIPITATION, "Precipitation")
        addMetric(this@toEnvironmentalUi, EnvironmentalMetricIds.UV_INDEX, EnvironmentalMetricKind.UV, "UV")
        addMetric(this@toEnvironmentalUi, EnvironmentalMetricIds.EUROPEAN_AQI, EnvironmentalMetricKind.AIR_QUALITY, "Air quality")
        addMetric(this@toEnvironmentalUi, EnvironmentalMetricIds.WIND_SPEED_MPS, EnvironmentalMetricKind.WIND, "Wind")
        addMetric(this@toEnvironmentalUi, EnvironmentalMetricIds.SURFACE_PRESSURE_HPA, EnvironmentalMetricKind.PRESSURE, "Pressure")
    }
    val observed = measurements.maxOfOrNull { it.measurementTimeEpochMs }
    val provider = measurements.firstOrNull()?.provenance?.providerId
    return EnvironmentalConditionsUi(
        weatherLabel = condition?.condition?.name?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
        metrics = ui,
        locationLabel = "Local area",
        observedAtLabel = observed?.let(::environmentTime),
        retrievedAtLabel = environmentTime(retrievedAtEpochMs),
        providerLabel = provider?.let { if (it.equals("open-meteo", true)) "Open-Meteo" else it },
        freshnessLabel = if (freshness.ageSinceRetrievalMs < 60_000L) "Updated now" else "Updated ${freshness.ageSinceRetrievalMs / 60_000L}m ago"
    )
}

private fun MutableList<EnvironmentalMetricUi>.addMetric(observation: EnvironmentalObservation, id: String, kind: EnvironmentalMetricKind, label: String) {
    observation.measurement(id)?.let { add(it.toUi(kind, label)) }
}

private fun EnvironmentalMeasurement.toUi(kind: EnvironmentalMetricKind, label: String) =
    EnvironmentalMetricUi(kind, label, environmentValue(value), unit.symbol)

private fun environmentValue(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

private fun environmentTime(epochMs: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
