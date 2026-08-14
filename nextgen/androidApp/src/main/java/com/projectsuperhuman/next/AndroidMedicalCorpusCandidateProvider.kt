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
 */
internal class AndroidMedicalCorpusCandidateProvider(
    context: Context,
    assetName: String = "conditions.v1.json"
) : MedicalConditionCandidateProvider {
    private val records: List<CorpusConditionRecord> = runCatching {
        context.applicationContext.assets.open(assetName).bufferedReader().use { reader ->
            parseCorpus(JSONObject(reader.readText()))
        }
    }.getOrElse { emptyList() }

    override suspend fun candidates(request: MedicalConditionCandidateRequest): List<MedicalConditionCandidate> {
        if (records.isEmpty()) return emptyList()
        val normalized = normalize(request.question)
        val queryTokens = tokens(normalized)

        val ranked = records.mapNotNull { record ->
            val directTerms = (record.aliases + record.displayName + record.id.replace('_', ' '))
                .map(::normalize)
                .filter { it.length >= 3 }
            val directMatches = directTerms.filter { normalized.contains(it) }

            val featureMatches = record.features.mapNotNull { feature ->
                val featureTerms = listOf(feature.id.replace('_', ' '), feature.label).map(::normalize)
                val exact = featureTerms.any { it.length >= 3 && normalized.contains(it) }
                val overlap = featureTerms.flatMap(::tokens).toSet().intersect(queryTokens)
                if (exact || overlap.size >= feature.minimumTokenOverlap()) feature else null
            }.distinctBy { it.id }

            val explicitlyRecorded = record.id in request.explicitlyRecordedConditionIds
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
                if (record.id in request.explicitlyRecordedConditionIds) {
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
                add(
                    CorpusConditionRecord(
                        id = id,
                        displayName = displayName,
                        aliases = item.optJSONArray("aliases").strings(),
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
                    add(CorpusFeature(id, label, frequency, row.optString(2).takeIf(String::isNotBlank)))
                }
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun tokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .asSequence()
        .filter { it.length >= 4 && it !in STOP_WORDS }
        .toSet()

    private fun CorpusFeature.minimumTokenOverlap(): Int =
        if (tokens(label).size <= 1 || tokens(id.replace('_', ' ')).size <= 1) 1 else 2

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
        val context: String?
    )

    private data class RankedCandidate(
        val record: CorpusConditionRecord,
        val score: Int,
        val directMatches: List<String>,
        val featureMatches: List<CorpusFeature>
    )

    private companion object {
        val STOP_WORDS = setOf(
            "have", "with", "that", "this", "from", "what", "could", "would", "about", "been",
            "does", "feel", "feeling", "when", "your", "some", "more", "very", "also", "like"
        )
    }
}
