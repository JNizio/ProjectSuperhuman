package com.projectsuperhuman.next

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.projectsuperhuman.next.environment.CachingEnvironmentalRepository
import com.projectsuperhuman.next.environment.EnvironmentalCoordinates
import com.projectsuperhuman.next.environment.EnvironmentalFetchResult
import com.projectsuperhuman.next.environment.EnvironmentalRepository
import com.projectsuperhuman.next.environment.OpenMeteoEnvironmentalProvider
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal fun hasEnvironmentalLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
        val place = resolvePlaceName(location.latitude, location.longitude)
            ?: EnvironmentalAutoRecorder.rememberedPlace(appContext)
            ?: "Local area"
        EnvironmentalAutoRecorder.rememberLocation(appContext, coordinates, place)
        EnvironmentalBackgroundSync.ensureScheduled(appContext)

        return when (val result = repository.current(coordinates, "local-area", System.currentTimeMillis())) {
            is EnvironmentalFetchResult.Success -> {
                EnvironmentalAutoRecorder.persistObservation(result.observation)
                EnvironmentalLoadResult.Data(result.observation.toEnvironmentalUi().copy(locationLabel = place))
            }
            is EnvironmentalFetchResult.Failure -> EnvironmentalLoadResult.Error("Environmental conditions are unavailable right now.")
        }
    }

    private fun hasPermission() = hasEnvironmentalLocationPermission(appContext)

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

    @Suppress("DEPRECATION")
    private suspend fun resolvePlaceName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                try {
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        val address = addresses.firstOrNull()
                        val label = address?.locality ?: address?.subAdminArea ?: address?.adminArea
                        if (continuation.isActive) continuation.resume(label)
                    }
                } catch (_: Throwable) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    address?.locality ?: address?.subAdminArea ?: address?.adminArea
                }.getOrNull()
            }
        }
    }
}
