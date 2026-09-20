package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlin.math.abs

internal class CardioNof1Repository(
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) {
    suspend fun persistHeartRateSamples(
        sessionId: String,
        samples: List<CardioHeartRateSample>
    ): CardioWriteResult {
        if (samples.isEmpty()) return CardioWriteResult(true, "No heart-rate samples to persist")
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val rows = ordered.mapIndexed { index, sample ->
            val quality = classifyHeartRate(sample, ordered.getOrNull(index - 1))
            HealthValue(
                domain = HealthDomain.EXERCISE,
                metric = "cardio_hr_sample_bpm",
                value = sample.bpm.toDouble(),
                unit = "bpm",
                timestampEpochMs = sample.timestampEpochMs,
                source = sample.source.sourceName.ifBlank { "cardio-sensor" },
                metadata = buildMap {
                    put(
                        "sourceRecordId",
                        "cardio-hr:" + sessionId + ":" + sample.timestampEpochMs + ":" + index
                    )
                    put("sessionId", sessionId)
                    put("valueClass", CardioValueClass.MEASURED.name)
                    put("quality", quality.name)
                    put("receivedAtEpochMs", sample.receivedAtEpochMs.toString())
                    sample.importedAtEpochMs?.let { put("importedAtEpochMs", it.toString()) }
                    put("timeBasis", sample.timeBasis.name)
                    put("algorithmVersion", CARDIO_NOF1_ALGORITHM_VERSION)
                    putAll(sample.source.toMetadata("sensor"))
                }
            )
        }
        return ingest(rows, "heart-rate samples")
    }

    suspend fun persistRrIntervals(
        sessionId: String,
        samples: List<CardioRrIntervalSample>
    ): CardioWriteResult {
        if (samples.isEmpty()) return CardioWriteResult(true, "No RR intervals to persist")
        val rows = samples.sortedBy { it.timestampEpochMs }.mapIndexed { index, sample ->
            val quality = if (sample.isPhysiologicallyStorable) {
                sample.quality
            } else {
                CardioObservationQuality.INVALID
            }
            HealthValue(
                domain = HealthDomain.EXERCISE,
                metric = "cardio_rr_interval_ms",
                value = sample.rrMs,
                unit = "ms",
                timestampEpochMs = sample.timestampEpochMs,
                source = sample.source.sourceName.ifBlank { "cardio-sensor" },
                metadata = buildMap {
                    put(
                        "sourceRecordId",
                        "cardio-rr:" + sessionId + ":" + sample.timestampEpochMs + ":" + index
                    )
                    put("sessionId", sessionId)
                    put("valueClass", CardioValueClass.MEASURED.name)
                    put("quality", quality.name)
                    put("receivedAtEpochMs", sample.receivedAtEpochMs.toString())
                    put("correctionApplied", sample.correctionApplied.toString())
                    put("algorithmVersion", CARDIO_NOF1_ALGORITHM_VERSION)
                    putAll(sample.source.toMetadata("sensor"))
                }
            )
        }
        return ingest(rows, "RR intervals")
    }

    suspend fun publishDerived(
        sessionId: String?,
        metric: CardioDerivedMetric,
        timestampEpochMs: Long = nowEpochMs()
    ): CardioWriteResult {
        val value = metric.value ?: return CardioWriteResult(false, "Derived metric is unavailable")
        val sourceRecordId = "cardio-derived:" + metric.metricId + ":" +
            (sessionId ?: "global") + ":" + timestampEpochMs
        val row = HealthValue(
            domain = HealthDomain.EXERCISE,
            metric = metric.metricId,
            value = value,
            unit = metric.unit,
            timestampEpochMs = timestampEpochMs,
            source = "cardio-derived",
            metadata = buildMap {
                put("sourceRecordId", sourceRecordId)
                sessionId?.let { put("sessionId", it) }
                put("valueClass", metric.valueClass.name)
                put("confidence", metric.confidence.name)
                put("algorithmVersion", metric.algorithmVersion)
                put("requiredInputs", metric.requiredInputs.joinToString(","))
                metric.caveat?.let { put("caveat", it) }
                put("trudyEvidence", "true")
            }
        )
        return ingest(listOf(row), metric.metricId)
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

        val resting = exercise.latest("resting_heart_rate_bpm")?.value
        val restingRows = exercise.between("resting_heart_rate_bpm", baselineStart, now)
            .map { it.value }
            .filter { it.isFinite() }
        val hrv = exercise.latest("heart_rate_variability_rmssd_ms")?.value
        val hrvRows = exercise.between("heart_rate_variability_rmssd_ms", baselineStart, now)
            .map { it.value }
            .filter { it.isFinite() }
        val sleepScore = sleep.latest("sleep_score")?.value

        return CardioRecoveryContext(
            restingHeartRateBpm = resting,
            restingHeartRateBaselineBpm = restingRows.takeIf { it.size >= 5 }?.average(),
            hrvRmssdMs = hrv,
            hrvBaselineRmssdMs = hrvRows.takeIf { it.size >= 5 }?.average(),
            sleepScore = sleepScore,
            trainingStressBalance = latestLoad?.trainingStressBalance
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

    private fun classifyHeartRate(
        sample: CardioHeartRateSample,
        previous: CardioHeartRateSample?
    ): CardioObservationQuality {
        if (!sample.isPhysiologicallyStorable) return CardioObservationQuality.INVALID
        val age = (sample.receivedAtEpochMs - sample.timestampEpochMs).coerceAtLeast(0L)
        if (age > CARDIO_HR_STALE_AFTER_MS) return CardioObservationQuality.STALE
        if (previous != null) {
            val deltaMs = sample.timestampEpochMs - previous.timestampEpochMs
            val jump = abs(sample.bpm - previous.bpm)
            if (deltaMs in 1..5_000L && jump >= 45) return CardioObservationQuality.SUSPECT_OUTLIER
            if (deltaMs > CARDIO_HR_STALE_AFTER_MS) return CardioObservationQuality.GAP_ADJACENT
        }
        return CardioObservationQuality.ACCEPTED
    }
}
