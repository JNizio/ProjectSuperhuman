package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TrudyLanguageKnowledgeRoutingTest {
    @Test
    fun largeRegressionPhraseSetRoutesToExpectedSemanticGroups() {
        val cases = listOf(
            case("sleep was rubbish", "sleep_poor"),
            case("I slept like crap", "sleep_poor"),
            case("rough night", "sleep_poor"),
            case("sleeping badly", "sleep_poor"),
            case("I keep waking up", "sleep_fragmented"),
            case("woke up loads", "sleep_fragmented"),
            case("broken sleep", "sleep_fragmented"),
            case("I'm shattered", "fatigue_slang"),
            case("I'm knackered", "fatigue_slang"),
            case("I feel wrecked", "fatigue_slang"),
            case("running on empty", "fatigue_slang"),
            case("brain fog", "brain_fog"),
            case("my head feels foggy", "brain_fog"),
            case("I can't focus", "brain_fog"),
            case("porridge", "food_uk_us"),
            case("oatmeal", "food_uk_us"),
            case("crisps", "food_uk_us"),
            case("potato chips", "food_uk_us"),
            case("chips", "food_uk_us"),
            case("fries", "food_uk_us"),
            case("chicken breast", "protein_food"),
            case("protein shake", "protein_food"),
            case("latte", "caffeine_exposure"),
            case("GORD", "gastro_oesophageal_reflux"),
            case("GERD", "gastro_oesophageal_reflux"),
            case("heartburn", "gastro_oesophageal_reflux"),
            case("acid coming up", "gastro_oesophageal_reflux"),
            case("my pulse is going mad", "palpitations"),
            case("heart racing", "palpitations"),
            case("palpatations", "palpitations"),
            case("heart fluttering", "palpitations"),
            case("tachycardia", "tachycardia"),
            case("fast heart rate", "tachycardia"),
            case("I'm breathless", "breathlessness"),
            case("short of breath", "breathlessness"),
            case("can't catch my breath", "breathlessness"),
            case("dyspnea", "breathlessness"),
            case("breathles", "breathlessness"),
            case("my stomach's playing up", "abdominal_pain"),
            case("my tummy hurts", "abdominal_pain"),
            case("belly ache", "abdominal_pain"),
            case("I've got the runs", "diarrhoea"),
            case("diarrhea", "diarrhoea"),
            case("loose stools", "diarrhoea"),
            case("I'm constipated", "constipation"),
            case("backed up", "constipation"),
            case("really bloated", "abdominal_bloating"),
            case("trapped wind", "abdominal_bloating"),
            case("I smashed legs yesterday", "exercise_hard_session"),
            case("I trained hard", "exercise_hard_session"),
            case("brutal workout", "exercise_hard_session"),
            case("I'm sore as hell", "exercise_soreness"),
            case("DOMS", "exercise_soreness"),
            case("why am I heavier this morning", "overnight_weight_change"),
            case("my weight shot up overnight", "overnight_weight_change"),
            case("the scale jumped", "overnight_weight_change"),
            case("have I been drinking enough", "hydration_adequacy"),
            case("am I dehydrated", "hydration_adequacy"),
            case("water intake", "hydration_adequacy"),
            case("electrolytes", "hydration_adequacy"),
            case("could caffeine affect my sleep", "caffeine_sleep"),
            case("coffee and sleep", "caffeine_sleep"),
            case("late coffee", "caffeine_sleep"),
            case("I'm stressed out", "stress_anxiety"),
            case("feeling anxious", "stress_anxiety"),
            case("burnt out", "stress_anxiety"),
            case("I feel wired", "wired_arousal"),
            case("hot bedroom", "hot_sleep_environment"),
            case("morning light", "light_environment"),
            case("bad air quality", "air_environment"),
            case("how could I test whether caffeine affects my sleep", "experiment_method"),
            case("run an N of 1", "experiment_method"),
            case("compare before and during", "experiment_method"),
            case("baseline period", "experiment_method")
        )

        assertTrue(cases.size >= 60)
        cases.forEach { regression ->
            val routing = TrudyLanguageRouter.route(regression.phrase)
            assertNotNull(
                routing.match(regression.semanticId),
                "Expected '${regression.phrase}' to route to ${regression.semanticId}; got ${routing.matches.map { it.semanticId }}"
            )
        }
    }

    @Test
    fun multiIntentSentenceReturnsBoundedRankedSetAcrossLanes() {
        val routing = TrudyLanguageRouter.route(
            "I've been tired and bloated and sleeping badly.",
            maxTopics = 5
        )

        assertNotNull(routing.match("fatigue_slang"))
        assertNotNull(routing.match("abdominal_bloating"))
        assertNotNull(routing.match("sleep_poor"))
        assertTrue(routing.hasKind(TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE))
        assertTrue(routing.hasKind(TrudyKnowledgeKind.MEDICAL))
        assertTrue(routing.hasKind(TrudyKnowledgeKind.NUTRITION))
        assertTrue(routing.matches.size <= 5)
    }

    @Test
    fun clinicallyAdjacentConceptsKeepSeparateSemanticIds() {
        val balance = TrudyLanguageRouter.route("I feel dizzy, lightheaded, and the room is spinning")
        val dizziness = assertNotNull(balance.match("dizziness"))
        val lightheadedness = assertNotNull(balance.match("lightheadedness"))
        val vertigo = assertNotNull(balance.match("vertigo"))

        assertEquals(dizziness.ambiguityBoundary, lightheadedness.ambiguityBoundary)
        assertEquals(lightheadedness.ambiguityBoundary, vertigo.ambiguityBoundary)
        assertEquals(3, setOf(dizziness.semanticId, lightheadedness.semanticId, vertigo.semanticId).size)

        val palpitations = TrudyLanguageRouter.route("my heart is racing")
        assertNotNull(palpitations.match("palpitations"))
        assertNull(palpitations.match("tachycardia"))

        val tachycardia = TrudyLanguageRouter.route("tachycardia")
        assertNotNull(tachycardia.match("tachycardia"))
        assertNull(tachycardia.match("palpitations"))

        val digestive = TrudyLanguageRouter.route("indigestion with acid reflux and abdominal pain")
        assertNotNull(digestive.match("indigestion"))
        assertNotNull(digestive.match("gastro_oesophageal_reflux"))
        assertNotNull(digestive.match("abdominal_pain"))
    }

    @Test
    fun medicalExpansionAddsCanonicalTermsWithoutChangingRetrievalArchitecture() {
        val tummy = TrudyLanguageRouter.expandMedicalSearchText("my tummy hurts")
        val reflux = TrudyLanguageRouter.expandMedicalSearchText("acid coming up")
        val breathless = TrudyLanguageRouter.expandMedicalSearchText("can't catch my breath")

        assertTrue(tummy.contains("abdominal pain"))
        assertTrue(reflux.contains("gastro oesophageal reflux"))
        assertTrue(breathless.contains("dyspnoea"))
        assertTrue(tummy.length <= 768 && reflux.length <= 768 && breathless.length <= 768)
    }

    @Test
    fun fuzzyMatchingIsBoundedAndDoesNotCollapseClinicalNeighbours() {
        val hydrationTypo = TrudyLanguageRouter.route("am I dehydratd")
        assertNotNull(hydrationTypo.match("hydration_adequacy"))

        // A non-listed near-neighbour is not allowed to turn one clinical concept into another.
        val vague = TrudyLanguageRouter.route("veritgoish")
        assertNull(vague.match("vertigo"))
        assertNull(vague.match("dizziness"))
    }

    @Test
    fun caffeineAndSleepAddsMethodologyOnlyWhenTestingIsRequested() = runTest {
        val coordinator = TrudyKnowledgeCoordinator()
        val discussion = coordinator.retrieve(
            TrudyKnowledgeQuery("Could caffeine be affecting my sleep?", emptyList(), emptyList())
        )
        val experiment = coordinator.retrieve(
            TrudyKnowledgeQuery("How could I test whether caffeine affects my sleep?", emptyList(), emptyList())
        )

        assertTrue(discussion.any { it.kind == TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE })
        assertTrue(discussion.any { it.kind == TrudyKnowledgeKind.NUTRITION })
        assertFalse(discussion.any { it.kind == TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY })
        assertTrue(experiment.any { it.kind == TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE })
        assertTrue(experiment.any { it.kind == TrudyKnowledgeKind.NUTRITION })
        assertTrue(experiment.any { it.kind == TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY })
    }

    @Test
    fun sharedRouterDoesNotNarrowTheMaturePerformanceLexicon() = runTest {
        val result = TrudyKnowledgeCoordinator().retrieve(
            TrudyKnowledgeQuery("Was my nap too late?", emptyList(), emptyList())
        )

        assertTrue(result.any { it.kind == TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE })
    }

    @Test
    fun productAndUnrelatedQueriesDoNotLeakHealthKnowledge() = runTest {
        val productQueries = listOf(
            "Please rename my dashboard nutrition tile",
            "Move the sleep tile",
            "Fix the hydration screen",
            "Implement a blood pressure button",
            "Navigate to experiments",
            "Change the dashboard layout",
            "How do I train a neural network model?"
        )
        val unrelatedQueries = listOf(
            "Explain Kotlin coroutines",
            "What colour should the app icon be?",
            "Draft a release note",
            "Sort these files alphabetically"
        )
        val coordinator = TrudyKnowledgeCoordinator()

        productQueries.forEach { query ->
            val routing = TrudyLanguageRouter.route(query)
            assertTrue(routing.productOnlyIntent, query)
            assertTrue(
                coordinator.retrieve(TrudyKnowledgeQuery(query, emptyList(), emptyList())).isEmpty(),
                query
            )
        }
        unrelatedQueries.forEach { query ->
            assertTrue(TrudyLanguageRouter.route(query).matches.isEmpty(), query)
            assertTrue(
                coordinator.retrieve(TrudyKnowledgeQuery(query, emptyList(), emptyList())).isEmpty(),
                query
            )
        }
    }

    @Test
    fun coordinatorRanksAndBoundsMultipleRelevantKinds() = runTest {
        val sources = listOf(
            fakeSource(TrudyKnowledgeKind.MEDICAL),
            fakeSource(TrudyKnowledgeKind.NUTRITION),
            fakeSource(TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE),
            fakeSource(TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY)
        )
        val result = TrudyKnowledgeCoordinator(sources, maxPerSource = 2, maxTotal = 3).retrieve(
            TrudyKnowledgeQuery(
                "How could I test whether caffeine affects my sleep?",
                listOf(HealthDomain.SLEEP, HealthDomain.NUTRITION),
                emptyList(),
                maxItems = 3
            )
        )

        assertEquals(3, result.size)
        assertTrue(result.any { it.kind == TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE })
        assertTrue(result.any { it.kind == TrudyKnowledgeKind.NUTRITION })
        assertTrue(result.any { it.kind == TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY })
        assertFalse(result.any { it.kind == TrudyKnowledgeKind.MEDICAL })
    }

    private fun fakeSource(itemKind: TrudyKnowledgeKind): TrudyKnowledgeSource = object : TrudyKnowledgeSource {
        override val sourceId: String = "fake-${itemKind.name.lowercase()}"
        override val kind: TrudyKnowledgeKind = itemKind

        override suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem> = listOf(
            TrudyKnowledgeItem(
                stableId = sourceId,
                kind = itemKind,
                title = itemKind.name,
                summary = "Test knowledge",
                sourceId = sourceId,
                sourceReferences = listOf("test-provenance"),
                relevantDomains = query.domains,
                version = "test",
                lastReviewed = "2026-08-14",
                lexicalRelevance = query.routing?.scoreForKind(itemKind) ?: 0
            )
        )
    }

    private data class RegressionCase(val phrase: String, val semanticId: String)
    private fun case(phrase: String, semanticId: String) = RegressionCase(phrase, semanticId)
}
