package com.projectsuperhuman.next

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CardioLiveMovementMetrics(
    val gpsQuality: CardioGpsQualityLabel = CardioGpsQualityLabel.UNAVAILABLE,
    val distanceMeters: Double = 0.0,
    val movingTimeMs: Long = 0L,
    val currentSpeedMetersPerSecond: Double? = null,
    val currentPaceSecondsPerKm: Int? = null,
    val movingPaceSecondsPerKm: Int? = null,
    val elevationGainMeters: Double? = null,
    val lastFixAgeMs: Long? = null,
    val rawFixCount: Int = 0,
    val acceptedFixCount: Int = 0,
    val autoPaused: Boolean = false,
    val laps: List<CardioLap> = emptyList(),
    val structuredProgress: CardioStructuredProgress? = null,
    val message: String = "Location unavailable"
)

internal data class CardioLiveTelemetrySnapshot(
    val sessionId: String,
    val route: CardioRouteSummary,
    val laps: List<CardioLap>,
    val pauseEvents: List<CardioPauseEvent>,
    val structuredProgress: CardioStructuredProgress?
)

internal object CardioGpsRuntime {
    private val _metrics = MutableStateFlow(CardioLiveMovementMetrics())
    val metrics: StateFlow<CardioLiveMovementMetrics> = _metrics.asStateFlow()

    private val _autoPauseDecisions = MutableSharedFlow<CardioAutoPauseDecision>(extraBufferCapacity = 16)
    val autoPauseDecisions: SharedFlow<CardioAutoPauseDecision> = _autoPauseDecisions.asSharedFlow()

    private var appContext: Context? = null
    private var locationManager: LocationManager? = null
    private var activeSessionId: String? = null
    private var activity: CardioActivityType = CardioActivityType.GENERAL_CARDIO
    private var startedAtEpochMs: Long = 0L
    private var recording = false
    private var manuallyPaused = false
    private var locationUpdatesActive = false
    private val rawFixes = mutableListOf<CardioGpsFix>()
    private val activeFixes = mutableListOf<CardioGpsFix>()
    private var lapTracker: CardioLiveLapTracker? = null
    private var autoPauseConfig = CardioAutoPauseConfig.forActivity(CardioActivityType.GENERAL_CARDIO, false)
    private var autoPauseEngine = CardioAutoPauseEngine(autoPauseConfig)
    private val pauseEvents = mutableListOf<CardioPauseEvent>()
    private var openPauseIndex: Int? = null
    private var structuredState: CardioStructuredExecutionState? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = System.currentTimeMillis()
            val fix = CardioGpsFix(
                timestampEpochMs = location.time.takeIf { it > 0L } ?: now,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY,
                altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
                speedMetersPerSecond = location.speed.toDouble().takeIf { location.hasSpeed() },
                receivedAtEpochMs = now
            )
            acceptFix(fix)
        }
        override fun onProviderEnabled(provider: String) = refresh()
        override fun onProviderDisabled(provider: String) = refresh(messageOverride = "Location unavailable")
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        locationManager = appContext?.getSystemService(LocationManager::class.java)
    }

    fun requiredPermissions(): Array<String> = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasActiveSession(sessionId: String? = null): Boolean =
        activeSessionId != null && (sessionId == null || activeSessionId == sessionId)

    fun hasPermission(): Boolean {
        val context = appContext ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun startSession(
        sessionId: String,
        activity: CardioActivityType,
        startedAtEpochMs: Long,
        autoPauseEnabled: Boolean = false,
        autoLapMeters: Double? = defaultAutoLapMeters(activity)
    ) {
        activeSessionId = sessionId
        this.activity = activity
        this.startedAtEpochMs = startedAtEpochMs
        recording = true
        manuallyPaused = false
        rawFixes.clear()
        activeFixes.clear()
        pauseEvents.clear()
        openPauseIndex = null
        autoPauseConfig = CardioAutoPauseConfig.forActivity(activity, autoPauseEnabled)
        autoPauseEngine = CardioAutoPauseEngine(autoPauseConfig)
        lapTracker = CardioLiveLapTracker(sessionId).also { it.start(startedAtEpochMs, autoLapMeters) }
        structuredState = null
        startLocationUpdates()
        refresh()
    }

    fun restoreSession(
        sessionId: String,
        activity: CardioActivityType,
        startedAtEpochMs: Long,
        paused: Boolean
    ) {
        if (activeSessionId == sessionId) return
        startSession(sessionId, activity, startedAtEpochMs)
        if (paused) pause(manual = true)
    }

    fun pause(atEpochMs: Long = System.currentTimeMillis(), manual: Boolean = true) {
        if (activeSessionId == null) return
        recording = false
        manuallyPaused = manual
        if (openPauseIndex == null) {
            pauseEvents += CardioPauseEvent(
                atEpochMs,
                null,
                if (manual) CardioPauseOrigin.MANUAL else CardioPauseOrigin.AUTO
            )
            openPauseIndex = pauseEvents.lastIndex
        }
        refresh()
    }

    fun resume(atEpochMs: Long = System.currentTimeMillis(), manual: Boolean = true) {
        if (activeSessionId == null) return
        recording = true
        manuallyPaused = false
        val index = openPauseIndex
        if (index != null) {
            val existing = pauseEvents.getOrNull(index)
            if (existing != null) pauseEvents[index] = existing.copy(endedAtEpochMs = atEpochMs)
            openPauseIndex = null
        }
        refresh()
    }

    fun stop(endedAtEpochMs: Long = System.currentTimeMillis()): CardioLiveTelemetrySnapshot? {
        val id = activeSessionId ?: return null
        val index = openPauseIndex
        if (index != null) {
            val existing = pauseEvents.getOrNull(index)
            if (existing != null) pauseEvents[index] = existing.copy(endedAtEpochMs = endedAtEpochMs)
            openPauseIndex = null
        }
        recording = false
        stopLocationUpdates()
        val route = CardioGpsProcessor.summarise(activeFixes, activity, endedAtEpochMs).copy(rawFixes = rawFixes.toList())
        val snapshot = CardioLiveTelemetrySnapshot(
            sessionId = id,
            route = route,
            laps = lapTracker?.all().orEmpty(),
            pauseEvents = pauseEvents.toList(),
            structuredProgress = structuredState?.let {
                CardioStructuredWorkoutEngine.update(
                    it,
                    endedAtEpochMs,
                    route.distanceMeters,
                    CardioSensorRuntime.liveMetrics.value.currentZone,
                    route.currentPaceSecondsPerKm
                )
            }
        )
        activeSessionId = null
        refresh(route = route)
        return snapshot
    }

    fun snapshot(nowEpochMs: Long = System.currentTimeMillis()): CardioLiveTelemetrySnapshot? {
        val id = activeSessionId ?: return null
        val route = CardioGpsProcessor.summarise(activeFixes, activity, nowEpochMs).copy(rawFixes = rawFixes.toList())
        return CardioLiveTelemetrySnapshot(
            sessionId = id,
            route = route,
            laps = lapTracker?.all().orEmpty(),
            pauseEvents = pauseEvents.toList(),
            structuredProgress = structuredState?.let {
                CardioStructuredWorkoutEngine.update(
                    it,
                    nowEpochMs,
                    route.distanceMeters,
                    CardioSensorRuntime.liveMetrics.value.currentZone,
                    route.currentPaceSecondsPerKm
                )
            }
        )
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        autoPauseConfig = CardioAutoPauseConfig.forActivity(activity, enabled)
        autoPauseEngine = CardioAutoPauseEngine(autoPauseConfig)
        refresh()
    }

    fun setStructuredWorkout(workout: CardioStructuredWorkout?) {
        structuredState = if (workout == null || activeSessionId == null) null else {
            val route = CardioGpsProcessor.summarise(activeFixes, activity).copy(rawFixes = rawFixes.toList())
            CardioStructuredWorkoutEngine.start(workout, System.currentTimeMillis(), route.distanceMeters)
        }
        refresh()
    }

    fun tick(nowEpochMs: Long = System.currentTimeMillis()) {
        if (activeSessionId == null) return
        if (
            CardioGpsProcessor.gpsEligible(activity) &&
            hasPermission() &&
            !locationUpdatesActive
        ) {
            startLocationUpdates()
        }
        val route = activeRouteSummary(nowEpochMs)
        structuredState?.let { state ->
            structuredState = CardioStructuredWorkoutEngine.update(
                state,
                nowEpochMs,
                route.distanceMeters,
                CardioSensorRuntime.liveMetrics.value.currentZone,
                route.currentPaceSecondsPerKm
            ).state
        }
        refresh(route = route)
    }

    fun manualLap(nowEpochMs: Long = System.currentTimeMillis()): CardioLap? {
        val route = CardioGpsProcessor.summarise(activeFixes, activity, nowEpochMs).copy(rawFixes = rawFixes.toList())
        val hr = CardioSensorRuntime.liveMetrics.value
        val lap = lapTracker?.manualLap(
            nowEpochMs = nowEpochMs,
            totalDistanceMeters = route.distanceMeters,
            avgHeartRate = hr.averageHeartRateBpm,
            maxHeartRate = hr.maxHeartRateBpm
        )
        refresh(route = route)
        return lap
    }

    fun rawFixes(): List<CardioGpsFix> = rawFixes.toList()

    fun enrichSession(
        session: CardioSession,
        snapshot: CardioLiveTelemetrySnapshot? = snapshot(session.endedAt)
    ): CardioSession {
        val route = snapshot?.route ?: return session
        val durationSeconds = session.durationSeconds.coerceAtLeast(1)
        val distanceKm = route.distanceKm.takeIf { it > 0.0 }
        val avgPace = if (
            distanceKm != null &&
            (session.activity == CardioActivityType.RUNNING ||
                session.activity == CardioActivityType.WALKING ||
                session.activity == CardioActivityType.HIKING)
        ) (durationSeconds / distanceKm).roundToInt() else null
        val avgSpeed = if (distanceKm != null) distanceKm / (durationSeconds / 3600.0) else null
        return session.copy(
            distanceKm = session.distanceKm ?: distanceKm,
            avgPaceSecPerKm = session.avgPaceSecPerKm ?: avgPace,
            avgSpeedKmh = session.avgSpeedKmh ?: avgSpeed,
            elevationGainM = session.elevationGainM ?: route.elevationGainMeters,
            extensions = session.extensions + buildMap {
                put("gpsRawFixCount", route.rawFixes.size.toString())
                put("gpsAcceptedFixCount", route.cleanedRoute.size.toString())
                put("gpsDistanceMeters", "%.3f".format(java.util.Locale.US, route.distanceMeters))
                put("movingTimeMs", route.movingTimeMs.toString())
                put("gpsQuality", route.gpsQuality.name)
                snapshot?.laps?.size?.let { put("lapCount", it.toString()) }
                if (snapshot?.pauseEvents?.isNotEmpty() == true) {
                    put("pauseEventCount", snapshot.pauseEvents.size.toString())
                    put(
                        "autoPauseEventCount",
                        snapshot.pauseEvents.count { it.origin == CardioPauseOrigin.AUTO }.toString()
                    )
                    put(
                        "manualPauseEventCount",
                        snapshot.pauseEvents.count { it.origin == CardioPauseOrigin.MANUAL }.toString()
                    )
                }
                snapshot?.structuredProgress?.state?.let { structured ->
                    put("structuredWorkoutId", structured.workoutId)
                    put("structuredStepIndex", structured.stepIndex.toString())
                    put("structuredStepCount", structured.flattenedSteps.size.toString())
                    put("structuredWorkoutCompleted", structured.completed.toString())
                }
            }
        )
    }

    @Synchronized
    internal fun acceptFix(fix: CardioGpsFix) {
        if (activeSessionId == null) return
        // Preserve raw GPS while the session owns the location stream, including pauses. Route
        // analytics only append distance during recording, preventing stopped time from becoming
        // movement distance while still retaining the underlying evidence.
        rawFixes += fix
        if (recording) activeFixes += fix
        val route = activeRouteSummary(fix.receivedAtEpochMs)
        val decisionSpeed = fix.speedMetersPerSecond
            ?: CardioGpsProcessor.summarise(rawFixes.takeLast(3), activity, fix.receivedAtEpochMs)
                .currentSpeedMetersPerSecond
        when (autoPauseEngine.observe(decisionSpeed, fix.receivedAtEpochMs, manuallyPaused)) {
            CardioAutoPauseDecision.PAUSE -> _autoPauseDecisions.tryEmit(CardioAutoPauseDecision.PAUSE)
            CardioAutoPauseDecision.RESUME -> _autoPauseDecisions.tryEmit(CardioAutoPauseDecision.RESUME)
            CardioAutoPauseDecision.NONE -> Unit
        }
        if (recording) {
            val hr = CardioSensorRuntime.liveMetrics.value
            lapTracker?.onDistance(
                nowEpochMs = fix.timestampEpochMs,
                totalDistanceMeters = route.distanceMeters,
                avgHeartRate = hr.averageHeartRateBpm,
                maxHeartRate = hr.maxHeartRateBpm
            )
        }
        structuredState?.let { state ->
            val progress = CardioStructuredWorkoutEngine.update(
                state,
                fix.receivedAtEpochMs,
                route.distanceMeters,
                CardioSensorRuntime.liveMetrics.value.currentZone,
                route.currentPaceSecondsPerKm
            )
            structuredState = progress.state
        }
        refresh(route = route)
    }

    private fun activeRouteSummary(nowEpochMs: Long): CardioRouteSummary =
        CardioGpsProcessor.summarise(activeFixes, activity, nowEpochMs)
            .copy(rawFixes = rawFixes.toList())

    private fun refresh(
        route: CardioRouteSummary = activeRouteSummary(System.currentTimeMillis()),
        messageOverride: String? = null
    ) {
        val progress = structuredState?.let {
            CardioStructuredWorkoutEngine.update(
                it,
                System.currentTimeMillis(),
                route.distanceMeters,
                CardioSensorRuntime.liveMetrics.value.currentZone,
                route.currentPaceSecondsPerKm
            )
        }
        _metrics.value = CardioLiveMovementMetrics(
            gpsQuality = route.gpsQuality,
            distanceMeters = route.distanceMeters,
            movingTimeMs = route.movingTimeMs,
            currentSpeedMetersPerSecond = route.currentSpeedMetersPerSecond,
            currentPaceSecondsPerKm = route.currentPaceSecondsPerKm,
            movingPaceSecondsPerKm = route.movingPaceSecondsPerKm,
            elevationGainMeters = route.elevationGainMeters,
            lastFixAgeMs = route.lastFixAgeMs,
            rawFixCount = rawFixes.size,
            acceptedFixCount = route.cleanedRoute.size,
            autoPaused = autoPauseEngine.isAutoPaused(),
            laps = lapTracker?.all().orEmpty(),
            structuredProgress = progress,
            message = messageOverride ?: when (route.gpsQuality) {
                CardioGpsQualityLabel.GOOD -> "GPS good"
                CardioGpsQualityLabel.WEAK -> "GPS weak"
                CardioGpsQualityLabel.UNAVAILABLE -> if (!hasPermission()) "Location permission required" else "Location unavailable"
            }
        )
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        if (locationUpdatesActive) return
        if (!CardioGpsProcessor.gpsEligible(activity) || !hasPermission()) {
            refresh()
            return
        }
        val manager = locationManager ?: return
        runCatching {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1_000L,
                0f,
                listener,
                Looper.getMainLooper()
            )
            locationUpdatesActive = true
        }.onFailure {
            locationUpdatesActive = false
            refresh(messageOverride = "Location unavailable")
        }
    }

    private fun stopLocationUpdates() {
        runCatching { locationManager?.removeUpdates(listener) }
        locationUpdatesActive = false
    }

    private fun defaultAutoLapMeters(activity: CardioActivityType): Double? = when (activity) {
        CardioActivityType.RUNNING,
        CardioActivityType.WALKING,
        CardioActivityType.HIKING -> 1_000.0
        CardioActivityType.CYCLING -> 5_000.0
        else -> null
    }
}
