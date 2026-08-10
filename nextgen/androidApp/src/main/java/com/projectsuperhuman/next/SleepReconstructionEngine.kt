package com.projectsuperhuman.next

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Canonical representation of one reconstructed sleep episode. */
internal data class CanonicalSleepEpisode(
    val start: Instant,
    val end: Instant,
    val blocks: List<CanonicalSleepBlock>,
    val interruptions: List<SleepInterruption>,
    val sourceRecordIds: List<String>,
    val classification: SleepEpisodeClassification,
    val confidence: Int
) {
    val spanMinutes: Int get() = Duration.between(start, end).toMinutes().coerceAtLeast(0).toInt()
    val interruptionMinutes: Int get() = interruptions.sumOf { it.durationMinutes }
    val recordedStageMinutes: Int get() = blocks.sumOf { it.recordedStageMinutes }
}

internal data class CanonicalSleepBlock(
    val start: Instant,
    val end: Instant,
    val sourceRecordId: String,
    val stages: List<CanonicalSleepStage>,
    val sourceConfidence: Int
) {
    val durationMinutes: Int get() = Duration.between(start, end).toMinutes().coerceAtLeast(0).toInt()
    val recordedStageMinutes: Int get() = stages.sumOf { it.durationMinutes }
}

internal data class CanonicalSleepStage(
    val type: String,
    val start: Instant,
    val end: Instant,
    val durationMinutes: Int,
    val sourceRecordId: String
)

internal data class SleepInterruption(
    val start: Instant,
    val end: Instant,
    val durationMinutes: Int,
    val confidence: Int,
    val reason: String
)

enum class SleepEpisodeClassification { NIGHT, NAP }

/**
 * Stage 1 only: reconstruct source records without inventing sleep.
 * Raw Health Connect records remain untouched. Unknown/gapped time stays unknown
 * unless the source explicitly reports a stage. Interpretation belongs to later layers.
 */
internal object SleepReconstructionEngine {
    private val maxNightGap = Duration.ofHours(6)
    private val maxNightSpan = Duration.ofHours(18)
    private val maxNapDuration = Duration.ofHours(4)
    private const val morningCutoff = 12
    private const val eveningStart = 18

    fun reconstruct(records: List<SleepSessionRecord>, zone: ZoneId = ZoneId.systemDefault()): List<CanonicalSleepEpisode> {
        if (records.isEmpty()) return emptyList()
        val ordered = records.sortedBy { it.startTime }
        val groups = mutableListOf<MutableList<SleepSessionRecord>>()

        ordered.forEach { record ->
            val current = groups.lastOrNull()
            if (current == null || !belongsToSameEpisode(current, record, zone)) {
                groups += mutableListOf(record)
            } else {
                current += record
            }
        }

        return groups.map { buildEpisode(it, zone) }
    }

    private fun belongsToSameEpisode(current: List<SleepSessionRecord>, next: SleepSessionRecord, zone: ZoneId): Boolean {
        val previous = current.last()
        val gap = Duration.between(previous.endTime, next.startTime)
        if (gap.isNegative || gap > maxNightGap) return false

        val span = Duration.between(current.first().startTime, next.endTime)
        if (span > maxNightSpan) return false

        val first = current.first().startTime.atZone(zone)
        val nextStart = next.startTime.atZone(zone)
        val nextHour = nextStart.hour

        // Morning continuation is allowed only when the episode began overnight.
        if (nextHour < morningCutoff && first.hour >= eveningStart) return true

        // Same-evening / overnight blocks may be adjacent without requiring a morning rule.
        return first.toLocalDate() == nextStart.toLocalDate() && first.hour >= eveningStart && nextHour >= eveningStart
    }

    private fun buildEpisode(records: List<SleepSessionRecord>, zone: ZoneId): CanonicalSleepEpisode {
        val ordered = records.sortedBy { it.startTime }
        val classification = classify(ordered, zone)
        val blocks = ordered.map { toBlock(it) }
        val interruptions = ordered.zipWithNext().mapNotNull { (a, b) ->
            val gap = Duration.between(a.endTime, b.startTime).toMinutes().coerceAtLeast(0).toInt()
            if (gap <= 0) null else SleepInterruption(
                start = a.endTime,
                end = b.startTime,
                durationMinutes = gap,
                confidence = 92,
                reason = "gap-between-source-sessions"
            )
        }
        val sourceIds = ordered.map { it.metadata.id }
        val confidence = calculateConfidence(ordered, interruptions, classification)
        return CanonicalSleepEpisode(
            start = ordered.first().startTime,
            end = ordered.last().endTime,
            blocks = blocks,
            interruptions = interruptions,
            sourceRecordIds = sourceIds,
            classification = classification,
            confidence = confidence
        )
    }

    private fun classify(records: List<SleepSessionRecord>, zone: ZoneId): SleepEpisodeClassification {
        val span = Duration.between(records.first().startTime, records.last().endTime)
        val startHour = records.first().startTime.atZone(zone).hour
        val endHour = records.last().endTime.atZone(zone).hour
        val overnight = startHour >= eveningStart || endHour < morningCutoff
        return if (span <= maxNapDuration && !overnight) SleepEpisodeClassification.NAP else SleepEpisodeClassification.NIGHT
    }

    private fun toBlock(record: SleepSessionRecord): CanonicalSleepBlock {
        val stages = record.stages.mapNotNull { stage ->
            val duration = Duration.between(stage.startTime, stage.endTime).toMinutes().coerceAtLeast(0).toInt()
            if (duration <= 0) return@mapNotNull null
            CanonicalSleepStage(
                type = stageType(stage.stage),
                start = stage.startTime,
                end = stage.endTime,
                durationMinutes = duration,
                sourceRecordId = record.metadata.id
            )
        }
        return CanonicalSleepBlock(record.startTime, record.endTime, record.metadata.id, stages, sourceConfidence = 100)
    }

    private fun stageType(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "awake"
        SleepSessionRecord.STAGE_TYPE_REM -> "rem"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "sleeping"
        else -> "unknown"
    }

    private fun calculateConfidence(
        records: List<SleepSessionRecord>,
        interruptions: List<SleepInterruption>,
        classification: SleepEpisodeClassification
    ): Int {
        var score = 100
        if (records.size > 1) score -= (records.size - 1).coerceAtMost(10) * 2
        if (interruptions.any { it.durationMinutes >= 30 }) score -= 8
        if (classification == SleepEpisodeClassification.NAP) score -= 2
        if (records.any { it.stages.isEmpty() }) score -= 8
        return score.coerceIn(50, 100)
    }
}
