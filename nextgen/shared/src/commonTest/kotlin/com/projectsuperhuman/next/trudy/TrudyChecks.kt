package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrudyOutputTest {
    private val now = 2_000_000_000_000L
    private val emotionalDomain = TrudyEmotionalSemantics.routingDomain()

    @Test
    fun exerciseMoodAssociationUsesNaturalCausalBoundary() {
        val window = TrudyTimeRange(now - 30 * 86_400_000L, now)
        val evidence = PersonalEvidenceItem(
            id = "assoc:exercise_minutes:emotional_valence",
            domains = listOf(HealthDomain.EXERCISE, emotionalDomain),
            metricIds = listOf("exercise_minutes", "emotional_valence"),
            evidenceType = PersonalEvidenceType.ASSOCIATION,
            observationWindow = window,
            sampleCount = 18,
            confidence = TrudyConfidence.MODERATE,
            dataQualityStatus = TrudyDataQualityStatus.GOOD
        )
        val result = TrudyAssociationResult(
            leftDomain = HealthDomain.EXERCISE,
            leftMetricId = "exercise_minutes",
            rightDomain = emotionalDomain,
            rightMetricId = "emotional_valence",
            method = TrudyAssociationMethod.SPEARMAN,
            coefficient = 0.58,
            sampleCount = 18,
            matchedFraction = 0.9,
            direction = TrudyEffectDirection.INCREASE,
            confidence = TrudyConfidence.MODERATE,
            dataQualityStatus = TrudyDataQualityStatus.GOOD,
            evidence = evidence
        )
        val answer = assertNotNull(TrudyEmotionalResponseComposer.association("Am I happier when I exercise?", result))
        assertTrue(answer.contains("happier", ignoreCase = true))
        assertTrue(answer.contains("don't have enough evidence to say exercise caused", ignoreCase = true))
        assertTrue(answer.contains("pattern is worth watching", ignoreCase = true))
        assertFalse(answer.contains("association is not causation", ignoreCase = true))
        assertFalse(answer.contains("personal association", ignoreCase = true))
    }

    @Test
    fun broadSummaryPlansEveryObservedEmotionalAxis() {
        val current = listOf(
            metric("emotional_valence", 0.3),
            metric("emotional_calmness", -0.2),
            metric("emotional_energy", -0.1)
        )
        val request = TrudyToolOperation.GetContext(TrudyContextRequest(listOf(emotionalDomain)))
        val context = TrudyToolResult.Context(
            request,
            TrudyHealthContext(
                requestedDomains = request.domains,
                domains = listOf(TrudyDomainContext(emotionalDomain, current, emptyList(), emptyList(), emptyList(), null))
            )
        )
        val planned = TrudyEmotionalToolPlanner.followUp("How have I been feeling lately?", listOf(context))
        assertEquals(
            setOf("emotional_valence", "emotional_calmness", "emotional_energy"),
            planned.filterIsInstance<GetPersonalTrend>().map { it.metricId }.toSet()
        )
    }

    @Test
    fun nonEmotionalRequestsPassThroughDecoratorUnchanged() = runTest {
        var calls = 0
        val delegate = object : TrudyModelClient {
            override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
                calls += 1
                return TrudyModelResult(responseText = "delegate")
            }
        }
        val result = TrudyEmotionalModelClient(delegate).complete(TrudyModelRequest(userRequest = "How was my sleep?"))
        assertEquals("delegate", result.responseText)
        assertEquals(1, calls)
    }

    private fun metric(id: String, value: Double) = TrudyMetricEvidence(
        domain = emotionalDomain,
        metricId = id,
        value = value,
        unit = "score",
        timestampEpochMs = now,
        source = "test"
    )
}
