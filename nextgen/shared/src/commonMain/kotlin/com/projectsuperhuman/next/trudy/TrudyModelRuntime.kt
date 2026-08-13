package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

/** Runtime-agnostic model mode. Selection belongs in a platform composition root. */
enum class TrudyModelRuntimeMode { DETERMINISTIC, HOSTED, LOCAL }

/**
 * Compact provider-neutral rendering of one model turn.
 * The formatter deliberately bounds history/results so hosted and local adapters do not dump
 * unbounded personal data into a prompt.
 */
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
        appendLine("SYSTEM")
        appendLine(systemInstruction)
        appendLine("\nCONVERSATION")
        appendLine(conversation.ifBlank { "(none)" })
        appendLine("\nUSER")
        appendLine(userMessage)
        appendLine("\nTOOLS")
        appendLine(tools.ifBlank { "(none)" })
        appendLine("\nTOOL RESULTS")
        appendLine(toolResults.ifBlank { "(none)" })
        appendLine("\nEVIDENCE INDEX")
        appendLine(evidenceIndex.ifBlank { "(none)" })
        append("\nITERATION ").append(iteration)
    }
}

class TrudyPromptFormatter(
    private val maxConversationTurns: Int = 12,
    private val maxEvidenceRowsPerResult: Int = 24
) {
    init {
        require(maxConversationTurns > 0)
        require(maxEvidenceRowsPerResult > 0)
    }

    fun format(request: TrudyModelRequest): TrudyFormattedModelInput {
        val conversation = request.conversationContext
            .takeLast(maxConversationTurns)
            .joinToString("\n") { turn ->
                "${turn.role.name}: ${turn.text.trim().take(MAX_TEXT_CHARS)}"
            }
        val tools = request.toolDefinitions.joinToString("\n") { definition ->
            "${definition.name}(${definition.requiredFields.joinToString()}): ${definition.description}"
        }
        val results = request.toolResults.joinToString("\n") { result -> renderResult(result) }
        val evidence = request.toolResults
            .flatMap(::evidenceReferences)
            .distinct()
            .take(MAX_EVIDENCE_INDEX)
            .joinToString("\n") { ref -> ref.renderKey() }

        return TrudyFormattedModelInput(
            systemInstruction = request.systemInstruction,
            userMessage = request.userRequest.trim().take(MAX_TEXT_CHARS),
            conversation = conversation,
            tools = tools,
            toolResults = results,
            evidenceIndex = evidence,
            iteration = request.iteration
        )
    }

    private fun renderResult(result: TrudyToolResult): String = when (result) {
        is TrudyToolResult.DomainState ->
            "domain_state ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.MetricHistory ->
            "metric_history ${result.operation.domain}/${result.operation.metricId}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DomainHistory ->
            "domain_history ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DerivedFeatures ->
            "derived ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.Insights ->
            "insights ${result.operation.domain}: ${result.evidence.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() }}"
        is TrudyToolResult.DataQuality ->
            "data_quality ${result.operation.domain}: ${result.evidence.renderCompact()}"
        is TrudyToolResult.Context -> buildString {
            append("context ")
            append(result.context.requestedDomains.joinToString())
            append(": ")
            append(
                result.context.domains.joinToString(" | ") { domain ->
                    buildString {
                        append(domain.domain)
                        append(" current=")
                        append(domain.currentState.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() })
                        append(" derived=")
                        append(domain.derivedFeatures.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() })
                        append(" insights=")
                        append(domain.insights.take(maxEvidenceRowsPerResult).joinToString { it.renderCompact() })
                        domain.dataQuality?.let { append(" quality=").append(it.renderCompact()) }
                    }
                }
            )
        }
        is TrudyToolResult.Failure ->
            "tool_failure ${result.operation}: ${result.code} ${result.message.take(MAX_TEXT_CHARS)}"
    }

    private fun TrudyMetricEvidence.renderCompact(): String =
        "${domain.name}/$metricId=$value $unit @${timestampEpochMs} source=$source"

    private fun TrudyDerivedMetricEvidence.renderCompact(): String =
        "${domain.name}/$metricId latest=$latest $unit mean=$mean samples=$sampleCount change=${change ?: "n/a"} range=${range.fromEpochMs}-${range.toEpochMs}"

    private fun TrudyInsightEvidence.renderCompact(): String =
        "${domain.name}/$id kind=${evidenceKind.name} confidence=${confidence ?: "n/a"} evidence=${evidenceMetricIds.joinToString()} title=${title.take(180)}"

    private fun TrudyDataQualityEvidence.renderCompact(): String =
        "${domain.name} score=$score records=$recordCount metrics=$distinctMetricCount stale=$isStale notes=${notes.joinToString(";").take(240)}"

    private companion object {
        const val MAX_TEXT_CHARS = 2_000
        const val MAX_EVIDENCE_INDEX = 128
    }
}

/**
 * Swappable local/open-model backend. A llama.cpp/ONNX/MediaPipe/ExecuTorch/MLC implementation can
 * sit behind this interface without changing shared Trudy orchestration.
 */
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
        val result = engine.generate(formatter.format(request))
        return TrudyModelResult(
            responseText = result.responseText,
            requestedTools = result.requestedTools,
            evidenceReferences = result.evidenceReferences,
            metadata = TrudyModelMetadata(
                provider = "local",
                model = engine.engineId,
                attributes = result.attributes
            )
        )
    }
}

/**
 * Offline development/runtime model. It uses the real tool loop and never reads health data itself.
 * First pass requests bounded tools. Second pass only summarizes returned structured evidence.
 */
class OfflineDeterministicTrudyModelClient : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val metadata = TrudyModelMetadata(provider = "offline", model = MODEL_ID)
        if (request.toolResults.isEmpty()) {
            val tools = toolPlan(request.userRequest)
            if (tools.isEmpty()) {
                return TrudyModelResult(
                    responseText = "I can answer general questions offline. For personal health questions I use Project Superhuman's structured health tools rather than guessing.",
                    metadata = metadata
                )
            }
            return TrudyModelResult(requestedTools = tools, metadata = metadata)
        }

        val successful = request.toolResults.filter { it !is TrudyToolResult.Failure }
        if (successful.isEmpty()) {
            return TrudyModelResult(
                responseText = "I couldn't access reliable health evidence for that request, so I won't infer a personal result.",
                metadata = metadata
            )
        }

        val references = successful.flatMap(::evidenceReferences).distinct().take(MAX_REFERENCES)
        return TrudyModelResult(
            responseText = summarize(request.userRequest, successful),
            evidenceReferences = references,
            metadata = metadata
        )
    }

    private fun toolPlan(message: String): List<TrudyToolOperation> {
        val text = message.lowercase()
        return when {
            "sleep" in text -> listOf(
                TrudyToolOperation.GetContext(
                    TrudyContextRequest(
                        domains = listOf(HealthDomain.SLEEP),
                        historyLimitPerDomain = 30,
                        includeHistory = false,
                        includeCurrentState = true,
                        includeDerivedFeatures = true,
                        includeInsights = true,
                        includeDataQuality = true
                    )
                )
            )
            "changed" in text || "today" in text -> listOf(
                TrudyToolOperation.GetContext(
                    TrudyContextRequest(
                        domains = HealthDomain.entries,
                        historyLimitPerDomain = 20,
                        includeHistory = false,
                        includeCurrentState = true,
                        includeDerivedFeatures = true,
                        includeInsights = true,
                        includeDataQuality = true
                    )
                )
            )
            "pay attention" in text || "attention" in text || "priority" in text -> listOf(
                TrudyToolOperation.GetContext(
                    TrudyContextRequest(
                        domains = HealthDomain.entries,
                        historyLimitPerDomain = 10,
                        includeHistory = false,
                        includeCurrentState = false,
                        includeDerivedFeatures = true,
                        includeInsights = true,
                        includeDataQuality = true
                    )
                )
            )
            else -> emptyList()
        }
    }

    private fun summarize(question: String, results: List<TrudyToolResult>): String {
        val contexts = results.filterIsInstance<TrudyToolResult.Context>().flatMap { it.context.domains }
        if (contexts.isEmpty()) return "I retrieved structured evidence, but there isn't enough bounded context here to produce a reliable personal summary."

        val nonEmpty = contexts.filter { domain ->
            domain.currentState.isNotEmpty() || domain.derivedFeatures.isNotEmpty() || domain.insights.isNotEmpty()
        }
        if (nonEmpty.isEmpty()) return "There isn't enough stored personal data in the requested area yet to give you a reliable answer."

        val stale = contexts.filter { it.dataQuality?.isStale == true }.map { it.domain }
        val lowQuality = contexts.filter { (it.dataQuality?.score ?: 100) < LOW_QUALITY_THRESHOLD }.map { it.domain }
        val text = question.lowercase()

        return buildString {
            when {
                "sleep" in text -> {
                    val sleep = nonEmpty.firstOrNull { it.domain == HealthDomain.SLEEP }
                    if (sleep == null) {
                        append("I don't have enough stored sleep evidence to assess your sleep reliably.")
                    } else {
                        append("Your sleep context has ")
                        append(sleep.currentState.size)
                        append(" current metric(s), ")
                        append(sleep.derivedFeatures.size)
                        append(" derived feature(s), and ")
                        append(sleep.insights.size)
                        append(" current insight(s).")
                        sleep.insights.firstOrNull()?.let { append(" The strongest available signal is: ").append(it.title).append('.') }
                    }
                }
                "changed" in text || "today" in text -> {
                    val changed = nonEmpty.flatMap { domain ->
                        domain.derivedFeatures.filter { it.change != null }.map { domain.domain to it }
                    }.take(4)
                    if (changed.isEmpty()) append("I found current health data, but not enough bounded trend evidence to identify a reliable recent change.")
                    else {
                        append("The bounded data shows recent change signals in ")
                        append(changed.joinToString { (domain, metric) -> "${domain.name.lowercase()} · ${metric.metricId}" })
                        append(". These are descriptive trends, not proof of causation.")
                    }
                }
                else -> {
                    val insights = nonEmpty.flatMap { it.insights }.sortedByDescending { it.confidence ?: 0.0 }.take(3)
                    if (insights.isEmpty()) append("I found personal data, but no current structured insight is strong enough to prioritize.")
                    else append("The current structured signals to review are: ").append(insights.joinToString { it.title }).append('.')
                }
            }
            if (stale.isNotEmpty()) append(" Some requested data is stale: ").append(stale.joinToString { it.name.lowercase() }).append('.')
            if (lowQuality.isNotEmpty()) append(" Data quality is limited for: ").append(lowQuality.joinToString { it.name.lowercase() }).append('.')
        }
    }

    private companion object {
        const val MODEL_ID = "trudy-deterministic-v1"
        const val MAX_REFERENCES = 24
        const val LOW_QUALITY_THRESHOLD = 40
    }
}

/** Only citation-bindable evidence is offered back to the model. Data quality stays a warning. */
internal fun evidenceReferences(result: TrudyToolResult): List<TrudyEvidenceReference> = when (result) {
    is TrudyToolResult.DomainState -> result.evidence.map { it.toReference() }
    is TrudyToolResult.MetricHistory -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DomainHistory -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DerivedFeatures -> result.evidence.map { it.toReference() }
    is TrudyToolResult.Insights -> result.evidence.map { it.toReference() }
    is TrudyToolResult.DataQuality -> emptyList()
    is TrudyToolResult.Context -> result.context.domains.flatMap { domain ->
        domain.currentState.map { it.toReference() } +
            domain.history.map { it.toReference() } +
            domain.derivedFeatures.map { it.toReference() } +
            domain.insights.map { it.toReference() }
    }
    is TrudyToolResult.Failure -> emptyList()
}

private fun TrudyMetricEvidence.toReference() = TrudyEvidenceReference(
    domain = domain,
    metricId = metricId,
    evidenceKind = evidenceKind,
    timestampEpochMs = timestampEpochMs
)

private fun TrudyDerivedMetricEvidence.toReference() = TrudyEvidenceReference(
    domain = domain,
    metricId = metricId,
    evidenceKind = evidenceKind,
    range = range
)

private fun TrudyInsightEvidence.toReference() = TrudyEvidenceReference(
    domain = domain,
    insightId = id,
    evidenceKind = evidenceKind
)

private fun TrudyEvidenceReference.renderKey(): String = buildString {
    append(domain.name)
    append('/')
    append(metricId ?: insightId ?: evidenceKind.name)
    append(" kind=").append(evidenceKind.name)
    timestampEpochMs?.let { append(" at=").append(it) }
    range?.let { append(" range=").append(it.fromEpochMs).append('-').append(it.toEpochMs) }
}
