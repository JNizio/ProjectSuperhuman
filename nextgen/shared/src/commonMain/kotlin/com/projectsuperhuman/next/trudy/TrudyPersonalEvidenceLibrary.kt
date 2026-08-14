package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

interface TrudyPersonalEvidenceSource {
    suspend fun metricHistory(domain: HealthDomain, metricId: String, limit: Int = 1000): List<TrudyMetricEvidence>
    suspend fun metricWindow(
        domain: HealthDomain,
        metricId: String,
        range: TrudyTimeRange,
        limit: Int = 1000
    ): List<TrudyMetricEvidence> = metricHistory(domain, metricId, limit)
        .filter { it.timestampEpochMs in range.fromEpochMs..range.toEpochMs }
    suspend fun dataQuality(domain: HealthDomain): TrudyDataQualityEvidence
}

class HealthContextPersonalEvidenceSource(private val healthContext: TrudyHealthContextService) : TrudyPersonalEvidenceSource {
    override suspend fun metricHistory(domain: HealthDomain, metricId: String, limit: Int) =
        healthContext.metricHistory(domain, metricId, limit.coerceIn(1, 1000), 0)
    override suspend fun metricWindow(domain: HealthDomain, metricId: String, range: TrudyTimeRange, limit: Int) =
        healthContext.metricWindow(domain, metricId, range, limit.coerceIn(1, 1000))
    override suspend fun dataQuality(domain: HealthDomain) = healthContext.dataQuality(domain)
}

class TrudyConfidenceModel private constructor() {
    companion object {
        fun classify(sampleCount: Int, matchedFraction: Double = 1.0, stale: Boolean = false, consistency: Double = 1.0, signalMagnitude: Double = 0.0, repeated: Boolean = false): TrudyConfidence {
            if (sampleCount < 5 || !matchedFraction.isFinite() || matchedFraction < 0.45) return TrudyConfidence.INSUFFICIENT
            var points = when { sampleCount >= 30 -> 3; sampleCount >= 14 -> 2; sampleCount >= 7 -> 1; else -> 0 }
            if (matchedFraction >= 0.8) points++
            if (!stale) points++
            if (consistency.isFinite() && consistency >= 0.7) points++
            if (signalMagnitude.isFinite() && abs(signalMagnitude) >= 0.5) points++
            if (repeated) points++
            return when { points >= 7 -> TrudyConfidence.STRONG; points >= 5 -> TrudyConfidence.MODERATE; points >= 3 -> TrudyConfidence.LOW; else -> TrudyConfidence.INSUFFICIENT }
        }
    }
}

object TrudyStatistics {
    data class AlignedPair(val left: TrudyMetricEvidence, val right: TrudyMetricEvidence)

    const val MAX_LAG_MS = 604_800_000L
    const val MAX_ALIGNMENT_WINDOW_MS = 604_800_000L
    const val MAX_ROLLING_WINDOW_MS = 1_209_600_000L

    fun align(left: List<TrudyMetricEvidence>, right: List<TrudyMetricEvidence>, lagMs: Long = 0L, alignmentWindowMs: Long): List<AlignedPair> {
        require(lagMs in 0..MAX_LAG_MS)
        require(alignmentWindowMs in 0..MAX_ALIGNMENT_WINDOW_MS)
        val l = left.asSequence()
            .filter(::usableEvidence)
            .filter { it.timestampEpochMs <= Long.MAX_VALUE - lagMs }
            .sortedBy { it.timestampEpochMs }
            .toList()
        val r = right.asSequence().filter(::usableEvidence).sortedBy { it.timestampEpochMs }.toList()
        val used = BooleanArray(r.size)
        return l.mapNotNull { item ->
            val target = item.timestampEpochMs + lagMs
            var best = -1
            var distance = Long.MAX_VALUE
            r.indices.forEach { i ->
                if (!used[i]) {
                    val candidate = r[i].timestampEpochMs
                    val d = if (candidate >= target) candidate - target else target - candidate
                    if (d <= alignmentWindowMs && d < distance) { best = i; distance = d }
                }
            }
            if (best < 0) null else { used[best] = true; AlignedPair(item, r[best]) }
        }
    }

    /**
     * Align each outcome with the mean exposure in one declared lookback window.
     * The lower boundary is exclusive, which makes a three-day window contain at most
     * the target day and two preceding daily observations.
     */
    fun alignRolling(
        left: List<TrudyMetricEvidence>,
        right: List<TrudyMetricEvidence>,
        rollingWindowMs: Long
    ): List<AlignedPair> {
        require(rollingWindowMs in 1L..MAX_ROLLING_WINDOW_MS)
        val exposures = left.asSequence().filter(::usableEvidence).sortedBy { it.timestampEpochMs }.toList()
        val outcomes = right.asSequence().filter(::usableEvidence).sortedBy { it.timestampEpochMs }.toList()
        return outcomes.mapNotNull { outcome ->
            val fromExclusive = (outcome.timestampEpochMs - rollingWindowMs).coerceAtLeast(0L)
            val window = exposures.filter {
                it.timestampEpochMs > fromExclusive && it.timestampEpochMs <= outcome.timestampEpochMs
            }
            val mean = mean(window.map { it.value }) ?: return@mapNotNull null
            val anchor = window.last().copy(value = mean, timestampEpochMs = outcome.timestampEpochMs)
            AlignedPair(anchor, outcome)
        }
    }

    fun pearson(pairs: List<AlignedPair>): Double? {
        val valid = pairs.filter { it.left.value.isFinite() && it.right.value.isFinite() }
        if (valid.size < 2) return null
        val xs = valid.map { it.left.value }; val ys = valid.map { it.right.value }
        val mx = xs.average(); val my = ys.average()
        if (!mx.isFinite() || !my.isFinite()) return null
        val numerator = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val dxSquared = xs.sumOf { (it - mx).pow(2) }
        val dySquared = ys.sumOf { (it - my).pow(2) }
        if (!numerator.isFinite() || !dxSquared.isFinite() || !dySquared.isFinite() || dxSquared <= 0.0 || dySquared <= 0.0) return null
        val denominator = sqrt(dxSquared) * sqrt(dySquared)
        if (!denominator.isFinite() || denominator <= 0.0) return null
        val coefficient = numerator / denominator
        return coefficient.takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0)
    }

    fun spearman(pairs: List<AlignedPair>): Double? {
        val valid = pairs.filter { it.left.value.isFinite() && it.right.value.isFinite() }
        if (valid.size < 2) return null
        val xr = rank(valid.map { it.left.value }); val yr = rank(valid.map { it.right.value })
        return pearson(valid.indices.map { i -> AlignedPair(valid[i].left.copy(value = xr[i]), valid[i].right.copy(value = yr[i])) })
    }

    fun mean(values: List<Double>): Double? {
        if (values.isEmpty() || values.any { !it.isFinite() }) return null
        return values.average().takeIf { it.isFinite() }
    }

    fun median(values: List<Double>): Double? {
        if (values.isEmpty() || values.any { !it.isFinite() }) return null
        val s = values.sorted()
        val result = if (s.size % 2 == 1) s[s.size/2] else (s[s.size/2-1] + s[s.size/2]) / 2.0
        return result.takeIf { it.isFinite() }
    }

    fun standardDeviation(values: List<Double>): Double? {
        if (values.size < 2 || values.any { !it.isFinite() }) return null
        val m = values.average()
        if (!m.isFinite()) return null
        val variance = values.sumOf { (it-m).pow(2) } / (values.size-1)
        return variance.takeIf { it.isFinite() && it >= 0.0 }?.let(::sqrt)?.takeIf { it.isFinite() }
    }

    fun usableEvidence(evidence: TrudyMetricEvidence): Boolean =
        evidence.value.isFinite() && evidence.timestampEpochMs >= 0L

    private fun rank(values: List<Double>): List<Double> {
        val indexed = values.withIndex().sortedBy { it.value }; val ranks = DoubleArray(values.size); var i = 0
        while (i < indexed.size) { var j = i + 1; while (j < indexed.size && indexed[j].value == indexed[i].value) j++; val rank = ((i+1)+j).toDouble()/2.0; for (k in i until j) ranks[indexed[k].index] = rank; i = j }
        return ranks.toList()
    }
}

class TrudyPersonalEvidenceLibrary(private val source: TrudyPersonalEvidenceSource, private val nowEpochMs: () -> Long = { System.currentTimeMillis() }) {
    suspend fun recentTrend(domain: HealthDomain, metricId: String, recentDays: Int = 7, baselineDays: Int = 28): TrudyBaselineComparison {
        require(recentDays in 1..MAX_WINDOW_DAYS && baselineDays in 1..MAX_WINDOW_DAYS)
        val now = nowEpochMs()
        require(now >= 0L) { "Current timestamp must be non-negative" }
        val recentDuration = recentDays.toLong() * DAY_MS
        val baselineDuration = baselineDays.toLong() * DAY_MS
        val recentStart = saturatedSubtract(now, recentDuration)
        val baselineStart = saturatedSubtract(recentStart, baselineDuration)
        val baselineEnd = if (recentStart == Long.MIN_VALUE) Long.MIN_VALUE else recentStart - 1L
        return compareBaseline(domain, metricId, TrudyTimeRange(recentStart, now), TrudyTimeRange(baselineStart, baselineEnd))
    }

    suspend fun compareBaseline(domain: HealthDomain, metricId: String, observationWindow: TrudyTimeRange, baselineWindow: TrudyTimeRange): TrudyBaselineComparison {
        val observationRows = source.metricWindow(domain, metricId, observationWindow, MAX_WINDOW_ROWS)
        val baselineRows = source.metricWindow(domain, metricId, baselineWindow, MAX_WINDOW_ROWS)
        val all = (observationRows + baselineRows).distinctBy {
            listOf(it.domain.name, it.metricId, it.timestampEpochMs.toString(), it.source, it.value.toString()).joinToString("|")
        }
        require(all.all { it.domain == domain && it.metricId == metricId }) { "Evidence source leaked a different domain or metric" }
        val usable = all.filter(TrudyStatistics::usableEvidence)
        val obs = usable.filter { it.timestampEpochMs in observationWindow.fromEpochMs..observationWindow.toEpochMs }
        val base = usable.filter { it.timestampEpochMs in baselineWindow.fromEpochMs..baselineWindow.toEpochMs }
        val om = TrudyStatistics.mean(obs.map { it.value }); val bm = TrudyStatistics.mean(base.map { it.value })
        val delta = if (om != null && bm != null) (om - bm).takeIf { it.isFinite() } else null
        val pct = if (delta != null && bm != null && bm != 0.0) (delta / abs(bm) * 100.0).takeIf { it.isFinite() } else null
        val sd = TrudyStatistics.standardDeviation(base.map { it.value }); val standardized = if (delta != null && sd != null && sd > 0) (delta / sd).takeIf { it.isFinite() } else null
        val q = source.dataQuality(domain); val confidence = TrudyConfidenceModel.classify(minOf(obs.size, base.size), stale = q.isStale, signalMagnitude = standardized ?: 0.0)
        val status = when { obs.isEmpty() || base.isEmpty() -> TrudyDataQualityStatus.INSUFFICIENT; q.isStale -> TrudyDataQualityStatus.STALE; minOf(obs.size,base.size) < 5 -> TrudyDataQualityStatus.SPARSE; q.score < 50 -> TrudyDataQualityStatus.LIMITED; else -> TrudyDataQualityStatus.GOOD }
        val refs = (obs + base).take(48).map { it.ref() }
        val caveats = buildList { if (all.size != usable.size) add("Invalid non-finite or negative-timestamp observations were excluded."); if (minOf(obs.size,base.size) < 5) add("One comparison window has fewer than five samples."); if (q.isStale) add("Data is stale."); if (q.score < 50) add("Module data quality is limited.") }
        val evidence = PersonalEvidenceItem("baseline:${domain.name}:$metricId:${observationWindow.fromEpochMs}", listOf(domain), listOf(metricId), if (om == null || bm == null) PersonalEvidenceType.INSUFFICIENT_EVIDENCE else PersonalEvidenceType.TREND, observationWindow, baselineWindow, effectDirection(delta), delta, obs.size + base.size, confidence, status, caveats, refs)
        return TrudyBaselineComparison(domain, metricId, observationWindow, baselineWindow, om, bm, delta, pct, standardized, obs.size, base.size, effectDirection(delta), confidence, status, caveats, evidence)
    }

    suspend fun association(leftDomain: HealthDomain, leftMetricId: String, rightDomain: HealthDomain, rightMetricId: String, method: TrudyAssociationMethod = TrudyAssociationMethod.PEARSON, lagMs: Long = 0, alignmentWindowMs: Long = 21_600_000L, limit: Int = 365, window: TrudyTimeRange? = null, rollingWindowMs: Long? = null): TrudyAssociationResult {
        require(leftMetricId.isNotBlank() && rightMetricId.isNotBlank())
        require(lagMs in 0..TrudyStatistics.MAX_LAG_MS && alignmentWindowMs in 0..TrudyStatistics.MAX_ALIGNMENT_WINDOW_MS)
        require(rollingWindowMs == null || rollingWindowMs in 1L..TrudyStatistics.MAX_ROLLING_WINDOW_MS)
        require(limit in 1..1000)
        val rawLeft = if (window == null) source.metricHistory(leftDomain, leftMetricId, limit)
        else source.metricWindow(leftDomain, leftMetricId, window, limit)
        val rawRight = if (window == null) source.metricHistory(rightDomain, rightMetricId, limit)
        else source.metricWindow(rightDomain, rightMetricId, window, limit)
        require(rawLeft.all { it.domain == leftDomain && it.metricId == leftMetricId }); require(rawRight.all { it.domain == rightDomain && it.metricId == rightMetricId })
        val left = rawLeft.filter(TrudyStatistics::usableEvidence); val right = rawRight.filter(TrudyStatistics::usableEvidence)
        val pairs = if (rollingWindowMs == null) {
            TrudyStatistics.align(left, right, lagMs, alignmentWindowMs)
        } else {
            TrudyStatistics.alignRolling(left, right, rollingWindowMs)
        }
        val coefficient = if (pairs.size < 5) null else if (method == TrudyAssociationMethod.PEARSON) TrudyStatistics.pearson(pairs) else TrudyStatistics.spearman(pairs)
        val matched = pairs.size.toDouble() / maxOf(left.size, right.size, 1)
        val q1 = source.dataQuality(leftDomain); val q2 = if (rightDomain == leftDomain) q1 else source.dataQuality(rightDomain); val stale = q1.isStale || q2.isStale
        val confidence = TrudyConfidenceModel.classify(pairs.size, matched, stale, signalMagnitude = coefficient ?: 0.0)
        val status = when { coefficient == null || pairs.size < 5 -> TrudyDataQualityStatus.INSUFFICIENT; stale -> TrudyDataQualityStatus.STALE; matched < .65 -> TrudyDataQualityStatus.LIMITED; else -> TrudyDataQualityStatus.GOOD }
        val direction = when { coefficient == null -> TrudyEffectDirection.UNKNOWN; coefficient > .05 -> TrudyEffectDirection.INCREASE; coefficient < -.05 -> TrudyEffectDirection.DECREASE; else -> TrudyEffectDirection.NONE }
        val refs = pairs.flatMap { listOf(it.left.ref(), it.right.ref()) }.distinct().take(48)
        val window = if (pairs.isEmpty()) TrudyTimeRange(0,0) else TrudyTimeRange(pairs.minOf { minOf(it.left.timestampEpochMs,it.right.timestampEpochMs) }, pairs.maxOf { maxOf(it.left.timestampEpochMs,it.right.timestampEpochMs) })
        val caveats = buildList { add("Association does not establish causation."); if (rawLeft.size != left.size || rawRight.size != right.size) add("Invalid non-finite or negative-timestamp observations were excluded."); if (lagMs > 0) add("Configured lag: ${lagMs}ms; no other lags were searched."); if (rollingWindowMs != null) add("Configured rolling window: ${rollingWindowMs}ms; no other window was searched."); if (pairs.size < 5 || coefficient == null) add("Too few usable aligned samples or insufficient variance for an association estimate."); if (stale) add("At least one input domain is stale."); if (matched < .65) add("A limited fraction of observations could be aligned.") }
        val evidence = PersonalEvidenceItem("association:${leftDomain.name}:$leftMetricId:${rightDomain.name}:$rightMetricId:$lagMs", listOf(leftDomain,rightDomain).distinct(), listOf(leftMetricId,rightMetricId).distinct(), if (coefficient == null) PersonalEvidenceType.INSUFFICIENT_EVIDENCE else PersonalEvidenceType.ASSOCIATION, window, effectDirection = direction, effectMagnitude = coefficient, sampleCount = pairs.size, confidence = confidence, dataQualityStatus = status, caveats = caveats, supportingEvidenceReferences = refs, attributes = buildMap {
            put("method", method.name)
            put("lagMs", lagMs.toString())
            rollingWindowMs?.let { put("rollingWindowMs", it.toString()) }
        })
        return TrudyAssociationResult(leftDomain,leftMetricId,rightDomain,rightMetricId,method,coefficient,pairs.size,matched.coerceIn(0.0,1.0),direction,confidence,status,lagMs,caveats,evidence)
    }

    suspend fun repeatedAssociation(leftDomain: HealthDomain, leftMetricId: String, rightDomain: HealthDomain, rightMetricId: String, periods: List<TrudyTimeRange>, method: TrudyAssociationMethod = TrudyAssociationMethod.PEARSON, lagMs: Long = 0, alignmentWindowMs: Long = 21_600_000L): PersonalEvidenceItem {
        require(periods.isNotEmpty())
        require(lagMs in 0..TrudyStatistics.MAX_LAG_MS && alignmentWindowMs in 0..TrudyStatistics.MAX_ALIGNMENT_WINDOW_MS)
        val left = source.metricHistory(leftDomain,leftMetricId,1000).filter(TrudyStatistics::usableEvidence); val right = source.metricHistory(rightDomain,rightMetricId,1000).filter(TrudyStatistics::usableEvidence)
        val coefficients = periods.mapNotNull { p -> val pairs = TrudyStatistics.align(left.filter { it.timestampEpochMs in p.fromEpochMs..p.toEpochMs }, right.filter { it.timestampEpochMs in p.fromEpochMs..p.toEpochMs }, lagMs, alignmentWindowMs); if (pairs.size < 5) null else if (method == TrudyAssociationMethod.PEARSON) TrudyStatistics.pearson(pairs) else TrudyStatistics.spearman(pairs) }.filter { it.isFinite() }
        val sameDirection = coefficients.isNotEmpty() && (coefficients.all { it >= 0 } || coefficients.all { it <= 0 }); val mean = coefficients.takeIf { it.isNotEmpty() }?.average()?.takeIf { it.isFinite() }
        val confidence = TrudyConfidenceModel.classify(coefficients.size * 5, consistency = if (sameDirection) 1.0 else 0.0, signalMagnitude = mean ?: 0.0, repeated = coefficients.size >= 2)
        return PersonalEvidenceItem("repeated-association:${leftDomain.name}:$leftMetricId:${rightDomain.name}:$rightMetricId", listOf(leftDomain,rightDomain).distinct(), listOf(leftMetricId,rightMetricId).distinct(), if (coefficients.size >= 2) PersonalEvidenceType.REPEATED_ASSOCIATION else PersonalEvidenceType.INSUFFICIENT_EVIDENCE, TrudyTimeRange(periods.minOf { it.fromEpochMs }, periods.maxOf { it.toEpochMs }), effectDirection = when { mean == null -> TrudyEffectDirection.UNKNOWN; mean > .05 -> TrudyEffectDirection.INCREASE; mean < -.05 -> TrudyEffectDirection.DECREASE; else -> TrudyEffectDirection.NONE }, effectMagnitude = mean, sampleCount = coefficients.size, confidence = confidence, dataQualityStatus = if (coefficients.size >= 2) TrudyDataQualityStatus.GOOD else TrudyDataQualityStatus.INSUFFICIENT, caveats = listOf("Repeated association does not establish causation.", "Only explicitly supplied periods were tested."))
    }

    private fun saturatedSubtract(value: Long, amount: Long): Long =
        if (amount > 0L && value < Long.MIN_VALUE + amount) Long.MIN_VALUE else value - amount

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_WINDOW_DAYS = 3650
        const val MAX_WINDOW_ROWS = 1_000
    }
}

private fun TrudyMetricEvidence.ref() = TrudyEvidenceReference(domain, metricId, evidenceKind = evidenceKind, timestampEpochMs = timestampEpochMs)
