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
    val iteration: Int
) {
    fun asPrompt(): String = buildString {
        appendLine("SYSTEM"); appendLine(systemInstruction)
        appendLine("\nCONVERSATION"); appendLine(conversation.ifBlank { "(none)" })
        appendLine("\nUSER"); appendLine(userMessage)
        appendLine("\nTOOLS"); appendLine(tools.ifBlank { "(none)" })
        appendLine("\nTOOL RESULTS"); appendLine(toolResults.ifBlank { "(none)" })
        appendLine("\nEVIDENCE INDEX"); appendLine(evidenceIndex.ifBlank { "(none)" })
        append("\nITERATION ").append(iteration)
    }
}

class TrudyPromptFormatter(private val maxConversationTurns: Int = 12, private val maxEvidenceRowsPerResult: Int = 24) {
    init { require(maxConversationTurns > 0); require(maxEvidenceRowsPerResult > 0) }

    fun format(request: TrudyModelRequest): TrudyFormattedModelInput {
        val conversation = request.conversationContext.takeLast(maxConversationTurns).joinToString("\n") { "${it.role.name}: ${it.text.trim().take(MAX_TEXT_CHARS)}" }
        val tools = request.toolDefinitions.joinToString("\n") { "${it.name}(${it.requiredFields.joinToString()}): ${it.description}" }
        val results = request.toolResults.joinToString("\n", transform = ::renderResult)
        val evidence = request.toolResults.flatMap(::evidenceReferences).distinct().take(MAX_EVIDENCE_INDEX).joinToString("\n") { it.renderKey() }
        return TrudyFormattedModelInput(request.systemInstruction, request.userRequest.trim().take(MAX_TEXT_CHARS), conversation, tools, results, evidence, request.iteration)
    }

    private fun renderResult(result: TrudyToolResult): String = when (result) {
        is TrudyToolResult.DomainState -> "domain_state ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.MetricHistory -> "metric_history ${result.operation.domain}/${result.operation.metricId}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
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
        is TrudyToolResult.Failure -> "tool_failure ${result.operation}: ${result.code} ${result.message.take(MAX_TEXT_CHARS)}"
    }

    private fun TrudyMetricEvidence.renderCompact() = "${domain.name}/$metricId=$value $unit @${timestampEpochMs} source=$source"
    private fun TrudyDerivedMetricEvidence.renderCompact() = "${domain.name}/$metricId latest=$latest $unit mean=$mean samples=$sampleCount change=${change ?: "n/a"} range=${range.fromEpochMs}-${range.toEpochMs}"
    private fun TrudyInsightEvidence.renderCompact() = "${domain.name}/$id kind=${evidenceKind.name} confidence=${confidence ?: "n/a"} evidence=${evidenceMetricIds.joinToString()} title=${title.take(180)}"
    private fun TrudyDataQualityEvidence.renderCompact() = "${domain.name} score=$score records=$recordCount metrics=$distinctMetricCount stale=$isStale notes=${notes.joinToString(";").take(240)}"
    private companion object { const val MAX_TEXT_CHARS = 2_000; const val MAX_EVIDENCE_INDEX = 128 }
}

interface LocalTrudyModelEngine { val engineId: String; suspend fun generate(input: TrudyFormattedModelInput): LocalTrudyModelResponse }
data class LocalTrudyModelResponse(val responseText: String? = null, val requestedTools: List<TrudyToolOperation> = emptyList(), val evidenceReferences: List<TrudyEvidenceReference> = emptyList(), val attributes: Map<String,String> = emptyMap())
class LocalTrudyModelClient(private val engine: LocalTrudyModelEngine, private val formatter: TrudyPromptFormatter = TrudyPromptFormatter()) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val r=engine.generate(formatter.format(request)); return TrudyModelResult(r.responseText,r.requestedTools,r.evidenceReferences,TrudyModelMetadata(provider="local",model=engine.engineId,attributes=r.attributes))
    }
}

class OfflineDeterministicTrudyModelClient : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val metadata=TrudyModelMetadata(provider="offline",model=MODEL_ID)
        if(request.toolResults.isEmpty()) {
            val tools=toolPlan(request.userRequest)
            return if(tools.isEmpty()) TrudyModelResult(responseText="I can answer general questions offline. For personal health questions I use Project Superhuman's structured tools rather than guessing.",metadata=metadata) else TrudyModelResult(requestedTools=tools,metadata=metadata)
        }
        val successful=request.toolResults.filter { it !is TrudyToolResult.Failure }
        if(successful.isEmpty()) return TrudyModelResult(responseText="I couldn't access reliable structured evidence for that request, so I won't infer a personal result.",metadata=metadata)
        return TrudyModelResult(responseText=summarize(request.userRequest,successful),evidenceReferences=successful.flatMap(::evidenceReferences).distinct().take(24),metadata=metadata)
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

    private fun summarize(question:String,results:List<TrudyToolResult>):String {
        results.filterIsInstance<AssociationToolResult>().firstOrNull()?.let { a -> return if(a.association.coefficient==null) "There are not enough aligned personal samples to estimate that association reliably. Association is not causation." else "The structured calculation found a ${a.association.direction.name.lowercase()} association (${a.association.method.name.lowercase()}, coefficient ${fmt(a.association.coefficient)}, ${a.association.sampleCount} aligned samples, ${a.association.confidence.name.lowercase()} confidence). This is an association, not evidence of causation." }
        results.filterIsInstance<PersonalTrendResult>().firstOrNull()?.let { c -> return if(c.comparison.absoluteDelta==null) "There is not enough recent and baseline data to assess that trend reliably." else "The recent structured comparison is ${c.comparison.direction.name.lowercase()} by ${fmt(c.comparison.absoluteDelta)} in the metric's native units, with ${c.comparison.confidence.name.lowercase()} confidence. This is a descriptive personal trend." }
        results.filterIsInstance<ExperimentHypothesisResult>().firstOrNull()?.let { h -> return "A safe structured option is: ${h.hypothesis.intervention} Track ${h.hypothesis.targetMetricId} for ${h.hypothesis.suggestedDurationDays} days. This would create personal evidence, not universal or causal proof." }
        results.filterIsInstance<ExperimentEvaluationResult>().firstOrNull()?.let { return "${it.result.summary} Confidence: ${it.result.confidence.name.lowercase()}." }
        val contexts=results.filterIsInstance<TrudyToolResult.Context>().flatMap { it.context.domains }
        if(contexts.isEmpty()) return "I retrieved structured evidence, but there isn't enough bounded context here to produce a reliable personal summary."
        val nonEmpty=contexts.filter { it.currentState.isNotEmpty() || it.derivedFeatures.isNotEmpty() || it.insights.isNotEmpty() }
        if(nonEmpty.isEmpty()) return "There isn't enough stored personal data in the requested area yet to give you a reliable answer."
        val stale=contexts.filter { it.dataQuality?.isStale==true }.map { it.domain }
        val text=question.lowercase()
        val relevant=if("sleep" in text) nonEmpty.filter { it.domain==HealthDomain.SLEEP }.ifEmpty { nonEmpty } else nonEmpty
        val insights=relevant.flatMap { it.insights }.filter { it.title.isNotBlank() || it.explanation.isNotBlank() }.sortedByDescending { it.confidence ?: -1.0 }
        val trends=relevant.flatMap { it.derivedFeatures }.filter { it.change != null && kotlin.math.abs(it.change ?: 0.0) > 0.0001 }.sortedByDescending { kotlin.math.abs(it.change ?: 0.0) }
        return buildString {
            when {
                insights.isNotEmpty() -> {
                    append(when {
                        "pay attention" in text || "attention" in text || "priority" in text -> "The clearest thing to pay attention to is "
                        "changed" in text || "today" in text -> "The main thing that stands out is "
                        "sleep" in text -> "The clearest sleep signal is "
                        else -> "The clearest signal is "
                    })
                    insights.take(3).forEachIndexed { index, insight ->
                        if(index > 0) append(if(index==1) " Another useful signal: " else " Also: ")
                        val title=insight.title.trim().trimEnd('.')
                        val explanation=insight.explanation.trim()
                        append(title)
                        if(explanation.isNotBlank() && !explanation.equals(insight.title,ignoreCase=true)) {
                            append(". ").append(explanation)
                        } else append('.')
                        if((insight.confidence ?: 1.0) < 0.45) append(" I have low confidence in this signal so far.")
                    }
                }
                trends.isNotEmpty() -> {
                    append(if("sleep" in text) "The strongest sleep trend I can see is " else "The strongest trend I can see is ")
                    trends.take(3).forEachIndexed { index, trend ->
                        if(index>0) append(" Also, ")
                        val change=trend.change ?: 0.0
                        append(trend.metricId.replace('_',' '))
                            .append(" is trending ")
                            .append(if(change>0) "higher" else "lower")
                            .append(" by about ")
                            .append(fmt(kotlin.math.abs(change)))
                            .append(if(trend.unit.isBlank()) "" else " ${trend.unit}")
                    }
                    append(". These are observed personal trends, not explanations for why they changed.")
                }
                else -> append("I have recorded data here, but not enough interpreted evidence yet to turn it into a useful personal insight.")
            }
            if(stale.isNotEmpty()) append(" Treat that cautiously because some of the latest requested data is stale.")
        }
    }
    private fun fmt(v:Double)=((v*100).toInt()/100.0).toString()
    private companion object { const val MODEL_ID="trudy-deterministic-v2-intelligence" }
}

internal fun evidenceReferences(result:TrudyToolResult):List<TrudyEvidenceReference> = when(result) {
    is TrudyToolResult.DomainState -> result.evidence.map { it.toReference() }
    is TrudyToolResult.MetricHistory -> result.evidence.map { it.toReference() }
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
    is TrudyToolResult.Failure -> emptyList()
}
private fun TrudyMetricEvidence.toReference()=TrudyEvidenceReference(domain,metricId,evidenceKind=evidenceKind,timestampEpochMs=timestampEpochMs)
private fun TrudyDerivedMetricEvidence.toReference()=TrudyEvidenceReference(domain,metricId,evidenceKind=evidenceKind,range=range)
private fun TrudyInsightEvidence.toReference()=TrudyEvidenceReference(domain,insightId=id,evidenceKind=evidenceKind)
private fun TrudyEvidenceReference.renderKey()=buildString { append(domain.name).append('/').append(metricId?:insightId?:evidenceKind.name).append(" kind=").append(evidenceKind.name); timestampEpochMs?.let { append(" at=").append(it) }; range?.let { append(" range=").append(it.fromEpochMs).append('-').append(it.toEpochMs) } }
