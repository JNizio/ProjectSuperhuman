package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthRepository
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.db.SuperhumanDatabase

class SqlHealthRepository(
    private val database: SuperhumanDatabase,
    private val nowEpochMs: () -> Long
) : HealthRepository {
    private val q = database.healthStoreQueries

    override suspend fun save(values: List<HealthValue>) {
        if (values.isEmpty()) return
        val now = nowEpochMs()
        q.transaction {
            values.forEach { value ->
                q.upsertHealthValue(
                    id = stableId(value),
                    domain = value.domain.name,
                    metric = value.metric,
                    numeric_value = value.value,
                    unit = value.unit,
                    timestamp_epoch_ms = value.timestampEpochMs,
                    source = value.source,
                    source_record_id = value.metadata["sourceRecordId"],
                    metadata_json = encodeMetadata(value.metadata),
                    created_epoch_ms = now,
                    updated_epoch_ms = now
                )
            }
        }
    }

    override suspend fun latest(metric: String): HealthValue? =
        q.latestByMetric(metric, ::mapHealthValue).executeAsOneOrNull()

    override suspend fun between(
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> =
        q.betweenByMetric(metric, fromEpochMs, toEpochMs, ::mapHealthValue).executeAsList()

    override suspend fun latestForDomain(domain: HealthDomain): List<HealthValue> =
        q.latestForDomain(domain.name, domain.name, ::mapHealthValue).executeAsList()

    fun count(): Long = q.countValues().executeAsOne()

    private fun mapHealthValue(
        id: String,
        domain: String,
        metric: String,
        numericValue: Double,
        unit: String,
        timestampEpochMs: Long,
        source: String,
        sourceRecordId: String?,
        metadataJson: String?,
        createdEpochMs: Long,
        updatedEpochMs: Long
    ): HealthValue = HealthValue(
        domain = runCatching { HealthDomain.valueOf(domain) }.getOrDefault(HealthDomain.CLINICAL),
        metric = metric,
        value = numericValue,
        unit = unit,
        timestampEpochMs = timestampEpochMs,
        source = source,
        metadata = decodeMetadata(metadataJson) + listOfNotNull(
            sourceRecordId?.let { "sourceRecordId" to it }
        ).toMap()
    )

    private fun stableId(v: HealthValue): String = buildString {
        append(v.source); append('|')
        append(v.metadata["sourceRecordId"] ?: "local"); append('|')
        append(v.domain.name); append('|')
        append(v.metric); append('|')
        append(v.timestampEpochMs); append('|')
        append(v.value); append('|')
        append(v.unit)
    }

    private fun encodeMetadata(map: Map<String, String>): String? {
        if (map.isEmpty()) return null
        return map.entries.joinToString("\n") { (k, v) -> escape(k) + "=" + escape(v) }
    }

    private fun decodeMetadata(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.lineSequence().mapNotNull { line ->
            val split = findUnescapedEquals(line)
            if (split < 0) null else unescape(line.substring(0, split)) to unescape(line.substring(split + 1))
        }.toMap()
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("=", "\\e")

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> out.append('\n')
                    'e' -> out.append('=')
                    '\\' -> out.append('\\')
                    else -> out.append(s[i + 1])
                }
                i += 2
            } else {
                out.append(s[i++])
            }
        }
        return out.toString()
    }

    private fun findUnescapedEquals(s: String): Int {
        var escaped = false
        s.forEachIndexed { index, c ->
            if (escaped) escaped = false
            else if (c == '\\') escaped = true
            else if (c == '=') return index
        }
        return -1
    }
}
