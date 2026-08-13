package com.projectsuperhuman.next.trudy

enum class TrudyOrchestrationStatus { SUCCESS, FALLBACK }

enum class TrudyWarningKind {
    EMPTY_DATA,
    STALE_DATA,
    LOW_DATA_QUALITY,
    TOOL_FAILURE,
    UNSUPPORTED_TOOL,
    MALFORMED_TOOL_REQUEST,
    MODEL_FAILURE,
    ITERATION_LIMIT,
    UNBOUND_EVIDENCE_REFERENCE
}

data class TrudyWarning(
    val kind: TrudyWarningKind,
    val message: String
)

data class TrudyToolCallRecord(
    val operation: TrudyToolOperation,
    val succeeded: Boolean,
    val failureCode: TrudyToolFailureCode? = null
)

data class TrudyAskRequest(
    val userMessage: String,
    val conversationContext: List<TrudyConversationTurn> = emptyList(),
    /**
     * Optional deterministic shortcut for callers that already know the bounded context needed.
     * When absent, the model decides whether tools are necessary.
     */
    val preselectedContext: TrudyContextRequest? = null
)

data class TrudyOrchestrationResult(
    val status: TrudyOrchestrationStatus,
    val answerText: String,
    val evidenceReferences: List<TrudyEvidenceReference>,
    val toolCallsMade: List<TrudyToolCallRecord>,
    val warnings: List<TrudyWarning>,
    val modelMetadata: TrudyModelMetadata? = null
)

/**
 * Bounded provider-neutral model/tool loop.
 *
 * The orchestrator only accepts typed [TrudyToolOperation] values and a [TrudyToolExecutor].
 * It has no SQL, repository, Android, networking, or provider-specific dependency.
 */
class TrudyOrchestrator(
    private val modelClient: TrudyModelClient,
    private val tools: TrudyToolExecutor,
    private val maxModelIterations: Int = DEFAULT_MAX_MODEL_ITERATIONS
) {
    init {
        require(maxModelIterations > 0) { "maxModelIterations must be > 0" }
    }

    suspend fun ask(request: TrudyAskRequest): TrudyOrchestrationResult {
        if (request.userMessage.isBlank()) {
            return fallback(
                answer = "Please ask a question so I can help.",
                warnings = listOf(TrudyWarning(TrudyWarningKind.MODEL_FAILURE, "User message was empty."))
            )
        }

        val toolResults = mutableListOf<TrudyToolResult>()
        val toolCalls = mutableListOf<TrudyToolCallRecord>()
        var preselectedContext: TrudyHealthContext? = null
        var lastMetadata: TrudyModelMetadata? = null

        request.preselectedContext?.let { contextRequest ->
            val result = executeSafely(TrudyToolOperation.GetContext(contextRequest))
            toolResults += result
            toolCalls += result.toCallRecord()
            if (result is TrudyToolResult.Context) preselectedContext = result.context
        }

        repeat(maxModelIterations) { iteration ->
            val modelResult = try {
                modelClient.complete(
                    TrudyModelRequest(
                        userRequest = request.userMessage,
                        systemInstruction = TrudyModelPolicy.SYSTEM_INSTRUCTION,
                        conversationContext = request.conversationContext,
                        context = preselectedContext,
                        toolDefinitions = tools.definitions,
                        toolResults = toolResults.toList(),
                        iteration = iteration
                    )
                )
            } catch (_: Throwable) {
                return fallback(
                    answer = "I couldn't complete the reasoning step reliably. Please try again.",
                    toolCalls = toolCalls,
                    warnings = deriveWarnings(toolResults) +
                        TrudyWarning(TrudyWarningKind.MODEL_FAILURE, "The model provider failed."),
                    metadata = lastMetadata
                )
            }

            lastMetadata = modelResult.metadata ?: lastMetadata

            if (modelResult.requestedTools.isEmpty()) {
                val answer = modelResult.responseText?.trim()
                if (answer.isNullOrEmpty()) {
                    return fallback(
                        answer = "I don't have enough reliable information to answer that yet.",
                        toolCalls = toolCalls,
                        warnings = deriveWarnings(toolResults) +
                            TrudyWarning(TrudyWarningKind.MODEL_FAILURE, "Model returned neither tools nor an answer."),
                        metadata = lastMetadata
                    )
                }

                val availableEvidence = toolResults.flatMap(::evidenceReferencesFrom)
                val failures = toolResults.filterIsInstance<TrudyToolResult.Failure>()
                val successfulToolResults = toolResults.count { it !is TrudyToolResult.Failure }
                if (failures.isNotEmpty() && successfulToolResults == 0) {
                    return fallback(
                        answer = "I couldn't access the requested health data reliably, so I won't guess.",
                        toolCalls = toolCalls,
                        warnings = deriveWarnings(toolResults),
                        metadata = lastMetadata
                    )
                }

                val bound = modelResult.evidenceReferences.mapNotNull { requested ->
                    availableEvidence.firstOrNull { actual -> actual.matches(requested) }
                }.distinct()
                val droppedCount = modelResult.evidenceReferences.size - bound.size
                val warnings = buildList {
                    addAll(deriveWarnings(toolResults))
                    if (droppedCount > 0) {
                        add(
                            TrudyWarning(
                                TrudyWarningKind.UNBOUND_EVIDENCE_REFERENCE,
                                "$droppedCount model evidence reference(s) were rejected because no matching tool evidence existed."
                            )
                        )
                    }
                }

                return TrudyOrchestrationResult(
                    status = TrudyOrchestrationStatus.SUCCESS,
                    answerText = answer,
                    evidenceReferences = bound,
                    toolCallsMade = toolCalls.toList(),
                    warnings = warnings.distinct(),
                    modelMetadata = lastMetadata
                )
            }

            if (iteration == maxModelIterations - 1) {
                return fallback(
                    answer = "I couldn't finish this health-data request within the safe tool limit.",
                    toolCalls = toolCalls,
                    warnings = deriveWarnings(toolResults) +
                        TrudyWarning(TrudyWarningKind.ITERATION_LIMIT, "Maximum model/tool iterations exceeded."),
                    metadata = lastMetadata
                )
            }

            modelResult.requestedTools.forEach { operation ->
                val result = executeSafely(operation)
                toolResults += result
                toolCalls += result.toCallRecord()
            }
        }

        return fallback(
            answer = "I couldn't finish this request reliably.",
            toolCalls = toolCalls,
            warnings = listOf(TrudyWarning(TrudyWarningKind.ITERATION_LIMIT, "Maximum model/tool iterations exceeded.")),
            metadata = lastMetadata
        )
    }

    private suspend fun executeSafely(operation: TrudyToolOperation): TrudyToolResult {
        if (operation is TrudyToolOperation.Unsupported) {
            return TrudyToolResult.Failure(
                operation,
                TrudyToolFailureCode.UNSUPPORTED_OPERATION,
                "Unsupported Trudy tool: ${operation.name}"
            )
        }
        val validation = validate(operation)
        if (validation != null) {
            return TrudyToolResult.Failure(
                operation,
                TrudyToolFailureCode.MALFORMED_REQUEST,
                validation
            )
        }
        return try {
            tools.execute(operation)
        } catch (_: Throwable) {
            TrudyToolResult.Failure(
                operation,
                TrudyToolFailureCode.EXECUTION_FAILED,
                "Health tool execution failed."
            )
        }
    }

    private fun validate(operation: TrudyToolOperation): String? = when (operation) {
        is TrudyToolOperation.GetMetricHistory -> when {
            operation.metricId.isBlank() -> "metricId must not be blank"
            operation.limit !in 1..MAX_TOOL_LIMIT -> "limit must be between 1 and $MAX_TOOL_LIMIT"
            operation.offset < 0 -> "offset must be >= 0"
            else -> null
        }
        is TrudyToolOperation.GetDomainHistory -> when {
            operation.limit !in 1..MAX_TOOL_LIMIT -> "limit must be between 1 and $MAX_TOOL_LIMIT"
            operation.offset < 0 -> "offset must be >= 0"
            else -> null
        }
        is TrudyToolOperation.GetContext -> when {
            operation.domains.isEmpty() -> "Cross-domain context must explicitly list domains"
            operation.request.historyLimitPerDomain !in 1..MAX_TOOL_LIMIT ->
                "historyLimitPerDomain must be between 1 and $MAX_TOOL_LIMIT"
            else -> null
        }
        is TrudyToolOperation.Unsupported -> "Unsupported tool operation"
        else -> if (operation.domains.size != 1) "Single-domain tool must contain exactly one domain" else null
    }

    private fun deriveWarnings(results: List<TrudyToolResult>): List<TrudyWarning> {
        val warnings = mutableListOf<TrudyWarning>()
        results.forEach { result ->
            when (result) {
                is TrudyToolResult.DomainState -> if (result.evidence.isEmpty()) {
                    warnings += TrudyWarning(TrudyWarningKind.EMPTY_DATA, "No current data was available for ${result.operation.domain}.")
                }
                is TrudyToolResult.MetricHistory -> if (result.evidence.isEmpty()) {
                    warnings += TrudyWarning(
                        TrudyWarningKind.EMPTY_DATA,
                        "No history was available for ${result.operation.domain}/${result.operation.metricId}."
                    )
                }
                is TrudyToolResult.DomainHistory -> if (result.evidence.isEmpty()) {
                    warnings += TrudyWarning(TrudyWarningKind.EMPTY_DATA, "No history was available for ${result.operation.domain}.")
                }
                is TrudyToolResult.DerivedFeatures -> if (result.evidence.isEmpty()) {
                    warnings += TrudyWarning(TrudyWarningKind.EMPTY_DATA, "No derived features were available for ${result.operation.domain}.")
                }
                is TrudyToolResult.Insights -> if (result.evidence.isEmpty()) {
                    warnings += TrudyWarning(TrudyWarningKind.EMPTY_DATA, "No insights were available for ${result.operation.domain}.")
                }
                is TrudyToolResult.DataQuality -> warnings += qualityWarnings(result.evidence)
                is TrudyToolResult.Context -> result.context.domains.forEach { domain ->
                    if (
                        domain.currentState.isEmpty() &&
                        domain.history.isEmpty() &&
                        domain.derivedFeatures.isEmpty() &&
                        domain.insights.isEmpty()
                    ) {
                        warnings += TrudyWarning(TrudyWarningKind.EMPTY_DATA, "No usable data was available for ${domain.domain}.")
                    }
                    domain.dataQuality?.let { warnings += qualityWarnings(it) }
                }
                is TrudyToolResult.Failure -> warnings += when (result.code) {
                    TrudyToolFailureCode.UNSUPPORTED_OPERATION ->
                        TrudyWarning(TrudyWarningKind.UNSUPPORTED_TOOL, result.message)
                    TrudyToolFailureCode.MALFORMED_REQUEST ->
                        TrudyWarning(TrudyWarningKind.MALFORMED_TOOL_REQUEST, result.message)
                    TrudyToolFailureCode.EXECUTION_FAILED ->
                        TrudyWarning(TrudyWarningKind.TOOL_FAILURE, result.message)
                }
            }
        }
        return warnings.distinct()
    }

    private fun qualityWarnings(quality: TrudyDataQualityEvidence): List<TrudyWarning> = buildList {
        if (quality.recordCount == 0L) {
            add(TrudyWarning(TrudyWarningKind.EMPTY_DATA, "No stored observations are available for ${quality.domain}."))
        }
        if (quality.isStale) {
            add(TrudyWarning(TrudyWarningKind.STALE_DATA, "The latest ${quality.domain} data is stale."))
        }
        if (quality.score < LOW_QUALITY_SCORE) {
            add(
                TrudyWarning(
                    TrudyWarningKind.LOW_DATA_QUALITY,
                    "${quality.domain} data quality is ${quality.score}/100."
                )
            )
        }
    }

    private fun evidenceReferencesFrom(result: TrudyToolResult): List<TrudyEvidenceReference> = when (result) {
        is TrudyToolResult.DomainState -> result.evidence.map(::metricReference)
        is TrudyToolResult.MetricHistory -> result.evidence.map(::metricReference)
        is TrudyToolResult.DomainHistory -> result.evidence.map(::metricReference)
        is TrudyToolResult.DerivedFeatures -> result.evidence.map(::derivedReference)
        is TrudyToolResult.Insights -> result.evidence.map(::insightReference)
        is TrudyToolResult.DataQuality -> emptyList()
        is TrudyToolResult.Context -> result.context.domains.flatMap { domain ->
            domain.currentState.map(::metricReference) +
                domain.history.map(::metricReference) +
                domain.derivedFeatures.map(::derivedReference) +
                domain.insights.map(::insightReference)
        }
        is TrudyToolResult.Failure -> emptyList()
    }

    private fun metricReference(evidence: TrudyMetricEvidence) = TrudyEvidenceReference(
        domain = evidence.domain,
        metricId = evidence.metricId,
        evidenceKind = evidence.evidenceKind,
        timestampEpochMs = evidence.timestampEpochMs
    )

    private fun derivedReference(evidence: TrudyDerivedMetricEvidence) = TrudyEvidenceReference(
        domain = evidence.domain,
        metricId = evidence.metricId,
        evidenceKind = evidence.evidenceKind,
        range = evidence.range
    )

    private fun insightReference(evidence: TrudyInsightEvidence) = TrudyEvidenceReference(
        domain = evidence.domain,
        insightId = evidence.id,
        evidenceKind = evidence.evidenceKind
    )

    private fun TrudyEvidenceReference.matches(requested: TrudyEvidenceReference): Boolean {
        if (domain != requested.domain || evidenceKind != requested.evidenceKind) return false
        if (requested.metricId != null && metricId != requested.metricId) return false
        if (requested.insightId != null && insightId != requested.insightId) return false
        if (requested.metricId == null && requested.insightId == null) return false
        return true
    }

    private fun TrudyToolResult.toCallRecord(): TrudyToolCallRecord =
        if (this is TrudyToolResult.Failure) {
            TrudyToolCallRecord(operation, succeeded = false, failureCode = code)
        } else {
            TrudyToolCallRecord(operation, succeeded = true)
        }

    private fun fallback(
        answer: String,
        toolCalls: List<TrudyToolCallRecord> = emptyList(),
        warnings: List<TrudyWarning>,
        metadata: TrudyModelMetadata? = null
    ) = TrudyOrchestrationResult(
        status = TrudyOrchestrationStatus.FALLBACK,
        answerText = answer,
        evidenceReferences = emptyList(),
        toolCallsMade = toolCalls.toList(),
        warnings = warnings.distinct(),
        modelMetadata = metadata
    )

    private companion object {
        const val DEFAULT_MAX_MODEL_ITERATIONS = 4
        const val MAX_TOOL_LIMIT = 5_000
        const val LOW_QUALITY_SCORE = 50
    }
}
