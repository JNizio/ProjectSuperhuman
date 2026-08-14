package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

/** Broad lexical classes used for auditability; semantic IDs retain the clinically useful detail. */
enum class TrudyPhraseClass {
    SLEEP,
    FATIGUE_AND_FOCUS,
    EXERCISE,
    CARDIOVASCULAR_LANGUAGE,
    ABDOMINAL_LANGUAGE,
    BREATHING_LANGUAGE,
    BALANCE_LANGUAGE,
    NUTRITION_AND_FOOD,
    HYDRATION,
    BODY_WEIGHT,
    EMOTIONAL_LANGUAGE,
    ENVIRONMENT,
    EXPERIMENT_LANGUAGE,
    FORMAL_MEDICAL_LANGUAGE
}

/**
 * One ranked lexical retrieval result. [score] is lexical relevance only: it must never be
 * rendered or interpreted as a diagnostic probability, condition likelihood or confidence.
 */
data class TrudyLanguageTopicMatch(
    val semanticId: String,
    val phraseClass: TrudyPhraseClass,
    val kinds: Set<TrudyKnowledgeKind>,
    val domains: Set<HealthDomain>,
    val score: Int,
    val matchedAliases: Set<String>,
    val canonicalSearchTerms: Set<String>,
    val ambiguityBoundary: String? = null
) {
    init {
        require(semanticId.isNotBlank())
        require(kinds.isNotEmpty())
        require(score in 1..MAX_LEXICAL_SCORE)
        require(matchedAliases.isNotEmpty() && matchedAliases.none(String::isBlank))
        require(canonicalSearchTerms.none(String::isBlank))
    }

    private companion object { const val MAX_LEXICAL_SCORE = 160 }
}

/** One immutable, bounded language decision that can be reused by every corpus during a turn. */
data class TrudyLanguageRouting(
    val originalText: String,
    val normalizedText: String,
    val matches: List<TrudyLanguageTopicMatch>,
    val productOnlyIntent: Boolean,
    val maxTopics: Int
) {
    init {
        require(originalText.isNotBlank())
        require(maxTopics in 1..TrudyLanguageRouter.MAX_TOPICS)
        require(matches.size <= maxTopics)
        require(matches.map { it.semanticId }.distinct().size == matches.size)
        if (productOnlyIntent) require(matches.isEmpty())
    }

    fun hasKind(kind: TrudyKnowledgeKind): Boolean = matches.any { kind in it.kinds }

    fun scoreForKind(kind: TrudyKnowledgeKind): Int =
        matches.filter { kind in it.kinds }.maxOfOrNull { it.score } ?: 0

    fun match(semanticId: String): TrudyLanguageTopicMatch? = matches.firstOrNull { it.semanticId == semanticId }

    fun canonicalTermsFor(kinds: Set<TrudyKnowledgeKind>, maxTerms: Int = 12): List<String> {
        require(maxTerms in 1..32)
        return matches.asSequence()
            .filter { match -> match.kinds.any { it in kinds } }
            .flatMap { it.canonicalSearchTerms.asSequence() }
            .distinct()
            .take(maxTerms)
            .toList()
    }
}

/**
 * Shared natural-language router for the already-curated Trudy corpora.
 *
 * Aliases are grouped by meaning, normalized once and addressed through a token index. Exact and
 * phrase matches dominate; one-edit fuzzy matching is limited to explicitly safe, long, single
 * words. Clinically adjacent concepts have separate semantic IDs and ambiguity boundaries.
 */
object TrudyLanguageRouter {
    const val DEFAULT_MAX_TOPICS = 8
    const val MAX_TOPICS = 12

    // Declared before the alias index because group construction normalizes its canonical rows.
    private val SPELLING_ALIASES = mapOf(
        "fiber" to "fibre",
        "glycemic" to "glycaemic",
        "diarrhea" to "diarrhoea",
        "dyspnea" to "dyspnoea",
        "excersize" to "exercise",
        "excercise" to "exercise",
        "exersize" to "exercise",
        "heartrate" to "heart rate"
    )
    private val COMPOUNDS = mapOf(
        "brainfog" to listOf("brain", "fog"),
        "bedtime" to listOf("bed", "time"),
        "breathwork" to listOf("breath", "work"),
        "nightshift" to listOf("night", "shift")
    )

    private val groups: List<AliasGroup> = languageGroups()
    private val aliasRows: List<AliasRow> = groups.flatMap { group ->
        group.aliases.mapNotNull { alias ->
            val normalized = normalize(alias)
            if (normalized.isBlank()) null else AliasRow(group, alias, normalized, normalized.split(' '))
        }.distinctBy { it.normalizedAlias }
    }
    private val tokenIndex: Map<String, List<AliasRow>> = buildMap<String, MutableList<AliasRow>> {
        aliasRows.forEach { row ->
            row.tokens.distinct().forEach { token -> getOrPut(token) { mutableListOf() }.add(row) }
        }
    }
    private val fuzzyRows: List<AliasRow> = aliasRows.filter {
        it.group.fuzzySafe && it.tokens.size == 1 && it.tokens.single().length >= MIN_FUZZY_LENGTH
    }

    val aliasCount: Int get() = aliasRows.size
    val groupCount: Int get() = groups.size

    init {
        require(groups.map { it.semanticId }.distinct().size == groups.size)
        require(aliasRows.map { it.normalizedAlias to it.group.semanticId }.distinct().size == aliasRows.size)
        require(groups.all { it.kinds.isNotEmpty() && it.aliases.isNotEmpty() })
    }

    fun route(text: String, maxTopics: Int = DEFAULT_MAX_TOPICS): TrudyLanguageRouting {
        require(text.isNotBlank())
        require(maxTopics in 1..MAX_TOPICS)
        val normalized = normalize(text)
        if (normalized.isBlank() || looksLikeProductOnlyRequest(normalized)) {
            return TrudyLanguageRouting(text, normalized, emptyList(), normalized.isNotBlank(), maxTopics)
        }

        val tokens = normalized.split(' ')
        val tokenSet = tokens.toSet()
        val candidates = linkedSetOf<AliasRow>()
        tokens.forEach { token -> candidates += tokenIndex[token].orEmpty() }
        fuzzyRows.forEach { row ->
            val aliasToken = row.tokens.single()
            if (tokens.any { token -> isSafeOneEditMatch(token, aliasToken) }) candidates += row
        }

        val accumulators = mutableMapOf<String, MatchAccumulator>()
        candidates.forEach { row ->
            val rowScore = score(row, normalized, tokens, tokenSet)
            if (rowScore <= 0) return@forEach
            val accumulator = accumulators.getOrPut(row.group.semanticId) { MatchAccumulator(row.group) }
            accumulator.bestScore = maxOf(accumulator.bestScore, rowScore)
            accumulator.aliases += row.originalAlias
        }

        val matches = accumulators.values.map { accumulator ->
            val group = accumulator.group
            TrudyLanguageTopicMatch(
                semanticId = group.semanticId,
                phraseClass = group.phraseClass,
                kinds = group.kinds,
                domains = group.domains,
                score = accumulator.bestScore.coerceAtMost(MAX_LEXICAL_SCORE),
                matchedAliases = accumulator.aliases,
                canonicalSearchTerms = group.canonicalTerms,
                ambiguityBoundary = group.ambiguityBoundary
            )
        }.sortedWith(
            compareByDescending<TrudyLanguageTopicMatch> { it.score }
                .thenBy { it.semanticId }
        ).take(maxTopics)

        return TrudyLanguageRouting(text, normalized, matches, productOnlyIntent = false, maxTopics = maxTopics)
    }

    /** Adds a bounded set of canonical symptom terms before the existing medical posting lookup. */
    fun expandMedicalSearchText(text: String, maxTerms: Int = 12): String {
        val routing = route(text, maxTopics = DEFAULT_MAX_TOPICS)
        if (routing.productOnlyIntent) return text
        val terms = routing.canonicalTermsFor(setOf(TrudyKnowledgeKind.MEDICAL), maxTerms)
        return appendTerms(text, terms)
    }

    fun normalize(text: String): String {
        val punctuationNormalized = text.lowercase()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace("'", "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (punctuationNormalized.isBlank()) return ""
        return punctuationNormalized.split(Regex("\\s+"))
            .flatMap { token -> COMPOUNDS[token] ?: listOf(SPELLING_ALIASES[token] ?: token) }
            .joinToString(" ")
    }

    private fun score(
        row: AliasRow,
        normalizedText: String,
        tokens: List<String>,
        tokenSet: Set<String>
    ): Int = when {
        normalizedText == row.normalizedAlias -> EXACT_SCORE + row.tokens.size * TOKEN_WEIGHT + row.group.priority
        tokens.containsSequence(row.tokens) -> PHRASE_SCORE + row.tokens.size * TOKEN_WEIGHT + row.group.priority
        row.tokens.size >= 2 && row.tokens.toSet().all { it in tokenSet } ->
            UNORDERED_SCORE + row.tokens.size * TOKEN_WEIGHT + row.group.priority
        row.tokens.size == 1 && row.tokens.single() in tokenSet -> SINGLE_TOKEN_SCORE + row.group.priority
        row.group.fuzzySafe && row.tokens.size == 1 &&
            tokens.any { isSafeOneEditMatch(it, row.tokens.single()) } -> FUZZY_SCORE + row.group.priority
        else -> 0
    }

    private fun looksLikeProductOnlyRequest(normalized: String): Boolean {
        if (STRICT_PRODUCT_PHRASES.any { containsPhrase(normalized, it) }) return true
        val hasProductObject = PRODUCT_OBJECTS.any { containsPhrase(normalized, it) }
        val hasProductOperation = PRODUCT_OPERATIONS.any { containsPhrase(normalized, it) }
        return hasProductObject && hasProductOperation
    }

    private fun appendTerms(text: String, terms: List<String>): String {
        if (terms.isEmpty()) return text
        val normalizedOriginal = normalize(text)
        val newTerms = terms.filterNot { containsPhrase(normalizedOriginal, normalize(it)) }
        // Prefix expansions so the medical provider's existing MAX_QUERY_WORDS bound cannot drop
        // canonical symptom terms from a long, multi-intent user message.
        return if (newTerms.isEmpty()) text else "${newTerms.joinToString(" ")} $text".take(MAX_EXPANDED_TEXT_CHARS)
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        phrase.isNotBlank() && " ${normalize(phrase)} " in " $text "

    private fun List<String>.containsSequence(needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        for (start in 0..size - needle.size) {
            if (needle.indices.all { offset -> this[start + offset] == needle[offset] }) return true
        }
        return false
    }

    private fun isSafeOneEditMatch(left: String, right: String): Boolean {
        if (left.length < MIN_FUZZY_LENGTH || right.length < MIN_FUZZY_LENGTH) return false
        if (kotlin.math.abs(left.length - right.length) > 1 || left == right) return false
        if (left.length == right.length) return left.indices.count { left[it] != right[it] } == 1
        val shorter = if (left.length < right.length) left else right
        val longer = if (left.length < right.length) right else left
        var shortIndex = 0
        var longIndex = 0
        var edits = 0
        while (shortIndex < shorter.length && longIndex < longer.length) {
            if (shorter[shortIndex] == longer[longIndex]) {
                shortIndex++
                longIndex++
            } else {
                edits++
                longIndex++
                if (edits > 1) return false
            }
        }
        return true
    }

    private data class AliasGroup(
        val semanticId: String,
        val phraseClass: TrudyPhraseClass,
        val kinds: Set<TrudyKnowledgeKind>,
        val domains: Set<HealthDomain>,
        val priority: Int,
        val canonicalTerms: Set<String>,
        val aliases: Set<String>,
        val fuzzySafe: Boolean = true,
        val ambiguityBoundary: String? = null
    )

    private data class AliasRow(
        val group: AliasGroup,
        val originalAlias: String,
        val normalizedAlias: String,
        val tokens: List<String>
    )

    private data class MatchAccumulator(
        val group: AliasGroup,
        var bestScore: Int = 0,
        val aliases: MutableSet<String> = linkedSetOf()
    )

    private fun languageGroups(): List<AliasGroup> = listOf(
        group("sleep_poor", TrudyPhraseClass.SLEEP, performanceKinds(), sleepDomains(), 15,
            setOf("sleep was rubbish", "bad sleep"), setOf(
                "sleep was rubbish", "sleep was terrible", "sleep was awful", "sleep was crap", "slept like crap", "slept like shit",
                "slept badly", "sleeping badly", "rubbish sleep", "crap sleep", "rough night", "bad nights sleep", "couldnt sleep",
                "cant sleep", "sleep sucked", "poor sleep", "bad sleep", "insomnia"
            )),
        group("sleep_fragmented", TrudyPhraseClass.SLEEP, performanceKinds(), sleepDomains(), 17,
            setOf("kept waking up", "broken sleep"), setOf(
                "i keep waking up", "kept waking up", "woke up loads", "woke up a lot", "waking all night",
                "broken sleep", "fragmented sleep", "restless sleep", "multiple awakenings", "sleep interruptions", "waso"
            )),
        group("sleep_general", TrudyPhraseClass.SLEEP, performanceKinds(), sleepDomains(), 3,
            setOf("sleep", "sleep quality"), setOf("sleep", "slept", "sleep quality", "sleep routine", "bedtime", "body clock")),
        group("wired_arousal", TrudyPhraseClass.FATIGUE_AND_FOCUS,
            setOf(TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE, TrudyKnowledgeKind.EMOTIONAL_WELLBEING),
            setOf(HealthDomain.SLEEP, HealthDomain.EMOTIONAL), 15, setOf("i feel wired", "mind racing"), setOf(
                "i feel wired", "wired but tired", "cant switch off", "cannot switch off", "mind racing",
                "brain wont switch off", "too alert to sleep", "keyed up", "on edge at night"
            )),
        group("fatigue_slang", TrudyPhraseClass.FATIGUE_AND_FOCUS, performanceKinds(),
            setOf(HealthDomain.SLEEP, HealthDomain.EXERCISE), 14, setOf("im shattered", "fatigue"), setOf(
                "im shattered", "shattered", "im knackered", "knackered", "im wrecked", "feel wrecked", "wrecked",
                "wiped out", "im wiped", "drained", "exhausted", "tired", "dead tired", "running on empty", "no energy",
                "feel cooked", "absolutely gassed", "fatigued", "fatigue"
            )),
        group("brain_fog", TrudyPhraseClass.FATIGUE_AND_FOCUS,
            setOf(TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE, TrudyKnowledgeKind.EMOTIONAL_WELLBEING),
            setOf(HealthDomain.SLEEP, HealthDomain.EMOTIONAL), 14, setOf("brain fog", "poor concentration"), setOf(
                "brain fog", "brainfog", "foggy headed", "head feels foggy", "cant focus", "cannot focus",
                "poor concentration", "mentally foggy", "sluggish thinking"
            )),
        group("exercise_hard_session", TrudyPhraseClass.EXERCISE, performanceKinds(), setOf(HealthDomain.EXERCISE), 16,
            setOf("trained hard", "hard workout"), setOf(
                "i smashed legs", "smashed legs", "smashed leg day", "trained hard", "hard session", "hard workout",
                "brutal workout", "crushed my workout", "killed my workout", "went beast mode", "big gym session",
                "gym destroyed me", "workout destroyed me"
            )),
        group("exercise_soreness", TrudyPhraseClass.EXERCISE, performanceKinds(), setOf(HealthDomain.EXERCISE), 17,
            setOf("doms", "muscle soreness"), setOf(
                "sore as hell", "legs are sore", "sore after gym", "aching after workout", "muscle soreness",
                "delayed onset muscle soreness", "doms", "leg day soreness", "cant walk after leg day"
            )),
        group("exercise_general", TrudyPhraseClass.EXERCISE, performanceKinds(), setOf(HealthDomain.EXERCISE), 2,
            setOf("exercise", "training"), setOf("exercise", "training", "workout", "work out", "gym", "trained", "fitness")),

        group("palpitations", TrudyPhraseClass.CARDIOVASCULAR_LANGUAGE,
            setOf(TrudyKnowledgeKind.MEDICAL, TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE),
            setOf(HealthDomain.CLINICAL, HealthDomain.EXERCISE), 19, setOf("palpitations"), setOf(
                "palpitations", "palpatations", "heart racing", "my heart is racing", "heart pounding", "pulse pounding",
                "pulse going mad", "my pulse is going mad", "heart going mad", "heart fluttering", "skipped beats"
            ), fuzzySafe = false, ambiguityBoundary = "palpitations_vs_tachycardia"),
        group("tachycardia", TrudyPhraseClass.CARDIOVASCULAR_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL, HealthDomain.BLOOD_PRESSURE), 18, setOf("tachycardia", "fast heart rate"), setOf(
                "tachycardia", "tachy", "fast heart rate", "high heart rate", "rapid heart rate", "high pulse", "fast pulse"
            ), fuzzySafe = false, ambiguityBoundary = "palpitations_vs_tachycardia"),
        group("heart_rate_general", TrudyPhraseClass.CARDIOVASCULAR_LANGUAGE, performanceKinds(),
            setOf(HealthDomain.EXERCISE, HealthDomain.BLOOD_PRESSURE), 6, setOf("heart rate", "pulse"), setOf(
                "heart rate", "heartrate", "pulse", "bpm", "resting heart rate", "rhr", "hr zone", "heart rate zone"
            )),

        group("gastro_oesophageal_reflux", TrudyPhraseClass.ABDOMINAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL, HealthDomain.NUTRITION, HealthDomain.SLEEP), 20,
            setOf("gastro oesophageal reflux", "acid reflux", "heartburn"), setOf(
                "gord", "gerd", "heartburn", "acid reflux", "acid coming up", "bringing acid up", "reflux",
                "gastro oesophageal reflux", "gastroesophageal reflux"
            ), fuzzySafe = false, ambiguityBoundary = "reflux_vs_indigestion_vs_abdominal_pain"),
        group("indigestion", TrudyPhraseClass.ABDOMINAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL, TrudyKnowledgeKind.NUTRITION),
            setOf(HealthDomain.CLINICAL, HealthDomain.NUTRITION), 16, setOf("indigestion", "dyspepsia"), setOf(
                "indigestion", "dyspepsia", "dyspeptic", "heavy stomach after eating", "uncomfortable after eating"
            ), fuzzySafe = false, ambiguityBoundary = "reflux_vs_indigestion_vs_abdominal_pain"),
        group("abdominal_pain", TrudyPhraseClass.ABDOMINAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL), 18, setOf("abdominal pain", "stomach ache"), setOf(
                "abdominal pain", "stomach pain", "stomach hurts", "my stomach hurts", "tummy hurts", "my tummy hurts",
                "tummy ache", "stomach ache", "belly ache", "belly pain", "stomachs playing up", "stomach playing up",
                "dodgy stomach"
            ), fuzzySafe = false, ambiguityBoundary = "reflux_vs_indigestion_vs_abdominal_pain"),
        group("diarrhoea", TrudyPhraseClass.ABDOMINAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL, HealthDomain.HYDRATION), 18, setOf("diarrhoea", "diarrhea", "loose stools"), setOf(
                "diarrhoea", "diarrhea", "diarhea", "ive got the runs", "got the runs", "the runs", "loose stools",
                "loose bowel movements", "runny poo", "runny poop"
            ), fuzzySafe = false),
        group("constipation", TrudyPhraseClass.ABDOMINAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL, HealthDomain.HYDRATION, HealthDomain.NUTRITION), 18,
            setOf("constipation", "hard stools"), setOf(
                "constipation", "constipated", "cant poo", "cant poop", "cannot poo", "cannot poop", "backed up",
                "hard stools", "struggling to go", "havent been", "have not been"
            ), fuzzySafe = false),
        group("abdominal_bloating", TrudyPhraseClass.ABDOMINAL_LANGUAGE,
            setOf(TrudyKnowledgeKind.MEDICAL, TrudyKnowledgeKind.NUTRITION),
            setOf(HealthDomain.CLINICAL, HealthDomain.NUTRITION), 16, setOf("abdominal bloating", "bloating"), setOf(
                "bloated", "bloating", "really bloated", "stomach bloated", "belly bloated", "abdominal bloating",
                "trapped wind", "gassy", "full of gas"
            ), fuzzySafe = false),
        group("nausea_vomiting", TrudyPhraseClass.ABDOMINAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL, HealthDomain.HYDRATION), 17, setOf("nausea", "vomiting"), setOf(
                "nausea", "nauseous", "feel sick", "being sick", "vomiting", "vomit", "throwing up", "threw up"
            ), fuzzySafe = false),
        group("irritable_bowel_syndrome", TrudyPhraseClass.FORMAL_MEDICAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL, HealthDomain.NUTRITION), 19, setOf("irritable bowel syndrome"), setOf(
                "ibs", "irritable bowel", "irritable bowel syndrome"
            ), fuzzySafe = false),

        group("breathlessness", TrudyPhraseClass.BREATHING_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL), 19, setOf("breathlessness", "dyspnoea", "dyspnea"), setOf(
                "breathless", "breathlessness", "short of breath", "out of breath", "cant catch my breath",
                "cannot catch my breath", "struggling to breathe", "difficulty breathing", "dyspnoea", "dyspnea", "breathles"
            ), fuzzySafe = false),
        group("asthma_wheeze", TrudyPhraseClass.FORMAL_MEDICAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL), 18, setOf("asthma", "wheeze"), setOf(
                "asthma", "asthmatic", "wheeze", "wheezing", "reactive airway"
            ), fuzzySafe = false),

        group("dizziness", TrudyPhraseClass.BALANCE_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL), 17, setOf("dizziness"), setOf("dizzy", "dizziness", "woozy"),
            fuzzySafe = false, ambiguityBoundary = "dizziness_vs_lightheadedness_vs_vertigo"),
        group("lightheadedness", TrudyPhraseClass.BALANCE_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL), 18, setOf("lightheadedness", "feeling faint"), setOf(
                "lightheaded", "light headed", "lightheadedness", "feel faint", "feeling faint", "faintish"
            ), fuzzySafe = false, ambiguityBoundary = "dizziness_vs_lightheadedness_vs_vertigo"),
        group("vertigo", TrudyPhraseClass.BALANCE_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL), 19, setOf("vertigo", "spinning sensation"), setOf(
                "vertigo", "vertgio", "room spinning", "world spinning", "spinning sensation", "everything is spinning"
            ), fuzzySafe = false, ambiguityBoundary = "dizziness_vs_lightheadedness_vs_vertigo"),

        group("hydration_adequacy", TrudyPhraseClass.HYDRATION, setOf(TrudyKnowledgeKind.NUTRITION),
            setOf(HealthDomain.HYDRATION), 15, setOf("hydration", "drinking enough"), setOf(
                "hydration", "hydrated", "dehydrated", "dehydration", "drinking enough", "enough water", "water intake",
                "fluid intake", "enough fluids", "electrolytes", "thirsty", "sweating loads"
            )),
        group("overnight_weight_change", TrudyPhraseClass.BODY_WEIGHT, setOf(TrudyKnowledgeKind.NUTRITION),
            setOf(HealthDomain.BODY), 19, setOf("weight jump overnight", "heavier this morning"), setOf(
                "weight jumped overnight", "weight jump overnight", "weight shot up overnight", "heavier this morning",
                "why am i heavier", "scale jumped", "scale shot up", "gained overnight", "overnight weight gain"
            )),
        group("protein_food", TrudyPhraseClass.NUTRITION_AND_FOOD, setOf(TrudyKnowledgeKind.NUTRITION),
            setOf(HealthDomain.NUTRITION), 12, setOf("protein intake", "protein shake"), setOf(
                "enough protein", "protein intake", "protein target", "protein shake", "chicken breast", "protein powder"
            )),
        group("food_uk_us", TrudyPhraseClass.NUTRITION_AND_FOOD, setOf(TrudyKnowledgeKind.NUTRITION),
            setOf(HealthDomain.NUTRITION), 10, setOf("food", "meal"), setOf(
                "porridge", "oatmeal", "oats", "crisps", "potato chips", "chips", "fries", "french fries",
                "latte", "coffee", "chicken breast", "meal", "food"
            )),
        group("food_energy", TrudyPhraseClass.NUTRITION_AND_FOOD,
            setOf(TrudyKnowledgeKind.NUTRITION, TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE),
            setOf(HealthDomain.NUTRITION, HealthDomain.SLEEP), 16, setOf("food affecting my energy", "tired after eating"), setOf(
                "food affecting my energy", "food affect my energy", "tired after eating", "sleepy after eating",
                "post meal crash", "energy after meals", "meal makes me tired"
            )),
        group("caffeine_exposure", TrudyPhraseClass.NUTRITION_AND_FOOD, setOf(TrudyKnowledgeKind.NUTRITION),
            setOf(HealthDomain.NUTRITION), 9, setOf("caffeine", "coffee"), setOf(
                "caffeine", "coffee", "latte", "energy drink", "pre workout", "caffeine intake", "caffeine dose"
            )),
        group("caffeine_sleep", TrudyPhraseClass.SLEEP,
            setOf(TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE, TrudyKnowledgeKind.NUTRITION),
            setOf(HealthDomain.SLEEP, HealthDomain.NUTRITION), 18, setOf("caffeine and sleep", "coffee and sleep"), setOf(
                "caffeine and sleep", "coffee and sleep", "caffeine affects sleep", "caffeine affect sleep",
                "caffeine affect my sleep", "caffeine affecting sleep", "caffeine affecting my sleep",
                "coffee affects sleep", "coffee affecting sleep", "late coffee",
                "caffeine cutoff", "caffeine cut off", "does caffeine affect my sleep", "does coffee ruin my sleep",
                "pre workout sleep", "energy drink sleep"
            )),

        group("stress_anxiety", TrudyPhraseClass.EMOTIONAL_LANGUAGE, setOf(TrudyKnowledgeKind.EMOTIONAL_WELLBEING),
            setOf(HealthDomain.EMOTIONAL), 15, setOf("stressed", "anxious feelings"), setOf(
                "stressed", "stressed out", "im stressed", "under pressure", "overwhelmed", "anxious", "feeling anxious",
                "anxiety", "on edge", "cant relax", "burnt out", "burned out", "tense"
            )),
        group("hot_sleep_environment", TrudyPhraseClass.ENVIRONMENT, setOf(TrudyKnowledgeKind.ENVIRONMENT),
            setOf(HealthDomain.ENVIRONMENT, HealthDomain.SLEEP), 16, setOf("hot bedroom", "sleeping in heat"), setOf(
                "hot bedroom", "bedroom too hot", "too hot to sleep", "sleeping in heat", "sweaty night", "humid bedroom"
            )),
        group("light_environment", TrudyPhraseClass.ENVIRONMENT, setOf(TrudyKnowledgeKind.ENVIRONMENT),
            setOf(HealthDomain.ENVIRONMENT, HealthDomain.SLEEP), 12, setOf("light exposure", "morning light"), setOf(
                "light exposure", "morning light", "daylight", "sunlight", "bright light", "dark mornings", "body clock light"
            )),
        group("air_environment", TrudyPhraseClass.ENVIRONMENT, setOf(TrudyKnowledgeKind.ENVIRONMENT),
            setOf(HealthDomain.ENVIRONMENT), 12, setOf("air quality", "pollution"), setOf(
                "air quality", "aqi", "pollution", "smog", "pm2 5", "pm10", "bad air"
            )),
        group("weather_environment", TrudyPhraseClass.ENVIRONMENT, setOf(TrudyKnowledgeKind.ENVIRONMENT),
            setOf(HealthDomain.ENVIRONMENT), 5, setOf("weather", "environmental conditions"), setOf(
                "weather", "environmental conditions", "weather affecting me", "heatwave", "humid", "muggy"
            )),

        group("experiment_method", TrudyPhraseClass.EXPERIMENT_LANGUAGE,
            setOf(TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY), emptySet(), 20,
            setOf("personal experiment", "baseline intervention outcome"), setOf(
                "how could i test", "how can i test", "test whether", "test if", "run an experiment", "personal experiment",
                "self experiment", "experiment", "n of 1", "n 1", "hypothesis", "baseline period", "intervention period",
                "compare before and during", "before vs during", "trial this", "confounder", "measurement noise"
            )),

        group("hypertension", TrudyPhraseClass.FORMAL_MEDICAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.BLOOD_PRESSURE, HealthDomain.CLINICAL), 17, setOf("hypertension", "high blood pressure"), setOf(
                "hypertension", "high blood pressure", "raised blood pressure", "high bp", "raised bp"
            ), fuzzySafe = false),
        group("panic_disorder", TrudyPhraseClass.FORMAL_MEDICAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL, HealthDomain.EMOTIONAL), 17, setOf("panic attack", "panic disorder"), setOf(
                "panic attack", "panic attacks", "panic disorder"
            ), fuzzySafe = false),
        group("low_back_pain", TrudyPhraseClass.FORMAL_MEDICAL_LANGUAGE, setOf(TrudyKnowledgeKind.MEDICAL),
            setOf(HealthDomain.CLINICAL, HealthDomain.EXERCISE), 15, setOf("low back pain", "sciatica"), setOf(
                "low back pain", "lower back pain", "sciatica", "slipped disc", "herniated disc", "lumbar radiculopathy"
            ), fuzzySafe = false)
    )

    private fun group(
        semanticId: String,
        phraseClass: TrudyPhraseClass,
        kinds: Set<TrudyKnowledgeKind>,
        domains: Set<HealthDomain>,
        priority: Int,
        canonicalTerms: Set<String>,
        aliases: Set<String>,
        fuzzySafe: Boolean = true,
        ambiguityBoundary: String? = null
    ) = AliasGroup(
        semanticId,
        phraseClass,
        kinds,
        domains,
        priority,
        canonicalTerms.map(::normalize).filter(String::isNotBlank).toSet(),
        aliases,
        fuzzySafe,
        ambiguityBoundary
    )

    private fun performanceKinds() = setOf(TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE)
    private fun sleepDomains() = setOf(HealthDomain.SLEEP)

    private const val EXACT_SCORE = 100
    private const val PHRASE_SCORE = 76
    private const val UNORDERED_SCORE = 52
    private const val SINGLE_TOKEN_SCORE = 58
    private const val FUZZY_SCORE = 34
    private const val TOKEN_WEIGHT = 3
    private const val MIN_FUZZY_LENGTH = 6
    private const val MAX_LEXICAL_SCORE = 160
    private const val MAX_EXPANDED_TEXT_CHARS = 768

    private val PRODUCT_OBJECTS = setOf(
        "dashboard", "dashboard tile", "home tile", "mini tile", "tile", "button", "screen", "navigation", "ui",
        "layout", "app icon", "developer settings", "data vault schema", "bluetooth", "api", "experiments",
        "neural network", "model", "code", "kotlin", "repository", "database", "release note", "file", "compiler", "software"
    )
    private val PRODUCT_OPERATIONS = setOf(
        "rename", "move", "drag", "reorder", "open", "navigate", "take me to", "where is", "design", "implement",
        "build", "remove", "add", "change the", "fix", "wire up", "create", "training", "train", "crash"
    )
    private val STRICT_PRODUCT_PHRASES = setOf(
        "dashboard ui", "user interface", "dashboard tile", "home tile", "mini tile", "developer settings",
        "data vault schema"
    )
}

/** Reuses the coordinator's routing decision when available and never adds personal persistence. */
fun TrudyKnowledgeQuery.searchTextFor(kinds: Set<TrudyKnowledgeKind>, maxTerms: Int = 12): String {
    val plan = routing ?: TrudyLanguageRouter.route(userText, maxTopics = minOf(maxItems, TrudyLanguageRouter.MAX_TOPICS))
    if (plan.productOnlyIntent) return userText
    val terms = plan.canonicalTermsFor(kinds, maxTerms)
    if (terms.isEmpty()) return userText
    val normalizedOriginal = TrudyLanguageRouter.normalize(userText)
    val missing = terms.filterNot { term ->
        " ${TrudyLanguageRouter.normalize(term)} " in " $normalizedOriginal "
    }
    return if (missing.isEmpty()) userText else "$userText ${missing.joinToString(" ")}".take(768)
}

fun TrudyKnowledgeQuery.searchTextFor(kind: TrudyKnowledgeKind, maxTerms: Int = 12): String =
    searchTextFor(setOf(kind), maxTerms)
