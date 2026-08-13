package com.projectsuperhuman.next

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.environment.CachingEnvironmentalRepository
import com.projectsuperhuman.next.environment.EnvironmentalCoordinates
import com.projectsuperhuman.next.environment.EnvironmentalFetchResult
import com.projectsuperhuman.next.environment.EnvironmentalObservation
import com.projectsuperhuman.next.environment.OpenMeteoEnvironmentalProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Privacy-preserving hourly Environmental history recording. */
internal object EnvironmentalAutoRecorder {
    private const val PREFS = "environment_runtime"
    private const val LAT = "coarse_lat"
    private const val LON = "coarse_lon"
    private const val PLACE = "place_label"
    private const val SAMPLE_MS = 60L * 60L * 1000L

    fun rememberLocation(context: Context, coordinates: EnvironmentalCoordinates, placeLabel: String?) {
        val coarse = coordinates.coarsened(2)
        val edit = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LAT, coarse.latitude.toString())
            .putString(LON, coarse.longitude.toString())
        if (!placeLabel.isNullOrBlank()) edit.putString(PLACE, placeLabel.trim())
        edit.apply()
    }

    fun rememberedCoordinates(context: Context): EnvironmentalCoordinates? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = prefs.getString(LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(LON, null)?.toDoubleOrNull() ?: return null
        return EnvironmentalCoordinates(lat, lon).takeIf { it.isValid() }
    }

    fun rememberedPlace(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PLACE, null)?.takeIf { it.isNotBlank() }

    suspend fun recordRememberedLocation(context: Context): Boolean {
        val coordinates = rememberedCoordinates(context) ?: return false
        val repository = CachingEnvironmentalRepository(OpenMeteoEnvironmentalProvider())
        return when (val result = repository.current(coordinates, "local-area", System.currentTimeMillis())) {
            is EnvironmentalFetchResult.Success -> {
                persistObservation(result.observation)
                true
            }
            is EnvironmentalFetchResult.Failure -> false
        }
    }

    suspend fun persistObservation(observation: EnvironmentalObservation) {
        val history = NativeDomainData.forDomain(HealthDomain.ENVIRONMENT)
        val rows = mutableListOf<HealthValue>()
        for (measurement in observation.measurements) {
            val bucketStart = measurement.measurementTimeEpochMs.floorDiv(SAMPLE_MS) * SAMPLE_MS
            val bucketEnd = bucketStart + SAMPLE_MS - 1L
            val exists = history.between(measurement.metricId, bucketStart, bucketEnd)
                .any { it.source.equals(measurement.provenance.providerId, true) }
            if (exists) continue
            rows += HealthValue(
                domain = HealthDomain.ENVIRONMENT,
                metric = measurement.metricId,
                value = measurement.value,
                unit = measurement.unit.symbol,
                timestampEpochMs = measurement.measurementTimeEpochMs,
                source = measurement.provenance.providerId,
                metadata = mapOf(
                    "sourceRecordId" to "env-sampled-v2|${measurement.provenance.providerId}|${measurement.metricId}|$bucketStart",
                    "environment.sampleIntervalMs" to SAMPLE_MS.toString(),
                    "environment.fetchedAtEpochMs" to observation.retrievedAtEpochMs.toString(),
                    "environment.evidenceKind" to "observation",
                    "environment.locationGranularity" to "coarse_grid"
                )
            )
        }
        if (rows.isEmpty()) return
        try {
            NativeDataHub.ingestValues(rows)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Keep live weather usable if archival persistence fails.
        }
    }
}

internal class EnvironmentalBackgroundWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (EnvironmentalAutoRecorder.rememberedCoordinates(applicationContext) == null) return Result.success()
        val success = EnvironmentalAutoRecorder.recordRememberedLocation(applicationContext)
        return when {
            success -> Result.success()
            runAttemptCount < 2 -> Result.retry()
            else -> Result.success()
        }
    }
}

internal object EnvironmentalBackgroundSync {
    private const val UNIQUE_WORK = "project-superhuman-environmental-history"

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val work = PeriodicWorkRequestBuilder<EnvironmentalBackgroundWorker>(
            60, TimeUnit.MINUTES,
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }
}
