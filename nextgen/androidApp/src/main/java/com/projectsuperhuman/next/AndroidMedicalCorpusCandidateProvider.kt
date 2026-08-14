package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.trudy.TrudyLanguageRouter
import com.projectsuperhuman.next.trudy.medical.MedicalCandidateRelevance
import com.projectsuperhuman.next.trudy.medical.MedicalConditionCandidate
import com.projectsuperhuman.next.trudy.medical.MedicalConditionCandidateProvider
import com.projectsuperhuman.next.trudy.medical.MedicalConditionCandidateRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Offline, non-diagnostic retrieval over the canonical medical corpus.
 *
 * The build-generated lexical index converts query n-grams and tokens into a bounded set of
 * condition IDs. Lay-language aliases are expanded to a bounded canonical query before that same
 * posting lookup. Only retrieved records are scored, so a turn never scans the complete corpus.
 * Parsing, normalization and fallback index construction happen at most once, on first use.
 */
internal class AndroidMedicalCorpusCandidateProvider(
    context: Context,
    private val conditionAssetName: String = "conditions.v1.json",
    private val lexicalAssetName: String = "medical-lexical-index.v1.json"
) : MedicalConditionCandidateProvider {
    private val appContext = context.applicationContext

    private val corpus: CorpusIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            val records = appContext.assets.open(conditionAssetName).bufferedReader().use { reader ->
                parseCorpus(JSONObject(reader.readText()))
            }
            val generated = runCatching {
                appContext.assets.open(lexicalAssetName).bufferedReader().use { reader ->
                    parseLexicalIndex(JSONObject(reader.readText()), records)
                }
            }.getOrNull()
            generated?.takeIf { it.recordsById.isNotEmpty() } ?: buildFallbackIndex(records)
        }.getOrElse { CorpusIndex.EMPTY }
    }

    override suspend fun candidates(request: MedicalConditionCandidateRequest): List<MedicalConditionCandidate> {
        if (request.maxCandidates <= 0 || corpus.recordsById.isEmpty()) return emptyList()

        val normalized = normalize(TrudyLanguageRouter.expandMedicalSearchText(request.question))
        val phrases = ngrams(normalized)
        val queryTokens = tokensFromNormalized(normalized)
        val retrievalScores = mutableMapOf<String, Int>()

        fun add(ids: List<String>?, weight: Int) {
            ids.orEmpty().take(MAX_POSTING_USE).forEach { id ->
                retrievalScores[id] = (retrievalScores[id] ?: 0) + weight
            }
        }

        phrases.forEach { phrase ->
            add(corpus.conditionPhrasePostings[phrase], DIRECT_PHRASE_WEIGHT)
            corpus.symptomPhrasePostings[phrase].orEmpty().take(MAX_SYMPTOMS_PER_QUERY_TERM).forEach { symptomId ->
                add(corpus.symptomConditionPostings[symptomId], SYMPTOM_PHRASE_WEIGHT)
            }
        }
        queryTokens.forEach { token ->
            add(corpus.conditionTokenPostings[token], CONDITION_TOKEN_WEIGHT)
            corpus.symptomTokenPostings[token].orEmpty().take(MAX_SYMPTOMS_PER_QUERY_TERM).forEach { symptomId ->
                add(corpus.symptomConditionPostings[symptomId], SYMPTOM_TOKEN_WEIGHT)
            }
        }
        request.explicitlyRecordedConditionIds.forEach { id ->
            if (id in corpus.recordsById) retrievalScores[id] = (retrievalScores[id] ?: 0) + RECORDED_WEIGHT
        }
        if (retrievalScores.isEmpty()) return emptyList()

        val candidateLimit = maxOf(MIN_RETRIEVAL_CANDIDATES, request.maxCandidates * CANDIDATE_MULTIPLIER)
            .coerceAtMost(MAX_RUNTIME_CANDIDATES)
        val selectedIds = retrievalScores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(candidateLimit)
            .map { it.key }

        val ranked = selectedIds.mapNotNull { id ->
            val record = corpus.recordsById[id] ?: return@mapNotNull null
            val directMatches = record.directTerms.filter(phrases::contains)
            val featureMatches = record.features.filter { feature ->
                feature.normalizedTerms.any(phrases::contains) ||
                    feature.tokens.count(queryTokens::contains) >= feature.minimumTokenOverlap
            }.distinctBy(CorpusFeature::id)
            val explicitlyRecorded = id in request.explicitlyRecordedConditionIds
            val featureScore = featureMatches.sumOf {
                when (it.frequency) {
                    "common" -> 4
                    "possible" -> 2
                    else -> 1
                }
            }
            RankedCandidate(
                record = record,
                score = (retrievalScores[id] ?: 0) + directMatches.size * 4 + featureScore,
                directMatches = directMatches,
                featureMatches = featureMatches,
                explicitlyRecorded = explicitlyRecorded
            )
        }.sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.record.displayName })

        return ranked.take(request.maxCandidates).map { rankedCandidate ->
            val record = rankedCandidate.record
            val fit = buildList {
                if (rankedCandidate.explicitlyRecorded) {
                    add("This condition is explicitly recorded in the user's health context.")
                }
                rankedCandidate.directMatches.take(2).forEach { term ->
                    add("The question directly mentions ${term.replaceFirstChar { it.uppercase() }}.")
                }
                rankedCandidate.featureMatches.take(4).forEach { feature ->
                    val qualifier = when (feature.frequency) {
                        "common" -> "a common feature"
                        "possible" -> "a possible feature"
                        else -> "a less common feature"
                    }
                    add("${feature.label} is $qualifier in the reference record${feature.context?.let { "; $it" } ?: ""}.")
                }
                if (isEmpty()) add("Indexed terms in the question retrieved this educational record.")
            }.distinct().take(6)

            val informationThatWouldMatter = buildList {
                record.differentiators.take(2).forEach { add(it) }
                record.investigations.take(2).forEach { add(it) }
                record.redFlags.take(1).forEach { add("Safety context to check: $it") }
            }.distinct().take(5)

            MedicalConditionCandidate(
                conditionId = record.id,
                displayName = record.displayName,
                relevance = when {
                    rankedCandidate.score >= 16 -> MedicalCandidateRelevance.HIGH
                    rankedCandidate.score >= 8 -> MedicalCandidateRelevance.MODERATE
                    else -> MedicalCandidateRelevance.LOW
                },
                reasonsForFit = fit,
                reasonsAgainstFit = emptyList(),
                informationThatWouldMatter = informationThatWouldMatter,
                sourceIds = record.sourceIds
            )
        }
    }

    private fun parseCorpus(root: JSONObject): Map<String, CorpusConditionRecord> {
        val conditions = root.optJSONArray("conditions") ?: return emptyMap()
        return buildMap {
            for (index in 0 until conditions.length()) {
                val item = conditions.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val displayName = item.optString("display_name")
                if (id.isBlank() || displayName.isBlank()) continue
                val aliases = item.optJSONArray("aliases").strings()
                val directTerms = (aliases + displayName + id.replace('_', ' '))
                    .map(::normalize)
                    .filter { it.length >= 3 }
                    .distinct()
                put(
                    id,
                    CorpusConditionRecord(
                        id = id,
                        displayName = displayName,
                        directTerms = directTerms,
                        features = parseFeatures(item.optJSONObject("features")),
                        differentiators = item.optJSONArray("differentiating_features").nestedStrings(1),
                        investigations = item.optJSONArray("common_investigations").nestedStrings(0),
                        redFlags = item.optJSONArray("red_flags").nestedStrings(2),
                        sourceIds = item.optJSONArray("source_refs").strings()
                    )
                )
            }
        }
    }

    private fun parseLexicalIndex(
        root: JSONObject,
        records: Map<String, CorpusConditionRecord>
    ): CorpusIndex {
        return CorpusIndex(
            recordsById = records,
            conditionPhrasePostings = root.optJSONObject("condition_phrase_postings").stringListMap(),
            symptomPhrasePostings = root.optJSONObject("symptom_phrase_postings").stringListMap(),
            conditionTokenPostings = root.optJSONObject("condition_token_postings").stringListMap(),
            symptomTokenPostings = root.optJSONObject("symptom_token_postings").stringListMap(),
            symptomConditionPostings = root.optJSONObject("symptom_condition_postings").stringListMap()
        )
    }

    /** Compatibility fallback for an older asset bundle; still computed once and never per turn. */
    private fun buildFallbackIndex(records: Map<String, CorpusConditionRecord>): CorpusIndex {
        val conditionPhrases = mutableMapOf<String, MutableSet<String>>()
        val conditionTokens = mutableMapOf<String, MutableSet<String>>()
        val symptomPhrases = mutableMapOf<String, MutableSet<String>>()
        val symptomTokens = mutableMapOf<String, MutableSet<String>>()
        val symptomConditions = mutableMapOf<String, MutableSet<String>>()

        fun MutableMap<String, MutableSet<String>>.add(key: String, value: String) {
            if (key.isNotBlank()) getOrPut(key) { linkedSetOf() }.add(value)
        }

        records.values.forEach { record ->
            record.directTerms.forEach { term ->
                conditionPhrases.add(term, record.id)
                tokensFromNormalized(term).forEach { token -> conditionTokens.add(token, record.id) }
            }
            record.features.forEach { feature ->
                symptomConditions.add(feature.id, record.id)
                feature.normalizedTerms.forEach { term ->
                    symptomPhrases.add(term, feature.id)
                    tokensFromNormalized(term).forEach { token -> symptomTokens.add(token, feature.id) }
                }
            }
        }

        fun freeze(source: Map<String, Set<String>>): Map<String, List<String>> =
            source.mapValues { (_, ids) -> ids.sorted().take(MAX_POSTING_USE) }

        return CorpusIndex(
            recordsById = records,
            conditionPhrasePostings = freeze(conditionPhrases),
            symptomPhrasePostings = freeze(symptomPhrases),
            conditionTokenPostings = freeze(conditionTokens),
            symptomTokenPostings = freeze(symptomTokens),
            symptomConditionPostings = freeze(symptomConditions)
        )
    }

    private fun parseFeatures(features: JSONObject?): List<CorpusFeature> {
        if (features == null) return emptyList()
        return buildList {
            listOf("common", "possible", "uncommon").forEach { frequency ->
                val rows = features.optJSONArray(frequency) ?: return@forEach
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONArray(index) ?: continue
                    val id = row.optString(0)
                    val label = row.optString(1)
                    if (id.isBlank() || label.isBlank()) continue
                    val normalizedTerms = listOf(id.replace('_', ' '), label)
                        .map(::normalize)
                        .filter(String::isNotBlank)
                        .distinct()
                    val tokens = normalizedTerms.flatMap(::tokensFromNormalized).toSet()
                    add(
                        CorpusFeature(
                            id = id,
                            label = label,
                            frequency = frequency,
                            context = row.optString(2).takeIf(String::isNotBlank),
                            normalizedTerms = normalizedTerms,
                            tokens = tokens,
                            minimumTokenOverlap = if (tokens.size <= 1) 1 else 2
                        )
                    )
                }
            }
        }
    }

    private fun ngrams(normalized: String): Set<String> {
        val words = normalized.split(' ').filter(String::isNotBlank).take(MAX_QUERY_WORDS)
        return buildSet {
            for (start in words.indices) {
                for (size in 1..minOf(MAX_NGRAM_WORDS, words.size - start)) {
                    add(words.subList(start, start + size).joinToString(" "))
                }
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(REPEATED_SPACE, " ")

    private fun tokensFromNormalized(value: String): Set<String> = value
        .split(' ')
        .asSequence()
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .toSet()

    private fun JSONObject?.stringListMap(): Map<String, List<String>> {
        if (this == null) return emptyMap()
        return buildMap {
            val names = keys()
            while (names.hasNext()) {
                val name = names.next()
                put(name, optJSONArray(name).strings())
            }
        }
    }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun JSONArray?.nestedStrings(position: Int): List<String> =
        if (this == null) emptyList() else buildList {
            for (index in 0 until length()) {
                optJSONArray(index)?.optString(position)?.takeIf(String::isNotBlank)?.let(::add)
            }
        }

    private data class CorpusIndex(
        val recordsById: Map<String, CorpusConditionRecord>,
        val conditionPhrasePostings: Map<String, List<String>>,
        val symptomPhrasePostings: Map<String, List<String>>,
        val conditionTokenPostings: Map<String, List<String>>,
        val symptomTokenPostings: Map<String, List<String>>,
        val symptomConditionPostings: Map<String, List<String>>
    ) {
        companion object {
            val EMPTY = CorpusIndex(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
        }
    }

    private data class CorpusConditionRecord(
        val id: String,
        val displayName: String,
        val directTerms: List<String>,
        val features: List<CorpusFeature>,
        val differentiators: List<String>,
        val investigations: List<String>,
        val redFlags: List<String>,
        val sourceIds: List<String>
    )

    private data class CorpusFeature(
        val id: String,
        val label: String,
        val frequency: String,
        val context: String?,
        val normalizedTerms: List<String>,
        val tokens: Set<String>,
        val minimumTokenOverlap: Int
    )

    private data class RankedCandidate(
        val record: CorpusConditionRecord,
        val score: Int,
        val directMatches: List<String>,
        val featureMatches: List<CorpusFeature>,
        val explicitlyRecorded: Boolean
    )

    private companion object {
        const val DIRECT_PHRASE_WEIGHT = 12
        const val SYMPTOM_PHRASE_WEIGHT = 6
        const val CONDITION_TOKEN_WEIGHT = 2
        const val SYMPTOM_TOKEN_WEIGHT = 1
        const val RECORDED_WEIGHT = 20
        const val CANDIDATE_MULTIPLIER = 12
        const val MIN_RETRIEVAL_CANDIDATES = 48
        const val MAX_RUNTIME_CANDIDATES = 128
        const val MAX_POSTING_USE = 64
        const val MAX_SYMPTOMS_PER_QUERY_TERM = 16
        const val MAX_NGRAM_WORDS = 6
        const val MAX_QUERY_WORDS = 48
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        val REPEATED_SPACE = Regex("\\s+")
        val STOP_WORDS = setOf(
            "have", "with", "that", "this", "from", "what", "could", "would", "about", "been",
            "does", "feel", "feeling", "when", "your", "some", "more", "very", "also", "like",
            "pain", "disease", "syndrome", "disorder", "acute", "chronic"
        )
    }
}
