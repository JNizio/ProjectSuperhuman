package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class CardioCadencePoint(
    val timestampEpochMs: Long,
    val cadencePerMinute: Double
)

internal data class CardioHrQualityPoint(
    val sample: CardioHeartRateSample,
    val quality: CardioObservationQuality,
    val reason: String? = null
)

internal data class CardioHrQualitySummary(
    val points: List<CardioHrQualityPoint>,
    val acceptedCount: Int,
    val invalidCount: Int,
    val suspectCount: Int,
    val gapAdjacentCount: Int,
    val staleCount: Int,
    val cadenceLockSuspectCount: Int,
    val acceptedPercent: Double
)

internal object CardioHrQualityProcessor {
    fun analyse(
        samples: List<CardioHeartRateSample>,
        cadence: List<CardioCadencePoint> = emptyList()
    ): CardioHrQualitySummary {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        var cadenceLockCount = 0
        val points = ordered.mapIndexed { index, sample ->
            val previous = ordered.getOrNull(index - 1)
            val next = ordered.getOrNull(index + 1)
            val age = (sample.receivedAtEpochMs - sample.timestampEpochMs).coerceAtLeast(0L)
            when {
                !sample.isPhysiologicallyStorable ->
                    CardioHrQualityPoint(sample, CardioObservationQuality.INVALID, "Outside storable HR range")
                age > CARDIO_HR_STALE_AFTER_MS ->
                    CardioHrQualityPoint(sample, CardioObservationQuality.STALE, "Delayed/stale sample")
                previous != null && sample.timestampEpochMs - previous.timestampEpochMs > CARDIO_HR_STALE_AFTER_MS ->
                    CardioHrQualityPoint(sample, CardioObservationQuality.GAP_ADJACENT, "Follows an HR data gap")
                isTransientSpike(previous, sample, next) ->
                    CardioHrQualityPoint(sample, CardioObservationQuality.SUSPECT_OUTLIER, "Transient implausible HR jump")
                looksCadenceLocked(sample, cadence) -> {
                    cadenceLockCount += 1
                    CardioHrQualityPoint(sample, CardioObservationQuality.SUSPECT_OUTLIER, "HR closely tracks measured cadence")
                }
                else -> CardioHrQualityPoint(sample, CardioObservationQuality.ACCEPTED)
            }
        }
        val accepted = points.count { it.quality == CardioObservationQuality.ACCEPTED }
        val suspect = points.count { it.quality == CardioObservationQuality.SUSPECT_OUTLIER }
        return CardioHrQualitySummary(
            points = points,
            acceptedCount = accepted,
            invalidCount = points.count { it.quality == CardioObservationQuality.INVALID },
            suspectCount = suspect,
            gapAdjacentCount = points.count { it.quality == CardioObservationQuality.GAP_ADJACENT },
            staleCount = points.count { it.quality == CardioObservationQuality.STALE },
            cadenceLockSuspectCount = cadenceLockCount,
            acceptedPercent = if (points.isEmpty()) 0.0 else accepted * 100.0 / points.size
        )
    }

    private fun isTransientSpike(
        previous: CardioHeartRateSample?,
        current: CardioHeartRateSample,
        next: CardioHeartRateSample?
    ): Boolean {
        if (previous == null || next == null) return false
        val beforeMs = current.timestampEpochMs - previous.timestampEpochMs
        val afterMs = next.timestampEpochMs - current.timestampEpochMs
        if (beforeMs !in 1..5_000L || afterMs !in 1..5_000L) return false
        val jump = abs(current.bpm - previous.bpm)
        val returnDelta = abs(next.bpm - previous.bpm)
        return jump >= 40 && returnDelta <= 18
    }

    private fun looksCadenceLocked(
        sample: CardioHeartRateSample,
        cadence: List<CardioCadencePoint>
    ): Boolean {
        val nearest = cadence.minByOrNull { abs(it.timestampEpochMs - sample.timestampEpochMs) } ?: return false
        if (abs(nearest.timestampEpochMs - sample.timestampEpochMs) > 2_000L) return false
        val cadenceBpm = nearest.cadencePerMinute
        if (cadenceBpm !in 70.0..220.0) return false
        return abs(sample.bpm - cadenceBpm) <= 3.0
    }
}

internal data class CardioRrQualityPoint(
    val sample: CardioRrIntervalSample,
    val quality: CardioObservationQuality,
    val correctionApplied: Boolean,
    val reason: String? = null
)

internal data class CardioRrQualitySummary(
    val points: List<CardioRrQualityPoint>,
    val validBeatPercent: Double,
    val coveragePercent: Double,
    val rmssdMs: Double?,
    val rmssdMetric: CardioDerivedMetric?
)

internal object CardioRrProcessor {
    fun analyse(
        samples: List<CardioRrIntervalSample>,
        activeDurationMs: Long
    ): CardioRrQualitySummary {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val points = ordered.mapIndexed { index, sample ->
            val previous = ordered.getOrNull(index - 1)
            when {
                !sample.isPhysiologicallyStorable ->
                    CardioRrQualityPoint(sample, CardioObservationQuality.INVALID, false, "RR outside 250-2500 ms")
                previous != null && previous.isPhysiologicallyStorable &&
                    abs(sample.rrMs - previous.rrMs) > maxOf(300.0, previous.rrMs * 0.35) ->
                    CardioRrQualityPoint(sample, CardioObservationQuality.SUSPECT_OUTLIER, false, "Abrupt RR change")
                else ->
                    CardioRrQualityPoint(sample, CardioObservationQuality.ACCEPTED, sample.correctionApplied)
            }
        }
        val accepted = points.filter { it.quality == CardioObservationQuality.ACCEPTED }
        val validPct = if (points.isEmpty()) 0.0 else accepted.size * 100.0 / points.size
        val coveredMs = accepted.sumOf { it.sample.rrMs }.coerceAtMost(activeDurationMs.toDouble().coerceAtLeast(0.0))
        val coverage = if (activeDurationMs <= 0L) 0.0 else (coveredMs / activeDurationMs * 100.0).coerceIn(0.0, 100.0)
        val rr = accepted.map { it.sample.rrMs }
        val rmssd = if (rr.size >= 5 && validPct >= 80.0) {
            sqrt(rr.zipWithNext { a, b -> (b - a).pow(2) }.average())
        } else null
        return CardioRrQualitySummary(
            points = points,
            validBeatPercent = validPct,
            coveragePercent = coverage,
            rmssdMs = rmssd,
            rmssdMetric = rmssd?.let {
                CardioDerivedMetric(
                    metricId = "cardio_session_hrv_rmssd_ms",
                    value = it,
                    unit = "ms",
                    valueClass = CardioValueClass.DERIVED,
                    confidence = when {
                        rr.size >= 120 && coverage >= 80.0 -> CardioConfidence.HIGH
                        rr.size >= 30 && coverage >= 50.0 -> CardioConfidence.MODERATE
                        else -> CardioConfidence.LOW
                    },
                    algorithmVersion = CARDIO_NOF1_ALGORITHM_VERSION,
                    requiredInputs = listOf("true RR intervals"),
                    caveat = "Derived from accepted beat-to-beat RR intervals; training context only, not diagnostic."
                )
            }
        )
    }
}

internal data class CardioGpsFix(
    val timestampEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val receivedAtEpochMs: Long = timestampEpochMs,
    val source: CardioSourceKind = CardioSourceKind.PHONE_GPS
)

internal data class CardioGpsQualityPoint(
    val fix: CardioGpsFix,
    val quality: CardioObservationQuality,
    val reason: String? = null
) {
    val accepted: Boolean get() = quality == CardioObservationQuality.ACCEPTED
}

internal enum class CardioGpsQualityLabel {
    GOOD,
    WEAK,
    UNAVAILABLE
}

internal data class CardioRouteSummary(
    val rawFixes: List<CardioGpsFix> = emptyList(),
    val qualityPoints: List<CardioGpsQualityPoint> = emptyList(),
    val cleanedRoute: List<CardioGpsFix> = emptyList(),
    val distanceMeters: Double = 0.0,
    val movingTimeMs: Long = 0L,
    val elevationGainMeters: Double? = null,
    val currentSpeedMetersPerSecond: Double? = null,
    val averageMovingSpeedMetersPerSecond: Double? = null,
    val gpsQuality: CardioGpsQualityLabel = CardioGpsQualityLabel.UNAVAILABLE,
    val lastFixAgeMs: Long? = null
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
    val currentPaceSecondsPerKm: Int?
        get() = currentSpeedMetersPerSecond?.takeIf { it > 0.2 }?.let { (1000.0 / it).roundToInt() }
    val movingPaceSecondsPerKm: Int?
        get() = if (distanceMeters > 0.0 && movingTimeMs > 0L) {
            (movingTimeMs / 1000.0 / (distanceMeters / 1000.0)).roundToInt()
        } else null
}

internal object CardioGpsProcessor {
    fun summarise(
        rawFixes: List<CardioGpsFix>,
        activity: CardioActivityType,
        nowEpochMs: Long = rawFixes.maxOfOrNull { it.receivedAtEpochMs } ?: System.currentTimeMillis()
    ): CardioRouteSummary {
        if (rawFixes.isEmpty()) return CardioRouteSummary()
        val ordered = rawFixes.sortedBy { it.timestampEpochMs }
        val points = ArrayList<CardioGpsQualityPoint>(ordered.size)
        var previousAccepted: CardioGpsFix? = null
        var distance = 0.0
        var movingMs = 0L
        var elevationGain = 0.0
        var altitudeEvidence = false

        ordered.forEach { fix ->
            val previous = previousAccepted
            val point = classify(fix, previous, activity)
            points += point
            if (point.accepted) {
                if (previous != null) {
                    val dtMs = fix.timestampEpochMs - previous.timestampEpochMs
                    if (dtMs in 1..30_000L) {
                        val segment = haversineMeters(previous, fix)
                        val speed = segment / (dtMs / 1000.0)
                        distance += segment
                        if (speed >= movingThresholdMps(activity)) movingMs += dtMs
                        val a = previous.altitudeMeters
                        val b = fix.altitudeMeters
                        if (a != null && b != null) {
                            altitudeEvidence = true
                            val climb = b - a
                            if (climb >= 1.5) elevationGain += climb
                        }
                    }
                }
                previousAccepted = fix
            }
        }
        val accepted = points.filter { it.accepted }.map { it.fix }
        val last = accepted.lastOrNull()
        val lastAge = last?.let { (nowEpochMs - it.receivedAtEpochMs).coerceAtLeast(0L) }
        val currentSpeed = accepted.takeLast(2).let { pair ->
            if (pair.size < 2) null else {
                val dt = pair[1].timestampEpochMs - pair[0].timestampEpochMs
                if (dt !in 1..15_000L) null else haversineMeters(pair[0], pair[1]) / (dt / 1000.0)
            }
        }
        val quality = when {
            last == null || lastAge == null || lastAge > 15_000L -> CardioGpsQualityLabel.UNAVAILABLE
            last.accuracyMeters <= 15f && points.takeLast(5).count { it.accepted } >= 4 -> CardioGpsQualityLabel.GOOD
            else -> CardioGpsQualityLabel.WEAK
        }
        return CardioRouteSummary(
            rawFixes = ordered,
            qualityPoints = points,
            cleanedRoute = accepted,
            distanceMeters = distance,
            movingTimeMs = movingMs,
            elevationGainMeters = elevationGain.takeIf { altitudeEvidence },
            currentSpeedMetersPerSecond = currentSpeed,
            averageMovingSpeedMetersPerSecond = if (movingMs > 0L) distance / (movingMs / 1000.0) else null,
            gpsQuality = quality,
            lastFixAgeMs = lastAge
        )
    }

    private fun classify(
        fix: CardioGpsFix,
        previousAccepted: CardioGpsFix?,
        activity: CardioActivityType
    ): CardioGpsQualityPoint {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite() ||
            fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0
        ) return CardioGpsQualityPoint(fix, CardioObservationQuality.INVALID, "Invalid coordinates")
        if (!fix.accuracyMeters.isFinite() || fix.accuracyMeters <= 0f || fix.accuracyMeters > 60f) {
            return CardioGpsQualityPoint(fix, CardioObservationQuality.FILTERED, "Horizontal accuracy too weak")
        }
        val age = (fix.receivedAtEpochMs - fix.timestampEpochMs).coerceAtLeast(0L)
        if (age > 15_000L) return CardioGpsQualityPoint(fix, CardioObservationQuality.STALE, "Stale location")
        if (previousAccepted != null) {
            val dtMs = fix.timestampEpochMs - previousAccepted.timestampEpochMs
            if (dtMs <= 0L) return CardioGpsQualityPoint(fix, CardioObservationQuality.INVALID, "Non-increasing GPS timestamp")
            if (dtMs <= 30_000L) {
                val derivedSpeed = haversineMeters(previousAccepted, fix) / (dtMs / 1000.0)
                if (derivedSpeed > plausibleMaxSpeedMps(activity)) {
                    return CardioGpsQualityPoint(fix, CardioObservationQuality.SUSPECT_OUTLIER, "Implausible GPS jump/speed")
                }
            }
        }
        return CardioGpsQualityPoint(fix, CardioObservationQuality.ACCEPTED)
    }

    fun haversineMeters(a: CardioGpsFix, b: CardioGpsFix): Double {
        val r = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun gpsEligible(activity: CardioActivityType): Boolean =
        activity == CardioActivityType.RUNNING ||
            activity == CardioActivityType.WALKING ||
            activity == CardioActivityType.HIKING ||
            activity == CardioActivityType.CYCLING

    fun movingThresholdMps(activity: CardioActivityType): Double = when (activity) {
        CardioActivityType.CYCLING -> 1.4
        CardioActivityType.RUNNING -> 0.8
        CardioActivityType.WALKING, CardioActivityType.HIKING -> 0.45
        else -> 0.5
    }

    fun plausibleMaxSpeedMps(activity: CardioActivityType): Double = when (activity) {
        CardioActivityType.CYCLING -> 35.0
        CardioActivityType.RUNNING -> 12.0
        CardioActivityType.WALKING, CardioActivityType.HIKING -> 5.5
        else -> 20.0
    }
}

internal enum class CardioPauseOrigin {
    MANUAL,
    AUTO
}

internal data class CardioPauseEvent(
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val origin: CardioPauseOrigin
)

internal data class CardioAutoPauseConfig(
    val enabled: Boolean = false,
    val pauseBelowMps: Double,
    val resumeAboveMps: Double,
    val pauseDebounceMs: Long = 5_000L,
    val resumeDebounceMs: Long = 3_000L
) {
    companion object {
        fun forActivity(activity: CardioActivityType, enabled: Boolean = false): CardioAutoPauseConfig {
            val pause = CardioGpsProcessor.movingThresholdMps(activity)
            return CardioAutoPauseConfig(enabled, pause, pause * 1.5)
        }
    }
}

internal enum class CardioAutoPauseDecision {
    NONE,
    PAUSE,
    RESUME
}

internal class CardioAutoPauseEngine(
    private val config: CardioAutoPauseConfig
) {
    private var autoPaused = false
    private var belowSince: Long? = null
    private var aboveSince: Long? = null

    fun observe(speedMps: Double?, timestampEpochMs: Long, manuallyPaused: Boolean): CardioAutoPauseDecision {
        if (!config.enabled || speedMps == null || manuallyPaused) {
            belowSince = null
            aboveSince = null
            return CardioAutoPauseDecision.NONE
        }
        if (!autoPaused) {
            aboveSince = null
            if (speedMps <= config.pauseBelowMps) {
                val start = belowSince ?: timestampEpochMs.also { belowSince = it }
                if (timestampEpochMs - start >= config.pauseDebounceMs) {
                    autoPaused = true
                    belowSince = null
                    return CardioAutoPauseDecision.PAUSE
                }
            } else belowSince = null
        } else {
            belowSince = null
            if (speedMps >= config.resumeAboveMps) {
                val start = aboveSince ?: timestampEpochMs.also { aboveSince = it }
                if (timestampEpochMs - start >= config.resumeDebounceMs) {
                    autoPaused = false
                    aboveSince = null
                    return CardioAutoPauseDecision.RESUME
                }
            } else aboveSince = null
        }
        return CardioAutoPauseDecision.NONE
    }

    fun isAutoPaused(): Boolean = autoPaused
}

internal class CardioLiveLapTracker(
    private val sessionId: String,
    private val source: CardioLapProvenance = CardioLapProvenance.GPS_ROUTE
) {
    private val laps = mutableListOf<CardioLap>()
    private var lapStartedAt: Long? = null
    private var lapStartDistanceMeters = 0.0
    private var autoLapDistanceMeters: Double? = null

    fun start(startedAtEpochMs: Long, autoLapMeters: Double? = null) {
        laps.clear()
        lapStartedAt = startedAtEpochMs
        lapStartDistanceMeters = 0.0
        autoLapDistanceMeters = autoLapMeters?.takeIf { it > 0.0 }
    }

    fun manualLap(
        nowEpochMs: Long,
        totalDistanceMeters: Double,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null
    ): CardioLap? = closeLap(nowEpochMs, totalDistanceMeters, avgHeartRate, maxHeartRate, exact = false)

    fun onDistance(
        nowEpochMs: Long,
        totalDistanceMeters: Double,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null
    ): List<CardioLap> {
        val target = autoLapDistanceMeters ?: return emptyList()
        val created = mutableListOf<CardioLap>()
        while (totalDistanceMeters - lapStartDistanceMeters >= target) {
            val fraction = target / (totalDistanceMeters - lapStartDistanceMeters).coerceAtLeast(target)
            val start = lapStartedAt ?: break
            val end = start + ((nowEpochMs - start) * fraction).toLong()
            closeLap(end, lapStartDistanceMeters + target, avgHeartRate, maxHeartRate, exact = true)?.let(created::add)
        }
        return created
    }

    fun all(): List<CardioLap> = laps.toList()

    private fun closeLap(
        nowEpochMs: Long,
        totalDistanceMeters: Double,
        avgHeartRate: Int?,
        maxHeartRate: Int?,
        exact: Boolean
    ): CardioLap? {
        val start = lapStartedAt ?: return null
        if (nowEpochMs <= start) return null
        val distance = (totalDistanceMeters - lapStartDistanceMeters).coerceAtLeast(0.0)
        val lap = CardioLap(
            sessionId = sessionId,
            index = laps.size + 1,
            startedAt = start,
            endedAt = nowEpochMs,
            durationSeconds = (nowEpochMs - start) / 1000.0,
            distanceMeters = distance,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            source = source,
            exactDistance = exact
        )
        laps += lap
        lapStartedAt = nowEpochMs
        lapStartDistanceMeters = totalDistanceMeters
        return lap
    }
}

internal enum class CardioStructuredStepKind {
    WARM_UP,
    WORK,
    RECOVERY,
    COOL_DOWN
}

internal enum class CardioStructuredTargetType {
    OPEN,
    TIME,
    DISTANCE,
    HR_ZONE,
    PACE
}

internal data class CardioStructuredTarget(
    val type: CardioStructuredTargetType,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val hrZone: Int? = null,
    val paceSecondsPerKmMin: Int? = null,
    val paceSecondsPerKmMax: Int? = null
)

internal sealed interface CardioStructuredNode {
    data class Step(
        val id: String,
        val label: String,
        val kind: CardioStructuredStepKind,
        val target: CardioStructuredTarget
    ) : CardioStructuredNode

    data class Repeat(
        val count: Int,
        val nodes: List<CardioStructuredNode>
    ) : CardioStructuredNode
}

internal data class CardioStructuredWorkout(
    val id: String,
    val name: String,
    val nodes: List<CardioStructuredNode>
)

internal data class CardioStructuredExecutionState(
    val workoutId: String,
    val flattenedSteps: List<CardioStructuredNode.Step>,
    val stepIndex: Int,
    val stepStartedAtEpochMs: Long,
    val stepStartedDistanceMeters: Double,
    val completed: Boolean = false
) {
    val currentStep: CardioStructuredNode.Step? get() = flattenedSteps.getOrNull(stepIndex)
    val upcomingStep: CardioStructuredNode.Step? get() = flattenedSteps.getOrNull(stepIndex + 1)
}

internal data class CardioStructuredProgress(
    val state: CardioStructuredExecutionState,
    val remainingSeconds: Int? = null,
    val remainingMeters: Double? = null,
    val targetMet: Boolean? = null
)

internal object CardioStructuredWorkoutEngine {
    fun start(
        workout: CardioStructuredWorkout,
        nowEpochMs: Long,
        totalDistanceMeters: Double
    ): CardioStructuredExecutionState {
        val steps = flatten(workout.nodes)
        require(steps.isNotEmpty()) { "Structured workout needs at least one step" }
        return CardioStructuredExecutionState(
            workoutId = workout.id,
            flattenedSteps = steps,
            stepIndex = 0,
            stepStartedAtEpochMs = nowEpochMs,
            stepStartedDistanceMeters = totalDistanceMeters
        )
    }

    fun update(
        state: CardioStructuredExecutionState,
        nowEpochMs: Long,
        totalDistanceMeters: Double,
        currentHrZone: Int?,
        currentPaceSecondsPerKm: Int?
    ): CardioStructuredProgress {
        if (state.completed) return CardioStructuredProgress(state)
        val step = state.currentStep ?: return CardioStructuredProgress(state.copy(completed = true))
        val elapsedSeconds = ((nowEpochMs - state.stepStartedAtEpochMs).coerceAtLeast(0L) / 1000L).toInt()
        val coveredMeters = (totalDistanceMeters - state.stepStartedDistanceMeters).coerceAtLeast(0.0)
        val target = step.target
        val remainingSeconds = target.durationSeconds?.let { (it - elapsedSeconds).coerceAtLeast(0) }
        val remainingMeters = target.distanceMeters?.let { (it - coveredMeters).coerceAtLeast(0.0) }
        val paceInTarget = if (
            target.paceSecondsPerKmMin != null &&
            target.paceSecondsPerKmMax != null &&
            currentPaceSecondsPerKm != null
        ) currentPaceSecondsPerKm in target.paceSecondsPerKmMin..target.paceSecondsPerKmMax else null
        val targetMet = when (target.type) {
            CardioStructuredTargetType.HR_ZONE -> currentHrZone?.let { it == target.hrZone }
            CardioStructuredTargetType.PACE -> paceInTarget
            else -> null
        }
        val stepComplete = when (target.type) {
            CardioStructuredTargetType.TIME -> target.durationSeconds?.let { elapsedSeconds >= it } == true
            CardioStructuredTargetType.DISTANCE -> target.distanceMeters?.let { coveredMeters >= it } == true
            else -> false
        }
        if (stepComplete) {
            if (state.stepIndex + 1 >= state.flattenedSteps.size) {
                return CardioStructuredProgress(state.copy(completed = true))
            }
            val nextStartedAt = when (target.type) {
                CardioStructuredTargetType.TIME ->
                    state.stepStartedAtEpochMs + (target.durationSeconds ?: 0).coerceAtLeast(0) * 1000L
                else -> nowEpochMs
            }
            val nextStartedDistance = when (target.type) {
                CardioStructuredTargetType.DISTANCE ->
                    state.stepStartedDistanceMeters + (target.distanceMeters ?: 0.0).coerceAtLeast(0.0)
                else -> totalDistanceMeters
            }
            val nextState = state.copy(
                stepIndex = state.stepIndex + 1,
                stepStartedAtEpochMs = nextStartedAt,
                stepStartedDistanceMeters = nextStartedDistance
            )
            // Re-evaluate immediately so long background gaps can advance across more than one
            // completed time/distance step without discarding the elapsed evidence.
            return update(
                nextState,
                nowEpochMs,
                totalDistanceMeters,
                currentHrZone,
                currentPaceSecondsPerKm
            )
        }
        return CardioStructuredProgress(state, remainingSeconds, remainingMeters, targetMet)
    }

    private fun flatten(nodes: List<CardioStructuredNode>): List<CardioStructuredNode.Step> =
        buildList {
            nodes.forEach { node ->
                when (node) {
                    is CardioStructuredNode.Step -> add(node)
                    is CardioStructuredNode.Repeat -> repeat(node.count.coerceAtLeast(0)) { addAll(flatten(node.nodes)) }
                }
            }
        }
}
