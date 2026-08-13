package com.projectsuperhuman.next.trudy

enum class TrudyOrchestrationStatus { SUCCESS, FALLBACK }
enum class TrudyWarningKind { EMPTY_DATA, STALE_DATA, LOW_DATA_QUALITY, TOOL_FAILURE, UNSUPPORTED_TOOL, MALFORMED_TOOL_REQUEST, MODEL_FAILURE, ITERATION_LIMIT, UNBOUND_EVIDENCE_REFERENCE }
data class TrudyWarning(val kind: TrudyWarningKind, val message: String)
data class TrudyToolCallRecord(val operation: TrudyToolOperation, val succeeded: Boolean, val failureCode: TrudyToolFailureCode? = null)
data class TrudyAskRequest(val userMessage: String, val conversationContext: List<TrudyConversationTurn> = emptyList(), val preselectedContext: TrudyContextRequest? = null)
data class TrudyOrchestrationResult(val status: TrudyOrchestrationStatus, val answerText: String, val evidenceReferences: List<TrudyEvidenceReference>, val toolCallsMade: List<TrudyToolCallRecord>, val warnings: List<TrudyWarning>, val modelMetadata: TrudyModelMetadata? = null)

/** Bounded provider-neutral model/tool loop. All important health math remains inside typed tools. */
class TrudyOrchestrator(private val modelClient: TrudyModelClient, private val tools: TrudyToolExecutor, private val maxModelIterations: Int = 4) {
    init { require(maxModelIterations > 0) }

    suspend fun ask(request: TrudyAskRequest): TrudyOrchestrationResult {
        if(request.userMessage.isBlank()) return fallback("Please ask a question so I can help.",warnings=listOf(TrudyWarning(TrudyWarningKind.MODEL_FAILURE,"User message was empty.")))
        val results=mutableListOf<TrudyToolResult>(); val calls=mutableListOf<TrudyToolCallRecord>(); var context:TrudyHealthContext?=null; var metadata:TrudyModelMetadata?=null
        request.preselectedContext?.let { c -> val r=executeSafely(TrudyToolOperation.GetContext(c)); results+=r; calls+=r.callRecord(); if(r is TrudyToolResult.Context) context=r.context }
        repeat(maxModelIterations) { iteration ->
            val model=try { modelClient.complete(TrudyModelRequest(request.userMessage,TrudyModelPolicy.SYSTEM_INSTRUCTION,request.conversationContext,context,tools.definitions,results.toList(),iteration)) } catch(_:Throwable) { return fallback("I couldn't complete the reasoning step reliably. Please try again.",calls,deriveWarnings(results)+TrudyWarning(TrudyWarningKind.MODEL_FAILURE,"The model provider failed."),metadata) }
            metadata=model.metadata?:metadata
            if(model.requestedTools.isEmpty()) {
                val answer=model.responseText?.trim()
                if(answer.isNullOrEmpty()) return fallback("I don't have enough reliable information to answer that yet.",calls,deriveWarnings(results)+TrudyWarning(TrudyWarningKind.MODEL_FAILURE,"Model returned neither tools nor an answer."),metadata)
                val failures=results.filterIsInstance<TrudyToolResult.Failure>()
                if(failures.isNotEmpty() && results.count { it !is TrudyToolResult.Failure }==0) return fallback("I couldn't access the requested health data reliably, so I won't guess.",calls,deriveWarnings(results),metadata)
                val available=results.flatMap(::evidenceReferencesFrom)
                val bound=model.evidenceReferences.mapNotNull { requested -> available.firstOrNull { it.matches(requested) } }.distinct()
                val dropped=model.evidenceReferences.size-bound.size
                val warnings=buildList { addAll(deriveWarnings(results)); if(dropped>0) add(TrudyWarning(TrudyWarningKind.UNBOUND_EVIDENCE_REFERENCE,"$dropped model evidence reference(s) were rejected because no matching tool evidence existed.")) }
                return TrudyOrchestrationResult(TrudyOrchestrationStatus.SUCCESS,answer,bound,calls.toList(),warnings.distinct(),metadata)
            }
            if(iteration==maxModelIterations-1) return fallback("I couldn't finish this health-data request within the safe tool limit.",calls,deriveWarnings(results)+TrudyWarning(TrudyWarningKind.ITERATION_LIMIT,"Maximum model/tool iterations exceeded."),metadata)
            model.requestedTools.forEach { op -> val r=executeSafely(op); results+=r; calls+=r.callRecord() }
        }
        return fallback("I couldn't finish this request reliably.",calls,listOf(TrudyWarning(TrudyWarningKind.ITERATION_LIMIT,"Maximum model/tool iterations exceeded.")),metadata)
    }

    private suspend fun executeSafely(operation:TrudyToolOperation):TrudyToolResult {
        if(operation is TrudyToolOperation.Unsupported) return TrudyToolResult.Failure(operation,TrudyToolFailureCode.UNSUPPORTED_OPERATION,"Unsupported Trudy tool: ${operation.name}")
        validate(operation)?.let { return TrudyToolResult.Failure(operation,TrudyToolFailureCode.MALFORMED_REQUEST,it) }
        return try { tools.execute(operation) } catch(_:Throwable) { TrudyToolResult.Failure(operation,TrudyToolFailureCode.EXECUTION_FAILED,"Typed Trudy tool execution failed.") }
    }

    private fun validate(op:TrudyToolOperation):String?=when(op) {
        is TrudyToolOperation.GetMetricHistory -> when { op.metricId.isBlank()->"metricId must not be blank"; op.limit !in 1..5000->"limit must be between 1 and 5000"; op.offset<0->"offset must be >= 0"; else->null }
        is TrudyToolOperation.GetDomainHistory -> when { op.limit !in 1..5000->"limit must be between 1 and 5000"; op.offset<0->"offset must be >= 0"; else->null }
        is TrudyToolOperation.GetContext -> if(op.domains.isEmpty()) "Cross-domain context must explicitly list domains" else if(op.request.historyLimitPerDomain !in 1..5000) "historyLimitPerDomain must be between 1 and 5000" else null
        is GetPersonalTrend -> if(op.metricId.isBlank()) "metricId must not be blank" else null
        is CompareBaseline -> if(op.metricId.isBlank()) "metricId must not be blank" else null
        is GetAssociation -> if(op.leftMetricId.isBlank()||op.rightMetricId.isBlank()) "Association metrics must be domain-qualified and non-blank" else if(op.domains != listOf(op.leftDomain,op.rightDomain).distinct()) "Association domains were not preserved" else null
        is GetLaggedAssociation -> if(op.leftMetricId.isBlank()||op.rightMetricId.isBlank()) "Association metrics must be domain-qualified and non-blank" else if(op.lagMs !in 0..604_800_000L) "lagMs must be between 0 and seven days" else null
        is GenerateExperimentHypothesis -> if(op.targetMetricId.isBlank()) "targetMetricId must not be blank" else null
        is EvaluateExperiment -> if(op.adherenceFraction !in 0.0..1.0) "adherenceFraction must be between 0 and 1" else null
        is TrudyToolOperation.Unsupported -> "Unsupported tool operation"
        else -> if(op.domains.size!=1) "Single-domain tool must contain exactly one domain" else null
    }

    private fun deriveWarnings(results:List<TrudyToolResult>):List<TrudyWarning> = buildList {
        results.forEach { r -> when(r) {
            is TrudyToolResult.DomainState -> if(r.evidence.isEmpty()) add(emptyWarning("No current data was available for ${r.operation.domain}."))
            is TrudyToolResult.MetricHistory -> if(r.evidence.isEmpty()) add(emptyWarning("No history was available for ${r.operation.domain}/${r.operation.metricId}."))
            is TrudyToolResult.DomainHistory -> if(r.evidence.isEmpty()) add(emptyWarning("No history was available for ${r.operation.domain}."))
            is TrudyToolResult.DerivedFeatures -> if(r.evidence.isEmpty()) add(emptyWarning("No derived features were available for ${r.operation.domain}."))
            is TrudyToolResult.Insights -> if(r.evidence.isEmpty()) add(emptyWarning("No insights were available for ${r.operation.domain}."))
            is TrudyToolResult.DataQuality -> addAll(qualityWarnings(r.evidence))
            is TrudyToolResult.Context -> r.context.domains.forEach { d -> if(d.currentState.isEmpty()&&d.history.isEmpty()&&d.derivedFeatures.isEmpty()&&d.insights.isEmpty()) add(emptyWarning("No usable data was available for ${d.domain}.")); d.dataQuality?.let { addAll(qualityWarnings(it)) } }
            is PersonalTrendResult -> { if(r.comparison.confidence==TrudyConfidence.INSUFFICIENT) add(emptyWarning("Insufficient personal trend evidence for ${r.operation.domain}/${r.operation.metricId}.")); if(r.comparison.dataQualityStatus==TrudyDataQualityStatus.STALE) add(TrudyWarning(TrudyWarningKind.STALE_DATA,"Trend input data is stale.")) }
            is BaselineComparisonResult -> if(r.comparison.confidence==TrudyConfidence.INSUFFICIENT) add(emptyWarning("Insufficient baseline comparison evidence for ${r.operation.domain}/${r.operation.metricId}."))
            is AssociationToolResult -> { if(r.association.confidence==TrudyConfidence.INSUFFICIENT) add(emptyWarning("Insufficient aligned samples for the requested association.")); if(r.association.dataQualityStatus==TrudyDataQualityStatus.STALE) add(TrudyWarning(TrudyWarningKind.STALE_DATA,"Association input data is stale.")) }
            is ExperimentHypothesisResult -> Unit
            is ExperimentEvaluationResult -> if(r.result.confidence==TrudyConfidence.INSUFFICIENT) add(emptyWarning("The personal experiment is inconclusive with the available samples/adherence."))
            is TrudyToolResult.Failure -> add(when(r.code){ TrudyToolFailureCode.UNSUPPORTED_OPERATION->TrudyWarning(TrudyWarningKind.UNSUPPORTED_TOOL,r.message); TrudyToolFailureCode.MALFORMED_REQUEST->TrudyWarning(TrudyWarningKind.MALFORMED_TOOL_REQUEST,r.message); TrudyToolFailureCode.EXECUTION_FAILED->TrudyWarning(TrudyWarningKind.TOOL_FAILURE,r.message) })
        } }
    }.distinct()

    private fun qualityWarnings(q:TrudyDataQualityEvidence)=buildList { if(q.recordCount==0L) add(emptyWarning("No stored observations are available for ${q.domain}.")); if(q.isStale) add(TrudyWarning(TrudyWarningKind.STALE_DATA,"The latest ${q.domain} data is stale.")); if(q.score<50) add(TrudyWarning(TrudyWarningKind.LOW_DATA_QUALITY,"${q.domain} data quality is ${q.score}/100.")) }
    private fun emptyWarning(message:String)=TrudyWarning(TrudyWarningKind.EMPTY_DATA,message)

    private fun evidenceReferencesFrom(r:TrudyToolResult):List<TrudyEvidenceReference>=when(r) {
        is TrudyToolResult.DomainState->r.evidence.map(::metricRef); is TrudyToolResult.MetricHistory->r.evidence.map(::metricRef); is TrudyToolResult.DomainHistory->r.evidence.map(::metricRef); is TrudyToolResult.DerivedFeatures->r.evidence.map(::derivedRef); is TrudyToolResult.Insights->r.evidence.map(::insightRef); is TrudyToolResult.DataQuality->emptyList();
        is TrudyToolResult.Context->r.context.domains.flatMap { d -> d.currentState.map(::metricRef)+d.history.map(::metricRef)+d.derivedFeatures.map(::derivedRef)+d.insights.map(::insightRef) }
        is PersonalTrendResult->r.comparison.evidence.supportingEvidenceReferences; is BaselineComparisonResult->r.comparison.evidence.supportingEvidenceReferences; is AssociationToolResult->r.association.evidence.supportingEvidenceReferences; is ExperimentHypothesisResult->r.hypothesis.evidenceBasis.flatMap { it.supportingEvidenceReferences }.distinct(); is ExperimentEvaluationResult->r.result.evidence.supportingEvidenceReferences; is TrudyToolResult.Failure->emptyList()
    }
    private fun metricRef(e:TrudyMetricEvidence)=TrudyEvidenceReference(e.domain,e.metricId,evidenceKind=e.evidenceKind,timestampEpochMs=e.timestampEpochMs)
    private fun derivedRef(e:TrudyDerivedMetricEvidence)=TrudyEvidenceReference(e.domain,e.metricId,evidenceKind=e.evidenceKind,range=e.range)
    private fun insightRef(e:TrudyInsightEvidence)=TrudyEvidenceReference(e.domain,insightId=e.id,evidenceKind=e.evidenceKind)
    private fun TrudyEvidenceReference.matches(r:TrudyEvidenceReference):Boolean { if(domain!=r.domain||evidenceKind!=r.evidenceKind)return false; if(r.metricId!=null&&metricId!=r.metricId)return false; if(r.insightId!=null&&insightId!=r.insightId)return false; return r.metricId!=null||r.insightId!=null }
    private fun TrudyToolResult.callRecord()=if(this is TrudyToolResult.Failure) TrudyToolCallRecord(operation,false,code) else TrudyToolCallRecord(operation,true)
    private fun fallback(answer:String,toolCalls:List<TrudyToolCallRecord> = emptyList(),warnings:List<TrudyWarning>,metadata:TrudyModelMetadata?=null)=TrudyOrchestrationResult(TrudyOrchestrationStatus.FALLBACK,answer,emptyList(),toolCalls.toList(),warnings.distinct(),metadata)
}
