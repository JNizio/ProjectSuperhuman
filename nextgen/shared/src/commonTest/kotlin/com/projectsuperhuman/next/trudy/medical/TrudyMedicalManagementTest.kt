package com.projectsuperhuman.next.trudy.medical

import com.projectsuperhuman.next.trudy.DeterministicTrudyModelClient
import com.projectsuperhuman.next.trudy.TrudyDomainContext
import com.projectsuperhuman.next.trudy.TrudyHealthContext
import com.projectsuperhuman.next.trudy.TrudyModelRequest
import com.projectsuperhuman.next.trudy.TrudyModelResult
import com.projectsuperhuman.next.trudy.TrudyToolOperation
import com.projectsuperhuman.next.trudy.TrudyToolResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrudyMedicalManagementTest {
    private val repository = CuratedMedicalManagementRepository()

    @Test
    fun curatedKnowledgeHasStableIdsProvenanceAndNoDoseInstructions() = runTest {
        val entries = CuratedMedicalManagementKnowledge.entries
        assertTrue(entries.size >= 7)
        entries.forEach { entry ->
            requireStableConditionId(entry.conditionId)
            assertTrue(entry.options.all { it.sourceIds.isNotEmpty() })
            assertTrue(entry.sources.all { it.url.startsWith("https://") })
            assertFalse(entry.options.any { Regex("\\b\\d+\\s*(mg|mcg|puffs?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.summary) })
        }
        assertFailsWith<IllegalArgumentException> { requireStableConditionId("Agent 2/Asthma") }
    }

    @Test
    fun searchReturnsManagementWithoutClaimingDiagnosis() = runTest {
        val result = repository.searchManagement("What usually helps IBS and when should I see a doctor?")
        assertEquals("irritable_bowel_syndrome", result.first().conditionId)
        assertTrue(result.first().entry.options.any { it.category == MedicalManagementCategory.DIETARY })
        assertTrue(result.first().entry.options.any { it.category == MedicalManagementCategory.SELF_TREATMENT_LIMIT })
    }

    @Test
    fun redFlagsAreProportionateAndRequireMeaningfulPattern() {
        val safety = CuratedMedicalSafetySignalProvider()
        assertEquals(MedicalEscalationLevel.NONE, safety.assess("I have ordinary back stiffness after gardening").level)
        assertEquals(MedicalEscalationLevel.NONE, safety.assess("My bladder feels full but I do not have back pain").level)
        assertEquals(
            MedicalEscalationLevel.EMERGENCY,
            safety.assess("My sciatica is worse and now I have saddle numbness").level
        )
        assertEquals(
            MedicalEscalationLevel.PROMPT,
            safety.assess("My acid reflux now comes with difficulty swallowing").level
        )
    }

    @Test
    fun urgentPlanDoesNotDelayEscalationForVaultRetrieval() = runTest {
        val planner = TrudyMedicalContextPlanner(repository)
        val plan = planner.plan("My herniated disc pain is worse and now I cannot pee")
        assertTrue(plan.isMedicalQuestion)
        assertEquals(MedicalEscalationLevel.EMERGENCY, plan.safety.level)
        assertEquals(null, plan.vaultRequest)
        assertTrue(plan.communicationPlan.leadWithEscalation)
    }

    @Test
    fun agentTwoContractAddsCandidatesWithoutCorpusDependency() = runTest {
        val agentTwoAdapter = object : MedicalConditionCandidateProvider {
            override suspend fun candidates(request: MedicalConditionCandidateRequest) = listOf(
                MedicalConditionCandidate(
                    conditionId = "asthma",
                    displayName = "Asthma",
                    relevance = MedicalCandidateRelevance.MODERATE,
                    reasonsForFit = listOf("The supplied corpus matched episodic wheeze."),
                    reasonsAgainstFit = listOf("No variability information was supplied."),
                    informationThatWouldMatter = listOf("Objective breathing tests and clinician assessment.")
                )
            )
        }
        val plan = TrudyMedicalContextPlanner(repository, agentTwoAdapter)
            .plan("What possibilities are worth considering for episodic wheeze?")
        assertEquals("asthma", plan.conditionCandidates.single().conditionId)
        assertTrue(plan.managementEntries.any { it.conditionId == "asthma" })
        assertNotNull(plan.vaultRequest)
    }

    @Test
    fun modelDecoratorRequestsBoundedVaultContextThenInjectsMedicalContext() = runTest {
        var delegatedRequest: TrudyModelRequest? = null
        val delegate = DeterministicTrudyModelClient { request ->
            delegatedRequest = request
            TrudyModelResult(responseText = "Management summary")
        }
        val client = MedicalContextAwareTrudyModelClient(
            delegate = delegate,
            planner = TrudyMedicalContextPlanner(repository)
        )
        val initial = client.complete(TrudyModelRequest(userRequest = "How is asthma usually managed?"))
        val getContext = initial.requestedTools.single() as TrudyToolOperation.GetContext
        assertTrue(getContext.request.historyLimitPerDomain <= 180)
        assertTrue(getContext.request.domains.size <= 6)
        assertEquals(null, delegatedRequest)

        val context = TrudyHealthContext(
            requestedDomains = getContext.request.domains,
            domains = getContext.request.domains.map { domain ->
                TrudyDomainContext(domain, emptyList(), emptyList(), emptyList(), emptyList(), null)
            }
        )
        val second = client.complete(
            TrudyModelRequest(
                userRequest = "How is asthma usually managed?",
                toolResults = listOf(TrudyToolResult.Context(getContext, context)),
                iteration = 1
            )
        )
        assertEquals("Management summary", second.responseText)
        val enriched = assertNotNull(delegatedRequest)
        assertTrue(enriched.systemInstruction.contains("structured, non-diagnostic"))
        assertTrue(enriched.systemInstruction.contains("asthma_inhaler_therapy"))
        assertTrue(enriched.systemInstruction.contains("Never convert symptom overlap"))
    }
}
