package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs

class TrudyExperimentEngine {
    fun plan(kind: TrudyExperimentKind, targetDomain: HealthDomain, targetMetricId: String, evidenceBasis: List<PersonalEvidenceItem> = emptyList()): TrudyExperimentHypothesis {
        require(targetMetricId.isNotBlank())
        val t = when (kind) {
            TrudyExperimentKind.EARLIER_CAFFEINE_CUTOFF -> Template("An earlier caffeine cutoff may be associated with improvement in the selected sleep outcome.", "Keep caffeine timing consistent and stop caffeine at least 8 hours before the intended sleep window.", 14, TrudyEffectDirection.INCREASE, listOf("bedtime changes","illness","alcohol","unusual exercise load"), listOf("Keep total caffeine intake within your normal safe range.","Do not alter prescribed medication."))
            TrudyExperimentKind.HYDRATION_CONSISTENCY -> Template("More consistent hydration may be associated with a steadier selected outcome.", "Keep daily fluid intake near your existing safe hydration target and reduce large day-to-day swings.", 14, TrudyEffectDirection.UNKNOWN, listOf("salt intake","exercise","heat exposure","illness"), listOf("Do not force excessive fluid intake.","Follow clinician-set fluid restrictions if applicable."))
            TrudyExperimentKind.SLEEP_SCHEDULE_CONSISTENCY -> Template("A more consistent sleep schedule may be associated with improvement in the selected sleep outcome.", "Keep bedtime and wake time within a consistent one-hour window.", 14, TrudyEffectDirection.INCREASE, listOf("travel","shift work","illness","late social events"), listOf("Do not deliberately restrict sleep duration to maintain the schedule."))
            TrudyExperimentKind.EXERCISE_TIMING -> Template("Changing exercise timing may be associated with a change in the selected outcome.", "Keep exercise type and approximate load stable while using one consistent exercise-time window.", 14, TrudyEffectDirection.UNKNOWN, listOf("exercise intensity","sleep duration","nutrition","stress"), listOf("Do not increase exercise intensity solely for this experiment.","Stop if exercise causes concerning symptoms."))
            TrudyExperimentKind.MINDFULNESS_ROUTINE -> Template("A consistent mindfulness routine may be associated with improvement in the selected outcome.", "Use the same brief mindfulness routine at a consistent time each day.", 14, TrudyEffectDirection.UNKNOWN, listOf("sleep","acute stressors","exercise","caffeine"), listOf("Use a comfortable practice and stop if it meaningfully worsens distress."))
        }
        require(rejectUnsafeFreeformIntervention(t.intervention) == null) { "Unsafe experiment template rejected." }
        val strength = evidenceBasis.maxByOrNull { it.confidence.ordinal }?.confidence ?: TrudyConfidence.LOW
        return TrudyExperimentHypothesis(
            id = "experiment:${kind.name}:${targetDomain.name}:$targetMetricId",
            hypothesis = t.hypothesis,
            intervention = t.intervention,
            targetDomain = targetDomain,
            targetMetricId = targetMetricId,
            secondaryMetrics = emptyList(),
            baselineWindowDays = 7,
            interventionWindowDays = t.duration,
            expectedDirection = t.direction,
            suggestedDurationDays = t.duration,
            confounders = t.confounders,
            safetyNotes = t.safety,
            evidenceBasis = evidenceBasis,
            expectedGain = TrudyExpectedGain(t.direction, evidenceStrength = strength, uncertainty = "No numeric gain is claimed unless bounded evidence supports one.", rationale = "This is a reversible lifestyle experiment with a measurable target and explicit confounders.")
        )
    }

    fun evaluate(hypothesis: TrudyExperimentHypothesis, baseline: List<TrudyMetricEvidence>, intervention: List<TrudyMetricEvidence>, adherenceFraction: Double): TrudyExperimentResult {
        require(adherenceFraction.isFinite() && adherenceFraction in 0.0..1.0)
        require(rejectUnsafeFreeformIntervention(hypothesis.intervention) == null) { "Unsafe experiment intervention rejected." }
        require(baseline.all { it.domain == hypothesis.targetDomain && it.metricId == hypothesis.targetMetricId })
        require(intervention.all { it.domain == hypothesis.targetDomain && it.metricId == hypothesis.targetMetricId })

        val validBaseline = baseline.filter(TrudyStatistics::usableEvidence)
        val validIntervention = intervention.filter(TrudyStatistics::usableEvidence)
        val b = validBaseline.map { it.value }; val i = validIntervention.map { it.value }
        val bm = TrudyStatistics.mean(b); val im = TrudyStatistics.mean(i)
        val delta = if (bm != null && im != null) (im - bm).takeIf { it.isFinite() } else null
        val relative = if (delta != null && bm != null && bm != 0.0) (delta / abs(bm) * 100.0).takeIf { it.isFinite() } else null
        val bv = TrudyStatistics.standardDeviation(b); val iv = TrudyStatistics.standardDeviation(i)
        val sufficient = b.size >= 5 && i.size >= 5 && bm != null && im != null
        val variability = listOfNotNull(bv,iv).takeIf { it.isNotEmpty() }?.average()?.takeIf { it.isFinite() }
        val standardized = if (delta != null && variability != null && variability > 0) (delta / variability).takeIf { it.isFinite() } ?: 0.0 else 0.0
        val confidence = if (!sufficient || adherenceFraction < .5) TrudyConfidence.INSUFFICIENT else TrudyConfidenceModel.classify(minOf(b.size,i.size), adherenceFraction, consistency = .7, signalMagnitude = standardized)
        val matches = when (hypothesis.expectedDirection) { TrudyEffectDirection.INCREASE -> delta != null && delta > 0; TrudyEffectDirection.DECREASE -> delta != null && delta < 0; TrudyEffectDirection.NONE -> delta != null && abs(delta) < 1e-9; else -> null }
        val conclusion = when { confidence == TrudyConfidence.INSUFFICIENT -> TrudyExperimentConclusion.INCONCLUSIVE; matches == true -> TrudyExperimentConclusion.SUPPORTS_HYPOTHESIS; matches == false -> TrudyExperimentConclusion.DID_NOT_SUPPORT; abs(standardized) >= .5 -> TrudyExperimentConclusion.SUPPORTS_HYPOTHESIS; else -> TrudyExperimentConclusion.INCONCLUSIVE }
        val summary = when (conclusion) { TrudyExperimentConclusion.SUPPORTS_HYPOTHESIS -> "The intervention result is consistent with the hypothesis in this personal, uncontrolled experiment."; TrudyExperimentConclusion.DID_NOT_SUPPORT -> "The intervention result did not support the hypothesis in this personal, uncontrolled experiment."; TrudyExperimentConclusion.INCONCLUSIVE -> "The experiment is inconclusive with the available samples, adherence, and variability." }
        val all = validBaseline + validIntervention
        val range = if (all.isEmpty()) TrudyTimeRange(0,0) else TrudyTimeRange(all.minOf { it.timestampEpochMs }, all.maxOf { it.timestampEpochMs })
        val refs = all.take(48).map { TrudyEvidenceReference(it.domain, it.metricId, evidenceKind = it.evidenceKind, timestampEpochMs = it.timestampEpochMs) }
        val caveats = buildList {
            add("A single uncontrolled personal experiment does not establish causation.")
            add("Confounders and regression to the mean may influence the result.")
            if (validBaseline.size != baseline.size || validIntervention.size != intervention.size) add("Invalid non-finite or negative-timestamp observations were excluded.")
        }
        val evidence = PersonalEvidenceItem("experiment-result:${hypothesis.id}", listOf(hypothesis.targetDomain), listOf(hypothesis.targetMetricId), if (sufficient) PersonalEvidenceType.EXPERIMENT_RESULT else PersonalEvidenceType.INSUFFICIENT_EVIDENCE, range, effectDirection = effectDirection(delta), effectMagnitude = delta, sampleCount = b.size + i.size, confidence = confidence, dataQualityStatus = if (sufficient) TrudyDataQualityStatus.GOOD else TrudyDataQualityStatus.INSUFFICIENT, caveats = caveats, supportingEvidenceReferences = refs, attributes = mapOf("adherenceFraction" to adherenceFraction.toString()))
        return TrudyExperimentResult(hypothesis.id,hypothesis.targetDomain,hypothesis.targetMetricId,bm,TrudyStatistics.median(b),im,TrudyStatistics.median(i),delta,relative,bv,iv,b.size,i.size,adherenceFraction,confidence,conclusion,summary,evidence)
    }

    fun rejectUnsafeFreeformIntervention(intervention: String): String? {
        val text = intervention.lowercase()
        val unsafe = listOf(
            "prescription", "medication", "insulin", "stop taking", "skip dose",
            "increase dose", "decrease dose", "change dose", "taper medication",
            "withdrawal", "cold turkey", "stop alcohol abruptly", "stop caffeine abruptly",
            "dangerous fasting", "water fast", "dry fast", "multi-day fast",
            "stop treatment", "change treatment", "alter treatment"
        )
        return unsafe.firstOrNull { it in text }?.let { "Unsafe or medically supervised intervention is outside the autonomous Trudy experiment boundary." }
    }

    private data class Template(val hypothesis:String,val intervention:String,val duration:Int,val direction:TrudyEffectDirection,val confounders:List<String>,val safety:List<String>)
}
