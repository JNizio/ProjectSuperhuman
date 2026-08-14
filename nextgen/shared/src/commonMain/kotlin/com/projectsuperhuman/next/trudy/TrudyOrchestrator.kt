package com.projectsuperhuman.next.trudy

enum class TrudyOrchestrationStatus { SUCCESS, FALLBACK }
enum class TrudyWarningKind { EMPTY_DATA, STALE_DATA, LOW_DATA_QUALITY, TOOL_FAILURE, UNSUPPORTED_TOOL, MALFORMED_TOOL_REQUEST, MODEL_FAILURE, ITERATION_LIMIT, UNBOUND_EVIDENCE_REFERENCE }
data class TrudyWarning(val kind: TrudyWarningKind, val message: String)
data class TrudyToolCallRecord(val operation: TrudyToolOperation, val succeeded: Boolean, val failureCode: TrudyToolFailureCode? = null)
data class TrudyAskRequest(val userMessage: String, val conversationContext: List<TrudyConversationTurn> = emptyList(), val preselectedContext: TrudyContextRequest? = null)
data class TrudyOrchestrationResult(val status: TrudyOrchestrationStatus, val answerText: String, val evidenceReferences: List<TrudyEvidenceReference>, val toolCallsMade: List<TrudyToolCallRecord>, val warnings: List<TrudyWarning>, val modelMetadata: TrudyModelMetadata? = null)

/** Bounded provider-neutral model/tool loop. All important health math remains inside typed tools. */
class TrudyOrchestrator(
    private val modelClient: TrudyModelClient,
    private val tools: TrudyToolExecutor,
    private val maxModelIterations: Int = 4,
    private val preflightPlanner: TrudyPreflightPlanner = NoOpTrudyPreflightPlanner,
    private val knowledgeCoordinator: TrudyKnowledgeCoordinator = TrudyKnowledgeCoordinator()
) {
    init { require(maxModelIterations > 0) }

    suspend fun ask(request: TrudyAskRequest): TrudyOrchestrationResult {
        if (request.userMessage.isBlank()) {
            return fallback(
                "Please ask a question so I can help.",
                warnings = listOf(TrudyWarning(TrudyWarningKind.MODEL_FAILURE, "User message was empty."))
            )
        }

        val results = mutableListOf<TrudyToolResult>()
        val calls = mutableListOf<TrudyToolCallRecord>()
        var context: TrudyHealthContext? = null
        var metadata: TrudyModelMetadata? = null

        request.preselectedContext?.let { selected ->
            val result = executeSafely(TrudyToolOperation.GetContext(selected))
            results += result
            calls += result.callRecord()
            if (result is TrudyToolResult.Context) context = result.context
        }

        val basePreflight = runCatching { preflightPlanner.plan(request) }.getOrDefault(emptyList())
            .distinct()
            .take(MAX_PREFLIGHT_OPERATIONS)
        val knowledge = runCatching {
            knowledgeCoordinator.retrieve(
                TrudyKnowledgeQuery(
                    userText = request.userMessage,
                    domains = (basePreflight.flatMap { it.domains } + request.preselectedContext?.domains.orEmpty()).distinct(),
                    metricIds = basePreflight.flatMap(::metricIdsFrom).distinct()
                )
            )
        }.getOrDefault(emptyList())
        val preflight = enrichPreflightWithKnowledge(request, basePreflight, knowledge)
            .distinct()
            .take(MAX_PREFLIGHT_OPERATIONS)

        preflight.forEach { operation ->
            val result = executeSafely(operation)
            results += result
            calls += result.callRecord()
            if (result is TrudyToolResult.Context) context = result.context
        }

        val modelKnowledge = knowledge.map(::withSafetyInSummary)
        repeat(maxModelIterations) { iteration ->
            val model = try {
                modelClient.complete(
                    TrudyModelRequest(
                        request.userMessage,
                        TrudyModelPolicy.SYSTEM_INSTRUCTION,
                        request.conversationContext,
                        context,
                        tools.definitions,
                        results.toList(),
                        iteration,
                        modelKnowledge
                    )
                )
            } catch (_: Throwable) {
                return fallback(
                    "I couldn't complete the reasoning step reliably. Please try again.",
                    calls,
                    deriveWarnings(results) + TrudyWarning(TrudyWarningKind.MODEL_FAILURE, "The model provider failed."),
                    metadata
                )
            }
            metadata = model.metadata ?: metadata
            if (model.requestedTools.isEmpty()) {
                val answer = model.responseText?.trim()
                if (answer.isNullOrEmpty()) {
                    return fallback(
                        "I don't have enough reliable information to answer that yet.",
                        calls,
                        deriveWarnings(results) + TrudyWarning(TrudyWarningKind.MODEL_FAILURE, "Model returned neither tools nor an answer."),
                        metadata
                    )
                }
                val failures = results.filterIsInstance<TrudyToolResult.Failure>()
                if (failures.isNotEmpty() && results.count { it !is TrudyToolResult.Failure } == 0) {
                    return fallback("I couldn't access the requested health data reliably, so I won't guess.", calls, deriveWarnings(results), metadata)
                }
                val available = results.flatMap(::evidenceReferencesFrom)
                val bound = model.evidenceReferences.mapNotNull { requested ->
                    available.firstOrNull { it.matchesExactly(requested) }
                }.distinct()
                val dropped = model.evidenceReferences.size - bound.size
                val warnings = buildList {
                    addAll(deriveWarnings(results))
                    if (dropped > 0) {
                        add(TrudyWarning(TrudyWarningKind.UNBOUND_EVIDENCE_REFERENCE, "$dropped model evidence reference(s) were rejected because no matching tool evidence existed."))
                    }
                }
                return TrudyOrchestrationResult(
                    TrudyOrchestrationStatus.SUCCESS,
                    answer,
                    bound,
                    calls.toList(),
                    warnings.distinct(),
                    metadata
                )
            }
            if (iteration == maxModelIterations - 1) {
                return fallback(
                    "I couldn't finish this health-data request within the safe tool limit.",
                    calls,
                    deriveWarnings(results) + TrudyWarning(TrudyWarningKind.ITERATION_LIMIT, "Maximum model/tool iterations exceeded."),
                    metadata
                )
            }
            model.requestedTools.forEach { operation ->
                val result = executeSafely(operation)
                results += result
                calls += result.callRecord()
            }
        }
        return fallback(
            "I couldn't finish this request reliably.",
            calls,
            listOf(TrudyWarning(TrudyWarningKind.ITERATION_LIMIT, "Maximum model/tool iterations exceeded.")),
            metadata
        )
    }

    /**
     * Agent 2 remains the investigation authority. Knowledge hints only enrich its bounded plan;
     * they never create a second statistics engine or query every domain.
     */
    private fun enrichPreflightWithKnowledge(
        request: TrudyAskRequest,
        base: List<TrudyToolOperation>,
        knowledge: List<TrudyKnowledgeItem>
    ): List<TrudyToolOperation> {
        val hints = knowledge.asSequence()
            .filter { it.kind != TrudyKnowledgeKind.MEDICAL && it.kind != TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY }
            .flatMap { it.metricHints.asSequence() }
            .filter { it.role != TrudyKnowledgeMetricRole.DATA_QUALITY }
            .distinctBy { it.domain to it.metricId }
            .toList()
        if (hints.isEmpty()) return base

        val investigationIndex = base.indexOfFirst { it is InvestigateChange }
        if (investigationIndex >= 0) {
            val investigation = base[investigationIndex] as InvestigateChange
            val existingTargetKeys = investigation.targets.map { it.domain to it.metricId }.toMutableSet()
            val existingRelatedKeys = investigation.related.map { it.domain to it.metricId }.toMutableSet()
            val extraTargets = hints.filter {
                it.role == TrudyKnowledgeMetricRole.PRIMARY_OUTCOME || it.role == TrudyKnowledgeMetricRole.SECONDARY_OUTCOME
            }.mapNotNull { hint ->
                val key = hint.domain to hint.metricId
                if (!existingTargetKeys.add(key)) null else hint.toInvestigationMetric(TrudyInvestigationRole.SUPPORTING)
            }
            val extraRelated = hints.filter {
                it.role == TrudyKnowledgeMetricRole.EXPOSURE ||
                    it.role == TrudyKnowledgeMetricRole.CONFOUNDER ||
                    it.role == TrudyKnowledgeMetricRole.CONTEXT
            }.mapNotNull { hint ->
                val key = hint.domain to hint.metricId
                if (key in existingTargetKeys || !existingRelatedKeys.add(key)) null else hint.toInvestigationMetric()
            }
            val enriched = investigation.copy(
                targets = (investigation.targets + extraTargets).take(
                    minOf(MAX_INVESTIGATION_TARGETS, investigation.budget.maxTargets)
                ),
                related = (investigation.related + extraRelated).take(
                    minOf(MAX_INVESTIGATION_RELATED, investigation.budget.maxRelatedSignals)
                )
            )
            return base.toMutableList().also { it[investigationIndex] = enriched }
        }

        if (!looksPersonal(request.userMessage) || asksExperimentMethodology(request.userMessage)) return base
        val covered = base.flatMap { operation ->
            operation.domains.flatMap { domain -> metricIdsFrom(operation).map { metricId -> domain to metricId } }
        }.toSet()
        val supplemental = hints.asSequence()
            .filter { (it.domain to it.metricId) !in covered }
            .sortedBy { rolePriority(it.role) }
            .take(MAX_KNOWLEDGE_METRIC_OPERATIONS)
            .map { TrudyToolOperation.GetMetricHistory(it.domain, it.metricId, KNOWLEDGE_HISTORY_LIMIT) }
            .toList()
        return (base + supplemental).take(MAX_PREFLIGHT_OPERATIONS)
    }

    private fun TrudyKnowledgeMetricHint.toInvestigationMetric(
        role: TrudyInvestigationRole = TrudyInvestigationRole.SUPPORTING
    ): TrudyInvestigationMetric {
        val preference = TrudySystemCatalog.metric(domain, metricId)?.preference ?: TrudyMetricPreference.CONTEXT_DEPENDENT
        return TrudyInvestigationMetric(domain, metricId, role, preference)
    }

    private fun rolePriority(role: TrudyKnowledgeMetricRole): Int = when (role) {
        TrudyKnowledgeMetricRole.PRIMARY_OUTCOME -> 0
        TrudyKnowledgeMetricRole.SECONDARY_OUTCOME -> 1
        TrudyKnowledgeMetricRole.EXPOSURE -> 2
        TrudyKnowledgeMetricRole.CONFOUNDER -> 3
        TrudyKnowledgeMetricRole.CONTEXT -> 4
        TrudyKnowledgeMetricRole.DATA_QUALITY -> 5
    }

    private fun looksPersonal(message: String): Boolean {
        val text = message.lowercase().replace(Regex("[^a-z0-9']+"), " ").trim()
        val tokens = text.split(' ').toSet()
        return "my" in tokens || "me" in tokens || "i" in tokens || "i'm" in tokens || "ive" in tokens || "i've" in tokens
    }

    private fun asksExperimentMethodology(message: String): Boolean {
        val text = message.lowercase()
        return "how could i test" in text || "how can i test" in text || "design an experiment" in text || "experiment methodology" in text
    }

    private fun withSafetyInSummary(item: TrudyKnowledgeItem): TrudyKnowledgeItem {
        if (item.safetyNotes.isEmpty()) return item
        val safety = item.safetyNotes.take(3).joinToString(" ")
        return item.copy(summary = (item.summary + " Safety boundaries: " + safety).take(MAX_MODEL_KNOWLEDGE_SUMMARY_CHARS))
    }

    private suspend fun executeSafely(operation: TrudyToolOperation): TrudyToolResult {
        if (operation is TrudyToolOperation.Unsupported) {
            return TrudyToolResult.Failure(operation, TrudyToolFailureCode.UNSUPPORTED_OPERATION, "Unsupported Trudy tool: ${operation.name}")
        }
        validate(operation)?.let {
            return TrudyToolResult.Failure(operation, TrudyToolFailureCode.MALFORMED_REQUEST, it)
        }
        return try {
            tools.execute(operation)
        } catch (_: Throwable) {
            TrudyToolResult.Failure(operation, TrudyToolFailureCode.EXECUTION_FAILED, "Typed Trudy tool execution failed.")
        }
    }

    private fun validate(op: TrudyToolOperation): String? = when (op) {
        is TrudyToolOperation.GetMetricHistory -> when {
            op.metricId.isBlank() -> "metricId must not be blank"
            op.limit !in 1..5000 -> "limit must be between 1 and 5000"
            op.offset < 0 -> "offset must be >= 0"
            else -> null
        }
        is TrudyToolOperation.GetMetricWindow -> when {
            op.metricId.isBlank() -> "metricId must not be blank"
            op.limit !in 1..5000 -> "limit must be between 1 and 5000"
            else -> null
        }
        is TrudyToolOperation.GetDomainHistory -> when {
            op.limit !in 1..5000 -> "limit must be between 1 and 5000"
            op.offset < 0 -> "offset must be >= 0"
            else -> null
        }
        is TrudyToolOperation.GetContext -> if (op.domains.isEmpty()) "Cross-domain context must explicitly list domains" else if (op.request.historyLimitPerDomain !in 1..5000) "historyLimitPerDomain must be between 1 and 5000" else null
        is GetPersonalTrend -> when {
            op.metricId.isBlank() -> "metricId must not be blank"
            op.recentDays !in 1..3650 || op.baselineDays !in 1..3650 -> "trend windows must be between 1 and 3650 days"
            else -> null
        }
        is CompareBaseline -> if (op.metricId.isBlank()) "metricId must not be blank" else null
        is GetAssociation -> when {
            op.leftMetricId.isBlank() || op.rightMetricId.isBlank() -> "Association metrics must be domain-qualified and non-blank"
            op.domains != listOf(op.leftDomain, op.rightDomain).distinct() -> "Association domains were not preserved"
            op.alignmentWindowMs !in 0..TrudyStatistics.MAX_ALIGNMENT_WINDOW_MS -> "alignmentWindowMs must be between 0 and seven days"
            else -> null
        }
        is GetLaggedAssociation -> when {
            op.leftMetricId.isBlank() || op.rightMetricId.isBlank() -> "Association metrics must be domain-qualified and non-blank"
            op.lagMs !in 0..TrudyStatistics.MAX_LAG_MS -> "lagMs must be between 0 and seven days"
            op.alignmentWindowMs !in 0..TrudyStatistics.MAX_ALIGNMENT_WINDOW_MS -> "alignmentWindowMs must be between 0 and seven days"
            else -> null
        }
        is InvestigateChange -> when {
            op.targets.isEmpty() -> "At least one target metric is required"
            op.targets.any { it.metricId.isBlank() } || op.related.any { it.metricId.isBlank() } -> "Investigation metrics must be domain-qualified and non-blank"
            op.maxAssociations !in 0..8 -> "maxAssociations must be between 0 and 8"
            else -> null
        }
        is GenerateExperimentHypothesis -> if (op.targetMetricId.isBlank()) "targetMetricId must not be blank" else null
        is EvaluateExperiment -> if (!op.adherenceFraction.isFinite() || op.adherenceFraction !in 0.0..1.0) "adherenceFraction must be a finite value between 0 and 1" else null
        is GetCanonicalExperiments -> if (op.limit !in 1..50) "experiment limit must be between 1 and 50" else null
        is EvaluateCanonicalExperiment -> if (op.experimentId?.isBlank() == true) "experimentId must not be blank" else null
        is GetSystemAvailability -> if (op.moduleIds.isEmpty()) "At least one module ID is required" else null
        is TrudyToolOperation.Unsupported -> "Unsupported tool operation"
        else -> if (op.domains.size != 1) "Single-domain tool must contain exactly one domain" else null
    }

    private fun deriveWarnings(results: List<TrudyToolResult>): List<TrudyWarning> = buildList {
        results.forEach { result ->
            when (result) {
                is TrudyToolResult.DomainState -> if (result.evidence.isEmpty()) add(emptyWarning("No current data was available for ${result.operation.domain}."))
                is TrudyToolResult.MetricHistory -> if (result.evidence.isEmpty()) add(emptyWarning("No history was available for ${result.operation.domain}/${result.operation.metricId}."))
                is TrudyToolResult.MetricWindow -> if (result.evidence.isEmpty()) add(emptyWarning("No measurement was available for ${result.operation.domain}/${result.operation.metricId} in the requested time window."))
                is TrudyToolResult.DomainHistory -> if (result.evidence.isEmpty()) add(emptyWarning("No history was available for ${result.operation.domain}."))
                is TrudyToolResult.DerivedFeatures -> if (result.evidence.isEmpty()) add(emptyWarning("No derived features were available for ${result.operation.domain}."))
                is TrudyToolResult.Insights -> if (result.evidence.isEmpty()) add(emptyWarning("No insights were available for ${result.operation.domain}."))
                is TrudyToolResult.DataQuality -> addAll(qualityWarnings(result.evidence))
                is TrudyToolResult.Context -> result.context.domains.forEach { domain ->
                    if (domain.currentState.isEmpty() && domain.history.isEmpty() && domain.derivedFeatures.isEmpty() && domain.insights.isEmpty()) add(emptyWarning("No usable data was available for ${domain.domain}."))
                    domain.dataQuality?.let { addAll(qualityWarnings(it)) }
                }
                is PersonalTrendResult -> {
                    if (result.comparison.confidence == TrudyConfidence.INSUFFICIENT) add(emptyWarning("Insufficient personal trend evidence for ${result.operation.domain}/${result.operation.metricId}."))
                    if (result.comparison.dataQualityStatus == TrudyDataQualityStatus.STALE) add(TrudyWarning(TrudyWarningKind.STALE_DATA, "Trend input data is stale."))
                }
                is BaselineComparisonResult -> if (result.comparison.confidence == TrudyConfidence.INSUFFICIENT) add(emptyWarning("Insufficient baseline comparison evidence for ${result.operation.domain}/${result.operation.metricId}."))
                is AssociationToolResult -> {
                    if (result.association.confidence == TrudyConfidence.INSUFFICIENT) add(emptyWarning("Insufficient aligned samples for the requested association."))
                    if (result.association.dataQualityStatus == TrudyDataQualityStatus.STALE) add(TrudyWarning(TrudyWarningKind.STALE_DATA, "Association input data is stale."))
                }
                is ExperimentHypothesisResult -> Unit
                is ExperimentEvaluationResult -> if (result.result.confidence == TrudyConfidence.INSUFFICIENT) add(emptyWarning("The personal experiment is inconclusive with the available samples/adherence."))
                is ChangeInvestigationResult -> {
                    if (result.investigation.structuredResult.premiseStatus == TrudyPremiseStatus.PREMISE_UNVERIFIABLE) {
                        add(emptyWarning("The requested change could not be verified in both time windows."))
                    }
                    if (result.investigation.structuredResult.quality.status == TrudyDataQualityStatus.STALE) {
                        add(TrudyWarning(TrudyWarningKind.STALE_DATA, "Investigation inputs are stale."))
                    }
                    if (result.investigation.structuredResult.quality.status == TrudyDataQualityStatus.LIMITED) {
                        add(TrudyWarning(TrudyWarningKind.LOW_DATA_QUALITY, "Investigation input quality is limited."))
                    }
                }
                is CanonicalExperimentsResult -> if (result.experiments.isEmpty()) add(emptyWarning(if (result.persistenceState == TrudyExperimentPersistenceState.NOT_CONNECTED) "Canonical Experiments persistence is not connected." else "No saved experiment matched the request."))
                is CanonicalExperimentEvaluationResult -> if (result.evaluation == null) add(emptyWarning(if (result.persistenceState == TrudyExperimentPersistenceState.NOT_CONNECTED) "Canonical Experiments persistence is not connected." else "No saved experiment was available to evaluate."))
                is SystemAvailabilityResult -> Unit
                is TrudyToolResult.Failure -> add(
                    when (result.code) {
                        TrudyToolFailureCode.UNSUPPORTED_OPERATION -> TrudyWarning(TrudyWarningKind.UNSUPPORTED_TOOL, result.message)
                        TrudyToolFailureCode.MALFORMED_REQUEST -> TrudyWarning(TrudyWarningKind.MALFORMED_TOOL_REQUEST, result.message)
                        TrudyToolFailureCode.EXECUTION_FAILED -> TrudyWarning(TrudyWarningKind.TOOL_FAILURE, result.message)
                    }
                )
            }
        }
    }.distinct()

    private fun qualityWarnings(quality: TrudyDataQualityEvidence) = buildList {
        if (quality.recordCount == 0L) add(emptyWarning("No stored observations are available for ${quality.domain}."))
        if (quality.isStale) add(TrudyWarning(TrudyWarningKind.STALE_DATA, "The latest ${quality.domain} data is stale."))
        if (quality.score < 50) add(TrudyWarning(TrudyWarningKind.LOW_DATA_QUALITY, "${quality.domain} data quality is ${quality.score}/100."))
    }

    private fun emptyWarning(message: String) = TrudyWarning(TrudyWarningKind.EMPTY_DATA, message)

    private fun evidenceReferencesFrom(result: TrudyToolResult): List<TrudyEvidenceReference> = when (result) {
        is TrudyToolResult.DomainState -> result.evidence.map(::metricRef)
        is TrudyToolResult.MetricHistory -> result.evidence.map(::metricRef)
        is TrudyToolResult.MetricWindow -> result.evidence.map(::metricRef)
        is TrudyToolResult.DomainHistory -> result.evidence.map(::metricRef)
        is TrudyToolResult.DerivedFeatures -> result.evidence.map(::derivedRef)
        is TrudyToolResult.Insights -> result.evidence.map(::insightRef)
        is TrudyToolResult.DataQuality -> emptyList()
        is TrudyToolResult.Context -> result.context.domains.flatMap { domain ->
            domain.currentState.map(::metricRef) + domain.history.map(::metricRef) +
                domain.derivedFeatures.map(::derivedRef) + domain.insights.map(::insightRef)
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

    private fun metricRef(evidence: TrudyMetricEvidence) = TrudyEvidenceReference(
        evidence.domain,
        evidence.metricId,
        evidenceKind = evidence.evidenceKind,
        timestampEpochMs = evidence.timestampEpochMs
    )

    private fun derivedRef(evidence: TrudyDerivedMetricEvidence) = TrudyEvidenceReference(
        evidence.domain,
        evidence.metricId,
        evidenceKind = evidence.evidenceKind,
        range = evidence.range
    )

    private fun insightRef(evidence: TrudyInsightEvidence) = TrudyEvidenceReference(
        evidence.domain,
        insightId = evidence.id,
        evidenceKind = evidence.evidenceKind
    )

    /** Bind only to the exact structured evidence returned by a tool, including temporal identity. */
    private fun TrudyEvidenceReference.matchesExactly(requested: TrudyEvidenceReference): Boolean {
        if (domain != requested.domain || evidenceKind != requested.evidenceKind) return false
        if (metricId != requested.metricId || insightId != requested.insightId) return false
        if (timestampEpochMs != requested.timestampEpochMs || range != requested.range) return false
        return metricId != null || insightId != null
    }

    private fun TrudyToolResult.callRecord() = if (this is TrudyToolResult.Failure) {
        TrudyToolCallRecord(operation, false, code)
    } else {
        TrudyToolCallRecord(operation, true)
    }

    private fun metricIdsFrom(operation: TrudyToolOperation): List<String> = when (operation) {
        is TrudyToolOperation.GetMetricHistory -> listOf(operation.metricId)
        is TrudyToolOperation.GetMetricWindow -> listOf(operation.metricId)
        is GetPersonalTrend -> listOf(operation.metricId)
        is CompareBaseline -> listOf(operation.metricId)
        is GetAssociation -> listOf(operation.leftMetricId, operation.rightMetricId)
        is GetLaggedAssociation -> listOf(operation.leftMetricId, operation.rightMetricId)
        is InvestigateChange -> (operation.targets + operation.related).map { it.metricId }
        is GenerateExperimentHypothesis -> listOf(operation.targetMetricId)
        is EvaluateExperiment -> listOf(operation.hypothesis.targetMetricId)
        else -> emptyList()
    }

    private fun fallback(
        answer: String,
        toolCalls: List<TrudyToolCallRecord> = emptyList(),
        warnings: List<TrudyWarning>,
        metadata: TrudyModelMetadata? = null
    ) = TrudyOrchestrationResult(
        TrudyOrchestrationStatus.FALLBACK,
        answer,
        emptyList(),
        toolCalls.toList(),
        warnings.distinct(),
        metadata
    )

    private companion object {
        const val MAX_PREFLIGHT_OPERATIONS = 12
        const val MAX_INVESTIGATION_TARGETS = 8
        const val MAX_INVESTIGATION_RELATED = 8
        const val MAX_KNOWLEDGE_METRIC_OPERATIONS = 4
        const val KNOWLEDGE_HISTORY_LIMIT = 60
        const val MAX_MODEL_KNOWLEDGE_SUMMARY_CHARS = 1_800
    }
}
