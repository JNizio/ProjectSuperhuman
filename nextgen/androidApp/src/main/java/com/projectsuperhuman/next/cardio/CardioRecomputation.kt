package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue

internal data class CardioRecomputeContext(
    val session: CardioSession,
    val capabilities: CardioCapabilities,
    val originalProfile: CardioPhysiologyProfile? = null,
    val currentProfile: CardioPhysiologyProfile? = null,
    val sourceIds: List<String> = emptyList(),
    val generatedAtEpochMs: Long = System.currentTimeMillis()
)

internal interface CardioMetricRecomputer {
    val metricId: String
    val algorithmVersion: String
    val requiredCapabilities: Set<CardioCapability>

    fun compute(context: CardioRecomputeContext): CardioDerivedMetric?
}

internal data class CardioRecomputeOutput(
    val sessionId: String,
    val effectiveTimestampEpochMs: Long,
    val metric: CardioDerivedMetric
) {
    val stableKey: String
        get() = listOf(
            metric.metricId,
            sessionId,
            metric.algorithmVersion,
            effectiveTimestampEpochMs.toString()
        ).joinToString("|")
}

internal data class CardioRecomputeReport(
    val outputs: List<CardioRecomputeOutput>,
    val alreadyPresentKeys: Set<String>,
    val unavailableMetrics: Map<String, Set<CardioCapability>>
)

internal class CardioRecomputationEngine(
    private val calculators: List<CardioMetricRecomputer>
) {
    fun plan(
        context: CardioRecomputeContext,
        existingKeys: Set<String> = emptySet()
    ): CardioRecomputeReport {
        val outputs = mutableListOf<CardioRecomputeOutput>()
        val alreadyPresent = mutableSetOf<String>()
        val unavailable = linkedMapOf<String, Set<CardioCapability>>()

        calculators.forEach { calculator ->
            val missing = context.capabilities.missing(calculator.requiredCapabilities)
            if (missing.isNotEmpty()) {
                unavailable[calculator.metricId] = missing
                return@forEach
            }

            val metric = calculator.compute(context)?.copy(
                metricId = calculator.metricId,
                algorithmVersion = calculator.algorithmVersion,
                sourceIds = calculatorResultSources(context, calculator),
                generatedAtEpochMs = context.generatedAtEpochMs
            ) ?: run {
                unavailable[calculator.metricId] = emptySet()
                return@forEach
            }
            val output = CardioRecomputeOutput(
                sessionId = context.session.id,
                effectiveTimestampEpochMs = context.session.endedAt,
                metric = metric
            )
            if (output.stableKey in existingKeys) {
                alreadyPresent += output.stableKey
            } else if (outputs.none { it.stableKey == output.stableKey }) {
                outputs += output
            }
        }

        return CardioRecomputeReport(
            outputs = outputs,
            alreadyPresentKeys = alreadyPresent,
            unavailableMetrics = unavailable
        )
    }

    private fun calculatorResultSources(
        context: CardioRecomputeContext,
        calculator: CardioMetricRecomputer
    ): List<String> = context.sourceIds
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty { listOf("session:" + context.session.id, "calculator:" + calculator.metricId) }
}

internal object CardioDerivedEvidenceCodec {
    fun toHealthValue(
        sessionId: String?,
        metric: CardioDerivedMetric,
        effectiveTimestampEpochMs: Long
    ): HealthValue? {
        val value = metric.value ?: return null
        val generatedAt = metric.generatedAtEpochMs ?: effectiveTimestampEpochMs
        val sourceRecordId = CardioTrustIds.derivedResultId(
            sessionId = sessionId,
            metricId = metric.metricId,
            algorithmVersion = metric.algorithmVersion
        )
        return HealthValue(
            domain = HealthDomain.EXERCISE,
            metric = metric.metricId,
            value = value,
            unit = metric.unit,
            timestampEpochMs = effectiveTimestampEpochMs,
            source = "cardio-derived",
            metadata = buildMap {
                put("sourceRecordId", sourceRecordId)
                sessionId?.let { put("sessionId", it) }
                put("valueClass", metric.valueClass.name)
                put("confidence", metric.confidence.name)
                put("algorithmVersion", metric.algorithmVersion)
                put("requiredInputs", metric.requiredInputs.joinToString(","))
                put("sourceIds", metric.sourceIds.joinToString(","))
                put("generatedAtEpochMs", generatedAt.toString())
                put("provenance", "derived")
                put("trudyEvidence", "true")
                metric.caveat?.let { put("caveat", it) }
            }
        )
    }
}
