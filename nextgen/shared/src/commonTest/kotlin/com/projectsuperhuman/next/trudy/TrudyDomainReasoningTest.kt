package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrudyDomainReasoningTest {
    private val now = 2_000_000_000_000L
    private val emotionalDomain = TrudyEmotionalSemantics.routingDomain()

    @Test
    fun canonicalBipolarSemanticsMatchEmotionalCoreContract() {
        val calmness = assertNotNull(TrudyEmotionalSemantics.semanticForMetric("emotional_calmness"))
        val energy = assertNotNull(TrudyEmotionalSemantics.semanticForMetric("emotional_energy"))
        val valence = assertNotNull(TrudyEmotionalSemantics.semanticForMetric("emotional_valence"))

        assertEquals(TrudyEmotionalAxis.CALMNESS, calmness.axis)
        assertEquals("calmer", calmness.phraseForDelta(0.2))
        assertEquals("more anxious", calmness.phraseForDelta(-0.2))
        assertEquals("more drained", energy.phraseForDelta(-0.2))
        assertEquals(TrudyEmotionalAxis.VALENCE, valence.axis)
        assertEquals(TrudyEmotionalConcept.ANXIETY, TrudyEmotionalSemantics.conceptForQuestion("Have I been more anxious recently?"))
        assertEquals(TrudyEmotionalConcept.DRAIN, TrudyEmotionalSemantics.conceptForQuestion("Have I been more drained this month?"))
        assertTrue(calmness.promptHint().contains("0 is neutral"))
    }

    @Test
    fun crossDomainPlannerDiscoversCanonicalMetricAndUsesSpearman() {
        val exercise = domainContext(
            HealthDomain.EXERCISE,
            current = listOf(metric(HealthDomain.EXERCISE, "exercise_minutes", 45.0, "min"))
        )
        val emotional = domainContext(
            emotionalDomain,
            current = listOf(metric(emotionalDomain, "emotional_valence", 0.45, "score"))
        )
        val request = TrudyToolOperation.GetContext(TrudyContextRequest(listOf(HealthDomain.EXERCISE, emotionalDomain)))
        val context = TrudyToolResult.Context(
            request,
            TrudyHealthContext(requestedDomains = request.domains, domains = listOf(exercise, emotional))
        )
        val operation = TrudyEmotionalToolPlanner.followUp("Am I happier when I exercise?", listOf(context)).single() as GetAssociation

        assertEquals(HealthDomain.EXERCISE, operation.leftDomain)
        assertEquals("exercise_minutes", operation.leftMetricId)
        assertEquals(emotionalDomain, operation.rightDomain)
        assertEquals("emotional_valence", operation.rightMetricId)
        assertEquals(TrudyAssociationMethod.SPEARMAN, operation.method)
    }

    @Test
    fun modelPolicyTreatsEmotionalScalesAsNonDiagnosticBipolarSignals() {
        val policy = TrudyModelPolicy.SYSTEM_INSTRUCTION.lowercase()
        assertTrue(policy.contains("not clinical diagnostic tests"))
        assertTrue(policy.contains("emotional_calmness from anxious toward calm"))
        assertTrue(policy.contains("emotional_energy from drained toward energetic"))
        assertTrue(policy.contains("0 is a real neutral observation"))
        assertTrue(policy.contains("communicate evidence boundaries naturally"))
    }

    private fun metric(domain: HealthDomain, id: String, value: Double, unit: String) = TrudyMetricEvidence(
        domain = domain,
        metricId = id,
        value = value,
        unit = unit,
        timestampEpochMs = now,
        source = "test"
    )

    private fun domainContext(
        domain: HealthDomain,
        current: List<TrudyMetricEvidence> = emptyList()
    ) = TrudyDomainContext(domain, current, emptyList(), emptyList(), emptyList(), null)
}
