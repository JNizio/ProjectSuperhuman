package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.trudy.TrudyCanonicalExperiment
import com.projectsuperhuman.next.trudy.TrudyCanonicalExperimentRepository
import com.projectsuperhuman.next.trudy.TrudyCanonicalExperimentStatus
import com.projectsuperhuman.next.trudy.TrudyConfidence
import com.projectsuperhuman.next.trudy.TrudyEvidenceKind
import com.projectsuperhuman.next.trudy.TrudyEvidenceReference
import com.projectsuperhuman.next.trudy.TrudyExperimentPersistenceState
import com.projectsuperhuman.next.trudy.TrudyTimeRange

/**
 * Canonical persistence for Cardio-focused n-of-1 protocols.
 *
 * Protocols live in the Data Vault alongside the evidence they reference; the preview-only
 * Experiments UI is intentionally not used as a source of truth.
 */
internal class DataVaultCardioExperimentRepository : TrudyCanonicalExperimentRepository {
    private val data = NativeDomainData.forDomain(HealthDomain.EXERCISE)

    override val persistenceState: TrudyExperimentPersistenceState =
        TrudyExperimentPersistenceState.AVAILABLE

    override suspend fun list(status: TrudyCanonicalExperimentStatus): List<TrudyCanonicalExperiment> =
        data.metricHistory(CARDIO_EXPERIMENT_METRIC, MAX_EXPERIMENT_ROWS, 0)
            .mapNotNull(::cardioExperimentFromValue)
            .distinctBy { it.id }
            .filter { status == TrudyCanonicalExperimentStatus.ANY || it.status == status }
            .sortedByDescending { it.updatedEpochMs }

    override suspend fun get(id: String): TrudyCanonicalExperiment? =
        list(TrudyCanonicalExperimentStatus.ANY).firstOrNull { it.id == id }

    suspend fun upsert(experiment: TrudyCanonicalExperiment): CardioWriteResult {
        require(experiment.targetDomain == HealthDomain.EXERCISE) {
            "Cardio experiment outcomes must use the EXERCISE domain."
        }
        return try {
            val result = NativeDataHub.ingestValues(listOf(experiment.toCardioExperimentValue()))
            CardioWriteResult(
                success = result.rejected == 0,
                message = if (result.rejected == 0) "Cardio experiment persisted"
                else "Cardio experiment was not accepted by the Data Vault",
                accepted = result.accepted,
                rejected = result.rejected,
                deduplicated = result.deduplicated
            )
        } catch (t: Throwable) {
            CardioWriteResult(false, t.message ?: "Cardio experiment persistence failed")
        }
    }

    suspend fun delete(id: String): Boolean {
        val row = data.metricHistory(CARDIO_EXPERIMENT_METRIC, MAX_EXPERIMENT_ROWS, 0)
            .firstOrNull { it.metadata["experimentId"] == id } ?: return false
        return try {
            NativeDataHub.deleteValue(row)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val CARDIO_EXPERIMENT_METRIC = "cardio_experiment_protocol"
        const val MAX_EXPERIMENT_ROWS = 1_000
    }
}

internal fun TrudyCanonicalExperiment.toCardioExperimentValue(): HealthValue {
    val meta = mutableMapOf(
        "sourceRecordId" to "cardio-experiment:$id",
        "experimentId" to id,
        "title" to title,
        "hypothesis" to hypothesis,
        "intervention" to intervention,
        "status" to status.name,
        "targetDomain" to targetDomain.name,
        "targetMetricId" to targetMetricId,
        "baselineFromEpochMs" to baselineWindow.fromEpochMs.toString(),
        "baselineToEpochMs" to baselineWindow.toEpochMs.toString(),
        "interventionFromEpochMs" to interventionWindow.fromEpochMs.toString(),
        "interventionToEpochMs" to interventionWindow.toEpochMs.toString(),
        "analysisMethod" to analysisMethod,
        "updatedEpochMs" to updatedEpochMs.toString(),
        "recordType" to "cardio_nof1_experiment"
    )
    comparator?.let { meta["comparator"] = it }
    adherenceFraction?.let { meta["adherenceFraction"] = it.toString() }
    confidence?.let { meta["confidence"] = it.name }
    result?.let { meta["result"] = it }

    meta.putStringList("inclusionRule", inclusionRules)
    meta.putStringList("confounder", confounders)
    meta.putStringList("observation", observations)
    meta.putStringList("caveat", caveats)
    meta["secondaryMetricCount"] = secondaryMetrics.size.toString()
    secondaryMetrics.forEachIndexed { index, (domain, metric) ->
        meta["secondaryMetric.$index.domain"] = domain.name
        meta["secondaryMetric.$index.metric"] = metric
    }
    meta["evidenceReferenceCount"] = evidenceReferences.size.toString()
    evidenceReferences.forEachIndexed { index, reference ->
        meta["evidenceReference.$index.domain"] = reference.domain.name
        reference.metricId?.let { meta["evidenceReference.$index.metricId"] = it }
        reference.insightId?.let { meta["evidenceReference.$index.insightId"] = it }
        meta["evidenceReference.$index.kind"] = reference.evidenceKind.name
        reference.timestampEpochMs?.let { meta["evidenceReference.$index.timestampEpochMs"] = it.toString() }
        reference.range?.let {
            meta["evidenceReference.$index.rangeFrom"] = it.fromEpochMs.toString()
            meta["evidenceReference.$index.rangeTo"] = it.toEpochMs.toString()
        }
    }

    return HealthValue(
        domain = HealthDomain.EXERCISE,
        metric = "cardio_experiment_protocol",
        value = 1.0,
        unit = "protocol",
        timestampEpochMs = updatedEpochMs,
        source = source.ifBlank { "cardio-experiment" },
        metadata = meta
    )
}

internal fun cardioExperimentFromValue(row: HealthValue): TrudyCanonicalExperiment? {
    if (row.domain != HealthDomain.EXERCISE || row.metric != "cardio_experiment_protocol") return null
    val meta = row.metadata
    val id = meta["experimentId"]?.takeIf { it.isNotBlank() } ?: return null
    val status = meta["status"]?.let { raw ->
        TrudyCanonicalExperimentStatus.entries.firstOrNull { it.name == raw }
    } ?: return null
    val targetDomain = meta["targetDomain"]?.let { raw ->
        HealthDomain.entries.firstOrNull { it.name == raw }
    } ?: return null
    val targetMetric = meta["targetMetricId"]?.takeIf { it.isNotBlank() } ?: return null
    val baselineFrom = meta["baselineFromEpochMs"]?.toLongOrNull() ?: return null
    val baselineTo = meta["baselineToEpochMs"]?.toLongOrNull() ?: return null
    val interventionFrom = meta["interventionFromEpochMs"]?.toLongOrNull() ?: return null
    val interventionTo = meta["interventionToEpochMs"]?.toLongOrNull() ?: return null

    val secondary = (0 until (meta["secondaryMetricCount"]?.toIntOrNull() ?: 0)).mapNotNull { index ->
        val domain = meta["secondaryMetric.$index.domain"]?.let { raw ->
            HealthDomain.entries.firstOrNull { it.name == raw }
        } ?: return@mapNotNull null
        val metric = meta["secondaryMetric.$index.metric"] ?: return@mapNotNull null
        domain to metric
    }
    val evidence = (0 until (meta["evidenceReferenceCount"]?.toIntOrNull() ?: 0)).mapNotNull { index ->
        val domain = meta["evidenceReference.$index.domain"]?.let { raw ->
            HealthDomain.entries.firstOrNull { it.name == raw }
        } ?: return@mapNotNull null
        val kind = meta["evidenceReference.$index.kind"]?.let { raw ->
            TrudyEvidenceKind.entries.firstOrNull { it.name == raw }
        } ?: return@mapNotNull null
        val from = meta["evidenceReference.$index.rangeFrom"]?.toLongOrNull()
        val to = meta["evidenceReference.$index.rangeTo"]?.toLongOrNull()
        TrudyEvidenceReference(
            domain = domain,
            metricId = meta["evidenceReference.$index.metricId"],
            insightId = meta["evidenceReference.$index.insightId"],
            evidenceKind = kind,
            timestampEpochMs = meta["evidenceReference.$index.timestampEpochMs"]?.toLongOrNull(),
            range = if (from != null && to != null && from <= to) TrudyTimeRange(from, to) else null
        )
    }

    return TrudyCanonicalExperiment(
        id = id,
        title = meta["title"].orEmpty().ifBlank { id },
        hypothesis = meta["hypothesis"].orEmpty(),
        intervention = meta["intervention"].orEmpty(),
        status = status,
        targetDomain = targetDomain,
        targetMetricId = targetMetric,
        secondaryMetrics = secondary,
        baselineWindow = TrudyTimeRange(baselineFrom, baselineTo),
        interventionWindow = TrudyTimeRange(interventionFrom, interventionTo),
        adherenceFraction = meta["adherenceFraction"]?.toDoubleOrNull(),
        source = row.source,
        updatedEpochMs = meta["updatedEpochMs"]?.toLongOrNull() ?: row.timestampEpochMs,
        comparator = meta["comparator"],
        inclusionRules = meta.stringList("inclusionRule"),
        confounders = meta.stringList("confounder"),
        observations = meta.stringList("observation"),
        analysisMethod = meta["analysisMethod"].orEmpty().ifBlank { "before_after_personal_comparison" },
        confidence = meta["confidence"]?.let { raw -> TrudyConfidence.entries.firstOrNull { it.name == raw } },
        result = meta["result"],
        caveats = meta.stringList("caveat"),
        evidenceReferences = evidence
    )
}

private fun MutableMap<String, String>.putStringList(prefix: String, values: List<String>) {
    this["${prefix}Count"] = values.size.toString()
    values.forEachIndexed { index, value -> this["$prefix.$index"] = value }
}

private fun Map<String, String>.stringList(prefix: String): List<String> =
    (0 until (this["${prefix}Count"]?.toIntOrNull() ?: 0)).mapNotNull { this["$prefix.$it"] }
