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
        appendLine("\nTOOLS"); appendLine(tools.ifBlank { "(none)" })
        appendLine("\nTOOL RESULTS"); appendLine(toolResults.ifBlank { "(none)" })
        appendLine("\nEVIDENCE INDEX"); appendLine(evidenceIndex.ifBlank { "(none)" })
        appendLine("\nRETRIEVED KNOWLEDGE"); appendLine(knowledge.ifBlank { "(none)" })
        appendLine("\nANSWER PLAN"); appendLine(answerPlan.ifBlank { "(none)" })
        append("\nITERATION ").append(iteration)
    }
}

class TrudyPromptFormatter(private val maxConversationTurns: Int = 12, private val maxEvidenceRowsPerResult: Int = 24) {
    init { require(maxConversationTurns > 0); require(maxEvidenceRowsPerResult > 0) }

    fun format(request: TrudyModelRequest): TrudyFormattedModelInput {
        val conversation = request.conversationContext.takeLast(maxConversationTurns).joinToString("\n") {
            buildString {
                append(it.role.name).append(": ").append(it.text.trim().take(MAX_TEXT_CHARS))
                if (it.evidenceKeys.isNotEmpty()) append(" evidence=").append(it.evidenceKeys.take(12).joinToString())
            }
        }
        val tools = request.toolDefinitions.joinToString("\n") { "${it.name}(${it.requiredFields.joinToString()}): ${it.description}" }
        val results = request.toolResults.joinToString("\n", transform = ::renderResult)
        val evidence = request.toolResults.flatMap(::evidenceReferences).distinct().take(MAX_EVIDENCE_INDEX).joinToString("\n") { it.renderKey() }
        val knowledge = request.knowledgeContext.take(MAX_KNOWLEDGE_ITEMS).joinToString("\n") {
            "${it.kind}/${it.stableId} title=${it.title.take(120)} summary=${it.summary.take(500)} source=${it.sourceId} refs=${it.sourceReferences.take(3).joinToString()} uncertainty=${it.uncertainty.orEmpty().take(180)}"
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
            answerPlan = request.answerPlan?.renderForModel().orEmpty()
        )
    }

    private fun renderResult(result: TrudyToolResult): String = when (result) {
        is TrudyToolResult.DomainState -> "domain_state ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.MetricHistory -> "metric_history ${result.operation.domain}/${result.operation.metricId}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.MetricWindow -> "metric_window ${result.operation.domain}/${result.operation.metricId} range=${result.operation.range.fromEpochMs}-${result.operation.range.toEpochMs}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DomainHistory -> "domain_history ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DerivedFeatures -> "derived ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.Insights -> "insights ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DataQuality -> "data_quality ${result.operation.domain}: ${result.evidence.renderCompact()}"
        is TrudyToolResult.Context -> "context ${result.context.requestedDomains.joinToString()}: ${result.context.domains.joinToString(" | ") { d -> "${d.domain} current=${d.currentState.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }} derived=${d.derivedFeatures.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }} insights=${d.insights.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }} quality=${d.dataQuality?.renderCompact() ?: "n/a"}" }}"
        is PersonalTrendResult -> "personal_trend ${result.comparison.domain}/${result.comparison.metricId} recent=${result.comparison.observationMean ?: "n/a"} baseline=${result.comparison.baselineMean ?: "n/a"} delta=${result.comparison.absoluteDelta ?: "n/a"} samples=${result.comparison.observationSampleCount}/${result.comparison.baselineSampleCount} confidence=${result.comparison.confidence}"
        is BaselineComparisonResult -> "baseline_comparison ${result.comparison.domain}/${result.comparison.metricId} delta=${result.comparison.absoluteDelta ?: "n/a"} confidence=${result.comparison.confidence}"
        is AssociationToolResult -> "personal_association ${result.association.leftDomain}/${result.association.leftMetricId} vs ${result.association.rightDomain}/${result.association.rightMetricId} method=${result.association.method} coefficient=${result.association.coefficient ?: "n/a"} samples=${result.association.sampleCount} lagMs=${result.association.lagMs} confidence=${result.association.confidence} caveat=association_not_causation"
        is ExperimentHypothesisResult -> "experiment_hypothesis ${result.hypothesis.id} target=${result.hypothesis.targetDomain}/${result.hypothesis.targetMetricId} duration=${result.hypothesis.suggestedDurationDays} intervention=${result.hypothesis.intervention.take(240)}"
        is ExperimentEvaluationResult -> "experiment_result ${result.result.hypothesisId} target=${result.result.targetDomain}/${result.result.targetMetricId} baseline=${result.result.baselineMean ?: "n/a"} intervention=${result.result.interventionMean ?: "n/a"} change=${result.result.absoluteChange ?: "n/a"} confidence=${result.result.confidence} conclusion=${result.result.conclusion}"
        is ChangeInvestigationResult -> "change_investigation premise=${result.investigation.premiseAssessment} observation=${result.investigation.observationWindow.fromEpochMs}-${result.investigation.observationWindow.toEpochMs} baseline=${result.investigation.baselineWindow.fromEpochMs}-${result.investigation.baselineWindow.toEpochMs} targets=${result.investigation.targetComparisons.joinToString { "${it.domain}/${it.metricId}:delta=${it.absoluteDelta ?: "n/a"},samples=${it.observationSampleCount}/${it.baselineSampleCount}" }} related=${result.investigation.relatedAssociations.joinToString { "${it.leftDomain}/${it.leftMetricId}~${it.rightDomain}/${it.rightMetricId}:r=${it.coefficient ?: "n/a"},n=${it.sampleCount}" }} missing=${result.investigation.missingMetrics.joinToString { "${it.first}/${it.second}" }} caveat=${result.investigation.caveats.joinToString()}"
        is CanonicalExperimentsResult -> "canonical_experiments persistence=${result.persistenceState} records=${result.experiments.joinToString { "${it.id}:${it.status}:${it.title}:target=${it.targetDomain}/${it.targetMetricId}" }}"
        is CanonicalExperimentEvaluationResult -> "canonical_experiment_evaluation persistence=${result.persistenceState} experiment=${result.experiment?.id ?: "none"} result=${result.evaluation?.summary ?: "unavailable"}"
        is SystemAvailabilityResult -> "system_availability unavailable=${result.unavailableReasons.entries.joinToString { "${it.key}:${it.value}" }}"
        is TrudyToolResult.Failure -> "tool_failure ${result.operation}: ${result.code} ${result.message.take(MAX_TEXT_CHARS)}"
    }

    private fun TrudyMetricEvidence.renderCompact() = buildString {
        append("${domain.name}/$metricId=$value $unit @${timestampEpochMs} source=$source")
        confidence?.let { append(" confidence=").append(it) }
        dataQuality?.let { append(" quality=").append(it.score).append("/100 stale=").append(it.isStale) }
        if (metadata.isNotEmpty()) append(" metadata=").append(
            metadata.entries.take(MAX_METADATA_FIELDS).joinToString { "${it.key}=${it.value.take(MAX_METADATA_VALUE_CHARS)}" }
        )
    }
    private fun TrudyDerivedMetricEvidence.renderCompact() = "${domain.name}/$metricId latest=$latest $unit mean=$mean samples=$sampleCount change=${change ?: "n/a"} range=${range.fromEpochMs}-${range.toEpochMs}"
    private fun TrudyInsightEvidence.renderCompact() = "${domain.name}/$id kind=${evidenceKind.name} confidence=${confidence ?: "n/a"} evidence=${evidenceMetricIds.joinToString()} title=${title.take(180)}"
    private fun TrudyDataQualityEvidence.renderCompact() = "${domain.name} score=$score records=$recordCount metrics=$distinctMetricCount stale=$isStale notes=${notes.joinToString(";").take(240)}"
    private companion object {
        const val MAX_TEXT_CHARS = 2_000
        const val MAX_EVIDENCE_INDEX = 128
        const val MAX_KNOWLEDGE_ITEMS = 8
        const val MAX_METADATA_FIELDS = 8
        const val MAX_METADATA_VALUE_CHARS = 120
    }
}

interface LocalTrudyModelEngine { val engineId: String; suspend fun generate(input: TrudyFormattedModelInput): LocalTrudyModelResponse }
data class LocalTrudyModelResponse(val responseText: String? = null, val requestedTools: List<TrudyToolOperation> = emptyList(), val evidenceReferences: List<TrudyEvidenceReference> = emptyList(), val attributes: Map<String,String> = emptyMap())
class LocalTrudyModelClient(private val engine: LocalTrudyModelEngine, private val formatter: TrudyPromptFormatter = TrudyPromptFormatter()) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val r=engine.generate(formatter.format(request)); return TrudyModelResult(r.responseText,r.requestedTools,r.evidenceReferences,TrudyModelMetadata(provider="local",model=engine.engineId,attributes=r.attributes))
    }
}

class OfflineDeterministicTrudyModelClient(
    private val answerEngine: TrudyAnswerEngine = TrudyAnswerEngine()
) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val metadata=TrudyModelMetadata(provider="offline",model=MODEL_ID)
        if(request.toolResults.isEmpty()) {
            val tools=toolPlan(request.userRequest)
            return if(tools.isEmpty()) TrudyModelResult(responseText="I can answer general questions offline. For personal health questions I use Project Superhuman's structured tools rather than guessing.",metadata=metadata) else TrudyModelResult(requestedTools=tools,metadata=metadata)
        }
        val successful=request.toolResults.filter { it !is TrudyToolResult.Failure }
        if(successful.isEmpty()) return TrudyModelResult(responseText="I couldn't access the requested health data reliably, so I won't guess.",metadata=metadata)
        val answerPlan = answerEngine.plan(
            question = request.userRequest,
            results = successful,
            conversation = request.conversationContext,
            systemInstruction = request.systemInstruction
        )
        return TrudyModelResult(
            responseText = answerEngine.synthesize(answerPlan),
            evidenceReferences = successful.flatMap(::evidenceReferences).distinct().take(24),
            metadata = metadata
        )
    }

    private fun toolPlan(message:String):List<TrudyToolOperation> {
        val t=message.lowercase()
        return when {
            ("linked" in t || "affect" in t || "associated" in t) && "exercise" in t && "sleep" in t -> listOf(GetLaggedAssociation(HealthDomain.EXERCISE,"exercise_load",HealthDomain.SLEEP,"sleep_score",12L*3_600_000L,alignmentWindowMs=8L*3_600_000L))
            ("improved" in t || "improve" in t || "trend" in t) && "sleep" in t -> listOf(GetPersonalTrend(HealthDomain.SLEEP,"sleep_score"))
            "experiment" in t && !("did" in t || "work" in t || "result" in t) -> listOf(GenerateExperimentHypothesis(TrudyExperimentKind.SLEEP_SCHEDULE_CONSISTENCY,HealthDomain.SLEEP,"sleep_score"))
            "sleep" in t -> listOf(TrudyToolOperation.GetContext(TrudyContextRequest(listOf(HealthDomain.SLEEP),30,includeHistory=false)))
            "changed" in t || "today" in t -> listOf(TrudyToolOperation.GetContext(TrudyContextRequest(HealthDomain.entries,20,includeHistory=false)))
            "pay attention" in t || "attention" in t || "priority" in t -> listOf(TrudyToolOperation.GetContext(TrudyContextRequest(HealthDomain.entries,10,includeCurrentState=false,includeHistory=false)))
            else -> emptyList()
        }
    }

    private companion object { const val MODEL_ID="trudy-deterministic-v3-answer-engine" }
}

internal fun evidenceReferences(result:TrudyToolResult):List<TrudyEvidenceReference> = when(result) {
    is TrudyToolResult.DomainState -> result.evidence.map { it.toReference() }
    is TrudyToolResult.MetricHistory -> result.evidence.map { it.toReference() }
    is TrudyToolResult.MetricWindow -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DomainHistory -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DerivedFeatures -> result.evidence.map { it.toReference() }
    is TrudyToolResult.Insights -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DataQuality -> emptyList()
    is TrudyToolResult.Context -> result.context.domains.flatMap { d -> d.currentState.map { it.toReference() } + d.history.map { it.toReference() } + d.derivedFeatures.map { it.toReference() } + d.insights.map { it.toReference() } }
    is PersonalTrendResult -> result.comparison.evidence.supportingEvidenceReferences
    is BaselineComparisonResult -> result.comparison.evidence.supportingEvidenceReferences
    is AssociationToolResult -> result.association.evidence.supportingEvidenceReferences
    is ExperimentHypothesisResult -> result.hypothesis.evidenceBasis.flatMap { it.supportingEvidenceReferences }.distinct()
    is ExperimentEvaluationResult -> result.result.evidence.supportingEvidenceReferences
    is ChangeInvestigationResult -> result.investigation.targetComparisons.flatMap { it.evidence.supportingEvidenceReferences } + result.investigation.relatedAssociations.flatMap { it.evidence.supportingEvidenceReferences }
    is CanonicalExperimentsResult -> emptyList()
    is CanonicalExperimentEvaluationResult -> result.evaluation?.evidence?.supportingEvidenceReferences.orEmpty()
    is SystemAvailabilityResult -> emptyList()
    is TrudyToolResult.Failure -> emptyList()
}
private fun TrudyMetricEvidence.toReference()=TrudyEvidenceReference(domain,metricId,evidenceKind=evidenceKind,timestampEpochMs=timestampEpochMs)
private fun TrudyDerivedMetricEvidence.toReference()=TrudyEvidenceReference(domain,metricId,evidenceKind=evidenceKind,range=range)
private fun TrudyInsightEvidence.toReference()=TrudyEvidenceReference(domain,insightId=id,evidenceKind=evidenceKind)
private fun TrudyEvidenceReference.renderKey()=buildString { append(domain.name).append('/').append(metricId?:insightId?:evidenceKind.name).append(" kind=").append(evidenceKind.name); timestampEpochMs?.let { append(" at=").append(it) }; range?.let { append(" range=").append(it.fromEpochMs).append('-').append(it.toEpochMs) } }

