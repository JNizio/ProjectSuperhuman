package com.projectsuperhuman.next

import java.time.Instant

/** Immutable source-facing snapshot. Nothing here is interpreted or corrected. */
internal data class RawSleepRecordSnapshot(
    val sourceRecordId: String,
    val start: Instant,
    val end: Instant,
    val stages: List<RawSleepStageSnapshot>,
    val sourcePackage: String? = null
)

internal data class RawSleepStageSnapshot(
    val type: String,
    val start: Instant,
    val end: Instant
)

/** Data contract consumed by future interpretation layers. */
internal data class ReconstructedSleepSnapshot(
    val episodes: List<CanonicalSleepEpisode>,
    val generatedAt: Instant = Instant.now()
) {
    val nights get() = episodes.filter { it.classification == SleepEpisodeClassification.NIGHT }
    val naps get() = episodes.filter { it.classification == SleepEpisodeClassification.NAP }
}
