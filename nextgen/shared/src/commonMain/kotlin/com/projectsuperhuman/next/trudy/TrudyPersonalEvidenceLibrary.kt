package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

interface TrudyPersonalEvidenceSource {
    suspend fun metricHistory(domain: HealthDomain, metricId: String, limit: Int = 1000): List<TrudyMetricEvidence>
    suspend fun dataQuality(domain: HealthDomain): TrudyDataQualityEvidence
}

class HealthContextPersonalEvidenceSource(private val healthContext: TrudyHealthContextService) : TrudyPersonalEvidenceSource {
    override suspend fun metricHistory(domain: HealthDomain, metricId: String, limit: Int) =
        healthContext.metricHistory(domain, metricId, limit.coerceIn(1, 1000), 0)
    override suspend fun dataQuality(domain: HealthDomain) = healthContext.dataQuality(domain)
}

object TrudyConfidenceModel {
    fun classify(sampleCount: Int, matchedFraction: Double = 1.0, stale: Boolean = false, consistency: Double = 1.0, signalMagnitude: Double = 0.0, repeated: Boolean = false): TrudyConfidence {
        if (sampleCount < 5 || matchedFraction < 0.45) return TrudyConfidence.INSUFFICIENT
        var points = when { sampleCount >= 30 -> 3; sampleCount >= 14 -> 2; sampleCount >= 7 -> 1; else -> 0 }
        if (matchedFraction >= 0.8) points++
        if (!stale) points++
        if (consistency >= 0.7) points++
        if (abs(signalMagnitude) >= 0.5) points++
        if (repeated) points++
        return when { points >= 7 -> TrudyConfidence.STRONG; points >= 5 -> TrudyConfidence.MODERATE; points >= 3 -> TrudyConfidence.LOW; else -> TrudyConfidence.INSUFFICIENT }
    }
}

object TrudyStatistics {
    data class AlignedPair(val left: TrudyMetricEvidence, val right: TrudyMetricEvidence)

    fun align(left: List<TrudyMetricEvidence>, right: List<TrudyMetricEvidence>, lagMs: Long = 0L, alignmentWindowMs: Long): List<AlignedPair> {
        require(lagMs >= 0 && alignmentWindowMs >= 0)
        val l = left.sortedBy { it.timestampEpochMs }
        val r = right.sortedBy { it.timestampEpochMs }
        val used = BooleanArray(r.size)
        return l.mapNotNull { item ->
            val target = item.timestampEpochMs + lagMs
            var best = -1
            var distance = Long.MAX_VALUE
            r.indices.forEach { i ->
                if (!used[i]) {
                    val d = kotlin.math.abs(r[i].timestampEpochMs - target)
                    if (d <= alignmentWindowMs && d < distance) { best = i; distance = d }
                }
            }
            if (best < 0) null else { used[best] = true; AlignedPair(item, r[best]) }
        }
    }

    fun pearson(pairs: List<AlignedPair>): Double? {
        if (pairs.size < 2) return null
        val xs = pairs.map { it.left.value }; val ys = pairs.map { it.right.value }
        val mx = xs.average(); val my = ys.average()
        val numerator = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val dx = sqrt(xs.sumOf { (it - mx).pow(2) }); val dy = sqrt(ys.sumOf { (it - my).pow(2) })
        return if (dx == 0.0 || dy == 0.0) null else (numerator / (dx * dy)).coerceIn(-1.0, 1.0)
    }

    fun spearman(pairs: List<AlignedPair>): Double? {
        if (pairs.size < 2) return null
        val xr = rank(pairs.map { it.left.value }); val yr = rank(pairs.map { it.right.value })
        return pearson(pairs.indices.map { i -> AlignedPair(pairs[i].left.copy(value = xr[i]), pairs[i].right.copy(value = yr[i])) })
    }

    fun mean(values: List<Double>) = values.takeIf { it.isNotEmpty() }?.average()
    fun median(values: List<Double>): Double? { if (values.isEmpty()) return null; val s = values.sorted(); return if (s.size % 2 == 1) s[s.size/2] else (s[s.size/2-1] + s[s.size/2]) / 2.0 }
    fun standardDeviation(values: List<Double>): Double? { if (values.size < 2) return null; val m = values.average(); return sqrt(values.sumOf { (it-m).pow(2) } / (values.size-1)) }

    private fun rank(values: List<Double>): List<Double> {
        val indexed = values.withIndex().sortedBy { it.value }; val ranks = DoubleArray(values.size); var i = 0
        while (i < indexed.size) { var j = i + 1; while (j < indexed.size && indexed[j].value == indexed[i].value) j++; val rank = ((i+1)+j).toDouble()/2.0; for (k in i until j) ranks[indexed[k].index] = rank; i = j }
        return ranks.toList()
    }
}

class TrudyPersonalEvidenceLibrary(private val source: TrudyPersonalEvidenceSource, private val nowEpochMs: () -> Long = { System.currentTimeMillis() }) {
    suspend fun recentTrend(domain: HealthDomain, metricId: String, recentDays: Int = 7, baselineDays: Int = 28): TrudyBaselineComparison {
        require(recentDays > 0 && baselineDays > 0)
        val now = nowEpochMs(); val recentStart = now - recentDays * DAY_MS; val baselineStart = recentStart - baselineDays * DAY_MS
        return compareBaseline(domain, metricId, TrudyTimeRange(recentStart, now), TrudyTimeRange(baselineStart, recentStart - 1))
    }

    suspend fun compareBaseline(domain: HealthDomain, metricId: String, observationWindow: TrudyTimeRange, baselineWindow: TrudyTimeRange): TrudyBaselineComparison {
        val all = source.metricHistory(domain, metricId, 1000)
        require(all.all { it.domain == domain && it.metricId == metricId }) { "Evidence source leaked a different domain or metric" }
        val obs = all.filter { it.timestampEpochMs in observationWindow.fromEpochMs..observationWindow.toEpochMs }
        val base = all.filter { it.timestampEpochMs in baselineWindow.fromEpochMs..baselineWindow.toEpochMs }
        val om = TrudyStatistics.mean(obs.map { it.value }); val bm = TrudyStatistics.mean(base.map { it.value })
        val delta = if (om != null && bm != null) om - bm else null
        val pct = if (delta != null && bm != null && bm != 0.0) delta / abs(bm) * 100.0 else null
        val sd = TrudyStatistics.standardDeviation(base.map { it.value }); val standardized = if (delta != null && sd != null && sd > 0) delta / sd else null
        val q = source.dataQuality(domain); val confidence = TrudyConfidenceModel.classify(minOf(obs.size, base.size), stale = q.isStale, signalMagnitude = standardized ?: 0.0)
        val status = when { obs.isEmpty() || base.isEmpty() -> TrudyDataQualityStatus.INSUFFICIENT; q.isStale -> TrudyDataQualityStatus.STALE; minOf(obs.size,base.size) < 5 -> TrudyDataQualityStatus.SPARSE; q.score < 50 -> TrudyDataQualityStatus.LIMITED; else -> TrudyDataQualityStatus.GOOD }
        val refs = (obs + base).take(48).map { it.ref() }
        val caveats = buildList { if (minOf(obs.size,base.size) < 5) add("One comparison window has fewer than five samples."); if (q.isStale) add("Data is stale."); if (q.score < 50) add("Module data quality is limited.") }
        val evidence = PersonalEvidenceItem("baseline:${domain.name}:$metricId:${observationWindow.fromEpochMs}", listOf(domain), listOf(metricId), if (om == null || bm == null) PersonalEvidenceType.INSUFFICIENT_EVIDENCE else PersonalEvidenceType.TREND, observationWindow, baselineWindow, effectDirection(delta), delta, obs.size + base.size, confidence, status, caveats, refs)
        return TrudyBaselineComparison(domain, metricId, observationWindow, baselineWindow, om, bm, delta, pct, standardized, obs.size, base.size, effectDirection(delta), confidence, status, caveats, evidence)
    }

    suspend fun association(leftDomain: HealthDomain, leftMetricId: String, rightDomain: HealthDomain, rightMetricId: String, method: TrudyAssociationMethod = TrudyAssociationMethod.PEARSON, lagMs: Long = 0, alignmentWindowMs: Long = 21_600_000L, limit: Int = 365): TrudyAssociationResult {
        require(leftMetricId.isNotBlank() && rightMetricId.isNotBlank() && lagMs >= 0 && alignmentWindowMs >= 0)
        val left = source.metricHistory(leftDomain, leftMetricId, limit); val right = source.metricHistory(rightDomain, rightMetricId, limit)
        require(left.all { it.domain == leftDomain && it.metricId == leftMetricId }); require(right.all { it.domain == rightDomain && it.metricId == rightMetricId })
        val pairs = TrudyStatistics.align(left, right, lagMs, alignmentWindowMs)
        val coefficient = if (pairs.size < 5) null else if (method == TrudyAssociationMethod.PEARSON) TrudyStatistics.pearson(pairs) else TrudyStatistics.spearman(pairs)
        val matched = pairs.size.toDouble() / maxOf(left.size, right.size, 1)
        val q1 = source.dataQuality(leftDomain); val q2 = if (rightDomain == leftDomain) q1 else source.dataQuality(rightDomain); val stale = q1.isStale || q2.isStale
        val confidence = TrudyConfidenceModel.classify(pairs.size, matched, stale, signalMagnitude = coefficient ?: 0.0)
        val status = when { pairs.size < 5 -> TrudyDataQualityStatus.INSUFFICIENT; stale -> TrudyDataQualityStatus.STALE; matched < .65 -> TrudyDataQualityStatus.LIMITED; else -> TrudyDataQualityStatus.GOOD }
        val direction = when { coefficient == null -> TrudyEffectDirection.UNKNOWN; coefficient > .05 -> TrudyEffectDirection.INCREASE; coefficient < -.05 -> TrudyEffectDirection.DECREASE; else -> TrudyEffectDirection.NONE }
        val refs = pairs.flatMap { listOf(it.left.ref(), it.right.ref()) }.distinct().take(48)
        val window = if (pairs.isEmpty()) TrudyTimeRange(0,0) else TrudyTimeRange(pairs.minOf { minOf(it.left.timestampEpochMs,it.right.timestampEpochMs) }, pairs.maxOf { maxOf(it.left.timestampEpochMs,it.right.timestampEpochMs) })
        val caveats = buildList { add("Association does not establish causation."); if (lagMs > 0) add("Configured lag: ${lagMs}ms; no other lags were searched."); if (pairs.size < 5) add("Too few aligned samples for an association estimate."); if (stale) add("At least one input domain is stale."); if (matched < .65) add("A limited fraction of observations could be aligned.") }
        val evidence = PersonalEvidenceItem("association:${leftDomain.name}:$leftMetricId:${rightDomain.name}:$rightMetricId:$lagMs", listOf(leftDomain,rightDomain).distinct(), listOf(leftMetricId,rightMetricId).distinct(), if (coefficient == null) PersonalEvidenceType.INSUFFICIENT_EVIDENCE else PersonalEvidenceType.ASSOCIATION, window, effectDirection = direction, effectMagnitude = coefficient, sampleCount = pairs.size, confidence = confidence, dataQualityStatus = status, caveats = caveats, supportingEvidenceReferences = refs, attributes = mapOf("method" to method.name, "lagMs" to lagMs.toString()))
        return TrudyAssociationResult(leftDomain,leftMetricId,rightDomain,rightMetricId,method,coefficient,pairs.size,matched.coerceIn(0.0,1.0),direction,confidence,status,lagMs,caveats,evidence)
    }

    suspend fun repeatedAssociation(leftDomain: HealthDomain, leftMetricId: String, rightDomain: HealthDomain, rightMetricId: String, periods: List<TrudyTimeRange>, method: TrudyAssociationMethod = TrudyAssociationMethod.PEARSON, lagMs: Long = 0, alignmentWindowMs: Long = 21_600_000L): PersonalEvidenceItem {
        require(periods.isNotEmpty())
        val left = source.metricHistory(leftDomain,leftMetricId,1000); val right = source.metricHistory(rightDomain,rightMetricId,1000)
        val coefficients = periods.mapNotNull { p -> val pairs = TrudyStatistics.align(left.filter { it.timestampEpochMs in p.fromEpochMs..p.toEpochMs }, right.filter { it.timestampEpochMs in p.fromEpochMs..p.toEpochMs }, lagMs, alignmentWindowMs); if (pairs.size < 5) null else if (method == TrudyAssociationMethod.PEARSON) TrudyStatistics.pearson(pairs) else TrudyStatistics.spearman(pairs) }
        val sameDirection = coefficients.isNotEmpty() && (coefficients.all { it >= 0 } || coefficients.all { it <= 0 }); val mean = coefficients.takeIf { it.isNotEmpty() }?.average()
        val confidence = TrudyConfidenceModel.classify(coefficients.size * 5, consistency = if (sameDirection) 1.0 else 0.0, signalMagnitude = mean ?: 0.0, repeated = coefficients.size >= 2)
        return PersonalEvidenceItem("repeated-association:${leftDomain.name}:$leftMetricId:${rightDomain.name}:$rightMetricId", listOf(leftDomain,rightDomain).distinct(), listOf(leftMetricId,rightMetricId).distinct(), if (coefficients.size >= 2) PersonalEvidenceType.REPEATED_ASSOCIATION else PersonalEvidenceType.INSUFFICIENT_EVIDENCE, TrudyTimeRange(periods.minOf { it.fromEpochMs }, periods.maxOf { it.toEpochMs }), effectDirection = when { mean == null -> TrudyEffectDirection.UNKNOWN; mean > .05 -> TrudyEffectDirection.INCREASE; mean < -.05 -> TrudyEffectDirection.DECREASE; else -> TrudyEffectDirection.NONE }, effectMagnitude = mean, sampleCount = coefficients.size, confidence = confidence, dataQualityStatus = if (coefficients.size >= 2) TrudyDataQualityStatus.GOOD else TrudyDataQualityStatus.INSUFFICIENT, caveats = listOf("Repeated association does not establish causation.", "Only explicitly supplied periods were tested."))
    }

    private companion object { const val DAY_MS = 86_400_000L }
}

private fun TrudyMetricEvidence.ref() = TrudyEvidenceReference(domain, metricId, evidenceKind = evidenceKind, timestampEpochMs = timestampEpochMs)
