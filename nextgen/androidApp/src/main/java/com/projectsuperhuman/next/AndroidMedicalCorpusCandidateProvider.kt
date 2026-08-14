package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.trudy.medical.MedicalCandidateRelevance
import com.projectsuperhuman.next.trudy.medical.MedicalConditionCandidate
import com.projectsuperhuman.next.trudy.medical.MedicalConditionCandidateProvider
import com.projectsuperhuman.next.trudy.medical.MedicalConditionCandidateRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Android adapter for the canonical offline medical corpus.
 *
 * Retrieval is deliberately lexical and non-diagnostic: scores only decide which reference records
 * are useful context for Trudy. They are never probabilities and are never persisted as diagnoses.
 *
 * Corpus parsing and lexical normalization are lazy and cached so cold app startup does not pay the
 * cost of medical indexing unless Trudy actually needs medical retrieval.
 */
internal class AndroidMedicalCorpusCandidateProvider(
    context: Context,
    private val assetName: String = "conditions.v1.json"
) : MedicalConditionCandidateProvider {
    private val appContext = context.applicationContext

    private val records: List<CorpusConditionRecord> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            appContext.assets.open(assetName).bufferedReader().use { reader ->
                parseCorpus(JSONObject(reader.readText()))
            }
        }.getOrElse { emptyList() }
    }

    override suspend fun candidates(request: MedicalConditionCandidateRequest): List<MedicalConditionCandidate> {
        val corpus = records
        if (corpus.isEmpty()) return emptyList()

        val normalized = normalize(request.question)
        val queryTokens = tokensFromNormalized(normalized)
        val explicitlyRecordedIds = request.explicitlyRecordedConditionIds

        val ranked = corpus.mapNotNull { record ->
            val directMatches = record.directTerms.filter { normalized.contains(it) }

            val featureMatches = record.features.mapNotNull { feature ->
                val exact = feature.normalizedTerms.any { it.length >= 3 && normalized.contains(it) }
                val overlap = feature.tokens.count(queryTokens::contains)
                if (exact || overlap >= feature.minimumTokenOverlap) feature else null
            }.distinctBy { it.id }

            val explicitlyRecorded = record.id in explicitlyRecordedIds
            if (directMatches.isEmpty() && featureMatches.isEmpty() && !explicitlyRecorded) return@mapNotNull null

            val score = (
                (if (explicitlyRecorded) 12 else 0) +
                    directMatches.size * 8 +
                    featureMatches.count { it.frequency == "common" } * 4 +
                    featureMatches.count { it.frequency == "possible" } * 2 +
                    featureMatches.count { it.frequency == "uncommon" }
                ).coerceAtMost(30)

            RankedCandidate(record, score, directMatches, featureMatches)
        }.sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.record.displayName })

        return ranked.take(request.maxCandidates).map { rankedCandidate ->
            val record = rankedCandidate.record
            val fit = buildList {
                if (record.id in explicitlyRecordedIds) {
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
            }.ifEmpty { listOf("The record matched terms in the user's question.") }

            val informationThatWouldMatter = buildList {
                record.differentiators.take(2).forEach { add(it) }
                record.investigations.take(2).forEach { add(it) }
                record.redFlags.take(1).forEach { add("Safety context to check: $it") }
            }.distinct().take(5)

            MedicalConditionCandidate(
                conditionId = record.id,
                displayName = record.displayName,
                relevance = when {
                    rankedCandidate.score >= 12 -> MedicalCandidateRelevance.HIGH
                    rankedCandidate.score >= 6 -> MedicalCandidateRelevance.MODERATE
                    else -> MedicalCandidateRelevance.LOW
                },
                reasonsForFit = fit.distinct().take(6),
                reasonsAgainstFit = emptyList(),
                informationThatWouldMatter = informationThatWouldMatter,
                sourceIds = record.sourceIds
            )
        }
    }

    private fun parseCorpus(root: JSONObject): List<CorpusConditionRecord> {
        val conditions = root.optJSONArray("conditions") ?: return emptyList()
        return buildList {
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

                add(
                    CorpusConditionRecord(
                        id = id,
                        displayName = displayName,
                        aliases = aliases,
                        directTerms = directTerms,
                        features = parseFeatures(item.optJSONObject("features")),
                        differentiators = item.optJSONArray("differentiating_features").nestedSecondStrings(),
                        investigations = item.optJSONArray("common_investigations").nestedFirstStrings(),
                        redFlags = item.optJSONArray("red_flags").nestedThirdStrings(),
                        sourceIds = item.optJSONArray("source_refs").strings()
                    )
                )
            }
        }
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
                    val featureTokens = normalizedTerms.flatMap(::tokensFromNormalized).toSet()
                    val minimumOverlap = if (featureTokens.size <= 1) 1 else 2

                    add(
                        CorpusFeature(
                            id = id,
                            label = label,
                            frequency = frequency,
                            context = row.optString(2).takeIf(String::isNotBlank),
                            normalizedTerms = normalizedTerms,
                            tokens = featureTokens,
                            minimumTokenOverlap = minimumOverlap
                        )
                    )
                }
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()

    private fun tokensFromNormalized(value: String): Set<String> = value
        .split(' ')
        .asSequence()
        .filter { it.length >= 4 && it !in STOP_WORDS }
        .toSet()

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun JSONArray?.nestedFirstStrings(): List<String> = nestedStrings(0)
    private fun JSONArray?.nestedSecondStrings(): List<String> = nestedStrings(1)
    private fun JSONArray?.nestedThirdStrings(): List<String> = nestedStrings(2)

    private fun JSONArray?.nestedStrings(position: Int): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) {
            optJSONArray(index)?.optString(position)?.takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private data class CorpusConditionRecord(
        val id: String,
        val displayName: String,
        val aliases: List<String>,
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
        val featureMatches: List<CorpusFeature>
    )

    private companion object {
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        val STOP_WORDS = setOf(
            "have", "with", "that", "this", "from", "what", "could", "would", "about", "been",
            "does", "feel", "feeling", "when", "your", "some", "more", "very", "also", "like"
        )
    }
}
