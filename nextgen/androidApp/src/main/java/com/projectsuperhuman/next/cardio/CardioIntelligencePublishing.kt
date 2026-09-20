package com.projectsuperhuman.next

/**
 * Publishes only supported, non-null analytical outputs into the canonical Data Vault.
 * CardioNof1Repository.publishDerived already stamps provenance, confidence,
 * algorithm version and trudyEvidence metadata.
 */
internal suspend fun CardioNof1Repository.publishIntelligence(
    sessionId: String?,
    timestampEpochMs: Long,
    load: CardioSessionLoadEstimate? = null,
    paceAtHeartRate: CardioHrBandEstimate? = null,
    heartRateAtPace: CardioHrBandEstimate? = null,
    efficiency: CardioEfficiencyEstimate? = null,
    decoupling: CardioDecouplingEstimate? = null,
    heartRateRecovery: CardioHeartRateRecoveryEstimate? = null,
    hrv: CardioHrvEstimate? = null,
    vo2: CardioVo2Estimate? = null,
    criticalSpeed: CardioCriticalSpeedEstimate? = null
): List<CardioWriteResult> {
    val results = mutableListOf<CardioWriteResult>()

    suspend fun publish(metric: CardioDerivedMetric) {
        results += publishDerived(sessionId, metric, timestampEpochMs)
    }

    load?.value?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_daily_training_load",
                value = value,
                unit = "load",
                valueClass = CardioValueClass.DERIVED,
                confidence = load.confidence,
                algorithmVersion = load.algorithmVersion,
                requiredInputs = listOf(load.method.name, "session duration"),
                caveat = load.formula + ". " + (load.caveat ?: "")
            )
        )
    }

    paceAtHeartRate?.value?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_pace_at_hr_sec_per_km",
                value = value,
                unit = "sec/km",
                valueClass = CardioValueClass.DERIVED,
                confidence = paceAtHeartRate.confidence,
                algorithmVersion = paceAtHeartRate.algorithmVersion,
                requiredInputs = listOf("time-series HR", "time-series speed", "target HR band"),
                caveat = paceAtHeartRate.formula
            )
        )
    }

    heartRateAtPace?.value?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_hr_at_pace_bpm",
                value = value,
                unit = "bpm",
                valueClass = CardioValueClass.DERIVED,
                confidence = heartRateAtPace.confidence,
                algorithmVersion = heartRateAtPace.algorithmVersion,
                requiredInputs = listOf("time-series HR", "time-series speed", "target pace band"),
                caveat = heartRateAtPace.formula
            )
        )
    }

    efficiency?.value?.let { value ->
        val powerBased = efficiency.input.contains("power", ignoreCase = true)
        publish(
            CardioDerivedMetric(
                metricId = if (powerBased) {
                    "cardio_aerobic_efficiency_power_w_per_bpm"
                } else {
                    "cardio_aerobic_efficiency_speed_mps_per_bpm"
                },
                value = value,
                unit = if (powerBased) "W/bpm" else "m/s/bpm",
                valueClass = CardioValueClass.DERIVED,
                confidence = efficiency.confidence,
                algorithmVersion = efficiency.algorithmVersion,
                requiredInputs = listOf(efficiency.input),
                caveat = efficiency.formula
            )
        )
    }

    decoupling?.percent?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_aerobic_decoupling_pct",
                value = value,
                unit = "%",
                valueClass = CardioValueClass.DERIVED,
                confidence = decoupling.confidence,
                algorithmVersion = decoupling.algorithmVersion,
                requiredInputs = listOf("continuous HR", decoupling.input ?: "continuous output"),
                caveat = decoupling.caveat + " Formula: " + decoupling.formula
            )
        )
    }

    heartRateRecovery?.hrr1MinuteBpm?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_hrr_1min_bpm",
                value = value,
                unit = "bpm",
                valueClass = CardioValueClass.DERIVED,
                confidence = heartRateRecovery.confidence,
                algorithmVersion = heartRateRecovery.algorithmVersion,
                requiredInputs = listOf("workout-end HR", "post-effort HR near 60 seconds"),
                caveat = heartRateRecovery.caveat
            )
        )
    }
    heartRateRecovery?.hrr2MinuteBpm?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_hrr_2min_bpm",
                value = value,
                unit = "bpm",
                valueClass = CardioValueClass.DERIVED,
                confidence = heartRateRecovery.confidence,
                algorithmVersion = heartRateRecovery.algorithmVersion,
                requiredInputs = listOf("workout-end HR", "post-effort HR near 120 seconds"),
                caveat = heartRateRecovery.caveat
            )
        )
    }

    hrv?.rmssdMs?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "heart_rate_variability_rmssd_ms",
                value = value,
                unit = "ms",
                valueClass = CardioValueClass.DERIVED,
                confidence = hrv.confidence,
                algorithmVersion = hrv.algorithmVersion,
                requiredInputs = listOf("genuine RR intervals"),
                caveat = hrv.artifactPolicy
            )
        )
    }

    vo2?.mlKgMin?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_vo2_estimate_ml_kg_min",
                value = value,
                unit = "ml/kg/min",
                valueClass = CardioValueClass.ESTIMATED,
                confidence = vo2.confidence,
                algorithmVersion = vo2.algorithmVersion,
                requiredInputs = vo2.assumptions,
                caveat = vo2.caveat + " Formula: " + vo2.formula
            )
        )
    }

    criticalSpeed?.criticalSpeedMetersPerSecond?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_critical_speed_mps",
                value = value,
                unit = "m/s",
                valueClass = CardioValueClass.ESTIMATED,
                confidence = criticalSpeed.confidence,
                algorithmVersion = criticalSpeed.algorithmVersion,
                requiredInputs = listOf("3+ verified maximal efforts at distinct durations"),
                caveat = criticalSpeed.caveat + " Formula: " + criticalSpeed.formula
            )
        )
    }
    criticalSpeed?.dPrimeMeters?.let { value ->
        publish(
            CardioDerivedMetric(
                metricId = "cardio_d_prime_m",
                value = value,
                unit = "m",
                valueClass = CardioValueClass.ESTIMATED,
                confidence = criticalSpeed.confidence,
                algorithmVersion = criticalSpeed.algorithmVersion,
                requiredInputs = listOf("3+ verified maximal efforts at distinct durations"),
                caveat = criticalSpeed.caveat
            )
        )
    }

    return results
}
