package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

/** Provider-independent tool requests. Metric operations are always domain-qualified. */
sealed interface TrudyToolOperation {
    val domains: List<HealthDomain>

    data class GetDomainState(val domain: HealthDomain) : TrudyToolOperation {
        override val domains: List<HealthDomain> = listOf(domain)
    }

    data class GetMetricHistory(
        val domain: HealthDomain,
        val metricId: String,
        val limit: Int = 250,
        val offset: Int = 0
    ) : TrudyToolOperation {
        override val domains: List<HealthDomain> = listOf(domain)
    }

    data class GetMetricWindow(
        val domain: HealthDomain,
        val metricId: String,
        val range: TrudyTimeRange,
        val limit: Int = 250
    ) : TrudyToolOperation {
        override val domains: List<HealthDomain> = listOf(domain)
    }

    data class GetDomainHistory(
        val domain: HealthDomain,
        val limit: Int = 250,
        val offset: Int = 0
    ) : TrudyToolOperation {
        override val domains: List<HealthDomain> = listOf(domain)
    }

    data class GetDerivedFeatures(val domain: HealthDomain) : TrudyToolOperation {
        override val domains: List<HealthDomain> = listOf(domain)
    }

    data class GetInsights(val domain: HealthDomain) : TrudyToolOperation {
        override val domains: List<HealthDomain> = listOf(domain)
    }

    data class GetDataQuality(val domain: HealthDomain) : TrudyToolOperation {
        override val domains: List<HealthDomain> = listOf(domain)
    }

    data class GetContext(val request: TrudyContextRequest) : TrudyToolOperation {
        override val domains: List<HealthDomain> = request.domains
    }

    data class Unsupported(
        val name: String,
        val arguments: Map<String, String> = emptyMap()
    ) : TrudyToolOperation {
        override val domains: List<HealthDomain> = emptyList()
    }
}

data class TrudyToolDefinition(
    val name: String,
    val description: String,
    val requiredFields: List<String>
)

sealed interface TrudyToolResult {
    val operation: TrudyToolOperation

    data class DomainState(override val operation: TrudyToolOperation.GetDomainState, val evidence: List<TrudyMetricEvidence>) : TrudyToolResult
    data class MetricHistory(override val operation: TrudyToolOperation.GetMetricHistory, val evidence: List<TrudyMetricEvidence>) : TrudyToolResult
    data class MetricWindow(override val operation: TrudyToolOperation.GetMetricWindow, val evidence: List<TrudyMetricEvidence>) : TrudyToolResult
    data class DomainHistory(override val operation: TrudyToolOperation.GetDomainHistory, val evidence: List<TrudyMetricEvidence>) : TrudyToolResult
    data class DerivedFeatures(override val operation: TrudyToolOperation.GetDerivedFeatures, val evidence: List<TrudyDerivedMetricEvidence>) : TrudyToolResult
    data class Insights(override val operation: TrudyToolOperation.GetInsights, val evidence: List<TrudyInsightEvidence>) : TrudyToolResult
    data class DataQuality(override val operation: TrudyToolOperation.GetDataQuality, val evidence: TrudyDataQualityEvidence) : TrudyToolResult
    data class Context(override val operation: TrudyToolOperation.GetContext, val context: TrudyHealthContext) : TrudyToolResult
    data class Failure(override val operation: TrudyToolOperation, val code: TrudyToolFailureCode, val message: String) : TrudyToolResult
}

enum class TrudyToolFailureCode { MALFORMED_REQUEST, UNSUPPORTED_OPERATION, EXECUTION_FAILED }

interface TrudyToolExecutor {
    val definitions: List<TrudyToolDefinition>
    suspend fun execute(operation: TrudyToolOperation): TrudyToolResult
}

class TrudyHealthToolService(
    private val healthContext: TrudyHealthContextService
) : TrudyToolExecutor {
    override val definitions: List<TrudyToolDefinition> = listOf(
        TrudyToolDefinition("get_domain_state", "Get current structured state for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_metric_history", "Get bounded history for one domain-qualified metric.", listOf("domain", "metricId")),
        TrudyToolDefinition("get_metric_window", "Get a bounded, domain-qualified metric inside one explicit time window.", listOf("domain", "metricId", "range")),
        TrudyToolDefinition("get_domain_history", "Get bounded history for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_derived_features", "Get derived personal features for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_insights", "Get current structured insights for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_data_quality", "Get data-quality evidence for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_context", "Get context for an explicit list of health domains only.", listOf("domains"))
    )

    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult {
        validate(operation)?.let { message ->
            return TrudyToolResult.Failure(operation, TrudyToolFailureCode.MALFORMED_REQUEST, message)
        }
        return when (operation) {
            is TrudyToolOperation.GetDomainState -> TrudyToolResult.DomainState(operation, healthContext.currentState(operation.domain))
            is TrudyToolOperation.GetMetricHistory -> TrudyToolResult.MetricHistory(operation, healthContext.metricHistory(operation.domain, operation.metricId, operation.limit, operation.offset))
            is TrudyToolOperation.GetMetricWindow -> TrudyToolResult.MetricWindow(operation, healthContext.metricWindow(operation.domain, operation.metricId, operation.range, operation.limit))
            is TrudyToolOperation.GetDomainHistory -> TrudyToolResult.DomainHistory(operation, healthContext.domainHistory(operation.domain, operation.limit, operation.offset))
            is TrudyToolOperation.GetDerivedFeatures -> TrudyToolResult.DerivedFeatures(operation, healthContext.derivedFeatures(operation.domain))
            is TrudyToolOperation.GetInsights -> TrudyToolResult.Insights(operation, healthContext.insights(operation.domain))
            is TrudyToolOperation.GetDataQuality -> TrudyToolResult.DataQuality(operation, healthContext.dataQuality(operation.domain))
            is TrudyToolOperation.GetContext -> TrudyToolResult.Context(operation, healthContext.context(operation.request))
            is TrudyToolOperation.Unsupported -> TrudyToolResult.Failure(operation, TrudyToolFailureCode.UNSUPPORTED_OPERATION, "Unsupported Trudy tool: ${operation.name}")
            else -> TrudyToolResult.Failure(operation, TrudyToolFailureCode.UNSUPPORTED_OPERATION, "This operation belongs to another typed Trudy tool service.")
        }
    }

    private fun validate(operation: TrudyToolOperation): String? = when (operation) {
        is TrudyToolOperation.GetMetricHistory -> when {
            operation.metricId.isBlank() -> "metricId must not be blank"
            operation.limit !in 1..MAX_RESULT_LIMIT -> "limit must be between 1 and $MAX_RESULT_LIMIT"
            operation.offset < 0 -> "offset must be >= 0"
            else -> null
        }
        is TrudyToolOperation.GetMetricWindow -> when {
            operation.metricId.isBlank() -> "metricId must not be blank"
            operation.limit !in 1..MAX_RESULT_LIMIT -> "limit must be between 1 and $MAX_RESULT_LIMIT"
            else -> null
        }
        is TrudyToolOperation.GetDomainHistory -> when {
            operation.limit !in 1..MAX_RESULT_LIMIT -> "limit must be between 1 and $MAX_RESULT_LIMIT"
            operation.offset < 0 -> "offset must be >= 0"
            else -> null
        }
        is TrudyToolOperation.GetContext -> when {
            operation.request.historyLimitPerDomain !in 1..MAX_RESULT_LIMIT -> "historyLimitPerDomain must be between 1 and $MAX_RESULT_LIMIT"
            else -> null
        }
        else -> null
    }

    private companion object { const val MAX_RESULT_LIMIT = 5_000 }
}
