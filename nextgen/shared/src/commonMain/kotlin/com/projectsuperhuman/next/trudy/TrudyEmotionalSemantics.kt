package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

/** Facade over Emotional metric and question semantics. */
object TrudyEmotionalSemantics {
    const val DOMAIN_NAME = "EMOTIONAL"

    fun domainOrNull(): HealthDomain? = HealthDomain.entries.firstOrNull { it.name == DOMAIN_NAME }
    fun routingDomain(): HealthDomain = domainOrNull() ?: HealthDomain.entries.first()
    fun isEmotionalDomain(domain: HealthDomain) = domain.name == DOMAIN_NAME
    fun looksEmotionalQuestion(message: String) = TrudyEmotionalQuestionParser.looksEmotional(message)
    fun conceptForQuestion(message: String) = TrudyEmotionalQuestionParser.concept(message)
    fun axisForQuestion(message: String) = conceptForQuestion(message)?.axis ?: TrudyEmotionalAxis.VALENCE
    fun isBroadSummaryQuestion(message: String) = TrudyEmotionalQuestionParser.isBroadSummary(message)
    fun semanticForMetric(metricId: String) = TrudyEmotionalMetricCatalog.semanticForMetric(metricId)
    fun preferredMetricId(axis: TrudyEmotionalAxis) = TrudyEmotionalMetricCatalog.semantic(axis).metricId
    fun displayMetric(metricId: String) = semanticForMetric(metricId)?.displayName ?: metricId.replace('_', ' ')
    fun selectMetric(axis: TrudyEmotionalAxis, ids: Collection<String>) = TrudyEmotionalMetricCatalog.selectMetric(axis, ids)
    fun phraseForQuestion(concept: TrudyEmotionalConcept?, metric: TrudyEmotionalMetricSemantic, numericDirection: Int) =
        TrudyEmotionalPhraseInterpreter.phrase(concept, metric, numericDirection)
}
