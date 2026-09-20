package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue

internal class CardioNof1Repository(
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) {
    suspend fun persistHeartRateSamples(
        sessionId: String,
        samples: List<CardioHeartRateSample>
    ): CardioWriteResult {
        if (samples.isEmpty()) return CardioWriteResult(true, "No heart-rate samples to persist")
        val quality = CardioHrQualityProcessor.analyse(samples)
        val rows = quality.points.mapIndexed { index, point ->
            val sample = point.sample
            val provenance = CardioProvenanceCodec.fromSensor(sample.source)
            val raw = CardioRawObservation(
                sessionId = sessionId,
                metricId = "cardio_hr_sample_bpm",
                originalTimestampEpochMs = sample.timestampEpochMs,
                originalValue = sample.bpm.toDouble(),
                canonicalValue = sample.bpm.toDouble(),
                unit = "bpm",
                ingestionTimestampEpochMs = sample.importedAtEpochMs ?: sample.receivedAtEpochMs,
                provenance = provenance,
                quality = point.quality,
                exclusionReason = point.reason
            )
            val health = raw.toHealthValue()
            health.copy(
                metadata = health.metadata + buildMap {
                    point.reason?.let { put("qualityReason", it) }
                    put("receivedAtEpochMs", sample.receivedAtEpochMs.toString())
                    sample.importedAtEpochMs?.let { put("importedAtEpochMs", it.toString()) }
                    put("timeBasis", sample.timeBasis.name)
                    put("sampleOrdinal", index.toString())
                    putAll(sample.source.toMetadata("sensor"))
                }
            )
        }
        return ingest(rows, "heart-rate samples")
    }

    suspend fun persistRrIntervals(
        sessionId: String,
        samples: List<CardioRrIntervalSample>,
        activeDurationMs: Long = samples.sumOf { it.rrMs.toLong().coerceAtLeast(0L) }
    ): CardioWriteResult {
        if (samples.isEmpty()) return CardioWriteResult(true, "No RR intervals to persist")
        val quality = CardioRrProcessor.analyse(samples, activeDurationMs)
        val rows = quality.points.mapIndexed { index, point ->
            val sample = point.sample
            val provenance = CardioProvenanceCodec.fromSensor(sample.source)
            val raw = CardioRawObservation(
                sessionId = sessionId,
                metricId = "cardio_rr_interval_ms",
                originalTimestampEpochMs = sample.timestampEpochMs,
                originalValue = sample.rrMs,
                canonicalValue = sample.rrMs,
                unit = "ms",
                ingestionTimestampEpochMs = sample.receivedAtEpochMs,
                provenance = provenance,
                quality = point.quality,
                exclusionReason = point.reason
            )
            val health = raw.toHealthValue()
            health.copy(
                metadata = health.metadata + buildMap {
                    point.reason?.let { put("qualityReason", it) }
                    put("receivedAtEpochMs", sample.receivedAtEpochMs.toString())
                    put("correctionApplied", point.correctionApplied.toString())
                    put("sampleOrdinal", index.toString())
                    putAll(sample.source.toMetadata("sensor"))
                }
            )
        }
        return ingest(rows, "RR intervals")
    }

    suspend fun persistGpsFixes(
        sessionId: String,
        route: CardioRouteSummary
    ): CardioWriteResult {
        if (route.rawFixes.isEmpty()) return CardioWriteResult(true, "No GPS fixes to persist")
        val qualityByFix = route.qualityPoints.associateBy { it.fix }
        val rows = route.rawFixes.mapIndexed { index, fix ->
            val point = qualityByFix[fix]
            HealthValue(
                domain = HealthDomain.EXERCISE,
                metric = "cardio_gps_fix",
                value = 1.0,
                unit = "count",
                timestampEpochMs = fix.timestampEpochMs,
                source = "phone-gps",
                metadata = buildMap {
                    put("sourceRecordId", "cardio-gps:" + sessionId + ":" + fix.timestampEpochMs + ":" + index)
                    put("sessionId", sessionId)
                    put("valueClass", CardioValueClass.MEASURED.name)
                    put("quality", (point?.quality ?: CardioObservationQuality.FILTERED).name)
                    point?.reason?.let { put("qualityReason", it) }
                    put("latitude", fix.latitude.toString())
                    put("longitude", fix.longitude.toString())
                    put("accuracyMeters", fix.accuracyMeters.toString())
                    put("receivedAtEpochMs", fix.receivedAtEpochMs.toString())
                    put("sourceKind", fix.source.name)
                    fix.altitudeMeters?.let { put("altitudeMeters", it.toString()) }
                    fix.speedMetersPerSecond?.let { put("speedMetersPerSecond", it.toString()) }
                    put("algorithmVersion", CARDIO_NOF1_ALGORITHM_VERSION)
                }
            )
        }
        return ingest(rows, "GPS fixes")
    }

    suspend fun persistLaps(
        sessionId: String,
        laps: List<CardioLap>
    ): CardioWriteResult {
        if (laps.isEmpty()) return CardioWriteResult(true, "No laps to persist")
        val rows = laps.map { lap ->
            HealthValue(
                domain = HealthDomain.EXERCISE,
                metric = "cardio_lap_distance_m",
                value = lap.distanceMeters,
                unit = "m",
                timestampEpochMs = lap.endedAt,
                source = "cardio-live",
                metadata = buildMap {
                    put("sourceRecordId", "cardio-lap:" + sessionId + ":" + lap.index)
                    put("sessionId", sessionId)
                    put("lapIndex", lap.index.toString())
                    put("startedAt", lap.startedAt.toString())
                    put("endedAt", lap.endedAt.toString())
                    put("durationSeconds", lap.durationSeconds.toString())
                    put("exactDistance", lap.exactDistance.toString())
                    put("lapSource", lap.source.name)
                    put("valueClass", CardioValueClass.MEASURED.name)
                    lap.avgHeartRate?.let { put("avgHeartRate", it.toString()) }
                    lap.maxHeartRate?.let { put("maxHeartRate", it.toString()) }
                    lap.paceSecondsPerKm?.let { put("paceSecondsPerKm", it.toString()) }
                    lap.speedKmh?.let { put("speedKmh", it.toString()) }
                    put("algorithmVersion", CARDIO_NOF1_ALGORITHM_VERSION)
                }
            )
        }
        return ingest(rows, "laps")
    }

    suspend fun persistPauseEvents(
        sessionId: String,
        events: List<CardioPauseEvent>
    ): CardioWriteResult {
        val completed = events.filter { it.endedAtEpochMs != null && it.endedAtEpochMs > it.startedAtEpochMs }
        if (completed.isEmpty()) return CardioWriteResult(true, "No pause events to persist")
        val rows = completed.mapIndexed { index, event ->
            val ended = requireNotNull(event.endedAtEpochMs)
            HealthValue(
                domain = HealthDomain.EXERCISE,
                metric = "cardio_pause_duration_s",
                value = (ended - event.startedAtEpochMs) / 1000.0,
                unit = "s",
                timestampEpochMs = ended,
                source = "cardio-live",
                metadata = mapOf(
                    "sourceRecordId" to "cardio-pause:" + sessionId + ":" + index,
                    "sessionId" to sessionId,
                    "startedAt" to event.startedAtEpochMs.toString(),
                    "endedAt" to ended.toString(),
                    "pauseOrigin" to event.origin.name,
                    "valueClass" to CardioValueClass.MEASURED.name,
                    "algorithmVersion" to CARDIO_NOF1_ALGORITHM_VERSION
                )
            )
        }
        return ingest(rows, "pause events")
    }

    suspend fun persistLiveTelemetry(
        session: CardioSession,
        heartRateSamples: List<CardioHeartRateSample>,
        rrIntervals: List<CardioRrIntervalSample>,
        telemetry: CardioLiveTelemetrySnapshot?
    ): CardioTelemetryPersistenceResult {
        val results = mutableListOf<CardioWriteResult>()
        results += persistHeartRateSamples(session.id, heartRateSamples)
        results += persistRrIntervals(
            session.id,
            rrIntervals,
            activeDurationMs = session.durationSeconds.coerceAtLeast(0) * 1000L
        )
        telemetry?.let {
            results += persistGpsFixes(session.id, it.route)
            results += persistLaps(session.id, it.laps)
            results += persistPauseEvents(session.id, it.pauseEvents)
        }
        val rr = CardioRrProcessor.analyse(
            rrIntervals,
            session.durationSeconds.coerceAtLeast(0) * 1000L
        )
        rr.rmssdMetric?.let { metric ->
            results += publishDerived(session.id, metric, session.endedAt)
        }
        return CardioTelemetryPersistenceResult(results)
    }

    suspend fun publishDerived(
        sessionId: String?,
        metric: CardioDerivedMetric,
        timestampEpochMs: Long = nowEpochMs()
    ): CardioWriteResult {
        val row = CardioDerivedEvidenceCodec.toHealthValue(
            sessionId = sessionId,
            metric = metric.copy(generatedAtEpochMs = metric.generatedAtEpochMs ?: timestampEpochMs),
            effectiveTimestampEpochMs = timestampEpochMs
        ) ?: return CardioWriteResult(false, "Derived metric is unavailable")
        return ingest(listOf(row), metric.metricId)
    }

    suspend fun publishRecomputed(report: CardioRecomputeReport): CardioWriteResult {
        val rows = report.outputs.mapNotNull { output ->
            CardioDerivedEvidenceCodec.toHealthValue(
                sessionId = output.sessionId,
                metric = output.metric,
                effectiveTimestampEpochMs = output.effectiveTimestampEpochMs
            )
        }
        if (rows.isEmpty()) return CardioWriteResult(true, "No recomputed metrics to persist")
        return ingest(rows, "recomputed cardio metrics")
    }

    suspend fun publishOverview(
        snapshot: CardioNof1OverviewSnapshot,
        latestLoad: CardioTrainingLoadPoint?
    ) {
        snapshot.fitness.trendDeltaPercent?.let { delta ->
            publishDerived(
                sessionId = null,
                metric = CardioDerivedMetric(
                    metricId = "cardio_fitness_efficiency_delta_pct",
                    value = delta,
                    unit = "%",
                    valueClass = CardioValueClass.DERIVED,
                    confidence = snapshot.fitness.confidence,
                    algorithmVersion = CARDIO_NOF1_ALGORITHM_VERSION,
                    requiredInputs = listOf("avgPaceSecPerKm", "avgHeartRate", "comparable sessions"),
                    caveat = snapshot.fitness.basis
                ),
                timestampEpochMs = snapshot.generatedAtEpochMs
            )
        }

        snapshot.readiness.score?.let { score ->
            publishDerived(
                sessionId = null,
                metric = CardioDerivedMetric(
                    metricId = "cardio_training_readiness_score",
                    value = score,
                    unit = "score",
                    valueClass = CardioValueClass.DERIVED,
                    confidence = snapshot.readiness.confidence,
                    algorithmVersion = CARDIO_NOF1_ALGORITHM_VERSION,
                    requiredInputs = listOf("available recovery signals", "personal baselines"),
                    caveat = snapshot.readiness.disclaimer
                ),
                timestampEpochMs = snapshot.generatedAtEpochMs
            )
        }

        latestLoad?.let { load ->
            publishDerived(
                null,
                CardioDerivedMetric(
                    metricId = "cardio_chronic_training_load",
                    value = load.chronicLoad,
                    unit = "load",
                    valueClass = CardioValueClass.DERIVED,
                    confidence = CardioConfidence.MODERATE,
                    algorithmVersion = CARDIO_NOF1_ALGORITHM_VERSION,
                    requiredInputs = listOf("session training load"),
                    caveat = "42-day exponentially weighted load."
                ),
                snapshot.generatedAtEpochMs
            )
            publishDerived(
                null,
                CardioDerivedMetric(
                    metricId = "cardio_acute_training_load",
                    value = load.acuteLoad,
                    unit = "load",
                    valueClass = CardioValueClass.DERIVED,
                    confidence = CardioConfidence.MODERATE,
                    algorithmVersion = CARDIO_NOF1_ALGORITHM_VERSION,
                    requiredInputs = listOf("session training load"),
                    caveat = "7-day exponentially weighted load."
                ),
                snapshot.generatedAtEpochMs
            )
            publishDerived(
                null,
                CardioDerivedMetric(
                    metricId = "cardio_training_stress_balance",
                    value = load.trainingStressBalance,
                    unit = "load",
                    valueClass = CardioValueClass.DERIVED,
                    confidence = CardioConfidence.MODERATE,
                    algorithmVersion = CARDIO_NOF1_ALGORITHM_VERSION,
                    requiredInputs = listOf("chronic load", "acute load"),
                    caveat = "Previous-day chronic load minus acute load."
                ),
                snapshot.generatedAtEpochMs
            )
        }
    }

    suspend fun loadRecoveryContext(
        latestLoad: CardioTrainingLoadPoint?,
        baselineDays: Long = 28
    ): CardioRecoveryContext {
        val now = nowEpochMs()
        val baselineStart = now - baselineDays * 86_400_000L
        val exercise = NativeDomainData.forDomain(HealthDomain.EXERCISE)
        val sleep = NativeDomainData.forDomain(HealthDomain.SLEEP)
        val body = NativeDomainData.forDomain(HealthDomain.BODY)
        val mindfulness = NativeDomainData.forDomain(HealthDomain.MINDFULNESS)
        val environment = NativeDomainData.forDomain(HealthDomain.ENVIRONMENT)

        val resting = exercise.latest("resting_heart_rate_bpm")?.value
        val restingRows = exercise.between("resting_heart_rate_bpm", baselineStart, now)
            .map { it.value }
            .filter { it.isFinite() }
        val hrv = exercise.latest("heart_rate_variability_rmssd_ms")?.value
        val hrvRows = exercise.between("heart_rate_variability_rmssd_ms", baselineStart, now)
            .map { it.value }
            .filter { it.isFinite() }
        val sleepScoreRow = sleep.latest("sleep_score")
        val sleepDurationRow = sleep.latest("sleep_total_minutes")
        val weightRow = body.latest("body_weight_kg")
        val stressRow = mindfulness.latest("stress_after")
        val temperatureRow = environment.latest("environment_temperature_c")
        val humidityRow = environment.latest("environment_relative_humidity_pct")
        val environmentTimestamp = listOfNotNull(
            temperatureRow?.timestampEpochMs,
            humidityRow?.timestampEpochMs
        ).maxOrNull()

        return CardioRecoveryContext(
            restingHeartRateBpm = resting,
            restingHeartRateBaselineBpm = restingRows.takeIf { it.size >= 5 }?.average(),
            hrvRmssdMs = hrv,
            hrvBaselineRmssdMs = hrvRows.takeIf { it.size >= 5 }?.average(),
            sleepScore = sleepScoreRow?.value,
            trainingStressBalance = latestLoad?.trainingStressBalance,
            sleepDurationMinutes = sleepDurationRow?.value,
            sleepObservedAtEpochMs = listOfNotNull(
                sleepScoreRow?.timestampEpochMs,
                sleepDurationRow?.timestampEpochMs
            ).maxOrNull(),
            bodyWeightKg = weightRow?.value,
            bodyWeightObservedAtEpochMs = weightRow?.timestampEpochMs,
            stressScore0To10 = stressRow?.value,
            stressObservedAtEpochMs = stressRow?.timestampEpochMs,
            environmentTemperatureC = temperatureRow?.value,
            environmentRelativeHumidityPct = humidityRow?.value,
            environmentObservedAtEpochMs = environmentTimestamp
        )
    }

    private suspend fun ingest(rows: List<HealthValue>, label: String): CardioWriteResult = try {
        val result = NativeDataHub.ingestValues(rows)
        CardioWriteResult(
            success = result.rejected == 0,
            message = if (result.rejected == 0) "Persisted " + label else "Some " + label + " were rejected",
            accepted = result.accepted,
            rejected = result.rejected,
            deduplicated = result.deduplicated
        )
    } catch (t: Throwable) {
        CardioWriteResult(false, t.message ?: "Could not persist " + label)
    }


}


internal data class CardioTelemetryPersistenceResult(
    val results: List<CardioWriteResult>
) {
    val success: Boolean get() = results.all { it.success }
    val failedCount: Int get() = results.count { !it.success }
    val accepted: Int get() = results.sumOf { it.accepted }
    val rejected: Int get() = results.sumOf { it.rejected }
}
