package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

enum class TrudyModelRuntimeMode { DETERMINISTIC, HOSTED, LOCAL }

data class TrudyFormattedModelInput(
    val systemInstruction: String,
    val userMessage: String,
    val conversation: String,
    val tools: String,
    val toolResults: String,
    val evidenceIndex: String,
    val iteration: Int,
    val knowledge: String = "",
    val answerPlan: String = ""
) {
    fun asPrompt(): String = buildString {
        appendLine("SYSTEM"); appendLine(systemInstruction)
        appendLine("\nCONVERSATION"); appendLine(conversation.ifBlank { "(none)" })
        appendLine("\nUSER"); appendLine(userMessage)
        appendLine("\nTOOLS"); appendLine(tools.ifBlank { "(tool phase complete)" })
        appendLine("\nPERSONAL EVIDENCE"); appendLine(toolResults.ifBlank { "(none)" })
        appendLine("\nEVIDENCE REFERENCES"); appendLine(evidenceIndex.ifBlank { "(none)" })
        appendLine("\nRELEVANT KNOWLEDGE"); appendLine(knowledge.ifBlank { "(none)" })
        appendLine("\nANSWER PLAN"); appendLine(answerPlan.ifBlank { "(none)" })
        append("\nITERATION ").append(iteration)
    }
}

/**
 * Converts typed Trudy state into a compact model context.
 *
 * Before deterministic tools have run, the model still receives the bounded tool catalogue. Once
 * an Answer Plan exists AND personal tool results are present, the formatter switches to synthesis
 * mode: tool definitions disappear, conversation is tighter, raw rows are filtered toward the
 * evidence the Answer Engine selected, and retrieved-but-unused records stay out of the prompt.
 */
class TrudyPromptFormatter(
    private val maxConversationTurns: Int = 8,
    private val maxEvidenceRowsPerResult: Int = 24,
    private val maxFinalConversationTurns: Int = 6,
    private val maxFinalEvidenceRowsPerResult: Int = 6
) {
    init {
        require(maxConversationTurns > 0)
        require(maxEvidenceRowsPerResult > 0)
        require(maxFinalConversationTurns > 0)
        require(maxFinalEvidenceRowsPerResult > 0)
    }

    fun format(request: TrudyModelRequest): TrudyFormattedModelInput {
        val synthesisMode = request.answerPlan != null && request.toolResults.isNotEmpty()
        val conversationLimit = if (synthesisMode) minOf(maxConversationTurns, maxFinalConversationTurns) else maxConversationTurns
        val conversationTextLimit = if (synthesisMode) MAX_FINAL_CONVERSATION_TEXT_CHARS else MAX_TEXT_CHARS
        val conversation = request.conversationContext.takeLast(conversationLimit).joinToString("\n") {
            buildString {
                append(it.role.name).append(": ").append(it.text.trim().take(conversationTextLimit))
                if (it.evidenceKeys.isNotEmpty()) {
                    append(" evidence=").append(it.evidenceKeys.take(if (synthesisMode) 6 else 12).joinToString())
                }
            }
        }

        val tools = if (synthesisMode) "" else request.toolDefinitions.joinToString("\n") {
            "${it.name}(${it.requiredFields.joinToString()}): ${it.description}"
        }

        val answerPlan = request.answerPlan
        val refs = if (synthesisMode && answerPlan != null) {
            selectedEvidenceReferences(request.toolResults, answerPlan, MAX_FINAL_EVIDENCE_INDEX)
        } else {
            request.toolResults.flatMap(::evidenceReferences).distinct().take(MAX_EVIDENCE_INDEX)
        }
        // Build raw-row filters from the references that actually survived answer selection. This
        // preserves both sides of a cross-domain association instead of assigning both metrics to
        // the AnswerEvidence's display domain.
        val selectedKeys = if (synthesisMode) {
            refs.mapNotNull { reference -> reference.metricId?.let { reference.domain to it } }.toSet()
        } else emptySet()

        val resultRows = if (synthesisMode) maxFinalEvidenceRowsPerResult else maxEvidenceRowsPerResult
        val selectedResults = if (synthesisMode && answerPlan != null) {
            prioritizeResultsForPlan(request.toolResults, answerPlan)
        } else request.toolResults
        val results = selectedResults
            .take(if (synthesisMode) MAX_FINAL_RESULT_BLOCKS else Int.MAX_VALUE)
            .joinToString("\n") { renderResult(it, resultRows, selectedKeys) }
        val evidence = refs.joinToString("\n") { it.renderKey() }

        val knowledgeLimit = if (synthesisMode) MAX_FINAL_KNOWLEDGE_ITEMS else MAX_KNOWLEDGE_ITEMS
        val knowledgeSummaryLimit = if (synthesisMode) MAX_FINAL_KNOWLEDGE_SUMMARY_CHARS else MAX_KNOWLEDGE_SUMMARY_CHARS
        val knowledge = request.knowledgeContext.take(knowledgeLimit).joinToString("\n") {
            "${it.kind}/${it.stableId} title=${it.title.take(120)} summary=${it.summary.take(knowledgeSummaryLimit)} " +
                "source=${it.sourceId} refs=${it.sourceReferences.take(3).joinToString()} uncertainty=${it.uncertainty.orEmpty().take(160)}"
        }

        return TrudyFormattedModelInput(
            systemInstruction = request.systemInstruction,
            userMessage = request.userRequest.trim().take(MAX_TEXT_CHARS),
            conversation = conversation,
            tools = tools,
            toolResults = results,
            evidenceIndex = evidence,
            iteration = request.iteration,
            knowledge = knowledge,
            answerPlan = answerPlan?.renderForModel().orEmpty()
        )
    }

    private fun prioritizeResultsForPlan(
        results: List<TrudyToolResult>,
        plan: TrudyAnswerPlan
    ): List<TrudyToolResult> {
        val relevant = results.filter { result -> result.isAggregateResult() || result.matchesPlan(plan) }
        if (relevant.isNotEmpty()) return relevant.distinct()
        // If the plan consists entirely of missing-data findings, preserve a few typed results so
        // the language model can explain what is absent without receiving the full retrieval dump.
        return results.filterNot { it is TrudyToolResult.Failure }.take(MIN_FALLBACK_RESULT_BLOCKS)
    }

    private fun TrudyToolResult.isAggregateResult(): Boolean = when (this) {
        is PersonalTrendResult,
        is BaselineComparisonResult,
        is AssociationToolResult,
        is ExperimentEvaluationResult,
        is CanonicalExperimentEvaluationResult,
        is ChangeInvestigationResult -> true
        else -> false
    }

    private fun TrudyToolResult.matchesPlan(plan: TrudyAnswerPlan): Boolean {
        if (this is TrudyToolResult.DataQuality) {
            return plan.limitations.any { it.domain == operation.domain }
        }
        if (this is SystemAvailabilityResult) {
            return plan.rankedEvidence.any { it.kind == TrudyAnswerEvidenceKind.AVAILABILITY } ||
                plan.limitations.any { it.kind == TrudyAnswerEvidenceKind.AVAILABILITY }
        }
        if (this is CanonicalExperimentsResult || this is ExperimentHypothesisResult) {
            return plan.intent == TrudyAnswerIntent.EXPERIMENT
        }
        val refs = evidenceReferences(this)
        return refs.any { reference -> plan.rankedEvidence.any { it.matches(reference) } }
    }

    private fun renderResult(
        result: TrudyToolResult,
        rowLimit: Int,
        selectedKeys: Set<Pair<HealthDomain, String>>
    ): String = when (result) {
        is TrudyToolResult.DomainState -> "domain_state ${result.operation.domain}: ${result.evidence.filtered(selectedKeys).take(rowLimit).joinToString { it.renderCompact() }}"
        is TrudyToolResult.MetricHistory -> "metric_history ${result.operation.domain}/${result.operation.metricId}: ${result.evidence.filtered(selectedKeys).take(rowLimit).joinToString { it.renderCompact() }}"
        is TrudyToolResult.MetricWindow -> "metric_window ${result.operation.domain}/${result.operation.metricId} range=${result.operation.range.fromEpochMs}-${result.operation.range.toEpochMs}: ${result.evidence.filtered(selectedKeys).take(rowLimit).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DomainHistory -> "domain_history ${result.operation.domain}: ${result.evidence.filtered(selectedKeys).take(rowLimit).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DerivedFeatures -> "derived ${result.operation.domain}: ${result.evidence.filteredDerived(selectedKeys).take(rowLimit).joinToString { it.renderCompact() }}"
        is TrudyToolResult.Insights -> "insights ${result.operation.domain}: ${result.evidence.take(rowLimit).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DataQuality -> "data_quality ${result.operation.domain}: ${result.evidence.renderCompact()}"
        is TrudyToolResult.Context -> {
            "context ${result.context.requestedDomains.joinToString()}: " + result.context.domains.joinToString(" | ") { d ->
                val current = d.currentState.filtered(selectedKeys)
                val history = d.history.filtered(selectedKeys)
                val derived = d.derivedFeatures.filteredDerived(selectedKeys)
                "${d.domain} current=${current.take(rowLimit).joinToString { it.renderCompact() }} " +
                    "history=${history.take(rowLimit).joinToString { it.renderCompact() }} " +
                    "derived=${derived.take(rowLimit).joinToString { it.renderCompact() }} " +
                    "insights=${d.insights.take(rowLimit).joinToString { it.renderCompact() }} quality=${d.dataQuality?.renderCompact() ?: "n/a"}"
            }
        }
        is PersonalTrendResult -> "personal_trend ${result.comparison.domain}/${result.comparison.metricId} recent=${result.comparison.observationMean ?: "n/a"} baseline=${result.comparison.baselineMean ?: "n/a"} delta=${result.comparison.absoluteDelta ?: "n/a"} samples=${result.comparison.observationSampleCount}/${result.comparison.baselineSampleCount} confidence=${result.comparison.confidence}"
        is BaselineComparisonResult -> "baseline_comparison ${result.comparison.domain}/${result.comparison.metricId} recent=${result.comparison.observationMean ?: "n/a"} baseline=${result.comparison.baselineMean ?: "n/a"} delta=${result.comparison.absoluteDelta ?: "n/a"} samples=${result.comparison.observationSampleCount}/${result.comparison.baselineSampleCount} confidence=${result.comparison.confidence}"
        is AssociationToolResult -> "personal_association ${result.association.leftDomain}/${result.association.leftMetricId} vs ${result.association.rightDomain}/${result.association.rightMetricId} method=${result.association.method} coefficient=${result.association.coefficient ?: "n/a"} samples=${result.association.sampleCount} lagMs=${result.association.lagMs} confidence=${result.association.confidence}"
        is ExperimentHypothesisResult -> "experiment_hypothesis ${result.hypothesis.id} target=${result.hypothesis.targetDomain}/${result.hypothesis.targetMetricId} duration=${result.hypothesis.suggestedDurationDays} intervention=${result.hypothesis.intervention.take(240)}"
        is ExperimentEvaluationResult -> "experiment_result ${result.result.hypothesisId} target=${result.result.targetDomain}/${result.result.targetMetricId} baseline=${result.result.baselineMean ?: "n/a"} intervention=${result.result.interventionMean ?: "n/a"} change=${result.result.absoluteChange ?: "n/a"} confidence=${result.result.confidence} conclusion=${result.result.conclusion}"
        is ChangeInvestigationResult -> "change_investigation premise=${result.investigation.premiseAssessment} observation=${result.investigation.observationWindow.fromEpochMs}-${result.investigation.observationWindow.toEpochMs} baseline=${result.investigation.baselineWindow.fromEpochMs}-${result.investigation.baselineWindow.toEpochMs} targets=${result.investigation.targetComparisons.joinToString { "${it.domain}/${it.metricId}:delta=${it.absoluteDelta ?: "n/a"},samples=${it.observationSampleCount}/${it.baselineSampleCount},confidence=${it.confidence}" }} related=${result.investigation.relatedAssociations.joinToString { "${it.leftDomain}/${it.leftMetricId}~${it.rightDomain}/${it.rightMetricId}:r=${it.coefficient ?: "n/a"},n=${it.sampleCount},confidence=${it.confidence}" }} missing=${result.investigation.missingMetrics.joinToString { "${it.first}/${it.second}" }}"
        is CanonicalExperimentsResult -> "canonical_experiments persistence=${result.persistenceState} records=${result.experiments.take(rowLimit).joinToString { "${it.id}:${it.status}:${it.title}:target=${it.targetDomain}/${it.targetMetricId}" }}"
        is CanonicalExperimentEvaluationResult -> "canonical_experiment_evaluation persistence=${result.persistenceState} experiment=${result.experiment?.id ?: "none"} result=${result.evaluation?.summary ?: "unavailable"}"
        is SystemAvailabilityResult -> "system_availability unavailable=${result.unavailableReasons.entries.take(rowLimit).joinToString { "${it.key}:${it.value}" }}"
        is TrudyToolResult.Failure -> "tool_failure ${result.operation}: ${result.code} ${result.message.take(MAX_TEXT_CHARS)}"
    }

    private fun List<TrudyMetricEvidence>.filtered(keys: Set<Pair<HealthDomain, String>>): List<TrudyMetricEvidence> =
        if (keys.isEmpty()) this else filter { (it.domain to it.metricId) in keys }

    private fun List<TrudyDerivedMetricEvidence>.filteredDerived(keys: Set<Pair<HealthDomain, String>>): List<TrudyDerivedMetricEvidence> =
        if (keys.isEmpty()) this else filter { (it.domain to it.metricId) in keys }

    private fun TrudyMetricEvidence.renderCompact() = buildString {
        append("${domain.name}/$metricId=$value $unit @${timestampEpochMs} source=$source")
        append(" class=").append(valueClass.name)
        confidence?.let { append(" confidence=").append(it) }
        confidenceLabel?.let { append(" confidenceLabel=").append(it) }
        algorithmVersion?.let { append(" algorithm=").append(it) }
        sessionId?.let { append(" session=").append(it) }
        deviceName?.let { append(" device=").append(it) }
        deviceId?.let { append(" deviceId=").append(it) }
        coverageFraction?.let { append(" coverage=").append(it) }
        caveat?.let { append(" caveat=").append(it.take(MAX_TEXT_CHARS)) }
        dataQuality?.let { append(" quality=").append(it.score).append("/100 stale=").append(it.isStale) }
        if (metadata.isNotEmpty()) append(" metadata=").append(
            metadata.entries.take(MAX_METADATA_FIELDS).joinToString { "${it.key}=${it.value.take(MAX_METADATA_VALUE_CHARS)}" }
        )
    }

    private fun TrudyDerivedMetricEvidence.renderCompact() =
        "${domain.name}/$metricId latest=$latest $unit mean=$mean samples=$sampleCount change=${change ?: "n/a"} range=${range.fromEpochMs}-${range.toEpochMs} source=$source"

    private fun TrudyInsightEvidence.renderCompact() =
        "${domain.name}/$id kind=${evidenceKind.name} confidence=${confidence ?: "n/a"} evidence=${evidenceMetricIds.joinToString()} title=${title.take(180)} source=$source"

    private fun TrudyDataQualityEvidence.renderCompact() =
        "${domain.name} score=$score records=$recordCount metrics=$distinctMetricCount stale=$isStale notes=${notes.joinToString(";").take(240)}"

    private companion object {
        const val MAX_TEXT_CHARS = 2_000
        const val MAX_FINAL_CONVERSATION_TEXT_CHARS = 1_000
        const val MAX_EVIDENCE_INDEX = 128
        const val MAX_FINAL_EVIDENCE_INDEX = 32
        const val MAX_KNOWLEDGE_ITEMS = 8
        const val MAX_FINAL_KNOWLEDGE_ITEMS = 6
        const val MAX_KNOWLEDGE_SUMMARY_CHARS = 500
        const val MAX_FINAL_KNOWLEDGE_SUMMARY_CHARS = 360
        const val MAX_FINAL_RESULT_BLOCKS = 8
        const val MIN_FALLBACK_RESULT_BLOCKS = 4
        const val MAX_METADATA_FIELDS = 6
        const val MAX_METADATA_VALUE_CHARS = 100
    }
}

interface LocalTrudyModelEngine {
    val engineId: String
    suspend fun generate(input: TrudyFormattedModelInput): LocalTrudyModelResponse
}

data class LocalTrudyModelResponse(
    val responseText: String? = null,
    val requestedTools: List<TrudyToolOperation> = emptyList(),
    val evidenceReferences: List<TrudyEvidenceReference> = emptyList(),
    val attributes: Map<String, String> = emptyMap()
)

class LocalTrudyModelClient(
    private val engine: LocalTrudyModelEngine,
    private val formatter: TrudyPromptFormatter = TrudyPromptFormatter()
) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val r = engine.generate(formatter.format(request))
        return TrudyModelResult(
            r.responseText,
            r.requestedTools,
            r.evidenceReferences,
            TrudyModelMetadata(provider = "local", model = engine.engineId, attributes = r.attributes)
        )
    }
}

class OfflineDeterministicTrudyModelClient(
    private val answerEngine: TrudyAnswerEngine = TrudyAnswerEngine()
) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val metadata = TrudyModelMetadata(provider = "offline", model = MODEL_ID)
        if (request.toolResults.isEmpty()) {
            val tools = toolPlan(request.userRequest)
            return if (tools.isEmpty()) {
                TrudyModelResult(
                    responseText = "I can answer general questions offline. For personal health questions I use Project Superhuman's structured tools rather than guessing.",
                    metadata = metadata
                )
            } else TrudyModelResult(requestedTools = tools, metadata = metadata)
        }
        val successful = request.toolResults.filter { it !is TrudyToolResult.Failure }
        if (successful.isEmpty()) {
            return TrudyModelResult(
                responseText = "I couldn't access the requested health data reliably, so I won't guess.",
                metadata = metadata
            )
        }
        val answerPlan = request.answerPlan ?: answerEngine.plan(
            question = request.userRequest,
            results = successful,
            conversation = request.conversationContext,
            systemInstruction = request.systemInstruction
        )
        return TrudyModelResult(
            responseText = answerEngine.synthesize(answerPlan),
            evidenceReferences = selectedEvidenceReferences(successful, answerPlan, 24),
            metadata = metadata
        )
    }

    private fun toolPlan(message: String): List<TrudyToolOperation> {
        val t = message.lowercase()
        return when {
            ("linked" in t || "affect" in t || "associated" in t) && "exercise" in t && "sleep" in t ->
                listOf(GetLaggedAssociation(HealthDomain.EXERCISE, "exercise_load", HealthDomain.SLEEP, "sleep_score", 12L * 3_600_000L, alignmentWindowMs = 8L * 3_600_000L))
            ("improved" in t || "improve" in t || "trend" in t) && "sleep" in t ->
                listOf(GetPersonalTrend(HealthDomain.SLEEP, "sleep_score"))
            "experiment" in t && !("did" in t || "work" in t || "result" in t) ->
                listOf(GenerateExperimentHypothesis(TrudyExperimentKind.SLEEP_SCHEDULE_CONSISTENCY, HealthDomain.SLEEP, "sleep_score"))
            "sleep" in t -> listOf(TrudyToolOperation.GetContext(TrudyContextRequest(listOf(HealthDomain.SLEEP), 30, includeHistory = false)))
            "changed" in t || "today" in t -> listOf(TrudyToolOperation.GetContext(TrudyContextRequest(HealthDomain.entries, 20, includeHistory = false)))
            "pay attention" in t || "attention" in t || "priority" in t -> listOf(TrudyToolOperation.GetContext(TrudyContextRequest(HealthDomain.entries, 10, includeCurrentState = false, includeHistory = false)))
            else -> emptyList()
        }
    }

    private companion object { const val MODEL_ID = "trudy-deterministic-v3-answer-engine" }
}

/**
 * Return references to evidence the Answer Engine actually selected, not every record touched by
 * retrieval. The final AnswerEngineModelClient performs a stricter temporal binding again before UI.
 */
internal fun selectedEvidenceReferences(
    results: List<TrudyToolResult>,
    plan: TrudyAnswerPlan,
    limit: Int = 24
): List<TrudyEvidenceReference> {
    if (limit <= 0 || plan.rankedEvidence.isEmpty()) return emptyList()
    val available = results.flatMap(::evidenceReferences).distinct()
    return plan.rankedEvidence.asSequence()
        .flatMap { evidence -> available.asSequence().filter { reference -> evidence.matches(reference) } }
        .distinct()
        .take(limit)
        .toList()
}

private fun TrudyAnswerEvidence.matches(reference: TrudyEvidenceReference): Boolean {
    val associationMetrics = listOfNotNull(associationLeftMetricId, associationRightMetricId).toSet()
    if (kind == TrudyAnswerEvidenceKind.ASSOCIATION && reference.metricId in associationMetrics) {
        return true
    }
    if (domain != null && reference.domain != domain) return false
    if (metricId == null || reference.metricId != metricId) return false
    if (timestampEpochMs != null && reference.timestampEpochMs != null && reference.timestampEpochMs != timestampEpochMs) return false
    if (range != null && reference.range != null && reference.range != range) return false
    return true
}

internal fun evidenceReferences(result: TrudyToolResult): List<TrudyEvidenceReference> = when (result) {
    is TrudyToolResult.DomainState -> result.evidence.map { it.toReference() }
    is TrudyToolResult.MetricHistory -> result.evidence.map { it.toReference() }
    is TrudyToolResult.MetricWindow -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DomainHistory -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DerivedFeatures -> result.evidence.map { it.toReference() }
    is TrudyToolResult.Insights -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DataQuality -> emptyList()
    is TrudyToolResult.Context -> result.context.domains.flatMap { d ->
        d.currentState.map { it.toReference() } + d.history.map { it.toReference() } +
            d.derivedFeatures.map { it.toReference() } + d.insights.map { it.toReference() }
    }
    is PersonalTrendResult -> result.comparison.evidence.supportingEvidenceReferences
    is BaselineComparisonResult -> result.comparison.evidence.supportingEvidenceReferences
    is AssociationToolResult -> result.association.evidence.supportingEvidenceReferences
    is ExperimentHypothesisResult -> result.hypothesis.evidenceBasis.flatMap { it.supportingEvidenceReferences }.distinct()
    is ExperimentEvaluationResult -> result.result.evidence.supportingEvidenceReferences
    is ChangeInvestigationResult -> result.investigation.targetComparisons.flatMap { it.evidence.supportingEvidenceReferences } +
        result.investigation.relatedAssociations.flatMap { it.evidence.supportingEvidenceReferences }
    is CanonicalExperimentsResult -> emptyList()
    is CanonicalExperimentEvaluationResult -> result.evaluation?.evidence?.supportingEvidenceReferences.orEmpty()
    is SystemAvailabilityResult -> emptyList()
    is TrudyToolResult.Failure -> emptyList()
}

private fun TrudyMetricEvidence.toReference() =
    TrudyEvidenceReference(domain, metricId, evidenceKind = evidenceKind, timestampEpochMs = timestampEpochMs)

private fun TrudyDerivedMetricEvidence.toReference() =
    TrudyEvidenceReference(domain, metricId, evidenceKind = evidenceKind, range = range)

private fun TrudyInsightEvidence.toReference() =
    TrudyEvidenceReference(domain, insightId = id, evidenceKind = evidenceKind)

private fun TrudyEvidenceReference.renderKey() = buildString {
    append(domain.name).append('/').append(metricId ?: insightId ?: evidenceKind.name).append(" kind=").append(evidenceKind.name)
    timestampEpochMs?.let { append(" at=").append(it) }
    range?.let { append(" range=").append(it.fromEpochMs).append('-').append(it.toEpochMs) }
}
