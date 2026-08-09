package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.HealthValue

/**
 * Neutral hand-off format between the current WebView/localStorage world and the
 * next-generation database. The legacy app can export batches into this shape;
 * the new app can validate them before committing anything.
 */
data class LegacyImportBatch(
    val sourceVersion: String,
    val exportedEpochMs: Long,
    val values: List<HealthValue>,
    val rawSections: Map<String, String> = emptyMap()
)

data class LegacyImportReport(
    val accepted: Int,
    val rejected: Int,
    val duplicateCandidates: Int,
    val warnings: List<String>
)

class LegacyImportValidator {
    fun validate(batch: LegacyImportBatch): LegacyImportReport {
        var accepted = 0
        var rejected = 0
        val seen = mutableSetOf<String>()
        var duplicates = 0
        val warnings = mutableListOf<String>()

        batch.values.forEach { v ->
            val valid = v.metric.isNotBlank() &&
                v.unit.isNotBlank() &&
                v.value.isFinite() &&
                v.timestampEpochMs > 0 &&
                v.source.isNotBlank()
            if (!valid) {
                rejected++
                return@forEach
            }
            accepted++
            val key = "${v.source}|${v.metadata["sourceRecordId"] ?: ""}|${v.metric}|${v.timestampEpochMs}|${v.value}|${v.unit}"
            if (!seen.add(key)) duplicates++
        }

        if (batch.sourceVersion.isBlank()) warnings += "Legacy source version is missing."
        if (batch.rawSections.isNotEmpty()) warnings += "Unmapped legacy sections are preserved for later migration passes."

        return LegacyImportReport(accepted, rejected, duplicates, warnings)
    }
}
