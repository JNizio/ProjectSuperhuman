package com.projectsuperhuman.next.trudy.nutrition

import com.projectsuperhuman.next.core.HealthDomain

private val STABLE_ID = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")
private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

internal fun requireStableNutritionId(id: String) {
    require(STABLE_ID.matches(id)) { "Nutrition knowledge IDs must be stable snake_case: $id" }
}

enum class NutritionKnowledgeArea {
    ENERGY_AND_METABOLISM,
    MACRONUTRIENTS,
    MICRONUTRIENTS,
    FOOD_AND_DIETARY_PATTERNS,
    HYDRATION_AND_ELECTROLYTES,
    EXERCISE_AND_RECOVERY,
    DIGESTION,
    BODY_WEIGHT_AND_COMPOSITION
}

enum class NutritionEvidenceType {
    PUBLIC_HEALTH_GUIDANCE,
    GOVERNMENT_GUIDANCE,
    PROFESSIONAL_POSITION,
    PEER_REVIEWED_RESEARCH
}

data class NutritionKnowledgeSource(
    val id: String,
    val title: String,
    val organisation: String,
    val url: String,
    val evidenceType: NutritionEvidenceType,
    val publishedOrUpdated: String? = null,
    val reviewedAt: String
) {
    init {
        requireStableNutritionId(id)
        require(title.isNotBlank() && organisation.isNotBlank())
        require(url.startsWith("https://"))
        require(publishedOrUpdated == null || publishedOrUpdated.isNotBlank())
        require(ISO_DATE.matches(reviewedAt))
    }
}

data class NutritionTopic(
    val id: String,
    val displayName: String,
    val area: NutritionKnowledgeArea,
    val aliases: Set<String>,
    val summary: String,
    val practicalContext: List<String>,
    val caveats: List<String>,
    val sourceIds: Set<String>
) {
    init {
        requireStableNutritionId(id)
        require(displayName.isNotBlank() && summary.isNotBlank())
        require(aliases.none(String::isBlank))
        require(practicalContext.isNotEmpty() && practicalContext.none(String::isBlank))
        require(caveats.none(String::isBlank))
        require(sourceIds.isNotEmpty() && sourceIds.none(String::isBlank))
    }
}

enum class NutrientCategory { VITAMIN, MINERAL, ELECTROLYTE, FATTY_ACID, OTHER_ESSENTIAL_NUTRIENT }

data class NutrientReference(
    val id: String,
    val displayName: String,
    val category: NutrientCategory,
    val aliases: Set<String>,
    val role: String,
    val dietarySources: List<String>,
    val lowIntakeContext: String,
    val excessContext: String,
    val populationConsiderations: List<String>,
    val interactionsAndCaveats: List<String>,
    val sourceIds: Set<String>
) {
    init {
        requireStableNutritionId(id)
        require(displayName.isNotBlank() && role.isNotBlank())
        require(aliases.none(String::isBlank))
        require(dietarySources.isNotEmpty() && dietarySources.none(String::isBlank))
        require(lowIntakeContext.isNotBlank() && excessContext.isNotBlank())
        require(populationConsiderations.none(String::isBlank))
        require(interactionsAndCaveats.none(String::isBlank))
        require(sourceIds.isNotEmpty() && sourceIds.none(String::isBlank))
    }
}

enum class NutritionQuestionIntent {
    PROTEIN_ADEQUACY,
    HYDRATION_ADEQUACY,
    OVERNIGHT_WEIGHT_CHANGE,
    BODY_COMPOSITION_CHANGE,
    ENERGY_BALANCE,
    FOOD_AND_ENERGY,
    DIETARY_NUTRIENT_GAP,
    EXERCISE_NUTRITION,
    GENERAL_NUTRITION
}

enum class FoodLocale { UK, US }

data class FoodAlias(
    val phrase: String,
    val locales: Set<FoodLocale> = emptySet()
) {
    init { require(phrase.isNotBlank()) }
}

data class FoodTerm(
    val id: String,
    val displayName: String,
    val aliases: Set<FoodAlias>,
    val interpretation: String,
    val clarification: String? = null
) {
    init {
        requireStableNutritionId(id)
        require(displayName.isNotBlank() && aliases.isNotEmpty() && interpretation.isNotBlank())
    }
}

sealed interface FoodTermResolution {
    data class Resolved(val term: FoodTerm, val matchedPhrase: String) : FoodTermResolution
    data class Ambiguous(val candidates: List<FoodTerm>, val matchedPhrase: String) : FoodTermResolution {
        init { require(candidates.size > 1) }
    }
    data object NotFound : FoodTermResolution
}

data class NutritionKnowledgeContext(
    val question: String,
    val intent: NutritionQuestionIntent,
    val topics: List<NutritionTopic>,
    val nutrients: List<NutrientReference>,
    val foodResolution: FoodTermResolution,
    val metricBindings: List<TrudyNutritionMetricBinding>,
    val sources: List<NutritionKnowledgeSource>,
    val safetyInstructions: List<String>
) {
    init {
        require(question.isNotBlank())
        val knownSourceIds = sources.mapTo(mutableSetOf()) { it.id }
        require((topics.flatMap { it.sourceIds } + nutrients.flatMap { it.sourceIds }).all { it in knownSourceIds }) {
            "Every knowledge item in a context must resolve to included provenance"
        }
    }
}

interface TrudyNutritionKnowledgeProvider {
    val nutrientCount: Int
    fun sources(): List<NutritionKnowledgeSource>
    fun topic(id: String): NutritionTopic?
    fun nutrient(idOrAlias: String): NutrientReference?
    fun searchNutrients(query: String, limit: Int = 8): List<NutrientReference>
    fun resolveFood(text: String, localeHint: FoodLocale? = null): FoodTermResolution
    fun resolveIntent(question: String): NutritionQuestionIntent
    fun contextFor(question: String, localeHint: FoodLocale? = null): NutritionKnowledgeContext
}

object TrudyNutritionSafetyPolicy {
    val constraints: List<String> = listOf(
        "Treat food logs as incomplete dietary evidence, never as a diagnosis of deficiency or toxicity.",
        "State when missing nutrient values make an apparent low intake uncertain; unknown is not zero.",
        "Do not infer a blood concentration, disease, absorption problem or supplement requirement from intake rows.",
        "Do not prescribe supplement doses. Flag clinician or registered-dietitian input for suspected deficiency, pregnancy, restrictive diets, kidney disease, medication interactions or persistent symptoms.",
        "Do not turn short-term weight changes into fat-gain claims; consider measurement conditions, fluid, glycogen, gut contents and sodium before longer-term energy balance.",
        "Avoid moralising food or weight, rigid targets, compensatory restriction and language that could reinforce disordered eating.",
        "Hydration advice must be contextual. Do not encourage forced intake or imply that more water is always safer.",
        "Use personal trends and associations cautiously and do not claim that a food or nutrient caused an outcome."
    )

    const val MODEL_INSTRUCTION: String = """NUTRITION, HYDRATION AND BODY EVIDENCE POLICY
Use the supplied Project Superhuman metrics only in their declared domain, unit, time window and provenance. Food-log micronutrients are partial because many source records omit micronutrients; missing data is unknown, not zero.
Dietary evidence can identify possible coverage gaps or patterns, but it cannot diagnose a nutrient deficiency, toxicity, malabsorption or medical condition. Do not infer blood status from food logs and do not prescribe supplement doses.
Interpret weight over an appropriate trend window. A short-term change can reflect water, glycogen, sodium, gut contents, menstrual-cycle context or measurement conditions and is not automatically fat change.
Hydration needs vary with food, activity, heat, sweating, illness, pregnancy and individual clinical constraints. Never encourage forced or excessive water intake.
Keep food and body language neutral and non-judgemental. Do not encourage punitive restriction, compensatory exercise or obsessive measurement.
Use deterministic Trudy trend and association tools for personal calculations. Describe associations as uncertain personal evidence rather than causation."""
}

data class TrudyNutritionMetricBinding(
    val id: String,
    val domain: HealthDomain,
    val metricIds: List<String>,
    val canonicalUnit: String,
    val purpose: String,
    val historyLimit: Int = 120,
    val registryRequired: Boolean = true
) {
    init {
        requireStableNutritionId(id)
        require(metricIds.isNotEmpty() && metricIds.none(String::isBlank))
        require(canonicalUnit.isNotBlank() && purpose.isNotBlank())
        require(historyLimit in 1..500)
    }
}
