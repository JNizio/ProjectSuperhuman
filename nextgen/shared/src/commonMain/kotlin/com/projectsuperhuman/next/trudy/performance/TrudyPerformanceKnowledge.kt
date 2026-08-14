package com.projectsuperhuman.next.trudy.performance

/**
 * Planner-facing, deterministic retrieval facade.
 *
 * Integration contract:
 *  1. call [resolve] with the user's message;
 *  2. use [PerformanceKnowledgeBundle.metricBindings] to request bounded canonical evidence;
 *  3. use claims as explanatory knowledge, never as personal observations;
 *  4. retain the source and safety metadata when synthesising a response.
 *
 * The facade does not access storage, perform statistics, or generate a medical conclusion.
 */
class TrudyPerformanceKnowledge(
    topics: List<PerformanceTopic> = PerformanceKnowledgeCatalog.topics,
    claims: List<PerformanceKnowledgeClaim> = PerformanceKnowledgeCatalog.claims,
    sources: List<PerformanceEvidenceSource> = PerformanceEvidenceCatalog.sources,
    phraseGroups: List<PerformancePhraseGroup> = PerformanceIntentLexicon.groups,
    blueprints: List<PerformanceExperimentBlueprint> = PerformanceExperimentMethodology.blueprints
) {
    private val topicsById = topics.associateBy { it.id }
    private val claimsById = claims.associateBy { it.id }
    private val sourcesById = sources.associateBy { it.id }
    private val experimentBlueprints = blueprints
    private val aliasRows: List<AliasRow> = phraseGroups.flatMap { group ->
        group.aliases.mapNotNull { alias ->
            val normalized = PerformanceTextNormalizer.normalize(alias)
            if (normalized.isBlank()) null else AliasRow(group, alias, normalized, normalized.split(' '))
        }
    }

    init {
        require(topicsById.size == topics.size)
        require(claimsById.size == claims.size)
        require(sourcesById.size == sources.size)
        require(phraseGroups.flatMap { it.topicIds }.all { it in topicsById })
        require(topics.flatMap { it.claimIds }.all { it in claimsById })
        require(claims.flatMap { it.evidenceSourceIds }.all { it in sourcesById })
    }

    fun resolve(message: String, maxTopics: Int = DEFAULT_MAX_TOPICS): PerformanceKnowledgeBundle {
        require(maxTopics in 1..MAX_TOPICS)
        val normalized = PerformanceTextNormalizer.normalize(message)
        if (normalized.isBlank()) return emptyBundle(normalized)
        val messageTokens = normalized.split(' ')
        val tokenSet = messageTokens.toSet()
        val accumulators = mutableMapOf<String, MatchAccumulator>()

        aliasRows.forEach { row ->
            val score = score(row, normalized, messageTokens, tokenSet)
            if (score <= 0) return@forEach
            row.group.topicIds.forEach { topicId ->
                val accumulator = accumulators.getOrPut(topicId, ::MatchAccumulator)
                accumulator.groupScores[row.group.id] = maxOf(accumulator.groupScores[row.group.id] ?: 0, score)
                accumulator.groupIds += row.group.id
                accumulator.aliases += row.originalAlias
            }
        }

        val matches = accumulators.mapNotNull { (topicId, accumulator) ->
            val topic = topicsById[topicId] ?: return@mapNotNull null
            PerformanceIntentTopicMatch(
                topic = topic,
                score = accumulator.groupScores.values.sum(),
                matchedAliasGroups = accumulator.groupIds,
                matchedAliases = accumulator.aliases
            )
        }.sortedWith(
            compareByDescending<PerformanceIntentTopicMatch> { it.score }
                .thenBy { it.topic.id }
        ).take(maxTopics)

        if (matches.isEmpty()) return emptyBundle(normalized)
        val matchedTopics = matches.map { it.topic }
        val relevantClaims = matchedTopics.flatMap { topic -> topic.claimIds.mapNotNull(claimsById::get) }
            .distinctBy { it.id }
        val relevantSources = relevantClaims.flatMap { claim -> claim.evidenceSourceIds.mapNotNull(sourcesById::get) }
            .distinctBy { it.id }
        val metrics = matchedTopics.flatMap { it.metricBindings }
            .distinctBy { listOf(it.domain.name, it.metricId, it.role.name) }
        val topicIds = matchedTopics.map { it.id }.toSet()
        val experiments = experimentBlueprints.filter { blueprint -> blueprint.topicIds.any { it in topicIds } }
        val safety = DEFAULT_SAFETY + relevantClaims.flatMap { it.safetyBoundaries }

        return PerformanceKnowledgeBundle(
            normalizedMessage = normalized,
            matches = matches,
            claims = relevantClaims,
            sources = relevantSources,
            metricBindings = metrics,
            experimentBlueprints = experiments,
            globalSafetyBoundaries = safety.toSet()
        )
    }

    fun topic(topicId: String): PerformanceTopic? = topicsById[topicId]

    fun knowledgeForTopic(topicId: String): PerformanceKnowledgeBundle {
        val topic = topicsById[topicId] ?: return emptyBundle(topicId)
        val relevantClaims = topic.claimIds.mapNotNull(claimsById::get)
        val relevantSources = relevantClaims.flatMap { it.evidenceSourceIds.mapNotNull(sourcesById::get) }
            .distinctBy { it.id }
        return PerformanceKnowledgeBundle(
            normalizedMessage = topicId,
            matches = listOf(PerformanceIntentTopicMatch(topic, DIRECT_TOPIC_SCORE, setOf("direct_topic"), setOf(topicId))),
            claims = relevantClaims,
            sources = relevantSources,
            metricBindings = topic.metricBindings,
            experimentBlueprints = experimentBlueprints.filter { topicId in it.topicIds },
            globalSafetyBoundaries = (DEFAULT_SAFETY + relevantClaims.flatMap { it.safetyBoundaries }).toSet()
        )
    }

    private fun score(
        row: AliasRow,
        normalizedMessage: String,
        messageTokens: List<String>,
        messageTokenSet: Set<String>
    ): Int = when {
        normalizedMessage == row.normalizedAlias -> EXACT_SCORE + row.tokens.size * TOKEN_WEIGHT + row.group.priority
        messageTokens.containsSequence(row.tokens) -> PHRASE_SCORE + row.tokens.size * TOKEN_WEIGHT + row.group.priority
        row.tokens.size >= MIN_UNORDERED_TOKENS && row.tokens.toSet().all { it in messageTokenSet } ->
            UNORDERED_SCORE + row.tokens.size * TOKEN_WEIGHT + row.group.priority
        else -> 0
    }

    private fun emptyBundle(normalized: String) = PerformanceKnowledgeBundle(
        normalizedMessage = normalized,
        matches = emptyList(),
        claims = emptyList(),
        sources = emptyList(),
        metricBindings = emptyList(),
        experimentBlueprints = emptyList(),
        globalSafetyBoundaries = DEFAULT_SAFETY
    )

    private data class AliasRow(
        val group: PerformancePhraseGroup,
        val originalAlias: String,
        val normalizedAlias: String,
        val tokens: List<String>
    )

    private data class MatchAccumulator(
        val groupScores: MutableMap<String, Int> = mutableMapOf(),
        val groupIds: MutableSet<String> = mutableSetOf(),
        val aliases: MutableSet<String> = mutableSetOf()
    )

    private fun List<String>.containsSequence(needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        for (start in 0..size - needle.size) {
            var matches = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    private companion object {
        const val DEFAULT_MAX_TOPICS = 8
        const val MAX_TOPICS = 16
        const val EXACT_SCORE = 80
        const val PHRASE_SCORE = 40
        const val UNORDERED_SCORE = 16
        const val TOKEN_WEIGHT = 3
        const val MIN_UNORDERED_TOKENS = 2
        const val DIRECT_TOPIC_SCORE = 100
        val DEFAULT_SAFETY = setOf(
            PerformanceSafetyBoundary.NON_DIAGNOSTIC,
            PerformanceSafetyBoundary.PERSONAL_ASSOCIATION_NOT_CAUSATION
        )
    }
}
