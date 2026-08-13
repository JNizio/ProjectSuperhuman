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
}

data class TrudyToolDefinition(
    val name: String,
    val description: String,
    val requiredFields: List<String>
)

sealed interface TrudyToolResult {
    val operation: TrudyToolOperation

    data class DomainState(
        override val operation: TrudyToolOperation.GetDomainState,
        val evidence: List<TrudyMetricEvidence>
    ) : TrudyToolResult

    data class MetricHistory(
        override val operation: TrudyToolOperation.GetMetricHistory,
        val evidence: List<TrudyMetricEvidence>
    ) : TrudyToolResult

    data class DomainHistory(
        override val operation: TrudyToolOperation.GetDomainHistory,
        val evidence: List<TrudyMetricEvidence>
    ) : TrudyToolResult

    data class DerivedFeatures(
        override val operation: TrudyToolOperation.GetDerivedFeatures,
        val evidence: List<TrudyDerivedMetricEvidence>
    ) : TrudyToolResult

    data class Insights(
        override val operation: TrudyToolOperation.GetInsights,
        val evidence: List<TrudyInsightEvidence>
    ) : TrudyToolResult

    data class DataQuality(
        override val operation: TrudyToolOperation.GetDataQuality,
        val evidence: TrudyDataQualityEvidence
    ) : TrudyToolResult

    data class Context(
        override val operation: TrudyToolOperation.GetContext,
        val context: TrudyHealthContext
    ) : TrudyToolResult
}

class TrudyHealthToolService(
    private val healthContext: TrudyHealthContextService
) {
    val definitions: List<TrudyToolDefinition> = listOf(
        TrudyToolDefinition("get_domain_state", "Get current structured state for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_metric_history", "Get bounded history for one domain-qualified metric.", listOf("domain", "metricId")),
        TrudyToolDefinition("get_domain_history", "Get bounded history for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_derived_features", "Get derived personal features for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_insights", "Get current structured insights for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_data_quality", "Get data-quality evidence for one health domain.", listOf("domain")),
        TrudyToolDefinition("get_context", "Get context for an explicit list of health domains only.", listOf("domains"))
    )

    suspend fun execute(operation: TrudyToolOperation): TrudyToolResult = when (operation) {
        is TrudyToolOperation.GetDomainState -> TrudyToolResult.DomainState(
            operation, healthContext.currentState(operation.domain)
        )
        is TrudyToolOperation.GetMetricHistory -> TrudyToolResult.MetricHistory(
            operation,
            healthContext.metricHistory(operation.domain, operation.metricId, operation.limit, operation.offset)
        )
        is TrudyToolOperation.GetDomainHistory -> TrudyToolResult.DomainHistory(
            operation, healthContext.domainHistory(operation.domain, operation.limit, operation.offset)
        )
        is TrudyToolOperation.GetDerivedFeatures -> TrudyToolResult.DerivedFeatures(
            operation, healthContext.derivedFeatures(operation.domain)
        )
        is TrudyToolOperation.GetInsights -> TrudyToolResult.Insights(
            operation, healthContext.insights(operation.domain)
        )
        is TrudyToolOperation.GetDataQuality -> TrudyToolResult.DataQuality(
            operation, healthContext.dataQuality(operation.domain)
        )
        is TrudyToolOperation.GetContext -> TrudyToolResult.Context(
            operation, healthContext.context(operation.request)
        )
    }
}
